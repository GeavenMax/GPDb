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
    @State private var isFavorite: Bool = false
    @State private var isLoading: Bool = true

    public init(studioName: String) {
        self.studioName = studioName
    }

    public var body: some View {
        VStack(spacing: 0) {
            // 分类标签栏
            Picker("分类", selection: $selectedTab) {
                Text("发行作品 (\(movies.count))").tag(StudioTab.movies)
                Text("发行分集 (\(episodes.count))").tag(StudioTab.episodes)
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
        .dynamicAmbientBackground(imagePath: movies.first?.coverFull ?? movies.first?.coverIcon ?? episodes.first?.thumbnailUrl)
        .navigationTitle(cleanTitle(studioName))
        .inlineNavigationTitle()
        .toolbar {
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
        ScrollView {
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

                            Text(movie.displayTitle)
                                .font(.caption.bold())
                                .lineLimit(1)

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
        ScrollView {
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
                                Text(ep.displayTitle)
                                    .font(.subheadline.bold())
                                    .lineLimit(2)
                                    .foregroundStyle(.primary)

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

    private func cleanTitle(_ t: String) -> String {
        return t.replacingOccurrences(of: "|||", with: " ")
    }

    private func loadStudioData() async {
        isLoading = true
        do {
            async let fetchMovies = repository.getMovies(studio: studioName, pageSize: 10000)
            async let fetchEpisodes = repository.getEpisodes(studio: studioName, pageSize: 10000)
            async let fetchFav = userRepo.isFavorite(entityType: "studio", entityKey: studioName)

            self.movies = try await fetchMovies
            self.episodes = try await fetchEpisodes
            self.isFavorite = (try? await fetchFav) ?? false
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
