//! Does the Rust port of the translation-source config agree with `translate.py`?
//!
//! The desktop app and the command-line translator read the same
//! `translate_config.json`. If the two disagree about what is in it, a source the
//! user adds in the settings UI either vanishes from the CLI or - worse - gets
//! clobbered on the next save. So every case here runs the *same* fixture and the
//! *same* operation sequence through both implementations and compares the results,
//! rather than asserting values this file also hard-codes.
//!
//! Skipped (not failed) when `translate.py` or a `python3` is unavailable, so
//! `cargo test` stays green on a machine that only has the Rust half.

use std::fs;
use std::path::{Path, PathBuf};
use std::process::Command;

use gpdb_core::translate_config as tc;

/// The project root that holds `translate.py`, found by walking up from this crate
/// rather than by counting parents — an off-by-one there silently turned this whole
/// file into a no-op that still reported `ok`, which is worse than no test at all.
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
        // A bare `python` on this machine may exist but be a shim that exits
        // non-zero, so check the status rather than only that it spawned.
        if let Ok(out) = Command::new(exe).args(["-c", "print(1)"]).output() {
            if out.status.success() {
                return Some(exe.to_string());
            }
        }
    }
    None
}

/// Applies a list of ops to a config through `translate.py` and prints the result
/// as canonical JSON, so the Rust side has something to be compared against.
const DRIVER: &str = r#"
import json, sys
from pathlib import Path
import translate

translate.CONFIG_FILE = Path(sys.argv[1])
ops = json.loads(sys.argv[2])
for op in ops:
    kind = op.pop("op")
    if kind == "save":
        translate.save_profile(op["name"], op)
        if op.get("active"):
            translate.set_active_profile(op["name"])
    elif kind == "activate":
        translate.set_active_profile(op["name"])
    elif kind == "delete":
        translate.delete_profile(op["name"])

cfg = translate.migrate_config(translate.load_config())
print(json.dumps({
    "profiles": translate.list_profiles(),
    "config": cfg,
}, sort_keys=True, ensure_ascii=False))
"#;

/// The same information, produced by the Rust implementation.
fn rust_result(path: &Path, ops: &[serde_json::Value]) -> serde_json::Value {
    for op in ops {
        let mut op = op.clone();
        let kind = op["op"].as_str().unwrap().to_string();
        let name = op["name"].as_str().unwrap().to_string();
        match kind.as_str() {
            "save" => {
                op.as_object_mut().unwrap().remove("op");
                op.as_object_mut().unwrap().remove("name");
                let input: tc::ProfileInput = serde_json::from_value({
                    let mut o = op.as_object().unwrap().clone();
                    o.insert("name".into(), serde_json::Value::String(name.clone()));
                    serde_json::Value::Object(o)
                })
                .unwrap();
                tc::save_profile(path, &input).unwrap();
                if input.active == Some(true) {
                    tc::set_active_profile(path, &name).unwrap();
                }
            }
            "activate" => tc::set_active_profile(path, &name).unwrap(),
            "delete" => tc::delete_profile(path, &name).unwrap(),
            other => panic!("unknown op {other}"),
        }
    }
    serde_json::json!({
        "profiles": tc::list_profiles(path),
        "config": tc::load_config(path),
    })
}

/// Drop empty-string values, recursively.
///
/// Every reader on both sides reads a field as `cfg.get(k) or ""`, so a key holding
/// `""` and a key that is absent mean the same thing. Rust normalises the latter away
/// when it writes (see the divergence list in `translate_config.rs`); Python keeps
/// whichever keys the file happened to have. Comparing the two literally would be
/// asserting on whitespace - what has to match is the meaning.
fn without_empties(v: &serde_json::Value) -> serde_json::Value {
    match v {
        serde_json::Value::Object(map) => serde_json::Value::Object(
            map.iter()
                .filter(|(_, val)| !matches!(val, serde_json::Value::String(s) if s.is_empty()))
                .map(|(k, val)| (k.clone(), without_empties(val)))
                .collect(),
        ),
        serde_json::Value::Array(items) => {
            serde_json::Value::Array(items.iter().map(without_empties).collect())
        }
        other => other.clone(),
    }
}

/// A deterministic, non-reversible fingerprint of a secret.
///
/// A failing assertion prints both sides, and the config on disk holds the user's
/// real API key — so the key must never appear in a diff. The fingerprint still
/// catches a key that was dropped, truncated or altered. (FNV-1a, written out here
/// rather than pulled in as a dependency.)
fn fingerprint(s: &str) -> String {
    let mut h: u64 = 0xcbf2_9ce4_8422_2325;
    for b in s.as_bytes() {
        h ^= *b as u64;
        h = h.wrapping_mul(0x0000_0100_0000_01b3);
    }
    format!("len{}·{:016x}", s.len(), h)
}

/// Replace every `api_key` value with its fingerprint, recursively.
fn redact_keys(v: &serde_json::Value) -> serde_json::Value {
    match v {
        serde_json::Value::Object(map) => serde_json::Value::Object(
            map.iter()
                .map(|(k, val)| {
                    let val = match (k.as_str(), val) {
                        ("api_key", serde_json::Value::String(s)) if !s.is_empty() => {
                            serde_json::Value::String(fingerprint(s))
                        }
                        _ => redact_keys(val),
                    };
                    (k.clone(), val)
                })
                .collect(),
        ),
        serde_json::Value::Array(items) => {
            serde_json::Value::Array(items.iter().map(redact_keys).collect())
        }
        other => other.clone(),
    }
}

/// Canonicalise both sides through `serde_json::Value` (objects are BTreeMaps, so
/// key order is not part of the comparison) before asserting equality.
fn compare(root: &Path, py: &str, name: &str, fixture: &str, ops: &[serde_json::Value]) {
    let dir = std::env::temp_dir().join(format!("gpdb_xlate_{}", std::process::id()));
    fs::create_dir_all(&dir).unwrap();
    let rust_path = dir.join(format!("{name}_rust.json"));
    let py_path = dir.join(format!("{name}_py.json"));
    fs::write(&rust_path, fixture).unwrap();
    fs::write(&py_path, fixture).unwrap();

    let out = Command::new(py)
        .arg("-c")
        .arg(DRIVER)
        .arg(&py_path)
        .arg(serde_json::to_string(ops).unwrap())
        .current_dir(root)
        .output()
        .expect("running translate.py driver");
    assert!(
        out.status.success(),
        "translate.py driver failed for {name}: {}",
        String::from_utf8_lossy(&out.stderr)
    );
    let expected: serde_json::Value = serde_json::from_slice(&out.stdout).unwrap();
    let actual = rust_result(&rust_path, ops);

    let norm = |v: &serde_json::Value| without_empties(&redact_keys(v));
    assert_eq!(
        norm(&actual["profiles"]),
        norm(&expected["profiles"]),
        "[{name}] the profile list the settings UI shows differs from translate.py's"
    );
    assert_eq!(
        norm(&actual["config"]),
        norm(&expected["config"]),
        "[{name}] the file left on disk differs from the one translate.py would write"
    );
}

fn ops(json: &str) -> Vec<serde_json::Value> {
    serde_json::from_str(json).unwrap()
}

#[test]
fn translation_config_matches_translate_py() {
    // Absent `translate.py` means this crate was taken out of the repo, and there is
    // legitimately nothing to compare against. A present `translate.py` with no
    // python to run it is *not* a skip - that combination means the comparison was
    // asked for and did not happen, so it fails rather than reporting a false green.
    let Some(root) = project_root() else {
        eprintln!("skipping: translate.py not found; this crate is outside the repo");
        return;
    };
    let py = python().expect(
        "translate.py is here but no working python3 was found - the parity check \
         cannot run, and passing without running it would be a lie",
    );
    let root = root.as_path();

    // 1. The shape in use today.
    //
    // The key here is a fixture, and must stay one: this file is tracked, and an
    // earlier revision of it carried the placeholder text replaced below with the
    // user's *real* DeepSeek key pasted in. The comparison only needs a non-empty
    // string of the same rough length — `the_real_config_on_disk_parses_the_same_way`
    // below covers the actual key by reading the ignored file at test time.
    compare(root, &py, "current",
        r#"{"active":"deepseek","profiles":{
            "deepseek":{"type":"openai","label":"DeepSeek 深度求索","api_key":"sk-fixture-not-a-real-key-0001","model":"deepseek-flash","base_url":"https://api.deepseek.com"},
            "openai":{"type":"openai","label":"OpenAI","api_key":"","model":"gpt-4o-mini","base_url":"https://api.openai.com/v1"}}}"#,
        &ops("[]"));

    // 2. The oldest flat shape, whose key has to survive the migration.
    compare(root, &py, "legacy_flat",
        r#"{"api_key":"sk-abc1234567890","provider":"openai","model":"gpt-4o-mini","base_url":"https://api.openai.com/v1"}"#,
        &ops("[]"));

    // 3. The brief `providers`-keyed shape.
    compare(root, &py, "legacy_providers",
        r#"{"active":"moonshot","providers":{"moonshot":{"type":"openai","label":"","api_key":"sk-moon1234567890","model":"","base_url":"https://api.moonshot.cn/v1"}}}"#,
        &ops("[]"));

    // 4. Nothing configured yet.
    compare(root, &py, "empty", r#"{}"#, &ops("[]"));

    // 5. The key-hint boundary: 12 chars stays masked as "已保存", 13 does not.
    compare(root, &py, "hint_edge",
        r#"{"profiles":{"a":{"api_key":"123456789012","type":"openai"},
                       "b":{"api_key":"1234567890123","type":"openai"},
                       "c":{"type":"openai"}}}"#,
        &ops("[]"));

    // 6. A save that omits the key must keep the stored one, and must not be able
    //    to widen the model into something the form never sent.
    compare(root, &py, "save_keeps_key",
        r#"{"active":"a","profiles":{"a":{"type":"openai","label":"A","api_key":"sk-keepme12345678","model":"old","base_url":"https://api.deepseek.com"}}}"#,
        &ops(r#"[{"op":"save","name":"a","type":"openai","label":"A renamed","model":"new-model","base_url":"https://api.deepseek.com"}]"#));

    // 7. Adding a second source, making it active, then deleting the first.
    compare(root, &py, "crud",
        r#"{"active":"a","profiles":{"a":{"type":"anthropic","label":"A","api_key":"sk-aaaa1234567890","model":"m1","base_url":"https://api.anthropic.com"}}}"#,
        &ops(r#"[{"op":"save","name":"b","type":"openai","label":"B","api_key":"sk-bbbb1234567890","model":"m2","base_url":"https://api.deepseek.com"},
                 {"op":"activate","name":"b"},
                 {"op":"delete","name":"a"}]"#));

    // 8. Deleting the source in use has to fall back to a remaining one, not to "".
    compare(root, &py, "delete_active",
        r#"{"active":"a","profiles":{
            "a":{"type":"openai","label":"A","api_key":"sk-aaaa1234567890"},
            "b":{"type":"openai","label":"B","api_key":"sk-bbbb1234567890"}}}"#,
        &ops(r#"[{"op":"delete","name":"a"}]"#));

    // 9. A save carrying only a name and a key: `type` has to fall back to `openai`
    //    and `label` to the source's own name, on both sides. This is the one case
    //    that exercises `setdefault`, and the form never produces it - the UI always
    //    sends both fields - so without this fixture a port that dropped the
    //    fallbacks entirely would still look correct.
    compare(root, &py, "save_defaults",
        r#"{}"#,
        &ops(r#"[{"op":"save","name":"kimi","api_key":"sk-kimi1234567890"}]"#));
}

#[test]
fn providers_payload_never_carries_a_key() {
    let dir = std::env::temp_dir().join(format!("gpdb_xlate_leak_{}", std::process::id()));
    fs::create_dir_all(&dir).unwrap();
    let path = dir.join("leak.json");
    fs::write(&path, r#"{"active":"s","profiles":{"s":{
        "type":"openai","label":"S","api_key":"sk-secret0123456789","model":"m","base_url":"https://x"}}}"#)
        .unwrap();

    // The serialised payload is what crosses to the webview, so assert on the wire
    // form rather than on the struct: that is the thing that would leak.
    let wire = serde_json::to_string(&tc::providers(&path)).unwrap();
    assert!(!wire.contains("sk-secret0123456789"), "the API key reached the frontend: {wire}");
    assert!(wire.contains("\"has_key\":true"));
    assert!(wire.contains("sk-sec…6789"), "the hint should still identify the key: {wire}");
}

/// The config file actually on disk, not a fixture.
///
/// This is the file the command-line translator has been using, and the desktop app
/// is about to read and rewrite it. Fixtures only prove the port handles shapes I
/// thought to write down; this proves it handles the one the user really has.
#[test]
fn the_real_config_on_disk_parses_the_same_way() {
    let Some(root) = project_root() else {
        eprintln!("skipping: translate.py not found; this crate is outside the repo");
        return;
    };
    let real = root.join("translate_config.json");
    if !real.exists() {
        eprintln!("skipping: no translate_config.json in the repo yet");
        return;
    }
    let py = python().expect("translate.py is here but no working python3 was found");
    let fixture = fs::read_to_string(&real).unwrap();
    // `compare` runs its ops against its own copy, so the real file is only ever read.
    compare(root.as_path(), &py, "real", &fixture, &ops("[]"));
}
