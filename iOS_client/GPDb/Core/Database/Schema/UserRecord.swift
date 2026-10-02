import Foundation
import GRDB

/// 用户收藏记录，映射 `user_favorites`
public struct UserFavoriteRecord: Codable, FetchableRecord, TableRecord, PersistableRecord {
    public static let databaseTableName = "user_favorites"

    public var entityType: String
    public var entityKey: String
    public var createdAt: String?

    enum CodingKeys: String, CodingKey {
        case entityType = "entity_type"
        case entityKey = "entity_key"
        case createdAt = "created_at"
    }

    public init(entityType: String, entityKey: String, createdAt: String? = nil) {
        self.entityType = entityType
        self.entityKey = entityKey
        self.createdAt = createdAt
    }
}

/// 用户电影标记与私密笔记，映射 `user_movie_data`
public struct UserMovieDataRecord: Codable, FetchableRecord, TableRecord, PersistableRecord, Identifiable {
    public static let databaseTableName = "user_movie_data"

    public var id: Int64 { movieId }
    public var movieId: Int64
    public var rating: Double?
    public var status: String?
    public var notes: String?
    public var updatedAt: String?

    enum CodingKeys: String, CodingKey {
        case movieId = "movie_id"
        case rating
        case status
        case notes
        case updatedAt = "updated_at"
    }

    public init(movieId: Int64, rating: Double? = nil, status: String? = nil, notes: String? = nil, updatedAt: String? = nil) {
        self.movieId = movieId
        self.rating = rating
        self.status = status
        self.notes = notes
        self.updatedAt = updatedAt
    }
}

/// 用户自定义标签，映射 `user_tags`
public struct UserTagRecord: Codable, FetchableRecord, TableRecord, PersistableRecord, Identifiable {
    public static let databaseTableName = "user_tags"

    public var id: Int64?
    public var name: String
    public var color: String?
    public var createdAt: String?

    enum CodingKeys: String, CodingKey {
        case id
        case name
        case color
        case createdAt = "created_at"
    }
}
