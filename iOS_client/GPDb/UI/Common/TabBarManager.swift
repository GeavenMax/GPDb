import SwiftUI
#if canImport(UIKit)
import UIKit

/// 负责底层原生 UITabBar 的平滑位移动画与手势联动管理
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
