#!/bin/bash
# Build the GPDb app icon.
#
#   make-app-icon.py  ->  AppIcon.icon/Assets/G.svg    (the artwork)
#   actool            ->  gen/icons/Assets.car         (macOS 26+: light/dark/tinted)
#   ictool + iconutil ->  gen/icons/AppIcon.icns       (older macOS: flat fallback)
#
# Both outputs come from the same .icon document, so the two paths cannot disagree
# about what the icon looks like.
#
# Why two of them at all: an .icns cannot carry appearance variants — that is a
# property of the compiled asset catalog, not of the image format. So Assets.car is
# the only way to satisfy "adapts to the system appearance", and the .icns is there
# for systems that predate it. See the note on the icns ladder below.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DOC="$HERE/AppIcon.icon"
OUT="$HERE/gen/icons"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

# actool and ictool both live inside Xcode. Neither /usr/bin/actool nor /usr/bin/ictool
# will do: both are stubs that share an inode with each other and only print a pointer
# to a man page. `xcrun --find` resolves to the real one under Xcode.app.
ACTOOL="$(xcrun --find actool 2>/dev/null || true)"
ICTOOL="/Applications/Xcode.app/Contents/Applications/Icon Composer.app/Contents/Executables/ictool"
if [ -z "$ACTOOL" ] || [ ! -x "$ACTOOL" ]; then
  echo "build-icon.sh: actool not found — install Xcode, or run 'xcode-select --install'." >&2
  exit 1
fi
if [ ! -x "$ICTOOL" ]; then
  echo "build-icon.sh: ictool not found at:" >&2
  echo "  $ICTOOL" >&2
  echo "It ships with Icon Composer, inside Xcode. The icns fallback cannot be built" >&2
  echo "without it." >&2
  exit 1
fi

echo "==> artwork"
python3 "$HERE/make-app-icon.py" "$DOC/Assets/G.svg"

echo "==> Assets.car"
mkdir -p "$OUT"
# The .icon document is passed to actool *directly*. Wrapping it in an
# Assets.xcassets catalog instead makes actool treat it as an opaque folder: it emits
# a partial plist, no car, and no diagnostic at all.
"$ACTOOL" "$DOC" \
  --compile "$OUT" \
  --app-icon AppIcon \
  --output-partial-info-plist "$TMP/partial.plist" \
  --platform macosx \
  --minimum-deployment-target 26.0 \
  --target-device mac \
  --output-format human-readable-text

# actool also writes an AppIcon.icns here, and it is not usable: because the document
# declares macOS (which reads the car), actool only fills in 16 and 128 points and
# leaves the rest of the ladder empty. macOS 26+ never looks at it, but anything older
# would show a low-resolution icon. So it is replaced below with a full ladder.
rm -f "$OUT/AppIcon.icns"

echo "==> AppIcon.icns (legacy fallback)"
# The flat Default rendition is the right source for the legacy path: those systems
# have no notion of light/dark/tinted app icons, so there is nothing to adapt to.
for PX in 16 32 64 128 256 512 1024; do
  "$ICTOOL" "$DOC" --export-image \
    --output-file "$TMP/$PX.png" \
    --platform macOS --rendition Default \
    --width "$PX" --height "$PX" --scale 1 >/dev/null
done

SET="$TMP/AppIcon.iconset"
mkdir -p "$SET"
cp "$TMP/16.png"   "$SET/icon_16x16.png"
cp "$TMP/32.png"   "$SET/icon_16x16@2x.png"
cp "$TMP/32.png"   "$SET/icon_32x32.png"
cp "$TMP/64.png"   "$SET/icon_32x32@2x.png"
cp "$TMP/128.png"  "$SET/icon_128x128.png"
cp "$TMP/256.png"  "$SET/icon_128x128@2x.png"
cp "$TMP/256.png"  "$SET/icon_256x256.png"
cp "$TMP/512.png"  "$SET/icon_256x256@2x.png"
cp "$TMP/512.png"  "$SET/icon_512x512.png"
cp "$TMP/1024.png" "$SET/icon_512x512@2x.png"
iconutil -c icns "$SET" -o "$OUT/AppIcon.icns"

# Deliberately no standalone PNGs, and bundle.icon lists none. Two reasons:
#
#  1. macOS reads the car and the icns and never looks at loose PNGs; they would only
#     be for platforms this app does not build for.
#  2. **ictool writes PNGs at 16 bits per channel**, and tauri::generate_context!
#     decodes one into twice the sample count it expects for the stated dimensions.
#     The build succeeds and the app then dies at startup with "invalid icon: The
#     specified dimensions (32x32) don't match the number of pixels supplied by the
#     rgba argument (2048)" — 2048 being 32x32x2. Nothing in that message points at
#     bit depth. The icns escapes it because Tauri reads sizes from the icns header
#     rather than trusting a decoded PNG.
#
# If a PNG ever is needed here, it has to be forced to 8-bit first: neither `sips -s
# format png` nor `iconutil -c iconset` reliably does it (sips passes 16-bit through,
# and iconutil only re-encodes the sizes the icns stores uncompressed).

echo "==> done"
ls -l "$OUT/Assets.car" "$OUT/AppIcon.icns"
