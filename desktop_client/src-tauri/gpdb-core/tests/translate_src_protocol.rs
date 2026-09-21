//! Does the src-echo protocol actually stop a batch from shifting?
//!
//! `extract_translations` has only one implementation, in `translate.py` — there is
//! nothing to compare a Rust port against, so these tests drive the Python function
//! directly and assert on its behaviour. They run under `cargo test` because that is
//! the command this repo verifies with; a Python test file nothing invokes would rot.
//!
//! # Why this file exists
//!
//! The synopsis path matches translations by position (`item["i"]`, falling back to
//! the item's index). That is safe for a 470-character synopsis, where a shift is
//! obvious on sight. Titles are 18 characters: the library holds 3,365 repeated
//! titles ("Boys Will Be Boys" appears 17 times), a model facing a numbered list with
//! repeated lines merges them, and every later entry slides up one — writing each
//! translation over a title it had nothing to do with. Every value is non-empty, so
//! the per-item retry never fires and the corruption is permanent and invisible.
//!
//! So the title path passes `sources` and matches on the echoed source instead. The
//! tests below are written against that failure mode specifically: the first one
//! fails if the positional fallback is ever reintroduced, which is exactly the
//! change that would look harmless in review.
//!
//! Skipped (not failed) when `translate.py` or a `python3` is unavailable, so
//! `cargo test` stays green on a machine that only has the Rust half.

use std::path::{Path, PathBuf};
use std::process::Command;

/// Walk up to the repo root, the way the other test files find `translate.py`.
fn project_root() -> Option<PathBuf> {
    let mut dir = Path::new(env!("CARGO_MANIFEST_DIR"));
    for _ in 0..5 {
        if dir.join("translate.py").exists() {
            return Some(dir.to_path_buf());
        }
        dir = dir.parent()?;
    }
    None
}

fn python() -> Option<String> {
    for exe in ["python3", "python"] {
        if let Ok(out) = Command::new(exe).args(["-c", "print(1)"]).output() {
            if out.status.success() {
                return Some(exe.to_string());
            }
        }
    }
    None
}

/// Reads one request as JSON on stdin and prints `extract_translations`'s result.
///
/// Driving the real function rather than a copy of it is the point: a reimplementation
/// here would keep passing after the implementation changed underneath it.
const DRIVER: &str = r#"
import json, sys
import translate
req = json.loads(sys.stdin.read())
out = translate.extract_translations(
    req["raw"], req["expected"], sources=req.get("sources")
)
print(json.dumps(out, ensure_ascii=False))
"#;

/// The same, for the pieces of provider plumbing the protocol depends on.
const SCHEMA_DRIVER: &str = r#"
import json, sys
import translate
req = json.loads(sys.stdin.read())
p = translate.AnthropicProvider(
    api_key="k", model="m", noun=req.get("noun", "简介"),
    echo_source=req["echo_source"],
)
out = {
    "schema": json.dumps(p.schema(), sort_keys=True, ensure_ascii=False),
    "has_src_in_schema": "src" in json.dumps(p.schema()),
    "user_turn": p.user_turn(req["texts"], req.get("contexts")),
}
print(json.dumps(out, ensure_ascii=False))
"#;

fn run_driver(root: &Path, py: &str, driver: &str, request: &str) -> serde_json::Value {
    use std::io::Write;
    let mut child = Command::new(py)
        .arg("-c")
        .arg(driver)
        .current_dir(root)
        .stdin(std::process::Stdio::piped())
        .stdout(std::process::Stdio::piped())
        .stderr(std::process::Stdio::piped())
        .spawn()
        .expect("spawning python");
    child
        .stdin
        .as_mut()
        .unwrap()
        .write_all(request.as_bytes())
        .unwrap();
    let out = child.wait_with_output().unwrap();
    assert!(
        out.status.success(),
        "driver failed: {}",
        String::from_utf8_lossy(&out.stderr)
    );
    serde_json::from_slice(&out.stdout).expect("driver printed non-JSON")
}

/// Both halves of the harness: the repo root and a working python.
///
/// A missing `translate.py` is a legitimate skip (this crate taken out of the repo).
/// A present `translate.py` with no python is *not* — the check was asked for and
/// would silently not happen, which is the failure mode this repo has been bitten by
/// before.
fn harness() -> Option<(PathBuf, String)> {
    let root = match project_root() {
        Some(r) => r,
        None => {
            eprintln!("skipping: translate.py not found; this crate is outside the repo");
            return None;
        }
    };
    let py = python().expect(
        "translate.py is here but no working python3 was found - the protocol check \
         cannot run, and passing without running it would be a lie",
    );
    Some((root, py))
}

fn extract(root: &Path, py: &str, raw: &str, expected: usize,
           sources: Option<&[&str]>) -> Vec<String> {
    let req = serde_json::json!({
        "raw": raw,
        "expected": expected,
        "sources": sources,
    });
    let out = run_driver(root, py, DRIVER, &req.to_string());
    out.as_array()
        .unwrap()
        .iter()
        .map(|v| v.as_str().unwrap().to_string())
        .collect()
}

/// The failure this whole protocol exists to prevent.
///
/// The model answers three of four entries and omits `i`. Positionally that is
/// indistinguishable from a complete answer with a hole at the end: Bravo would be
/// handed Charlie's translation and Delta would get nothing, silently. Matching on
/// the echoed source instead leaves a hole exactly where the model left one.
#[test]
fn a_dropped_entry_does_not_shift_the_rest() {
    let Some((root, py)) = harness() else { return };
    let sources = ["Alpha", "Bravo", "Charlie", "Delta"];
    let raw = r#"{"translations":[
        {"src":"Alpha","zh":"甲"},
        {"src":"Charlie","zh":"丙"},
        {"src":"Delta","zh":"丁"}]}"#;

    let out = extract(&root, &py, raw, 4, Some(&sources));

    assert_eq!(out, vec!["甲", "", "丙", "丁"]);
    // Stated separately because it is the actual requirement: the *hole* is at the
    // dropped entry. A positional match would put "丙" here, and that is a wrong
    // translation written over a real film's name.
    assert_eq!(out[1], "", "Bravo must not inherit Charlie's translation");
}

/// A repeated source resolves to the same translation for every copy.
///
/// This cannot arise from the title path as collected today — `get_untranslated_titles`
/// groups by title, so one batch never holds the same title twice. It is here because
/// the first version of `_match_by_source` indexed sources with a plain `{s: i}`,
/// which keeps only the *last* position for a repeated key: the earlier copies came
/// back empty and were re-sent on the next run. That is the kind of bug a hand-written
/// "looks right" implementation carries, and the collector's grouping is a property
/// of today's SQL rather than of this function.
#[test]
fn a_repeated_source_fills_every_occurrence() {
    let Some((root, py)) = harness() else { return };
    let sources = ["Boys Will Be Boys", "Hard at Work", "Boys Will Be Boys"];
    let raw = r#"{"translations":[
        {"src":"Boys Will Be Boys","zh":"男孩终归是男孩"},
        {"src":"Hard at Work","zh":"努力工作"}]}"#;

    let out = extract(&root, &py, raw, 3, Some(&sources));

    assert_eq!(out, vec!["男孩终归是男孩", "努力工作", "男孩终归是男孩"]);
}

/// A source the model rewrote is a miss, not a shift.
///
/// Asserted both ways: a case-only difference still matches (models "tidy" text, and
/// an exact-only rule would make every entry a miss and the whole run empty), while a
/// source that matches nothing at all is dropped rather than placed at its index.
#[test]
fn an_unmatched_source_is_dropped_never_placed_positionally() {
    let Some((root, py)) = harness() else { return };

    // Case and whitespace only: still a match, on content.
    let out = extract(&root, &py,
        r#"{"translations":[{"src":"creamy  ranch","zh":"浓郁牧场"}]}"#,
        1, Some(&["Creamy Ranch"]));
    assert_eq!(out, vec!["浓郁牧场"]);

    // Genuinely different text: dropped, so the caller retries that one entry.
    let out = extract(&root, &py,
        r#"{"translations":[{"src":"Something Else Entirely","zh":"别的东西"}]}"#,
        1, Some(&["Creamy Ranch"]));
    assert_eq!(out, vec![""], "an unmatched src must not land at the entry's index");
}

/// The synopsis path must keep behaving exactly as it did.
///
/// 60,871 synopses are still queued through it. `sources=None` is the old contract:
/// `i` if present, the item's position otherwise.
#[test]
fn without_sources_the_positional_contract_is_unchanged() {
    let Some((root, py)) = harness() else { return };

    // `i` present, out of order.
    let out = extract(&root, &py,
        r#"{"translations":[{"i":2,"zh":"乙"},{"i":1,"zh":"甲"}]}"#, 2, None);
    assert_eq!(out, vec!["甲", "乙"]);

    // `i` missing: falls back to position, which is the documented old behaviour.
    let out = extract(&root, &py,
        r#"{"translations":[{"zh":"甲"},{"zh":"乙"}]}"#, 2, None);
    assert_eq!(out, vec!["甲", "乙"]);

    // A missing entry leaves its slot empty so it gets retried.
    let out = extract(&root, &py,
        r#"{"translations":[{"i":1,"zh":"甲"},{"i":3,"zh":"丙"}]}"#, 3, None);
    assert_eq!(out, vec!["甲", "", "丙"]);
}

/// Structured output is *enforced*, not requested.
///
/// The Anthropic backend sends a JSON schema; a field absent from that schema is
/// stripped by the API no matter what the prompt asks for. So asking for `src` in the
/// system prompt while leaving it out of the schema would return translations with no
/// source to match on — every entry a miss, every title untranslated, at full cost.
#[test]
fn the_anthropic_schema_carries_src_when_sources_are_echoed() {
    let Some((root, py)) = harness() else { return };

    let echoed = run_driver(&root, &py, SCHEMA_DRIVER,
        &serde_json::json!({"echo_source": true, "texts": ["Alpha"]}).to_string());
    assert_eq!(echoed["has_src_in_schema"], true,
               "echo_source=True must put src in the schema, not just the prompt: {}",
               echoed["schema"]);

    let positional = run_driver(&root, &py, SCHEMA_DRIVER,
        &serde_json::json!({"echo_source": false, "texts": ["Alpha"]}).to_string());
    assert_eq!(positional["has_src_in_schema"], false,
               "the synopsis schema must stay as it was");
}

/// The context a title is judged against has to reach the model, and be labelled.
///
/// "Creamy Ranch" is only translatable as a pun once you know it is a film about a
/// ranch; the label matters because an unlabelled second line is just as likely to be
/// translated as part of the title.
#[test]
fn the_user_turn_carries_labelled_context_and_the_bare_title() {
    let Some((root, py)) = harness() else { return };

    let with = run_driver(&root, &py, SCHEMA_DRIVER, &serde_json::json!({
        "echo_source": true,
        "texts": ["Creamy Ranch"],
        "contexts": ["A cowboy discovers the ranch hands have other ideas."],
        "noun": "片名",
    }).to_string());
    let turn = with["user_turn"].as_str().unwrap();
    assert!(turn.contains("1. Creamy Ranch"), "the source line is missing: {turn}");
    assert!(turn.contains("不要翻译"), "the context is not marked as untranslatable: {turn}");
    assert!(turn.contains("ranch hands"), "the context itself is missing: {turn}");
    assert!(turn.contains("片名"), "the noun was not parameterised: {turn}");
    assert!(!turn.contains("条简介"), "the synopsis noun leaked into the title turn: {turn}");

    let without = run_driver(&root, &py, SCHEMA_DRIVER, &serde_json::json!({
        "echo_source": false,
        "texts": ["Alpha", "Bravo"],
    }).to_string());
    assert_eq!(without["user_turn"].as_str().unwrap(),
               "请翻译以下 2 条简介：\n\n1. Alpha\n2. Bravo");
}
