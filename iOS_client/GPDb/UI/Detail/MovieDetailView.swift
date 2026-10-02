import SwiftUI
#if canImport(UIKit)
import UIKit
#endif

public struct MovieDetailView: View {
    public let movieId: Int64

    private let movieRepo = MovieRepository()
    private let userRepo = UserRepository()
    private let analyticsRepo = UserAnalyticsRepository.shared

    @State private var detailData: MovieDetailData? = nil
    @State private var isFavorite: Bool = false
    @State private var isLoading: Bool = true
    @State private var showShareCardSheet: Bool = false
    @State private var lightboxImage: String? = nil

    public init(movieId: Int64) {
        self.movieId = movieId
    }

    public var body: some View {
        ScrollView {
            if isLoading {
                ProgressView()
                    .padding(.top, 60)
            } else if let data = detailData {
                VStack(alignment: .leading, spacing: 20) {
                    // 1. 顶部海报展台 (正反双封面或单面，点击呼出全屏灯箱)
                    postersGallery(movie: data.movie)

                    VStack(alignment: .leading, spacing: 14) {
                        // 2. 标题与核心元数据
                        titlesAndBadges(movie: data.movie)

                        Divider()

                        // 3. 剧情简介 (中英双语自适应)
                        synopsisSection(movie: data.movie)

                        // 5. 参演阵容
                        if !data.performers.isEmpty {
                            Divider()
                            performersSection(performers: data.performers)
                        }

                        // 6. 场景片段
                        if !data.episodes.isEmpty {
                            Divider()
                            episodesSection(episodes: data.episodes)
                        }
                    }
                    .padding(.horizontal)
                }
                .padding(.bottom, 40)
            }
        }
        .dynamicAmbientBackground(imagePath: detailData?.movie.coverFull ?? detailData?.movie.coverIcon)
        .inlineNavigationTitle()
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                HStack(spacing: 12) {
                    Button {
                        showShareCardSheet = true
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
        .fullScreenOrSheet(item: Binding(
            get: { lightboxImage != nil ? LightboxItem(path: lightboxImage!) : nil },
            set: { lightboxImage = $0?.path }
        )) { item in
            ZoomableImageViewer(imagePath: item.path) {
                lightboxImage = nil
            }
        }
        .sheet(isPresented: $showShareCardSheet) {
            if let data = detailData {
                ShareCardModalView(
                    title: data.movie.displayTitle,
                    titleAlt: data.movie.displaySubtitle,
                    isEpisode: false,
                    year: data.movie.releaseYear,
                    duration: data.movie.formattedDuration,
                    studio: data.movie.studioName,
                    director: data.movie.directorName,
                    performers: data.performers.map { $0.name },
                    synopsis: data.movie.displayDescription,
                    posterPath: data.movie.coverFull ?? data.movie.coverIcon,
                    backCoverPath: data.movie.coverBack
                )
            }
        }
        .task {
            if detailData == nil {
                await loadDetail()
                analyticsRepo.recordMovieView(id: movieId)
            }
        }
    }

    // MARK: - 子模块

    @ViewBuilder
    private func postersGallery(movie: MovieRecord) -> some View {
        if let back = movie.coverBack, !back.isEmpty {
            // 双封面排布 (等宽对半分隔约束，防止撑破屏幕)
            HStack(spacing: 12) {
                GpdbImageView(rawPath: movie.coverFull ?? movie.coverIcon, contentMode: .fill, cornerRadius: 14)
                    .frame(maxWidth: .infinity)
                    .frame(height: 250)
                    .clipped()
                    .clipShape(RoundedRectangle(cornerRadius: 14))
                    .overlay(
                        RoundedRectangle(cornerRadius: 14)
                            .stroke(Color.primary.opacity(0.08), lineWidth: 1)
                    )
                    .onTapGesture { lightboxImage = movie.coverFull ?? movie.coverIcon }

                GpdbImageView(rawPath: back, contentMode: .fill, cornerRadius: 14)
                    .frame(maxWidth: .infinity)
                    .frame(height: 250)
                    .clipped()
                    .clipShape(RoundedRectangle(cornerRadius: 14))
                    .overlay(
                        RoundedRectangle(cornerRadius: 14)
                            .stroke(Color.primary.opacity(0.08), lineWidth: 1)
                    )
                    .onTapGesture { lightboxImage = back }
            }
            .frame(maxWidth: .infinity)
            .padding(.horizontal)
            .padding(.top, 8)
        } else {
            // 单封面居中 (严格约束屏幕内)
            ZStack(alignment: .bottomTrailing) {
                GpdbImageView(rawPath: movie.coverFull ?? movie.coverIcon, contentMode: .fill, cornerRadius: 16)
                    .frame(maxWidth: .infinity)
                    .frame(height: 380)
                    .clipped()
                    .clipShape(RoundedRectangle(cornerRadius: 16))
                    .overlay(
                        RoundedRectangle(cornerRadius: 16)
                            .stroke(Color.primary.opacity(0.08), lineWidth: 1)
                    )
                    .onTapGesture { lightboxImage = movie.coverFull ?? movie.coverIcon }

                Label("轻触查看大图", systemImage: "arrow.up.left.and.arrow.down.right")
                    .font(.caption2.bold())
                    .foregroundStyle(.white)
                    .padding(6)
                    .background(.black.opacity(0.6), in: RoundedRectangle(cornerRadius: 8))
                    .padding(12)
            }
            .frame(maxWidth: .infinity)
            .padding(.horizontal)
            .padding(.top, 8)
        }
    }

    private func titlesAndBadges(movie: MovieRecord) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            // 长标题循环平滑滚动跑马灯 (超长标题不截断)
            MarqueeText(
                text: movie.displayTitle,
                font: .title2,
                weight: .bold
            )

            if let sub = movie.displaySubtitle {
                MarqueeText(
                    text: sub,
                    font: .subheadline,
                    weight: .regular,
                    foregroundColor: .secondary,
                    speed: 28
                )
            }

            // 自适应信息胶囊流式折行排布 (年份、时长、片商、导演、BT4G 等)
            FlowLayout(horizontalSpacing: 8, verticalSpacing: 8) {
                if let y = movie.releaseYear {
                    BadgePill(text: "\(y) 年", icon: "calendar")
                }
                if let dur = movie.formattedDuration {
                    BadgePill(text: dur, icon: "clock")
                }
                if let studio = movie.studioName {
                    NavigationLink(destination: StudioDetailView(studioName: studio)) {
                        BadgePill(text: studio, icon: "building.2", isInteractive: true)
                    }
                    .buttonStyle(.plain)
                }
                if let dir = movie.directorName {
                    NavigationLink(destination: FilteredMovieListView(title: dir, filterKey: "director", filterValue: dir)) {
                        BadgePill(text: dir, icon: "person.crop.circle", isInteractive: true)
                    }
                    .buttonStyle(.plain)
                }

                if let encoded = movie.title.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
                   let url = URL(string: "https://bt4gprx.com/search?q=\(encoded)") {
                    Link(destination: url) {
                        HStack(spacing: 4) {
                            Image(systemName: "arrow.up.right.square")
                                .font(.system(size: 10))
                            Text("BT4G")
                                .font(.caption2.bold())
                        }
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(Color.red.opacity(0.18), in: Capsule())
                        .foregroundStyle(.red)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.top, 4)
        }
    }


    private func synopsisSection(movie: MovieRecord) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Label("剧情简介", systemImage: "text.quote")
                    .font(.headline.bold())
                Spacer()
                if movie.descriptionZh != nil {
                    Text("中文译文")
                        .font(.caption2.bold())
                        .foregroundStyle(.tint)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(Color.tintColor.opacity(0.12), in: Capsule())
                }
            }

            // 说明文字增加宽边距卡片包裹与行距呼吸感
            VStack(alignment: .leading, spacing: 8) {
                if let desc = movie.displayDescription {
                    Text(desc)
                        .font(.subheadline)
                        .lineSpacing(6)
                        .foregroundStyle(.primary.opacity(0.9))
                } else {
                    Text("暂无剧情简介记录。")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
            .background(Color.secondary.opacity(0.08), in: RoundedRectangle(cornerRadius: 12))
        }
    }

    private func performersSection(performers: [PerformerRecord]) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("主演阵容 (\(performers.count))")
                .font(.headline.bold())

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 14) {
                    ForEach(performers) { p in
                        NavigationLink(destination: PerformerDetailView(performerId: p.id)) {
                            VStack(spacing: 4) {
                                GpdbImageView(rawPath: p.imageUrl, contentMode: .fill, cornerRadius: 28, placeholderIcon: "person.fill")
                                    .frame(width: 56, height: 56)
                                    .clipShape(Circle())

                                MarqueeText(text: p.name, font: .caption2, weight: .bold, speed: 20)
                                    .frame(width: 64)
                            }
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }

    private func episodesSection(episodes: [EpisodeRecord]) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("场景分集 (\(episodes.count))")
                .font(.headline.bold())

            ForEach(episodes) { ep in
                NavigationLink(destination: EpisodeDetailView(episodeId: ep.id)) {
                    HStack(spacing: 12) {
                        GpdbImageView(rawPath: ep.thumbnailUrl, contentMode: .fill, cornerRadius: 8, placeholderIcon: "play.rectangle.fill")
                            .frame(width: 80, height: 50)

                        VStack(alignment: .leading, spacing: 4) {
                            MarqueeText(text: ep.displayTitle, font: .subheadline, weight: .bold, speed: 25)

                            if let desc = ep.displayDescription {
                                Text(desc)
                                    .font(.caption2)
                                    .foregroundStyle(.secondary)
                                    .lineLimit(1)
                            }
                        }
                        Spacer()
                    }
                    .padding(8)
                    .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 10))
                }
                .buttonStyle(.plain)
            }
        }
    }

    private func loadDetail() async {
        isLoading = true
        do {
            if let data = try await movieRepo.getMovieDetail(id: movieId) {
                self.detailData = data
                self.isFavorite = data.isFavorite
            }
        } catch {
            print("加载电影详情失败: \(error)")
        }
        isLoading = false
    }

    private func toggleFavorite() async {
        isFavorite.toggle()
        #if canImport(UIKit)
        let impact = UIImpactFeedbackGenerator(style: .medium)
        impact.impactOccurred()
        #endif
        try? await userRepo.toggleFavorite(entityType: "movie", entityKey: String(movieId), isFavorite: isFavorite)
    }
}

private struct LightboxItem: Identifiable {
    var id: String { path }
    let path: String
}

private struct BadgePill: View {
    let text: String
    let icon: String
    var isInteractive: Bool = false

    var body: some View {
        HStack(spacing: 4) {
            Image(systemName: icon)
                .font(.system(size: 10))
            Text(text)
                .font(.caption2.bold())
            if isInteractive {
                Image(systemName: "chevron.right")
                    .font(.system(size: 8))
            }
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .background(Color.secondary.opacity(0.12), in: Capsule())
        .foregroundStyle(isInteractive ? Color.accentColor : Color.primary)
    }
}

private extension Color {
    static var tintColor: Color { .accentColor }
}
