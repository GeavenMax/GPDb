import Foundation
import GRDB

public struct HomeFeedData {
    public let spotlightMovies: [MovieRecord]
    public let todayStars: [PerformerRecord]
    public let classicSeries: [SeriesCollectionRecord]
    public let luckyMovies: [MovieRecord]
    public let totalMovies: Int
    public let totalEpisodes: Int
    public let totalPerformers: Int
    public let totalStudios: Int

    public init(
        spotlightMovies: [MovieRecord] = [],
        todayStars: [PerformerRecord] = [],
        classicSeries: [SeriesCollectionRecord] = [],
        luckyMovies: [MovieRecord] = [],
        totalMovies: Int = 0,
        totalEpisodes: Int = 0,
        totalPerformers: Int = 0,
        totalStudios: Int = 0
    ) {
        self.spotlightMovies = spotlightMovies
        self.todayStars = todayStars
        self.classicSeries = classicSeries
        self.luckyMovies = luckyMovies
        self.totalMovies = totalMovies
        self.totalEpisodes = totalEpisodes
        self.totalPerformers = totalPerformers
        self.totalStudios = totalStudios
    }
}

public final class HomeFeedRepository {
    private let holder = DatabaseHolder.shared

    public init() {}

    /// 获取首页聚合 Feed 数据
    public func getHomeFeed() async throws -> HomeFeedData {
        guard let db = holder.database else {
            return HomeFeedData()
        }

        return try await db.read { db in
            // 1. 焦点轮播电影 (12 部带封面与简介的精选影片)
            let spotlightSql = """
            SELECT * FROM movies
            WHERE cover_full IS NOT NULL AND TRIM(cover_full) != ''
              AND ((description_zh IS NOT NULL AND TRIM(description_zh) != '') OR (description IS NOT NULL AND TRIM(description) != ''))
            ORDER BY RANDOM()
            LIMIT 12
            """
            let spotlight = try MovieRecord.fetchAll(db, sql: spotlightSql)

            // 2. 今日星光 · 标志面孔 (严格从图片库中筛选能获取到真实头像的演员)
            let starsSql = """
            SELECT p.* FROM performers p
            JOIN movie_performers mp ON p.id = mp.performer_id
            WHERE p.image_url IS NOT NULL AND TRIM(p.image_url) != ''
            GROUP BY p.id
            HAVING COUNT(mp.movie_id) >= 3
            ORDER BY RANDOM()
            LIMIT 80
            """
            let candidateStars = try PerformerRecord.fetchAll(db, sql: starsSql)
            let verifiedStars = candidateStars.filter { performer in
                guard let img = performer.imageUrl, !img.isEmpty else { return false }
                if let relPath = img.toImageCachePath() {
                    if ZipArchiveService.shared.hasEntry(path: relPath) { return true }
                }
                return false
            }
            let stars = verifiedStars.isEmpty ? Array(candidateStars.prefix(20)) : Array(verifiedStars.prefix(20))

            // 3. 经典系列大放送 · 连贯篇章 (随机抽取 10 个系列)
            let seriesSql = """
            SELECT DISTINCT s.* FROM series_collections s
            WHERE s.movie_count >= 2
            ORDER BY RANDOM()
            LIMIT 10
            """
            let series = try SeriesCollectionRecord.fetchAll(db, sql: seriesSql)

            // 4. 随心探索 · 盲盒发现 (随机抽取 6 部有封面海报的佳作)
            let luckySql = """
            SELECT * FROM movies
            WHERE cover_full IS NOT NULL AND TRIM(cover_full) != ''
            ORDER BY RANDOM()
            LIMIT 6
            """
            let lucky = try MovieRecord.fetchAll(db, sql: luckySql)

            // 5. 全库总览统计计数
            let totalM = try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM movies") ?? 0
            let totalE = try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM episodes") ?? 0
            let totalP = try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM performers") ?? 0
            let totalS = try Int.fetchOne(db, sql: "SELECT COUNT(DISTINCT NULLIF(TRIM(studio_name), '')) FROM movies") ?? 0

            return HomeFeedData(
                spotlightMovies: spotlight,
                todayStars: stars,
                classicSeries: series,
                luckyMovies: lucky,
                totalMovies: totalM,
                totalEpisodes: totalE,
                totalPerformers: totalP,
                totalStudios: totalS
            )
        }
    }

    /// 随心探索 · 换一批盲盒影片 (随机 6 部)
    public func getLuckyMovies(count: Int = 6) async throws -> [MovieRecord] {
        guard let db = holder.database else { return [] }
        return try await db.read { db in
            let sql = """
            SELECT * FROM movies
            WHERE cover_full IS NOT NULL AND TRIM(cover_full) != ''
            ORDER BY RANDOM()
            LIMIT \(count)
            """
            return try MovieRecord.fetchAll(db, sql: sql)
        }
    }

    /// 随心探索盲盒抽卡 (单张兜底)
    public func getRandomMovie() async throws -> MovieRecord? {
        guard let db = holder.database else { return nil }
        return try await db.read { db in
            let sql = """
            SELECT * FROM movies
            WHERE cover_full IS NOT NULL AND TRIM(cover_full) != ''
            ORDER BY RANDOM()
            LIMIT 1
            """
            return try MovieRecord.fetchOne(db, sql: sql)
        }
    }
}
