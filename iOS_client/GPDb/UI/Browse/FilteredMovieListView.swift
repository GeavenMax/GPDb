import SwiftUI

/// 特定条件（片商、导演、系列等）下的电影过滤列表页
public struct FilteredMovieListView: View {
    @EnvironmentObject private var environment: AppEnvironment
    private let repository = BrowseRepository()

    public let title: String
    public let filterKey: String // "studio", "director", "series"
    public let filterValue: String

    @State private var movies: [MovieRecord] = []
    @State private var isLoading: Bool = true

    public init(title: String, filterKey: String, filterValue: String) {
        self.title = title
        self.filterKey = filterKey
        self.filterValue = filterValue
    }

    public var body: some View {
        ScrollView {
            if isLoading {
                ProgressView()
                    .padding(.top, 40)
            } else if movies.isEmpty {
                ContentUnavailableView("暂无相关影视作品", systemImage: "film.stack", description: Text("该分类或系列下暂无匹配记录。"))
                    .padding(.top, 40)
            } else {
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
        .dynamicAmbientBackground(imagePath: movies.first?.coverFull ?? movies.first?.coverIcon)
        .navigationTitle(cleanTitle(title))
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                ColumnSwitchButton()
            }
        }
        .task {
            if movies.isEmpty {
                await loadMovies()
            }
        }
    }

    private func cleanTitle(_ t: String) -> String {
        return t.replacingOccurrences(of: "|||", with: " ")
    }

    private func loadMovies() async {
        isLoading = true
        do {
            if filterKey == "studio" {
                self.movies = try await repository.getMovies(studio: filterValue, pageSize: 1000)
            } else if filterKey == "director" {
                self.movies = try await repository.getMovies(director: filterValue, pageSize: 1000)
            } else if filterKey == "series" {
                // 系列标题可能包含 |||，取纯净标题模糊查询
                let clean = filterValue.replacingOccurrences(of: "|||", with: " ")
                self.movies = try await repository.getMovies(query: clean, pageSize: 1000)
            }
        } catch {
            print("加载过滤影片失败: \(error)")
        }
        isLoading = false
    }
}
