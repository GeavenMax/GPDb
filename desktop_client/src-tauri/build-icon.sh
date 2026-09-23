#!/bin/bash
# Build the GPDb app icon conforming to macOS HIG / macOS 27 design specifications.
#
# Artwork source: ../src/assets/icons/scheme-a.png
# Outputs:
#   gen/icons/AppIcon.icns  (macOS full resolution icns ladder 16x16..1024x1024)
#   gen/icons/Assets.car    (compiled macOS Asset Catalog for modern macOS appearance)
#   icons/icon.icns         (fallback bundle icns)
#   icons/icon.ico          (Windows multi-res ICO)
#   icons/*.png             (Tauri standard PNG icon ladder)

set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
python3 "$HERE/generate-app-icons.py"
