import SwiftUI

public enum BrowseCategory: String, CaseIterable, Identifiable {
    case movies = "影片"
    case episodes = "分集"
    case performers = "演员"
    case series = "系列"
    case studios = "片商"
    case directors = "导演"

    public var id: String { rawValue }
}

public struct BrowseView: View {
    @EnvironmentObject private var environment: AppEnvironment
    private let repository = BrowseRepository()

    @State private var selectedCategory: BrowseCategory = .movies
    @State private var searchText: String = ""

    // 影片状态与分页
    @State private var movies: [MovieRecord] = []
    @State private var moviesPage: Int = 1
    @State private var hasMoreMovies: Bool = true
    @State private var isLoadingMoreMovies: Bool = false

    // 分集状态与分页
    @State private var episodes: [EpisodeRecord] = []
    @State private var episodesPage: Int = 1
    @State private var hasMoreEpisodes: Bool = true
    @State private var isLoadingMoreEpisodes: Bool = false

    // 演员状态与分页
    @State private var performers: [PerformerRecord] = []
    @State private var performersPage: Int = 1
    @State private var hasMorePerformers: Bool = true
    @State private var isLoadingMorePerformers: Bool = false

    // 系列、片商、导演
    @State private var seriesList: [SeriesCollectionRecord] = []
    @State private var studiosList: [StudioItem] = []
    @State private var directorsList: [DirectorItem] = []

    @State private var isLoading: Bool = false
    @State private var hasInitialLoaded: Bool = false
    @State private var showFilterSheet: Bool = false

    // 筛选状态
    @State private var selectedStudio: String? = nil
    @State private var selectedYear: Int? = nil
    @State private var selectedCategoryTag: String? = nil
    @State private var selectedSortBy: String = "id_desc"

    public init() {}

    public var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // 顶部频道分段选择器
                Picker("类别", selection: $selectedCategory) {
                    ForEach(BrowseCategory.allCases) { cat in
                        Text(cat.rawValue).tag(cat)
                    }
                }
                .pickerStyle(.segmented)
                .padding(.horizontal)
                .padding(.vertical, 8)

                if isLoading {
                    Spacer()
                    ProgressView()
                    Spacer()
                } else {
                    contentForSelectedCategory
                }
            }
            .navigationTitle("影视库")
            .searchable(text: $searchText, prompt: "搜索\(selectedCategory.rawValue)...")
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    HStack(spacing: 12) {
                        if selectedCategory == .movies {
                            ColumnSwitchButton()

                            Button {
                                showFilterSheet = true
                            } label: {
                                Image(systemName: "line.3.horizontal.decrease.circle")
                            }
                        }
                    }
                }
            }
            .sheet(isPresented: $showFilterSheet) {
                FilterSheetView(
                    selectedStudio: $selectedStudio,
                    selectedYear: $selectedYear,
                    selectedCategoryTag: $selectedCategoryTag,
                    selectedSortBy: $selectedSortBy,
                    onApply: {
                        Task { await performSearch() }
                    }
                )
                .presentationDetents([.medium, .large])
            }
            .onChange(of: selectedCategory) { _, _ in
                Task { await performSearch() }
            }
            .onChange(of: searchText) { _, _ in
                Task { await performSearch() }
            }
            .task {
                if !hasInitialLoaded {
                    hasInitialLoaded = true
                    await performSearch()
                }
            }
        }
    }

    // MARK: - 分类内容视图

    @ViewBuilder
    private var contentForSelectedCategory: some View {
        switch selectedCategory {
        case .movies:
            movieGrid
        case .episodes:
            episodesListSection
        case .performers:
            performerGrid
        case .series:
            seriesListSection
        case .studios:
            studiosListSection
        case .directors:
            directorsListSection
        }
    }

    private var movieGrid: some View {
        ScrollView {
            LazyVGrid(
                columns: Array(repeating: GridItem(.flexible(), spacing: 12), count: environment.layoutColumns),
                spacing: 14
            ) {
                ForEach(Array(movies.enumerated()), id: \.element.id) { index, movie in
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
                                if let studio = movie.studioName {
                                    Text(studio)
                                        .font(.caption2)
                                        .foregroundStyle(.secondary)
                                        .lineLimit(1)
                                }
                            }
                        }
                    }
                    .buttonStyle(.plain)
                    .onAppear {
                        if index >= movies.count - 6 {
                            Task { await loadMoreMovies() }
                        }
                    }
                }
            }
            .padding()

            if isLoadingMoreMovies {
                ProgressView()
                    .padding(.vertical, 12)
            }
        }
    }

    private var episodesListSection: some View {
        ScrollView {
            LazyVStack(spacing: 14) {
                ForEach(Array(episodes.enumerated()), id: \.element.id) { index, ep in
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

                                HStack {
                                    if let date = ep.releaseDate {
                                        Text("发行: \(date)")
                                            .font(.caption2)
                                            .foregroundStyle(.secondary)
                                    }
                                    Spacer()
                                    if let studio = ep.studioName {
                                        Text(studio)
                                            .font(.caption2)
                                            .foregroundStyle(.secondary)
                                            .lineLimit(1)
                                    }
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
                    .onAppear {
                        if index >= episodes.count - 6 {
                            Task { await loadMoreEpisodes() }
                        }
                    }
                }

                if isLoadingMoreEpisodes {
                    ProgressView()
                        .padding(.vertical, 12)
                }
            }
            .padding(.vertical, 8)
        }
    }

    private var performerGrid: some View {
        ScrollView {
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 100), spacing: 14)], spacing: 16) {
                ForEach(Array(performers.enumerated()), id: \.element.id) { index, performer in
                    NavigationLink(destination: PerformerDetailView(performerId: performer.id)) {
                        VStack(spacing: 6) {
                            GpdbImageView(rawPath: performer.imageUrl, contentMode: .fill, cornerRadius: 40, placeholderIcon: "person.fill")
                                .frame(width: 80, height: 80)
                                .clipShape(Circle())
                                .overlay(Circle().stroke(Color.secondary.opacity(0.2), lineWidth: 1))

                            Text(performer.name)
                                .font(.caption2.bold())
                                .lineLimit(1)
                        }
                    }
                    .buttonStyle(.plain)
                    .onAppear {
                        if index >= performers.count - 6 {
                            Task { await loadMorePerformers() }
                        }
                    }
                }
            }
            .padding()

            if isLoadingMorePerformers {
                ProgressView()
                    .padding(.vertical, 12)
            }
        }
    }

    private var seriesListSection: some View {
        List {
            ForEach(seriesList, id: \.compositeKey) { series in
                NavigationLink(destination: FilteredMovieListView(title: series.displayTitle, filterKey: "series", filterValue: series.rootTitle)) {
                    HStack(spacing: 12) {
                        GpdbImageView(rawPath: series.coverUrl, contentMode: .fill, cornerRadius: 8, placeholderIcon: "film.stack")
                            .frame(width: 50, height: 68)

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
                            .font(.caption.bold())
                            .foregroundStyle(.secondary)
                    }
                }
            }
        }
        .listStyle(.plain)
    }

    private var studiosListSection: some View {
        List {
            ForEach(studiosList) { studio in
                NavigationLink(destination: StudioDetailView(studioName: studio.name)) {
                    HStack {
                        Image(systemName: "building.2.crop.circle")
                            .font(.title3)
                            .foregroundStyle(.tint)
                        Text(studio.name)
                            .font(.body.bold())
                        Spacer()
                        Text("\(studio.count) 部作品")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    .padding(.vertical, 4)
                }
            }
        }
        .listStyle(.plain)
    }

    private var directorsListSection: some View {
        List {
            ForEach(directorsList) { item in
                NavigationLink(destination: FilteredMovieListView(title: item.director.name, filterKey: "director", filterValue: item.director.name)) {
                    HStack {
                        Image(systemName: "person.crop.circle.badge.checkmark")
                            .font(.title3)
                            .foregroundStyle(.tint)
                        Text(item.director.name)
                            .font(.body.bold())
                        Spacer()
                        Text("\(item.movieCount) 部作品")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    .padding(.vertical, 4)
                }
            }
        }
        .listStyle(.plain)
    }

    // MARK: - 数据加载与分页

    private func performSearch() async {
        isLoading = true
        moviesPage = 1
        episodesPage = 1
        performersPage = 1

        do {
            switch selectedCategory {
            case .movies:
                let list = try await repository.getMovies(
                    query: searchText,
                    studio: selectedStudio,
                    category: selectedCategoryTag,
                    year: selectedYear,
                    sortBy: selectedSortBy,
                    page: 1,
                    pageSize: 40
                )
                self.movies = list
                self.hasMoreMovies = list.count >= 40

            case .episodes:
                let list = try await repository.getEpisodes(
                    query: searchText,
                    page: 1,
                    pageSize: 40
                )
                self.episodes = list
                self.hasMoreEpisodes = list.count >= 40

            case .performers:
                let list = try await repository.getPerformers(
                    query: searchText,
                    page: 1,
                    pageSize: 40
                )
                self.performers = list
                self.hasMorePerformers = list.count >= 40

            case .series:
                self.seriesList = try await repository.getSeries(query: searchText)

            case .studios:
                self.studiosList = try await repository.getStudios()

            case .directors:
                self.directorsList = try await repository.getDirectors(query: searchText)
            }
        } catch {
            print("搜索失败: \(error)")
        }
        isLoading = false
    }

    private func loadMoreMovies() async {
        guard hasMoreMovies && !isLoadingMoreMovies else { return }
        isLoadingMoreMovies = true
        do {
            let next = try await repository.getMovies(
                query: searchText,
                studio: selectedStudio,
                category: selectedCategoryTag,
                year: selectedYear,
                sortBy: selectedSortBy,
                page: moviesPage + 1,
                pageSize: 40
            )
            if next.isEmpty {
                hasMoreMovies = false
            } else {
                moviesPage += 1
                movies.append(contentsOf: next)
                hasMoreMovies = next.count >= 40
            }
        } catch {
            print("加载更多电影失败: \(error)")
        }
        isLoadingMoreMovies = false
    }

    private func loadMoreEpisodes() async {
        guard hasMoreEpisodes && !isLoadingMoreEpisodes else { return }
        isLoadingMoreEpisodes = true
        do {
            let next = try await repository.getEpisodes(
                query: searchText,
                page: episodesPage + 1,
                pageSize: 40
            )
            if next.isEmpty {
                hasMoreEpisodes = false
            } else {
                episodesPage += 1
                episodes.append(contentsOf: next)
                hasMoreEpisodes = next.count >= 40
            }
        } catch {
            print("加载更多分集失败: \(error)")
        }
        isLoadingMoreEpisodes = false
    }

    private func loadMorePerformers() async {
        guard hasMorePerformers && !isLoadingMorePerformers else { return }
        isLoadingMorePerformers = true
        do {
            let next = try await repository.getPerformers(
                query: searchText,
                page: performersPage + 1,
                pageSize: 40
            )
            if next.isEmpty {
                hasMorePerformers = false
            } else {
                performersPage += 1
                performers.append(contentsOf: next)
                hasMorePerformers = next.count >= 40
            }
        } catch {
            print("加载更多演员失败: \(error)")
        }
        isLoadingMorePerformers = false
    }
}
