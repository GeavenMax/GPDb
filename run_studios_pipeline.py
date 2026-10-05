#!/usr/bin/env python3
"""
run_studios_pipeline.py
-----------------------
GPDb 多厂牌多阶段全自动全库翻译流水线：
- 主引擎：Google Gemini (4个 API Key 自动轮换，免费配额高，速度快)
- 安全兜底：DeepSeek-Chat (自动接管因内容露骨被 Gemini 拦截的条目)
- 批次调优：剧情简介 12 条/批，影片片名 50 条/批
- 全自动挂起：当日全部 Key 配额耗尽后自动挂起到次日 UTC 00:00 唤醒
- 深度后处理：正文中引用的片名自动包裹《书名号》，汉化地名国名，并双写同步 translations.db
- 全库覆盖：优先推进重点大厂，随后按影片体量自动遍历全库所有片商，永不中断
"""

import sys
import time
import sqlite3
import datetime
import subprocess
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent

# 推进厂牌优先队列（优先处理重点大厂分集与片名）
PRIORITY_STUDIOS = [
    "Next Door Studios",
    "Sean Cody",
    "Staxus",
    "Rascal Video",
    "William Higgins Productions",
    "Triga Films",
    "Mile High Media",
    "Miami Studios",
    "Orrange Media Group",
    "Triangle Dream Home Video",
    "Cocky Boys",
    "Men at Play",
    "Hot House Entertainment",
    "Helix Studios",
    "Eurocreme",
    "Channel 1 Releasing"
]


def seconds_until_utc_midnight() -> float:
    """计算距离次日 00:00:00 UTC 的秒数，留 60 秒缓冲"""
    now = datetime.datetime.now(datetime.timezone.utc)
    tomorrow = (now + datetime.timedelta(days=1)).replace(
        hour=0, minute=0, second=0, microsecond=0
    )
    diff = (tomorrow - now).total_seconds()
    return max(diff + 60.0, 300.0)


EXIT_ON_QUOTA = False


def run_cmd(cmd):
    print(f"\n>>> Running: {' '.join(cmd)}")
    while True:
        res = subprocess.run(cmd, cwd=str(BASE_DIR))
        if res.returncode == 42:
            if EXIT_ON_QUOTA:
                print(f"\n[🛑 Quota Exhausted] 检测到 API 配额耗尽 (Exit code 42)，按照 --exit-on-quota 策略直接退出。")
                sys.exit(42)
            wait_sec = seconds_until_utc_midnight()
            wake_dt = datetime.datetime.now() + datetime.timedelta(seconds=wait_sec)
            wake_str = wake_dt.strftime("%Y-%m-%d %H:%M:%S")
            print("\n" + "=" * 72)
            print(f"⏸️  [流水线挂起等待] 所有 API Key 配额已用尽 (Exit code 42)")
            print(f"   将在次日配额刷新后自动重启当前步骤: {wake_str} (休眠等待 {wait_sec/3600:.1f} 小时)")
            print("=" * 72)
            time.sleep(wait_sec)
            print(f"\n⏰ [定时唤醒] 配额已刷新，重新执行步骤: {' '.join(cmd)}")
            continue
        elif res.returncode != 0:
            print(f"[-] Command exited with code {res.returncode}")
        return res.returncode


def get_all_studios() -> list[str]:
    """生成全库推进队列：优先队列 + 全库所有片商（按影片体量降序排列）"""
    studios = list(PRIORITY_STUDIOS)
    seen = set(studios)
    try:
        conn = sqlite3.connect(BASE_DIR / "GPDb.db")
        c = conn.cursor()
        query = """
            SELECT studio_name, COUNT(*) as cnt
            FROM movies
            WHERE studio_name IS NOT NULL AND studio_name != ''
            GROUP BY studio_name
            ORDER BY cnt DESC
        """
        for row in c.execute(query).fetchall():
            s = row[0]
            if s not in seen:
                studios.append(s)
                seen.add(s)
        conn.close()
    except Exception as e:
        print(f"[-] 读取全库片商列表出错: {e}")
    return studios


def main():
    global EXIT_ON_QUOTA
    if "--exit-on-quota" in sys.argv:
        EXIT_ON_QUOTA = True

    studios = get_all_studios()
    print("=" * 75)
    print("🚀 启动 GPDb 全库多厂牌多阶段全自动翻译流水线")
    print(f"   引擎: Google Gemini Flash-Lite (3 Keys 轮换)")
    print(f"   覆盖片商总数: {len(studios)} 个 (优先队列 {len(PRIORITY_STUDIOS)} 个 + 全库片商)")
    print("=" * 75)

    post_process_script = BASE_DIR / "scratch" / "post_process_translations.py"

    # 0. 演员档案优先翻译阶段（全库 1,195 位重点演员的国籍/出生地与维基生平）
    print("\n" + "=" * 75)
    print("🌍 【前置任务 1】推进演员档案页的国籍与出生地汉化 (Nationality / Birth Place)...")
    print("=" * 75)
    run_cmd([
        sys.executable, "translate.py",
        "--profile", "gemini",
        "--performer-origins",
        "--batch-size", "20",
        "--workers", "1",
        "--no-auto-wait"
    ])

    print("\n" + "=" * 75)
    print("👤 【前置任务 2】推进演员档案页的生平档案与演艺经历 (PBC Wiki Bio)...")
    print("=" * 75)
    run_cmd([
        sys.executable, "translate.py",
        "--profile", "gemini",
        "--performer-bios",
        "--batch-size", "6",
        "--workers", "1",
        "--no-auto-wait"
    ])

    for idx, studio in enumerate(studios, 1):
        print(f"\n===========================================================================")
        print(f"🎬 【[{idx}/{len(studios)}] 正在处理片商: {studio}】")
        print(f"===========================================================================")

        # 1. 剧情简介翻译（影片简介 + 分集简介，批次 12）
        print(f"[*] 阶段一：翻译 {studio} 的全部剧情简介 (影片与分集)...")
        cmd_desc = [
            sys.executable, "translate.py",
            "--profile", "gemini",
            "--studio", studio,
            "--batch-size", "12",
            "--workers", "1",
            "--no-auto-wait"
        ]
        run_cmd(cmd_desc)

        # 2. 影片片名翻译（结合简介语境，批次 50）
        print(f"[*] 阶段二：结合剧情语境翻译 {studio} 的影片片名 (批次 50)...")
        cmd_title = [
            sys.executable, "translate.py",
            "--profile", "gemini",
            "--titles",
            "--studio", studio,
            "--batch-size", "50",
            "--workers", "1",
            "--no-auto-wait"
        ]
        run_cmd(cmd_title)

        # 3. 深度后处理与同步（仅针对当前片商快速定向润色）
        if post_process_script.exists():
            print(f"[*] 阶段三：执行 {studio} 译文深度后处理与 translations.db 同步...")
            run_cmd([sys.executable, str(post_process_script), "--studio", studio])

    # 4. 全库收尾阶段：处理无片商或孤立未分配条目
    print("\n" + "=" * 75)
    print("🌐 最终阶段：全库无片商归属的孤立条目大收尾...")
    print("=" * 75)
    run_cmd([sys.executable, "translate.py", "--profile", "gemini", "--batch-size", "12", "--workers", "1", "--no-auto-wait"])
    run_cmd([sys.executable, "translate.py", "--profile", "gemini", "--titles", "--batch-size", "50", "--workers", "1", "--no-auto-wait"])
    if post_process_script.exists():
        run_cmd([sys.executable, str(post_process_script)])

    print("\n" + "=" * 75)
    print("✨ 全库所有厂牌流水线翻译已圆满完成！")
    print("=" * 75)


if __name__ == "__main__":
    main()
