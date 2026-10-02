import SwiftUI
#if canImport(UIKit)
import UIKit
#endif

/// 循环平滑滚动跑马灯文字组件 (长标题溢出时自动启用无缝循环跑马灯，未溢出时静态靠左显示)
public struct MarqueeText: View {
    public let text: String
    public var font: Font
    public var weight: Font.Weight
    public var foregroundColor: Color
    public var spacing: CGFloat
    public var speed: Double // 移动速度 (点/秒)

    @State private var textWidth: CGFloat = 0
    @State private var textHeight: CGFloat = 18
    @State private var containerWidth: CGFloat = 0
    @State private var offset: CGFloat = 0
    @State private var animationKey: UUID = UUID()

    public init(
        text: String,
        font: Font = .title2,
        weight: Font.Weight = .bold,
        foregroundColor: Color = .primary,
        spacing: CGFloat = 40,
        speed: Double = 35
    ) {
        self.text = text
        self.font = font
        self.weight = weight
        self.foregroundColor = foregroundColor
        self.spacing = spacing
        self.speed = speed
    }

    private var shouldAnimate: Bool {
        textWidth > containerWidth && containerWidth > 0
    }

    public var body: some View {
        GeometryReader { geo in
            let cWidth = geo.size.width

            ZStack(alignment: .leading) {
                if shouldAnimate {
                    HStack(spacing: spacing) {
                        singleTextItem
                        singleTextItem
                    }
                    .fixedSize()
                    .offset(x: offset)
                    .id(animationKey)
                    .task(id: animationKey) {
                        runAnimation()
                    }
                } else {
                    singleTextItem
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
            .clipped()
            .onAppear {
                containerWidth = cWidth
                if shouldAnimate {
                    animationKey = UUID()
                }
            }
            .onChange(of: cWidth) { _, newWidth in
                let wasAnimating = shouldAnimate
                containerWidth = newWidth
                if shouldAnimate != wasAnimating || shouldAnimate {
                    animationKey = UUID()
                }
            }
        }
        .frame(height: max(textHeight, 18))
        .background(
            // 隐藏的测量视图，获取文字自然单行宽度与高度
            singleTextItem
                .fixedSize()
                .background(
                    GeometryReader { mGeo in
                        Color.clear.preference(
                            key: MarqueeTextSizePreferenceKey.self,
                            value: mGeo.size
                        )
                    }
                )
                .hidden()
        )
        .onPreferenceChange(MarqueeTextSizePreferenceKey.self) { size in
            let prevWidth = self.textWidth
            self.textWidth = size.width
            if size.height > 0 {
                self.textHeight = size.height
            }
            if abs(prevWidth - size.width) > 1 && shouldAnimate {
                animationKey = UUID()
            }
        }
        .onChange(of: text) { _, _ in
            offset = 0
            animationKey = UUID()
        }
    }

    private var singleTextItem: some View {
        Text(text)
            .font(font)
            .fontWeight(weight)
            .foregroundStyle(foregroundColor)
            .lineLimit(1)
            .fixedSize(horizontal: true, vertical: false)
    }

    private func runAnimation() {
        guard shouldAnimate else {
            offset = 0
            return
        }
        let distance = textWidth + spacing
        let duration = max(Double(distance) / speed, 2.5)

        Task { @MainActor in
            offset = 0
            try? await Task.sleep(nanoseconds: 1_200_000_000)
            guard !Task.isCancelled, shouldAnimate else { return }
            withAnimation(.linear(duration: duration).repeatForever(autoreverses: false)) {
                offset = -distance
            }
        }
    }
}

private struct MarqueeTextSizePreferenceKey: PreferenceKey {
    static var defaultValue: CGSize = .zero
    static func reduce(value: inout CGSize, nextValue: () -> CGSize) {
        let next = nextValue()
        value = CGSize(width: max(value.width, next.width), height: max(value.height, next.height))
    }
}
