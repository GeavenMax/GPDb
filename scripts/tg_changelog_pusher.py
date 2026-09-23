#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Telegram Changelog Pusher
读取 CHANGELOG.md，将版本更新日志按照 Telegram Markdown 规范格式化，并推送到指定的 Telegram 频道。
支持由旧到新发布已有版本、记录发布状态防止重复、以及文件更新变动检测。
"""

import os
import sys
import re
import time
import json
import urllib.request
import urllib.parse
from datetime import datetime

DEFAULT_CHANGELOG_PATH = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "CHANGELOG.md"))
DEFAULT_BOT_TOKEN = os.environ.get("TG_BOT_TOKEN", "")
DEFAULT_CHAT_ID = os.environ.get("TG_CHAT_ID", "@gpdbnews")
STATE_FILE_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".changelog_pusher_state.json")
GITHUB_REPO_URL = "https://github.com/GeavenMax/GPDb"


def parse_semver(version_str):
    """
    解析语义化版本号用于比较排序。
    例如 'v2.4.1' -> (2, 4, 1)
    """
    clean_ver = re.sub(r'^[vV]', '', version_str.strip())
    # 提取数字部分
    parts = re.findall(r'\d+', clean_ver)
    return tuple(int(p) for p in parts) if parts else (0, 0, 0)


def extract_versions(changelog_content):
    """
    从 CHANGELOG.md 中提取所有规范的版本小节。
    返回列表: [
        {
            "version": "v1.0.0",
            "date": "2026-09-20",
            "title": "...",
            "body": "..."
        },
        ...
    ]
    按照版本由旧到新排序。
    """
    # 匹配规范的语义化版本标头: ## [v1.0.0] - 2026-09-20 或 ## [v1.0.0]
    pattern = r'## \[(v\d+\.\d+(?:\.\d+)?(?:-[^\]]+)?)\]\s*(?:-\s*(\d{4}-\d{2}-\d{2}))?\s*\n(.*?)(?=\n## |\Z)'
    matches = list(re.finditer(pattern, changelog_content, re.DOTALL))
    
    versions = []
    for m in matches:
        ver = m.group(1).strip()
        date = m.group(2).strip() if m.group(2) else ""
        body = m.group(3).strip()
        versions.append({
            "version": ver,
            "date": date,
            "body": body
        })
    
    # 按照语义化版本由旧到新排序
    versions.sort(key=lambda x: (parse_semver(x["version"]), x["date"]))
    return versions


def escape_telegram_markdown(text):
    """
    将普通的 Markdown 文本转换为 Telegram 标准 Markdown (Markdown V1/V2 友好):
    - **粗体** 转为 *粗体*
    - 相对链接转为绝对链接或友好文本
    - 针对不在代码块或链接内的下划线 '_' 进行转义，避免 Telegram 解析为未闭合的斜体
    """
    # 1. 转换相对链接 [title](./path) -> [title](https://github.com/GeavenMax/GPDb/blob/main/path)
    def link_repl(match):
        title = match.group(1).replace('`', '')
        url = match.group(2)
        if url.startswith('./') or url.startswith('../') or (not url.startswith('http://') and not url.startswith('https://')):
            clean_path = url.lstrip('./').lstrip('../')
            full_url = f"{GITHUB_REPO_URL}/blob/main/{clean_path}"
            return f"[{title}]({full_url})"
        return f"[{title}]({url})"

    text = re.sub(r'\[(.*?)\]\((.*?)\)', link_repl, text)

    # 2. 转换 **加粗** 为 *加粗* (Telegram MD 规范使用单星号)
    text = re.sub(r'\*\*(.*?)\*\*', r'*\1*', text)

    # 3. 对非代码、非链接内部的孤立下划线 '_' 进行转义
    # 按行内代码块 `...` 分词
    parts = re.split(r'(`[^`\n]+`)', text)
    escaped_parts = []
    for part in parts:
        if part.startswith('`') and part.endswith('`'):
            escaped_parts.append(part)
        else:
            # 在非代码部分，转义下划线
            escaped_parts.append(part.replace('_', r'\_'))
    text = ''.join(escaped_parts)

    return text


def format_version_message(version_info):
    """
    格式化单版本的 Telegram Markdown 推送消息
    """
    ver = version_info["version"]
    date = version_info["date"]
    body = version_info["body"]

    lines = []
    lines.append(f"📢 *GPDb 更新日志 · {ver}*")
    if date:
        lines.append(f"📅 *发布日期*：`{date}`")
    lines.append("────────────────────")

    for raw_line in body.split("\n"):
        line = raw_line.rstrip()
        if not line or line.strip() == "---":
            continue

        if line.startswith("### "):
            # 小节标题（例如：### 🚀 新增 (Added) 或 ### 🐛 修复 (Fixed)）
            header = line[4:].strip()
            lines.append(f"\n*{header}*")
        elif line.startswith("- **") or (line.startswith("- ") and not line.startswith("  - ")):
            # 一级列表项
            item_text = line[2:].strip()
            lines.append(f"• {item_text}")
        elif line.strip().startswith("- "):
            # 二级列表项
            sub_text = line.strip()[2:].strip()
            lines.append(f"   ▫️ {sub_text}")
        else:
            # 普通缩进或段落说明
            lines.append(f"   {line.strip()}")

    lines.append("\n" + "────────────────────")
    lines.append(f"🔗 [GitHub 仓库]({GITHUB_REPO_URL}) · [版本发布]({GITHUB_REPO_URL}/releases)")

    raw_message = "\n".join(lines)
    return escape_telegram_markdown(raw_message)


def send_telegram_message(bot_token, chat_id, text, disable_web_page_preview=True):
    """
    发送消息到指定的 Telegram 频道
    """
    url = f"https://api.telegram.org/bot{bot_token}/sendMessage"
    payload = {
        "chat_id": chat_id,
        "text": text,
        "parse_mode": "Markdown",
        "disable_web_page_preview": disable_web_page_preview
    }
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=data,
        headers={"Content-Type": "application/json"}
    )
    
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            result = json.loads(resp.read().decode("utf-8"))
            return result
    except urllib.error.HTTPError as e:
        error_body = e.read().decode("utf-8")
        print(f"[ERROR] HTTPError {e.code}: {error_body}", file=sys.stderr)
        # 如果是 Markdown 解析错误，尝试降级为纯文本发送
        if "can't parse entities" in error_body:
            print("[WARN] Retrying with plain text fallback...", file=sys.stderr)
            payload.pop("parse_mode", None)
            plain_data = json.dumps(payload).encode("utf-8")
            req_plain = urllib.request.Request(
                url,
                data=plain_data,
                headers={"Content-Type": "application/json"}
            )
            with urllib.request.urlopen(req_plain, timeout=15) as resp:
                return json.loads(resp.read().decode("utf-8"))
        raise


def load_state():
    if os.path.exists(STATE_FILE_PATH):
        try:
            with open(STATE_FILE_PATH, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception:
            pass
    return {"published_versions": [], "last_checked_mtime": 0}


def save_state(state):
    try:
        with open(STATE_FILE_PATH, "w", encoding="utf-8") as f:
            json.dump(state, f, ensure_ascii=False, indent=2)
    except Exception as e:
        print(f"[WARN] Failed to save state: {e}", file=sys.stderr)


def push_pending_versions(changelog_path, bot_token, chat_id, dry_run=False):
    """
    检查并由旧到新发布所有尚未发布的版本。
    """
    if not os.path.exists(changelog_path):
        print(f"[ERROR] Changelog file not found at: {changelog_path}", file=sys.stderr)
        return []

    with open(changelog_path, "r", encoding="utf-8") as f:
        content = f.read()

    versions = extract_versions(content)
    if not versions:
        print("[INFO] No versions found in CHANGELOG.md")
        return []

    state = load_state()
    published = set(state.get("published_versions", []))
    
    pending = [v for v in versions if v["version"] not in published]
    if not pending:
        print("[INFO] All versions have already been published. Nothing to do.")
        return []

    print(f"[INFO] Found {len(pending)} pending version(s) to publish: {[p['version'] for p in pending]}")
    published_now = []

    for item in pending:
        ver = item["version"]
        msg = format_version_message(item)
        print(f"\n--- Publishing {ver} ({len(msg)} chars) ---")
        
        if dry_run:
            print(msg)
            print("[DRY RUN] Message not actually sent.")
            published_now.append(ver)
        else:
            try:
                res = send_telegram_message(bot_token, chat_id, msg)
                if res.get("ok"):
                    print(f"[SUCCESS] Successfully published {ver} to {chat_id}")
                    published.add(ver)
                    state["published_versions"] = list(published)
                    state["last_updated"] = datetime.now().isoformat()
                    save_state(state)
                    published_now.append(ver)
                    # 避免 Telegram 频道的速率限制
                    time.sleep(2)
                else:
                    print(f"[ERROR] Telegram API error for {ver}: {res}", file=sys.stderr)
                    break
            except Exception as e:
                print(f"[ERROR] Failed to publish {ver}: {e}", file=sys.stderr)
                break

    return published_now


def watch_changelog(changelog_path, bot_token, chat_id, poll_interval=10):
    """
    持续监听 CHANGELOG.md 文件更新，发现新版本即刻发布。
    """
    print(f"[WATCHER] Starting watcher on: {changelog_path}")
    print(f"[WATCHER] Target channel: {chat_id}, Poll interval: {poll_interval}s")
    
    # 启动时首先发布一次所有未发布的历史版本
    push_pending_versions(changelog_path, bot_token, chat_id)
    
    last_mtime = os.path.getmtime(changelog_path) if os.path.exists(changelog_path) else 0

    while True:
        try:
            time.sleep(poll_interval)
            if not os.path.exists(changelog_path):
                continue

            current_mtime = os.path.getmtime(changelog_path)
            if current_mtime != last_mtime:
                print(f"\n[WATCHER] Detected file change at {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
                last_mtime = current_mtime
                push_pending_versions(changelog_path, bot_token, chat_id)

        except KeyboardInterrupt:
            print("\n[WATCHER] Stopped by user.")
            break
        except Exception as e:
            print(f"[WATCHER ERROR] {e}", file=sys.stderr)
            time.sleep(poll_interval)


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description="Telegram Changelog Pusher")
    parser.add_argument("--changelog", default=DEFAULT_CHANGELOG_PATH, help="Path to CHANGELOG.md")
    parser.add_argument("--token", default=DEFAULT_BOT_TOKEN, help="Telegram Bot Token")
    parser.add_argument("--chat", default=DEFAULT_CHAT_ID, help="Telegram Channel ID or Username")
    parser.add_argument("--dry-run", action="store_true", help="Print messages without sending")
    parser.add_argument("--watch", action="store_true", help="Keep watching for file changes")
    parser.add_argument("--interval", type=int, default=10, help="Poll interval in seconds for --watch")
    
    args = parser.parse_args()

    if not args.token and not args.dry_run:
        print("[ERROR] Telegram Bot Token is missing. Please set TG_BOT_TOKEN env var or pass --token.", file=sys.stderr)
        sys.exit(1)
    
    if args.watch:
        watch_changelog(args.changelog, args.token, args.chat, args.interval)
    else:
        push_pending_versions(args.changelog, args.token, args.chat, dry_run=args.dry_run)
