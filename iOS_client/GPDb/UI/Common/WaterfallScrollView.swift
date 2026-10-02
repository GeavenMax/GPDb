import SwiftUI

/// 瀑布流滚动偏移量 PreferenceKey
public struct WaterfallScrollOffsetPreferenceKey: PreferenceKey {
    public static var defaultValue: CGFloat = 0
    public static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) {
        value = nextValue()
    }
}

/// 专为瀑布流设计的自适应沉浸式滚动容器
/// 当用户向下滑动内容时自动收缩底部导航栏，向上滑动时自动展开还原；到达顶部时强制展开
public struct WaterfallScrollView<Content: View>: View {
    public let showsIndicators: Bool
    @ViewBuilder public let content: () -> Content

    @EnvironmentObject private var environment: AppEnvironment
    @State private var lastOffset: CGFloat = 0
    private let spaceName: String = UUID().uuidString

    public init(
        showsIndicators: Bool = true,
        @ViewBuilder content: @escaping () -> Content
    ) {
        self.showsIndicators = showsIndicators
        self.content = content
    }

    public var body: some View {
        ScrollView(.vertical, showsIndicators: showsIndicators) {
            GeometryReader { proxy in
                Color.clear.preference(
                    key: WaterfallScrollOffsetPreferenceKey.self,
                    value: proxy.frame(in: .named(spaceName)).minY
                )
            }
            .frame(height: 0)

            content()
        }
        .coordinateSpace(name: spaceName)
        .onPreferenceChange(WaterfallScrollOffsetPreferenceKey.self) { currentY in
            handleScroll(currentY)
        }
        .onDisappear {
            // 离开当前视图时，恢复底部导航栏为可见状态
            if environment.isTabBarHidden {
                withAnimation(.easeInOut(duration: 0.2)) {
                    environment.isTabBarHidden = false
                }
            }
        }
    }

    private func handleScroll(_ currentY: CGFloat) {
        // 接近页面顶部 (拉至顶端或下拉刷新) 强制展开导航栏
        if currentY >= -15 {
            if environment.isTabBarHidden {
                withAnimation(.easeInOut(duration: 0.25)) {
                    environment.isTabBarHidden = false
                }
            }
            lastOffset = currentY
            return
        }

        let delta = currentY - lastOffset
        // 设定 22pt 防抖阈值，过滤轻微微颤
        if delta < -22 {
            // 手指向下划，内容向上滚 -> 收起导航栏以拓展全屏浏览沉浸感
            if !environment.isTabBarHidden {
                withAnimation(.easeInOut(duration: 0.25)) {
                    environment.isTabBarHidden = true
                }
            }
            lastOffset = currentY
        } else if delta > 22 {
            // 手指向上划，内容向下滚 -> 展开底部导航栏方便随时切换
            if environment.isTabBarHidden {
                withAnimation(.easeInOut(duration: 0.25)) {
                    environment.isTabBarHidden = false
                }
            }
            lastOffset = currentY
        }
    }
}
