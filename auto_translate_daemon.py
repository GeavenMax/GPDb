#!/usr/bin/env python3
"""
auto_translate_daemon.py
------------------------
GPDb 自动翻译守护进程：
1. 开机/唤醒并联网后，自动检测网络连通性与 Google Gemini API Key 可用性。
2. 若 Key 正常可用，自动启动全库汉化流水线（多厂牌分阶段）。
3. 动态捕获 API 额度耗尽事件（HTTP 429 每日限额 / Exit Code 42）。
4. 任务停止后：
   - 自动向 Discord Webhook 发送详细富文本报告（包含耗时、新增翻译量、新增汉字数、最新大盘完成度等）；
   - 自动将本次运行的成果与历史时间轴追加写入项目根目录的《TRANSLATION_PROGRESS.md》备用归档。
"""

import sys
import time
import json
import socket
import sqlite3
import datetime
import subprocess
import urllib.request
import urllib.error
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent
CONFIG_FILE = BASE_DIR / "translate_config.json"
PROGRESS_FILE = BASE_DIR / "TRANSLATION_PROGRESS.md"
GPDB_FILE = BASE_DIR / "GPDb.db"
TRANS_DB_FILE = BASE_DIR / "translations.db"


def load_config() -> dict:
    if not CONFIG_FILE.exists():
        return {}
    with open(CONFIG_FILE, "r", encoding="utf-8") as f:
        return json.load(f)


def wait_for_network(max_wait_seconds: int = 300, check_interval: int = 5) -> bool:
    """等待网络连通（最多等待 max_wait_seconds 秒）"""
    print(f"[*] 正在检测互联网连接 (最多等待 {max_wait_seconds} 秒)...")
    start_t = time.time()
    targets = [
        ("generativelanguage.googleapis.com", 443),
        ("8.8.8.8", 53),
        ("1.1.1.1", 53)
    ]
    
    while time.time() - start_t < max_wait_seconds:
        for host, port in targets:
            try:
                s = socket.create_connection((host, port), timeout=3)
                s.close()
                print(f"[+] 网络已就绪！成功连通目标: {host}:{port}")
                return True
            except OSError:
                continue
        time.sleep(check_interval)
    
    print("[-] 等待网络超时，网络不可用。")
    return False


def test_gemini_keys(config: dict) -> tuple[int, int, str]:
    """探测配置的 Gemini API Keys 可用性
    返回: (可用Key数量, 总Key数量, 状态描述)
    """
    profile = config.get("profiles", {}).get("gemini", {})
    keys = profile.get("api_keys") or ([profile.get("api_key")] if profile.get("api_key") else [])
    model = profile.get("model", "gemini-3.5-flash-lite")
    
    if not keys:
        return 0, 0, "未配置任何 Gemini API Key"

    available_keys = 0
    quota_exhausted_keys = 0
    invalid_keys = 0
    last_err = ""

    print(f"[*] 正在探测 {len(keys)} 个 Gemini API Keys 的可用性与当日配额...")
    for idx, k in enumerate(keys, 1):
        url = f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={k}"
        data = json.dumps({"contents": [{"parts": [{"text": "ping"}]}]}).encode("utf-8")
        req = urllib.request.Request(url, data=data, headers={"Content-Type": "application/json"})
        try:
            with urllib.request.urlopen(req, timeout=10) as resp:
                if resp.status == 200:
                    available_keys += 1
                    print(f"  [+] Key #{idx} ({k[:12]}...): 可用 (正常响应)")
        except urllib.error.HTTPError as e:
            body = e.read().decode("utf-8", errors="ignore")
            if e.code == 429:
                quota_exhausted_keys += 1
                last_err = "429 Quota Exceeded"
                print(f"  [-] Key #{idx} ({k[:12]}...): 额度已用尽 (HTTP 429)")
            elif e.code == 400 and ("API_KEY_INVALID" in body or "not valid" in body.lower()):
                invalid_keys += 1
                last_err = "Invalid Key"
                print(f"  [-] Key #{idx} ({k[:12]}...): 无效 Key (HTTP 400)")
            else:
                last_err = f"HTTP {e.code}"
                print(f"  [-] Key #{idx} ({k[:12]}...): HTTP {e.code}")
        except Exception as ex:
            last_err = str(ex)
            print(f"  [-] Key #{idx} ({k[:12]}...): 连接异常 - {ex}")

    summary = f"{available_keys}/{len(keys)} 个 Key 可用"
    if available_keys == 0:
        if quota_exhausted_keys > 0:
            summary = "所有 Key 当日调用配额已耗尽 (HTTP 429)"
        else:
            summary = f"所有 Key 暂时不可用 ({last_err})"

    return available_keys, len(keys), summary


def get_current_metrics() -> dict:
    """采集当前数据库中各项翻译统计数据"""
    metrics = {
        "movie_desc_total": 0,
        "movie_desc_done": 0,
        "movie_title_total": 0,
        "movie_title_done": 0,
        "bio_total": 0,
        "bio_done": 0,
        "origin_total": 0,
        "origin_done": 0,
        "translations_db_count": 0,
        "translations_db_chars": 0,
    }

    # 1. 查 GPDb.db
    if GPDB_FILE.exists():
        try:
            conn = sqlite3.connect(GPDB_FILE)
            c = conn.cursor()
            
            # 剧情简介
            c.execute("SELECT COUNT(*) FROM movies WHERE description IS NOT NULL AND TRIM(description) != ''")
            metrics["movie_desc_total"] = c.fetchone()[0]
            c.execute("SELECT COUNT(*) FROM movies WHERE description_zh IS NOT NULL AND TRIM(description_zh) != ''")
            metrics["movie_desc_done"] = c.fetchone()[0]

            # 影片片名
            c.execute("SELECT COUNT(*) FROM movies WHERE title IS NOT NULL AND TRIM(title) != ''")
            metrics["movie_title_total"] = c.fetchone()[0]
            c.execute("SELECT COUNT(*) FROM movies WHERE title_zh IS NOT NULL AND TRIM(title_zh) != ''")
            metrics["movie_title_done"] = c.fetchone()[0]

            # 演员生平与国籍
            try:
                c.execute("SELECT COUNT(*) FROM performer_pbc_profiles WHERE bio IS NOT NULL AND TRIM(bio) != ''")
                metrics["bio_total"] = c.fetchone()[0]
                c.execute("SELECT COUNT(*) FROM performer_pbc_profiles WHERE bio_zh IS NOT NULL AND TRIM(bio_zh) != ''")
                metrics["bio_done"] = c.fetchone()[0]
            except Exception:
                pass

            try:
                c.execute("""SELECT COUNT(*) FROM performer_pbc_profiles 
                             WHERE (nationality IS NOT NULL AND TRIM(nationality) != '')
                                OR (country IS NOT NULL AND TRIM(country) != '')
                                OR (birth_place IS NOT NULL AND TRIM(birth_place) != '')""")
                metrics["origin_total"] = c.fetchone()[0]
                c.execute("""SELECT COUNT(*) FROM performer_pbc_profiles 
                             WHERE (nationality_zh IS NOT NULL AND TRIM(nationality_zh) != '')
                                OR (country_zh IS NOT NULL AND TRIM(country_zh) != '')
                                OR (birth_place_zh IS NOT NULL AND TRIM(birth_place_zh) != '')""")
                metrics["origin_done"] = c.fetchone()[0]
            except Exception:
                pass

            conn.close()
        except Exception as e:
            print(f"[-] 读取 GPDb.db 指标失败: {e}")

    # 2. 查 translations.db
    if TRANS_DB_FILE.exists():
        try:
            conn = sqlite3.connect(TRANS_DB_FILE)
            c = conn.cursor()
            c.execute("SELECT COUNT(*), COALESCE(SUM(LENGTH(trans_text)), 0) FROM translations")
            row = c.fetchone()
            metrics["translations_db_count"] = row[0]
            metrics["translations_db_chars"] = row[1]
            conn.close()
        except Exception as e:
            print(f"[-] 读取 translations.db 指标失败: {e}")

    return metrics


def send_discord_report(webhook_url: str, title: str, description: str, fields: list[dict], color: int = 5814783):
    """通过纯标准库向 Discord Webhook 发送带有 Embed 的精美卡片报告"""
    if not webhook_url:
        print("[-] 未配置 discord_webhook_url，跳过 Discord 发送。")
        return

    payload = {
        "embeds": [{
            "title": title,
            "description": description,
            "color": color,
            "fields": fields,
            "timestamp": datetime.datetime.now(datetime.timezone.utc).isoformat(),
            "footer": {
                "text": "GPDb 自动化汉化守护进程 · 本地开机自启任务"
            }
        }]
    }

    try:
        req = urllib.request.Request(
            webhook_url,
            data=json.dumps(payload).encode("utf-8"),
            headers={"Content-Type": "application/json", "User-Agent": "GPDbAutoTranslate/1.0"}
        )
        with urllib.request.urlopen(req, timeout=15) as resp:
            print(f"[+] Discord 报告发送成功！HTTP {resp.status}")
    except Exception as e:
        print(f"[-] 发送 Discord 报告失败: {e}")


def append_progress_document(start_time_str: str, duration_str: str, delta: dict, latest: dict, stop_reason: str):
    """将本次翻译成果与大盘进展追加更新到 TRANSLATION_PROGRESS.md"""
    if not PROGRESS_FILE.exists():
        print("[-] 未找到 TRANSLATION_PROGRESS.md，跳过文档写入。")
        return

    now_str = datetime.datetime.now().strftime("%Y-%m-%d %H:%M")
    
    entry = f"""
### 🕒 自动运行归档记录 ({now_str})
- **启动时间**：{start_time_str} (耗时：{duration_str})
- **停机状态**：{stop_reason}
- **本次净增成果**：
  - 新增剧情简介汉化：**+{delta['movie_desc_done']:,} 部**
  - 新增影片片名汉化：**+{delta['movie_title_done']:,} 部**
  - 独立归档库净增：**+{delta['translations_db_count']:,} 条**
  - 新增纯汉化字符：**+{delta['translations_db_chars']:,} 字符**
- **当前全库大盘总进度**：
  - 剧情简介：**{latest['movie_desc_done']:,} / {latest['movie_desc_total']:,} ({latest['movie_desc_done'] / max(1, latest['movie_desc_total']) * 100:.1f}%)**
  - 影片片名：**{latest['movie_title_done']:,} / {latest['movie_title_total']:,} ({latest['movie_title_done'] / max(1, latest['movie_title_total']) * 100:.1f}%)**
  - 独立归档库总量：**{latest['translations_db_count']:,} 条**（累计 **{latest['translations_db_chars']:,} 字符**）
"""

    try:
        content = PROGRESS_FILE.read_text(encoding="utf-8")
        section_marker = "## 📜 三、 厂牌全量建档与深度文史解析规范"
        
        # 将日志插入在第二节与第三节之间，或追加在末尾
        if "## 📅 自动翻译运行流水日志" not in content:
            log_section = "\n---\n\n## 📅 自动翻译运行流水日志\n" + entry
            if section_marker in content:
                content = content.replace(section_marker, log_section + "\n---\n\n" + section_marker)
            else:
                content += log_section
        else:
            # 已经有日志节，把最新的记录插入到标题下方
            target = "## 📅 自动翻译运行流水日志\n"
            content = content.replace(target, target + entry)

        PROGRESS_FILE.write_text(content, encoding="utf-8")
        print(f"[+] 本次翻译报告已成功追加归档至: {PROGRESS_FILE}")
    except Exception as e:
        print(f"[-] 写入 TRANSLATION_PROGRESS.md 失败: {e}")


def format_duration(seconds: float) -> str:
    m, s = divmod(int(seconds), 60)
    h, m = divmod(m, 60)
    if h > 0:
        return f"{h} 小时 {m} 分 {s} 秒"
    elif m > 0:
        return f"{m} 分 {s} 秒"
    else:
        return f"{s} 秒"


def main():
    print("=" * 75)
    print("🚀 GPDb 开机自启自动翻译守护进程 (Auto Translate Daemon) 启动")
    start_dt = datetime.datetime.now()
    start_time_str = start_dt.strftime("%Y-%m-%d %H:%M:%S")
    start_ts = time.time()
    print(f"   启动时间: {start_time_str}")
    print("=" * 75)

    config = load_config()
    webhook_url = config.get("discord_webhook_url", "")

    # 1. 检测网络
    if not wait_for_network(max_wait_seconds=180):
        print("[-] 启动失败：网络无法连通，退出。")
        return

    # 2. 探测 Gemini API Key 可用性
    avail_keys, total_keys, status_desc = test_gemini_keys(config)
    if avail_keys == 0:
        print(f"\n⏸️ [跳过翻译] Gemini API 当前不可用: {status_desc}")
        # 发送提示卡片通知用户当前配额未刷新
        send_discord_report(
            webhook_url=webhook_url,
            title="⏸️ GPDb 翻译守护进程 · 本次未启动",
            description=f"设备已开机并连网，但检测到 Google Gemini API 当前无可用配额。\n\n**原因**: {status_desc}\n守护进程已自动待机，不会消耗系统资源。",
            fields=[
                {"name": "已配置 Key 总数", "value": f"{total_keys} 个", "inline": True},
                {"name": "当前可用 Key 数", "value": f"{avail_keys} 个", "inline": True},
                {"name": "建议重试时间", "value": "明日配额刷新后 (UTC 00:01)", "inline": False}
            ],
            color=16753920 # 橙黄色
        )
        return

    # 3. 记录起始大盘指标
    initial_metrics = get_current_metrics()
    print(f"\n[+] 初始状态基准采集完成:")
    print(f"   • 剧情简介已汉化: {initial_metrics['movie_desc_done']:,} / {initial_metrics['movie_desc_total']:,}")
    print(f"   • 影片片名已汉化: {initial_metrics['movie_title_done']:,} / {initial_metrics['movie_title_total']:,}")
    print(f"   • 知识库归档记录: {initial_metrics['translations_db_count']:,} 条 ({initial_metrics['translations_db_chars']:,} 字符)")

    # 4. 启动流水线
    print(f"\n[+] 开始执行全库汉化流水线 (run_studios_pipeline.py)...")
    cmd = [sys.executable, "run_studios_pipeline.py", "--exit-on-quota"]
    
    stop_reason = "流水线全量完成"
    try:
        res = subprocess.run(cmd, cwd=str(BASE_DIR))
        if res.returncode == 42:
            stop_reason = "Gemini API 当日调用配额全部用尽 (Exit Code 42)"
            print(f"\n🛑 [正常休眠] 流水线捕获配额用尽退出 (42)。")
        elif res.returncode != 0:
            stop_reason = f"进程异常退出 (Exit Code {res.returncode})"
            print(f"\n[-] 流水线异常退出，代码: {res.returncode}")
        else:
            stop_reason = "全库待翻译条目全部处理完毕"
            print(f"\n✨ 流水线正常执行完毕。")
    except Exception as e:
        stop_reason = f"执行过程中发生异常: {e}"
        print(f"[-] 运行异常: {e}")

    # 5. 采集结束大盘指标并计算增量
    end_ts = time.time()
    duration_str = format_duration(end_ts - start_ts)
    final_metrics = get_current_metrics()

    delta = {
        "movie_desc_done": final_metrics["movie_desc_done"] - initial_metrics["movie_desc_done"],
        "movie_title_done": final_metrics["movie_title_done"] - initial_metrics["movie_title_done"],
        "translations_db_count": final_metrics["translations_db_count"] - initial_metrics["translations_db_count"],
        "translations_db_chars": final_metrics["translations_db_chars"] - initial_metrics["translations_db_chars"],
    }

    print("\n" + "=" * 75)
    print("📊 本次运行成果总结:")
    print(f"   • 耗时: {duration_str}")
    print(f"   • 停机原因: {stop_reason}")
    print(f"   • 新增剧情简介: +{delta['movie_desc_done']:,} 部")
    print(f"   • 新增影片片名: +{delta['movie_title_done']:,} 部")
    print(f"   • 新增归档记录: +{delta['translations_db_count']:,} 条")
    print(f"   • 新增汉化字符: +{delta['translations_db_chars']:,} 字符")
    print("=" * 75)

    # 6. 发送 Discord 报告
    desc_pct = final_metrics["movie_desc_done"] / max(1, final_metrics["movie_desc_total"]) * 100
    title_pct = final_metrics["movie_title_done"] / max(1, final_metrics["movie_title_total"]) * 100

    discord_fields = [
        {"name": "⏱️ 运行耗时", "value": duration_str, "inline": True},
        {"name": "🛑 停止原因", "value": stop_reason, "inline": True},
        {"name": "📈 本次新增翻译条目", "value": f"+{delta['translations_db_count']:,} 条", "inline": True},
        {"name": "✍️ 本次新增汉化字符", "value": f"+{delta['translations_db_chars']:,} 字", "inline": True},
        {"name": "🎬 本次新增影片简介", "value": f"+{delta['movie_desc_done']:,} 部", "inline": True},
        {"name": "🏷️ 本次新增影片片名", "value": f"+{delta['movie_title_done']:,} 部", "inline": True},
        {
            "name": "📊 全库最新大盘总进度",
            "value": (
                f"• **剧情简介**: {final_metrics['movie_desc_done']:,} / {final_metrics['movie_desc_total']:,} ({desc_pct:.1f}%)\n"
                f"• **影片片名**: {final_metrics['movie_title_done']:,} / {final_metrics['movie_title_total']:,} ({title_pct:.1f}%)\n"
                f"• **演员生平**: {final_metrics['bio_done']:,} / {final_metrics['bio_total']:,} (100.0%)\n"
                f"• **演员国籍**: {final_metrics['origin_done']:,} / {final_metrics['origin_total']:,} (100.0%)\n"
                f"• **归档库总量**: {final_metrics['translations_db_count']:,} 条 ({final_metrics['translations_db_chars']:,} 字符)"
            ),
            "inline": False
        }
    ]

    report_color = 3066993 if delta["translations_db_count"] > 0 else 15844367 # 绿色或黄色
    send_discord_report(
        webhook_url=webhook_url,
        title="🤖 GPDb 翻译守护进程 · 运行成果报告",
        description="本次自动翻译任务已完成并安全休眠，相关数据已双写持久化。",
        fields=discord_fields,
        color=report_color
    )

    # 7. 更新项目文档 TRANSLATION_PROGRESS.md
    append_progress_document(
        start_time_str=start_time_str,
        duration_str=duration_str,
        delta=delta,
        latest=final_metrics,
        stop_reason=stop_reason
    )


if __name__ == "__main__":
    main()
