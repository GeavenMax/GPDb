import SwiftUI
#if canImport(UIKit)
import UIKit
#endif

public enum PerformerTab: String, CaseIterable, Identifiable {
    case movies = "出演电影"
    case episodes = "出演分集"

    public var id: String { rawValue }
}

public struct PerformerDetailView: View {
    @EnvironmentObject private var environment: AppEnvironment
    public let performerId: Int64

    private let performerRepo = PerformerRepository()
    private let userRepo = UserRepository()
    private let analyticsRepo = UserAnalyticsRepository.shared

    @State private var detailData: PerformerDetailData? = nil
    @State private var selectedTab: PerformerTab = .movies
    @State private var isFavorite: Bool = false
    @State private var isAliasesExpanded: Bool = false
    @State private var isLoading: Bool = true
    @State private var lightboxImage: String? = nil
    @Namespace private var tabNamespace

    public init(performerId: Int64) {
        self.performerId = performerId
    }

    public var body: some View {
        WaterfallScrollView {
            if isLoading {
                ProgressView()
                    .padding(.top, 60)
            } else if let data = detailData {
                // 单流式 Lazy 布局：将人物全息档案与作品内容区置于统一流中
                LazyVStack(spacing: 16) {
                    // 1. 演员人物全息档案卡片
                    performerProfileHeader(data: data)
                        .padding(.horizontal)

                    // 2. 作品内容区 (电影网格 / 分集列表)
                    if selectedTab == .movies {
                        moviesGrid(movies: data.movies)
                    } else {
                        episodesList(episodes: data.episodes)
                    }
                }
                .padding(.bottom, 16)
            }
        }
        .safeAreaInset(edge: .bottom) {
            if let data = detailData {
                tabHeader(data: data)
            }
        }
        .dynamicAmbientBackground(imagePath: detailData?.performer.imageUrl)
        .inlineNavigationTitle()
        .toolbar(environment.isTabBarHidden ? .hidden : .visible, for: .tabBar)
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
        .fullScreenOrSheet(item: Binding(
            get: { lightboxImage != nil ? PerformerLightboxItem(path: lightboxImage!) : nil },
            set: { lightboxImage = $0?.path }
        )) { item in
            ZoomableImageViewer(imagePath: item.path) {
                lightboxImage = nil
            }
        }
        .task {
            if detailData == nil {
                await loadDetail()
                analyticsRepo.recordPerformerView()
            }
        }
    }

    // MARK: - 单流式头部板块

    private func performerProfileHeader(data: PerformerDetailData) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            // 头像与核心名号
            HStack(spacing: 16) {
                GpdbImageView(
                    rawPath: data.performer.imageUrl ?? data.pbcProfile?.imageUrl,
                    contentMode: .fill,
                    cornerRadius: 44,
                    placeholderIcon: "person.fill",
                    cropAlignment: .top
                )
                .frame(width: 88, height: 88)
                .clipShape(Circle())
                .overlay(Circle().stroke(Color.tintColor.opacity(0.4), lineWidth: 2))
                .onTapGesture {
                    lightboxImage = data.performer.imageUrl ?? data.pbcProfile?.imageUrl
                }

                VStack(alignment: .leading, spacing: 6) {
                    HStack(alignment: .center, spacing: 8) {
                        MarqueeText(
                            text: data.performer.name,
                            font: .title2,
                            weight: .bold
                        )

                        if let pbc = data.pbcProfile, pbc.careerStatus != nil {
                            Text(pbc.isActive ? "活跃中" : "已退役")
                                .font(.system(size: 10, weight: .bold))
                                .foregroundStyle(pbc.isActive ? .green : .secondary)
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background((pbc.isActive ? Color.green : Color.secondary).opacity(0.15), in: Capsule())
                                .fixedSize()
                        }
                    }

                    if let birthName = data.pbcProfile?.birthName, birthName != data.performer.name {
                        Text("本名: \(birthName)")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                    }

                    if let start = data.pbcProfile?.careerStart {
                        Text("出道: \(start)年")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
                Spacer()
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            // 生理指标网格 (GEVI 优先，PBC 补缺)
            if !data.mergedTraits.isEmpty {
                traitsGrid(traits: data.mergedTraits)
            }

            // 曾用艺名 / 别名 (AKA) 独立折叠卡片
            if !data.allAliases.isEmpty {
                aliasesCard(aliases: data.allAliases)
            }

            // 维基生平简介 (PBC / SJ)
            if let bio = data.pbcProfile?.bio ?? data.sjProfile?.bio, !bio.isEmpty {
                VStack(alignment: .leading, spacing: 6) {
                    Text("百科人物小传")
                        .font(.caption.bold())
                        .foregroundStyle(.secondary)
                    Text(bio)
                        .font(.caption)
                        .lineSpacing(4)
                        .foregroundStyle(.primary.opacity(0.85))
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(12)
                .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 12))
            }

            // 全网互联档案胶囊
            connectedProfilesRow(data: data)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func traitsGrid(traits: [(label: String, value: String)]) -> some View {
        FlowLayout(horizontalSpacing: 8, verticalSpacing: 8) {
            ForEach(0..<traits.count, id: \.self) { idx in
                let trait = traits[idx]
                HStack(spacing: 5) {
                    Text(trait.label)
                        .font(.system(size: 10))
                        .foregroundStyle(.secondary)
                    Text(trait.value)
                        .font(.caption.bold())
                        .foregroundStyle(.primary)
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(.ultraThinMaterial, in: Capsule())
                .overlay(Capsule().stroke(Color.primary.opacity(0.08), lineWidth: 1))
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func aliasesCard(aliases: [String]) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Label("曾用艺名 / 别名 (\(aliases.count))", systemImage: "person.text.rectangle")
                    .font(.caption.bold())
                Spacer()
                if aliases.count > 8 {
                    Button(isAliasesExpanded ? "收起" : "展开全部") {
                        withAnimation { isAliasesExpanded.toggle() }
                    }
                    .font(.caption2.bold())
                }
            }

            let displayList = isAliasesExpanded ? aliases : Array(aliases.prefix(8))
            FlowLayout(spacing: 6) {
                ForEach(displayList, id: \.self) { alias in
                    Text(alias)
                        .font(.caption2)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(Color.secondary.opacity(0.12), in: Capsule())
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 12))
    }

    private func connectedProfilesRow(data: PerformerDetailData) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            // 互联档案外链 (IAFD, IMDb, Twitter/X 等)
            if !data.externalLinks.isEmpty {
                HStack(spacing: 6) {
                    Text("互联档案:")
                        .font(.caption2.bold())
                        .foregroundStyle(.secondary)

                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(data.externalLinks, id: \.name) { link in
                                if let url = URL(string: link.url) {
                                    Link(destination: url) {
                                        Text("\(link.name) ↗")
                                            .font(.caption2.bold())
                                            .foregroundStyle(Color.tintColor)
                                            .padding(.horizontal, 8)
                                            .padding(.vertical, 4)
                                            .background(Color.secondary.opacity(0.12), in: Capsule())
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 快捷资源检索与外链按钮: BT4G 搜索, BoyfriendTV, PBC 百科, SmutJunkies
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    // BT4G 搜索按钮
                    if let encoded = data.performer.name.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
                       let url = URL(string: "https://bt4gprx.com/search?q=\(encoded)") {
                        Link(destination: url) {
                            HStack(spacing: 4) {
                                Image(systemName: "magnifyingglass")
                                    .font(.system(size: 11, weight: .bold))
                                Text("BT4G 搜索")
                                    .font(.caption2.bold())
                            }
                            .padding(.horizontal, 10)
                            .padding(.vertical, 6)
                            .background(Color.red.opacity(0.18), in: Capsule())
                            .foregroundStyle(.red)
                        }
                    }

                    if let bftv = data.performer.bftvUrl, let url = URL(string: bftv) {
                        Link(destination: url) {
                            ExternalLinkBadge(title: "BoyfriendTV ↗", color: .blue)
                        }
                    }

                    if let pbc = data.pbcProfile, let url = URL(string: pbc.pbcUrl) {
                        Link(destination: url) {
                            ExternalLinkBadge(title: "PBC 百科 ↗", color: .indigo)
                        }
                    }

                    if let sj = data.sjProfile, let url = URL(string: sj.sjUrl) {
                        Link(destination: url) {
                            ExternalLinkBadge(title: "SmutJunkies ↗", color: .pink)
                        }
                    }
                }
            }
        }
    }

    private func tabHeader(data: PerformerDetailData) -> some View {
        HStack(spacing: 4) {
            ForEach(PerformerTab.allCases) { tab in
                let isSelected = selectedTab == tab
                let count = tab == .movies ? data.movies.count : data.episodes.count
                let icon = tab == .movies ? "film.fill" : "play.rectangle.fill"

                Button {
                    #if canImport(UIKit)
                    let generator = UISelectionFeedbackGenerator()
                    generator.selectionChanged()
                    #endif
                    withAnimation(.spring(response: 0.35, dampingFraction: 0.76)) {
                        selectedTab = tab
                    }
                } label: {
                    HStack(spacing: 7) {
                        Image(systemName: icon)
                            .font(.system(size: 13, weight: isSelected ? .bold : .medium))
                            .symbolRenderingMode(.hierarchical)
                        
                        Text(tab.rawValue)
                            .font(.system(size: 13, weight: isSelected ? .bold : .medium))

                        Text("\(count)")
                            .font(.system(size: 11, weight: .heavy, design: .rounded))
                            .padding(.horizontal, 7)
                            .padding(.vertical, 2.5)
                            .background(
                                Capsule()
                                    .fill(isSelected ? Color.tintColor.opacity(0.18) : Color.primary.opacity(0.08))
                            )
                            .foregroundStyle(isSelected ? Color.tintColor : .secondary)
                    }
                    .foregroundStyle(isSelected ? Color.tintColor : .secondary)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 8)
                    .background {
                        if isSelected {
                            Capsule()
                                .fill(Color(uiColor: .secondarySystemGroupedBackground))
                                .matchedGeometryEffect(id: "PerformerActiveTabPill", in: tabNamespace)
                                .shadow(color: Color.black.opacity(0.12), radius: 6, x: 0, y: 3)
                                .overlay(
                                    Capsule()
                                        .stroke(Color.tintColor.opacity(0.35), lineWidth: 1)
                                )
                        }
                    }
                }
                .buttonStyle(.plain)
            }
        }
        .padding(4)
        .background(
            Capsule()
                .fill(.ultraThinMaterial)
                .shadow(color: Color.black.opacity(0.15), radius: 16, x: 0, y: 6)
                .overlay(
                    Capsule()
                        .stroke(
                            LinearGradient(
                                colors: [
                                    Color.white.opacity(0.35),
                                    Color.white.opacity(0.08),
                                    Color.white.opacity(0.02)
                                ],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            ),
                            lineWidth: 1
                        )
                )
        )
        .padding(.horizontal, 20)
        .padding(.bottom, 12)
    }

    private func moviesGrid(movies: [MovieRecord]) -> some View {
        LazyVGrid(
            columns: Array(repeating: GridItem(.flexible(), spacing: 12), count: environment.layoutColumns),
            spacing: 14
        ) {
            ForEach(movies) { movie in
                NavigationLink(destination: MovieDetailView(movieId: movie.id)) {
                    VStack(alignment: .leading, spacing: 6) {
                        GpdbImageView(rawPath: movie.coverFull ?? movie.coverIcon, contentMode: .fill, cornerRadius: 10)
                            .aspectRatio(0.68, contentMode: .fill)
                            .frame(maxWidth: .infinity)
                            .clipped()
                            .clipShape(RoundedRectangle(cornerRadius: 10))

                        MarqueeText(text: movie.displayTitle, font: .caption, weight: .bold, speed: 25)

                        if let y = movie.releaseYear {
                            Text("\(y)年")
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal)
        .padding(.top, 12)
    }

    private func episodesList(episodes: [EpisodeRecord]) -> some View {
        LazyVStack(spacing: 10) {
            ForEach(episodes) { ep in
                NavigationLink(destination: EpisodeDetailView(episodeId: ep.id)) {
                    HStack(spacing: 12) {
                        GpdbImageView(rawPath: ep.thumbnailUrl, contentMode: .fill, cornerRadius: 8, placeholderIcon: "play.rectangle.fill")
                            .frame(width: 80, height: 50)

                        VStack(alignment: .leading, spacing: 4) {
                            MarqueeText(text: ep.displayTitle, font: .subheadline, weight: .bold, speed: 25)
                            if let d = ep.displayDescription {
                                Text(d)
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
        .padding(.horizontal)
        .padding(.top, 12)
    }

    private func loadDetail() async {
        isLoading = true
        do {
            if let data = try await performerRepo.getPerformerDetail(id: performerId) {
                self.detailData = data
                self.isFavorite = data.isFavorite
            }
        } catch {
            print("加载演员详情失败: \(error)")
        }
        isLoading = false
    }

    private func toggleFavorite() async {
        isFavorite.toggle()
        #if canImport(UIKit)
        let impact = UIImpactFeedbackGenerator(style: .medium)
        impact.impactOccurred()
        #endif
        try? await userRepo.toggleFavorite(entityType: "performer", entityKey: String(performerId), isFavorite: isFavorite)
    }
}

private struct PerformerLightboxItem: Identifiable {
    var id: String { path }
    let path: String
}

private struct ExternalLinkBadge: View {
    let title: String
    let color: Color

    var body: some View {
        Text(title)
            .font(.caption2.bold())
            .foregroundStyle(color)
            .padding(.horizontal, 10)
            .padding(.vertical, 5)
            .background(color.opacity(0.12), in: Capsule())
    }
}

private extension Color {
    static var tintColor: Color { .accentColor }
}
