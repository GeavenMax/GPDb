#!/usr/bin/env python3
"""
GEVI Offline Database - Local HTTP/REST Server
Provides sub-millisecond local API endpoints for the Desktop Client (Vue 3 / Browser / LAN).
Requires zero external pip dependencies (built entirely on Python standard library & SQLite3 WAL).
"""

from __future__ import annotations
import json
import os
import re
import sqlite3
import threading
import urllib.parse
from http.server import HTTPServer, BaseHTTPRequestHandler
from socketserver import ThreadingMixIn
from pathlib import Path
import cache_images
from db_manager import DatabaseManager

BASE_DIR = Path(__file__).resolve().parent
DB_PATH = BASE_DIR / "gevi.db"

# Performer attribute columns exposed as filter facets, mapped to their API names.
# The site writes multi-value attributes with an inline <br /> separator
# (e.g. hair = "Brown<br />Blond"), so matching has to explode the value first.
PERFORMER_FACETS: dict[str, str] = {
    "build": "bodyType",
    "hair": "hair",
    "eyes": "eyes",
    "skin": "skin",
    "body_hair": "bodyHair",
    "facial_hair": "facialHair",
    "dick_size": "dickSize",
    "foreskin": "foreskin",
}

_BR_RE = re.compile(r"<br\s*/?>", re.IGNORECASE)


def split_facet_value(raw: str | None) -> list[str]:
    """Explode a stored attribute into its individual values."""
    if not raw:
        return []
    return [p.strip() for p in _BR_RE.split(raw) if p.strip()]


def facet_sql(column: str) -> str:
    """SQL expression rewriting a performer attribute into '|a|b|' form.

    Lets `LIKE '%|Brown|%'` match the token "Brown" inside "Brown<br />Blond"
    without a false hit on a hypothetical token "Dark Brown".
    """
    return (
        "'|' || REPLACE(REPLACE(REPLACE(COALESCE(p.{col}, ''),"
        " '<br />', '|'), '<br/>', '|'), '<br>', '|') || '|'"
    ).format(col=column)


class _NoArgs:
    """Stand-in for argparse output: lets translate.resolve_settings fall through
    to environment variables / translate_config.json for every field."""

    provider = None
    api_key = None
    model = None
    base_url = None


# State of the background batch-translation job started from the UI.
TRANSLATION_JOB: dict = {"running": False, "summary": {}}


def get_db_connection() -> sqlite3.Connection:
    conn = sqlite3.connect(str(DB_PATH), timeout=20.0)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA journal_mode = WAL;")
    conn.execute("PRAGMA synchronous = NORMAL;")
    return conn

class ThreadedHTTPServer(ThreadingMixIn, HTTPServer):
    daemon_threads = True

class GEVIRequestHandler(BaseHTTPRequestHandler):
    def send_cors_headers(self):
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")

    def do_OPTIONS(self):
        self.send_response(204)
        self.send_cors_headers()
        self.end_headers()

    def read_json_body(self) -> dict:
        content_length = int(self.headers.get("Content-Length", 0))
        if content_length == 0:
            return {}
        raw = self.rfile.read(content_length).decode("utf-8")
        try:
            return json.loads(raw)
        except Exception:
            return {}

    def send_file(self, file_path: Path, content_type: str = "image/jpeg"):
        if not file_path.exists() or file_path.stat().st_size == 0:
            self.send_json({"error": "File not found"}, status=404)
            return

        size = file_path.stat().st_size
        self.send_response(200)
        self.send_cors_headers()
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(size))
        self.send_header("Cache-Control", "public, max-age=31536000, immutable")
        self.end_headers()
        with open(file_path, "rb") as f:
            while chunk := f.read(65536):
                self.wfile.write(chunk)

    def send_json(self, data: any, status: int = 200):
        body = json.dumps(data, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_cors_headers()
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path.rstrip("/")
        query_params = urllib.parse.parse_qs(parsed.query)

        # Helper to extract single param
        def q(key: str, default: str = "") -> str:
            return query_params.get(key, [default])[0].strip()

        try:
            # 0. /api/images/cache?url=...
            if path == "/api/images/cache":
                return self.handle_image_cache(q("url"))

            # 0b. /api/cache/stats
            if path == "/api/cache/stats":
                return self.handle_cache_stats()

            # 1. /api/stats
            if path == "/api/stats":
                return self.handle_stats()

            # 2. /api/user/tags
            if path == "/api/user/tags":
                return self.handle_get_user_tags()

            # 2b. /api/user/export
            if path == "/api/user/export":
                return self.handle_user_export()

            # 2c. /api/movies/:id/user_data
            user_data_match = re.match(r"^/api/movies/(\d+)/user_data$", path)
            if user_data_match:
                return self.handle_get_movie_user_data(int(user_data_match.group(1)))

            # 3. /api/movies/:id
            movie_match = re.match(r"^/api/movies/(\d+)$", path)
            if movie_match:
                movie_id = int(movie_match.group(1))
                return self.handle_movie_detail(movie_id)

            # 4. /api/movies
            if path == "/api/movies":
                return self.handle_movies(query_params)

            # 5b. /api/performers/facets  (before the :id route, which is digits-only)
            if path == "/api/performers/facets":
                return self.handle_performer_facets()

            # 5. /api/performers/:id
            perf_match = re.match(r"^/api/performers/(\d+)$", path)
            if perf_match:
                perf_id = int(perf_match.group(1))
                return self.handle_performer_detail(perf_id)

            # 6. /api/performers
            if path == "/api/performers":
                return self.handle_performers(query_params)

            # 6b. /api/translate/stats
            if path == "/api/translate/stats":
                return self.handle_translate_stats()

            # 7. /api/studios/:name/works
            studio_works_match = re.match(r"^/api/studios/([^/]+)/works$", path)
            if studio_works_match:
                studio_name = urllib.parse.unquote(studio_works_match.group(1))
                return self.handle_studio_works(studio_name)

            # 8. /api/studios
            if path == "/api/studios":
                return self.handle_studios()

            # 9. /api/categories
            if path == "/api/categories":
                return self.handle_categories()

            # Not found
            self.send_json({"error": "Endpoint not found", "path": path}, status=404)

        except Exception as e:
            self.send_json({"error": str(e)}, status=500)

    def do_POST(self):
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path.rstrip("/")

        try:
            if path == "/api/sync":
                return self.handle_sync()

            if path == "/api/cache/clear":
                return self.handle_cache_clear()

            if path == "/api/cache/download_all":
                return self.handle_cache_download_all()

            if path == "/api/user/tags":
                return self.handle_create_user_tag()

            delete_tag_match = re.match(r"^/api/user/tags/(\d+)/delete$", path)
            if delete_tag_match:
                return self.handle_delete_user_tag(int(delete_tag_match.group(1)))

            user_data_match = re.match(r"^/api/movies/(\d+)/user_data$", path)
            if user_data_match:
                return self.handle_save_movie_user_data(int(user_data_match.group(1)))

            if path == "/api/user/import":
                return self.handle_user_import()

            if path == "/api/translate/run":
                return self.handle_translate_run()

            translate_one = re.match(r"^/api/movies/(\d+)/translate$", path)
            if translate_one:
                return self.handle_translate_movie(int(translate_one.group(1)))

            self.send_json({"error": "Endpoint not found"}, status=404)
        except Exception as e:
            self.send_json({"error": str(e)}, status=500)

    # -------------------------------------------------------------
    # Handlers
    # -------------------------------------------------------------
    def handle_stats(self):
        with get_db_connection() as conn:
            def count(tbl: str) -> int:
                row = conn.execute(f"SELECT count(*) FROM {tbl}").fetchone()
                return row[0] if row else 0

            def prog_count(t: str, s: int) -> int:
                row = conn.execute("SELECT count(*) FROM scrape_progress WHERE item_type = ? AND status = ?", (t, s)).fetchone()
                return row[0] if row else 0

            data = {
                "movies": count("movies"),
                "performers": count("performers"),
                "episodes": count("episodes"),
                "movie_performers": count("movie_performers"),
                "movies_scraped_ok": prog_count("movie", 200),
                "movies_scraped_404": prog_count("movie", 404),
                "movies_failed_500": prog_count("movie", 500),
                "performers_scraped_ok": prog_count("performer", 200),
                "performers_scraped_404": prog_count("performer", 404),
                "performers_with_image": conn.execute(
                    "SELECT count(*) FROM performers WHERE image_url IS NOT NULL AND trim(image_url) != ''"
                ).fetchone()[0],
            }
            data.update(self._translation_stats(conn))
            data["translation_configured"] = self._translation_configuration()["configured"]
            self.send_json(data)

    @staticmethod
    def _translation_stats(conn) -> dict:
        total = conn.execute(
            "SELECT count(*) FROM movies WHERE description IS NOT NULL AND trim(description) != ''"
        ).fetchone()[0]
        done = conn.execute(
            "SELECT count(*) FROM movies WHERE description_zh IS NOT NULL AND trim(description_zh) != ''"
        ).fetchone()[0]
        failed = conn.execute(
            "SELECT count(*) FROM movies WHERE (description_zh IS NULL OR trim(description_zh) = '')"
            " AND COALESCE(translation_attempts, 0) >= 3"
            " AND description IS NOT NULL AND trim(description) != ''"
        ).fetchone()[0]
        return {
            "translation_total": total,
            "translation_done": done,
            "translation_failed": failed,
            "translation_pending": max(0, total - done - failed),
        }

    @staticmethod
    def _translation_configuration() -> dict:
        """Resolve translation settings without leaking the API key to the client."""
        try:
            import translate
        except ImportError as e:
            return {"configured": False, "provider": "", "model": "", "reason": str(e)}

        settings = translate.resolve_settings(_NoArgs())
        provider = (settings["provider"] or "").strip().lower()
        configured = bool(provider in translate.PROVIDERS and settings["api_key"])
        model = settings["model"] or (
            translate.PROVIDERS[provider].DEFAULT_MODEL if provider in translate.PROVIDERS else ""
        )
        return {
            "configured": configured,
            "provider": provider,
            "model": model,
            "reason": "" if configured else "未配置 API Key（设置 GEVI_LLM_API_KEY 或 translate_config.json）",
        }

    def handle_movies(self, params: dict):
        q = params.get("query", [""])[0].strip()
        studio = params.get("studio", [""])[0].strip()
        category = params.get("category", [""])[0].strip()
        year_min = params.get("yearMin", [""])[0].strip()
        year_max = params.get("yearMax", [""])[0].strip()
        sort_by = params.get("sortBy", ["year_desc"])[0].strip()
        page = max(1, int(params.get("page", [1])[0]))
        page_size = max(1, min(500, int(params.get("pageSize", [24])[0])))
        offset = (page - 1) * page_size

        with get_db_connection() as conn:
            conditions = []
            args = []

            # Full-text search or keyword search. The Chinese translation is only
            # in `description_zh`, which the FTS index does not cover, so a LIKE
            # scan is OR-ed in alongside any FTS hit.
            if q:
                clean_q = re.sub(r'[\'"*^:]', '', q)
                like_q = f"%{clean_q}%"
                numeric_id = int(clean_q) if clean_q.isdigit() else -1

                # Check if FTS5 has matches
                try:
                    fts_test = conn.execute(
                        "SELECT id FROM movies_fts WHERE movies_fts MATCH ? LIMIT 1",
                        (f"{clean_q}*",)
                    ).fetchone()
                except sqlite3.OperationalError:
                    fts_test = None  # malformed FTS query syntax

                if fts_test:
                    conditions.append(
                        "(m.id IN (SELECT id FROM movies_fts WHERE movies_fts MATCH ?)"
                        " OR m.description_zh LIKE ? OR m.id = ?)"
                    )
                    args.extend([f"{clean_q}*", like_q, numeric_id])
                else:
                    conditions.append(
                        "(m.title LIKE ? OR m.studio_name LIKE ? OR m.director_name LIKE ?"
                        " OR m.description_zh LIKE ? OR m.id = ?)"
                    )
                    args.extend([like_q, like_q, like_q, like_q, numeric_id])

            if studio:
                conditions.append("m.studio_name = ?")
                args.append(studio)

            if category:
                conditions.append("m.category = ?")
                args.append(category)

            if year_min and year_min.isdigit():
                conditions.append("m.release_year >= ?")
                args.append(int(year_min))

            if year_max and year_max.isdigit():
                conditions.append("m.release_year <= ?")
                args.append(int(year_max))

            where_clause = f"WHERE {' AND '.join(conditions)}" if conditions else ""

            # Sorting
            sort_map = {
                "year_desc": "m.release_year DESC, m.id DESC",
                "year_asc": "m.release_year ASC, m.id ASC",
                "title_asc": "m.title ASC",
                "id_desc": "m.id DESC",
            }
            order_by = sort_map.get(sort_by, "m.release_year DESC, m.id DESC")

            # Count total
            count_sql = f"SELECT count(*) FROM movies m {where_clause}"
            total = conn.execute(count_sql, args).fetchone()[0]

            # Select page items
            select_sql = f"""
                SELECT m.id, m.title, m.studio_id, m.studio_name, m.release_year,
                       m.duration_mins, m.category, m.rating, m.movie_type,
                       m.description, m.description_zh, m.cover_icon, m.cover_full,
                       m.covers_json, m.director_id, m.director_name
                FROM movies m
                {where_clause}
                ORDER BY {order_by}
                LIMIT ? OFFSET ?
            """
            rows = conn.execute(select_sql, args + [page_size, offset]).fetchall()

            # Batch fetch performers for these movies
            movie_ids = [r["id"] for r in rows]
            perf_map = {}
            user_data_map = {}
            if movie_ids:
                placeholders = ",".join("?" * len(movie_ids))
                perf_rows = conn.execute(
                    f"SELECT movie_id, performer_id, performer_name FROM movie_performers WHERE movie_id IN ({placeholders})",
                    movie_ids
                ).fetchall()
                for pr in perf_rows:
                    mid = pr["movie_id"]
                    if mid not in perf_map:
                        perf_map[mid] = []
                    perf_map[mid].append({"id": pr["performer_id"], "name": pr["performer_name"]})

                # Batch fetch user data (rating, status, tags)
                ud_rows = conn.execute(
                    f"SELECT movie_id, rating, status, notes FROM user_movie_data WHERE movie_id IN ({placeholders})",
                    movie_ids
                ).fetchall()
                for ur in ud_rows:
                    user_data_map[ur["movie_id"]] = {
                        "rating": ur["rating"],
                        "status": ur["status"],
                        "notes": ur["notes"],
                        "tags": []
                    }
                try:
                    tag_rows = conn.execute(
                        f"""SELECT mt.movie_id, t.id, t.name, t.color 
                            FROM movie_user_tags mt 
                            JOIN user_tags t ON mt.tag_id = t.id 
                            WHERE mt.movie_id IN ({placeholders})""",
                        movie_ids
                    ).fetchall()
                    for tr in tag_rows:
                        mid = tr["movie_id"]
                        if mid not in user_data_map:
                            user_data_map[mid] = {"rating": None, "status": None, "notes": "", "tags": []}
                        user_data_map[mid]["tags"].append({"id": tr["id"], "name": tr["name"], "color": tr["color"]})
                except Exception:
                    pass

            items = []
            for r in rows:
                m_dict = dict(r)
                m_dict["performers"] = perf_map.get(m_dict["id"], [])
                m_dict["userData"] = user_data_map.get(m_dict["id"], None)
                if m_dict.get("covers_json"):
                    try:
                        m_dict["covers"] = json.loads(m_dict["covers_json"])
                    except Exception:
                        m_dict["covers"] = [m_dict["cover_full"]] if m_dict.get("cover_full") else []
                else:
                    m_dict["covers"] = [m_dict["cover_full"]] if m_dict.get("cover_full") else []
                items.append(m_dict)

            self.send_json({"items": items, "total": total})

    def handle_movie_detail(self, movie_id: int):
        with get_db_connection() as conn:
            row = conn.execute("SELECT * FROM movies WHERE id = ?", (movie_id,)).fetchone()
            if not row:
                return self.send_json({"error": "Movie not found"}, status=404)

            movie = dict(row)
            if movie.get("covers_json"):
                try:
                    movie["covers"] = json.loads(movie["covers_json"])
                except Exception:
                    movie["covers"] = [movie["cover_full"]] if movie.get("cover_full") else []
            else:
                movie["covers"] = [movie["cover_full"]] if movie.get("cover_full") else []

            # Performers (LEFT JOIN so cast members without a scraped profile still appear)
            p_rows = conn.execute(
                """SELECT mp.performer_id AS id,
                          COALESCE(p.name, mp.performer_name) AS name,
                          p.image_url AS image_url
                   FROM movie_performers mp
                   LEFT JOIN performers p ON p.id = mp.performer_id
                   WHERE mp.movie_id = ?""",
                (movie_id,)
            ).fetchall()
            movie["performers"] = [dict(p) for p in p_rows]

            # Episodes / Scenes
            ep_rows = conn.execute(
                "SELECT id, movie_id, title, thumbnail_url, description, action_notes FROM episodes WHERE movie_id = ? ORDER BY id ASC",
                (movie_id,)
            ).fetchall()
            movie["episodes"] = [dict(ep) for ep in ep_rows]

            # User data
            db = DatabaseManager(str(DB_PATH))
            movie["userData"] = db.get_movie_user_data(movie_id)
            db.close()

            self.send_json(movie)

    def handle_performers(self, params: dict):
        q = params.get("query", [""])[0].strip()
        page = max(1, int(params.get("page", [1])[0]))
        page_size = max(1, min(100, int(params.get("pageSize", [24])[0])))
        offset = (page - 1) * page_size
        has_image = params.get("hasImage", [""])[0].strip() == "1"
        min_movies = params.get("minMovies", [""])[0].strip()
        max_movies = params.get("maxMovies", [""])[0].strip()
        sort_by = params.get("sortBy", ["movies_desc"])[0].strip()

        with get_db_connection() as conn:
            conditions = []
            args = []

            if q:
                clean_q = re.sub(r'[\'"*^:]', '', q)
                conditions.append("(p.name LIKE ? OR p.id = ?)")
                like_q = f"%{clean_q}%"
                args.extend([like_q, int(clean_q) if clean_q.isdigit() else -1])

            # Attribute facets. Multiple values for one attribute are OR-ed
            # (Blond OR Brown); different attributes are AND-ed together.
            for column, api_name in PERFORMER_FACETS.items():
                values = [v.strip() for v in params.get(api_name, []) if v.strip()]
                if not values:
                    continue
                expr = facet_sql(column)
                ors = []
                for v in values:
                    # Facet values are site-controlled labels; strip LIKE wildcards
                    # so a stray % cannot widen the match.
                    safe = v.replace("%", "").replace("_", "")
                    ors.append(f"{expr} LIKE ?")
                    args.append(f"%|{safe}|%")
                conditions.append("(" + " OR ".join(ors) + ")")

            if has_image:
                conditions.append("(p.image_url IS NOT NULL AND trim(p.image_url) != '')")

            # movies_count is a correlated subquery, so the bounds are applied in the
            # outer query rather than as a HAVING on a GROUP BY.
            count_expr = "(SELECT count(*) FROM movie_performers mp WHERE mp.performer_id = p.id)"
            if min_movies.isdigit():
                conditions.append(f"{count_expr} >= ?")
                args.append(int(min_movies))
            if max_movies.isdigit():
                conditions.append(f"{count_expr} <= ?")
                args.append(int(max_movies))

            where_clause = f"WHERE {' AND '.join(conditions)}" if conditions else ""

            sort_map = {
                "movies_desc": f"{count_expr} DESC, p.id DESC",
                "movies_asc": f"{count_expr} ASC, p.id ASC",
                "name_asc": "p.name ASC",
                "id_desc": "p.id DESC",
                "id_asc": "p.id ASC",
            }
            order_by = sort_map.get(sort_by, sort_map["movies_desc"])

            # Count
            count_sql = f"SELECT count(*) FROM performers p {where_clause}"
            total = conn.execute(count_sql, args).fetchone()[0]

            # List with movies_count
            select_sql = f"""
                SELECT p.*, {count_expr} AS movies_count
                FROM performers p
                {where_clause}
                ORDER BY {order_by}
                LIMIT ? OFFSET ?
            """
            rows = conn.execute(select_sql, args + [page_size, offset]).fetchall()
            items = [dict(r) for r in rows]

            self.send_json({"items": items, "total": total})

    def handle_performer_facets(self):
        """Distinct attribute values with counts, so the UI only offers real options.

        Aggregation happens in Python: multi-value attributes share a column and
        only a small slice of performers has been detail-scraped so far.
        """
        with get_db_connection() as conn:
            columns = ", ".join(PERFORMER_FACETS.keys())
            rows = conn.execute(f"SELECT {columns} FROM performers").fetchall()

            facets: dict[str, dict[str, int]] = {api: {} for api in PERFORMER_FACETS.values()}
            for row in rows:
                for column, api_name in PERFORMER_FACETS.items():
                    for value in split_facet_value(row[column]):
                        facets[api_name][value] = facets[api_name].get(value, 0) + 1

            payload = {
                api_name: sorted(
                    ({"value": v, "count": n} for v, n in counts.items()),
                    key=lambda x: (-x["count"], x["value"]),
                )
                for api_name, counts in facets.items()
            }

            total = conn.execute("SELECT count(*) FROM performers").fetchone()[0]
            with_image = conn.execute(
                "SELECT count(*) FROM performers WHERE image_url IS NOT NULL AND trim(image_url) != ''"
            ).fetchone()[0]

            self.send_json({
                "facets": payload,
                "total": total,
                "withImage": with_image,
                "enriched": sum(1 for r in rows if any(r[c] for c in PERFORMER_FACETS)),
            })

    def handle_performer_detail(self, perf_id: int):
        with get_db_connection() as conn:
            row = conn.execute("SELECT * FROM performers WHERE id = ?", (perf_id,)).fetchone()
            if not row:
                return self.send_json({"error": "Performer not found"}, status=404)

            perf = dict(row)
            # Multi-value attributes arrive as "Brown<br />Blond"; expose them exploded
            # so the client never has to parse markup.
            perf["attributes"] = {
                api_name: split_facet_value(perf.get(column))
                for column, api_name in PERFORMER_FACETS.items()
            }

            # Feature Movies starring this performer
            m_rows = conn.execute("""
                SELECT m.id, m.title, m.studio_id, m.studio_name, m.release_year,
                       m.duration_mins, m.category, m.rating, m.cover_icon, m.cover_full
                FROM movies m
                JOIN movie_performers mp ON m.id = mp.movie_id
                WHERE mp.performer_id = ?
                ORDER BY m.release_year DESC, m.id DESC
            """, (perf_id,)).fetchall()

            perf["movies"] = [dict(m) for m in m_rows]
            perf["movies_count"] = len(perf["movies"])

            # Episodes / Scenes starring this performer (Separated from feature movies)
            db = DatabaseManager(str(DB_PATH))
            perf["episodes"] = db.get_performer_episodes(perf_id)
            perf["episodes_count"] = len(perf["episodes"])
            db.close()

            self.send_json(perf)

    def handle_studio_works(self, studio_name: str):
        with get_db_connection() as conn:
            m_rows = conn.execute("""
                SELECT m.id, m.title, m.studio_id, m.studio_name, m.release_year,
                       m.duration_mins, m.category, m.rating, m.cover_icon, m.cover_full
                FROM movies m
                WHERE m.studio_name = ?
                ORDER BY m.release_year DESC, m.id DESC
            """, (studio_name,)).fetchall()

            db = DatabaseManager(str(DB_PATH))
            eps = db.get_studio_episodes(studio_name)
            db.close()

            self.send_json({
                "studio_name": studio_name,
                "movies": [dict(m) for m in m_rows],
                "movies_count": len(m_rows),
                "episodes": eps,
                "episodes_count": len(eps)
            })

    def handle_image_cache(self, url: str):
        if not url:
            return self.send_json({"error": "Missing url parameter"}, status=400)
        local_path = cache_images.get_cache_path(url)
        if not local_path.exists() or local_path.stat().st_size < 100:
            ok = cache_images.download_image(url)
            if not ok:
                return self.send_json({"error": "Failed to download image", "url": url}, status=502)

        ct = "image/jpeg"
        if url.lower().endswith(".png"):
            ct = "image/png"
        elif url.lower().endswith(".webp"):
            ct = "image/webp"
        self.send_file(local_path, ct)

    def handle_cache_stats(self):
        self.send_json(cache_images.get_cache_stats())

    def handle_cache_clear(self):
        cleared = cache_images.clear_cache()
        self.send_json({"success": True, "cleared_files": cleared})

    def handle_cache_download_all(self):
        body = self.read_json_body()
        mode = body.get("mode") or "all"
        if mode not in ("all", "covers", "episodes", "performers"):
            mode = "all"
        t = threading.Thread(
            target=cache_images.run_batch_download,
            kwargs={"mode": mode, "concurrency": 6},
            daemon=True,
        )
        t.start()
        self.send_json({"success": True, "message": f"Batch cache download ({mode}) started in background"})

    # --- Machine translation (issue #4: LLM API, key supplied by the user) ---

    def handle_translate_stats(self):
        with get_db_connection() as conn:
            payload = self._translation_stats(conn)
        payload.update(self._translation_configuration())
        payload["running"] = TRANSLATION_JOB["running"]
        payload["job"] = TRANSLATION_JOB["summary"]
        self.send_json(payload)

    def handle_translate_movie(self, movie_id: int):
        """Translate a single synopsis on demand (used by the detail modal)."""
        import translate

        conf = self._translation_configuration()
        if not conf["configured"]:
            return self.send_json({"error": conf["reason"]}, status=400)

        with get_db_connection() as conn:
            row = conn.execute(
                "SELECT id, title, description, description_zh FROM movies WHERE id = ?", (movie_id,)
            ).fetchone()
        if not row:
            return self.send_json({"error": "Movie not found"}, status=404)

        text = (row["description"] or "").strip()
        if not text:
            return self.send_json({"error": "该影片没有简介可供翻译"}, status=400)

        try:
            provider = translate.build_provider(translate.resolve_settings(_NoArgs()))
            result = provider.translate([text])
        except (translate.TranslationError, SystemExit) as e:
            return self.send_json({"error": str(e)}, status=502)

        zh = (result[0] if result else "").strip()
        db = DatabaseManager(str(DB_PATH))
        if zh:
            db.set_movie_translation(movie_id, zh)
        else:
            db.set_movie_translation(movie_id, None)
        db.close()

        self.send_json({"id": movie_id, "description_zh": zh})

    def handle_translate_run(self):
        """Kick off a batch translation in the background."""
        if TRANSLATION_JOB["running"]:
            return self.send_json(
                {"success": False, "error": "已有翻译任务在运行中", "job": TRANSLATION_JOB["summary"]},
                status=409,
            )

        conf = self._translation_configuration()
        if not conf["configured"]:
            return self.send_json({"success": False, "error": conf["reason"]}, status=400)

        body = self.read_json_body()
        limit = int(body.get("limit") or 0) or None
        batch_size = max(1, min(50, int(body.get("batchSize") or 20)))
        workers = max(1, min(16, int(body.get("workers") or 4)))

        def job():
            import translate
            TRANSLATION_JOB["running"] = True
            TRANSLATION_JOB["summary"] = {"total": limit or 0, "done": 0, "status": "running"}
            try:
                db = DatabaseManager(str(DB_PATH))
                provider = translate.build_provider(translate.resolve_settings(_NoArgs()))
                translate.run_translation(
                    db=db,
                    provider=provider,
                    limit=limit,
                    batch_size=batch_size,
                    workers=workers,
                    dry_run=False,
                )
                with get_db_connection() as conn:
                    TRANSLATION_JOB["summary"] = {"status": "finished", **self._translation_stats(conn)}
                db.close()
            except Exception as e:
                TRANSLATION_JOB["summary"] = {"status": "error", "error": str(e)}
            finally:
                TRANSLATION_JOB["running"] = False

        threading.Thread(target=job, daemon=True).start()
        self.send_json({"success": True, "message": "翻译任务已在后台启动"})

    def handle_get_user_tags(self):
        db = DatabaseManager(str(DB_PATH))
        tags = db.get_user_tags()
        db.close()
        self.send_json(tags)

    def handle_create_user_tag(self):
        body = self.read_json_body()
        name = body.get("name", "").strip()
        color = body.get("color", "#f59e0b").strip()
        if not name:
            return self.send_json({"error": "Tag name is required"}, status=400)
        db = DatabaseManager(str(DB_PATH))
        tag = db.add_user_tag(name, color)
        db.close()
        self.send_json(tag)

    def handle_delete_user_tag(self, tag_id: int):
        db = DatabaseManager(str(DB_PATH))
        db.delete_user_tag(tag_id)
        db.close()
        self.send_json({"success": True})

    def handle_get_movie_user_data(self, movie_id: int):
        db = DatabaseManager(str(DB_PATH))
        ud = db.get_movie_user_data(movie_id)
        db.close()
        self.send_json(ud)

    def handle_save_movie_user_data(self, movie_id: int):
        body = self.read_json_body()
        rating = body.get("rating")
        status = body.get("status")
        notes = body.get("notes", "")
        tag_ids = body.get("tag_ids")
        db = DatabaseManager(str(DB_PATH))
        db.set_movie_user_data(movie_id, rating=rating, status=status, notes=notes, tag_ids=tag_ids)
        ud = db.get_movie_user_data(movie_id)
        db.close()
        self.send_json(ud)

    def handle_user_export(self):
        db = DatabaseManager(str(DB_PATH))
        exp = db.export_user_data()
        db.close()
        self.send_json(exp)

    def handle_user_import(self):
        body = self.read_json_body()
        db = DatabaseManager(str(DB_PATH))
        res = db.import_user_data(body)
        db.close()
        self.send_json(res)

    def handle_studios(self):
        with get_db_connection() as conn:
            rows = conn.execute("""
                SELECT studio_name, count(*) as count
                FROM movies
                WHERE studio_name IS NOT NULL AND trim(studio_name) != ''
                GROUP BY studio_name
                ORDER BY count DESC
                LIMIT 200
            """).fetchall()
            studios = [r["studio_name"] for r in rows]
            self.send_json(studios)

    def handle_categories(self):
        with get_db_connection() as conn:
            rows = conn.execute("""
                SELECT category, count(*) as count
                FROM movies
                WHERE category IS NOT NULL AND trim(category) != ''
                GROUP BY category
                ORDER BY count DESC
            """).fetchall()
            categories = [r["category"] for r in rows]
            self.send_json(categories)

    def handle_sync(self):
        # Trigger incremental sync
        import subprocess
        sync_script = BASE_DIR / "sync_gevi.py"
        try:
            res = subprocess.run(
                ["python3", str(sync_script), "--probe-count", "15"],
                capture_output=True,
                text=True,
                timeout=45
            )
            # Parse output for count of new items
            new_movies = 0
            new_perf = 0
            for line in res.stdout.splitlines():
                if "Saved" in line and "movies" in line:
                    match = re.search(r"Saved (\d+) new movies", line)
                    if match:
                        new_movies = int(match.group(1))
                if "Saved" in line and "performers" in line:
                    match = re.search(r"Saved (\d+) new performers", line)
                    if match:
                        new_perf = int(match.group(1))

            self.send_json({
                "success": True,
                "newMovies": new_movies,
                "newPerformers": new_perf,
                "output": res.stdout[-500:] if res.stdout else ""
            })
        except Exception as e:
            self.send_json({"success": False, "error": str(e)}, status=500)

def main():
    port = int(os.environ.get("PORT", 8787))
    server = ThreadedHTTPServer(("127.0.0.1", port), GEVIRequestHandler)
    print(f"[*] GEVI Offline API Server running at http://127.0.0.1:{port}/")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n[*] Server stopping...")
        server.server_close()

if __name__ == "__main__":
    main()
