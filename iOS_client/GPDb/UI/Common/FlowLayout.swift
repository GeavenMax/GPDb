import SwiftUI

/// 自适应流式折行布局容器 (支持 iOS 16+ 原生 Layout 协议)
/// 根据子视图自然尺寸排布，超出容器可用宽度自动平滑折行
public struct FlowLayout: Layout {
    public var horizontalSpacing: CGFloat
    public var verticalSpacing: CGFloat

    public init(spacing: CGFloat = 8) {
        self.horizontalSpacing = spacing
        self.verticalSpacing = spacing
    }

    public init(horizontalSpacing: CGFloat = 8, verticalSpacing: CGFloat = 8) {
        self.horizontalSpacing = horizontalSpacing
        self.verticalSpacing = verticalSpacing
    }

    private func computeWidth(proposal: ProposedViewSize) -> CGFloat {
        if let w = proposal.width, w > 0, w < .infinity {
            return w
        }
        #if canImport(UIKit)
        return max(UIScreen.main.bounds.width - 32, 120)
        #else
        return 350
        #endif
    }

    public func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let maxRowWidth = computeWidth(proposal: proposal)
        var totalHeight: CGFloat = 0
        var currentX: CGFloat = 0
        var currentRowHeight: CGFloat = 0

        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            if currentX + size.width > maxRowWidth && currentX > 0 {
                totalHeight += currentRowHeight + verticalSpacing
                currentX = 0
                currentRowHeight = 0
            }
            currentX += size.width + horizontalSpacing
            currentRowHeight = max(currentRowHeight, size.height)
        }
        totalHeight += currentRowHeight
        return CGSize(width: maxRowWidth, height: totalHeight)
    }

    public func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let maxRowWidth = bounds.width > 0 ? bounds.width : computeWidth(proposal: proposal)
        let maxX = bounds.minX + maxRowWidth
        var currentX = bounds.minX
        var currentY = bounds.minY
        var currentRowHeight: CGFloat = 0

        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            if currentX + size.width > maxX && currentX > bounds.minX {
                currentY += currentRowHeight + verticalSpacing
                currentX = bounds.minX
                currentRowHeight = 0
            }
            subview.place(at: CGPoint(x: currentX, y: currentY), proposal: ProposedViewSize(size))
            currentX += size.width + horizontalSpacing
            currentRowHeight = max(currentRowHeight, size.height)
        }
    }
}
