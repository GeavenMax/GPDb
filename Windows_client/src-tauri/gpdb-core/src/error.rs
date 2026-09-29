//! The error every query returns.
//!
//! Splitting the query layer out of `lib.rs` had to leave the user-visible
//! messages byte-identical, so `Display` here is exactly what the old
//! `Result<_, String>` layer produced with `to_string()`. The Tauri side stringifies
//! at the command boundary, which is why `From<String>` exists at all.

use std::fmt;

#[derive(Debug)]
pub enum Error {
    /// A rusqlite failure, printed exactly as rusqlite prints it.
    Db(rusqlite::Error),
    /// A message already formatted for the user — validation, mostly.
    Message(String),
}

impl fmt::Display for Error {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Error::Db(e) => write!(f, "{}", e),
            Error::Message(m) => write!(f, "{}", m),
        }
    }
}

impl std::error::Error for Error {}

impl From<rusqlite::Error> for Error {
    fn from(e: rusqlite::Error) -> Self {
        Error::Db(e)
    }
}

impl From<String> for Error {
    fn from(m: String) -> Self {
        Error::Message(m)
    }
}

pub type Result<T> = std::result::Result<T, Error>;
