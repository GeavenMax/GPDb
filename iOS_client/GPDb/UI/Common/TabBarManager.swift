import SwiftUI
#if canImport(UIKit)
import UIKit

/// 底层 UITabBar 的原生位移动画管理器 (备用方案，当前未启用)
///
/// 注意: 在 iOS 18+ 上，SwiftUI 的 `.toolbar(.hidden, for: .tabBar)` 与 UIKit 的
/// `CGAffineTransform` 存在冲突 — SwiftUI 会在布局阶段重置 transform。
/// 当前版本使用纯 SwiftUI `withAnimation` + `.toolbar` 方案，此管理器保留备用。
public enum TabBarManager {
    @MainActor
    public static func setTabBarHidden(_ hidden: Bool, animated: Bool = true) {
        guard let window = activeKeyWindow() else { return }
        guard let tabBar = findTabBar(in: window) else { return }

        let bottomInset = window.safeAreaInsets.bottom
        let barHeight = tabBar.frame.height > 0 ? tabBar.frame.height : 49
        let totalOffset = barHeight + bottomInset + 20

        let targetTransform = hidden ? CGAffineTransform(translationX: 0, y: totalOffset) : .identity
        let targetAlpha: CGFloat = hidden ? 0.0 : 1.0

        tabBar.isUserInteractionEnabled = !hidden

        if animated {
            UIView.animate(
                withDuration: 0.28,
                delay: 0,
                options: [.curveEaseInOut, .allowUserInteraction]
            ) {
                tabBar.transform = targetTransform
                tabBar.alpha = targetAlpha
            }
        } else {
            tabBar.transform = targetTransform
            tabBar.alpha = targetAlpha
        }
    }

    @MainActor
    public static func activeKeyWindow() -> UIWindow? {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }
            .first { $0.isKeyWindow } ??
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }
            .first
    }

    @MainActor
    public static func findTabBar(in view: UIView) -> UITabBar? {
        if let bar = view as? UITabBar {
            return bar
        }
        for subview in view.subviews {
            if let bar = findTabBar(in: subview) {
                return bar
            }
        }
        return nil
    }
}
#endif
