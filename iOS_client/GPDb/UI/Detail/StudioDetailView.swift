import SwiftUI
#if canImport(UIKit)
import UIKit
#endif

public enum StudioTab: String, CaseIterable, Identifiable {
    case movies = "发行作品"
    case episodes = "发行分集"

    public var id: String { rawValue }
}

/// 片商/厂牌档案全息页 (对标 Android StudioDetailScreen，支持电影与分集双瀑布流)
public struct StudioDetailView: View {
    @EnvironmentObject private var environment: AppEnvironment
    public let studioName: String

    private let repository = BrowseRepository()
    private let userRepo = UserRepository()

    @State private var selectedTab: StudioTab = .movies
    @State private var movies: [MovieRecord] = []
    @State private var episodes: [EpisodeRecord] = []
    @State private var totalMoviesCount: Int = 0
    @State private var totalEpisodesCount: Int = 0
    @State private var logoUrl: String? = nil
    @State private var bannerUrl: String? = nil
    @State private var isFavorite: Bool = false
    @State private var isLoading: Bool = true

    public init(studioName: String) {
        self.studioName = studioName
    }

    public var body: some View {
        VStack(spacing: 0) {
            // 片商头部档案 (Logo 与统计摘要)
            studioHeaderView
                .padding(.horizontal)
                .padding(.top, 12)
                .padding(.bottom, 6)

            // 分类标签栏
            Picker("分类", selection: $selectedTab) {
                Text("发行作品 (\(max(totalMoviesCount, movies.count)))").tag(StudioTab.movies)
                Text("发行分集 (\(max(totalEpisodesCount, episodes.count)))").tag(StudioTab.episodes)
            }
            .pickerStyle(.segmented)
            .padding(.horizontal)
            .padding(.vertical, 8)

            if isLoading {
                Spacer()
                ProgressView()
                Spacer()
            } else {
                tabContent
            }
        }
        .dynamicAmbientBackground(imagePath: logoUrl ?? movies.first?.coverFull ?? movies.first?.coverIcon ?? episodes.first?.thumbnailUrl)
        .navigationTitle(cleanTitle(studioName))
        .inlineNavigationTitle()
        .toolbar(environment.isTabBarHidden ? .hidden : .visible, for: .tabBar)
        .toolbar {
            ToolbarItem(placement: .principal) {
                MarqueeText(text: cleanTitle(studioName), font: .headline, weight: .bold, speed: 25)
                    .frame(maxWidth: 220)
            }
            ToolbarItem(placement: .primaryAction) {
                HStack(spacing: 12) {
                    if selectedTab == .movies {
                        ColumnSwitchButton()
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
        .task {
            if movies.isEmpty && episodes.isEmpty {
                await loadStudioData()
            }
        }
    }

    @ViewBuilder
    private var tabContent: some View {
        switch selectedTab {
        case .movies:
            if movies.isEmpty {
                ContentUnavailableView("暂无发行作品", systemImage: "film.stack", description: Text("该片商名下暂无长片电影记录。"))
            } else {
                moviesGrid
            }
        case .episodes:
            if episodes.isEmpty {
                ContentUnavailableView("暂无发行分集", systemImage: "play.rectangle.on.rectangle", description: Text("该片商名下暂无分集场景片段记录。"))
            } else {
                episodesList
            }
        }
    }

    private var moviesGrid: some View {
        WaterfallScrollView {
            LazyVGrid(
                columns: Array(repeating: GridItem(.flexible(), spacing: 12), count: environment.layoutColumns),
                spacing: 14
            ) {
                ForEach(movies) { movie in
                    NavigationLink(destination: MovieDetailView(movieId: movie.id)) {
                        VStack(alignment: .leading, spacing: 6) {
                            GpdbImageView(
                                rawPath: movie.coverFull ?? movie.coverIcon,
                                contentMode: .fill,
                                cornerRadius: 10
                            )
                            .aspectRatio(0.68, contentMode: .fit)

                            MarqueeText(text: movie.displayTitle, font: .caption, weight: .bold, speed: 25)

                            HStack {
                                if let y = movie.releaseYear {
                                    Text("\(y)年")
                                        .font(.caption2)
                                        .foregroundStyle(.secondary)
                                }
                                Spacer()
                                if let dur = movie.formattedDuration {
                                    Text(dur)
                                        .font(.caption2)
                                        .foregroundStyle(.secondary)
                                }
                            }
                        }
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding()
        }
    }

    private var episodesList: some View {
        WaterfallScrollView {
            LazyVStack(spacing: 14) {
                ForEach(episodes) { ep in
                    NavigationLink(destination: EpisodeDetailView(episodeId: ep.id)) {
                        HStack(spacing: 12) {
                            GpdbImageView(
                                rawPath: ep.thumbnailUrl,
                                contentMode: .fill,
                                cornerRadius: 8,
                                placeholderIcon: "play.rectangle.fill"
                            )
                            .frame(width: 120, height: 75)
                            .clipped()
                            .clipShape(RoundedRectangle(cornerRadius: 8))

                            VStack(alignment: .leading, spacing: 4) {
                                MarqueeText(text: ep.displayTitle, font: .subheadline, weight: .bold, speed: 25)

                                if let date = ep.releaseDate {
                                    Text("发行: \(date)")
                                        .font(.caption2)
                                        .foregroundStyle(.secondary)
                                }

                                if let desc = ep.displayDescription {
                                    Text(desc)
                                        .font(.caption2)
                                        .foregroundStyle(.secondary)
                                        .lineLimit(1)
                                }
                            }
                            Spacer()
                        }
                        .padding(.horizontal)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.vertical, 8)
        }
    }

    private var studioHeaderView: some View {
        HStack(spacing: 16) {
            // Logo 容器 (带微弱半透明底色与首字母降级兜底)
            ZStack {
                if let logo = logoUrl, !logo.isEmpty {
                    GpdbImageView(
                        rawPath: logo,
                        contentMode: .fit,
                        cornerRadius: 12,
                        placeholderIcon: "building.2.crop.circle"
                    )
                    .padding(6)
                } else {
                    LinearGradient(
                        colors: [Color.blue.opacity(0.8), Color.purple.opacity(0.8)],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                    Text(studioInitials)
                        .font(.system(size: 24, weight: .black))
                        .foregroundStyle(.white)
                }
            }
            .frame(width: 68, height: 68)
            .background(Color.secondary.opacity(0.12))
            .clipShape(RoundedRectangle(cornerRadius: 14))
            .overlay(
                RoundedRectangle(cornerRadius: 14)
                    .stroke(Color.primary.opacity(0.08), lineWidth: 1)
            )

            VStack(alignment: .leading, spacing: 4) {
                Text(cleanTitle(studioName))
                    .font(.title3.bold())
                    .lineLimit(2)

                HStack(spacing: 8) {
                    Label("\(totalMoviesCount) 部长片", systemImage: "film")
                        .font(.caption)
                        .foregroundStyle(.secondary)

                    Text("·")
                        .foregroundStyle(.secondary)

                    Label("\(totalEpisodesCount) 个分集", systemImage: "play.rectangle")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            Spacer()
        }
    }

    private var studioInitials: String {
        let clean = cleanTitle(studioName)
        let words = clean.split(separator: " ").filter { !$0.isEmpty }
        if words.count >= 2 {
            return "\(words[0].prefix(1))\(words[1].prefix(1))".uppercased()
        }
        return String(clean.prefix(2)).uppercased()
    }

    private func cleanTitle(_ t: String) -> String {
        return t.replacingOccurrences(of: "|||", with: " ")
    }

    private func loadStudioData() async {
        isLoading = true
        do {
            async let fetchCounts = repository.getStudioWorksCounts(studio: studioName)
            async let fetchInfo = repository.getStudioInfo(studio: studioName)
            async let fetchMovies = repository.getMovies(studio: studioName, pageSize: 50000)
            async let fetchEpisodes = repository.getEpisodes(studio: studioName, pageSize: 50000)
            async let fetchFav = userRepo.isFavorite(entityType: "studio", entityKey: studioName)

            let (counts, info, mList, epList, fav) = try await (fetchCounts, fetchInfo, fetchMovies, fetchEpisodes, await fetchFav)

            self.totalMoviesCount = counts.moviesCount
            self.totalEpisodesCount = counts.episodesCount
            self.logoUrl = info.logoUrl
            self.bannerUrl = info.bannerUrl
            self.movies = mList
            self.episodes = epList
            self.isFavorite = fav
        } catch {
            print("加载片商详情失败: \(error)")
        }
        isLoading = false
    }

    private func toggleFavorite() async {
        isFavorite.toggle()
        #if canImport(UIKit)
        let impact = UIImpactFeedbackGenerator(style: .medium)
        impact.impactOccurred()
        #endif
        try? await userRepo.toggleFavorite(entityType: "studio", entityKey: studioName, isFavorite: isFavorite)
    }
}
