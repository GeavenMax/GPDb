
import re
import urllib.parse
import time
import threading
from pathlib import Path

def _NoArgs():
    class Args:
        provider = None
        model = None
        base_url = None
    return Args()

TRANSLATION_JOB = {
    "running": False,
    "summary": None, 
}

class TranslatePlugin:
    def __init__(self, handler):
        self.handler = handler

    def send_json(self, *args, **kwargs):
        self.handler.send_json(*args, **kwargs)

    def read_json_body(self):
        return self.handler.read_json_body()

def handle_translate_providers(self):
    """List the saved translation sources plus the presets the UI can prefill.

    Only `has_key` / `key_hint` cross the wire - the API key itself is never
    sent to the client, by design (see the project memory notes).
    """
    import translate
    self.send_json({
        "profiles": translate.list_profiles(),
        "presets": translate.PROVIDER_PRESETS,
        "config_file": translate.CONFIG_FILE.name,
    })

def handle_translate_provider_save(self):
    import translate
    body = self.read_json_body()
    name = (body.get("name") or "").strip()
    if not name:
        return self.send_json({"error": "缺少配置名称"}, status=400)
    ptype = (body.get("type") or "openai").strip()
    if ptype not in translate.PROVIDERS:
        return self.send_json(
            {"error": f"不支持的接口类型 '{ptype}'，可选: {', '.join(translate.PROVIDERS)}"},
            status=400,
        )
    try:
        translate.save_profile(name, {
            "type": ptype,
            "label": body.get("label") or name,
            "model": body.get("model") or "",
            "base_url": body.get("base_url") or "",
            # Absent key = "keep the stored one"; the UI is never given it back.
            "api_key": body.get("api_key") or "",
        })
        if body.get("active"):
            translate.set_active_profile(name)
    except (ValueError, OSError) as e:
        return self.send_json({"error": str(e)}, status=400)
    self.send_json({"success": True, "profiles": translate.list_profiles()})

def handle_translate_provider_activate(self):
    import translate
    body = self.read_json_body()
    try:
        translate.set_active_profile((body.get("name") or "").strip())
    except ValueError as e:
        return self.send_json({"error": str(e)}, status=404)
    self.send_json({"success": True, "profiles": translate.list_profiles()})

def handle_translate_provider_delete(self):
    import translate
    body = self.read_json_body()
    try:
        translate.delete_profile((body.get("name") or "").strip())
    except ValueError as e:
        return self.send_json({"error": str(e)}, status=404)
    self.send_json({"success": True, "profiles": translate.list_profiles()})

def handle_translate_provider_test(self):
    """Translate one short string through a source to prove the key works."""
    import translate
    body = self.read_json_body()
    name = (body.get("name") or "").strip() or None
    settings: dict = {"profile": name or ""}
    try:
        settings = translate.resolve_settings(_NoArgs(), profile=name)
        provider = translate.build_provider(settings)
    except SystemExit as e:
        return self.send_json({"error": str(e), "profile": settings.get("profile", "")},
                              status=400)

    sample = body.get("text") or "The biggest toys around and they all play with each others'."
    t0 = time.time()
    try:
        out = provider.translate([sample])
    except translate.TranslationError as e:
        return self.send_json({"error": str(e), "profile": settings["profile"]}, status=502)
    except Exception as e:
        return self.send_json({"error": f"{type(e).__name__}: {e}"}, status=502)

    zh = (out[0] if out else "").strip()
    self.send_json({
        "success": bool(zh),
        "profile": settings["profile"],
        "model": provider.model,
        "elapsed": round(time.time() - t0, 2),
        "source": sample,
        "result": zh,
        "error": "" if zh else "服务返回了空译文",
    })

def handle_translate_stats(self):
    with get_db_connection() as conn:
        payload = self._translation_stats(conn)
    payload.update(self._translation_configuration())
    payload["running"] = TRANSLATION_JOB["running"]
    payload["job"] = TRANSLATION_JOB["summary"]
    self.send_json(payload)

def handle_translate_movie(self, movie_id: int):
    """Translate one synopsis on demand, together with this movie's episode synopses.

    The episodes ride along in the same request on purpose: they are short and
    only ever shown inside this movie's modal, so batching them means opening a
    movie costs exactly one API call no matter how many episodes it has.
    """
    import translate

    conf = self._translation_configuration()
    if not conf["configured"]:
        return self.send_json({"error": conf["reason"]}, status=400)

    with get_db_connection() as conn:
        row = conn.execute(
            "SELECT id, title, description, description_zh FROM movies WHERE id = ?",
            (movie_id,),
        ).fetchone()
    if not row:
        return self.send_json({"error": "Movie not found"}, status=404)

    # A film whose synopsis is already translated contributes nothing here. That
    # case is the whole point of this being callable twice: this endpoint is also
    # how episodes get translated for a film that was translated before the
    # episodes were, so re-sending the synopsis would both waste a slot and
    # silently rewrite text the user has already read.
    movie_text = "" if (row["description_zh"] or "").strip() else (row["description"] or "").strip()

    db = DatabaseManager(str(DB_PATH))
    try:
        episodes = db.get_untranslated_episodes(movie_id=movie_id)
        texts = ([movie_text] if movie_text else []) + [e["description"].strip() for e in episodes]
        if not texts:
            return self.send_json({"error": "该影片没有简介可供翻译"}, status=400)

        try:
            provider = translate.build_provider(translate.resolve_settings(_NoArgs()))
            results = provider.translate(texts)
        except (translate.TranslationError, SystemExit) as e:
            return self.send_json({"error": str(e)}, status=502)

        # results is positionally aligned with texts: [movie, *episodes].
        offset = 1 if movie_text else 0
        movie_zh = ""
        if movie_text:
            movie_zh = (results[0] or "").strip()
            db.set_movie_translation(movie_id, movie_zh or None)

        translated_episodes = []
        for i, ep in enumerate(episodes):
            zh = (results[offset + i] or "").strip()
            if zh:
                db.set_episode_translation(ep["id"], zh)
            translated_episodes.append({"id": ep["id"], "description_zh": zh})
    finally:
        db.close()

    self.send_json({
        "id": movie_id,
        "description_zh": movie_zh,
        "episodes": translated_episodes,
    })

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
    profile = (body.get("profile") or "").strip() or None

    def job():
        import translate
        TRANSLATION_JOB["running"] = True
        TRANSLATION_JOB["summary"] = {"total": limit or 0, "done": 0, "status": "running"}
        try:
            db = DatabaseManager(str(DB_PATH))
            provider = translate.build_provider(
                translate.resolve_settings(_NoArgs(), profile=profile)
            )
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

def handle_translate_glossary_run(self):
    """Translate the whole attribute vocabulary in one API call. Supports dry_run."""
    body = self.read_json_body()
    import translate
    db = DatabaseManager(str(DB_PATH))
    try:
        result = translate.translate_glossary(db, dry_run=bool(body.get("dry_run")))
    finally:
        db.close()
    self.send_json(result)

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
    # A loopback endpoint (Ollama, llama.cpp) needs no key to count as usable.
    has_credentials = bool(settings["api_key"]) or translate.is_local_endpoint(settings["base_url"])
    configured = bool(provider in translate.PROVIDERS and has_credentials)
    model = settings["model"] or (
        translate.PROVIDERS[provider].DEFAULT_MODEL if provider in translate.PROVIDERS else ""
    )
    label = next((p["label"] for p in translate.list_profiles()
                  if p["name"] == settings.get("profile")), "")
    return {
        "configured": configured,
        "provider": provider,
        "profile": settings.get("profile", ""),
        "profile_label": label,
        "model": model,
        "reason": "" if configured else "未配置 API Key（在「设置 → 翻译服务来源」里添加或选择一套配置）",
    }

def _translation_configuration() -> dict:
    """Resolve translation settings without leaking the API key to the client."""
    try:
        import translate
    except ImportError as e:
        return {"configured": False, "provider": "", "model": "", "reason": str(e)}

    settings = translate.resolve_settings(_NoArgs())
    provider = (settings["provider"] or "").strip().lower()
    # A loopback endpoint (Ollama, llama.cpp) needs no key to count as usable.
    has_credentials = bool(settings["api_key"]) or translate.is_local_endpoint(settings["base_url"])
    configured = bool(provider in translate.PROVIDERS and has_credentials)
    model = settings["model"] or (
        translate.PROVIDERS[provider].DEFAULT_MODEL if provider in translate.PROVIDERS else ""
    )
    label = next((p["label"] for p in translate.list_profiles()
                  if p["name"] == settings.get("profile")), "")
    return {
        "configured": configured,
        "provider": provider,
        "profile": settings.get("profile", ""),
        "profile_label": label,
        "model": model,
        "reason": "" if configured else "未配置 API Key（在「设置 → 翻译服务来源」里添加或选择一套配置）",
    }



def handle_translate_routes_get(path, query_params, handler):
    plugin = TranslatePlugin(handler)
    if path == "/api/translate/stats":
        plugin.handle_translate_stats()
        return True
    if path == "/api/translate/providers":
        plugin.handle_translate_providers()
        return True
    return False

def handle_translate_routes_post(path, handler):
    plugin = TranslatePlugin(handler)
    if path == "/api/translate/glossary/run":
        plugin.handle_translate_glossary_run()
        return True
    if path == "/api/translate/run":
        plugin.handle_translate_run()
        return True
    if path == "/api/translate/providers/save":
        plugin.handle_translate_provider_save()
        return True
    if path == "/api/translate/providers/activate":
        plugin.handle_translate_provider_activate()
        return True
    if path == "/api/translate/providers/delete":
        plugin.handle_translate_provider_delete()
        return True
    if path == "/api/translate/providers/test":
        plugin.handle_translate_provider_test()
        return True
    
    translate_one = re.match(r"^/api/movies/(\d+)/translate$", path)
    if translate_one:
        plugin.handle_translate_movie(int(translate_one.group(1)))
        return True
    
    return False
