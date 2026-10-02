import SwiftUI

/// 动态图片取色自适应氛围渐变背景组件
public struct DynamicAmbientBackground: View {
    @EnvironmentObject private var environment: AppEnvironment

    public let imagePath: String?
    public var gradientHeight: CGFloat
    public var blurRadius: CGFloat
    public var glowOpacity: Double

    @State private var palette: ImagePalette? = nil

    public init(
        imagePath: String?,
        gradientHeight: CGFloat = 480,
        blurRadius: CGFloat = 65,
        glowOpacity: Double = 0.42
    ) {
        self.imagePath = imagePath
        self.gradientHeight = gradientHeight
        self.blurRadius = blurRadius
        self.glowOpacity = glowOpacity
    }

    public var body: some View {
        ZStack(alignment: .top) {
            // 1. 底层系统默认背景色 (自适应浅色/深色外观)
            #if canImport(UIKit)
            Color(uiColor: .systemBackground)
                .ignoresSafeArea()
            #else
            Color.black
                .ignoresSafeArea()
            #endif

            // 2. 动态氛围虚化海报漫反射层 (超大高斯虚化投射)
            if let path = imagePath, !path.isEmpty {
                GpdbImageView(
                    rawPath: path,
                    contentMode: .fill,
                    cornerRadius: 0,
                    targetSize: CGSize(width: 100, height: 140)
                )
                .frame(maxWidth: .infinity)
                .frame(height: gradientHeight * 0.75)
                .blur(radius: blurRadius)
                .opacity(glowOpacity)
                .clipped()
                .mask(
                    LinearGradient(
                        stops: [
                            .init(color: .black.opacity(0.95), location: 0.0),
                            .init(color: .black.opacity(0.40), location: 0.50),
                            .init(color: .clear, location: 1.0)
                        ],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                )
                .ignoresSafeArea(edges: .top)
            }

            // 3. 动态图片提取色彩的渐变晕染层 (真实取色高透亮渐变)
            if let pal = palette {
                LinearGradient(
                    stops: [
                        .init(color: pal.primary.opacity(0.55), location: 0.0),
                        .init(color: pal.secondary.opacity(0.30), location: 0.38),
                        .init(color: pal.secondary.opacity(0.10), location: 0.70),
                        .init(color: Color.clear, location: 1.0)
                    ],
                    startPoint: .top,
                    endPoint: .bottom
                )
                .frame(height: gradientHeight)
                .ignoresSafeArea(edges: .top)
                .transition(.opacity)
            }
        }
        .task(id: imagePath) {
            #if canImport(UIKit)
            if let path = imagePath, let cached = PaletteExtractor.shared.cachedPalette(for: path) {
                self.palette = cached
            }
            let extracted = await PaletteExtractor.shared.extract(for: imagePath, environment: environment)
            withAnimation(.easeInOut(duration: 0.35)) {
                self.palette = extracted
            }
            #endif
        }
        .onReceive(NotificationCenter.default.publisher(for: PaletteExtractor.didExtractPaletteNotification)) { notification in
            #if canImport(UIKit)
            if let path = notification.object as? String, path == imagePath,
               let cached = PaletteExtractor.shared.cachedPalette(for: path) {
                withAnimation(.easeInOut(duration: 0.35)) {
                    self.palette = cached
                }
            }
            #endif
        }
    }
}

public extension View {
    /// 赋予页面从目标图片动态取色的自适应氛围渐变背景
    func dynamicAmbientBackground(imagePath: String?, gradientHeight: CGFloat = 480) -> some View {
        self.background(
            DynamicAmbientBackground(imagePath: imagePath, gradientHeight: gradientHeight)
        )
    }
}
