import SwiftUI

/// 瀑布流滚动偏移量 PreferenceKey (iOS 17 回退方案)
public struct WaterfallScrollOffsetPreferenceKey: PreferenceKey {
    public static var defaultValue: CGFloat = 0
    public static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) {
        value = nextValue()
    }
}

/// 专为瀑布流设计的自适应沉浸式滚动容器
/// 当用户向下滑动内容时自动收缩底部导航栏，向上滑动时自动展开还原；到达顶部时强制展开
///
/// iOS 18+ 使用 `onScrollGeometryChange` 高频精确追踪滚动偏移量；
/// iOS 17 回退使用 `GeometryReader` + `PreferenceKey` 方案。
public struct WaterfallScrollView<Content: View>: View {
    public let showsIndicators: Bool
    @ViewBuilder public let content: () -> Content

    @EnvironmentObject private var environment: AppEnvironment
    /// 上次触发隐藏/显示判定时的原始偏移量
    @State private var lastRawOffset: CGFloat = 0
    /// 静止状态 (顶部) 的原始偏移量基线——取初始化后的 contentOffset 稳定值
    @State private var restingOffset: CGFloat = 0
    /// 收到的帧数，用于跳过初始布局阶段不稳定的前几帧
    @State private var frameCount: Int = 0

    public init(
        showsIndicators: Bool = true,
        @ViewBuilder content: @escaping () -> Content
    ) {
        self.showsIndicators = showsIndicators
        self.content = content
    }

    public var body: some View {
        Group {
            if #available(iOS 18.0, *) {
                modernScrollView
            } else {
                legacyScrollView
            }
        }
        .onDisappear {
            environment.setTabBarHidden(false, animated: false)
        }
    }

    // MARK: - iOS 18+ onScrollGeometryChange

    @available(iOS 18.0, *)
    private var modernScrollView: some View {
        ScrollView(.vertical, showsIndicators: showsIndicators) {
            content()
        }
        .onScrollGeometryChange(for: CGFloat.self) { geometry in
            geometry.contentOffset.y
        } action: { _, newValue in
            handleRawOffset(newValue)
        }
    }

    // MARK: - iOS 17 GeometryReader + PreferenceKey

    private var legacyScrollView: some View {
        ScrollView(.vertical, showsIndicators: showsIndicators) {
            VStack(spacing: 0) {
                Color.clear
                    .frame(width: 1, height: 1)
                    .background(
                        GeometryReader { proxy in
                            Color.clear.preference(
                                key: WaterfallScrollOffsetPreferenceKey.self,
                                value: proxy.frame(in: .named("WaterfallLegacyCoord")).minY
                            )
                        }
                    )
                content()
            }
        }
        .coordinateSpace(name: "WaterfallLegacyCoord")
        .onPreferenceChange(WaterfallScrollOffsetPreferenceKey.self) { rawY in
            handleRawOffset(-rawY)
        }
    }

    // MARK: - 统一偏移处理

    private func handleRawOffset(_ rawOffset: CGFloat) {
        frameCount += 1

        // 前 3 帧为布局稳定期 — 只记录基线，不做判定
        // (首帧 contentOffset.y 可能为 0，稳定后变为 -116 等受 safe area 影响的值)
        if frameCount <= 3 {
            restingOffset = rawOffset
            lastRawOffset = rawOffset
            return
        }

        // scrolledAmount: 相对于静止位置的滚动深度 (正值 = 已向下滚动)
        let scrolledAmount = rawOffset - restingOffset

        // 接近页面顶部 → 强制展开导航栏
        if scrolledAmount <= 15 {
            if environment.isTabBarHidden {
                environment.setTabBarHidden(false, animated: true)
            }
            lastRawOffset = rawOffset
            return
        }

        let delta = rawOffset - lastRawOffset
        if delta > 12 {
            // 手指向上划 → 内容向上滚 → 收起导航栏
            if !environment.isTabBarHidden {
                environment.setTabBarHidden(true, animated: true)
            }
            lastRawOffset = rawOffset
        } else if delta < -12 {
            // 手指向下划 → 内容向下滚 → 展开导航栏
            if environment.isTabBarHidden {
                environment.setTabBarHidden(false, animated: true)
            }
            lastRawOffset = rawOffset
        }
    }
}
