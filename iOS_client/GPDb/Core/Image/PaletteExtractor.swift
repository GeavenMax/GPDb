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

/// 高性能图像色彩分析与主色调提取器 (采用直接位图硬件采样 + 饱和度与明度双重加权)
public final class PaletteExtractor: @unchecked Sendable {
    public static let shared = PaletteExtractor()
    public static let didExtractPaletteNotification = Notification.Name("GPDbPaletteExtractorDidExtract")

    private let cache = NSCache<NSString, PaletteCacheEntry>()

    private init() {
        cache.countLimit = 300
    }

    #if canImport(UIKit)
    /// 注册并缓存已知图片的色彩，并广播通知
    public func register(image: UIImage, for rawPath: String) {
        let key = rawPath as NSString
        let palette = processImage(image)
        cache.setObject(PaletteCacheEntry(palette: palette), forKey: key)
        DispatchQueue.main.async {
            NotificationCenter.default.post(
                name: PaletteExtractor.didExtractPaletteNotification,
                object: rawPath,
                userInfo: ["palette": palette]
            )
        }
    }

    /// 获取内存中已提取的色彩 (如有)
    public func cachedPalette(for rawPath: String) -> ImagePalette? {
        return cache.object(forKey: rawPath as NSString)?.palette
    }

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
        if zipPath != nil, let relPath = rawPath.toImageCachePath() {
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

    /// 核心色彩提取分析逻辑：通过确定性 CGBitmapContext 读取 24x24 采样像素，加权计算主导生动色相与对比色
    public func processImage(_ image: UIImage) -> ImagePalette {
        let width = 24
        let height = 24
        var rawData = [UInt8](repeating: 0, count: width * height * 4)
        let colorSpace = CGColorSpaceCreateDeviceRGB()
        let bitmapInfo = CGImageAlphaInfo.premultipliedLast.rawValue | CGBitmapInfo.byteOrder32Big.rawValue

        guard let context = CGContext(
            data: &rawData,
            width: width,
            height: height,
            bitsPerComponent: 8,
            bytesPerRow: width * 4,
            space: colorSpace,
            bitmapInfo: bitmapInfo
        ) else {
            return .fallback
        }

        context.interpolationQuality = .medium
        if let cgImage = image.cgImage {
            context.draw(cgImage, in: CGRect(x: 0, y: 0, width: width, height: height))
        } else {
            UIGraphicsPushContext(context)
            image.draw(in: CGRect(x: 0, y: 0, width: width, height: height))
            UIGraphicsPopContext()
        }

        var candidates: [(r: Double, g: Double, b: Double, sat: Double, lum: Double, score: Double)] = []

        for y in 0..<height {
            for x in 0..<width {
                let offset = (y * width + x) * 4
                let r = Double(rawData[offset]) / 255.0
                let g = Double(rawData[offset + 1]) / 255.0
                let b = Double(rawData[offset + 2]) / 255.0
                let a = Double(rawData[offset + 3]) / 255.0

                if a < 0.35 { continue }

                let maxVal = max(r, max(g, b))
                let minVal = min(r, min(g, b))
                let lum = (maxVal + minVal) / 2.0
                let delta = maxVal - minVal
                let sat = (lum > 0 && lum < 1) ? delta / (1.0 - abs(2.0 * lum - 1.0)) : 0.0

                // 排除近黑 (lum < 0.08) 和 近白 (lum > 0.90) 以及纯无彩度灰色 (sat < 0.06)
                if lum >= 0.08 && lum <= 0.90 && sat >= 0.06 {
                    // 得分算法：饱和度与适度明度加权，优先提取鲜明有氛围感的色调
                    let lumFactor = 1.0 - abs(lum - 0.52) * 1.6
                    let score = sat * 2.2 + max(0, lumFactor)
                    candidates.append((r, g, b, sat, lum, score))
                }
            }
        }

        guard !candidates.isEmpty else {
            return .fallback
        }

        // 按色彩得分排序，优先选取色彩饱满且明度舒适的代表色
        let sorted = candidates.sorted { $0.score > $1.score }
        let top = sorted.first!

        // 次选色寻找与主色有足够色差（不同色调或对比色）的候选项
        let secondary = sorted.dropFirst().first { c in
            let dist = abs(c.r - top.r) + abs(c.g - top.g) + abs(c.b - top.b)
            return dist >= 0.32 && c.sat >= 0.12
        } ?? sorted.last ?? top

        // 适度增强色彩生动度 (vibrancy)，呈现更透亮、更具有氛围感的渐变
        let primaryColor = boostVibrancy(r: top.r, g: top.g, b: top.b)
        let secondaryColor = boostVibrancy(r: secondary.r, g: secondary.g, b: secondary.b)

        return ImagePalette(primary: primaryColor, secondary: secondaryColor)
    }

    private func boostVibrancy(r: Double, g: Double, b: Double) -> Color {
        let maxVal = max(r, max(g, b))
        let minVal = min(r, min(g, b))
        let lum = (maxVal + minVal) / 2.0
        if maxVal == minVal {
            return Color(red: r, green: g, blue: b)
        }
        let factor = 1.22
        let rBoost = min(1.0, max(0.0, lum + (r - lum) * factor))
        let gBoost = min(1.0, max(0.0, lum + (g - lum) * factor))
        let bBoost = min(1.0, max(0.0, lum + (b - lum) * factor))
        return Color(red: rBoost, green: gBoost, blue: bBoost)
    }
    #endif
}

private final class PaletteCacheEntry {
    let palette: ImagePalette
    init(palette: ImagePalette) { self.palette = palette }
}
