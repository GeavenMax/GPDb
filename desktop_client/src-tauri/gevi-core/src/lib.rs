//! GEVI+ 查询层。
//!
//! 有意做成不依赖 Tauri：这里每个函数只吃一个 `&rusqlite::Connection` 和普通数据，
//! 所以 `cargo test -p gevi-core` 几秒就能跑完（不必构建整个 app），而且改一个查询
//! 不可能顺带碰到命令注册表或前端契约。

pub mod error;
pub mod migrate;
pub mod models;
pub mod queries;
pub mod sql;
pub mod translate_config;

pub use error::{Error, Result};
