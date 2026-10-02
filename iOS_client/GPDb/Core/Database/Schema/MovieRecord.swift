import Foundation
import GRDB

/// 电影实体记录，1:1 精确映射 `movies` 表
public struct MovieRecord: Codable, FetchableRecord, TableRecord, PersistableRecord, Identifiable {
    public static let databaseTableName = "movies"

    public var id: Int64
    public var title: String
    public var studioId: Int64?
    public var studioName: String?
    public var releaseYear: Int?
    public var durationMins: Int?
    public var category: String?
    public var rating: String?
    public var movieType: String?
    public var description: String?
    public var coverIcon: String?
    public var coverFull: String?
    public var coverBack: String?
    public var coversJson: String?
    public var directorId: Int64?
    public var directorName: String?
    public var descriptionZh: String?
    public var translationAttempts: Int?
    public var titleZh: String?
    public var titleAttempts: Int?
    public var scrapedAt: String?

    enum CodingKeys: String, CodingKey {
        case id
        case title
        case studioId = "studio_id"
        case studioName = "studio_name"
        case releaseYear = "release_year"
        case durationMins = "duration_mins"
        case category
        case rating
        case movieType = "movie_type"
        case description
        case coverIcon = "cover_icon"
        case coverFull = "cover_full"
        case coverBack = "cover_back"
        case coversJson = "covers_json"
        case directorId = "director_id"
        case directorName = "director_name"
        case descriptionZh = "description_zh"
        case translationAttempts = "translation_attempts"
        case titleZh = "title_zh"
        case titleAttempts = "title_attempts"
        case scrapedAt = "scraped_at"
    }

    public init(
        id: Int64,
        title: String,
        studioId: Int64? = nil,
        studioName: String? = nil,
        releaseYear: Int? = nil,
        durationMins: Int? = nil,
        category: String? = nil,
        rating: String? = nil,
        movieType: String? = nil,
        description: String? = nil,
        coverIcon: String? = nil,
        coverFull: String? = nil,
        coverBack: String? = nil,
        coversJson: String? = nil,
        directorId: Int64? = nil,
        directorName: String? = nil,
        descriptionZh: String? = nil,
        translationAttempts: Int? = 0,
        titleZh: String? = nil,
        titleAttempts: Int? = 0,
        scrapedAt: String? = nil
    ) {
        self.id = id
        self.title = title
        self.studioId = studioId
        self.studioName = studioName
        self.releaseYear = releaseYear
        self.durationMins = durationMins
        self.category = category
        self.rating = rating
        self.movieType = movieType
        self.description = description
        self.coverIcon = coverIcon
        self.coverFull = coverFull
        self.coverBack = coverBack
        self.coversJson = coversJson
        self.directorId = directorId
        self.directorName = directorName
        self.descriptionZh = descriptionZh
        self.translationAttempts = translationAttempts
        self.titleZh = titleZh
        self.titleAttempts = titleAttempts
        self.scrapedAt = scrapedAt
    }

    /// 显示标题 (优先中文翻译片名)
    public var displayTitle: String {
        if let zh = titleZh, !zh.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return zh
        }
        return title
    }

    /// 副标题 (英文原名，若有中文译名)
    public var displaySubtitle: String? {
        if let zh = titleZh, !zh.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, zh != title {
            return title
        }
        return nil
    }

    /// 显示剧情简介 (优先中文翻译简介)
    public var displayDescription: String? {
        if let zh = descriptionZh, !zh.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return zh
        }
        return description
    }

    /// 格式化时长 (如 "1小时 45分钟" 或 "95分钟")
    public var formattedDuration: String? {
        guard let mins = durationMins, mins > 0 else { return nil }
        let h = mins / 60
        let m = mins % 60
        if h > 0 {
            return m > 0 ? "\(h)小时 \(m)分" : "\(h)小时"
        }
        return "\(m)分钟"
    }
}
