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
    @State private var nameZh: String? = nil
    @State private var descriptionZh: String? = nil
    @State private var logoUrl: String? = nil
    @State private var bannerUrl: String? = nil
    @State private var isFavorite: Bool = false
    @State private var isLoading: Bool = true

    public init(studioName: String) {
        self.studioName = studioName
    }

    public var body: some View {
        WaterfallScrollView {
            if isLoading {
                ProgressView()
                    .padding(.top, 60)
            } else {
                LazyVStack(spacing: 12, pinnedViews: [.sectionHeaders]) {
                    // 厂牌介绍卡片 (181 家核心厂牌深度中文历史与创办背景档案，高度自适应，随滚动平滑移动)
                    if let desc = descriptionZh, !desc.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                        studioIntroCard(description: desc)
                            .padding(.horizontal)
                            .padding(.top, 8)
                    }

                    // 分类标签栏 (吸顶 Section Header)
                    Section {
                        tabContent
                    } header: {
                        tabPickerHeader
                    }
                }
                .padding(.bottom, 24)
            }
        }
        .dynamicAmbientBackground(imagePath: logoUrl ?? movies.first?.coverFull ?? movies.first?.coverIcon ?? episodes.first?.thumbnailUrl)
        .navigationTitle(bilingualMarqueeTitle)
        .inlineNavigationTitle()
        .toolbar(environment.isTabBarHidden ? .hidden : .visible, for: .tabBar)
        .toolbar {
            ToolbarItem(placement: .principal) {
                MarqueeText(
                    text: bilingualMarqueeTitle,
                    font: .headline,
                    weight: .bold,
                    alignment: .center,
                    speed: 25
                )
                .frame(width: principalTitleWidth)
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
        .onAppear {
            environment.setTabBarHidden(false, animated: false)
        }
        .task {
            if movies.isEmpty && episodes.isEmpty {
                await loadStudioData()
            }
        }
    }

    private var tabPickerHeader: some View {
        VStack(spacing: 0) {
            Picker("分类", selection: $selectedTab) {
                Text("发行作品 (\(max(totalMoviesCount, movies.count)))").tag(StudioTab.movies)
                Text("发行分集 (\(max(totalEpisodesCount, episodes.count)))").tag(StudioTab.episodes)
            }
            .pickerStyle(.segmented)
            .padding(.horizontal)
            .padding(.vertical, 8)
        }
        .background(.ultraThinMaterial)
    }

    @ViewBuilder
    private var tabContent: some View {
        switch selectedTab {
        case .movies:
            if movies.isEmpty {
                ContentUnavailableView("暂无发行作品", systemImage: "film.stack", description: Text("该片商名下暂无长片电影记录。"))
                    .padding(.vertical, 40)
            } else {
                moviesGrid
            }
        case .episodes:
            if episodes.isEmpty {
                ContentUnavailableView("暂无发行分集", systemImage: "play.rectangle.on.rectangle", description: Text("该片商名下暂无分集场景片段记录。"))
                    .padding(.vertical, 40)
            } else {
                episodesList
            }
        }
    }

    private var moviesGrid: some View {
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
        .padding(.horizontal)
    }

    private var episodesList: some View {
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
        .padding(.vertical, 4)
    }

    @ViewBuilder
    private func studioIntroCard(description: String) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Label("厂牌介绍", systemImage: "books.vertical.fill")
                    .font(.caption.bold())
                    .foregroundStyle(.tint)

                Spacer()

                Text("中文历史档案")
                    .font(.system(size: 10, weight: .semibold))
                    .foregroundStyle(.secondary)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(Color.secondary.opacity(0.12), in: Capsule())
            }

            Text(description)
                .font(.subheadline)
                .foregroundStyle(.primary.opacity(0.9))
                .lineSpacing(5)
                .lineLimit(nil)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(14)
        .background(Color.secondary.opacity(0.08), in: RoundedRectangle(cornerRadius: 14))
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .stroke(Color.primary.opacity(0.06), lineWidth: 1)
        )
    }

    private var bilingualMarqueeTitle: String {
        let clean = cleanTitle(studioName)
        if let zh = nameZh, !zh.isEmpty, zh != clean {
            return "\(clean) · \(zh)"
        }
        return clean
    }

    private var principalTitleWidth: CGFloat {
        #if canImport(UIKit)
        let screenWidth = UIScreen.main.bounds.width
        // 预留两侧返回按钮 (~70pt) 与操作按钮 (~80pt) 空间，确保在 SE(375pt) 到 Pro Max(440pt) 均饱满且不与左右按钮挤压
        return max(min(screenWidth - 170, 260), 190)
        #else
        return 220
        #endif
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
            self.nameZh = info.nameZh
            self.descriptionZh = info.descriptionZh
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
