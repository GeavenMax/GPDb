import SwiftUI

public enum MainTab: Hashable {
    case feed
    case browse
    case library
}

/// 支持 iPhone TabView 与 iPadOS NavigationSplitView 深度适配的导航框架
public struct AppNavigation: View {
    @EnvironmentObject private var environment: AppEnvironment
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass

    @State private var selectedTab: MainTab = .feed

    public init() {}

    private var splitSelectionBinding: Binding<MainTab?> {
        Binding(
            get: { selectedTab },
            set: { if let val = $0 { selectedTab = val } }
        )
    }

    public var body: some View {
        Group {
            if horizontalSizeClass == .regular {
                // iPadOS / macOS 侧边栏分栏架构 (对标桌面端)
                NavigationSplitView {
                    List(selection: splitSelectionBinding) {
                        Label("主页", systemImage: "house").tag(Optional(MainTab.feed))
                        Label("影视库", systemImage: "film.stack").tag(Optional(MainTab.browse))
                        Label("我的影库", systemImage: "bookmark").tag(Optional(MainTab.library))
                    }
                    .navigationTitle("GPDb")
                } detail: {
                    detailViewForTab(selectedTab)
                }
            } else {
                // iPhone 触控底部导航栏
                TabView(selection: $selectedTab) {
                    HomeFeedView()
                        .tabItem { Label("主页", systemImage: "house") }
                        .tag(MainTab.feed)

                    BrowseView()
                        .tabItem { Label("影视库", systemImage: "film.stack") }
                        .tag(MainTab.browse)

                    LibraryView()
                        .tabItem { Label("我的影库", systemImage: "bookmark") }
                        .tag(MainTab.library)
                }
                .tint(.amber)
            }
        }
    }

    @ViewBuilder
    private func detailViewForTab(_ tab: MainTab) -> some View {
        switch tab {
        case .feed:
            HomeFeedView()
        case .browse:
            BrowseView()
        case .library:
            LibraryView()
        }
    }
}

