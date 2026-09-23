//! Generate the app icon before the crate compiles.
//!
//! `tauri::generate_context!()` (src/lib.rs) reads every path in tauri.conf.json's
//! `bundle.icon` **at macro-expansion time** and embeds the bytes. So those files have
//! to exist before this crate compiles — not before it *bundles*.
//!
//! That distinction is the whole reason this lives here rather than in an npm script.
//! `beforeBuildCommand` covers `tauri build` and `beforeDevCommand` covers `tauri dev`,
//! but `cargo test` and a bare `cargo build` run neither, and on a fresh clone
//! `gen/icons/` is absent (it is generated, and gitignored). The failure that produces
//! is a proc-macro panic pointing at lib.rs:38 with a message about a missing *icon* —
//! nothing that suggests "run the icon build first". build.rs runs before macro
//! expansion every time, so the icons are simply always there.
//!
//! It is also the fix for a race: running the icon script by hand while `tauri dev` is
//! watching the directory lets the compiler read a half-written PNG, which surfaces
//! much later as "invalid icon: dimensions don't match the pixel count". Generating
//! from inside the build closes that window.

use std::path::{Path, PathBuf};
use std::process::Command;
use std::time::SystemTime;

/// Everything that has to be on disk before `generate_context!` expands: the icns it
/// embeds, plus the car the bundler copies in later. No PNGs — see build-icon.sh.
const OUTPUTS: &[&str] = &["gen/icons/Assets.car", "gen/icons/AppIcon.icns"];

/// The tracked source of the artwork — these, and only these, are worth rebuilding for.
const INPUTS: &[&str] = &[
    "generate-app-icons.py",
    "build-icon.sh",
    "../src/assets/icons/scheme-a.png",
];

fn mtime(p: &Path) -> Option<SystemTime> {
    std::fs::metadata(p).ok()?.modified().ok()
}

fn main() {
    let root = PathBuf::from(std::env::var("CARGO_MANIFEST_DIR").expect("CARGO_MANIFEST_DIR"));
    for input in INPUTS {
        println!("cargo:rerun-if-changed={input}");
    }

    // Stale when anything is missing, or when a source is newer than the oldest
    // product. The second half matters because editing the artwork and forgetting to
    // rebuild would otherwise ship the previous icon with no complaint at all.
    let products: Option<Vec<SystemTime>> = OUTPUTS
        .iter()
        .map(|o| mtime(&root.join(o)))
        .collect();
    let newest_input = INPUTS.iter().filter_map(|i| mtime(&root.join(i))).max();

    let stale = match (products, newest_input) {
        (Some(products), Some(newest_input)) => {
            products.into_iter().min().expect("non-empty") < newest_input
        }
        _ => true,
    };

    if stale {
        // Worth surfacing: a silent five-second regeneration in the middle of a build
        // is confusing when it is not what you thought you were waiting for.
        println!("cargo:warning=regenerating the app icon (gen/icons was missing or stale)");
        let script = root.join("build-icon.sh");
        let status = Command::new("bash").arg(&script).current_dir(&root).status();
        match status {
            Ok(s) if s.success() => {}
            Ok(s) => panic!(
                "build-icon.sh exited with {s}.\n\
                 It needs Xcode: actool and ictool both live inside Xcode.app, and the \
                 copies in /usr/bin are stubs. Install Xcode, or run \
                 `xcode-select --install` if only the command line tools are missing."
            ),
            Err(e) => panic!("could not run {}: {e}", script.display()),
        }
    }

    tauri_build::build()
}
