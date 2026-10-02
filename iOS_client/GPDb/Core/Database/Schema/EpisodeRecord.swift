import Foundation
import GRDB

/// 场景片段实体，映射 `episodes`
public struct EpisodeRecord: Codable, FetchableRecord, TableRecord, PersistableRecord, Identifiable {
    public static let databaseTableName = "episodes"

    public var id: Int64
    public var movieId: Int64?
    public var title: String?
    public var thumbnailUrl: String?
    public var description: String?
    public var descriptionZh: String?
    public var actionNotes: String?
    public var releaseDate: String?
    public var studioId: Int64?
    public var studioName: String?

    enum CodingKeys: String, CodingKey {
        case id
        case movieId = "movie_id"
        case title
        case thumbnailUrl = "thumbnail_url"
        case description
        case descriptionZh = "description_zh"
        case actionNotes = "action_notes"
        case releaseDate = "release_date"
        case studioId = "studio_id"
        case studioName = "studio_name"
    }

    public var displayTitle: String {
        if let t = title, !t.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return t
        }
        return "片段 #\(id)"
    }

    public var displayDescription: String? {
        if let zh = descriptionZh, !zh.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return zh
        }
        return description
    }
}

/// 电影-演员多对多关联，映射 `movie_performers`
public struct MoviePerformerRecord: Codable, FetchableRecord, TableRecord, PersistableRecord {
    public static let databaseTableName = "movie_performers"

    public var movieId: Int64
    public var performerId: Int64
    public var performerName: String?

    enum CodingKeys: String, CodingKey {
        case movieId = "movie_id"
        case performerId = "performer_id"
        case performerName = "performer_name"
    }
}

/// 分集-演员多对多关联，映射 `episode_performers`
public struct EpisodePerformerRecord: Codable, FetchableRecord, TableRecord, PersistableRecord {
    public static let databaseTableName = "episode_performers"

    public var episodeId: Int64
    public var performerId: Int64
    public var performerName: String?

    enum CodingKeys: String, CodingKey {
        case episodeId = "episode_id"
        case performerId = "performer_id"
        case performerName = "performer_name"
    }
}
