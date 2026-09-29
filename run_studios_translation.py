#!/usr/bin/env python3
"""
Batch Runner for Priority Studios in GPDb

Translates movie synopses first, then translates movie titles using the newly
translated synopses as context.
"""

import sys
import subprocess
from pathlib import Path

TARGET_STUDIOS = [
    "BelAmi",
    "Titan Media",
    "Falcon Studios",
    "Raging Stallion Studios"
]

def run_cmd(cmd):
    print(f"\n>>> Running: {' '.join(cmd)}")
    res = subprocess.run(cmd)
    if res.returncode != 0:
        print(f"[-] Command failed with return code {res.returncode}")
        # We don't exit, just continue to ensure pipeline completes
    return res.returncode

def main():
    print("=" * 70)
    print("🚀 启动四大重点片商自动化全量翻译流水线")
    print("目标厂牌: BelAmi, Titan Media, Falcon Studios, Raging Stallion Studios")
    print("=" * 70)

    for studio in TARGET_STUDIOS:
        print(f"\n==================== 【片商: {studio}】 ====================")
        
        # 1. 优先翻译影片剧情简介 (movies.description)
        print(f"[*] 阶段一：翻译 {studio} 的全部影片剧情简介...")
        cmd_desc = [
            sys.executable, "translate.py",
            "--profile", "deepseek",
            "--studio", studio,
            "--batch-size", "25",
            "--workers", "4"
        ]
        run_cmd(cmd_desc)

        # 2. 根据剧情语境翻译影片标题 (movies.title)
        print(f"[*] 阶段二：根据中文剧情语境翻译 {studio} 的全部影片片名...")
        cmd_title = [
            sys.executable, "translate.py",
            "--profile", "deepseek",
            "--titles",
            "--studio", studio,
            "--batch-size", "25",
            "--workers", "4"
        ]
        run_cmd(cmd_title)

    print("\n" + "=" * 70)
    print("✨ 四大重点厂牌翻译流水线全部执行完毕！")
    print("=" * 70)

if __name__ == "__main__":
    main()
