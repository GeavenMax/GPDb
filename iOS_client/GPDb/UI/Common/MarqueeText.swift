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
            let cWidth = max(geo.size.width, 0)

            ZStack(alignment: .leading) {
                if shouldAnimate {
                    HStack(spacing: spacing) {
                        singleTextItem
                        singleTextItem
                    }
                    .fixedSize()
                    .frame(width: cWidth, alignment: .leading)
                    .offset(x: offset)
                    .task(id: animationKey) {
                        await startMarqueeAnimation()
                    }
                } else {
                    singleTextItem
                        .frame(width: cWidth, alignment: .leading)
                }
            }
            .frame(width: cWidth, height: geo.size.height, alignment: .leading)
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
                    resetOffsetInstant()
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
                resetOffsetInstant()
                animationKey = UUID()
            }
        }
        .onChange(of: text) { _, _ in
            resetOffsetInstant()
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

    private func resetOffsetInstant() {
        var tx = Transaction()
        tx.disablesAnimations = true
        withTransaction(tx) {
            offset = 0
        }
    }

    @MainActor
    private func startMarqueeAnimation() async {
        guard shouldAnimate else {
            resetOffsetInstant()
            return
        }

        let distance = textWidth + spacing
        let duration = max(Double(distance) / speed, 2.5)

        while !Task.isCancelled && shouldAnimate {
            resetOffsetInstant()

            // 停留 1.5 秒让用户舒适阅读首部
            try? await Task.sleep(nanoseconds: 1_500_000_000)
            guard !Task.isCancelled, shouldAnimate else { break }

            // 平滑线性滚动至末尾，Item 2 精准接替 Item 1 位置
            withAnimation(.linear(duration: duration)) {
                offset = -distance
            }

            // 等待本次滚动结束
            try? await Task.sleep(nanoseconds: UInt64(duration * 1_000_000_000))
            guard !Task.isCancelled, shouldAnimate else { break }
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
