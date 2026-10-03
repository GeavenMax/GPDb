import Foundation
import Kingfisher

public extension String {
    private static let pathRegex: NSRegularExpression? = {
        try? NSRegularExpression(pattern: "(?:images|image_cache)/([^/]+)/([^?#]+)", options: .caseInsensitive)
    }()

    /// 将数据库中存储的 `images/Covers/xxx.jpg` 格式 URL 转换为本地相对路径
    func toImageCachePath() -> String? {
        var trimmed = self.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty { return nil }

        // 统一剥离多余开头斜杠与 URL 编码
        while trimmed.hasPrefix("/") {
            trimmed.removeFirst()
        }
        if let decoded = trimmed.removingPercentEncoding, !decoded.isEmpty {
            trimmed = decoded
        }

        if trimmed.hasPrefix("image_cache/") {
            return trimmed
        }

        let nsString = trimmed as NSString
        let fullRange = NSRange(location: 0, length: nsString.length)
        if let match = Self.pathRegex?.firstMatch(in: trimmed, options: [], range: fullRange),
           match.numberOfRanges >= 3 {
            var folder = nsString.substring(with: match.range(at: 1)).capitalized
            if folder.lowercased() == "banners" {
                folder = "Logos"
            }
            let filename = nsString.substring(with: match.range(at: 2))
            return "image_cache/\(folder)/\(filename)"
        }

        let prefixes = ["Covers/", "Episodes/", "Stars/", "icons/", "logo/", "logos/", "banners/"]
        for p in prefixes {
            if trimmed.lowercased().hasPrefix(p.lowercased()) {
                var folder = p.replacingOccurrences(of: "/", with: "").capitalized
                if folder.lowercased() == "banners" {
                    folder = "Logos"
                }
                let filename = String(trimmed.dropFirst(p.count))
                return "image_cache/\(folder)/\(filename)"
            }
        }

        return nil
    }
}

/// 自定义 Kingfisher ImageDataProvider，支持直接从沙盒 GPDb_Images.zip 内存流解压图片
public struct ZipImageDataProvider: ImageDataProvider {
    public let cacheKey: String
    private let relativeImagePath: String

    public init(zipPath: String, relativeImagePath: String) {
        self.relativeImagePath = relativeImagePath
        self.cacheKey = "gpdb-zip://\(relativeImagePath)"
    }

    public func data(handler: @escaping (Result<Data, Error>) -> Void) {
        ZipArchiveService.shared.extract(path: relativeImagePath) { result in
            handler(result)
        }
    }
}
