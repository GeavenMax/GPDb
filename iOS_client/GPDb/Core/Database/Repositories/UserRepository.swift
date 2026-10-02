import Foundation
import GRDB

public final class UserRepository {
    private let holder = DatabaseHolder.shared

    public init() {}

    // MARK: - 收藏操作 (Favorites)

    public func isFavorite(entityType: String, entityKey: String) async -> Bool {
        guard let db = holder.database else { return false }
        do {
            return try await db.read { db in
                let sql = "SELECT 1 FROM user_favorites WHERE entity_type = ? AND entity_key = ? LIMIT 1"
                return try Int.fetchOne(db, sql: sql, arguments: [entityType, entityKey]) != nil
            }
        } catch {
            return false
        }
    }

    public func toggleFavorite(entityType: String, entityKey: String, isFavorite: Bool) async throws {
        guard let db = holder.database else { return }
        try await db.write { db in
            if isFavorite {
                let sql = "INSERT OR IGNORE INTO user_favorites (entity_type, entity_key) VALUES (?, ?)"
                try db.execute(sql: sql, arguments: [entityType, entityKey])
            } else {
                let sql = "DELETE FROM user_favorites WHERE entity_type = ? AND entity_key = ?"
                try db.execute(sql: sql, arguments: [entityType, entityKey])
            }
        }
    }

    public func getFavorites(entityType: String) async throws -> [UserFavoriteRecord] {
        guard let db = holder.database else { return [] }
        return try await db.read { db in
            let sql = "SELECT * FROM user_favorites WHERE entity_type = ? ORDER BY created_at DESC"
            return try UserFavoriteRecord.fetchAll(db, sql: sql, arguments: [entityType])
        }
    }

    // MARK: - 电影状态与评分 (Rating & Notes)

    public func updateMovieRating(movieId: Int64, rating: Double) async throws {
        guard let db = holder.database else { return }
        try await db.write { db in
            let sql = """
            INSERT INTO user_movie_data (movie_id, rating)
            VALUES (?, ?)
            ON CONFLICT(movie_id) DO UPDATE SET rating = excluded.rating, updated_at = CURRENT_TIMESTAMP
            """
            try db.execute(sql: sql, arguments: [movieId, rating])
        }
    }

    public func updateMovieStatus(movieId: Int64, status: String?) async throws {
        guard let db = holder.database else { return }
        try await db.write { db in
            if let s = status {
                let sql = """
                INSERT INTO user_movie_data (movie_id, status)
                VALUES (?, ?)
                ON CONFLICT(movie_id) DO UPDATE SET status = excluded.status, updated_at = CURRENT_TIMESTAMP
                """
                try db.execute(sql: sql, arguments: [movieId, s])
            } else {
                let sql = "UPDATE user_movie_data SET status = NULL, updated_at = CURRENT_TIMESTAMP WHERE movie_id = ?"
                try db.execute(sql: sql, arguments: [movieId])
            }
        }
    }

    public func updateMovieNotes(movieId: Int64, notes: String?) async throws {
        guard let db = holder.database else { return }
        try await db.write { db in
            let sql = """
            INSERT INTO user_movie_data (movie_id, notes)
            VALUES (?, ?)
            ON CONFLICT(movie_id) DO UPDATE SET notes = excluded.notes, updated_at = CURRENT_TIMESTAMP
            """
            try db.execute(sql: sql, arguments: [movieId, notes])
        }
    }

    // MARK: - 跨平台通用数据备份导出与导入 (gpdb_universal_backup)

    public func exportFavorites() async throws -> [BackupFavorite] {
        guard let db = holder.database else { return [] }
        return try await db.read { db in
            let rows = try UserFavoriteRecord.fetchAll(db)
            return rows.map { BackupFavorite(entityType: $0.entityType, entityKey: $0.entityKey, createdAt: $0.createdAt) }
        }
    }

    public func exportMovieUserData() async throws -> [BackupMovieUserData] {
        guard let db = holder.database else { return [] }
        return try await db.read { db in
            let rows = try UserMovieDataRecord.fetchAll(db)
            return rows.map { BackupMovieUserData(movieId: Int($0.movieId), rating: $0.rating, status: $0.status, notes: $0.notes) }
        }
    }

    public func importFavorites(_ items: [BackupFavorite]) async throws -> Int {
        guard let db = holder.database else { return 0 }
        return try await db.write { db in
            var count = 0
            for item in items {
                let sql = "INSERT OR IGNORE INTO user_favorites (entity_type, entity_key, created_at) VALUES (?, ?, ?)"
                try db.execute(sql: sql, arguments: [item.entityType, item.entityKey, item.createdAt ?? Date().ISO8601Format()])
                count += 1
            }
            return count
        }
    }

    public func importMovieUserData(_ items: [BackupMovieUserData]) async throws -> Int {
        guard let db = holder.database else { return 0 }
        return try await db.write { db in
            var count = 0
            for item in items {
                let sql = """
                INSERT INTO user_movie_data (movie_id, rating, status, notes)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(movie_id) DO UPDATE SET
                    rating = COALESCE(excluded.rating, user_movie_data.rating),
                    status = COALESCE(excluded.status, user_movie_data.status),
                    notes = COALESCE(excluded.notes, user_movie_data.notes),
                    updated_at = CURRENT_TIMESTAMP
                """
                try db.execute(sql: sql, arguments: [item.movieId, item.rating, item.status, item.notes])
                count += 1
            }
            return count
        }
    }
}
