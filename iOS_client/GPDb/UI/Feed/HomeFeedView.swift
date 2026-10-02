import SwiftUI
#if canImport(UIKit)
import UIKit
#endif

public struct HomeFeedView: View {
    @EnvironmentObject private var environment: AppEnvironment
    private let repository = HomeFeedRepository()

    @State private var feedData = HomeFeedData()
    @State private var isLoading: Bool = true
    @State private var spotlightIndex: Int = 0
    @State private var luckyMovies: [MovieRecord] = []
    @State private var diceAngle: Double = 0.0
    @State private var isRefreshingLucky: Bool = false

    public init() {}

    public var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 24) {
                    if isLoading {
                        homeFeedLoadingSkeleton
                    } else {
                        // 1. 焦点海报轮播 (Spotlight Banner)
                        if !feedData.spotlightMovies.isEmpty {
                            spotlightCarousel
                        }

                        // 2. 今日星光 · 标志面孔 (动态过滤无头像演员)
                        if !feedData.todayStars.isEmpty {
                            todayStarsSection
                        }

                        // 3. 经典系列大放送 · 连贯篇章 (随机抽取 10 组)
                        if !feedData.classicSeries.isEmpty {
                            classicSeriesSection
                        }

                        // 4. 随心探索 · 盲盒发现 (Lucky Discovery: 6部随机影片网格)
                        if !luckyMovies.isEmpty {
                            blindBoxSection
                        }

                        // 5. 离线全库总览看板
                        libraryOverviewStats
                    }
                }
                .padding(.top, 4)
                .padding(.bottom, 24)
            }
            .dynamicAmbientBackground(
                imagePath: feedData.spotlightMovies.indices.contains(spotlightIndex) ?
                    (feedData.spotlightMovies[spotlightIndex].coverFull ?? feedData.spotlightMovies[spotlightIndex].coverIcon) : nil,
                gradientHeight: 520
            )
            .navigationTitle("主页")
            .inlineNavigationTitle()
            .toolbar {
                ToolbarItem(placement: .navigation) {
                    Button {
                        environment.togglePrivacyMode()
                    } label: {
                        Image(systemName: environment.isPrivacyModeActive ? "eye.slash.fill" : "eye.fill")
                            .foregroundStyle(environment.isPrivacyModeActive ? .red : .primary)
                    }
                }
                ToolbarItem(placement: .primaryAction) {
                    Button {
                        Task { await loadFeed() }
                    } label: {
                        Image(systemName: "arrow.clockwise")
                    }
                }
            }
            .task {
                if feedData.spotlightMovies.isEmpty {
                    await loadFeed()
                }
            }
        }
    }

    // MARK: - 子视图组件

    private var spotlightCarousel: some View {
        VStack(spacing: 8) {
            TabView(selection: $spotlightIndex) {
                ForEach(0..<feedData.spotlightMovies.count, id: \.self) { idx in
                    let movie = feedData.spotlightMovies[idx]
                    NavigationLink(destination: MovieDetailView(movieId: movie.id)) {
                        ZStack {
                            // 1. 动态氛围背景虚化 (Ambient Glow: 严格限制在容器边界内)
                            GpdbImageView(rawPath: movie.coverFull ?? movie.coverIcon, contentMode: .fill, cornerRadius: 20)
                                .frame(maxWidth: .infinity, maxHeight: .infinity)
                                .blur(radius: 35)
                                .opacity(0.45)
                                .clipped()

                            // 渐变暗黑蒙层
                            LinearGradient(
                                colors: [Color.black.opacity(0.55), Color.black.opacity(0.88)],
                                startPoint: .top,
                                endPoint: .bottom
                            )
                            .clipShape(RoundedRectangle(cornerRadius: 20))

                            // 2. 左右分栏核心内容：左侧完整海报，右侧排版信息
                            HStack(alignment: .center, spacing: 14) {
                                // 左侧：完整竖版海报，0.68 黄金比例，完全不被上下裁切
                                GpdbImageView(rawPath: movie.coverFull ?? movie.coverIcon, contentMode: .fill, cornerRadius: 12)
                                    .frame(width: 115, height: 170)
                                    .clipShape(RoundedRectangle(cornerRadius: 12))
                                    .shadow(color: .black.opacity(0.5), radius: 8, x: 0, y: 4)

                                // 右侧：影片信息结构排布 (自适应填充剩余宽度)
                                VStack(alignment: .leading, spacing: 6) {
                                    // 顶部徽章行
                                    HStack(spacing: 6) {
                                        Text("焦点推荐")
                                            .font(.system(size: 10, weight: .bold))
                                            .foregroundStyle(.black)
                                            .padding(.horizontal, 6)
                                            .padding(.vertical, 2)
                                            .background(Color.amber, in: Capsule())

                                        if let year = movie.releaseYear {
                                            Text("\(year)年")
                                                .font(.caption.bold())
                                                .foregroundStyle(.white.opacity(0.85))
                                        }

                                        if let rating = movie.rating, !rating.isEmpty {
                                            HStack(spacing: 2) {
                                                Image(systemName: "star.fill")
                                                    .font(.system(size: 9))
                                                    .foregroundStyle(Color.amber)
                                                Text(rating)
                                                    .font(.caption2.bold())
                                                    .foregroundStyle(Color.amber)
                                            }
                                        }
                                    }

                                    // 标题 (超长平滑跑马灯支持)
                                    MarqueeText(
                                        text: movie.displayTitle,
                                        font: .headline,
                                        weight: .bold,
                                        foregroundColor: .white,
                                        speed: 28
                                    )

                                    // 片商厂牌
                                    if let studio = movie.studioName, !studio.isEmpty {
                                        Text(studio)
                                            .font(.caption2)
                                            .foregroundStyle(.white.opacity(0.7))
                                            .lineLimit(1)
                                    }

                                    // 剧情简介 (中文优先)
                                    if let desc = movie.displayDescription, !desc.isEmpty {
                                        Text(desc)
                                            .font(.caption2)
                                            .lineSpacing(2)
                                            .foregroundStyle(.white.opacity(0.8))
                                            .lineLimit(2)
                                            .multilineTextAlignment(.leading)
                                    }

                                    Spacer(minLength: 4)

                                    // 底部立即探索按钮
                                    HStack {
                                        HStack(spacing: 4) {
                                            Text("立即探索")
                                                .font(.caption.bold())
                                            Image(systemName: "arrow.right.circle.fill")
                                                .font(.caption)
                                        }
                                        .foregroundStyle(.white)
                                        .padding(.horizontal, 10)
                                        .padding(.vertical, 5)
                                        .background(Color.white.opacity(0.2), in: Capsule())

                                        Spacer()
                                    }
                                }
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding(.vertical, 12)
                            }
                            .padding(.horizontal, 14)
                        }
                        .frame(maxWidth: .infinity)
                        .frame(height: 198)
                        .clipShape(RoundedRectangle(cornerRadius: 20))
                        .overlay(RoundedRectangle(cornerRadius: 20).stroke(Color.white.opacity(0.12), lineWidth: 1))
                    }
                    .buttonStyle(.plain)
                    .tag(idx)
                    .padding(.horizontal)
                }
            }
            .frame(height: 198)
            .tabViewStyle(.page(indexDisplayMode: .never))

            // 外部独立指示器条，不遮挡卡片底部与立即探索按钮
            if feedData.spotlightMovies.count > 1 {
                HStack(spacing: 6) {
                    ForEach(0..<feedData.spotlightMovies.count, id: \.self) { idx in
                        Capsule()
                            .fill(spotlightIndex == idx ? Color.amber : Color.secondary.opacity(0.3))
                            .frame(width: spotlightIndex == idx ? 16 : 5, height: 5)
                            .animation(.easeInOut(duration: 0.25), value: spotlightIndex)
                    }
                }
                .padding(.top, 2)
            }
        }
    }

    private var todayStarsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Label("今日星光 · 标志面孔", systemImage: "sparkles")
                    .font(.headline.bold())
                Spacer()
            }
            .padding(.horizontal)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 16) {
                    ForEach(feedData.todayStars) { performer in
                        NavigationLink(destination: PerformerDetailView(performerId: performer.id)) {
                            VStack(spacing: 6) {
                                GpdbImageView(rawPath: performer.imageUrl, contentMode: .fill, cornerRadius: 36, placeholderIcon: "person.fill")
                                    .frame(width: 72, height: 72)
                                    .clipShape(Circle())
                                    .overlay(Circle().stroke(Color.tintColor.opacity(0.6), lineWidth: 2))

                                MarqueeText(text: performer.name, font: .caption2, weight: .bold, speed: 20)
                                    .frame(width: 76)
                            }
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal)
            }
        }
    }

    private var classicSeriesSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Label("经典系列大放送 · 连贯篇章", systemImage: "square.stack.3d.up.fill")
                    .font(.headline.bold())
                Spacer()
                Text("随机抽取")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            .padding(.horizontal)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 14) {
                    ForEach(feedData.classicSeries, id: \.compositeKey) { series in
                        NavigationLink(destination: FilteredMovieListView(title: series.displayTitle, filterKey: "series", filterValue: series.rootTitle)) {
                            VStack(alignment: .leading, spacing: 6) {
                                GpdbImageView(rawPath: series.coverUrl, contentMode: .fill, cornerRadius: 12, placeholderIcon: "film.stack")
                                    .frame(width: 140, height: 190)

                                MarqueeText(text: series.displayTitle, font: .caption, weight: .bold, speed: 25)
                                    .frame(width: 140)

                                HStack {
                                    Text("\(series.movieCount) 部作品")
                                        .font(.caption2)
                                        .foregroundStyle(.secondary)
                                    Spacer()
                                }
                            }
                            .frame(width: 140)
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal)
            }
        }
    }

    private var blindBoxSection: some View {
        VStack(alignment: .leading, spacing: 14) {
            // 标题行与“换一批”按钮 (对标 Android LuckyDiscoverySection)
            HStack {
                Label("随心探索 · 盲盒发现", systemImage: "dice.fill")
                    .font(.headline.bold())

                Spacer()

                Button {
                    Task { await refreshLuckyMovies() }
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "arrow.clockwise")
                            .font(.caption.bold())
                            .rotationEffect(.degrees(diceAngle))
                        Text("换一批")
                            .font(.caption.bold())
                    }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(Color.secondary.opacity(0.12), in: Capsule())
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal)

            // 6 部随机盲盒影片 (对标 Android: 3 列 x 2 行等宽网格，卡片等间距与呼吸留白)
            LazyVGrid(columns: [
                GridItem(.flexible(), spacing: 12),
                GridItem(.flexible(), spacing: 12),
                GridItem(.flexible(), spacing: 12)
            ], spacing: 14) {
                ForEach(luckyMovies) { movie in
                    NavigationLink(destination: MovieDetailView(movieId: movie.id)) {
                        VStack(alignment: .leading, spacing: 6) {
                            // 海报主体 + 底部渐变元数据条
                            ZStack(alignment: .bottom) {
                                GpdbImageView(
                                    rawPath: movie.coverFull ?? movie.coverIcon,
                                    contentMode: .fill,
                                    cornerRadius: 10
                                )
                                .aspectRatio(0.7, contentMode: .fit)

                                // 底部渐变半透明信息栏 (年份 + 时长)
                                HStack {
                                    if let y = movie.releaseYear {
                                        Text("\(y)")
                                            .font(.system(size: 9, weight: .bold))
                                            .foregroundStyle(.white)
                                    }
                                    Spacer()
                                    if let mins = movie.durationMins, mins > 0 {
                                        Text("\(mins)分")
                                            .font(.system(size: 9, weight: .bold))
                                            .foregroundStyle(.white.opacity(0.85))
                                    }
                                }
                                .padding(.horizontal, 6)
                                .padding(.vertical, 4)
                                .background(
                                    LinearGradient(
                                        colors: [Color.clear, Color.black.opacity(0.75)],
                                        startPoint: .top,
                                        endPoint: .bottom
                                    )
                                )
                                .clipShape(RoundedRectangle(cornerRadius: 10))
                            }
                            .clipShape(RoundedRectangle(cornerRadius: 10))
                            .shadow(color: .black.opacity(0.08), radius: 3, x: 0, y: 2)

                            // 底部文字信息区
                            VStack(alignment: .leading, spacing: 2) {
                                MarqueeText(text: movie.displayTitle, font: .caption2, weight: .bold, speed: 22)

                                if let studio = movie.studioName, !studio.isEmpty {
                                    Text(studio)
                                        .font(.system(size: 9))
                                        .foregroundStyle(.secondary)
                                        .lineLimit(1)
                                }
                            }
                            .padding(.horizontal, 2)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal)
        }
    }

    private var libraryOverviewStats: some View {
        VStack(spacing: 10) {
            Divider()
                .padding(.horizontal)
                .padding(.top, 8)

            HStack(spacing: 12) {
                StatBadge(title: "影片总计", count: "\(feedData.totalMovies)", icon: "film")
                StatBadge(title: "场景分集", count: "\(feedData.totalEpisodes)", icon: "play.rectangle")
                StatBadge(title: "演职人员", count: "\(feedData.totalPerformers)", icon: "person.2")
                StatBadge(title: "片商厂牌", count: "\(feedData.totalStudios)", icon: "building.2")
            }
            .padding(.horizontal)
        }
    }

    private var homeFeedLoadingSkeleton: some View {
        VStack(spacing: 24) {
            // 装载状态提示微光胶囊
            HStack(spacing: 10) {
                ProgressView()
                    .tint(.amber)
                    .scaleEffect(0.85)
                Text("正在装载离线影库...")
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(.primary)
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 10)
            .background(.ultraThinMaterial, in: Capsule())
            .overlay(Capsule().stroke(Color.amber.opacity(0.35), lineWidth: 1))
            .shadow(color: Color.black.opacity(0.15), radius: 8, y: 3)
            .padding(.top, 8)

            // 焦点海报骨架卡片
            RoundedRectangle(cornerRadius: 16)
                .fill(Color.white.opacity(0.06))
                .frame(height: 200)
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(Color.white.opacity(0.06), lineWidth: 1)
                )
                .padding(.horizontal, 16)

            // 今日星光头像骨架屏
            VStack(alignment: .leading, spacing: 12) {
                RoundedRectangle(cornerRadius: 4)
                    .fill(Color.white.opacity(0.08))
                    .frame(width: 120, height: 16)
                    .padding(.horizontal, 16)

                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 16) {
                        ForEach(0..<6, id: \.self) { _ in
                            VStack(spacing: 8) {
                                Circle()
                                    .fill(Color.white.opacity(0.07))
                                    .frame(width: 66, height: 66)
                                RoundedRectangle(cornerRadius: 4)
                                    .fill(Color.white.opacity(0.05))
                                    .frame(width: 48, height: 10)
                            }
                        }
                    }
                    .padding(.horizontal, 16)
                }
            }

            // 经典系列骨架屏
            VStack(alignment: .leading, spacing: 12) {
                RoundedRectangle(cornerRadius: 4)
                    .fill(Color.white.opacity(0.08))
                    .frame(width: 140, height: 16)
                    .padding(.horizontal, 16)

                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 16) {
                        ForEach(0..<3, id: \.self) { _ in
                            RoundedRectangle(cornerRadius: 14)
                                .fill(Color.white.opacity(0.06))
                                .frame(width: 160, height: 96)
                        }
                    }
                    .padding(.horizontal, 16)
                }
            }
        }
        .padding(.vertical, 8)
    }

    private func refreshLuckyMovies() async {
        #if canImport(UIKit)
        let impact = UIImpactFeedbackGenerator(style: .medium)
        impact.impactOccurred()
        #endif
        withAnimation(.spring(response: 0.45, dampingFraction: 0.65)) {
            diceAngle += 360
        }
        isRefreshingLucky = true
        if let newMovies = try? await repository.getLuckyMovies(count: 6), !newMovies.isEmpty {
            withAnimation(.easeInOut(duration: 0.2)) {
                self.luckyMovies = newMovies
            }
        }
        isRefreshingLucky = false
    }

    private func loadFeed() async {
        isLoading = true
        do {
            let data = try await repository.getHomeFeed()
            self.feedData = data
            self.luckyMovies = data.luckyMovies
        } catch {
            print("装载首页失败: \(error)")
        }
        isLoading = false
    }
}

private struct StatBadge: View {
    let title: String
    let count: String
    let icon: String

    var body: some View {
        VStack(spacing: 4) {
            Image(systemName: icon)
                .font(.caption)
                .foregroundStyle(.tint)
            Text(count)
                .font(.subheadline.bold())
            Text(title)
                .font(.system(size: 9))
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 10)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 10))
    }
}

private extension Color {
    static var tintColor: Color { .accentColor }
}
