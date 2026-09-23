#!/usr/bin/env python3
"""
Generate all GPDb application icons from the master macOS 27 design asset (scheme-a.png).

Outputs:
  - gen/icons/AppIcon.icns (macOS full resolution icon ladder 16x16..1024x1024)
  - gen/icons/Assets.car   (compiled macOS Asset Catalog for modern macOS appearance)
  - icons/icon.icns        (fallback bundle icns)
  - icons/icon.png         (512x512)
  - icons/icon.ico         (Windows multi-res ICO)
  - icons/*.png            (Tauri standard PNG icon ladder)
"""
import os
import sys
import shutil
import struct
import tempfile
import subprocess
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
SRC_IMAGE = ROOT / "src" / "assets" / "icons" / "scheme-a.png"
GEN_DIR = HERE / "gen" / "icons"
ICONS_DIR = HERE / "icons"

def run_cmd(cmd):
    res = subprocess.run(cmd, capture_output=True, text=True)
    if res.returncode != 0:
        raise RuntimeError(f"Command failed ({res.returncode}): {' '.join(cmd)}\n{res.stderr}\n{res.stdout}")
    return res

def create_ico(png_dict: dict, out_path: Path):
    """Write modern Windows ICO file with embedded PNG frames."""
    count = len(png_dict)
    header = struct.pack('<HHH', 0, 1, count)
    entries = []
    data_offset = 6 + count * 16
    payloads = []
    for (w, h), data in png_dict.items():
        w_byte = 0 if w >= 256 else w
        h_byte = 0 if h >= 256 else h
        entry = struct.pack('<BBBBHHII', w_byte, h_byte, 0, 0, 1, 32, len(data), data_offset)
        entries.append(entry)
        payloads.append(data)
        data_offset += len(data)
    with open(out_path, 'wb') as f:
        f.write(header + b''.join(entries) + b''.join(payloads))

def main():
    if not SRC_IMAGE.exists():
        print(f"Error: Source image not found at {SRC_IMAGE}", file=sys.stderr)
        sys.exit(1)

    GEN_DIR.mkdir(parents=True, exist_ok=True)
    ICONS_DIR.mkdir(parents=True, exist_ok=True)

    with tempfile.TemporaryDirectory() as tmp_dir:
        tmp = Path(tmp_dir)

        print("==> 1. Generating macOS .icns ladder (iconutil)...")
        iconset = tmp / "AppIcon.iconset"
        iconset.mkdir()

        ladder = [
            (16, "icon_16x16.png"),
            (32, "icon_16x16@2x.png"),
            (32, "icon_32x32.png"),
            (64, "icon_32x32@2x.png"),
            (128, "icon_128x128.png"),
            (256, "icon_128x128@2x.png"),
            (256, "icon_256x256.png"),
            (512, "icon_256x256@2x.png"),
            (512, "icon_512x512.png"),
            (1024, "icon_512x512@2x.png"),
        ]
        for px, filename in ladder:
            dst = iconset / filename
            run_cmd(["sips", "-z", str(px), str(px), str(SRC_IMAGE), "--out", str(dst)])

        app_icns = GEN_DIR / "AppIcon.icns"
        run_cmd(["iconutil", "-c", "icns", str(iconset), "-o", str(app_icns)])
        shutil.copy(app_icns, ICONS_DIR / "icon.icns")
        print(f"    Created {app_icns} ({app_icns.stat().st_size:,} bytes)")

        print("==> 2. Generating macOS Asset Catalog Assets.car (actool)...")
        actool_path = shutil.which("actool") or "/Applications/Xcode.app/Contents/Developer/usr/bin/actool"
        if os.path.exists(actool_path):
            xcassets = tmp / "Assets.xcassets"
            appiconset = xcassets / "AppIcon.appiconset"
            appiconset.mkdir(parents=True)

            contents_images = []
            sizes = [
                ("16x16", 1, 16),
                ("16x16", 2, 32),
                ("32x32", 1, 32),
                ("32x32", 2, 64),
                ("128x128", 1, 128),
                ("128x128", 2, 256),
                ("256x256", 1, 256),
                ("256x256", 2, 512),
                ("512x512", 1, 512),
                ("512x512", 2, 1024),
            ]
            import json
            for size_str, scale, px in sizes:
                fn = f"icon_{size_str}_{scale}x.png"
                dst = appiconset / fn
                run_cmd(["sips", "-z", str(px), str(px), str(SRC_IMAGE), "--out", str(dst)])
                contents_images.append({
                    "size": size_str,
                    "idiom": "mac",
                    "filename": fn,
                    "scale": f"{scale}x"
                })
            with open(appiconset / "Contents.json", "w") as f:
                json.dump({"images": contents_images, "info": {"version": 1, "author": "xcode"}}, f, indent=2)

            run_cmd([
                actool_path, str(xcassets),
                "--compile", str(GEN_DIR),
                "--app-icon", "AppIcon",
                "--output-partial-info-plist", str(tmp / "partial.plist"),
                "--platform", "macosx",
                "--minimum-deployment-target", "11.0",
                "--target-device", "mac"
            ])
            car_file = GEN_DIR / "Assets.car"
            print(f"    Created {car_file} ({car_file.stat().st_size:,} bytes)")
        else:
            print("    actool not found, skipping Assets.car compilation")

        print("==> 3. Generating Tauri standard icon bundle...")
        tauri_sizes = {
            "icon.png": 512,
            "32x32.png": 32,
            "128x128.png": 128,
            "128x128@2x.png": 256,
            "Square30x30Logo.png": 30,
            "Square44x44Logo.png": 44,
            "Square71x71Logo.png": 71,
            "Square89x89Logo.png": 89,
            "Square107x107Logo.png": 107,
            "Square142x142Logo.png": 142,
            "Square150x150Logo.png": 150,
            "Square284x284Logo.png": 284,
            "Square310x310Logo.png": 310,
            "StoreLogo.png": 50,
        }
        for name, px in tauri_sizes.items():
            run_cmd(["sips", "-z", str(px), str(px), str(SRC_IMAGE), "--out", str(ICONS_DIR / name)])

        print("==> 4. Generating Windows multi-resolution icon.ico...")
        ico_sizes = [16, 32, 48, 64, 128, 256]
        ico_frames = {}
        for s in ico_sizes:
            ico_png = tmp / f"ico_{s}.png"
            run_cmd(["sips", "-z", str(s), str(s), str(SRC_IMAGE), "--out", str(ico_png)])
            with open(ico_png, "rb") as f:
                ico_frames[(s, s)] = f.read()
        create_ico(ico_frames, ICONS_DIR / "icon.ico")
        print(f"    Created {ICONS_DIR / 'icon.ico'}")

    print("✅ All application icon assets successfully synchronized with Scheme A!")

if __name__ == "__main__":
    main()
