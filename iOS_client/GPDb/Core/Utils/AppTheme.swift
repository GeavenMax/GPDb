import SwiftUI

/// App 外观主题选项 (深色、浅色、跟随系统)
public enum AppThemeMode: String, CaseIterable, Identifiable {
    case dark = "dark"
    case light = "light"
    case system = "system"

    public var id: String { rawValue }

    public var title: String {
        switch self {
        case .dark: return "暗色模式"
        case .light: return "浅色模式"
        case .system: return "跟随系统"
        }
    }

    public var icon: String {
        switch self {
        case .dark: return "moon.fill"
        case .light: return "sun.max.fill"
        case .system: return "circle.lefthalf.filled"
        }
    }

    /// 映射到 SwiftUI 的 ColorScheme?
    public var colorScheme: ColorScheme? {
        switch self {
        case .dark: return .dark
        case .light: return .light
        case .system: return nil
        }
    }
}
