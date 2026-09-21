//! One module per domain. Every function here is `pub fn name(conn: &Connection, …)
//! -> Result<T>` — nothing else, so these can be called from tests and from the
//! command layer interchangeably.

pub mod episodes;
pub mod favorites;
pub mod movies;
pub mod performers;
pub mod stats;
pub mod studios;
