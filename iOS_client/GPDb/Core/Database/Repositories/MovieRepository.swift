import Foundation
import GRDB

public struct MovieDetailData {
    public let movie: MovieRecord
    public let performers: [PerformerRecord]
    public let episodes: [EpisodeRecord]
    public let directors: [DirectorRecord]
    public let userData: UserMovieDataRecord?
    public let isFavorite: Bool

    public init(
        movie: MovieRecord,
        performers: [PerformerRecord] = [],
        episodes: [EpisodeRecord] = [],
        directors: [DirectorRecord] = [],
        userData: UserMovieDataRecord? = nil,
        isFavorite: Bool = false
    ) {
        self.movie = movie
        self.performers = performers
        self.episodes = episodes
        self.directors = directors
        self.userData = userData
        self.isFavorite = isFavorite
    }
}

public final class MovieRepository {
    private let holder = DatabaseHolder.shared

    public init() {}

    /// 获取电影全量详情与关联演职员、分集及用户状态
    public func getMovieDetail(id: Int64) async throws -> MovieDetailData? {
        guard let db = holder.database else { return nil }

        return try await db.read { db in
            guard let movie = try MovieRecord.fetchOne(db, key: id) else { return nil }

            // 关联演员列表
            let performersSql = """
            SELECT p.* FROM performers p
            JOIN movie_performers mp ON p.id = mp.performer_id
            WHERE mp.movie_id = ?
            ORDER BY p.id ASC
            """
            let performers = try PerformerRecord.fetchAll(db, sql: performersSql, arguments: [id])

            // 关联场景分集
            let episodesSql = """
            SELECT * FROM episodes
            WHERE movie_id = ?
            ORDER BY id ASC
            """
            let episodes = try EpisodeRecord.fetchAll(db, sql: episodesSql, arguments: [id])

            // 关联导演列表 (从 movie_directors 关联表读取真实多导演花名册)
            let directorsSql = """
            SELECT d.* FROM directors d
            JOIN movie_directors md ON d.id = md.director_id
            WHERE md.movie_id = ?
            ORDER BY md.position ASC, d.id ASC
            """
            let directors = try DirectorRecord.fetchAll(db, sql: directorsSql, arguments: [id])

            // 用户评分与私密笔记
            let userData = try UserMovieDataRecord.fetchOne(db, key: id)

            // 是否已收藏
            let favSql = "SELECT 1 FROM user_favorites WHERE entity_type = 'movie' AND entity_key = ? LIMIT 1"
            let isFav = try Int.fetchOne(db, sql: favSql, arguments: [String(id)]) != nil

            return MovieDetailData(
                movie: movie,
                performers: performers,
                episodes: episodes,
                directors: directors,
                userData: userData,
                isFavorite: isFav
            )
        }
    }
}
