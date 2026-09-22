//! `#[tauri::command]` wrappers.
//!
//! Each one does exactly three things: open a connection, call the matching
//! `gpdb_core::queries` function, and stringify the error. No SQL lives here — that is
//! what keeps a query change from also being a frontend-contract change.
//!
//! Argument names are the frontend's contract: Tauri maps the JS `pageSize` onto the
//! Rust `page_size`, so renaming a parameter here silently changes what the UI sends.

pub mod database;
pub mod detail;
pub mod library;
pub mod sync;
pub mod translate;
pub mod user;
