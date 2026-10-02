import SwiftUI
import GRDB
#if canImport(UIKit)
import UIKit
#endif

public struct EpisodeDetailView: View {
    public let episodeId: Int64

    private let holder = DatabaseHolder.shared
    private let userRepo = UserRepository()
    private let analyticsRepo = UserAnalyticsRepository.shared

    @State private var episode: EpisodeRecord? = nil
    @State private var parentMovie: MovieRecord? = nil
    @State private var performers: [PerformerRecord] = []
    @State private var isFavorite: Bool = false
    @State private var isLoading: Bool = true
    @State private var showShareSheet: Bool = false

    public init(episodeId: Int64) {
        self.episodeId = episodeId
    }

    public var body: some View {
        ScrollView {
            if isLoading {
                ProgressView()
                    .padding(.top, 60)
            } else if let ep = episode {
                VStack(alignment: .leading, spacing: 18) {
                    // 1. 16:9 展台与防形变剧照
                    GpdbImageView(rawPath: ep.thumbnailUrl, contentMode: .fill, cornerRadius: 16, placeholderIcon: "play.rectangle.fill")
                        .frame(maxWidth: .infinity)
                        .frame(height: 210)
                        .clipped()
                        .clipShape(RoundedRectangle(cornerRadius: 16))
                        .padding(.horizontal)

                    VStack(alignment: .leading, spacing: 12) {
                        // 2. 标题与日期
                        Text(ep.displayTitle)
                            .font(.title2.bold())

                        if let date = ep.releaseDate {
                            Text("发行日期: \(date)")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }

                        // 3. 关联所属电影
                        if let movie = parentMovie {
                            NavigationLink(destination: MovieDetailView(movieId: movie.id)) {
                                HStack(spacing: 10) {
                                    GpdbImageView(rawPath: movie.coverFull ?? movie.coverIcon, contentMode: .fill, cornerRadius: 6)
                                        .frame(width: 40, height: 56)

                                    VStack(alignment: .leading, spacing: 2) {
                                        Text("收录于完整长片:")
                                            .font(.caption2)
                                            .foregroundStyle(.secondary)
                                        Text(movie.displayTitle)
                                            .font(.subheadline.bold())
                                            .lineLimit(1)
                                    }
                                    Spacer()
                                    Image(systemName: "chevron.right")
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                                .padding(10)
                                .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 10))
                            }
                            .buttonStyle(.plain)
                        }

                        // 4. 片段动作笔记与简介
                        if let desc = ep.displayDescription {
                            Divider()
                            VStack(alignment: .leading, spacing: 6) {
                                Text("剧情说明")
                                    .font(.subheadline.bold())
                                Text(desc)
                                    .font(.caption)
                                    .lineSpacing(4)
                                    .foregroundStyle(.primary.opacity(0.85))
                            }
                        }

                        // 5. 参演演员
                        if !performers.isEmpty {
                            Divider()
                            VStack(alignment: .leading, spacing: 10) {
                                Text("出镜演员 (\(performers.count))")
                                    .font(.subheadline.bold())

                                HStack(spacing: 12) {
                                    ForEach(performers) { p in
                                        NavigationLink(destination: PerformerDetailView(performerId: p.id)) {
                                            VStack(spacing: 4) {
                                                GpdbImageView(rawPath: p.imageUrl, contentMode: .fill, cornerRadius: 24, placeholderIcon: "person.fill")
                                                    .frame(width: 48, height: 48)
                                                    .clipShape(Circle())

                                                Text(p.name)
                                                    .font(.caption2.bold())
                                                    .lineLimit(1)
                                            }
                                        }
                                        .buttonStyle(.plain)
                                    }
                                }
                            }
                        }
                    }
                    .padding(.horizontal)
                }
                .padding(.vertical)
            }
        }
        .dynamicAmbientBackground(imagePath: episode?.thumbnailUrl ?? parentMovie?.coverFull)
        .inlineNavigationTitle()
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                HStack(spacing: 12) {
                    Button {
                        showShareSheet = true
                    } label: {
                        Image(systemName: "square.and.arrow.up")
                    }

                    Button {
                        Task { await toggleFavorite() }
                    } label: {
                        Image(systemName: isFavorite ? "heart.fill" : "heart")
                            .foregroundStyle(isFavorite ? .red : .primary)
                    }
                }
            }
        }
        .sheet(isPresented: $showShareSheet) {
            if let ep = episode {
                ShareCardModalView(
                    title: ep.displayTitle,
                    isEpisode: true,
                    studio: ep.studioName ?? parentMovie?.studioName,
                    performers: performers.map { $0.name },
                    synopsis: ep.displayDescription,
                    posterPath: ep.thumbnailUrl,
                    backCoverPath: nil
                )
            }
        }
        .task {
            if episode == nil {
                await loadDetail()
                analyticsRepo.recordEpisodeView()
            }
        }
    }

    private func loadDetail() async {
        guard let db = holder.database else { return }
        isLoading = true
        defer { isLoading = false }
        do {
            let (ep, parent, perfs, fav) = try await db.read { db -> (EpisodeRecord?, MovieRecord?, [PerformerRecord], Bool) in
                let ep = try EpisodeRecord.fetchOne(db, key: episodeId)
                var parent: MovieRecord? = nil
                if let movieId = ep?.movieId {
                    parent = try MovieRecord.fetchOne(db, key: movieId)
                }
                let perfSql = """
                SELECT p.* FROM performers p
                JOIN episode_performers ep ON p.id = ep.performer_id
                WHERE ep.episode_id = ?
                """
                let perfs = try PerformerRecord.fetchAll(db, sql: perfSql, arguments: [episodeId])

                let favSql = "SELECT 1 FROM user_favorites WHERE entity_type = 'episode' AND entity_key = ? LIMIT 1"
                let fav = try Int.fetchOne(db, sql: favSql, arguments: [String(episodeId)]) != nil

                return (ep, parent, perfs, fav)
            }
            self.episode = ep
            self.parentMovie = parent
            self.performers = perfs
            self.isFavorite = fav
        } catch {
            print("加载分集失败: \(error)")
        }
    }

    private func toggleFavorite() async {
        isFavorite.toggle()
        #if canImport(UIKit)
        let impact = UIImpactFeedbackGenerator(style: .medium)
        impact.impactOccurred()
        #endif
        try? await userRepo.toggleFavorite(entityType: "episode", entityKey: String(episodeId), isFavorite: isFavorite)
    }
}
