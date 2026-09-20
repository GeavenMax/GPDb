#!/usr/bin/env python3
"""
GEVI Offline Database - Batch Machine Translation of Movie Synopses (EN -> ZH)

Translates the English `description` of every movie into Chinese and stores the
result in `movies.description_zh`, so the offline library stays fully usable in
Chinese with no runtime network access. Translation is a one-time cost per movie.

Supported LLM backends (bring your own API key):
  - anthropic  Anthropic Messages API          (default model: claude-opus-5)
  - openai     Any OpenAI-compatible endpoint  (DeepSeek, Moonshot, OpenAI, Ollama, ...)
  - gemini     Google Gemini generateContent

Configuration (first match wins):
  1. CLI flags      --provider --api-key --model --base-url
  2. Environment    GEVI_LLM_PROVIDER / GEVI_LLM_API_KEY / GEVI_LLM_MODEL / GEVI_LLM_BASE_URL
  3. Config file    translate_config.json next to this script

Usage:
  python3 translate.py --provider openai --api-key sk-... --limit 20 --dry-run
  python3 translate.py --provider anthropic --api-key sk-ant-... --batch-size 20
  python3 translate.py --stats

Note on dependencies: this module deliberately uses only the Python standard
library to honour the project's "zero third-party dependencies" rule. The
official `anthropic` SDK would otherwise be the more idiomatic choice for the
anthropic backend - run `pip install anthropic` and say the word if you would
rather trade the dependency for the SDK.
"""

from __future__ import annotations
import argparse
import json
import os
import re
import sys
import threading
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path
from typing import Any

from db_manager import DatabaseManager

BASE_DIR = Path(__file__).resolve().parent
CONFIG_FILE = BASE_DIR / "translate_config.json"

# A response that is already mostly CJK needs no translation.
CJK_RE = re.compile(r"[一-鿿]")

SYSTEM_PROMPT = """你是一名成人影片资料库的专职译者。你会收到一批英文的剧情简介，需要逐条翻译成简体中文。

翻译风格（最重要，必须严格遵守）：
1. 忠于原文。逐句对应，原文说了什么就译什么，不增不减、不概括、不改写、不调整叙述顺序。
2. 非常直白。用词直接、露骨、不回避，按原文的露骨程度如实翻译：原文用了直白的说法就译成直白的说法，不要替换成委婉语、暗示或模糊表述，也不要弱化、淡化、"净化"或美化原文内容。
3. 不要意译或润色。宁可保持原文的句式和语序，也不要为了文采而改变原意。
4. 人名、片名、厂牌名（Studio）一律保留原文拉丁字母写法，绝对不要音译成汉字。
   例：Count Vladimir 就译作 "Count Vladimir"，不要写成 "弗拉基米尔伯爵"；Staxus 保持 "Staxus"。
5. 保持档案记录语气：只陈述原文内容，不添加评价、不添加原文没有的粗俗词。

处理特殊内容：涉及性行为、身体部位、性取向等成人内容时，照实翻译，使用中文成人语境中通用的直接说法，不要因内容露骨而删减、跳过或含糊处理。

只输出 JSON，不要输出任何解释、前言或 Markdown 代码块。

输出格式（必须严格遵守）：
{"translations": [{"i": 1, "zh": "第一条译文"}, {"i": 2, "zh": "第二条译文"}]}
其中 i 是输入的序号，必须与输入一一对应，不得遗漏或调换顺序。"""


# --------------------------------------------------------------------------
# Providers
# --------------------------------------------------------------------------

class TranslationError(RuntimeError):
    pass


class Provider:
    """Base class: one JSON-mode chat completion returning a list of strings."""

    def __init__(self, api_key: str, model: str, base_url: str = "", timeout: float = 180.0):
        self.api_key = api_key
        self.model = model
        self.base_url = base_url.rstrip("/")
        self.timeout = timeout

    def _post(self, url: str, payload: dict, headers: dict) -> dict:
        data = json.dumps(payload).encode("utf-8")
        req = urllib.request.Request(url, data=data, headers=headers, method="POST")
        try:
            with urllib.request.urlopen(req, timeout=self.timeout) as resp:
                return json.loads(resp.read().decode("utf-8", errors="ignore"))
        except urllib.error.HTTPError as e:
            detail = e.read().decode("utf-8", errors="ignore")[:500]
            raise TranslationError(f"HTTP {e.code} from {url}: {detail}") from e
        except urllib.error.URLError as e:
            raise TranslationError(f"Network error calling {url}: {e.reason}") from e
        except json.JSONDecodeError as e:
            raise TranslationError(f"Non-JSON response from {url}: {e}") from e

    def translate(self, texts: list[str]) -> list[str]:
        raise NotImplementedError


class AnthropicProvider(Provider):
    """Anthropic Messages API with a JSON-schema structured output."""

    DEFAULT_MODEL = "claude-opus-5"
    DEFAULT_BASE_URL = "https://api.anthropic.com"

    SCHEMA = {
        "type": "object",
        "properties": {
            "translations": {
                "type": "array",
                "items": {
                    "type": "object",
                    "properties": {
                        "i": {"type": "integer"},
                        "zh": {"type": "string"},
                    },
                    "required": ["i", "zh"],
                    "additionalProperties": False,
                },
            }
        },
        "required": ["translations"],
        "additionalProperties": False,
    }

    def translate(self, texts: list[str]) -> list[str]:
        numbered = "\n".join(f"{i}. {t}" for i, t in enumerate(texts, 1))
        payload = {
            "model": self.model,
            "max_tokens": 16000,
            "system": SYSTEM_PROMPT,
            "messages": [{"role": "user", "content": f"请翻译以下 {len(texts)} 条简介：\n\n{numbered}"}],
            "output_config": {
                "effort": "low",  # mechanical translation task: no deep reasoning needed
                "format": {"type": "json_schema", "schema": self.SCHEMA},
            },
        }
        body = self._post(
            f"{self.base_url or self.DEFAULT_BASE_URL}/v1/messages",
            payload,
            {
                "content-type": "application/json",
                "x-api-key": self.api_key,
                "anthropic-version": "2023-06-01",
            },
        )
        # A refusal stops with an empty/partial content list; surface it as a retryable error.
        if body.get("stop_reason") == "refusal":
            raise TranslationError("Model declined the request (stop_reason=refusal)")
        text = "".join(b.get("text", "") for b in body.get("content", []) if b.get("type") == "text")
        return extract_translations(text, len(texts))


class OpenAICompatProvider(Provider):
    """Any OpenAI-compatible /chat/completions endpoint."""

    DEFAULT_MODEL = "deepseek-chat"
    DEFAULT_BASE_URL = "https://api.deepseek.com"

    def translate(self, texts: list[str]) -> list[str]:
        numbered = "\n".join(f"{i}. {t}" for i, t in enumerate(texts, 1))
        payload = {
            "model": self.model,
            "messages": [
                {"role": "system", "content": SYSTEM_PROMPT},
                {"role": "user", "content": f"请翻译以下 {len(texts)} 条简介：\n\n{numbered}"},
            ],
            "response_format": {"type": "json_object"},
            "temperature": 0.3,
        }
        body = self._post(
            f"{self.base_url or self.DEFAULT_BASE_URL}/chat/completions",
            payload,
            {
                "Content-Type": "application/json",
                "Authorization": f"Bearer {self.api_key}",
            },
        )
        try:
            text = body["choices"][0]["message"]["content"]
        except (KeyError, IndexError) as e:
            raise TranslationError(f"Unexpected response shape: {json.dumps(body)[:400]}") from e
        return extract_translations(text, len(texts))


class GeminiProvider(Provider):
    """Google Gemini generateContent with a JSON response MIME type."""

    DEFAULT_MODEL = "gemini-2.5-flash"
    DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com"

    def translate(self, texts: list[str]) -> list[str]:
        numbered = "\n".join(f"{i}. {t}" for i, t in enumerate(texts, 1))
        base = self.base_url or self.DEFAULT_BASE_URL
        payload = {
            "systemInstruction": {"parts": [{"text": SYSTEM_PROMPT}]},
            "contents": [{"role": "user", "parts": [{"text": f"请翻译以下 {len(texts)} 条简介：\n\n{numbered}"}]}],
            "generationConfig": {"responseMimeType": "application/json", "temperature": 0.3},
        }
        body = self._post(
            f"{base}/v1beta/models/{self.model}:generateContent",
            payload,
            {
                "Content-Type": "application/json",
                "x-goog-api-key": self.api_key,
            },
        )
        try:
            text = "".join(
                p.get("text", "")
                for p in body["candidates"][0]["content"]["parts"]
            )
        except (KeyError, IndexError) as e:
            raise TranslationError(f"Unexpected response shape: {json.dumps(body)[:400]}") from e
        return extract_translations(text, len(texts))


PROVIDERS: dict[str, type[Provider]] = {
    "anthropic": AnthropicProvider,
    "openai": OpenAICompatProvider,
    "gemini": GeminiProvider,
}


def extract_translations(raw: str, expected: int) -> list[str]:
    """Pull the ordered translation list out of a model response.

    Returns a list of length `expected`; entries the model failed to return are
    empty strings so the caller can retry exactly those.
    """
    text = raw.strip()
    # Tolerate a stray ```json fence even though the prompt forbids one.
    if text.startswith("```"):
        text = re.sub(r"^```[a-zA-Z]*\s*", "", text)
        text = re.sub(r"\s*```$", "", text)

    data: Any = None
    try:
        data = json.loads(text)
    except json.JSONDecodeError:
        # Fall back to the outermost {...} span.
        start, end = text.find("{"), text.rfind("}")
        if start != -1 and end > start:
            try:
                data = json.loads(text[start:end + 1])
            except json.JSONDecodeError:
                data = None
    if data is None:
        raise TranslationError(f"Could not parse JSON from model output: {raw[:300]}")

    items = data.get("translations") if isinstance(data, dict) else data
    if not isinstance(items, list):
        raise TranslationError(f"Missing 'translations' array in: {raw[:300]}")

    out = [""] * expected
    for pos, item in enumerate(items):
        if isinstance(item, str):
            idx, value = pos, item
        elif isinstance(item, dict):
            idx = item.get("i", pos + 1)
            value = item.get("zh") or item.get("translation") or ""
            try:
                idx = int(idx) - 1
            except (TypeError, ValueError):
                idx = pos
        else:
            continue
        if 0 <= idx < expected and value:
            out[idx] = str(value).strip()
    return out


# --------------------------------------------------------------------------
# Configuration
# --------------------------------------------------------------------------

def load_config() -> dict:
    if CONFIG_FILE.exists():
        try:
            return json.loads(CONFIG_FILE.read_text(encoding="utf-8"))
        except (json.JSONDecodeError, OSError) as e:
            print(f"⚠️  配置文件 {CONFIG_FILE.name} 读取失败，已忽略: {e}", file=sys.stderr)
    return {}


def resolve_settings(args) -> dict:
    cfg = load_config()
    return {
        "provider": args.provider or os.environ.get("GEVI_LLM_PROVIDER") or cfg.get("provider") or "",
        "api_key": args.api_key or os.environ.get("GEVI_LLM_API_KEY") or cfg.get("api_key") or "",
        "model": args.model or os.environ.get("GEVI_LLM_MODEL") or cfg.get("model") or "",
        "base_url": args.base_url or os.environ.get("GEVI_LLM_BASE_URL") or cfg.get("base_url") or "",
    }


def build_provider(settings: dict) -> Provider:
    name = (settings["provider"] or "").strip().lower()
    if not name:
        raise SystemExit(
            "❌ 未指定翻译服务商。请用 --provider 指定 anthropic / openai / gemini，\n"
            "   或设置环境变量 GEVI_LLM_PROVIDER。\n"
            f"   例: python3 translate.py --provider openai --api-key sk-..."
        )
    if name not in PROVIDERS:
        raise SystemExit(f"❌ 未知的服务商 '{name}'，可选: {', '.join(PROVIDERS)}")
    if not settings["api_key"]:
        raise SystemExit(
            "❌ 缺少 API Key。请用 --api-key 传入，或设置环境变量 GEVI_LLM_API_KEY，\n"
            "   或在 translate_config.json 中写入 {\"api_key\": \"...\"}。"
        )
    cls = PROVIDERS[name]
    return cls(
        api_key=settings["api_key"],
        model=settings["model"] or cls.DEFAULT_MODEL,
        base_url=settings["base_url"] or cls.DEFAULT_BASE_URL,
    )


# --------------------------------------------------------------------------
# Batch driver
# --------------------------------------------------------------------------

def run_translation(
    db: DatabaseManager,
    provider: Provider,
    limit: int | None,
    batch_size: int,
    workers: int,
    dry_run: bool,
) -> None:
    rows = db.get_untranslated_movies(limit=limit)
    total = len(rows)
    if total == 0:
        print("🎉 没有需要翻译的影片（全部已翻译或没有简介）。")
        return

    batches = [rows[i:i + batch_size] for i in range(0, total, batch_size)]
    print("=" * 70)
    print(f"🌐 GEVI 剧情简介批量翻译 | 服务商: {provider.__class__.__name__} | 模型: {provider.model}")
    print(f"   待翻译: {total:,} 条 | 批次大小: {batch_size} | 批次数: {len(batches)} | 并发: {workers}")
    if dry_run:
        print("   ⚠️  试运行模式 (--dry-run)：只翻译不写库")
    print("=" * 70)

    t0 = time.time()
    done = 0
    saved = 0
    failed = 0
    progress_lock = threading.Lock()

    def handle(batch: list[dict]) -> tuple[int, int]:
        texts = [r["description"] for r in batch]
        try:
            results = provider.translate(texts)
        except TranslationError as e:
            print(f"\n  ⚠️  批次失败: {e}", file=sys.stderr)
            return 0, len(batch)

        # Retry dropped items individually so a partial batch is not lost.
        for i, v in enumerate(results):
            if not v:
                try:
                    single = provider.translate([texts[i]])
                    if single and single[0]:
                        results[i] = single[0]
                except TranslationError:
                    pass

        batch_saved = 0
        batch_failed = 0
        for row, zh in zip(batch, results):
            if not zh:
                batch_failed += 1
                if not dry_run:
                    db.set_movie_translation(row["id"], None)
                continue
            batch_saved += 1
            if dry_run:
                if batch_saved <= 2:
                    print(f"\n  #{row['id']} {row['title']}\n    EN: {row['description'][:110]}\n    ZH: {zh[:110]}")
            else:
                db.set_movie_translation(row["id"], zh)
        return batch_saved, batch_failed

    with ThreadPoolExecutor(max_workers=workers) as pool:
        futures = [pool.submit(handle, b) for b in batches]
        for fut in as_completed(futures):
            s, f = fut.result()
            with progress_lock:
                saved += s
                failed += f
                done += 1
                elapsed = time.time() - t0
                rate = saved / elapsed if elapsed > 0 else 0
                sys.stdout.write(
                    f"\r[{done}/{len(batches)} 批] ({done / len(batches) * 100:5.1f}%) | "
                    f"已翻译: {saved:,} | 失败: {failed:,} | Speed: {rate:4.1f} 条/s"
                )
                sys.stdout.flush()

    elapsed = time.time() - t0
    print(f"\n\n✨ 翻译完成！耗时 {elapsed:.1f}s | 成功 {saved:,} 条 | 失败 {failed:,} 条")
    if failed and not dry_run:
        print("   失败条目已记录重试次数，可再次运行本命令自动重试。")


def main():
    parser = argparse.ArgumentParser(description="GEVI 影片简介批量翻译 (EN -> ZH)")
    parser.add_argument("--provider", choices=list(PROVIDERS), help="翻译服务商")
    parser.add_argument("--api-key", help="API Key")
    parser.add_argument("--model", help="模型名（留空则用服务商默认模型）")
    parser.add_argument("--base-url", help="自定义 API 端点")
    parser.add_argument("--db", type=str, default="gevi.db", help="SQLite 数据库路径")
    parser.add_argument("--limit", type=int, default=None, help="本次最多翻译多少条")
    parser.add_argument("--batch-size", type=int, default=20, help="每批翻译多少条 (默认 20)")
    parser.add_argument("--workers", type=int, default=4, help="并发批次数 (默认 4)")
    parser.add_argument("--dry-run", action="store_true", help="试运行：只翻译不写库")
    parser.add_argument("--stats", action="store_true", help="只打印翻译进度统计后退出")
    args = parser.parse_args()

    db = DatabaseManager(str(Path(args.db).resolve()))

    if args.stats:
        s = db.get_translation_stats()
        print("\n📊 【剧情简介翻译进度】")
        print(f"  - 可翻译简介总数: {s['translatable']:,}")
        print(f"  - 已翻译 (中文):  {s['translated']:,}")
        print(f"  - 待翻译:         {s['pending']:,}")
        print(f"  - 翻译失败:       {s['failed']:,}")
        print(f"  - 完成度:         {(s['translated'] / s['translatable'] * 100) if s['translatable'] else 0:.1f}%\n")
        return

    provider = build_provider(resolve_settings(args))
    run_translation(
        db=db,
        provider=provider,
        limit=args.limit,
        batch_size=args.batch_size,
        workers=args.workers,
        dry_run=args.dry_run,
    )


if __name__ == "__main__":
    main()
