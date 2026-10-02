import Foundation

/// GPDb 跨平台通用用户备份数据模型
public struct UniversalBackup: Codable {
    public let format: String
    public let version: Int
    public let appVersion: String
    public let platform: String
    public let exportedAt: String
    public let favorites: [BackupFavorite]
    public let tags: [BackupTag]?
    public let movieUserData: [BackupMovieUserData]?
    public let analytics: BackupAnalytics?

    enum CodingKeys: String, CodingKey {
        case format
        case version
        case appVersion = "app_version"
        case platform
        case exportedAt = "exported_at"
        case favorites
        case tags
        case movieUserData = "movie_user_data"
        case analytics
    }

    public init(
        format: String = "gpdb_universal_backup",
        version: Int = 2,
        appVersion: String = "2.15.0",
        platform: String = "ios",
        exportedAt: String = ISO8601DateFormatter().string(from: Date()),
        favorites: [BackupFavorite] = [],
        tags: [BackupTag]? = nil,
        movieUserData: [BackupMovieUserData]? = nil,
        analytics: BackupAnalytics? = nil
    ) {
        self.format = format
        self.version = version
        self.appVersion = appVersion
        self.platform = platform
        self.exportedAt = exportedAt
        self.favorites = favorites
        self.tags = tags
        self.movieUserData = movieUserData
        self.analytics = analytics
    }
}

public struct BackupFavorite: Codable {
    public let entityType: String
    public let entityKey: String
    public let createdAt: String?

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

public struct BackupTag: Codable {
    public let id: Int?
    public let name: String
    public let color: String?

    public init(id: Int? = nil, name: String, color: String? = "#6366f1") {
        self.id = id
        self.name = name
        self.color = color
    }
}

public struct BackupMovieUserData: Codable {
    public let movieId: Int
    public let rating: Double?
    public let status: String?
    public let notes: String?

    enum CodingKeys: String, CodingKey {
        case movieId = "movie_id"
        case rating
        case status
        case notes
    }

    public init(movieId: Int, rating: Double? = nil, status: String? = nil, notes: String? = nil) {
        self.movieId = movieId
        self.rating = rating
        self.status = status
        self.notes = notes
    }
}

public struct BackupAnalytics: Codable {
    public let totalFocusTimeSeconds: Int?
    public let movieViewsCount: Int?
    public let uniqueMoviesViewed: [Int]?
    public let episodeViewsCount: Int?
    public let performerViewsCount: Int?
    public let directorViewsCount: Int?
    public let studioViewsCount: Int?
    public let searchesCount: Int?
    public let favoritesAddedCount: Int?
    public let ratingsCount: Int?
    public let activeDays: [String]?

    public init(
        totalFocusTimeSeconds: Int? = 0,
        movieViewsCount: Int? = 0,
        uniqueMoviesViewed: [Int]? = [],
        episodeViewsCount: Int? = 0,
        performerViewsCount: Int? = 0,
        directorViewsCount: Int? = 0,
        studioViewsCount: Int? = 0,
        searchesCount: Int? = 0,
        favoritesAddedCount: Int? = 0,
        ratingsCount: Int? = 0,
        activeDays: [String]? = []
    ) {
        self.totalFocusTimeSeconds = totalFocusTimeSeconds
        self.movieViewsCount = movieViewsCount
        self.uniqueMoviesViewed = uniqueMoviesViewed
        self.episodeViewsCount = episodeViewsCount
        self.performerViewsCount = performerViewsCount
        self.directorViewsCount = directorViewsCount
        self.studioViewsCount = studioViewsCount
        self.searchesCount = searchesCount
        self.favoritesAddedCount = favoritesAddedCount
        self.ratingsCount = ratingsCount
        self.activeDays = activeDays
    }
}

/// iOS 客户端通用配置导出导入服务
public final class UserBackupService {
    public static let shared = UserBackupService()
    private init() {}

    /// 导出为 JSON 文件
    public func exportUniversalBackup(
        favorites: [BackupFavorite],
        analytics: BackupAnalytics? = nil
    ) throws -> Data {
        let backup = UniversalBackup(
            favorites: favorites,
            analytics: analytics
        )
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        return try encoder.encode(backup)
    }

    /// 从 Data 解析通用备份
    public func parseUniversalBackup(from data: Data) throws -> UniversalBackup {
        let decoder = JSONDecoder()
        return try decoder.decode(UniversalBackup.self, from: data)
    }
}
