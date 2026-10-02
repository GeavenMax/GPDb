import Foundation
import GRDB

/// 系列集合实体，映射 `series_collections`
public struct SeriesCollectionRecord: Codable, FetchableRecord, TableRecord, PersistableRecord, Identifiable {
    public static let databaseTableName = "series_collections"

    public var id: Int64?
    public var rootTitle: String
    public var studioName: String?
    public var movieCount: Int
    public var coverUrl: String?
    public var yearStart: Int?
    public var yearEnd: Int?
    public var sampleMovieIds: String?
    public var sampleCovers: String?
    public var updatedAt: String?

    enum CodingKeys: String, CodingKey {
        case id
        case rootTitle = "root_title"
        case studioName = "studio_name"
        case movieCount = "movie_count"
        case coverUrl = "cover_url"
        case yearStart = "year_start"
        case yearEnd = "year_end"
        case sampleMovieIds = "sample_movie_ids"
        case sampleCovers = "sample_covers"
        case updatedAt = "updated_at"
    }

    /// 复合唯一键，彻底防止 ForEach 键冲突闪退
    public var compositeKey: String {
        if let id = self.id { return "series_\(id)" }
        let s = studioName ?? "unknown"
        return "\(s)_\(rootTitle)_\(rootTitle.hashValue)"
    }

    /// 友好展示标题（如 "SeriesName (StudioName)"）
    public var displayTitle: String {
        let clean = rootTitle.replacingOccurrences(of: "|||", with: " ")
        if let studio = studioName, !studio.isEmpty {
            return "\(clean) (\(studio))"
        }
        return clean
    }

    /// 年份范围描述 (如 "2015 - 2021")
    public var yearSpan: String? {
        if let s = yearStart, let e = yearEnd {
            return s == e ? "\(s)年" : "\(s) - \(e)年"
        } else if let s = yearStart {
            return "\(s)年"
        }
        return nil
    }
}

/// 导演实体，映射 `directors`
public struct DirectorRecord: Codable, FetchableRecord, TableRecord, PersistableRecord, Identifiable {
    public static let databaseTableName = "directors"

    public var id: Int64
    public var siteId: Int64?
    public var name: String
    public var worksCount: Int?
    public var createdAt: String?

    enum CodingKeys: String, CodingKey {
        case id
        case siteId = "site_id"
        case name
        case worksCount = "works_count"
        case createdAt = "created_at"
    }
}

/// 电影-导演关联，映射 `movie_directors`
public struct MovieDirectorRecord: Codable, FetchableRecord, TableRecord, PersistableRecord {
    public static let databaseTableName = "movie_directors"

    public var movieId: Int64
    public var directorId: Int64
    public var position: Int

    enum CodingKeys: String, CodingKey {
        case movieId = "movie_id"
        case directorId = "director_id"
        case position
    }
}
