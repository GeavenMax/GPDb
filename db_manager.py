from __future__ import annotations
import datetime
import json
import sqlite3
import os
import threading
from pathlib import Path
from typing import Any

SCHEMA_FILE = Path(__file__).parent / "schema.sql"

class DatabaseManager:
    """SQLite access layer.

    The underlying connection is shared across threads (`check_same_thread=False`),
    so every write is serialised through a re-entrant lock: concurrent
    `with self.conn:` blocks on one connection interleave and raise
    "cannot commit transaction - SQL statements in progress".
    """

    def __init__(self, db_path: str = "gevi.db"):
        self.db_path = db_path
        self._write_lock = threading.RLock()
        self.conn = sqlite3.connect(self.db_path, timeout=30.0, check_same_thread=False)
        self.conn.execute("PRAGMA journal_mode = WAL;")
        self.conn.execute("PRAGMA synchronous = NORMAL;")
        self.conn.execute("PRAGMA foreign_keys = ON;")
        self.init_db()

    # Columns added after the initial release. `CREATE TABLE IF NOT EXISTS` never
    # alters an existing table, so pre-existing databases must be migrated explicitly.
    MIGRATIONS: dict[str, list[tuple[str, str]]] = {
        "movies": [
            ("description_zh", "TEXT"),
            ("translation_attempts", "INTEGER DEFAULT 0"),
            ("cover_back", "TEXT"),
        ],
        "performers": [
            ("image_url", "TEXT"),
        ],
        "episodes": [
            ("description_zh", "TEXT"),
        ],
    }

    def init_db(self):
        """Create/upgrade the schema.

        Migrations run *before* schema.sql: on an existing database the tables are
        already there, so the new columns (and the indexes over them) must be added
        before schema.sql is replayed. On a fresh database the tables do not exist
        yet, migrations are a no-op, and schema.sql creates everything at once.
        """
        if not SCHEMA_FILE.exists():
            return
        self.apply_migrations()
        with open(SCHEMA_FILE, "r", encoding="utf-8") as f:
            schema_sql = f.read()
        with self._write_lock, self.conn:
            self.conn.executescript(schema_sql)

    def apply_migrations(self):
        """Add any missing columns to pre-existing tables (idempotent)."""
        with self._write_lock, self.conn:
            for table, columns in self.MIGRATIONS.items():
                existing = {
                    row[1] for row in self.conn.execute(f"PRAGMA table_info({table})").fetchall()
                }
                if not existing:
                    continue  # table itself missing; schema.sql will create it next run
                for col_name, col_type in columns:
                    if col_name not in existing:
                        self.conn.execute(f"ALTER TABLE {table} ADD COLUMN {col_name} {col_type}")

    def get_completed_ids(self, item_type: str) -> set[int]:
        """Fetch all IDs that are already completed (status 200 or 404)."""
        cur = self.conn.cursor()
        cur.execute(
            "SELECT item_id FROM scrape_progress WHERE item_type = ? AND status IN (200, 404)",
            (item_type,)
        )
        return {row[0] for row in cur.fetchall()}

    def get_failed_ids(self, item_type: str) -> set[int]:
        """Fetch all IDs that resulted in status 500 (error) for retry."""
        cur = self.conn.cursor()
        cur.execute(
            "SELECT item_id FROM scrape_progress WHERE item_type = ? AND status = 500",
            (item_type,)
        )
        return {row[0] for row in cur.fetchall()}

    def get_pending_ids(self, item_type: str, start_id: int, end_id: int) -> list[int]:
        """Return a sorted list of IDs within [start_id, end_id] that have not been scraped."""
        completed = self.get_completed_ids(item_type)
        return [i for i in range(start_id, end_id + 1) if i not in completed]

    def get_404_ids(self, item_type: str) -> list[int]:
        """IDs previously recorded as 404 (candidates for re-verification)."""
        cur = self.conn.cursor()
        cur.execute(
            "SELECT item_id FROM scrape_progress WHERE item_type = ? AND status = 404 ORDER BY item_id",
            (item_type,)
        )
        return [row[0] for row in cur.fetchall()]

    def get_status_ids(self, item_type: str, status: int) -> set[int]:
        """Fetch all IDs recorded with a specific status."""
        cur = self.conn.cursor()
        cur.execute(
            "SELECT item_id FROM scrape_progress WHERE item_type = ? AND status = ?",
            (item_type, status)
        )
        return {row[0] for row in cur.fetchall()}

    def record_progress(self, item_type: str, item_id: int, status: int) -> None:
        """Record the outcome of one scrape attempt.

        Status codes: 200 = saved, 404 = does not exist, 500 = transient failure,
        0 = interrupted. A 200 also refreshes the row's scraped_at timestamp.
        """
        with self._write_lock, self.conn:
            self.conn.execute(
                "INSERT OR REPLACE INTO scrape_progress (item_type, item_id, status) VALUES (?, ?, ?)",
                (item_type, item_id, status)
            )
            if status == 200:
                table = "movies" if item_type == "movie" else "performers"
                self.conn.execute(
                    f"UPDATE {table} SET scraped_at = CURRENT_TIMESTAMP WHERE id = ?",
                    (item_id,)
                )

    # Fields a movie record is expected to have. Used to find rows that are marked
    # "scraped" (status 200) but whose content is actually incomplete - those are the
    # ones worth re-fetching, as opposed to blindly re-scraping everything.
    MOVIE_GAP_CHECKS: dict[str, str] = {
        "description": "(description IS NULL OR trim(description) = '')",
        "year": "release_year IS NULL",
        "duration": "duration_mins IS NULL",
        "cover": "((cover_full IS NULL OR cover_full = '') AND (cover_icon IS NULL OR cover_icon = ''))",
        # A film can have a front cover on file and still have an unrecorded back
        # cover: covers_json was added after ~2,900 rows were already scraped, and
        # "cover" above is satisfied by cover_full alone, so nothing would ever go
        # back for them. This check selects exactly those films. It closes itself
        # either way - a re-fetch writes the variant list, or records a void when the
        # page turns out to have no gallery after all.
        "cover_variants": """(COALESCE(covers_json, '') = ''
                             AND (COALESCE(cover_full, '') <> '' OR COALESCE(cover_icon, '') <> ''))""",
        "cast": "NOT EXISTS (SELECT 1 FROM movie_performers mp WHERE mp.movie_id = movies.id)",
        "studio": "(studio_name IS NULL OR trim(studio_name) = '')",
        "director": "(director_name IS NULL OR trim(director_name) = '')",
        # Films whose page has no scene list. Nothing else in this dict would ever
        # send them back to the site: every other field can be perfectly filled while
        # `episodes` is empty, which is how ~25k films came to have no episode rows
        # at all. --mode episodes reads this check, and the void it records on a
        # second empty fetch is what keeps that work list from growing back.
        "episodes": "NOT EXISTS (SELECT 1 FROM episodes e WHERE e.movie_id = movies.id)",
    }

    # The fields --mode gaps looks for when --gaps is not given. Kept here next to the
    # predicates so the audit report and the scraper's actual work list cannot drift
    # apart (they did: the audit said "0 films need work" while the scraper had 2,793).
    DEFAULT_MOVIE_GAPS: list[str] = [
        "description", "year", "duration", "cover", "cover_variants", "cast",
    ]

    PERFORMER_GAP_CHECKS: dict[str, str] = {
        "attributes": """(COALESCE(hair,'') = '' AND COALESCE(eyes,'') = '' AND COALESCE(height,'') = ''
                         AND COALESCE(weight,'') = '' AND COALESCE(build,'') = '' AND COALESCE(skin,'') = '')""",
        "image": "(image_url IS NULL OR image_url = '')",
        "notes": "(notes IS NULL OR trim(notes) = '')",
    }

    def get_incomplete_movies(self, gaps: list[str] | None = None,
                              void_min_attempts: int = 2) -> list[dict]:
        """Movies whose stored row is missing one or more of the given fields.

        Defaults to the fields whose absence means the earlier scrape of that movie
        failed to read something real (see MOVIE_GAP_CHECKS).

        A field recorded in `scrape_voids` with at least `void_min_attempts` failed
        fetches is left out: the source has no value for it, so asking again is a
        wasted request. Pass 0 to ignore voids and see every gap.
        """
        keys = gaps or self.DEFAULT_MOVIE_GAPS
        unknown = [k for k in keys if k not in self.MOVIE_GAP_CHECKS]
        if unknown:
            raise ValueError(f"未知的字段检查项: {unknown}")
        voids = self.get_voids("movie", void_min_attempts)
        cols = ", ".join(
            f"CASE WHEN {self.MOVIE_GAP_CHECKS[k]} THEN 1 ELSE 0 END AS missing_{k}" for k in keys
        )
        rows = self.conn.execute(f"SELECT id, title, {cols} FROM movies ORDER BY id").fetchall()
        out = []
        for r in rows:
            voided = voids.get(r[0], frozenset())
            missing = [keys[i] for i, flag in enumerate(r[2:]) if flag and keys[i] not in voided]
            if missing:
                out.append({"id": r[0], "title": r[1], "missing": missing})
        return out

    def get_incomplete_performers(self, gaps: list[str] | None = None,
                                  void_min_attempts: int = 2) -> list[dict]:
        """Performers whose stored row is missing one or more of the given fields."""
        keys = gaps or ["attributes"]
        unknown = [k for k in keys if k not in self.PERFORMER_GAP_CHECKS]
        if unknown:
            raise ValueError(f"未知的字段检查项: {unknown}")
        voids = self.get_voids("performer", void_min_attempts)
        cols = ", ".join(
            f"CASE WHEN {self.PERFORMER_GAP_CHECKS[k]} THEN 1 ELSE 0 END AS missing_{k}" for k in keys
        )
        rows = self.conn.execute(f"SELECT id, name, {cols} FROM performers ORDER BY id").fetchall()
        out = []
        for r in rows:
            voided = voids.get(r[0], frozenset())
            missing = [keys[i] for i, flag in enumerate(r[2:]) if flag and keys[i] not in voided]
            if missing:
                out.append({"id": r[0], "name": r[1], "missing": missing})
        return out

    def movie_field_state(self, movie_id: int, fields: list[str]) -> dict[str, bool]:
        """For each field, True when this movie's *stored* row now has a value for it.

        Read back after a write rather than trusting the parse, which is what makes
        the "does the source actually have this?" bookkeeping exact.
        """
        unknown = [f for f in fields if f not in self.MOVIE_GAP_CHECKS]
        if unknown:
            raise ValueError(f"未知的字段检查项: {unknown}")
        cols = ", ".join(
            f"CASE WHEN {self.MOVIE_GAP_CHECKS[f]} THEN 0 ELSE 1 END" for f in fields
        )
        row = self.conn.execute(f"SELECT {cols} FROM movies WHERE id = ?", (movie_id,)).fetchone()
        if row is None:
            return {f: False for f in fields}
        return {f: bool(row[i]) for i, f in enumerate(fields)}

    def performer_field_state(self, performer_id: int, fields: list[str]) -> dict[str, bool]:
        """For each field, True when this performer's stored row now has a value for it."""
        unknown = [f for f in fields if f not in self.PERFORMER_GAP_CHECKS]
        if unknown:
            raise ValueError(f"未知的字段检查项: {unknown}")
        cols = ", ".join(
            f"CASE WHEN {self.PERFORMER_GAP_CHECKS[f]} THEN 0 ELSE 1 END" for f in fields
        )
        row = self.conn.execute(f"SELECT {cols} FROM performers WHERE id = ?", (performer_id,)).fetchone()
        if row is None:
            return {f: False for f in fields}
        return {f: bool(row[i]) for i, f in enumerate(fields)}

    def get_voids(self, item_type: str, min_attempts: int = 1) -> dict[int, set[str]]:
        """Fields known to be absent at the source: {item_id: {field, ...}}."""
        cur = self.conn.execute(
            "SELECT item_id, field FROM scrape_voids WHERE item_type = ? AND attempts >= ?",
            (item_type, min_attempts)
        )
        out: dict[int, set[str]] = {}
        for item_id, field in cur.fetchall():
            out.setdefault(item_id, set()).add(field)
        return out

    def record_void(self, item_type: str, item_id: int, field: str) -> None:
        """Note that a fetch of this item did not produce this field.

        Counted rather than boolean: a single miss can be a parser hiccup, so callers
        only act on voids that have accumulated (see void_min_attempts).
        """
        with self._write_lock, self.conn:
            self.conn.execute("""
                INSERT INTO scrape_voids (item_type, item_id, field, attempts)
                VALUES (?, ?, ?, 1)
                ON CONFLICT(item_type, item_id, field) DO UPDATE SET
                    attempts = scrape_voids.attempts + 1,
                    noted_at = CURRENT_TIMESTAMP
            """, (item_type, item_id, field))

    def clear_void(self, item_type: str, item_id: int, field: str) -> None:
        """Drop a void: the source turned out to have the value after all."""
        with self._write_lock, self.conn:
            self.conn.execute(
                "DELETE FROM scrape_voids WHERE item_type = ? AND item_id = ? AND field = ?",
                (item_type, item_id, field)
            )

    def clear_voids(self, item_type: str | None = None) -> int:
        """Forget every recorded void (use when the source may have filled them in)."""
        with self._write_lock, self.conn:
            if item_type:
                cur = self.conn.execute("DELETE FROM scrape_voids WHERE item_type = ?", (item_type,))
            else:
                cur = self.conn.execute("DELETE FROM scrape_voids")
            return cur.rowcount

    def void_summary(self, item_type: str) -> dict[str, int]:
        """How many items have each field recorded as absent at the source."""
        cur = self.conn.execute(
            "SELECT field, COUNT(*) FROM scrape_voids WHERE item_type = ? GROUP BY field ORDER BY 2 DESC",
            (item_type,)
        )
        return {row[0]: row[1] for row in cur.fetchall()}

    def clear_progress(self, item_type: str, statuses: tuple[int, ...]) -> int:
        """Delete progress rows with the given statuses so those IDs get re-scraped."""
        if not statuses:
            return 0
        placeholders = ",".join("?" * len(statuses))
        with self._write_lock, self.conn:
            cur = self.conn.execute(
                f"DELETE FROM scrape_progress WHERE item_type = ? AND status IN ({placeholders})",
                (item_type, *statuses)
            )
            return cur.rowcount

    def get_known_performer_ids(self) -> list[int]:
        """Every performer ID we have ever seen referenced by a movie's cast list.

        Far cheaper than scanning a numeric range: the GEVI performer ID space is
        extremely sparse, so range scanning wastes the vast majority of requests.
        """
        cur = self.conn.cursor()
        cur.execute("SELECT id FROM performers ORDER BY id")
        return [row[0] for row in cur.fetchall()]

    # --- Machine Translation Support ---
    def get_untranslated_movies(self, limit: int | None = None, max_attempts: int = 3) -> list[dict]:
        """Movies that still need a Chinese description, skipping ones that keep failing."""
        sql = """
            SELECT id, title, description FROM movies
            WHERE description IS NOT NULL AND trim(description) != ''
              AND description_zh IS NULL
              AND COALESCE(translation_attempts, 0) < ?
            ORDER BY id ASC
        """
        args: list[Any] = [max_attempts]
        if limit and limit > 0:
            sql += " LIMIT ?"
            args.append(limit)
        rows = self.conn.execute(sql, args).fetchall()
        return [{"id": r[0], "title": r[1], "description": r[2]} for r in rows]

    def set_movie_translation(self, movie_id: int, description_zh: str | None):
        """Store (or clear) a translated description. Passing None records a failed attempt."""
        with self._write_lock, self.conn:
            if description_zh:
                self.conn.execute(
                    "UPDATE movies SET description_zh = ?, translation_attempts = 0 WHERE id = ?",
                    (description_zh, movie_id)
                )
            else:
                self.conn.execute(
                    "UPDATE movies SET translation_attempts = COALESCE(translation_attempts, 0) + 1 WHERE id = ?",
                    (movie_id,)
                )

    def get_untranslated_episodes(self, limit: int | None = None, movie_id: int | None = None) -> list[dict]:
        """Episodes that still need a Chinese description."""
        sql = """
            SELECT id, movie_id, title, description FROM episodes
            WHERE description IS NOT NULL AND trim(description) != ''
              AND description_zh IS NULL
        """
        args: list[Any] = []
        if movie_id is not None:
            sql += " AND movie_id = ?"
            args.append(movie_id)
        sql += " ORDER BY id ASC"
        if limit and limit > 0:
            sql += " LIMIT ?"
            args.append(limit)
        rows = self.conn.execute(sql, args).fetchall()
        return [{"id": r[0], "movie_id": r[1], "title": r[2], "description": r[3]} for r in rows]

    def set_episode_translation(self, episode_id: int, description_zh: str | None):
        """Store a translated episode synopsis. None is a no-op (see set_movie_translation)."""
        if not description_zh:
            return
        with self._write_lock, self.conn:
            self.conn.execute(
                "UPDATE episodes SET description_zh = ? WHERE id = ?",
                (description_zh, episode_id)
            )

    # --- Attribute glossary (see schema.sql §10) ---
    def load_glossary(self) -> dict[str, str]:
        """The whole glossary as {english: chinese}. Small (~73 rows), cache freely."""
        rows = self.conn.execute("SELECT en, zh FROM attr_glossary").fetchall()
        return {r[0]: r[1] for r in rows}

    def save_glossary(self, entries: dict[str, str]) -> int:
        """Upsert glossary entries. Returns how many rows were written."""
        if not entries:
            return 0
        with self._write_lock, self.conn:
            self.conn.executemany(
                "INSERT INTO attr_glossary (en, zh, updated_at) VALUES (?, ?, CURRENT_TIMESTAMP) "
                "ON CONFLICT(en) DO UPDATE SET zh = excluded.zh, updated_at = CURRENT_TIMESTAMP",
                [(en, zh) for en, zh in entries.items() if en and zh],
            )
        return len(entries)

    def get_translation_stats(self) -> dict[str, int]:
        cur = self.conn.cursor()
        total = cur.execute(
            "SELECT COUNT(*) FROM movies WHERE description IS NOT NULL AND trim(description) != ''"
        ).fetchone()[0]
        done = cur.execute("SELECT COUNT(*) FROM movies WHERE description_zh IS NOT NULL").fetchone()[0]
        failed = cur.execute(
            "SELECT COUNT(*) FROM movies WHERE description_zh IS NULL "
            "AND COALESCE(translation_attempts, 0) >= 3 AND description IS NOT NULL AND trim(description) != ''"
        ).fetchone()[0]
        return {"translatable": total, "translated": done, "pending": total - done - failed, "failed": failed}

    def record_progress(self, item_type: str, item_id: int, status: int):
        with self._write_lock, self.conn:
            self.conn.execute(
                "INSERT OR REPLACE INTO scrape_progress (item_type, item_id, status) VALUES (?, ?, ?)",
                (item_type, item_id, status)
            )

    def save_movie(self, m: dict, status: int = 200):
        """Save a single movie record with all related entities in a transaction.

        Re-scrapes merge rather than replace: a field is only overwritten when the
        new parse actually produced a value. A site redesign that silently breaks
        one of the scraper's regexes would otherwise blank out thousands of rows
        that are already correct.
        """
        with self._write_lock, self.conn:
            # 1. Movie record
            # covers_json = every cover variant in site order, written whenever the film
            # has any. A film with no cover art renders no gallery at all, so its list
            # stays NULL - see cover_back for how "none" is told apart from "unknown".
            covers_val = json.dumps(m["covers"]) if m.get("covers") else None

            # cover_back is deliberately NOT NULLIF'd against '': an empty string here
            # means "gallery was read, film has a front cover but no back cover" and
            # must survive the UPSERT. It is only written at all when the gallery was
            # actually found, so NULL keeps its one meaning - never checked - and a
            # later bulk cover download can select work with:
            #     cover_full <> '' AND cover_back IS NULL
            # without re-fetching films that were already checked.
            cover_back_val = (m.get("cover_back") or "") if m.get("covers_known") else None
            # UPSERT rather than INSERT OR REPLACE: REPLACE deletes the old row, which
            # would silently discard description_zh / translation_attempts on re-scrape.
            self.conn.execute("""
                INSERT INTO movies (
                    id, title, studio_id, studio_name, release_year, duration_mins,
                    category, rating, movie_type, description, cover_icon, cover_full,
                    covers_json, cover_back, director_id, director_name
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    title = COALESCE(NULLIF(excluded.title, ''), movies.title),
                    studio_id = COALESCE(excluded.studio_id, movies.studio_id),
                    studio_name = COALESCE(NULLIF(excluded.studio_name, ''), movies.studio_name),
                    release_year = COALESCE(excluded.release_year, movies.release_year),
                    duration_mins = COALESCE(excluded.duration_mins, movies.duration_mins),
                    category = COALESCE(NULLIF(excluded.category, ''), movies.category),
                    rating = COALESCE(NULLIF(excluded.rating, ''), movies.rating),
                    movie_type = COALESCE(NULLIF(excluded.movie_type, ''), movies.movie_type),
                    description = COALESCE(NULLIF(excluded.description, ''), movies.description),
                    cover_icon = COALESCE(NULLIF(excluded.cover_icon, ''), movies.cover_icon),
                    cover_full = COALESCE(NULLIF(excluded.cover_full, ''), movies.cover_full),
                    covers_json = COALESCE(NULLIF(excluded.covers_json, ''), movies.covers_json),
                    cover_back = COALESCE(excluded.cover_back, movies.cover_back),
                    director_id = COALESCE(excluded.director_id, movies.director_id),
                    director_name = COALESCE(NULLIF(excluded.director_name, ''), movies.director_name),
                    scraped_at = CURRENT_TIMESTAMP
            """, (
                m["id"], m["title"], m.get("studio_id"), m.get("studio_name"),
                m.get("release_year"), m.get("duration_mins"), m.get("category"),
                m.get("rating"), m.get("movie_type"), m.get("description"),
                m.get("cover_icon"), m.get("cover_full"),
                covers_val, cover_back_val, m.get("director_id"), m.get("director_name")
            ))

            # 2. Performers link. An empty cast list means "we failed to read the
            # cast", not "this film has no cast" - deleting on that basis throws
            # away every cast link the movie already had.
            new_performers = m.get("performers") or []
            if new_performers:
                self.conn.execute("DELETE FROM movie_performers WHERE movie_id = ?", (m["id"],))
                for pid, pname in new_performers:
                    self.conn.execute("INSERT OR IGNORE INTO performers (id, name) VALUES (?, ?)", (pid, pname))
                    self.conn.execute(
                        "INSERT OR REPLACE INTO movie_performers (movie_id, performer_id, performer_name) VALUES (?, ?, ?)",
                        (m["id"], pid, pname)
                    )

            # 3. Episodes
            self._write_episodes(m["id"], m.get("episodes") or [])

            # 4. FTS5 Index update
            perf_names = " ".join(p[1] for p in m.get("performers", []))
            self.conn.execute("DELETE FROM movies_fts WHERE id = ?", (m["id"],))
            self.conn.execute("""
                INSERT INTO movies_fts (id, title, studio_name, category, description, performers)
                VALUES (?, ?, ?, ?, ?, ?)
            """, (m["id"], m["title"], m.get("studio_name"), m.get("category"), m.get("description"), perf_names))

            # 5. Scrape progress
            self.conn.execute(
                "INSERT OR REPLACE INTO scrape_progress (item_type, item_id, status) VALUES (?, ?, ?)",
                ("movie", m["id"], status)
            )

    def save_episodes(self, movie_id: int, episodes: list[dict] | None) -> int:
        """Write only the episode rows of one film, leaving the movie row untouched.

        This is what `--mode episodes` calls. That mode exists because episode rows
        are only ever written as a side effect of scraping a film's own page, so a
        film scraped before a given episode was on the site - or before the parser
        could read it - keeps an empty scene list forever unless something goes back
        for it deliberately.
        """
        with self._write_lock, self.conn:
            return self._write_episodes(movie_id, episodes or [])

    def _write_episodes(self, movie_id: int, episodes: list[dict]) -> int:
        """The shared body; the caller holds the write lock and an open transaction.

        Split out because save_movie writes episodes inside its own transaction -
        opening a second one there would commit the movie before its FTS row landed.

        An upsert rather than INSERT OR REPLACE: REPLACE deletes the row first, which
        would drop description_zh - the Chinese synopsis - on every re-scrape. The
        translation is carried over while the site's English text is unchanged, and
        dropped when it really did change, since it then translates something else.
        """
        for ep in episodes:
            self.conn.execute("""
                INSERT INTO episodes (id, movie_id, title, thumbnail_url, description, action_notes)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    movie_id = excluded.movie_id,
                    title = COALESCE(NULLIF(excluded.title, ''), episodes.title),
                    thumbnail_url = COALESCE(NULLIF(excluded.thumbnail_url, ''), episodes.thumbnail_url),
                    action_notes = COALESCE(NULLIF(excluded.action_notes, ''), episodes.action_notes),
                    description = COALESCE(NULLIF(excluded.description, ''), episodes.description),
                    description_zh = CASE
                        -- Nothing new was read: keep both the old text and its translation.
                        WHEN COALESCE(excluded.description, '') = '' THEN episodes.description_zh
                        WHEN excluded.description = episodes.description THEN episodes.description_zh
                        ELSE NULL
                    END
            """, (
                ep["id"], movie_id, ep.get("title"), ep.get("thumbnail_url"),
                ep.get("description"), ep.get("action_notes")
            ))
            # An empty performer list means the parse found none, not that the scene
            # has none - so, as with a film's cast, the old links are left alone.
            new_performers = ep.get("performers") or []
            if new_performers:
                self.conn.execute("DELETE FROM episode_performers WHERE episode_id = ?", (ep["id"],))
                for epid, epname in new_performers:
                    self.conn.execute("INSERT OR IGNORE INTO performers (id, name) VALUES (?, ?)", (epid, epname))
                    self.conn.execute(
                        "INSERT OR REPLACE INTO episode_performers (episode_id, performer_id, performer_name) VALUES (?, ?, ?)",
                        (ep["id"], epid, epname)
                    )
        return len(episodes)

    def save_performer(self, p: dict, status: int = 200):
        """Save a single performer record and update FTS5."""
        with self._write_lock, self.conn:
            # Upsert. Every attribute merges rather than replaces, so a re-scrape that
            # reads the page incompletely cannot blank out fields we already have -
            # including a previously discovered portrait.
            self.conn.execute("""
                INSERT INTO performers (
                    id, name, hair, eyes, body_hair, facial_hair, height, weight,
                    build, skin, dick_size, foreskin, tattoos, notes, image_url
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    name = COALESCE(NULLIF(excluded.name, ''), performers.name),
                    hair = COALESCE(NULLIF(excluded.hair, ''), performers.hair),
                    eyes = COALESCE(NULLIF(excluded.eyes, ''), performers.eyes),
                    body_hair = COALESCE(NULLIF(excluded.body_hair, ''), performers.body_hair),
                    facial_hair = COALESCE(NULLIF(excluded.facial_hair, ''), performers.facial_hair),
                    height = COALESCE(NULLIF(excluded.height, ''), performers.height),
                    weight = COALESCE(NULLIF(excluded.weight, ''), performers.weight),
                    build = COALESCE(NULLIF(excluded.build, ''), performers.build),
                    skin = COALESCE(NULLIF(excluded.skin, ''), performers.skin),
                    dick_size = COALESCE(NULLIF(excluded.dick_size, ''), performers.dick_size),
                    foreskin = COALESCE(NULLIF(excluded.foreskin, ''), performers.foreskin),
                    tattoos = COALESCE(NULLIF(excluded.tattoos, ''), performers.tattoos),
                    notes = COALESCE(NULLIF(excluded.notes, ''), performers.notes),
                    image_url = COALESCE(NULLIF(excluded.image_url, ''), performers.image_url),
                    scraped_at = CURRENT_TIMESTAMP
            """, (
                p["id"], p["name"], p.get("hair"), p.get("eyes"), p.get("body_hair"),
                p.get("facial_hair"), p.get("height"), p.get("weight"), p.get("build"),
                p.get("skin"), p.get("dick_size"), p.get("foreskin"), p.get("tattoos"),
                p.get("notes"), p.get("image_url")
            ))

            # Only rewrite the search index when the parse actually found a name;
            # reindexing an empty name would make the performer unsearchable.
            if p.get("name"):
                self.conn.execute("DELETE FROM performers_fts WHERE id = ?", (p["id"],))
                self.conn.execute("""
                    INSERT INTO performers_fts (id, name, tattoos, notes)
                    VALUES (?, ?, ?, ?)
                """, (p["id"], p["name"], p.get("tattoos"), p.get("notes")))

            self.conn.execute(
                "INSERT OR REPLACE INTO scrape_progress (item_type, item_id, status) VALUES (?, ?, ?)",
                ("performer", p["id"], status)
            )

    # --- User Customization: Tags, Ratings, Notes ---
    def get_user_tags(self) -> list[dict]:
        cur = self.conn.cursor()
        cur.execute("SELECT id, name, color, created_at FROM user_tags ORDER BY name ASC")
        return [{"id": r[0], "name": r[1], "color": r[2], "created_at": r[3]} for r in cur.fetchall()]

    def add_user_tag(self, name: str, color: str = "#f59e0b") -> dict:
        with self._write_lock, self.conn:
            cur = self.conn.cursor()
            cur.execute("INSERT OR IGNORE INTO user_tags (name, color) VALUES (?, ?)", (name.strip(), color))
            cur.execute("SELECT id, name, color FROM user_tags WHERE name = ?", (name.strip(),))
            r = cur.fetchone()
            return {"id": r[0], "name": r[1], "color": r[2]}

    def delete_user_tag(self, tag_id: int):
        with self._write_lock, self.conn:
            self.conn.execute("DELETE FROM user_tags WHERE id = ?", (tag_id,))
            self.conn.execute("DELETE FROM movie_user_tags WHERE tag_id = ?", (tag_id,))

    def get_movie_user_data(self, movie_id: int) -> dict:
        cur = self.conn.cursor()
        cur.execute("SELECT rating, status, notes, updated_at FROM user_movie_data WHERE movie_id = ?", (movie_id,))
        row = cur.fetchone()
        user_data = {
            "movie_id": movie_id,
            "rating": row[0] if row else None,
            "status": row[1] if row else None,
            "notes": row[2] if row else "",
            "updated_at": row[3] if row else None,
            "tags": []
        }
        cur.execute("""
            SELECT t.id, t.name, t.color 
            FROM user_tags t 
            JOIN movie_user_tags mt ON t.id = mt.tag_id 
            WHERE mt.movie_id = ?
        """, (movie_id,))
        user_data["tags"] = [{"id": r[0], "name": r[1], "color": r[2]} for r in cur.fetchall()]
        return user_data

    def set_movie_user_data(self, movie_id: int, rating: float | None = None, status: str | None = None, notes: str = "", tag_ids: list[int] | None = None):
        with self._write_lock, self.conn:
            self.conn.execute("""
                INSERT OR REPLACE INTO user_movie_data (movie_id, rating, status, notes, updated_at)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
            """, (movie_id, rating, status, notes))

            if tag_ids is not None:
                self.conn.execute("DELETE FROM movie_user_tags WHERE movie_id = ?", (movie_id,))
                for tid in tag_ids:
                    self.conn.execute("INSERT OR IGNORE INTO movie_user_tags (movie_id, tag_id) VALUES (?, ?)", (movie_id, tid))

    # --- Favorites (see schema.sql §9) ---
    #
    # One table covers all five item types. Studios and directors have no table of
    # their own, so their entity_key is the name itself; everything else keys on the
    # numeric id as a string. See the schema comment for why.
    FAVORITE_TYPES: tuple[str, ...] = ("movie", "performer", "studio", "director", "episode")

    def add_favorite(self, entity_type: str, entity_key: str):
        if entity_type not in self.FAVORITE_TYPES or not entity_key:
            return
        with self._write_lock, self.conn:
            self.conn.execute(
                "INSERT OR IGNORE INTO user_favorites (entity_type, entity_key) VALUES (?, ?)",
                (entity_type, str(entity_key)),
            )

    def remove_favorite(self, entity_type: str, entity_key: str):
        with self._write_lock, self.conn:
            self.conn.execute(
                "DELETE FROM user_favorites WHERE entity_type = ? AND entity_key = ?",
                (entity_type, str(entity_key)),
            )

    def is_favorite(self, entity_type: str, entity_key: str) -> bool:
        row = self.conn.execute(
            "SELECT 1 FROM user_favorites WHERE entity_type = ? AND entity_key = ?",
            (entity_type, str(entity_key)),
        ).fetchone()
        return row is not None

    def toggle_favorite(self, entity_type: str, entity_key: str) -> bool:
        """Flip one favorite. Returns True if it is favorited afterwards."""
        if entity_type not in self.FAVORITE_TYPES or not entity_key:
            return False
        if self.is_favorite(entity_type, entity_key):
            self.remove_favorite(entity_type, entity_key)
            return False
        self.add_favorite(entity_type, entity_key)
        return True

    def get_favorite_keys(self) -> dict[str, list[str]]:
        """Lightweight {type: [key, ...]} — enough for the UI to light up hearts."""
        out: dict[str, list[str]] = {t: [] for t in self.FAVORITE_TYPES}
        for etype, ekey in self.conn.execute(
            "SELECT entity_type, entity_key FROM user_favorites ORDER BY created_at DESC"
        ).fetchall():
            out.setdefault(etype, []).append(ekey)
        return out

    def get_favorites(self) -> dict[str, list[dict]]:
        """Favorited items grouped by type, each enriched with what the card needs.

        The favorites page has to come from here rather than filtering an in-memory
        list: once the full-site scrape lands there will be six figures of performers,
        so the client never holds them all.
        """
        cur = self.conn.cursor()
        out: dict[str, list[dict]] = {t: [] for t in self.FAVORITE_TYPES}

        cur.execute("""
            SELECT f.entity_key, f.created_at, m.title, m.release_year, m.studio_name,
                   m.cover_full, m.description_zh IS NOT NULL
            FROM user_favorites f JOIN movies m ON m.id = CAST(f.entity_key AS INTEGER)
            WHERE f.entity_type = 'movie' ORDER BY f.created_at DESC
        """)
        out["movie"] = [
            {"key": r[0], "created_at": r[1], "title": r[2], "release_year": r[3],
             "studio_name": r[4], "cover_full": r[5], "has_zh": bool(r[6])}
            for r in cur.fetchall()
        ]

        cur.execute("""
            SELECT f.entity_key, f.created_at, p.name, p.image_url
            FROM user_favorites f JOIN performers p ON p.id = CAST(f.entity_key AS INTEGER)
            WHERE f.entity_type = 'performer' ORDER BY f.created_at DESC
        """)
        out["performer"] = [
            {"key": r[0], "created_at": r[1], "name": r[2], "image_url": r[3]}
            for r in cur.fetchall()
        ]

        cur.execute("""
            SELECT f.entity_key, f.created_at, e.title, e.thumbnail_url, e.movie_id,
                   m.title, m.studio_name, e.description_zh IS NOT NULL
            FROM user_favorites f JOIN episodes e ON e.id = CAST(f.entity_key AS INTEGER)
            LEFT JOIN movies m ON m.id = e.movie_id
            WHERE f.entity_type = 'episode' ORDER BY f.created_at DESC
        """)
        out["episode"] = [
            {"key": r[0], "created_at": r[1], "title": r[2], "thumbnail_url": r[3],
             "movie_id": r[4], "movie_title": r[5], "studio_name": r[6], "has_zh": bool(r[7])}
            for r in cur.fetchall()
        ]

        # Studios / directors live only as columns on movies, so the card just needs
        # the name plus how many works we hold for it.
        for etype, column in (("studio", "studio_name"), ("director", "director_name")):
            cur.execute(f"""
                SELECT f.entity_key, f.created_at,
                       (SELECT COUNT(*) FROM movies WHERE {column} = f.entity_key)
                FROM user_favorites f WHERE f.entity_type = ? ORDER BY f.created_at DESC
            """, (etype,))
            out[etype] = [
                {"key": r[0], "created_at": r[1], "name": r[0], "works_count": r[2]}
                for r in cur.fetchall()
            ]

        return out

    def export_user_data(self) -> dict:
        """Export all user annotations (tags, ratings, status, notes) to a portable JSON-ready dict."""
        cur = self.conn.cursor()
        cur.execute("SELECT id, name, color, created_at FROM user_tags")
        tags = [{"id": r[0], "name": r[1], "color": r[2], "created_at": r[3]} for r in cur.fetchall()]

        cur.execute("SELECT movie_id, rating, status, notes, updated_at FROM user_movie_data")
        movie_data = []
        for r in cur.fetchall():
            mid = r[0]
            cur.execute("SELECT tag_id FROM movie_user_tags WHERE movie_id = ?", (mid,))
            assigned_tags = [t[0] for t in cur.fetchall()]
            movie_data.append({
                "movie_id": mid,
                "rating": r[1],
                "status": r[2],
                "notes": r[3],
                "updated_at": r[4],
                "tag_ids": assigned_tags
            })

        cur.execute("SELECT entity_type, entity_key, created_at FROM user_favorites")
        favorites = [
            {"entity_type": r[0], "entity_key": r[1], "created_at": r[2]}
            for r in cur.fetchall()
        ]

        return {
            "version": 2,
            "export_time": datetime.datetime.now().isoformat(),
            "tags": tags,
            "movie_user_data": movie_data,
            "favorites": favorites,
        }

    def import_user_data(self, data: dict) -> dict:
        """Merge/restore user data from JSON backup.

        Additive only: nothing is deleted, so a v1 backup (which has no
        "favorites" key) imports as zero favorites and leaves the existing ones
        alone. Merging is the right semantic here because the export is a partial
        backup — the caller may be restoring it into a library that has moved on.
        """
        tags_imported = 0
        movies_updated = 0
        favorites_imported = 0
        tag_id_map = {}  # old_id -> new_id
        with self._write_lock, self.conn:
            # 1. Tags
            for t in data.get("tags", []):
                self.conn.execute("INSERT OR IGNORE INTO user_tags (name, color) VALUES (?, ?)", (t["name"], t.get("color", "#f59e0b")))
                cur = self.conn.cursor()
                cur.execute("SELECT id FROM user_tags WHERE name = ?", (t["name"],))
                row = cur.fetchone()
                if row:
                    tag_id_map[t["id"]] = row[0]
                tags_imported += 1

            # 2. Movie user data
            for md in data.get("movie_user_data", []):
                mid = md["movie_id"]
                self.conn.execute("""
                    INSERT OR REPLACE INTO user_movie_data (movie_id, rating, status, notes, updated_at)
                    VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, (mid, md.get("rating"), md.get("status"), md.get("notes", "")))

                new_tag_ids = [tag_id_map[tid] for tid in md.get("tag_ids", []) if tid in tag_id_map]
                self.conn.execute("DELETE FROM movie_user_tags WHERE movie_id = ?", (mid,))
                for ntid in new_tag_ids:
                    self.conn.execute("INSERT OR IGNORE INTO movie_user_tags (movie_id, tag_id) VALUES (?, ?)", (mid, ntid))
                movies_updated += 1

            # 3. Favorites (v2+; absent in v1 backups). Validated here rather than
            # via add_favorite() because that would re-take the write lock.
            for fav in data.get("favorites", []):
                etype, ekey = fav.get("entity_type"), fav.get("entity_key")
                if etype not in self.FAVORITE_TYPES or not ekey:
                    continue
                self.conn.execute(
                    "INSERT OR IGNORE INTO user_favorites (entity_type, entity_key) VALUES (?, ?)",
                    (etype, str(ekey)),
                )
                favorites_imported += 1

        return {
            "tags_imported": tags_imported,
            "movies_updated": movies_updated,
            "favorites_imported": favorites_imported,
        }

    # --- Episode Works Queries (Separated from Movies) ---
    # Both episode queries below carry description_zh so the client can show the
    # translated synopsis without a second round-trip. The desktop build reads the
    # same column through its own EPISODE_SQL, so the two must stay in step.
    def get_performer_episodes(self, performer_id: int) -> list[dict]:
        cur = self.conn.cursor()
        cur.execute("""
            SELECT e.id, e.movie_id, e.title, e.thumbnail_url, e.description, e.description_zh,
                   e.action_notes, m.title as movie_title, m.studio_name, m.release_year
            FROM episodes e
            JOIN episode_performers ep ON e.id = ep.episode_id
            LEFT JOIN movies m ON e.movie_id = m.id
            WHERE ep.performer_id = ?
            ORDER BY m.release_year DESC, e.id DESC
        """, (performer_id,))
        eps = []
        for r in cur.fetchall():
            eps.append({
                "id": r[0],
                "movie_id": r[1],
                "title": r[2],
                "thumbnail_url": r[3],
                "description": r[4],
                "description_zh": r[5],
                "action_notes": r[6],
                "movie_title": r[7],
                "studio_name": r[8],
                "release_year": r[9]
            })
        return eps

    def get_studio_episodes(self, studio_name: str) -> list[dict]:
        cur = self.conn.cursor()
        cur.execute("""
            SELECT e.id, e.movie_id, e.title, e.thumbnail_url, e.description, e.description_zh,
                   e.action_notes, m.title as movie_title, m.studio_name, m.release_year
            FROM episodes e
            JOIN movies m ON e.movie_id = m.id
            WHERE m.studio_name = ?
            ORDER BY m.release_year DESC, e.id DESC
        """, (studio_name,))
        eps = []
        for r in cur.fetchall():
            eps.append({
                "id": r[0],
                "movie_id": r[1],
                "title": r[2],
                "thumbnail_url": r[3],
                "description": r[4],
                "description_zh": r[5],
                "action_notes": r[6],
                "movie_title": r[7],
                "studio_name": r[8],
                "release_year": r[9]
            })
        return eps

    def list_studios(self, query: str = "", sort: str = "works",
                     limit: int = 24, offset: int = 0) -> tuple[list[dict], int]:
        """One page of the studio library, plus how many studios match the search.

        Studios exist only as a column on `movies` — there is no table for them and
        no artwork — so both counts come from grouping that column. The episode
        count needs the LEFT JOIN rather than a second GROUP BY, because a studio
        whose films have no episodes must still be listed, with 0. joined row count
        is movies + episodes, and idx_movies_studio / idx_episodes_movie_id cover it.
        """
        where = "WHERE m.studio_name IS NOT NULL AND trim(m.studio_name) != ''"
        args: list = []
        if query:
            where += " AND m.studio_name LIKE ? ESCAPE '\\'"
            # The search box is free text, so % and _ must reach LIKE as literals.
            escaped = query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
            args.append(f"%{escaped}%")

        order = {
            "name": "m.studio_name COLLATE NOCASE ASC",
            "episodes": "episodes_count DESC, works_count DESC, m.studio_name COLLATE NOCASE ASC",
        }.get(sort, "works_count DESC, m.studio_name COLLATE NOCASE ASC")

        row = self.conn.execute(
            f"SELECT count(DISTINCT m.studio_name) FROM movies m {where}", args
        ).fetchone()
        total = row[0] if row else 0

        cur = self.conn.execute(f"""
            SELECT m.studio_name,
                   count(DISTINCT m.id) AS works_count,
                   count(e.id) AS episodes_count
            FROM movies m
            LEFT JOIN episodes e ON e.movie_id = m.id
            {where}
            GROUP BY m.studio_name
            ORDER BY {order}
            LIMIT ? OFFSET ?
        """, [*args, limit, offset])
        items = [
            {"name": r[0], "works_count": r[1], "episodes_count": r[2]}
            for r in cur.fetchall()
        ]
        return items, total

    def get_stats(self) -> dict[str, int]:
        cur = self.conn.cursor()
        stats = {}
        for table in ["movies", "performers", "episodes", "movie_performers", "episode_performers", "user_tags", "user_movie_data"]:
            try:
                cur.execute(f"SELECT COUNT(*) FROM {table}")
                stats[table] = cur.fetchone()[0]
            except Exception:
                stats[table] = 0

        cur.execute("SELECT COUNT(*) FROM scrape_progress WHERE item_type = 'movie' AND status = 200")
        stats["movies_scraped_ok"] = cur.fetchone()[0]

        cur.execute("SELECT COUNT(*) FROM scrape_progress WHERE item_type = 'movie' AND status = 404")
        stats["movies_scraped_404"] = cur.fetchone()[0]

        cur.execute("SELECT COUNT(*) FROM scrape_progress WHERE item_type = 'performer' AND status = 200")
        stats["performers_scraped_ok"] = cur.fetchone()[0]

        cur.execute("SELECT COUNT(*) FROM scrape_progress WHERE item_type = 'performer' AND status = 404")
        stats["performers_scraped_404"] = cur.fetchone()[0]

        cur.execute("SELECT COUNT(*) FROM scrape_progress WHERE item_type = 'movie' AND status = 500")
        stats["movies_failed_500"] = cur.fetchone()[0]

        cur.execute("SELECT COUNT(*) FROM performers WHERE image_url IS NOT NULL AND trim(image_url) != ''")
        stats["performers_with_image"] = cur.fetchone()[0]

        stats.update(self.get_translation_stats())

        return stats

    def close(self):
        self.conn.close()

