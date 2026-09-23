
import re
import urllib.parse
from pathlib import Path

class SyncPlugin:
    def __init__(self, handler):
        self.handler = handler

    def send_json(self, *args, **kwargs):
        self.handler.send_json(*args, **kwargs)

def handle_sync(self):
    # Trigger incremental sync
    import subprocess
    sync_script = BASE_DIR / "sync_gpdb.py"
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



def handle_sync_routes_post(path, handler):
    plugin = SyncPlugin(handler)
    if path == "/api/sync":
        plugin.handle_sync()
        return True
    return False
