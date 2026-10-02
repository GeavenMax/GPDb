import SwiftUI
#if canImport(UIKit)
import UIKit
#endif
import Kingfisher

/// 提取出的图片主导色与次导色调色板
public struct ImagePalette: Equatable, Sendable {
    public let primary: Color
    public let secondary: Color

    public static let fallback = ImagePalette(
        primary: Color(red: 0.20, green: 0.23, blue: 0.32),
        secondary: Color(red: 0.11, green: 0.13, blue: 0.18)
    )

    public init(primary: Color, secondary: Color) {
        self.primary = primary
        self.secondary = secondary
    }
}

/// 高性能图像色彩分析与主色调提取器 (采用极速降采样 + 饱和度/亮度加权算法)
public final class PaletteExtractor: @unchecked Sendable {
    public static let shared = PaletteExtractor()

    private let cache = NSCache<NSString, PaletteCacheEntry>()

    private init() {
        cache.countLimit = 300
    }

    #if canImport(UIKit)
    /// 针对已有的 UIImage 进行同步取色 (内置内存缓存)
    public func extract(from image: UIImage, cacheKey: String? = nil) -> ImagePalette {
        if let key = cacheKey, let cached = cache.object(forKey: key as NSString) {
            return cached.palette
        }

        let palette = processImage(image)
        if let key = cacheKey {
            cache.setObject(PaletteCacheEntry(palette: palette), forKey: key as NSString)
        }
        return palette
    }

    /// 针对原始图片路径 (ZIP/本地磁盘/网络/Kingfisher缓存) 异步提取色彩
    @MainActor
    public func extract(for rawPath: String?, environment: AppEnvironment) async -> ImagePalette {
        guard let rawPath = rawPath?.trimmingCharacters(in: .whitespacesAndNewlines), !rawPath.isEmpty else {
            return .fallback
        }

        let key = rawPath as NSString
        if let cached = cache.object(forKey: key) {
            return cached.palette
        }

        let zipPath = environment.imageZipPath
        let physicalRoot = environment.imagePhysicalRoot

        // 1. 尝试从 Kingfisher 内存缓存秒级读取
        if let relPath = rawPath.toImageCachePath() {
            let zipKey = "gpdb-zip://\(relPath)"
            if let img = ImageCache.default.retrieveImageInMemoryCache(forKey: zipKey) {
                let p = processImage(img)
                cache.setObject(PaletteCacheEntry(palette: p), forKey: key)
                return p
            }
        }
        if let img = ImageCache.default.retrieveImageInMemoryCache(forKey: rawPath) {
            let p = processImage(img)
            cache.setObject(PaletteCacheEntry(palette: p), forKey: key)
            return p
        }

        // 2. 尝试从沙盒 ZIP 归档读取 Data
        if let zip = zipPath, let relPath = rawPath.toImageCachePath() {
            let data: Data? = await withCheckedContinuation { continuation in
                ZipArchiveService.shared.extract(path: relPath) { result in
                    continuation.resume(returning: try? result.get())
                }
            }
            if let data = data, let img = UIImage(data: data) {
                let p = processImage(img)
                cache.setObject(PaletteCacheEntry(palette: p), forKey: key)
                return p
            }
        }

        // 3. 尝试从本地挂载物理目录读取
        if let root = physicalRoot, let relPath = rawPath.toImageCachePath() {
            let fileUrl = URL(fileURLWithPath: root).appendingPathComponent(relPath)
            if let img = UIImage(contentsOfFile: fileUrl.path) {
                let p = processImage(img)
                cache.setObject(PaletteCacheEntry(palette: p), forKey: key)
                return p
            }
        }

        return .fallback
    }

    /// 核心色彩提取分析逻辑：降采样至 16x16，加权计算主色彩相与对比色
    public func processImage(_ image: UIImage) -> ImagePalette {
        let size = CGSize(width: 16, height: 16)
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        let renderer = UIGraphicsImageRenderer(size: size, format: format)

        let smallImage = renderer.image { _ in
            image.draw(in: CGRect(origin: .zero, size: size))
        }

        guard let cgImage = smallImage.cgImage,
              let dataProvider = cgImage.dataProvider,
              let pixelData = dataProvider.data,
              let data = CFDataGetBytePtr(pixelData) else {
            return .fallback
        }

        let width = cgImage.width
        let height = cgImage.height
        let bytesPerPixel = 4
        let bytesPerRow = cgImage.bytesPerRow

        var candidates: [(r: Double, g: Double, b: Double, sat: Double, lum: Double)] = []

        for y in 0..<height {
            for x in 0..<width {
                let offset = y * bytesPerRow + x * bytesPerPixel
                let r = Double(data[offset]) / 255.0
                let g = Double(data[offset + 1]) / 255.0
                let b = Double(data[offset + 2]) / 255.0
                let a = Double(data[offset + 3]) / 255.0

                if a < 0.4 { continue }

                let maxVal = max(r, max(g, b))
                let minVal = min(r, min(g, b))
                let lum = (maxVal + minVal) / 2.0
                let delta = maxVal - minVal
                let sat = (lum > 0 && lum < 1) ? delta / (1.0 - abs(2.0 * lum - 1.0)) : 0.0

                // 过滤极端亮度（近纯黑与近纯白），提取有色彩倾向的像素
                if lum >= 0.10 && lum <= 0.88 {
                    candidates.append((r, g, b, sat, lum))
                }
            }
        }

        guard !candidates.isEmpty else {
            return .fallback
        }

        // 按饱和度降序，优先选取色彩饱满的代表色
        let sorted = candidates.sorted { $0.sat > $1.sat }
        let topPrimary = sorted.first!

        // 次选色寻找与主色有一定色差或次高饱和度的色调
        let secondary = sorted.dropFirst().first { c in
            let dist = abs(c.r - topPrimary.r) + abs(c.g - topPrimary.g) + abs(c.b - topPrimary.b)
            return dist >= 0.35
        } ?? sorted.last ?? topPrimary

        let primaryColor = Color(red: topPrimary.r, green: topPrimary.g, blue: topPrimary.b)
        let secondaryColor = Color(red: secondary.r, green: secondary.g, blue: secondary.b)

        return ImagePalette(primary: primaryColor, secondary: secondaryColor)
    }
    #endif
}

private final class PaletteCacheEntry {
    let palette: ImagePalette
    init(palette: ImagePalette) { self.palette = palette }
}
