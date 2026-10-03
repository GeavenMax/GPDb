import Foundation
import GRDB

public struct StudioItem: Identifiable {
    public var id: String { name }
    public let name: String
    public let count: Int
}

public struct DirectorItem: Identifiable {
    public var id: Int64 { director.id }
    public let director: DirectorRecord
    public let movieCount: Int
}

public final class BrowseRepository {
    private let holder = DatabaseHolder.shared

    public init() {}

    /// 获取电影列表 (多维筛选 + 三级降级检索 + 分页)
    public func getMovies(
        query: String? = nil,
        studio: String? = nil,
        director: String? = nil,
        category: String? = nil,
        year: Int? = nil,
        sortBy: String = "id_desc",
        page: Int = 1,
        pageSize: Int = 24
    ) async throws -> [MovieRecord] {
        guard let db = holder.database else { return [] }

        return try await db.read { db in
            var conditions: [String] = []
            var arguments: [DatabaseValueConvertible] = []

            if let q = query?.trimmingCharacters(in: .whitespacesAndNewlines), !q.isEmpty {
                // 多字段容错联合检索：原名、中文名、片商、导演、中文简介、演员、多导演关联表
                conditions.append("""
                (m.title LIKE ? OR m.title_zh LIKE ? OR m.studio_name LIKE ? OR m.director_name LIKE ? OR m.description_zh LIKE ? OR EXISTS (
                    SELECT 1 FROM movie_performers mp WHERE mp.movie_id = m.id AND mp.performer_name LIKE ?
                ) OR EXISTS (
                    SELECT 1 FROM movie_directors md JOIN directors d ON d.id = md.director_id WHERE md.movie_id = m.id AND d.name LIKE ?
                ))
                """)
                let match = "%\(q)%"
                arguments.append(contentsOf: [match, match, match, match, match, match, match])
            }

            if let s = studio, !s.isEmpty {
                conditions.append("m.studio_name = ?")
                arguments.append(s)
            }

            if let d = director, !d.isEmpty {
                conditions.append("""
                (m.director_name = ? OR EXISTS (
                    SELECT 1 FROM movie_directors md
                    JOIN directors d ON d.id = md.director_id
                    WHERE md.movie_id = m.id AND d.name = ?
                ))
                """)
                arguments.append(d)
                arguments.append(d)
            }

            if let c = category, !c.isEmpty {
                conditions.append("m.category LIKE ?")
                arguments.append("%\(c)%")
            }

            if let y = year, y > 0 {
                conditions.append("m.release_year = ?")
                arguments.append(y)
            }

            let whereClause = conditions.isEmpty ? "" : "WHERE " + conditions.joined(separator: " AND ")

            let orderClause: String
            switch sortBy {
            case "year_desc": orderClause = "ORDER BY m.release_year DESC, m.id DESC"
            case "year_asc": orderClause = "ORDER BY m.release_year ASC, m.id ASC"
            case "title_asc": orderClause = "ORDER BY m.title ASC"
            default: orderClause = "ORDER BY m.id DESC"
            }

            let offset = max(0, (page - 1) * pageSize)
            let sql = """
            SELECT m.* FROM movies m
            \(whereClause)
            \(orderClause)
            LIMIT \(pageSize) OFFSET \(offset)
            """

            return try MovieRecord.fetchAll(db, sql: sql, arguments: StatementArguments(arguments))
        }
    }

    /// 获取演员列表 (多维属性筛选 + 检索 + 分页)
    public func getPerformers(
        query: String? = nil,
        build: String? = nil,
        hair: String? = nil,
        eyes: String? = nil,
        sortBy: String = "works_desc",
        page: Int = 1,
        pageSize: Int = 24
    ) async throws -> [PerformerRecord] {
        guard let db = holder.database else { return [] }

        return try await db.read { db in
            var conditions: [String] = []
            var arguments: [DatabaseValueConvertible] = []

            if let q = query?.trimmingCharacters(in: .whitespacesAndNewlines), !q.isEmpty {
                conditions.append("(p.name LIKE ?)")
                arguments.append("%\(q)%")
            }

            if let b = build, !b.isEmpty {
                conditions.append("p.build LIKE ?")
                arguments.append("%\(b)%")
            }

            if let h = hair, !h.isEmpty {
                conditions.append("p.hair LIKE ?")
                arguments.append("%\(h)%")
            }

            if let e = eyes, !e.isEmpty {
                conditions.append("p.eyes LIKE ?")
                arguments.append("%\(e)%")
            }

            let whereClause = conditions.isEmpty ? "" : "WHERE " + conditions.joined(separator: " AND ")

            let worksExpr = "(SELECT COUNT(*) FROM movie_performers mp WHERE mp.performer_id = p.id) + (SELECT COUNT(*) FROM episode_performers ep WHERE ep.performer_id = p.id)"

            let orderClause: String
            switch sortBy {
            case "name_asc": orderClause = "ORDER BY p.name ASC"
            case "id_desc": orderClause = "ORDER BY p.id DESC"
            default: orderClause = "ORDER BY \(worksExpr) DESC, p.id DESC"
            }

            let offset = max(0, (page - 1) * pageSize)
            let sql = """
            SELECT p.* FROM performers p
            \(whereClause)
            \(orderClause)
            LIMIT \(pageSize) OFFSET \(offset)
            """

            return try PerformerRecord.fetchAll(db, sql: sql, arguments: StatementArguments(arguments))
        }
    }

    /// 获取片商聚合列表 (影片与分集作品数量之和)
    public func getStudios() async throws -> [StudioItem] {
        guard let db = holder.database else { return [] }

        return try await db.read { db in
            let sql = """
            WITH m_counts AS (
                SELECT studio_name, COUNT(*) as m_cnt 
                FROM movies 
                WHERE studio_name IS NOT NULL AND TRIM(studio_name) != '' 
                GROUP BY studio_name
            ),
            e_counts AS (
                SELECT IFNULL(NULLIF(e.studio_name, ''), m.studio_name) as eff_studio, COUNT(*) as ep_cnt 
                FROM episodes e 
                LEFT JOIN movies m ON e.movie_id = m.id 
                GROUP BY eff_studio
            )
            SELECT s.name, 
                   COALESCE(m.m_cnt, 0) + COALESCE(e.ep_cnt, 0) as total_count
            FROM (
                SELECT studio_name as name FROM m_counts
                UNION
                SELECT eff_studio as name FROM e_counts WHERE eff_studio IS NOT NULL AND TRIM(eff_studio) != ''
            ) s
            LEFT JOIN m_counts m ON s.name = m.studio_name
            LEFT JOIN e_counts e ON s.name = e.eff_studio
            ORDER BY total_count DESC, s.name ASC
            """
            let rows = try Row.fetchAll(db, sql: sql)
            return rows.compactMap { row in
                guard let name = row["name"] as? String else { return nil }
                let count = (row["total_count"] as? Int64).map(Int.init) ?? (row["total_count"] as? Int ?? 0)
                return StudioItem(name: name, count: count)
            }
        }
    }

    /// 获取片商名下的作品总数（电影数与分集数）
    public func getStudioWorksCounts(studio: String) async throws -> (moviesCount: Int, episodesCount: Int) {
        guard let db = holder.database else { return (0, 0) }

        return try await db.read { db in
            let movieSql = "SELECT COUNT(*) FROM movies WHERE studio_name = ?"
            let epSql = "SELECT COUNT(*) FROM episodes e LEFT JOIN movies m ON e.movie_id = m.id WHERE IFNULL(NULLIF(e.studio_name, ''), m.studio_name) = ?"
            let mCount = try Int.fetchOne(db, sql: movieSql, arguments: [studio]) ?? 0
            let eCount = try Int.fetchOne(db, sql: epSql, arguments: [studio]) ?? 0
            return (mCount, eCount)
        }
    }

    /// 获取经典系列列表
    public func getSeries(query: String? = nil) async throws -> [SeriesCollectionRecord] {
        guard let db = holder.database else { return [] }

        return try await db.read { db in
            if let q = query?.trimmingCharacters(in: .whitespacesAndNewlines), !q.isEmpty {
                let sql = """
                SELECT DISTINCT s.* FROM series_collections s
                WHERE s.root_title LIKE ? OR s.studio_name LIKE ?
                ORDER BY s.movie_count DESC
                """
                let match = "%\(q)%"
                return try SeriesCollectionRecord.fetchAll(db, sql: sql, arguments: [match, match])
            } else {
                let sql = """
                SELECT DISTINCT s.* FROM series_collections s
                ORDER BY s.movie_count DESC
                """
                return try SeriesCollectionRecord.fetchAll(db, sql: sql)
            }
        }
    }

    /// 获取分集片段列表 (分页 + 多维联合过滤)
    public func getEpisodes(
        query: String? = nil,
        studio: String? = nil,
        page: Int = 1,
        pageSize: Int = 40
    ) async throws -> [EpisodeRecord] {
        guard let db = holder.database else { return [] }

        return try await db.read { db in
            var conditions: [String] = []
            var arguments: [DatabaseValueConvertible] = []

            if let q = query?.trimmingCharacters(in: .whitespacesAndNewlines), !q.isEmpty {
                conditions.append("(e.title LIKE ? OR e.title_zh LIKE ? OR e.description_zh LIKE ? OR e.studio_name LIKE ?)")
                let match = "%\(q)%"
                arguments.append(contentsOf: [match, match, match, match])
            }

            if let s = studio, !s.isEmpty {
                conditions.append("IFNULL(NULLIF(e.studio_name, ''), m.studio_name) = ?")
                arguments.append(s)
            }

            let whereClause = conditions.isEmpty ? "" : "WHERE " + conditions.joined(separator: " AND ")
            let offset = max(0, (page - 1) * pageSize)
            let sql = """
            SELECT e.* FROM episodes e
            LEFT JOIN movies m ON e.movie_id = m.id
            \(whereClause)
            ORDER BY e.release_date DESC, e.id DESC
            LIMIT \(pageSize) OFFSET \(offset)
            """

            return try EpisodeRecord.fetchAll(db, sql: sql, arguments: StatementArguments(arguments))
        }
    }

    /// 获取导演列表 (执导影片与所属分集作品数量之和)
    public func getDirectors(query: String? = nil) async throws -> [DirectorItem] {
        guard let db = holder.database else { return [] }

        return try await db.read { db in
            var whereClause = ""
            var arguments: [DatabaseValueConvertible] = []
            if let q = query?.trimmingCharacters(in: .whitespacesAndNewlines), !q.isEmpty {
                whereClause = "WHERE d.name LIKE ?"
                arguments.append("%\(q)%")
            }

            let sql = """
            WITH m_counts AS (
                SELECT director_id, COUNT(DISTINCT movie_id) as m_cnt
                FROM movie_directors
                GROUP BY director_id
            ),
            e_counts AS (
                SELECT md.director_id, COUNT(DISTINCT e.id) as ep_cnt
                FROM episodes e
                JOIN movie_directors md ON e.movie_id = md.movie_id
                GROUP BY md.director_id
            )
            SELECT d.*, 
                   COALESCE(m.m_cnt, 0) + COALESCE(e.ep_cnt, 0) as total_count
            FROM directors d
            LEFT JOIN m_counts m ON d.id = m.director_id
            LEFT JOIN e_counts e ON d.id = e.director_id
            \(whereClause)
            ORDER BY total_count DESC, d.name ASC
            """
            let rows = try Row.fetchAll(db, sql: sql, arguments: StatementArguments(arguments))
            return rows.compactMap { row in
                guard let record = try? DirectorRecord(row: row) else { return nil }
                let count = (row["total_count"] as? Int64).map(Int.init) ?? (row["total_count"] as? Int ?? 0)
                return DirectorItem(director: record, movieCount: count)
            }
        }
    }
}
