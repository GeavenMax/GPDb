import SwiftUI
import Kingfisher

#if canImport(UIKit)
import UIKit
public typealias PlatformImage = UIImage
#elseif canImport(AppKit)
import AppKit
public typealias PlatformImage = NSImage
#endif

public struct PlatformImageView: View {
    public let image: PlatformImage

    public init(image: PlatformImage) {
        self.image = image
    }

    public var body: some View {
        #if canImport(UIKit)
        Image(uiImage: image)
            .resizable()
        #elseif canImport(AppKit)
        Image(nsImage: image)
            .resizable()
        #endif
    }
}

public struct ShareCardData {
    public let title: String
    public let titleAlt: String?
    public let isEpisode: Bool
    public let year: Int?
    public let duration: String?
    public let studio: String?
    public let director: String?
    public let performers: [String]
    public let synopsis: String?
    public let posterImage: PlatformImage?
    public let backCoverImage: PlatformImage?

    public init(
        title: String,
        titleAlt: String? = nil,
        isEpisode: Bool = false,
        year: Int? = nil,
        duration: String? = nil,
        studio: String? = nil,
        director: String? = nil,
        performers: [String] = [],
        synopsis: String? = nil,
        posterImage: PlatformImage? = nil,
        backCoverImage: PlatformImage? = nil
    ) {
        self.title = title
        self.titleAlt = titleAlt
        self.isEpisode = isEpisode
        self.year = year
        self.duration = duration
        self.studio = studio
        self.director = director
        self.performers = performers
        self.synopsis = synopsis
        self.posterImage = posterImage
        self.backCoverImage = backCoverImage
    }
}

public struct ShareCardView: View {
    public let data: ShareCardData
    public var blurPoster: Bool = false
    public var blurText: Bool = false

    public init(data: ShareCardData, blurPoster: Bool = false, blurText: Bool = false) {
        self.data = data
        self.blurPoster = blurPoster
        self.blurText = blurText
    }

    public var body: some View {
        VStack(spacing: 16) {
            // 核心海报/剧照框 (电影双面封面/单面，分集 16:9 居中无畸变)
            if !data.isEpisode, let backImg = data.backCoverImage {
                HStack(spacing: 10) {
                    // 封面 (Front Cover)
                    ZStack(alignment: .topLeading) {
                        if let img = data.posterImage {
                            PlatformImageView(image: img)
                                .scaledToFill()
                                .blur(radius: blurPoster ? 16 : 0)
                        } else {
                            Color.black.opacity(0.3)
                        }
                        if !blurPoster {
                            Text("封面")
                                .font(.system(size: 9, weight: .bold))
                                .foregroundStyle(.white)
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(Color.black.opacity(0.6), in: RoundedRectangle(cornerRadius: 4))
                                .padding(6)
                        } else {
                            Color.black.opacity(0.4)
                            Label("已脱敏", systemImage: "lock.fill")
                                .font(.caption2.bold())
                                .foregroundStyle(.white)
                                .frame(maxWidth: .infinity, maxHeight: .infinity)
                        }
                    }
                    .frame(width: 155, height: 215)
                    .clipped()
                    .clipShape(RoundedRectangle(cornerRadius: 14))
                    .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color.white.opacity(0.2), lineWidth: 1))

                    // 封底 (Back Cover)
                    ZStack(alignment: .topLeading) {
                        PlatformImageView(image: backImg)
                            .scaledToFill()
                            .blur(radius: blurPoster ? 16 : 0)
                        if !blurPoster {
                            Text("封底")
                                .font(.system(size: 9, weight: .bold))
                                .foregroundStyle(.white)
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(Color.black.opacity(0.6), in: RoundedRectangle(cornerRadius: 4))
                                .padding(6)
                        } else {
                            Color.black.opacity(0.4)
                            Label("已脱敏", systemImage: "lock.fill")
                                .font(.caption2.bold())
                                .foregroundStyle(.white)
                                .frame(maxWidth: .infinity, maxHeight: .infinity)
                        }
                    }
                    .frame(width: 155, height: 215)
                    .clipped()
                    .clipShape(RoundedRectangle(cornerRadius: 14))
                    .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color.white.opacity(0.2), lineWidth: 1))
                }
            } else {
                ZStack {
                    if let img = data.posterImage {
                        PlatformImageView(image: img)
                            .scaledToFill()
                            .blur(radius: blurPoster ? 16 : 0)
                    } else {
                        Color.black.opacity(0.3)
                        Image(systemName: "film")
                            .font(.system(size: 40))
                            .foregroundStyle(.white.opacity(0.3))
                    }

                    if blurPoster {
                        Color.black.opacity(0.4)
                        Label("封面已防窥脱敏", systemImage: "lock.fill")
                            .font(.caption.bold())
                            .foregroundStyle(.white)
                    }
                }
                .frame(width: 320, height: data.isEpisode ? 180 : 420)
                .clipped()
                .clipShape(RoundedRectangle(cornerRadius: 16))
                .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.white.opacity(0.2), lineWidth: 1))
            }

            // 标题与元数据
            VStack(spacing: 4) {
                Text(data.title)
                    .font(.headline.bold())
                    .foregroundStyle(.white)
                    .multilineTextAlignment(.center)

                if let alt = data.titleAlt {
                    Text(alt)
                        .font(.caption)
                        .foregroundStyle(.white.opacity(0.7))
                }

                HStack(spacing: 8) {
                    if let y = data.year { Text("\(y)年") }
                    if let d = data.duration { Text(d) }
                    if let s = data.studio { Text(s) }
                }
                .font(.caption2)
                .foregroundStyle(.white.opacity(0.8))
                .padding(.top, 2)

                if let dir = data.director {
                    Text("🎬 导演: \(dir)")
                        .font(.caption2.bold())
                        .foregroundStyle(Color.amber.opacity(0.9))
                }

                // 演职员全量自适应展示
                if !data.performers.isEmpty {
                    Text("主演: " + data.performers.joined(separator: "  "))
                        .font(.caption2)
                        .foregroundStyle(.white.opacity(0.75))
                        .multilineTextAlignment(.center)
                        .padding(.top, 2)
                }
            }
            .padding(.horizontal)

            // 剧情简介 (无截断)
            if let synopsis = data.synopsis, !synopsis.isEmpty {
                ZStack {
                    Text(synopsis)
                        .font(.caption)
                        .foregroundStyle(.white.opacity(0.85))
                        .lineSpacing(4)
                        .blur(radius: blurText ? 6 : 0)

                    if blurText {
                        Color.black.opacity(0.4)
                        Label("剧情已安全脱敏", systemImage: "lock.fill")
                            .font(.caption2.bold())
                            .foregroundStyle(.white)
                    }
                }
                .padding(12)
                .background(.white.opacity(0.08), in: RoundedRectangle(cornerRadius: 12))
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.white.opacity(0.12), lineWidth: 1))
                .padding(.horizontal)
            }

            // 底部水印与 Telegram 官方频道二维码
            Divider()
                .background(Color.white.opacity(0.15))
                .padding(.horizontal)
                .padding(.top, 4)

            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("GPDb · 个人离线数字影库")
                        .font(.caption2.bold())
                        .foregroundStyle(.white.opacity(0.85))
                    Text("本地私有数字档案")
                        .font(.system(size: 9))
                        .foregroundStyle(.white.opacity(0.5))
                    Text("📢 官方频道: t.me/gpdbnews")
                        .font(.system(size: 9, weight: .medium))
                        .foregroundStyle(Color(red: 0.98, green: 0.75, blue: 0.2))
                }
                Spacer()
                TelegramQrCodeView()
                    .frame(width: 44, height: 44)
            }
            .padding(.horizontal)
        }
        .padding(.vertical, 24)
        .frame(width: 360)
        .background(
            Group {
                #if canImport(UIKit)
                if let img = data.posterImage {
                    let pal = PaletteExtractor.shared.extract(from: img, cacheKey: data.title)
                    LinearGradient(
                        colors: [
                            pal.primary.opacity(0.48),
                            pal.secondary.opacity(0.24),
                            Color(red: 0.05, green: 0.05, blue: 0.08)
                        ],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                } else {
                    LinearGradient(
                        colors: [Color(red: 0.1, green: 0.12, blue: 0.2), Color(red: 0.05, green: 0.05, blue: 0.08)],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                }
                #else
                LinearGradient(
                    colors: [Color(red: 0.1, green: 0.12, blue: 0.2), Color(red: 0.05, green: 0.05, blue: 0.08)],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
                #endif
            }
        )
        .clipShape(RoundedRectangle(cornerRadius: 24))
        .overlay(RoundedRectangle(cornerRadius: 24).stroke(Color.white.opacity(0.15), lineWidth: 1))
    }

    /// 使用 SwiftUI ImageRenderer 导出高保真图片
    @MainActor
    public func renderToImage() -> PlatformImage? {
        let renderer = ImageRenderer(content: self)
        renderer.scale = 3.0 // 3x Retina 导出
        #if canImport(UIKit)
        return renderer.uiImage
        #elseif canImport(AppKit)
        return renderer.nsImage
        #endif
    }
}

public struct TelegramQrCodeView: View {
    private static let matrix: [String] = [
        "000000000000000000000000000",
        "011111110111100100011111110",
        "010000010101000000010000010",
        "010111010111001001010111010",
        "010111010001001001010111010",
        "010111010101100101010111010",
        "010000010000100101010000010",
        "011111110101010101011111110",
        "000000000010101001000000000",
        "010011111101100111100101110",
        "011101101010000010000111100",
        "010010110110101010000110010",
        "010101100101110101101011110",
        "000010110110010010010000010",
        "010111101010001011000100100",
        "011000110000110010100111110",
        "010011101000100010111011010",
        "010010011001000111111101100",
        "000000000101111001000101100",
        "011111110100100001010100010",
        "010000010111010111000100110",
        "010111010111011111111100110",
        "010111010110000110110000110",
        "010111010001010101100111110",
        "010000010011000100011101110",
        "011111110110001111010010010",
        "000000000000000000000000000"
    ]

    public init() {}

    public var body: some View {
        Canvas { context, size in
            let modSize = size.width / 27.0
            for r in 0..<27 {
                let row = Array(Self.matrix[r])
                for c in 0..<27 {
                    if row[c] == "1" {
                        let rect = CGRect(x: CGFloat(c) * modSize, y: CGFloat(r) * modSize, width: modSize + 0.1, height: modSize + 0.1)
                        context.fill(Path(rect), with: .color(.black))
                    }
                }
            }
        }
        .padding(3)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 6))
    }
}

// MARK: - 异步图片解析器

@MainActor
public final class ShareCardImageLoader {
    public static func loadImage(rawPath: String?, env: AppEnvironment) async -> PlatformImage? {
        guard let path = rawPath, !path.isEmpty else { return nil }

        // 1. 尝试从本地 ZIP 归档或挂载目录提取
        if let relPath = path.toImageCachePath() {
            if env.imageZipPath != nil {
                if let data = try? await ZipArchiveService.shared.extract(path: relPath),
                   let img = PlatformImage(data: data) {
                    return img
                }
            } else if let localRoot = env.imagePhysicalRoot {
                let fileUrl = URL(fileURLWithPath: localRoot).appendingPathComponent(relPath)
                if let img = PlatformImage(contentsOfFile: fileUrl.path) {
                    return img
                }
            }
        }

        // 2. 网络外链或直接路径兜底
        if path.starts(with: "http"), let url = URL(string: path) {
            if let (data, _) = try? await URLSession.shared.data(from: url),
               let img = PlatformImage(data: data) {
                return img
            }
        }

        return nil
    }
}

#if canImport(UIKit)
public struct ActivityShareSheet: UIViewControllerRepresentable {
    public let items: [Any]

    public func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }

    public func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}

private class ImageSaver: NSObject {
    var onSuccess: (() -> Void)?
    var onError: ((Error) -> Void)?

    func save(_ image: UIImage) {
        UIImageWriteToSavedPhotosAlbum(image, self, #selector(saveCompleted(_:didFinishSavingWithError:contextInfo:)), nil)
    }

    @objc func saveCompleted(_ image: UIImage, didFinishSavingWithError error: Error?, contextInfo: UnsafeRawPointer) {
        if let error = error {
            onError?(error)
        } else {
            onSuccess?()
        }
    }
}
#endif

// MARK: - 统一流光分享模态弹窗

public struct ShareCardModalView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var environment: AppEnvironment

    public let title: String
    public let titleAlt: String?
    public let isEpisode: Bool
    public let year: Int?
    public let duration: String?
    public let studio: String?
    public let director: String?
    public let performers: [String]
    public let synopsis: String?
    public let posterPath: String?
    public let backCoverPath: String?

    @State private var posterImage: PlatformImage? = nil
    @State private var backCoverImage: PlatformImage? = nil
    @State private var isLoadingImages: Bool = true

    @State private var blurPoster: Bool = false
    @State private var blurText: Bool = false
    @State private var showShareSheet: Bool = false
    @State private var renderedShareImage: PlatformImage? = nil
    @State private var toastMessage: String? = nil

    public init(
        title: String,
        titleAlt: String? = nil,
        isEpisode: Bool = false,
        year: Int? = nil,
        duration: String? = nil,
        studio: String? = nil,
        director: String? = nil,
        performers: [String] = [],
        synopsis: String? = nil,
        posterPath: String? = nil,
        backCoverPath: String? = nil
    ) {
        self.title = title
        self.titleAlt = titleAlt
        self.isEpisode = isEpisode
        self.year = year
        self.duration = duration
        self.studio = studio
        self.director = director
        self.performers = performers
        self.synopsis = synopsis
        self.posterPath = posterPath
        self.backCoverPath = backCoverPath
    }

    private var currentData: ShareCardData {
        ShareCardData(
            title: title,
            titleAlt: titleAlt,
            isEpisode: isEpisode,
            year: year,
            duration: duration,
            studio: studio,
            director: director,
            performers: performers,
            synopsis: synopsis,
            posterImage: posterImage,
            backCoverImage: backCoverImage
        )
    }

    public var body: some View {
        NavigationStack {
            ZStack {
                #if canImport(UIKit)
                Color(uiColor: .systemGroupedBackground).ignoresSafeArea()
                #else
                Color.gray.opacity(0.1).ignoresSafeArea()
                #endif

                ScrollView {
                    VStack(spacing: 20) {
                        // 1. 卡片实时预览
                        ZStack {
                            ShareCardView(data: currentData, blurPoster: blurPoster, blurText: blurText)
                                .shadow(color: .black.opacity(0.18), radius: 16, x: 0, y: 8)

                            if isLoadingImages {
                                RoundedRectangle(cornerRadius: 24)
                                    .fill(.black.opacity(0.45))
                                    .frame(width: 360)
                                    .overlay(
                                        VStack(spacing: 10) {
                                            ProgressView()
                                                .tint(.white)
                                            Text("正在从离线库载入高清画报...")
                                                .font(.caption2.bold())
                                                .foregroundStyle(.white)
                                        }
                                    )
                            }
                        }
                        .padding(.top, 12)

                        // 2. 脱敏防窥控制组
                        VStack(spacing: 12) {
                            HStack {
                                Label("安全脱敏设置", systemImage: "hand.raised.fill")
                                    .font(.subheadline.bold())
                                    .foregroundStyle(.secondary)
                                Spacer()
                            }

                            HStack(spacing: 12) {
                                Toggle(isOn: $blurPoster) {
                                    Label("海报防窥", systemImage: "photo.badge.checkmark")
                                        .font(.caption.bold())
                                }
                                .toggleStyle(.button)
                                .tint(.indigo)

                                Toggle(isOn: $blurText) {
                                    Label("文字脱敏", systemImage: "text.badge.checkmark")
                                        .font(.caption.bold())
                                }
                                .toggleStyle(.button)
                                .tint(.indigo)
                            }
                        }
                        .padding()
                        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16))
                        .padding(.horizontal)

                        // 3. 操作按钮：保存到相册 & 原生系统分享
                        HStack(spacing: 16) {
                            Button {
                                saveCardToPhotos()
                            } label: {
                                Label("保存到相册", systemImage: "arrow.down.to.line.circle.fill")
                                    .font(.headline)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 14)
                                    .background(Color.accentColor, in: RoundedRectangle(cornerRadius: 14))
                                    .foregroundStyle(.white)
                            }

                            Button {
                                shareViaSystem()
                            } label: {
                                Label("系统分享", systemImage: "square.and.arrow.up.circle.fill")
                                    .font(.headline)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 14)
                                    .background(Color.secondary.opacity(0.15), in: RoundedRectangle(cornerRadius: 14))
                                    .foregroundStyle(.primary)
                            }
                        }
                        .padding(.horizontal)
                        .padding(.bottom, 30)
                    }
                }

                // 成功 Toast 弹窗
                if let msg = toastMessage {
                    VStack {
                        Spacer()
                        HStack(spacing: 8) {
                            Image(systemName: "checkmark.circle.fill")
                                .foregroundStyle(.green)
                            Text(msg)
                                .font(.subheadline.bold())
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 10)
                        .background(.ultraThinMaterial, in: Capsule())
                        .shadow(radius: 8)
                        .padding(.bottom, 24)
                    }
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                }
            }
            .navigationTitle("流光分享卡片")
            .inlineNavigationTitle()
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("关闭") { dismiss() }
                }
            }
            .task {
                await loadImages()
            }
            #if canImport(UIKit)
            .sheet(isPresented: $showShareSheet) {
                if let img = renderedShareImage {
                    ActivityShareSheet(items: [img])
                }
            }
            #endif
        }
    }

    private func loadImages() async {
        isLoadingImages = true
        async let p = ShareCardImageLoader.loadImage(rawPath: posterPath, env: environment)
        async let b = ShareCardImageLoader.loadImage(rawPath: backCoverPath, env: environment)
        let (post, back) = await (p, b)
        self.posterImage = post
        self.backCoverImage = back
        self.isLoadingImages = false
    }

    private func saveCardToPhotos() {
        let card = ShareCardView(data: currentData, blurPoster: blurPoster, blurText: blurText)
        guard let img = card.renderToImage() else { return }

        #if canImport(UIKit)
        let saver = ImageSaver()
        saver.onSuccess = {
            UINotificationFeedbackGenerator().notificationOccurred(.success)
            withAnimation {
                self.toastMessage = "已成功保存到系统相册"
            }
            DispatchQueue.main.asyncAfter(deadline: .now() + 2.5) {
                withAnimation { self.toastMessage = nil }
            }
        }
        saver.onError = { err in
            UINotificationFeedbackGenerator().notificationOccurred(.error)
            withAnimation {
                self.toastMessage = "保存失败: \(err.localizedDescription)"
            }
            DispatchQueue.main.asyncAfter(deadline: .now() + 2.5) {
                withAnimation { self.toastMessage = nil }
            }
        }
        saver.save(img)
        #endif
    }

    private func shareViaSystem() {
        let card = ShareCardView(data: currentData, blurPoster: blurPoster, blurText: blurText)
        guard let img = card.renderToImage() else { return }
        self.renderedShareImage = img
        self.showShareSheet = true
    }
}
