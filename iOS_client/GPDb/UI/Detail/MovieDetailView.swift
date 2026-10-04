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
    @State private var failedCoverUrls: Set<String> = []

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
                    // 1. 顶部海报展台 (自适应物理画幅零黑边展台，点击呼出全屏灯箱)
                    postersGallery(movie: data.movie)

                    VStack(alignment: .leading, spacing: 14) {
                        // 2. 标题与核心元数据
                        titlesAndBadges(movie: data.movie, directors: data.directors)

                        // 3. 核心操作矩阵芯片组 (BT磁链、BFTV、Google搜索、分享海报、快捷收藏)
                        actionMatrixSection(movie: data.movie)

                        Divider()

                        // 4. 剧情简介 (中英双语自适应)
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
                let directorDisplay: String? = {
                    if !data.directors.isEmpty {
                        return data.directors.map { $0.name }.joined(separator: " / ")
                    }
                    return data.movie.fallbackDirectorNames.joined(separator: " / ")
                }()

                ShareCardModalView(
                    title: data.movie.displayTitle,
                    titleAlt: data.movie.displaySubtitle,
                    isEpisode: false,
                    year: data.movie.releaseYear,
                    duration: data.movie.formattedDuration,
                    studio: data.movie.studioName,
                    director: directorDisplay,
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
        let candidateCovers: [String] = [
            movie.coverFull ?? movie.coverIcon,
            movie.coverBack
        ].compactMap { $0 }.filter { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }

        let effectiveCovers = candidateCovers.filter { !failedCoverUrls.contains($0) }

        if effectiveCovers.count > 1 {
            // 双封面并排自适应画幅 (消除上下黑边与裁剪，等比贴合)
            HStack(spacing: 12) {
                Spacer(minLength: 0)
                ForEach(effectiveCovers, id: \.self) { coverUrl in
                    AdaptivePosterCard(
                        imagePath: coverUrl,
                        maxHeight: 270,
                        maxWidth: (UIScreen.main.bounds.width - 56) / 2,
                        cornerRadius: 14,
                        onError: {
                            withAnimation(.easeInOut(duration: 0.25)) {
                                _ = failedCoverUrls.insert(coverUrl)
                            }
                        },
                        onTap: {
                            lightboxImage = coverUrl
                        }
                    )
                }
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity)
            .padding(.horizontal)
            .padding(.top, 8)
        } else if let singleCover = effectiveCovers.first {
            // 单封面居中艺术展台 (自适应物理比例，最大高度限制，零黑边)
            HStack {
                Spacer()
                AdaptivePosterCard(
                    imagePath: singleCover,
                    maxHeight: 380,
                    maxWidth: UIScreen.main.bounds.width - 48,
                    cornerRadius: 16,
                    onError: {
                        withAnimation(.easeInOut(duration: 0.25)) {
                            _ = failedCoverUrls.insert(singleCover)
                        }
                    },
                    onTap: {
                        lightboxImage = singleCover
                    }
                )
                Spacer()
            }
            .frame(maxWidth: .infinity)
            .padding(.horizontal)
            .padding(.top, 8)
        } else {
            // 全封面缺失或损坏时的优雅占位展台
            HStack {
                Spacer()
                VStack(spacing: 12) {
                    Image(systemName: "film")
                        .font(.system(size: 38))
                        .foregroundStyle(.secondary.opacity(0.5))
                    Text("暂无海报")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                .frame(width: 200, height: 280)
                .background(Color.secondary.opacity(0.08), in: RoundedRectangle(cornerRadius: 16))
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(Color.primary.opacity(0.08), lineWidth: 1)
                )
                Spacer()
            }
            .padding(.horizontal)
            .padding(.top, 8)
        }
    }

    private func titlesAndBadges(movie: MovieRecord, directors: [DirectorRecord]) -> some View {
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

            // 自适应信息胶囊流式折行排布 (年份、时长、片商、导演)
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

                // 独立导演胶囊列表：优先使用关联表读取的真实多导演列表；若无则通过 fallbackDirectorNames 拆分 " / "
                let directorNames: [String] = {
                    if !directors.isEmpty {
                        return directors.map { $0.name }.filter { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
                    }
                    return movie.fallbackDirectorNames
                }()

                ForEach(directorNames, id: \.self) { dir in
                    NavigationLink(destination: FilteredMovieListView(title: dir, filterKey: "director", filterValue: dir)) {
                        BadgePill(text: "导演: \(dir)", icon: "person.crop.circle", isInteractive: true)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.top, 4)
        }
    }

    /// 核心操作矩阵芯片组 (对标 Android v2.17.0 独立操作芯片矩阵)
    private func actionMatrixSection(movie: MovieRecord) -> some View {
        let queryTitle = movie.title
        let encoded = queryTitle.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""

        return ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 10) {
                // 1. 快捷收藏
                Button {
                    Task { await toggleFavorite() }
                } label: {
                    ActionChip(
                        title: isFavorite ? "已收藏" : "收藏电影",
                        icon: isFavorite ? "heart.fill" : "heart",
                        color: isFavorite ? .red : .primary,
                        isFilled: isFavorite
                    )
                }
                .buttonStyle(.plain)

                // 2. 流光分享卡片
                Button {
                    showShareCardSheet = true
                } label: {
                    ActionChip(
                        title: "分享海报",
                        icon: "square.and.arrow.up",
                        color: .green
                    )
                }
                .buttonStyle(.plain)

                // 3. BT 磁链检索
                if !encoded.isEmpty, let btUrl = URL(string: "https://bt4gprx.com/search?q=\(encoded)") {
                    Link(destination: btUrl) {
                        ActionChip(
                            title: "BT 磁链",
                            icon: "arrow.down.circle",
                            color: .red
                        )
                    }
                    .buttonStyle(.plain)
                }

                // 4. BFTV 视频检索
                if !encoded.isEmpty, let bftvUrl = URL(string: "https://www.boyfriendtv.com/search/videos/\(encoded)/") {
                    Link(destination: bftvUrl) {
                        ActionChip(
                            title: "BFTV 检索",
                            icon: "play.tv",
                            color: Color(red: 0.88, green: 0.25, blue: 0.6)
                        )
                    }
                    .buttonStyle(.plain)
                }

                // 5. Google 深度搜索
                if !encoded.isEmpty, let googleUrl = URL(string: "https://www.google.com/search?q=\(encoded)") {
                    Link(destination: googleUrl) {
                        ActionChip(
                            title: "Google 搜索",
                            icon: "magnifyingglass",
                            color: .blue
                        )
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.vertical, 2)
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

private struct ActionChip: View {
    let title: String
    let icon: String
    let color: Color
    var isFilled: Bool = false

    var body: some View {
        HStack(spacing: 6) {
            Image(systemName: icon)
                .font(.system(size: 11, weight: .semibold))
            Text(title)
                .font(.caption2.bold())
        }
        .padding(.horizontal, 11)
        .padding(.vertical, 7)
        .background(
            isFilled ? color.opacity(0.18) : Color.secondary.opacity(0.12),
            in: Capsule()
        )
        .overlay(
            Capsule()
                .stroke(color.opacity(isFilled ? 0.45 : 0.2), lineWidth: 1)
        )
        .foregroundStyle(isFilled ? color : .primary)
    }
}

private extension Color {
    static var tintColor: Color { .accentColor }
}
