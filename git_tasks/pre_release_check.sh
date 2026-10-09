#!/bin/bash
# ==============================================================================
# GPDb Git 管理与发布自动化预检脚本 (Pre-flight Release Check)
# ==============================================================================
set -e

REPO_ROOT="/Users/joel/iCloud Drive (Archive)/Documents/antigravity/GPDb 开发"
cd "$REPO_ROOT"

echo "=== [1/6] 检查工作区路径 ==="
CURRENT_PWD=$(pwd)
echo "当前工作目录: $CURRENT_PWD"
if [[ "$CURRENT_PWD" != "$REPO_ROOT" ]]; then
  echo "❌ 路径不匹配，期望: $REPO_ROOT"
  exit 1
fi

echo "=== [2/6] 检查敏感信息与隐私路径残留 ==="
PRIVACY_MATCHES=$(git grep -i "/Users/joel" README*.md || true)
if [ -n "$PRIVACY_MATCHES" ]; then
  echo "❌ README 中检测到本地绝对路径泄露:"
  echo "$PRIVACY_MATCHES"
  exit 1
fi
echo "✅ README 中无绝对用户路径残留"

echo "=== [3/6] 检查 iOS 相关内容排除合规性 ==="
IOS_STANDALONE=$(git grep -w -i "ios" README*.md || true)
if [ -n "$IOS_STANDALONE" ]; then
  echo "❌ README 中检测到未排除的 iOS 关键字:"
  echo "$IOS_STANDALONE"
  exit 1
fi
echo "✅ README 中已完全排除 iOS 相关描述"

echo "=== [4/6] 检查各端版本号一致性 ==="
DESKTOP_VER=$(grep -m1 '"version"' desktop_client/package.json | awk -F '"' '{print $4}')
WIN_VER=$(grep -m1 '"version"' Windows_client/package.json | awk -F '"' '{print $4}')
CARGO_CORE_VER=$(grep -m1 'version' desktop_client/src-tauri/gpdb-core/Cargo.toml | awk -F '"' '{print $2}')
ANDROID_VER=$(grep -m1 'versionName' android_client/app/build.gradle.kts | awk -F '"' '{print $2}')

echo "desktop_client 版本: $DESKTOP_VER"
echo "Windows_client 版本: $WIN_VER"
echo "gpdb-core 版本:      $CARGO_CORE_VER"
echo "android_client 版本: $ANDROID_VER"

if [[ "$DESKTOP_VER" != "$WIN_VER" || "$DESKTOP_VER" != "$CARGO_CORE_VER" || "$DESKTOP_VER" != "$ANDROID_VER" ]]; then
  echo "⚠️ 各端版本号存在不一致，请核对！"
  exit 1
else
  echo "✅ 全端核心版本号严格对齐 ($DESKTOP_VER)"
fi

echo "=== [5/6] 检查 Android 签名包是否存在 ==="
APK_FILE=$(find android_client -name "GPDb_Android_v${DESKTOP_VER}_release_signed.apk" | head -n 1)
if [ -z "$APK_FILE" ]; then
  echo "⚠️ 未找到当前版本 ($DESKTOP_VER) 的签名 APK 文件，如需发布 Android 端请先构建"
  exit 1
else
  echo "✅ 找到签名 APK: $APK_FILE"
fi

echo "=== [6/6] 检查 Git 分支与未跟踪重要文件 ==="
git status --short

echo ""
echo "🎉 预检完成！所有关键安全合规检查项均已通过。"
