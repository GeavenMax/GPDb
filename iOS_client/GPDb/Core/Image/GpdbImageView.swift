import SwiftUI
import Kingfisher

/// 统一的海报、剧照与头像异步加载组件，内置三级缓存与防窥高斯模糊
public struct GpdbImageView: View {
    @EnvironmentObject private var environment: AppEnvironment

    public let rawPath: String?
    public var contentMode: SwiftUI.ContentMode = .fill
    public var cornerRadius: CGFloat = 8
    public var placeholderIcon: String = "photo"
    public var targetSize: CGSize? = CGSize(width: 400, height: 600)
    public var cropAlignment: Alignment = .center

    public init(
        rawPath: String?,
        contentMode: SwiftUI.ContentMode = .fill,
        cornerRadius: CGFloat = 8,
        placeholderIcon: String = "photo",
        targetSize: CGSize? = CGSize(width: 400, height: 600),
        cropAlignment: Alignment? = nil
    ) {
        self.rawPath = rawPath
        self.contentMode = contentMode
        self.cornerRadius = cornerRadius
        self.placeholderIcon = placeholderIcon
        self.targetSize = targetSize
        // 人物头像默认使用头部构图对齐 (.top)，电影海报等默认居中 (.center)
        if let alignment = cropAlignment {
            self.cropAlignment = alignment
        } else if placeholderIcon.contains("person") {
            self.cropAlignment = .top
        } else {
            self.cropAlignment = .center
        }
    }

    public var body: some View {
        ZStack {
            if let path = rawPath, !path.isEmpty {
                if let zipPath = environment.imageZipPath, let relPath = path.toImageCachePath() {
                    // 从 ZIP 极速 O(1) 抽取并硬件级降采样解码
                    buildImage(KFImage.dataProvider(ZipImageDataProvider(zipPath: zipPath, relativeImagePath: relPath)))
                } else if let localRoot = environment.imagePhysicalRoot, let relPath = path.toImageCachePath() {
                    // 从本地挂载目录直接读取
                    let fileUrl = URL(fileURLWithPath: localRoot).appendingPathComponent(relPath)
                    buildImage(KFImage(fileUrl))
                } else if let url = URL(string: path), path.starts(with: "http") {
                    // 网络外链降级
                    buildImage(KFImage(url))
                } else {
                    placeholderView
                }
            } else {
                placeholderView
            }

            // 即时防窥打码与遮罩覆层 (对标 Android 敏感内容高斯打码)
            if environment.isPrivacyModeActive {
                ZStack {
                    Color.black.opacity(0.35)
                    Image(systemName: "eye.slash.fill")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(.white.opacity(0.9))
                        .shadow(color: .black.opacity(0.6), radius: 3)
                }
                .transition(.opacity)
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: cornerRadius))
        .clipped()
        .animation(.easeInOut(duration: 0.2), value: environment.isPrivacyModeActive)
    }

    @ViewBuilder
    private func buildImage(_ kfImage: KFImage) -> some View {
        let base = kfImage
            .setProcessor(targetSize != nil ? DownsamplingImageProcessor(size: targetSize!) : DefaultImageProcessor.default)
            .diskCacheExpiration(.days(30))
            .fade(duration: 0.15)
            .placeholder { placeholderView }
            .resizable()
            .aspectRatio(contentMode: contentMode)
            .blur(radius: environment.isPrivacyModeActive ? 28 : 0)

        GeometryReader { geo in
            base
                .frame(width: geo.size.width, height: geo.size.height, alignment: cropAlignment)
                .clipped()
        }
    }

    private var placeholderView: some View {
        ZStack {
            Color.secondary.opacity(0.15)
            Image(systemName: placeholderIcon)
                .font(.system(size: 24))
                .foregroundStyle(.secondary.opacity(0.6))
        }
    }
}
