import SwiftUI
#if canImport(UIKit)
import UIKit
#endif

/// 具有物理画幅自适应贴合能力的封面海报卡片 (消除上下/左右黑边，自适应贴合图片边缘)
/// 对标 Android AdaptivePosterCard 与跨端 v2.17.0 纯化海报展台
public struct AdaptivePosterCard: View {
    public let imagePath: String?
    public var maxHeight: CGFloat = 360
    public var maxWidth: CGFloat? = nil
    public var cornerRadius: CGFloat = 16
    public var fallbackIcon: String = "film"
    public var onError: (() -> Void)? = nil
    public var onTap: (() -> Void)? = nil

    @State private var aspectRatio: CGFloat? = nil

    public init(
        imagePath: String?,
        maxHeight: CGFloat = 360,
        maxWidth: CGFloat? = nil,
        cornerRadius: CGFloat = 16,
        fallbackIcon: String = "film",
        onError: (() -> Void)? = nil,
        onTap: (() -> Void)? = nil
    ) {
        self.imagePath = imagePath
        self.maxHeight = maxHeight
        self.maxWidth = maxWidth
        self.cornerRadius = cornerRadius
        self.fallbackIcon = fallbackIcon
        self.onError = onError
        self.onTap = onTap
    }

    public var body: some View {
        Button {
            onTap?()
        } label: {
            ZStack {
                GpdbImageView(
                    rawPath: imagePath,
                    contentMode: .fill,
                    cornerRadius: cornerRadius,
                    placeholderIcon: fallbackIcon,
                    targetSize: CGSize(width: 800, height: 1200),
                    onSuccess: { result in
                        #if canImport(UIKit)
                        let size = result.image.size
                        if size.width > 0 && size.height > 0 {
                            let ratio = size.width / size.height
                            withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) {
                                self.aspectRatio = ratio
                            }
                        }
                        #endif
                    },
                    onFailure: { _ in
                        onError?()
                    }
                )
            }
            .frame(width: computedWidth, height: maxHeight)
            .clipShape(RoundedRectangle(cornerRadius: cornerRadius))
            .overlay(
                RoundedRectangle(cornerRadius: cornerRadius)
                    .stroke(Color.primary.opacity(0.08), lineWidth: 1)
            )
            .shadow(color: Color.black.opacity(0.12), radius: 8, x: 0, y: 4)
        }
        .buttonStyle(.plain)
    }

    private var computedWidth: CGFloat {
        let ratio = aspectRatio ?? (2.0 / 3.0)
        let naturalWidth = maxHeight * ratio
        if let maxW = maxWidth {
            return min(naturalWidth, maxW)
        }
        return naturalWidth
    }
}
