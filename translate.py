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
  1. CLI flags      --provider --api-key --model --base-url --profile
  2. Environment    GEVI_LLM_PROVIDER / GEVI_LLM_API_KEY / GEVI_LLM_MODEL / GEVI_LLM_BASE_URL
  3. Config file    translate_config.json next to this script

The config file holds several named sources, so you can keep a cheap model for bulk
work and a better one for spot fixes and switch between them:

  {"active": "deepseek",
   "profiles": {"deepseek": {"type": "openai", "label": "DeepSeek",
                             "api_key": "sk-...", "model": "deepseek-flash",
                             "base_url": "https://api.deepseek.com"},
                "claude":   {"type": "anthropic", "label": "Anthropic Claude",
                             "api_key": "sk-ant-...", "model": "claude-opus-5"}}}

Manage them from the app's Settings tab, or with:
  python3 translate.py --list-profiles
  python3 translate.py --profile claude --limit 20 --dry-run

Usage:
  python3 translate.py --provider openai --api-key sk-... --limit 20 --dry-run
  python3 translate.py --profile deepseek --batch-size 20
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
import urllib.parse
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


# Film titles need their own prompt, and can NOT reuse SYSTEM_PROMPT above:
#
# * Its rule 4 lists 片名 among the things that must stay in Latin letters, so the
#   model would faithfully hand back the English title and the whole run would be a
#   no-op that still cost money. (GLOSSARY_SYSTEM_PROMPT has the mirror-image problem:
#   its rule 1 forbids the parenthetical a pun sometimes needs.)
# * Its rule 3 (「不要意译或润色」) is exactly wrong here. A pun cannot survive a literal
#   translation — 意译 is the whole job.
TITLE_SYSTEM_PROMPT = """你是一名成人影片资料库的专职译者。你会收到一批英文片名（部分附带剧情简介作为背景），需要逐个译成简体中文。

这些片名大量使用双关语、谐音、俚语和性暗示，用来指代影片的主打内容、欲望、男性性特征等成人内容。翻译的核心就是把这些隐喻在中文里落地。

翻译要求：
1. 优先把双关的意思**化进中文片名本身**，让中文读者不看原文也能领会那一层意思。
   例：Creamy Ranch 里的 creamy 既指「浓郁」也指精液，中文片名要让这层意思还在；
   Full Depth 里的 depth 是体位上的双关，译名要保住。
2. **只有当中文确实承载不了这个双关时**，才在片名后加一个极短的括号点明，括号内不超过 6 个字。
   能融进正文就绝不加括号。
3. 保留系列编号（Part 2、Vol. 3）、人名、厂牌名的拉丁字母写法，不要音译。
   例：Count Vladimir 保持 "Count Vladimir"，不要写成「弗拉基米尔伯爵」。
4. 不要音译；不要把英文原名原样再抄一遍当作译文。
5. 中文片名尽量不超过 20 个字，要像一个片名，不要写成一句解释。
6. 附带的简介只用来判断片名里的双关指向什么，**不要翻译简介本身**。

只输出 JSON，不要输出任何解释、前言或 Markdown 代码块。

输出格式（必须严格遵守）：
{"translations": [{"src": "英文原名", "zh": "中文译名"}]}
src 必须**逐字符照抄**输入里的英文原名（含大小写、撇号、标点），一条都不能省略、不能改写。
不要合并相同的片名：输入里有几条就输出几条 —— 本模式按 src 而不是按位置对应。"""


# How much of a synopsis rides along as pun context, and how much of it the dry-run
# listing echoes back. The verdict only needs the premise: a 2,000-character synopsis
# costs tokens on every title in the batch for no extra signal, and pasting it into
# the listing would bury the translations the run exists to show.
TITLE_CONTEXT_CHARS = 300
TITLE_CONTEXT_SHOWN = 60


# Content tags rather than prose: a fixed vocabulary where the target is the wording a
# Chinese catalogue would actually use.
CATEGORY_SYSTEM_PROMPT = """你是一名成人影片资料库的术语译者。你会收到一批影片分类标签（英文），需要逐个译成简体中文。

分类标签描述的是影片的内容类型、题材或卖点，属于固定小词表。

翻译要求：
1. 用中文同类资料库里常见的说法，简洁、直接，2 到 8 个字。
   例：Twink→嫩弟，Bareback→无套，Wrestling→摔角，General Hardcore→普通硬核。
2. 不要解释、不要加括号补充、不要音译。
3. 缩写和符号按行业内的约定处理：J/O→自慰，S/M→性虐，POV→第一人称视角。
   注意 J/O 是 jerk off 的缩写，指**自己**撸，不要译成「手交」——那是别人用手施加的
   handjob，是另一个行为。
4. 不确定时按字面直译，不要臆造。

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

    def __init__(self, api_key: str, model: str, base_url: str = "",
                 timeout: float = 180.0, system_prompt: str | None = None,
                 noun: str = "简介", echo_source: bool = False):
        self.api_key = api_key
        self.model = model
        self.base_url = base_url.rstrip("/")
        self.timeout = timeout
        # Which prompt to use depends on what is being translated (synopses vs the
        # attribute glossary vs film titles); everything else about the call is
        # identical.
        self.system_prompt = system_prompt or SYSTEM_PROMPT
        # Only the wording of the user turn; the system prompt carries the rules.
        self.noun = noun
        # Ask the model to echo each source string back with its translation, and have
        # extract_translations match on that instead of on position. See the note on
        # `sources` in extract_translations for why titles need it.
        self.echo_source = echo_source

    def user_turn(self, texts: list[str], contexts: list[str] | None = None) -> str:
        if contexts is None:
            numbered = "\n".join(f"{i}. {t}" for i, t in enumerate(texts, 1))
        else:
            # Title mode: the source line is what gets translated and echoed back as
            # `src`; the context is background for judging a pun, labelled so the
            # model does not translate it too.
            numbered = "\n\n".join(
                f"{i}. {t}\n   剧情背景（仅供判断双关，不要翻译）：{c}"
                for i, (t, c) in enumerate(zip(texts, contexts), 1)
            )
        return f"请翻译以下 {len(texts)} 条{self.noun}：\n\n{numbered}"

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

    def translate(self, texts: list[str],
                  contexts: list[str] | None = None) -> list[str]:
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

    def schema(self) -> dict:
        """SCHEMA, or the src-echoing variant when echo_source is on.

        A structured-output request is enforced, not suggested: leaving `src` out of
        the schema while the prompt asks for it would have the API strip the field,
        and every title would come back unmatched.
        """
        if not self.echo_source:
            return self.SCHEMA
        item = {
            "type": "object",
            "properties": {"src": {"type": "string"}, "zh": {"type": "string"}},
            "required": ["src", "zh"],
            "additionalProperties": False,
        }
        return {
            "type": "object",
            "properties": {"translations": {"type": "array", "items": item}},
            "required": ["translations"],
            "additionalProperties": False,
        }

    def translate(self, texts: list[str],
                  contexts: list[str] | None = None) -> list[str]:
        payload = {
            "model": self.model,
            "max_tokens": 16000,
            "system": self.system_prompt,
            "messages": [{"role": "user", "content": self.user_turn(texts, contexts)}],
            "output_config": {
                "effort": "low",  # mechanical translation task: no deep reasoning needed
                "format": {"type": "json_schema", "schema": self.schema()},
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
        return extract_translations(text, len(texts), sources=texts if self.echo_source else None)


class OpenAICompatProvider(Provider):
    """Any OpenAI-compatible /chat/completions endpoint."""

    DEFAULT_MODEL = "deepseek-chat"
    DEFAULT_BASE_URL = "https://api.deepseek.com"

    def translate(self, texts: list[str],
                  contexts: list[str] | None = None) -> list[str]:
        payload = {
            "model": self.model,
            "messages": [
                {"role": "system", "content": self.system_prompt},
                {"role": "user", "content": self.user_turn(texts, contexts)},
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
        return extract_translations(text, len(texts), sources=texts if self.echo_source else None)


class GeminiProvider(Provider):
    """Google Gemini generateContent with a JSON response MIME type."""

    DEFAULT_MODEL = "gemini-2.5-flash"
    DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com"

    def translate(self, texts: list[str],
                  contexts: list[str] | None = None) -> list[str]:
        base = self.base_url or self.DEFAULT_BASE_URL
        payload = {
            "systemInstruction": {"parts": [{"text": self.system_prompt}]},
            "contents": [{"role": "user", "parts": [{"text": self.user_turn(texts, contexts)}]}],
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
        return extract_translations(text, len(texts), sources=texts if self.echo_source else None)


PROVIDERS: dict[str, type[Provider]] = {
    "anthropic": AnthropicProvider,
    "openai": OpenAICompatProvider,
    "gemini": GeminiProvider,
}


def extract_translations(raw: str, expected: int,
                         sources: list[str] | None = None) -> list[str]:
    """Pull the ordered translation list out of a model response.

    Returns a list of length `expected`; entries the model failed to return are
    empty strings so the caller can retry exactly those.

    `sources` switches the matching from position to content. Pass the exact input
    list and each result is matched by the `src` the model echoed back; anything
    that does not match a source comes back empty and is retried.

    Why titles need this: the positional path below trusts `i`, and falls back to the
    item's position when `i` is missing. That is safe for 470-character synopses,
    where a shift is obvious on sight. For an 18-character title it is neither
    visible nor recoverable - the model merges duplicate lines (the library has
    3,365 repeated titles; "Boys Will Be Boys" appears 17 times), every later entry
    slides up one, and because the values are all non-empty the per-item retry in
    `translate_batch` never fires. The wrong translation is then written over a
    title that had nothing to do with it, permanently.

    With `sources` the failure mode becomes a miss, not a shift: an unmatched or
    merged entry returns "" and gets retried. The cost is ~40% more output tokens,
    which for a title is nothing.
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

    if sources is not None:
        return _match_by_source(items, sources)

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


def _norm_source(s: str) -> str:
    """Casefold and collapse whitespace, for matching an echoed source loosely.

    Titles carry apostrophes, ampersands and double spaces ("Guess Who's Cummin' to
    Dinner", "Nutt  Crackers"), and a model asked to echo one back may "tidy" it. An
    exact-only match would then call every entry a miss, and the whole run would
    return empty — the safe direction, but a total loss.
    """
    return " ".join(s.casefold().split())


def _match_by_source(items: list, sources: list[str]) -> list[str]:
    """Resolve a src-echoing response against the inputs it claims to answer.

    Deliberately no positional fallback: an entry whose `src` matches no input is
    dropped (so it is retried) rather than being placed at the item's index, which
    is the shift this whole protocol exists to prevent. A loose match (case and
    whitespace only) is still a match on content, so it keeps that guarantee.

    A source that appears twice in one batch resolves to the same translation for
    both - that is the desired behaviour, not a collision. (It cannot arise from the
    title path as collected today, since `get_untranslated_titles` groups by title,
    but a dict keyed by source must not silently drop one of them.)
    """
    # Indexed to a *list* of positions, not one: a plain `{s: i}` keeps only the last
    # occurrence of a repeated source, so the earlier ones quietly came back empty.
    exact: dict[str, list[int]] = {}
    loose: dict[str, list[int]] = {}
    for i, s in enumerate(sources):
        exact.setdefault(s, []).append(i)
        loose.setdefault(_norm_source(s), []).append(i)

    out = [""] * len(sources)
    unmatched: list[str] = []
    for item in items:
        if not isinstance(item, dict):
            continue
        src = item.get("src") or item.get("source") or item.get("en") or ""
        value = item.get("zh") or item.get("translation") or ""
        if not value:
            continue
        key = str(src).strip()
        slots = exact.get(key)
        if slots is None:
            slots = loose.get(_norm_source(key))
        if slots is None:
            unmatched.append(key or "<空>")
        else:
            for i in slots:
                out[i] = str(value).strip()
    if unmatched:
        # Surfaced rather than silent: a systematic mismatch (the model translating
        # the source line instead of echoing it) looks exactly like "the model
        # dropped everything", and the fix is a prompt change, not a retry.
        print(f"  ⚠️  {len(unmatched)} 条译文对不上原文，将作为漏译重试: "
              f"{', '.join(unmatched[:3])}", file=sys.stderr)
    return out


# --------------------------------------------------------------------------
# Configuration
# --------------------------------------------------------------------------

# Sources the settings UI offers to prefill. `type` selects the wire protocol, so
# every OpenAI-compatible vendor (which is nearly all of them) reuses one adapter.
PROVIDER_PRESETS: list[dict] = [
    {"id": "deepseek", "label": "DeepSeek 深度求索", "type": "openai",
     "base_url": "https://api.deepseek.com", "model": "deepseek-flash",
     "hint": "便宜、快。国内可直连。"},
    {"id": "openai", "label": "OpenAI", "type": "openai",
     "base_url": "https://api.openai.com/v1", "model": "gpt-4o-mini",
     "hint": "需要能访问 OpenAI 的网络环境。"},
    {"id": "anthropic", "label": "Anthropic Claude", "type": "anthropic",
     "base_url": "https://api.anthropic.com", "model": "claude-opus-5",
     "hint": "译文质量最好，价格最高。"},
    {"id": "gemini", "label": "Google Gemini", "type": "gemini",
     "base_url": "https://generativelanguage.googleapis.com", "model": "gemini-2.5-flash",
     "hint": "有免费额度，需要能访问 Google。"},
    {"id": "moonshot", "label": "Moonshot 月之暗面 (Kimi)", "type": "openai",
     "base_url": "https://api.moonshot.cn/v1", "model": "moonshot-v1-8k",
     "hint": "中文语感好。"},
    {"id": "zhipu", "label": "智谱 AI (GLM)", "type": "openai",
     "base_url": "https://open.bigmodel.cn/api/paas/v4", "model": "glm-4-flash",
     "hint": "glm-4-flash 有免费额度。"},
    {"id": "dashscope", "label": "阿里通义千问", "type": "openai",
     "base_url": "https://dashscope.aliyuncs.com/compatible-mode/v1", "model": "qwen-plus",
     "hint": "阿里云百炼平台。"},
    {"id": "siliconflow", "label": "硅基流动 SiliconFlow", "type": "openai",
     "base_url": "https://api.siliconflow.cn/v1", "model": "Qwen/Qwen2.5-7B-Instruct",
     "hint": "聚合多家开源模型。"},
    {"id": "ollama", "label": "本地 Ollama", "type": "openai",
     "base_url": "http://localhost:11434/v1", "model": "qwen2.5:7b",
     "hint": "完全离线、不花钱，需要本机已装 Ollama 并 pull 过模型。",
     "needs_key": False},
    {"id": "custom", "label": "自定义 (OpenAI 兼容接口)", "type": "openai",
     "base_url": "", "model": "", "hint": "任何兼容 /chat/completions 的服务。"},
]


def load_config() -> dict:
    if CONFIG_FILE.exists():
        try:
            return json.loads(CONFIG_FILE.read_text(encoding="utf-8"))
        except (json.JSONDecodeError, OSError) as e:
            print(f"⚠️  配置文件 {CONFIG_FILE.name} 读取失败，已忽略: {e}", file=sys.stderr)
    return {}


def save_config(cfg: dict) -> None:
    """Write the config file with owner-only permissions (it holds API keys)."""
    tmp = CONFIG_FILE.with_suffix(".json.tmp")
    tmp.write_text(json.dumps(cfg, ensure_ascii=False, indent=2), encoding="utf-8")
    os.chmod(tmp, 0o600)
    tmp.replace(CONFIG_FILE)


def name_for_base_url(base_url: str, fallback: str) -> str:
    """Pick a preset id whose host matches, so a source is named after the service
    it points at ("deepseek") rather than the protocol it speaks ("openai")."""
    host = urllib.parse.urlparse(base_url or "").netloc
    if host:
        for preset in PROVIDER_PRESETS:
            if preset["base_url"] and urllib.parse.urlparse(preset["base_url"]).netloc == host:
                return preset["id"]
    return fallback


def migrate_config(cfg: dict) -> dict:
    """Normalise any older config shape into the current one (idempotent).

    Current shape:
        {"active": "<profile name>", "profiles": {"<name>": {type, label, api_key,
                                                             model, base_url}}}

    Older versions stored the settings flat, and briefly kept a `providers` map
    keyed by protocol name. Both are folded into `profiles`, and the superseded
    keys are dropped so the file never holds two copies of an API key.
    """
    if "profiles" in cfg:
        return cfg

    profiles: dict[str, dict] = {}
    flat_key = cfg.get("api_key") or ""
    legacy = cfg.get("providers") or {}
    for name, entry in legacy.items():
        if not isinstance(entry, dict):
            continue
        base_url = entry.get("base_url") or ""
        ptype = entry.get("type") or name
        key = name_for_base_url(base_url, name)
        profiles[key] = {
            "type": ptype,
            "label": entry.get("label") or next(
                (p["label"] for p in PROVIDER_PRESETS if p["id"] == key), name),
            "api_key": entry.get("api_key") or "",
            "model": entry.get("model") or "",
            "base_url": base_url,
        }

    if flat_key and not any(p.get("api_key") for p in profiles.values()):
        provider = (cfg.get("provider") or "openai").strip().lower()
        base_url = cfg.get("base_url") or ""
        key = name_for_base_url(base_url, provider)
        profiles[key] = {
            "type": provider,
            "label": next((p["label"] for p in PROVIDER_PRESETS if p["id"] == key), key),
            "api_key": flat_key,
            "model": cfg.get("model") or "",
            "base_url": base_url,
        }

    if profiles:
        cfg["profiles"] = profiles
        active = cfg.get("active")
        # A legacy "provider" naming the protocol still identifies the source when
        # only one was saved, which is exactly the single-source upgrade case.
        if active not in profiles:
            legacy_name = (cfg.get("provider") or "").strip().lower()
            if len(profiles) == 1 and legacy_name in profiles:
                active = legacy_name
        cfg["active"] = active if active in profiles else next(iter(profiles))

    for obsolete in ("provider", "api_key", "model", "base_url", "providers"):
        cfg.pop(obsolete, None)
    return cfg


def list_profiles() -> list[dict]:
    """Every saved source, with the API key reduced to a yes/no flag.

    The key itself never leaves this module: the settings UI only ever learns
    whether one is stored, never what it is.
    """
    cfg = migrate_config(load_config())
    active = cfg.get("active") or ""
    out = []
    for name, p in (cfg.get("profiles") or {}).items():
        key = p.get("api_key") or ""
        out.append({
            "name": name,
            "label": p.get("label") or name,
            "type": p.get("type") or "openai",
            "model": p.get("model") or "",
            "base_url": p.get("base_url") or "",
            "has_key": bool(key),
            "key_hint": f"{key[:6]}…{key[-4:]}" if len(key) > 12 else ("已保存" if key else ""),
            "active": name == active,
        })
    out.sort(key=lambda p: (not p["active"], p["label"]))
    return out


def save_profile(name: str, data: dict) -> None:
    """Create or update one source. An absent api_key keeps the stored one.

    That omission is deliberate: the UI is never given the key back, so a form
    saved without retyping it must not erase it.
    """
    name = (name or "").strip()
    if not name:
        raise ValueError("配置名称不能为空")
    cfg = migrate_config(load_config())
    profiles = cfg.setdefault("profiles", {})
    entry = profiles.get(name, {})
    for field in ("type", "label", "model", "base_url"):
        if field in data and data[field] is not None:
            entry[field] = str(data[field]).strip()
    if data.get("api_key"):
        entry["api_key"] = str(data["api_key"]).strip()
    entry.setdefault("type", "openai")
    entry.setdefault("label", name)
    profiles[name] = entry
    if not cfg.get("active"):
        cfg["active"] = name
    save_config(cfg)


def set_active_profile(name: str) -> None:
    cfg = migrate_config(load_config())
    if name not in (cfg.get("profiles") or {}):
        raise ValueError(f"没有名为 '{name}' 的翻译服务配置")
    cfg["active"] = name
    save_config(cfg)


def delete_profile(name: str) -> None:
    cfg = migrate_config(load_config())
    profiles = cfg.get("profiles") or {}
    if name not in profiles:
        raise ValueError(f"没有名为 '{name}' 的翻译服务配置")
    del profiles[name]
    if cfg.get("active") == name:
        cfg["active"] = next(iter(profiles), "")
    save_config(cfg)


def resolve_settings(args, profile: str | None = None) -> dict:
    """Resolve settings for one source.

    Precedence: CLI flag > environment variable > the named profile > the active
    profile. Environment variables still win over the file so that a one-off
    `GEVI_LLM_API_KEY=... python3 translate.py` keeps working.
    """
    cfg = migrate_config(load_config())
    profiles = cfg.get("profiles") or {}
    name = profile or getattr(args, "profile", None) or cfg.get("active") or ""
    entry = profiles.get(name, {})

    def pick(attr: str, env: str, key: str, default: str = "") -> str:
        return (getattr(args, attr, None) or os.environ.get(env)
                or entry.get(key) or cfg.get(key) or default)

    return {
        "profile": name,
        "provider": pick("provider", "GEVI_LLM_PROVIDER", "type"),
        "api_key": pick("api_key", "GEVI_LLM_API_KEY", "api_key"),
        "model": pick("model", "GEVI_LLM_MODEL", "model"),
        "base_url": pick("base_url", "GEVI_LLM_BASE_URL", "base_url"),
    }


def is_local_endpoint(base_url: str) -> bool:
    """True for a server on this machine, which authenticates by being local.

    Ollama and friends expose an OpenAI-compatible endpoint that ignores the
    Authorization header, so demanding a key for them would be wrong.
    """
    host = (urllib.parse.urlparse(base_url or "").hostname or "").lower()
    return host in ("localhost", "127.0.0.1", "::1", "0.0.0.0")


def build_provider(settings: dict, system_prompt: str | None = None,
                   noun: str = "简介", echo_source: bool = False) -> Provider:
    name = (settings["provider"] or "").strip().lower()
    if not name:
        raise SystemExit(
            "❌ 未指定翻译服务商。请用 --provider 指定 anthropic / openai / gemini，\n"
            "   或设置环境变量 GEVI_LLM_PROVIDER。\n"
            f"   例: python3 translate.py --provider openai --api-key sk-..."
        )
    if name not in PROVIDERS:
        raise SystemExit(f"❌ 未知的服务商 '{name}'，可选: {', '.join(PROVIDERS)}")
    if not settings["api_key"] and not is_local_endpoint(settings["base_url"]):
        raise SystemExit(
            "❌ 缺少 API Key。请用 --api-key 传入，或设置环境变量 GEVI_LLM_API_KEY，\n"
            "   或在 translate_config.json 中写入 {\"api_key\": \"...\"}。"
        )
    cls = PROVIDERS[name]
    return cls(
        api_key=settings["api_key"],
        model=settings["model"] or cls.DEFAULT_MODEL,
        base_url=settings["base_url"] or cls.DEFAULT_BASE_URL,
        system_prompt=system_prompt,
        noun=noun,
        echo_source=echo_source,
    )


# --------------------------------------------------------------------------
# Performer attribute glossary
# --------------------------------------------------------------------------
#
# The performer profile attributes are drawn from a tiny closed vocabulary: the
# seven translatable facet columns hold ~44 distinct values, and the tattoo
# locations add ~30 more. Translating that whole set once costs a single API
# call and then serves every performer forever, including the six-figure set the
# full-site scrape is still filling in.
#
# Two fields are deliberately excluded:
#   - dick_size / height / weight hold "8in / 20cm" style measurement strings —
#     numbers and units are language-neutral, and a glossary entry per size would
#     be noise.
#   - `notes` is not rendered by the client at all.

GLOSSARY_SYSTEM_PROMPT = """你是一名成人影片资料库的术语译者。你会收到一批演员档案里的英文属性词，需要逐个翻译成简体中文。

这些词来自固定的小词表：体型（Swimmer、Trim）、肤色（Olive、Caramel）、眼睛颜色、发型、体毛、胡须、包皮，以及纹身部位（Deltoid、Groin、Chest）。此外还会收到几个度量单位缩写。

翻译要求：
1. 简洁、直接，用中文资料库里常见的说法，不要解释、不要加括号补充。
   例：Swimmer→游泳运动员，Trim→匀称，Body Builder→健美，Goatee→山羊胡，Jaw Line→下颌线，Cut→已割，Uncut→未割。
2. 颜色词（Brown、Blond、Dark Brown）译成对应颜色即可。
3. 身体部位词（Deltoid、Biceps、Forearm、Chest、Groin、Glans、Sternum、Thigh）用解剖学常用中文说法，例如 Deltoid→三角肌，Biceps→肱二头肌，Forearm→前臂，Glans→龟头。
4. 位置短语（left chest、right bicep、base of spine）译成"左胸""右肱二头肌""脊柱底部"这类说法。
5. 度量单位只译单位本身，不要带数字、不要换算：
   ft→英尺，in→英寸（此处是 inch 的缩写，不是介词），lbs→磅，kg→千克，cm→厘米。
6. 大小写与单复数保持原意；不确定时按字面直译，不要臆造。

只输出 JSON，不要输出任何解释、前言或 Markdown 代码块。

输出格式（必须严格遵守）：
{"translations": [{"i": 1, "zh": "第一条译文"}, {"i": 2, "zh": "第二条译文"}]}
其中 i 是输入的序号，必须与输入一一对应，不得遗漏或调换顺序。"""

# Columns whose values are measurement strings or otherwise untranslatable.
GLOSSARY_SKIP_COLUMNS = frozenset({"dick_size"})

# The unit abbreviations inside those measurement strings.
#
# height / weight / dick_size store "5ft 10in / 178cm" — both systems at once — and
# are the reason this set exists rather than a whole-value glossary entry per
# measurement: the units are five fixed tokens, while the values are unbounded and
# churn on every re-scrape. The client rewrites the units inside each value with
# trMeasure(), so a height it has never seen still renders in Chinese.
GLOSSARY_UNIT_TERMS = ("ft", "in", "lbs", "kg", "cm")

# Tattoo values are junkier than the facet columns. A single stored value looks like
#   "Deltoid left Deltoid: \"USMC\", Chest left chest: Chinese dragon"
# i.e. comma-separated entries of "<location> <location>: <free text>", where the
# free text can itself contain further "location: text" pairs, and the leading
# comma item may carry no description at all. Only the locations are worth
# translating — the descriptions are open text ("centaur with bow & arrow") that no
# bounded glossary could cover, so they stay in English.
_TATTOO_MAX_LOCATION_WORDS = 3

# Free-text words that survive the fragment filter below and must not become entries.
#
# The filter accepts a colon-less single-word fragment because real locations are
# listed that way ("Deltoid, Biceps, Forearm"). The cost is that a description which
# itself contains a comma hands its continuation to the same rule: in
# "Deltoid right Deltoid: shark, expanded", the trailing "expanded" looks exactly like
# a location. It is not one, and an entry for it is not harmless — the client renders
# tattoo values by translating each location and passing the rest through, so a bogus
# entry shows up inside the English description as "shark，膨胀".
#
# A blocklist rather than a smarter split: the comma is genuinely ambiguous here, the
# vocabulary is tiny and stable, and this was found by reading the whole collected
# table. Re-check after a full re-scrape with a fresh set of tattoos values.
_TATTOO_FREE_TEXT_STOPWORDS = frozenset({"expanded"})


def _tattoo_location_terms(raw: str | None) -> set[str]:
    """Pull just the location words out of one `performers.tattoos` value.

    The leading comma-separated run is the location list; everything from the first
    colon onwards is free text, and a description that itself contains a comma will
    split into further entries ("…, gothic lttering"). Those fragments are dropped
    by only accepting a colon-less entry when it is a single word — real locations
    are one token, the fragments are not. See _TATTOO_FREE_TEXT_STOPWORDS for the
    stragglers that rule cannot catch.
    """
    from server import split_facet_value  # the one <br /> splitter, shared

    found: set[str] = set()
    for chunk in split_facet_value(raw):
        for entry in chunk.split(","):
            entry = entry.strip()
            if not entry or entry == "?" or entry.lower() == "none":
                continue
            head, sep, _ = entry.partition(":")
            words = head.split()
            if not words:
                continue
            if not sep:
                if len(words) == 1 and words[0] not in _TATTOO_FREE_TEXT_STOPWORDS:
                    found.add(words[0])
                continue
            found.add(words[0])
            rest = " ".join(words[1:])
            if rest and len(words) - 1 <= _TATTOO_MAX_LOCATION_WORDS:
                found.add(rest)
    return found - _TATTOO_FREE_TEXT_STOPWORDS


def collect_glossary_terms(db: DatabaseManager) -> list[str]:
    """Every distinct attribute value worth a glossary entry.

    Terms are returned exactly as stored (already Title Case on the site) because
    the client looks them up with the same string the API handed it — any
    normalisation here would silently miss on the lookup side.
    """
    from server import PERFORMER_FACETS, split_facet_value

    terms: set[str] = set()
    # Not a facet or a column: the units embedded in the measurement strings.
    terms.update(GLOSSARY_UNIT_TERMS)
    for column in PERFORMER_FACETS:
        if column in GLOSSARY_SKIP_COLUMNS:
            continue
        rows = db.conn.execute(
            f"SELECT {column} FROM performers WHERE {column} IS NOT NULL"
        ).fetchall()
        for (raw,) in rows:
            for value in split_facet_value(raw):
                if value and value != "?":
                    terms.add(value)

    rows = db.conn.execute(
        "SELECT tattoos FROM performers WHERE tattoos IS NOT NULL"
    ).fetchall()
    for (raw,) in rows:
        terms.update(_tattoo_location_terms(raw))

    return sorted(terms)


def translate_glossary(db: DatabaseManager, dry_run: bool = False,
                       provider: Provider | None = None) -> dict:
    """Translate the whole attribute vocabulary in one call and store it.

    Already-translated terms are skipped, so re-running costs nothing when nothing
    new has appeared.
    """
    terms = collect_glossary_terms(db)
    existing = db.load_glossary()
    pending = [t for t in terms if t not in existing]

    print("=" * 70)
    print("📖 GEVI 演员属性术语表翻译")
    print(f"   词表共 {len(terms)} 条 | 已译 {len(existing)} 条 | 本次待译 {len(pending)} 条")
    if dry_run:
        print("   ⚠️  试运行模式 (--dry-run)：只翻译不写库")
    print("=" * 70)

    if not pending:
        print("🎉 术语表已是最新，无需调用 API。")
        return {"success": True, "total": len(terms), "translated": 0,
                "failed": 0, "pending": 0, "terms": existing}

    if provider is None:
        provider = build_provider(resolve_settings(argparse.Namespace()),
                                  system_prompt=GLOSSARY_SYSTEM_PROMPT)
    print(f"   服务商: {provider.__class__.__name__} | 模型: {provider.model}")

    # A single call: ~74 short terms comfortably fit one request. extract_translations
    # keeps the result positionally aligned with `pending`, so a dropped term simply
    # comes back empty and gets reported rather than shifting every later entry.
    try:
        results = provider.translate(pending)
    except TranslationError as e:
        print(f"\n  ⚠️  术语表翻译失败: {e}", file=sys.stderr)
        raise

    mapping: dict[str, str] = {}
    failed = 0
    for term, zh in zip(pending, results):
        if zh:
            mapping[term] = zh
        else:
            failed += 1

    if dry_run:
        for term, zh in mapping.items():
            print(f"   {term:24s} → {zh}")
    elif mapping:
        db.save_glossary(mapping)

    print(f"\n{'（试运行，未写库）' if dry_run else '✨ 已写入术语表'}: "
          f"成功 {len(mapping)} 条 | 失败 {failed} 条")
    if failed and not dry_run:
        print("   失败条目未入库，再次运行本命令会自动重试。")

    return {"success": True, "total": len(terms), "translated": len(mapping),
            "failed": failed, "pending": len(pending),
            "terms": db.load_glossary()}


def collect_category_terms(db: DatabaseManager) -> list[str]:
    """Every distinct atomic category term in the library.

    `movies.category` holds raw strings that may carry several tokens joined by the
    site's inline `<br />` separator ("Wrestling<br />J/O"), so the raw values are
    split through the same `split_facet_value` the API uses. The library's 74 raw
    values collapse to 53 atomic terms, and two of the raw values are a bare trailing
    separator ("General Hardcore<br />") whose empty token is dropped here.

    Kept separate from collect_glossary_terms rather than merged into it: the two
    vocabularies, prompts, collectors and lifetimes are all different, and while the
    two sets happen not to overlap today (53 category terms vs ~80 attribute terms),
    that is luck rather than design - several words ("Muscle", "Twink") are plausible
    in both, and `tr()` would then have to guess which table was meant.
    """
    from server import split_facet_value

    terms: set[str] = set()
    rows = db.conn.execute(
        "SELECT DISTINCT category FROM movies "
        "WHERE category IS NOT NULL AND trim(category) != ''"
    ).fetchall()
    for (raw,) in rows:
        for term in split_facet_value(raw):
            if term:
                terms.add(term)
    return sorted(terms)


def translate_categories(db: DatabaseManager, dry_run: bool = False,
                         provider: Provider | None = None) -> dict:
    """Translate the whole category vocabulary in one call and store it.

    Shaped exactly like translate_glossary: 53 terms fit one request comfortably, and
    already-translated terms are skipped so re-running costs nothing when no new
    category has appeared.
    """
    terms = collect_category_terms(db)
    existing = db.load_category_glossary()
    pending = [t for t in terms if t not in existing]

    print("=" * 70)
    print("🏷️  GEVI 影片分类术语表翻译")
    print(f"   词表共 {len(terms)} 条 | 已译 {len(existing)} 条 | 本次待译 {len(pending)} 条")
    if dry_run:
        print("   ⚠️  试运行模式 (--dry-run)：只翻译不写库")
    print("=" * 70)

    if not pending:
        print("🎉 分类术语表已是最新，无需调用 API。")
        return {"success": True, "total": len(terms), "translated": 0,
                "failed": 0, "pending": 0, "terms": existing}

    if provider is None:
        provider = build_provider(resolve_settings(argparse.Namespace()),
                                  system_prompt=CATEGORY_SYSTEM_PROMPT, noun="分类词")
    print(f"   服务商: {provider.__class__.__name__} | 模型: {provider.model}")

    # Positional, like the attribute glossary: one call, no duplicate terms, and the
    # dry-run listing prints every pair for eyeballing before anything is written.
    try:
        results = provider.translate(pending)
    except TranslationError as e:
        print(f"\n  ⚠️  分类术语表翻译失败: {e}", file=sys.stderr)
        raise

    mapping: dict[str, str] = {}
    failed = 0
    for term, zh in zip(pending, results):
        if zh:
            mapping[term] = zh
        else:
            failed += 1

    if dry_run:
        for term, zh in mapping.items():
            print(f"   {term:24s} → {zh}")
    elif mapping:
        db.save_category_glossary(mapping)

    print(f"\n{'（试运行，未写库）' if dry_run else '✨ 已写入分类术语表'}: "
          f"成功 {len(mapping)} 条 | 失败 {failed} 条")
    if failed and not dry_run:
        print("   失败条目未入库，再次运行本命令会自动重试。")

    return {"success": True, "total": len(terms), "translated": len(mapping),
            "failed": failed, "pending": len(pending),
            "terms": db.load_category_glossary()}


# --------------------------------------------------------------------------
# Batch driver
# --------------------------------------------------------------------------

def collect_translation_tasks(db: DatabaseManager, limit: int | None) -> list[dict]:
    """Every untranslated synopsis, as one flat list of {kind, id, title, text}.

    Movies first, then episodes. Keeping both kinds in a single list means the
    batching, retry and write-back logic below stays identical for the two — only
    the `kind` matters when storing the result.
    """
    tasks: list[dict] = []
    for row in db.get_untranslated_movies(limit=None):
        tasks.append({"kind": "movie", "id": row["id"],
                      "title": row["title"], "text": row["description"]})
    for row in db.get_untranslated_episodes(limit=None):
        tasks.append({"kind": "episode", "id": row["id"],
                      "title": row["title"], "text": row["description"]})
    # The cap is applied after merging, so a run without --limit reaches the episode
    # tail while a limited one stays on movies (normally what the button is aimed at).
    if limit:
        tasks = tasks[:limit]
    return tasks


def store_translation(db: DatabaseManager, task: dict, zh: str | None) -> None:
    """Write one result back to the right table. None marks a failed attempt."""
    if task["kind"] == "movie":
        db.set_movie_translation(task["id"], zh)
    else:
        db.set_episode_translation(task["id"], zh)


def translate_batch(provider: Provider, texts: list[str],
                    contexts: list[str] | None = None) -> list[str]:
    """Translate one batch, retrying dropped items one at a time.

    A partial answer is common enough that losing a whole batch to one dropped line
    would matter; retrying per item means only the dropped lines cost a call.

    A batch-level failure returns all-empty rather than falling through to the
    per-item retry: N separate calls for a batch that just failed wholesale is the
    wrong bet, and the empty results record a failed attempt on every row, which is
    what makes the next run pick them up again.
    """
    try:
        results = provider.translate(texts, contexts)
    except TranslationError as e:
        print(f"\n  ⚠️  批次失败: {e}", file=sys.stderr)
        return [""] * len(texts)

    for i, v in enumerate(results):
        if not v:
            try:
                single = provider.translate(
                    [texts[i]], [contexts[i]] if contexts else None
                )
                if single and single[0]:
                    results[i] = single[0]
            except TranslationError:
                pass
    return results


def drive_batches(provider: Provider, batches: list[list[dict]], workers: int,
                  dry_run: bool, handle, t0: float) -> tuple[int, int]:
    """Run every batch through `handle` across a thread pool, reporting progress.

    `handle(batch) -> (saved, failed)` owns what a batch means; this owns the
    concurrency and the one-line progress counter, so the synopsis and title runs
    differ only in their `handle`.
    """
    done = 0
    saved = 0
    failed = 0
    progress_lock = threading.Lock()

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
    return saved, failed


def run_translation(
    db: DatabaseManager,
    provider: Provider,
    limit: int | None,
    batch_size: int,
    workers: int,
    dry_run: bool,
) -> None:
    rows = collect_translation_tasks(db, limit)
    total = len(rows)
    if total == 0:
        print("🎉 没有需要翻译的简介（全部已翻译或没有简介）。")
        return

    n_movies = sum(1 for r in rows if r["kind"] == "movie")
    batches = [rows[i:i + batch_size] for i in range(0, total, batch_size)]
    print("=" * 70)
    print(f"🌐 GEVI 剧情简介批量翻译 | 服务商: {provider.__class__.__name__} | 模型: {provider.model}")
    print(f"   待翻译: {total:,} 条 (影片 {n_movies:,} + 片段 {total - n_movies:,}) | "
          f"批次大小: {batch_size} | 批次数: {len(batches)} | 并发: {workers}")
    if dry_run:
        print("   ⚠️  试运行模式 (--dry-run)：只翻译不写库")
    print("=" * 70)

    def handle(batch: list[dict]) -> tuple[int, int]:
        texts = [r["text"] for r in batch]
        results = translate_batch(provider, texts)

        batch_saved = 0
        batch_failed = 0
        for row, zh in zip(batch, results):
            if not zh:
                batch_failed += 1
                if not dry_run:
                    store_translation(db, row, None)
                continue
            batch_saved += 1
            if dry_run:
                # Only the first two per batch: a synopsis is long enough that
                # printing all of them would bury the progress line.
                if batch_saved <= 2:
                    print(f"\n  [{row['kind']}] #{row['id']} {row['title']}\n"
                          f"    EN: {row['text'][:110]}\n    ZH: {zh[:110]}")
            else:
                store_translation(db, row, zh)
        return batch_saved, batch_failed

    drive_batches(provider, batches, workers, dry_run, handle, time.time())


# --------------------------------------------------------------------------
# Film titles
# --------------------------------------------------------------------------

def collect_title_tasks(db: DatabaseManager, limit: int | None,
                        with_context: bool = True) -> list[dict]:
    """Distinct untranslated titles, as one flat list of {i, title, text}.

    `text` is what rides along as pun context: the longest synopsis of that title's
    films, or the bare title when `with_context` is off (the control group for
    comparing the two runs).
    """
    tasks: list[dict] = []
    for i, row in enumerate(db.get_untranslated_titles(limit=limit)):
        context = (row.get("description") or "") if with_context else ""
        tasks.append({
            "i": i,
            "title": row["title"],
            "text": context[:TITLE_CONTEXT_CHARS],
        })
    return tasks


def run_title_translation(
    db: DatabaseManager,
    provider: Provider,
    limit: int | None,
    batch_size: int,
    workers: int,
    dry_run: bool,
    with_context: bool = True,
) -> None:
    tasks = collect_title_tasks(db, limit, with_context)
    total = len(tasks)
    if total == 0:
        print("🎉 没有需要翻译的片名（全部已翻译或没有片名）。")
        return

    stats = db.get_title_translation_stats()
    batches = [tasks[i:i + batch_size] for i in range(0, total, batch_size)]
    print("=" * 70)
    print(f"🎬 GEVI 片名批量翻译 | 服务商: {provider.__class__.__name__} | 模型: {provider.model}")
    print(f"   全库片名: {stats['translatable']:,} 条 | 已译: {stats['translated']:,} 条 | "
          f"本次待译: {total:,} 条")
    print(f"   批次大小: {batch_size} | 批次数: {len(batches)} | 并发: {workers} | "
          f"简介上下文: {'带' if with_context else '不带'}")
    if dry_run:
        print("   ⚠️  试运行模式 (--dry-run)：只翻译不写库")
    print("=" * 70)

    # Dry-run output is for a human to read and judge, so it is collected per task
    # index and printed at the end in the order the tasks were collected (which
    # get_untranslated_titles already returns alphabetically). Printing from inside
    # `handle` would interleave batches across the worker threads and arrive in
    # completion order, which is exactly the wrong shape for reviewing puns.
    review: list[str] = [""] * total

    def handle(batch: list[dict]) -> tuple[int, int]:
        texts = [r["title"] for r in batch]
        contexts = [r["text"] for r in batch] if with_context else None
        results = translate_batch(provider, texts, contexts)

        batch_saved = 0
        batch_failed = 0
        for row, zh in zip(batch, results):
            if not zh:
                batch_failed += 1
                if not dry_run:
                    db.set_movie_title_translation(row["title"], None)
                continue
            batch_saved += 1
            if dry_run:
                review[row["i"]] = zh
            else:
                db.set_movie_title_translation(row["title"], zh)
        return batch_saved, batch_failed

    drive_batches(provider, batches, workers, dry_run, handle, time.time())

    if dry_run:
        print("\n" + "=" * 70)
        print(f"📋 译文清单（按原文排序，共 {total} 条）")
        print("=" * 70)
        for row, zh in zip(tasks, review):
            mark = "  " if zh else "❌"
            print(f"{mark} {row['title']}")
            if zh:
                print(f"     → {zh}")
                if with_context and row["text"]:
                    print(f"     〔{row['text'][:TITLE_CONTEXT_SHOWN]}〕")
            else:
                print("     → (未译出，下次运行会重试)")
        missing = sum(1 for zh in review if not zh)
        print(f"\n共 {total} 条：译出 {total - missing} 条 | 未译出 {missing} 条")


def main():
    parser = argparse.ArgumentParser(description="GEVI 影片简介批量翻译 (EN -> ZH)")
    parser.add_argument("--provider", choices=list(PROVIDERS), help="翻译服务商")
    parser.add_argument("--profile", help="使用 translate_config.json 中哪一套服务配置 "
                                          "(默认用 active)")
    parser.add_argument("--api-key", help="API Key")
    parser.add_argument("--model", help="模型名（留空则用服务商默认模型）")
    parser.add_argument("--base-url", help="自定义 API 端点")
    parser.add_argument("--db", type=str, default="gevi.db", help="SQLite 数据库路径")
    parser.add_argument("--limit", type=int, default=None, help="本次最多翻译多少条")
    parser.add_argument("--batch-size", type=int, default=None,
                        help="每批翻译多少条（默认：简介 20，片名 50）")
    parser.add_argument("--workers", type=int, default=4, help="并发批次数 (默认 4)")
    parser.add_argument("--dry-run", action="store_true", help="试运行：只翻译不写库")
    parser.add_argument("--stats", action="store_true", help="只打印翻译进度统计后退出")
    parser.add_argument("--glossary", action="store_true",
                        help="只翻译演员属性术语表（一次调用，约 74 个词，之后永久复用）")
    parser.add_argument("--categories", action="store_true",
                        help="只翻译影片分类术语表（一次调用，约 53 个词，之后永久复用）")
    parser.add_argument("--titles", action="store_true",
                        help="只翻译影片片名（按 title 去重，译文体现原标题的双关）")
    parser.add_argument("--no-context", action="store_true",
                        help="片名模式不带简介上下文。用于和默认模式对比双关译准不准")
    parser.add_argument("--list-profiles", action="store_true",
                        help="列出已保存的翻译服务配置后退出 (不显示 API Key)")
    args = parser.parse_args()

    if args.list_profiles:
        profiles = list_profiles()
        if not profiles:
            print("尚未配置任何翻译服务。可在 App 的「设置」里添加，或直接编辑 translate_config.json。")
            return
        print("\n🔌 【已保存的翻译服务】")
        for p in profiles:
            mark = "✅ 使用中" if p["active"] else "  "
            key = p["key_hint"] or "未填 Key"
            print(f" {mark} {p['name']:12s} {p['label']:24s} {p['type']:9s} "
                  f"{p['model'] or '(默认模型)':22s} {key}")
        print()
        return

    db = DatabaseManager(str(Path(args.db).resolve()))

    if args.stats:
        s = db.get_translation_stats()
        t = db.get_title_translation_stats()
        print("\n📊 【剧情简介翻译进度】")
        print(f"  - 可翻译简介总数: {s['translatable']:,}")
        print(f"  - 已翻译 (中文):  {s['translated']:,}")
        print(f"  - 待翻译:         {s['pending']:,}")
        print(f"  - 翻译失败:       {s['failed']:,}")
        print(f"  - 完成度:         {(s['translated'] / s['translatable'] * 100) if s['translatable'] else 0:.1f}%\n")
        print("🎬 【片名翻译进度】(按去重后的片名计)")
        print(f"  - 全库片名:       {t['titles']:,}")
        print(f"  - 已翻译:         {t['translated']:,}")
        print(f"  - 待翻译:         {t['pending']:,}")
        print(f"  - 翻译失败:       {t['failed']:,}")
        print(f"  - 完成度:         {(t['translated'] / t['titles'] * 100) if t['titles'] else 0:.1f}%\n")
        return

    if args.glossary:
        # Built with the glossary prompt rather than the synopsis one, and from the
        # CLI flags so --profile / --api-key behave the same as for a normal run.
        translate_glossary(
            db,
            dry_run=args.dry_run,
            provider=build_provider(resolve_settings(args),
                                    system_prompt=GLOSSARY_SYSTEM_PROMPT, noun="属性词"),
        )
        return

    if args.categories:
        translate_categories(
            db,
            dry_run=args.dry_run,
            provider=build_provider(resolve_settings(args),
                                    system_prompt=CATEGORY_SYSTEM_PROMPT, noun="分类词"),
        )
        return

    if args.titles:
        # The only mode that echoes sources back (see extract_translations): titles are
        # short enough that a positional shift is invisible and permanent.
        run_title_translation(
            db=db,
            provider=build_provider(resolve_settings(args),
                                    system_prompt=TITLE_SYSTEM_PROMPT,
                                    noun="片名", echo_source=True),
            limit=args.limit,
            batch_size=args.batch_size or 50,
            workers=args.workers,
            dry_run=args.dry_run,
            with_context=not args.no_context,
        )
        return

    provider = build_provider(resolve_settings(args))
    run_translation(
        db=db,
        provider=provider,
        limit=args.limit,
        batch_size=args.batch_size or 20,
        workers=args.workers,
        dry_run=args.dry_run,
    )


if __name__ == "__main__":
    main()
