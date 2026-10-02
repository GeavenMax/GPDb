import SwiftUI
import GRDB

public enum LibraryCategory: String, CaseIterable, Identifiable, Sendable {
    case movies = "电影"
    case performers = "演员"
    case series = "系列"
    case studios = "片商"
    case directors = "导演"
    case episodes = "分集"

    public var id: String { rawValue }

    var entityType: String {
        switch self {
        case .movies: return "movie"
        case .performers: return "performer"
        case .series: return "series"
        case .studios: return "studio"
        case .directors: return "director"
        case .episodes: return "episode"
        }
    }
}

public struct LibraryView: View {
    @EnvironmentObject private var environment: AppEnvironment
    private let holder = DatabaseHolder.shared

    @State private var selectedCategory: LibraryCategory = .movies
    @State private var favoriteMovies: [MovieRecord] = []
    @State private var favoritePerformers: [PerformerRecord] = []
    @State private var favoriteSeries: [SeriesCollectionRecord] = []
    @State private var favoriteEpisodes: [EpisodeRecord] = []
    @State private var favoriteStudios: [String] = []
    @State private var favoriteDirectors: [String] = []
    @State private var isLoading: Bool = false
    @State private var hasInitialLoaded: Bool = false

    public init() {}

    public var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // 1. 收藏分类分段切换
                Picker("分类", selection: $selectedCategory) {
                    ForEach(LibraryCategory.allCases) { cat in
                        Text(cat.rawValue).tag(cat)
                    }
                }
                .pickerStyle(.segmented)
                .padding(.horizontal)
                .padding(.vertical, 8)

                // 2. 收藏内容列表
                if isLoading {
                    Spacer()
                    ProgressView()
                    Spacer()
                } else {
                    categoryContent
                }
            }
            .navigationTitle("我的影库")
            .toolbar(environment.isTabBarHidden ? .hidden : .visible, for: .tabBar)
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    HStack(spacing: 12) {
                        if selectedCategory == .movies {
                            ColumnSwitchButton()
                        }
                        NavigationLink(destination: SettingsView()) {
                            Image(systemName: "gearshape")
                        }
                    }
                }
            }
            .onChange(of: selectedCategory) { _, _ in
                Task { await loadFavorites() }
            }
            .task {
                if !hasInitialLoaded {
                    hasInitialLoaded = true
                    await loadFavorites()
                }
            }
        }
    }

    // MARK: - 子视图

    @ViewBuilder
    private var categoryContent: some View {
        switch selectedCategory {
        case .movies:
            if favoriteMovies.isEmpty { emptyPlaceholder } else { moviesGrid }
        case .performers:
            if favoritePerformers.isEmpty { emptyPlaceholder } else { performersGrid }
        case .series:
            if favoriteSeries.isEmpty { emptyPlaceholder } else { seriesList }
        case .studios:
            if favoriteStudios.isEmpty { emptyPlaceholder } else { stringList(items: favoriteStudios, filterKey: "studio") }
        case .directors:
            if favoriteDirectors.isEmpty { emptyPlaceholder } else { stringList(items: favoriteDirectors, filterKey: "director") }
        case .episodes:
            if favoriteEpisodes.isEmpty { emptyPlaceholder } else { episodesList }
        }
    }

    private var emptyPlaceholder: some View {
        ContentUnavailableView("暂无收藏记录", systemImage: "bookmark", description: Text("在浏览详情时轻触红心即可将其收入私有影库。"))
    }

    private var moviesGrid: some View {
        WaterfallScrollView {
            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 12), count: environment.layoutColumns), spacing: 14) {
                ForEach(favoriteMovies) { movie in
                    NavigationLink(destination: MovieDetailView(movieId: movie.id)) {
                        VStack(alignment: .leading, spacing: 6) {
                            GpdbImageView(rawPath: movie.coverFull ?? movie.coverIcon, contentMode: .fill, cornerRadius: 10)
                                .aspectRatio(0.68, contentMode: .fit)

                            Text(movie.displayTitle)
                                .font(.caption.bold())
                                .lineLimit(1)
                        }
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding()
        }
    }

    private var performersGrid: some View {
        WaterfallScrollView {
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 90), spacing: 14)], spacing: 16) {
                ForEach(favoritePerformers) { performer in
                    NavigationLink(destination: PerformerDetailView(performerId: performer.id)) {
                        VStack(spacing: 6) {
                            GpdbImageView(rawPath: performer.imageUrl, contentMode: .fill, cornerRadius: 36, placeholderIcon: "person.fill")
                                .frame(width: 72, height: 72)
                                .clipShape(Circle())

                            Text(performer.name)
                                .font(.caption2.bold())
                                .lineLimit(1)
                        }
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding()
        }
    }

    private var seriesList: some View {
        List {
            // 采用复合唯一键，彻底杜绝 ForEach 键重复崩溃
            ForEach(favoriteSeries, id: \.compositeKey) { series in
                NavigationLink(destination: FilteredMovieListView(title: series.displayTitle, filterKey: "series", filterValue: series.rootTitle)) {
                    HStack(spacing: 12) {
                        GpdbImageView(rawPath: series.coverUrl, contentMode: .fill, cornerRadius: 8, placeholderIcon: "film.stack")
                            .frame(width: 48, height: 64)

                        VStack(alignment: .leading, spacing: 4) {
                            Text(series.displayTitle)
                                .font(.subheadline.bold())
                            if let span = series.yearSpan {
                                Text(span)
                                    .font(.caption2)
                                    .foregroundStyle(.secondary)
                            }
                        }
                        Spacer()
                        Text("\(series.movieCount) 部")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
            }
        }
        .listStyle(.plain)
    }

    private func stringList(items: [String], filterKey: String) -> some View {
        List {
            ForEach(items, id: \.self) { item in
                if filterKey == "studio" {
                    NavigationLink(destination: StudioDetailView(studioName: item)) {
                        Text(item)
                            .font(.body.bold())
                            .padding(.vertical, 4)
                    }
                } else {
                    NavigationLink(destination: FilteredMovieListView(title: item, filterKey: filterKey, filterValue: item)) {
                        Text(item)
                            .font(.body.bold())
                            .padding(.vertical, 4)
                    }
                }
            }
        }
        .listStyle(.plain)
    }

    private var episodesList: some View {
        List {
            ForEach(favoriteEpisodes) { ep in
                NavigationLink(destination: EpisodeDetailView(episodeId: ep.id)) {
                    HStack(spacing: 12) {
                        GpdbImageView(rawPath: ep.thumbnailUrl, contentMode: .fill, cornerRadius: 8, placeholderIcon: "play.rectangle.fill")
                            .frame(width: 70, height: 44)

                        VStack(alignment: .leading, spacing: 2) {
                            Text(ep.displayTitle)
                                .font(.subheadline.bold())
                                .lineLimit(1)
                            if let d = ep.displayDescription {
                                Text(d)
                                    .font(.caption2)
                                    .foregroundStyle(.secondary)
                                    .lineLimit(1)
                            }
                        }
                    }
                }
            }
        }
        .listStyle(.plain)
    }

    private func loadFavorites() async {
        guard let db = holder.database else { return }
        isLoading = true
        defer { isLoading = false }
        do {
            let cat = selectedCategory
            switch cat {
            case .movies:
                let movies = try await db.read { db -> [MovieRecord] in
                    let keys = try String.fetchAll(db, sql: "SELECT entity_key FROM user_favorites WHERE entity_type = ? ORDER BY created_at DESC", arguments: [cat.entityType])
                    let ids = keys.compactMap { Int64($0) }
                    return ids.isEmpty ? [] : try MovieRecord.fetchAll(db, keys: ids)
                }
                self.favoriteMovies = movies
            case .performers:
                let performers = try await db.read { db -> [PerformerRecord] in
                    let keys = try String.fetchAll(db, sql: "SELECT entity_key FROM user_favorites WHERE entity_type = ? ORDER BY created_at DESC", arguments: [cat.entityType])
                    let ids = keys.compactMap { Int64($0) }
                    return ids.isEmpty ? [] : try PerformerRecord.fetchAll(db, keys: ids)
                }
                self.favoritePerformers = performers
            case .series:
                let series = try await db.read { db -> [SeriesCollectionRecord] in
                    let keys = try String.fetchAll(db, sql: "SELECT entity_key FROM user_favorites WHERE entity_type = ? ORDER BY created_at DESC", arguments: [cat.entityType])
                    if keys.isEmpty { return [] }
                    let placeholders = keys.map { _ in "?" }.joined(separator: ",")
                    return try SeriesCollectionRecord.fetchAll(db, sql: "SELECT DISTINCT s.* FROM series_collections s WHERE s.root_title IN (\(placeholders))", arguments: StatementArguments(keys))
                }
                self.favoriteSeries = series
            case .studios:
                let studios = try await db.read { db -> [String] in
                    try String.fetchAll(db, sql: "SELECT entity_key FROM user_favorites WHERE entity_type = ? ORDER BY created_at DESC", arguments: [cat.entityType])
                }
                self.favoriteStudios = studios
            case .directors:
                let directors = try await db.read { db -> [String] in
                    try String.fetchAll(db, sql: "SELECT entity_key FROM user_favorites WHERE entity_type = ? ORDER BY created_at DESC", arguments: [cat.entityType])
                }
                self.favoriteDirectors = directors
            case .episodes:
                let episodes = try await db.read { db -> [EpisodeRecord] in
                    let keys = try String.fetchAll(db, sql: "SELECT entity_key FROM user_favorites WHERE entity_type = ? ORDER BY created_at DESC", arguments: [cat.entityType])
                    let ids = keys.compactMap { Int64($0) }
                    return ids.isEmpty ? [] : try EpisodeRecord.fetchAll(db, keys: ids)
                }
                self.favoriteEpisodes = episodes
            }
        } catch {
            print("加载收藏失败: \(error)")
        }
    }
}
