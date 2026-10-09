#!/usr/bin/env python3
"""
import_studio_logos.py — 片商 Logo/Banner 批量压缩导入工具

将 image_cache/Logos/ 目录下的图片文件批量压缩为 WebP 格式，
写入 GPDb.db 的 studio_logos 表。

用法：
    python import_studio_logos.py                      # 默认参数
    python import_studio_logos.py --db GPDb.db --dry-run  # 预览模式
    python import_studio_logos.py --logo-width 200     # 自定义宽度

依赖：
    pip install Pillow
"""

from __future__ import annotations
import argparse
import hashlib
import io
import sqlite3
import sys
from pathlib import Path

try:
    from PIL import Image
except ImportError:
    print("❌ 需要 Pillow 库。请运行：pip install Pillow")
    sys.exit(1)


# ── 默认参数 ───────────────────────────────────────────────────
DEFAULT_DB    = "GPDb.db"
DEFAULT_DIR   = Path("image_cache/Logos")
LOGO_WIDTH    = 150    # Logo 最大宽度 (px)
BANNER_WIDTH  = 400    # Banner 最大宽度 (px)

# 用 dict 包裹以避免 global 声明问题
_config = {"webp_quality": 82}


def to_webp(filepath: Path, max_width: int) -> bytes | None:
    """将任意格式图片压缩为 WebP，限定最大宽度，返回 bytes。"""
    try:
        with Image.open(filepath) as img:
            # 保留透明通道（PNG Logo 常见）
            if img.mode in ("RGBA", "LA", "PA", "P"):
                img = img.convert("RGBA")
            else:
                img = img.convert("RGB")

            # 等比缩放
            if img.width > max_width:
                ratio = max_width / img.width
                new_h = max(1, int(img.height * ratio))
                img = img.resize((max_width, new_h), Image.LANCZOS)

            buf = io.BytesIO()
            img.save(buf, format="WEBP", quality=_config["webp_quality"], method=4)
            return buf.getvalue()
    except Exception as e:
        print(f"  ⚠️  转换失败 {filepath.name}: {e}")
        return None



def find_logo_file(logo_dir: Path, url_or_filename: str | None) -> Path | None:
    """从 logo_url 或 banner_url 还原出本地文件路径。"""
    if not url_or_filename:
        return None
    filename = Path(url_or_filename).name       # e.g. "kinkmen_logo.png"
    candidate = logo_dir / filename
    if candidate.exists() and candidate.stat().st_size > 0:
        return candidate
    return None


def create_table(conn: sqlite3.Connection) -> None:
    """确保 studio_logos 表存在。"""
    conn.execute("""
        CREATE TABLE IF NOT EXISTS studio_logos (
            studio_id   INTEGER PRIMARY KEY,
            logo_webp   BLOB,
            banner_webp BLOB,
            logo_hash   TEXT,
            updated_at  TEXT DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (studio_id) REFERENCES studios(id) ON DELETE CASCADE
        )
    """)
    conn.commit()


def main() -> None:
    parser = argparse.ArgumentParser(
        description="将 image_cache/Logos/ 下的 Logo 压缩为 WebP 并写入 GPDb.db"
    )
    parser.add_argument("--db", default=DEFAULT_DB, help="SQLite 数据库路径")
    parser.add_argument("--logo-dir", default=str(DEFAULT_DIR), help="Logo 图片目录")
    parser.add_argument("--logo-width", type=int, default=LOGO_WIDTH, help="Logo 最大宽度")
    parser.add_argument("--banner-width", type=int, default=BANNER_WIDTH, help="Banner 最大宽度")
    parser.add_argument("--quality", type=int, default=_config["webp_quality"], help="WebP 质量 (0-100)")
    parser.add_argument("--dry-run", action="store_true", help="只统计，不写入数据库")
    parser.add_argument("--force", action="store_true", help="强制覆盖已有记录")
    args = parser.parse_args()

    db_path = Path(args.db)
    logo_dir = Path(args.logo_dir)

    if not db_path.exists():
        print(f"❌ 数据库不存在: {db_path}")
        sys.exit(1)
    if not logo_dir.exists():
        print(f"❌ Logo 目录不存在: {logo_dir}")
        sys.exit(1)

    _config["webp_quality"] = args.quality

    conn = sqlite3.connect(str(db_path))
    conn.execute("PRAGMA journal_mode = WAL")

    # 确保表存在
    create_table(conn)

    # 读取所有片商
    studios = conn.execute(
        "SELECT id, name, logo_url, banner_url FROM studios ORDER BY id"
    ).fetchall()
    print(f"📊 共 {len(studios)} 个片商")

    # 已有记录（跳过用）
    existing_ids: set[int] = set()
    if not args.force:
        rows = conn.execute("SELECT studio_id FROM studio_logos").fetchall()
        existing_ids = {r[0] for r in rows}
        if existing_ids:
            print(f"   已有 {len(existing_ids)} 条记录，将跳过（使用 --force 覆盖）")

    # 额外扫描：logo_dir 中可能有文件但 studios 表里没有对应 logo_url 的
    # 这种情况通过文件名模糊匹配也能覆盖一部分
    all_logo_files = {f.stem.lower(): f for f in logo_dir.iterdir()
                      if f.is_file() and f.suffix.lower() in ('.png', '.jpg', '.jpeg', '.gif', '.webp', '.svg')}

    imported = 0
    skipped = 0
    failed = 0
    total_logo_bytes = 0
    total_banner_bytes = 0

    for sid, name, logo_url, banner_url in studios:
        if sid in existing_ids:
            skipped += 1
            continue

        logo_bytes: bytes | None = None
        banner_bytes: bytes | None = None

        # ── 匹配 Logo 文件 ──
        logo_file = find_logo_file(logo_dir, logo_url)
        if logo_file:
            logo_bytes = to_webp(logo_file, args.logo_width)

        # ── 匹配 Banner 文件 ──
        banner_file = find_logo_file(logo_dir, banner_url)
        if banner_file:
            banner_bytes = to_webp(banner_file, args.banner_width)

        # ── 兜底：用 name 生成候选文件名尝试匹配 ──
        if not logo_bytes and not banner_bytes:
            # 尝试 "studio_name_logo" 格式
            normalized = name.lower().replace(" ", "_").replace("-", "_").replace("'", "").replace(".", "")
            logo_key = f"{normalized}_logo"
            banner_key = f"{normalized}_banner"

            if logo_key in all_logo_files:
                logo_bytes = to_webp(all_logo_files[logo_key], args.logo_width)
            if banner_key in all_logo_files:
                banner_bytes = to_webp(all_logo_files[banner_key], args.banner_width)

        if not logo_bytes and not banner_bytes:
            continue  # 这个片商确实没有可导入的图片

        logo_hash = hashlib.sha256(logo_bytes).hexdigest()[:16] if logo_bytes else None

        if logo_bytes:
            total_logo_bytes += len(logo_bytes)
        if banner_bytes:
            total_banner_bytes += len(banner_bytes)

        if not args.dry_run:
            try:
                conn.execute(
                    """INSERT OR REPLACE INTO studio_logos
                       (studio_id, logo_webp, banner_webp, logo_hash, updated_at)
                       VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)""",
                    (sid, logo_bytes, banner_bytes, logo_hash),
                )
                imported += 1
            except Exception as e:
                print(f"  ❌ 写入失败 [{name}]: {e}")
                failed += 1
        else:
            imported += 1
            if logo_bytes:
                print(f"  🖼  {name}: logo {len(logo_bytes) / 1024:.1f} KB"
                      + (f", banner {len(banner_bytes) / 1024:.1f} KB" if banner_bytes else ""))

    if not args.dry_run:
        conn.commit()

    conn.close()

    # ── 汇总报告 ──
    mode = "[DRY-RUN] " if args.dry_run else ""
    print()
    print(f"{mode}✅ 导入: {imported} 个片商")
    print(f"{mode}⏭  跳过: {skipped} 个（已存在）")
    if failed:
        print(f"{mode}❌ 失败: {failed} 个")
    print(f"{mode}📊 Logo 总体积:   {total_logo_bytes / 1024 / 1024:.2f} MB")
    print(f"{mode}📊 Banner 总体积: {total_banner_bytes / 1024 / 1024:.2f} MB")
    print(f"{mode}📊 合计:          {(total_logo_bytes + total_banner_bytes) / 1024 / 1024:.2f} MB")


if __name__ == "__main__":
    main()
