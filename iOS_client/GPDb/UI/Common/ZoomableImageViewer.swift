import SwiftUI

/// 全屏手势缩放与拖拽画廊灯箱 (对标 Android ZoomableImageDialog)
public struct ZoomableImageViewer: View {
    public let imagePath: String?
    public let onDismiss: () -> Void

    @State private var scale: CGFloat = 1.0
    @State private var lastScale: CGFloat = 1.0
    @State private var offset: CGSize = .zero
    @State private var lastOffset: CGSize = .zero

    public init(imagePath: String?, onDismiss: @escaping () -> Void) {
        self.imagePath = imagePath
        self.onDismiss = onDismiss
    }

    public var body: some View {
        ZStack {
            Color.black.opacity(0.92)
                .ignoresSafeArea()
                .onTapGesture {
                    onDismiss()
                }

            GpdbImageView(rawPath: imagePath, contentMode: .fit, cornerRadius: 0, targetSize: nil)
                .scaleEffect(scale)
                .offset(offset)
                .gesture(
                    SimultaneousGesture(
                        MagnificationGesture()
                            .onChanged { value in
                                scale = lastScale * value
                            }
                            .onEnded { _ in
                                if scale < 1.0 {
                                    scale = 1.0
                                    offset = .zero
                                } else if scale > 4.0 {
                                    scale = 4.0
                                }
                                lastScale = scale
                            },
                        DragGesture()
                            .onChanged { value in
                                if scale > 1.0 {
                                    offset = CGSize(
                                        width: lastOffset.width + value.translation.width,
                                        height: lastOffset.height + value.translation.height
                                    )
                                }
                            }
                            .onEnded { _ in
                                lastOffset = offset
                            }
                    )
                )
                .onTapGesture(count: 2) {
                    withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) {
                        if scale > 1.0 {
                            scale = 1.0
                            offset = .zero
                        } else {
                            scale = 2.5
                        }
                        lastScale = scale
                        lastOffset = offset
                    }
                }

            // 顶部关闭按钮
            VStack {
                HStack {
                    Spacer()
                    Button {
                        onDismiss()
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .font(.system(size: 30))
                            .foregroundStyle(.white.opacity(0.85))
                            .padding()
                    }
                }
                Spacer()
            }
        }
        .transition(.opacity)
    }
}
