import Foundation
import GRDB

public struct PerformerDetailData {
    public let performer: PerformerRecord
    public let pbcProfile: PerformerPbcProfileRecord?
    public let sjProfile: PerformerSjProfileRecord?
    public let movies: [MovieRecord]
    public let episodes: [EpisodeRecord]
    public let allAliases: [String]
    public let isFavorite: Bool

    public init(
        performer: PerformerRecord,
        pbcProfile: PerformerPbcProfileRecord? = nil,
        sjProfile: PerformerSjProfileRecord? = nil,
        movies: [MovieRecord] = [],
        episodes: [EpisodeRecord] = [],
        allAliases: [String] = [],
        isFavorite: Bool = false
    ) {
        self.performer = performer
        self.pbcProfile = pbcProfile
        self.sjProfile = sjProfile
        self.movies = movies
        self.episodes = episodes
        self.allAliases = allAliases
        self.isFavorite = isFavorite
    }

    /// 互联档案外链列表 (IAFD, IMDb, Twitter/X, Instagram 等)
    public var externalLinks: [(name: String, url: String)] {
        var links: [(String, String)] = []
        if let jsonStr = pbcProfile?.socialLinksJson, let data = jsonStr.data(using: .utf8) {
            if let dict = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
                for (key, val) in dict {
                    if let u = val as? String, !u.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                        links.append((key.uppercased(), u))
                    }
                }
            }
        }
        if let jsonStr = pbcProfile?.externalIdsJson, let data = jsonStr.data(using: .utf8) {
            if let dict = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
                if let iafd = dict["iafd_id"] as? String, !iafd.isEmpty {
                    links.append(("IAFD", "https://www.iafd.com/person.rme/perfid=\(iafd)/gender=m"))
                }
                if let imdb = dict["imdb_id"] as? String, !imdb.isEmpty {
                    links.append(("IMDb", "https://www.imdb.com/name/nm\(imdb)"))
                }
            }
        }
        return links
    }

    /// 生理指标无损合并与全面中文汉化 (GEVI 优先，PBC 补缺，SJ 兜底)
    public var mergedTraits: [(label: String, value: String)] {
        var traits: [(String, String)] = []

        let h = performer.height ?? pbcProfile?.height ?? sjProfile?.height
        if let h = h, !h.isEmpty { traits.append(("身高", GlossaryHelper.trMeasure(h))) }

        let w = performer.weight ?? pbcProfile?.weight ?? sjProfile?.weight
        if let w = w, !w.isEmpty { traits.append(("体重", GlossaryHelper.trMeasure(w))) }

        let d = performer.dickSize ?? pbcProfile?.penisSize ?? sjProfile?.dickSize
        if let d = d, !d.isEmpty, d != "none available" { traits.append(("尺寸", GlossaryHelper.trMeasure(d))) }

        let f = performer.foreskin ?? pbcProfile?.foreskin ?? sjProfile?.foreskin
        if let f = f, !f.isEmpty, f != "none available" { traits.append(("包皮", GlossaryHelper.translate(f))) }

        let b = performer.build ?? pbcProfile?.build ?? sjProfile?.build
        if let b = b, !b.isEmpty { traits.append(("体型", GlossaryHelper.translate(b))) }

        let hr = performer.hair ?? pbcProfile?.hair ?? sjProfile?.hair
        if let hr = hr, !hr.isEmpty { traits.append(("发色", GlossaryHelper.translate(hr))) }

        let e = performer.eyes ?? pbcProfile?.eyes ?? sjProfile?.eyes
        if let e = e, !e.isEmpty { traits.append(("瞳色", GlossaryHelper.translate(e))) }

        let bh = performer.bodyHair ?? pbcProfile?.bodyHair ?? sjProfile?.bodyHair
        if let bh = bh, !bh.isEmpty { traits.append(("体毛", GlossaryHelper.translate(bh))) }

        let fh = performer.facialHair ?? pbcProfile?.facialHair
        if let fh = fh, !fh.isEmpty { traits.append(("胡须", GlossaryHelper.translate(fh))) }

        let t = performer.tattoos ?? pbcProfile?.tattoos ?? sjProfile?.tattoos
        if let t = t, !t.isEmpty, t != "none available" { traits.append(("纹身", GlossaryHelper.trTattoo(t))) }

        if let birth = GlossaryHelper.formatBirth(pbcProfile?.birthDate) {
            traits.append(("生日", birth))
        }

        if let astro = GlossaryHelper.formatAstro(pbcProfile?.astrology) {
            traits.append(("星座", astro))
        }

        if let place = pbcProfile?.birthPlace, !place.isEmpty {
            traits.append(("籍贯", place))
        }

        if let eth = pbcProfile?.ethnicity, !eth.isEmpty {
            traits.append(("族裔", GlossaryHelper.translate(eth)))
        }

        return traits
    }
}

public final class PerformerRepository {
    private let holder = DatabaseHolder.shared

    public init() {}

    /// 获取演员全息档案（GEVI + PBC + SmutJunkies 无损合并）
    public func getPerformerDetail(id: Int64) async throws -> PerformerDetailData? {
        guard let db = holder.database else { return nil }

        return try await db.read { db in
            guard let performer = try PerformerRecord.fetchOne(db, key: id) else { return nil }

            // 1. 获取 PBC 维基百科扩展资料
            let pbc = try PerformerPbcProfileRecord.fetchOne(db, key: id)

            // 2. 获取 SmutJunkies 扩展资料
            let sj = try PerformerSjProfileRecord.fetchOne(db, key: id)

            // 3. 获取出演的所有长片电影
            let moviesSql = """
            SELECT m.* FROM movies m
            JOIN movie_performers mp ON m.id = mp.movie_id
            WHERE mp.performer_id = ?
            ORDER BY m.release_year DESC, m.id DESC
            """
            let movies = try MovieRecord.fetchAll(db, sql: moviesSql, arguments: [id])

            // 4. 获取出演的所有场景分集
            let episodesSql = """
            SELECT e.* FROM episodes e
            JOIN episode_performers ep ON e.id = ep.episode_id
            WHERE ep.performer_id = ?
            ORDER BY e.release_date DESC, e.id DESC
            """
            let episodes = try EpisodeRecord.fetchAll(db, sql: episodesSql, arguments: [id])

            // 5. 聚合去重曾用艺名 (AKA)
            var aliasSet = Set<String>()
            let mainName = performer.name.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()

            // 来源 A: 影片参演历史别名
            let mpNamesSql = "SELECT DISTINCT performer_name FROM movie_performers WHERE performer_id = ?"
            let mpNames = try String.fetchAll(db, sql: mpNamesSql, arguments: [id])
            for name in mpNames {
                let clean = name.trimmingCharacters(in: .whitespacesAndNewlines)
                if !clean.isEmpty && clean.lowercased() != mainName {
                    aliasSet.insert(clean)
                }
            }

            // 来源 B: PBC 别名字段
            if let pbcAliases = pbc?.aliases {
                for alias in pbcAliases.components(separatedBy: ",") {
                    let clean = alias.trimmingCharacters(in: .whitespacesAndNewlines)
                    if !clean.isEmpty && clean.lowercased() != mainName {
                        aliasSet.insert(clean)
                    }
                }
            }

            // 来源 C: SJ 别名字段
            if let sjAliases = sj?.aliases {
                for alias in sjAliases.components(separatedBy: ",") {
                    let clean = alias.trimmingCharacters(in: .whitespacesAndNewlines)
                    if !clean.isEmpty && clean.lowercased() != mainName {
                        aliasSet.insert(clean)
                    }
                }
            }

            // 6. 是否已收藏
            let favSql = "SELECT 1 FROM user_favorites WHERE entity_type = 'performer' AND entity_key = ? LIMIT 1"
            let isFav = try Int.fetchOne(db, sql: favSql, arguments: [String(id)]) != nil

            return PerformerDetailData(
                performer: performer,
                pbcProfile: pbc,
                sjProfile: sj,
                movies: movies,
                episodes: episodes,
                allAliases: Array(aliasSet).sorted(),
                isFavorite: isFav
            )
        }
    }
}
