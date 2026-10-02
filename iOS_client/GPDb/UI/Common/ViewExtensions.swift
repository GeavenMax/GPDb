import SwiftUI

public extension Color {
    static var amber: Color { Color(red: 0.98, green: 0.75, blue: 0.2) }

    init(hex: String) {
        let hex = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        var int: UInt64 = 0
        Scanner(string: hex).scanHexInt64(&int)
        let a, r, g, b: UInt64
        switch hex.count {
        case 3: // RGB (12-bit)
            (a, r, g, b) = (255, (int >> 8) * 17, (int >> 4 & 0xF) * 17, (int & 0xF) * 17)
        case 6: // RGB (24-bit)
            (a, r, g, b) = (255, int >> 16, int >> 8 & 0xFF, int & 0xFF)
        case 8: // ARGB (32-bit)
            (a, r, g, b) = (int >> 24, int >> 16 & 0xFF, int >> 8 & 0xFF, int & 0xFF)
        default:
            (a, r, g, b) = (255, 0, 0, 0)
        }
        self.init(
            .sRGB,
            red: Double(r) / 255,
            green: Double(g) / 255,
            blue: Double(b) / 255,
            opacity: Double(a) / 255
        )
    }
}

public extension ShapeStyle where Self == Color {
    static var amber: Color { Color.amber }
}

public extension View {
    /// iOS 专用的内联导航标题修饰器 (在 macOS 上平滑降级)
    @ViewBuilder
    func inlineNavigationTitle() -> some View {
        #if os(iOS)
        self.navigationBarTitleDisplayMode(.inline)
        #else
        self
        #endif
    }

    /// 分页轮播样式 (在 macOS 上平滑降级)
    @ViewBuilder
    func pagedTabViewStyle() -> some View {
        #if os(iOS) || os(tvOS) || os(watchOS)
        self.tabViewStyle(.page(indexDisplayMode: .automatic))
        #else
        self
        #endif
    }

    /// 全屏或弹窗展示修饰器 (在 macOS 上平滑降级为 sheet)
    @ViewBuilder
    func fullScreenOrSheet<Item: Identifiable, Content: View>(
        item: Binding<Item?>,
        onDismiss: (() -> Void)? = nil,
        @ViewBuilder content: @escaping (Item) -> Content
    ) -> some View {
        #if os(iOS) || os(tvOS) || os(watchOS)
        self.fullScreenCover(item: item, onDismiss: onDismiss, content: content)
        #else
        self.sheet(item: item, onDismiss: onDismiss, content: content)
        #endif
    }
}
