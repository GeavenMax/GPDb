//! Does the settings page find the *same* `translate_config.json` the CLI uses?
//!
//! The core layer is already checked against `translate.py` field by field. What that
//! cannot catch is the command layer pointing at the wrong path: the settings page
//! would then cheerfully render "尚未配置任何来源" while the file next to the database
//! is full of sources. A silent wrong answer, not an error — so it gets its own test.
//!
//! These call the `#[tauri::command]` functions directly. They are plain functions;
//! nothing about reading this file needs a running Tauri app.

use std::path::PathBuf;

use gevi_plus_lib::commands::translate;

/// The repo's config file, found the way the parity test finds `translate.py`.
fn repo_config() -> Option<PathBuf> {
    for candidate in ["../../translate_config.json", "translate_config.json"] {
        let p = PathBuf::from(candidate);
        if p.exists() {
            return Some(p);
        }
    }
    None
}

#[test]
fn the_command_layer_resolves_the_cli_config_file() {
    let Some(expected_path) = repo_config() else {
        eprintln!("skipping: no translate_config.json - this build is outside the repo");
        return;
    };

    let got = translate::get_translation_providers().expect("reading the providers");
    let want = gevi_core::translate_config::list_profiles(&expected_path);

    // Non-empty when the file has sources: the failure this guards against is an empty
    // list rendering as "nothing configured".
    assert!(
        !want.is_empty(),
        "the fixture has no sources, so this test would pass vacuously"
    );
    assert_eq!(
        serde_json::to_value(&got.profiles).unwrap(),
        serde_json::to_value(&want).unwrap(),
        "the settings page would list different sources than the command line sees \
         (resolved config: {:?})",
        expected_path
    );
    assert_eq!(got.config_file, "translate_config.json");
    assert!(!got.presets.is_empty(), "the preset buttons would come up empty");
}

/// Editing a source without retyping its key must not erase the key.
///
/// Run against a copy of the real config rather than a fixture: this file holds the
/// key the user is actually translating with, and a save is the operation that could
/// lose it. The command layer's own save is deliberately not called here — it resolves
/// the live file, and no test should write that. `save_profile` is the same function
/// the command calls, so this covers the behaviour; the path resolution is covered
/// by the test above.
#[test]
fn saving_a_copy_of_the_real_config_keeps_the_stored_key() {
    let Some(real) = repo_config() else {
        eprintln!("skipping: no translate_config.json - this build is outside the repo");
        return;
    };

    let dir = std::env::temp_dir().join(format!("gevi_cmd_xlate_{}", std::process::id()));
    std::fs::create_dir_all(&dir).unwrap();
    let tmp = dir.join("translate_config.json");
    assert_ne!(
        std::fs::canonicalize(&dir).unwrap(),
        std::fs::canonicalize(real.parent().unwrap()).unwrap(),
        "refusing to run: the scratch directory is the real config's directory"
    );
    std::fs::write(&tmp, std::fs::read_to_string(&real).unwrap()).unwrap();

    let before = gevi_core::translate_config::list_profiles(&tmp);
    let Some(existing) = before.first() else {
        eprintln!("skipping: the config has no sources to round-trip");
        return;
    };
    let (name, hint) = (existing.name.clone(), existing.key_hint.clone());
    assert!(!hint.is_empty(), "the fixture source has no key, so nothing is at risk");

    // Touch only the model, exactly as the form does when the key field is left blank.
    let input: gevi_core::translate_config::ProfileInput =
        serde_json::from_value(serde_json::json!({ "name": name, "model": "gevi-round-trip-probe" }))
            .unwrap();
    gevi_core::translate_config::save_profile(&tmp, &input).unwrap();

    let after = gevi_core::translate_config::list_profiles(&tmp);
    let updated = after.iter().find(|p| p.name == name).expect("the source survived");
    assert_eq!(updated.model, "gevi-round-trip-probe");
    assert_eq!(
        updated.key_hint, hint,
        "saving without a key must leave the stored key alone"
    );
}
