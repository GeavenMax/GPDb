#!/usr/bin/env bash
#
# 全站影片刮削 —— 按顺序跑三步，中途 Ctrl-C 或断电都可以，重跑同一命令会接着跑。
#
#   1) gaps  补齐 2,793 部影片缺失的封面变体记录（主要就是封底图），约 10 分钟
#   2) new   走完 64,962 个尚未探测的 ID，把站点剩下的影片全部抓回来，约 4-6 小时
#   3) failed 重试上次失败(500)的影片
#
# 为什么不需要枚举：站点影片 ID 密度很高（未探测区抽样 92% 命中），直接走 ID
# 比按片商枚举更省事；scrape_progress 表会自动跳过已探测过的 ID，所以重跑不会
# 重复抓取，66,000 个 ID 里真正发出去的请求只有没抓过的那 64,962 个。
#
# 图片保持按需缓存，本脚本不下载任何封面图（浏览到哪张才缓存哪张）。
#
set -uo pipefail

cd "$(dirname "$0")" || exit 1

DB="${DB:-gevi.db}"
END="${END:-76000}"                 # 影片 ID 上界，实测 76,000 之后全是 404
LIMIT="${LIMIT:-0}"                 # 0 = 不限。设成 20 可先小跑一批看看效果
CONCURRENCY="${CONCURRENCY:-4}"
RATE="${RATE:-3.0}"
MAX_RATE="${MAX_RATE:-8.0}"

LOG_DIR="logs"
mkdir -p "$LOG_DIR"
LOG="$LOG_DIR/scrape_movies_$(date +%Y%m%d_%H%M%S).log"

# 两个刮削进程同时跑会叠加请求速率，等于把限流机制废掉，很容易被站点封。
if pgrep -f "scraper_v2\.py" > /dev/null 2>&1; then
  echo "❌ 已经有 scraper_v2.py 在运行了。同时跑两个会让请求速率翻倍，容易被封。"
  echo "   先停掉它（Ctrl-C，或 kill \$(pgrep -f scraper_v2.py)）再执行本脚本。"
  exit 1
fi

if [ ! -f "$DB" ]; then
  echo "❌ 找不到 $DB，请在项目根目录运行本脚本。"
  exit 1
fi

echo "日志会同时写入: $LOG"
echo "（Ctrl-C 可随时中断，重跑本脚本会从断点继续）"
echo

# tee 到日志，同时让你在屏幕上看到进度条。
run() {
  echo "──────────────────────────────────────────────────────────────"
  echo "▶ $*"
  echo "──────────────────────────────────────────────────────────────"
  "$@" 2>&1 | tee -a "$LOG"
  local rc=${PIPESTATUS[0]}
  # 130 = Ctrl-C。中断不算错误：下次重跑会接着来。
  if [ "$rc" -ne 0 ] && [ "$rc" -ne 130 ]; then
    echo "⚠️  上一步以退出码 $rc 结束，见日志 $LOG"
    return "$rc"
  fi
  return 0
}

echo "=== 刮削前体检 ==="
run python3 scraper_v2.py --db "$DB" --audit

echo
echo "=== 第 1 步 / 共 3 步：补齐封面变体（封底图）==="
run python3 scraper_v2.py --db "$DB" --mode gaps --gaps cover_variants --limit "$LIMIT" \
  --concurrency "$CONCURRENCY" --rate "$RATE" --max-rate "$MAX_RATE" || true

echo
echo "=== 第 2 步 / 共 3 步：抓取全站剩余影片（预计 4-6 小时）==="
echo "    想后台跑、关掉终端也不停，改用："
echo "    nohup ./scrape_all_movies.sh > /dev/null 2>&1 &"
echo
run python3 scraper_v2.py --db "$DB" --mode new --start 1 --end "$END" --limit "$LIMIT" \
  --concurrency "$CONCURRENCY" --rate "$RATE" --max-rate "$MAX_RATE" || true

echo
echo "=== 第 3 步 / 共 3 步：重试失败项 ==="
run python3 scraper_v2.py --db "$DB" --mode failed --limit "$LIMIT" \
  --concurrency "$CONCURRENCY" --rate "$RATE" --max-rate "$MAX_RATE" || true

echo
echo "=== 刮削后体检 ==="
run python3 scraper_v2.py --db "$DB" --audit

echo
echo "✅ 全部完成。日志: $LOG"
echo "   备份（保留最近 5 份）: gevi.db.backup-*"
