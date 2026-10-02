import SwiftUI

/// 退后台高斯模糊遮罩视图 (对标 Android FLAG_SECURE)
public struct PrivacyProtectionView: View {
    public init() {}

    public var body: some View {
        ZStack {
            Color.black.opacity(0.4)
                .ignoresSafeArea()
            Rectangle()
                .fill(.thickMaterial)
                .ignoresSafeArea()

            VStack(spacing: 14) {
                Image(systemName: "shield.lefthalf.filled")
                    .font(.system(size: 52))
                    .foregroundStyle(.tint)
                Text("隐私防窥保护已生效")
                    .font(.headline.bold())
                    .foregroundStyle(.primary)
                Text("返回应用即可自动恢复浏览")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
            .padding(28)
            .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 22))
            .shadow(color: .black.opacity(0.15), radius: 20, x: 0, y: 10)
        }
    }
}

/// 响应式防窥模糊视图修饰符
public struct PrivacyBlurModifier: ViewModifier {
    public let isActive: Bool
    public let radius: CGFloat

    public func body(content: Content) -> some View {
        if isActive {
            content
                .blur(radius: radius)
                .overlay(
                    Rectangle()
                        .fill(.black.opacity(0.2))
                )
        } else {
            content
        }
    }
}

public extension View {
    func privacyBlur(isActive: Bool, radius: CGFloat = 24) -> some View {
        modifier(PrivacyBlurModifier(isActive: isActive, radius: radius))
    }
}
