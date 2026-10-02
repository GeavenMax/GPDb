import Foundation
import ZIPFoundation

/// ZIP 归档持久化单例与极速 O(1) 随机寻址管理器 (深度对标 Android ZipHolder)
public final class ZipArchiveService: @unchecked Sendable {
    public static let shared = ZipArchiveService()

    private let ioQueue = DispatchQueue(label: "com.gpdb.zip.io", qos: .userInitiated)
    private var archive: Archive?
    private var currentZipPath: String?
    private var entriesMap: [String: Entry] = [:]

    private init() {}

    /// 设置并挂载 ZIP 路径，在专用后台 IO 线程构建 O(1) 索引字典
    public func setZipPath(_ path: String) {
        ioQueue.async { [weak self] in
            guard let self = self else { return }

            if self.currentZipPath == path && self.archive != nil && !self.entriesMap.isEmpty {
                return
            }

            guard FileManager.default.fileExists(atPath: path) else {
                print("[ZipArchiveService] 目标 ZIP 不存在: \(path)")
                return
            }

            print("[ZipArchiveService] 开始挂载并索引 ZIP 归档 (后台并发线程): \(path)")
            let startTime = Date()

            guard let newArchive = try? Archive(url: URL(fileURLWithPath: path), accessMode: .read, pathEncoding: nil) else {
                print("[ZipArchiveService] 打开 ZIP 失败")
                self.archive = nil
                self.currentZipPath = nil
                self.entriesMap.removeAll()
                return
            }

            self.archive = newArchive
            self.currentZipPath = path

            // 一次性遍历 Central Directory，构建 O(1) 快速哈希索引表
            var map = [String: Entry]()
            for entry in newArchive {
                let p = entry.path
                map[p] = entry
                if p.hasPrefix("image_cache/") {
                    let stripped = String(p.dropFirst("image_cache/".count))
                    map[stripped] = entry
                } else {
                    map["image_cache/\(p)"] = entry
                }
            }
            self.entriesMap = map

            let cost = Date().timeIntervalSince(startTime)
            print("[ZipArchiveService] ZIP 挂载完成: \(map.count) 个条目索引，耗时: \(String(format: "%.2f", cost))s")
        }
    }

    /// 极速 O(1) 检查 ZIP 中是否存在目标路径的条目
    public func hasEntry(path: String) -> Bool {
        return ioQueue.sync {
            if entriesMap[path] != nil { return true }
            let stripped = path.replacingOccurrences(of: "image_cache/", with: "")
            if entriesMap[stripped] != nil { return true }
            if let normalized = path.toImageCachePath() {
                if entriesMap[normalized] != nil { return true }
                let normStripped = normalized.replacingOccurrences(of: "image_cache/", with: "")
                if entriesMap[normStripped] != nil { return true }
            }
            return false
        }
    }

    /// O(1) 寻址极速提取文件数据
    public func extract(path: String, completion: @escaping (Result<Data, Error>) -> Void) {
        ioQueue.async { [weak self] in
            guard let self = self else {
                completion(.failure(NSError(domain: "ZipService", code: 500, userInfo: [NSLocalizedDescriptionKey: "ZipService 已销毁"])))
                return
            }

            guard let archive = self.archive else {
                completion(.failure(NSError(domain: "ZipService", code: 404, userInfo: [NSLocalizedDescriptionKey: "ZIP 归档未初始化"])))
                return
            }

            // O(1) 字典查找条目，杜绝任何 O(N) 线性遍历
            var targetEntry = self.entriesMap[path]
            if targetEntry == nil {
                let stripped = path.replacingOccurrences(of: "image_cache/", with: "")
                targetEntry = self.entriesMap[stripped]
            }

            guard let entry = targetEntry else {
                completion(.failure(NSError(domain: "ZipService", code: 404, userInfo: [NSLocalizedDescriptionKey: "ZIP 中未找到目标条目: \(path)"])))
                return
            }

            var data = Data(capacity: max(1024, Int(entry.uncompressedSize)))
            do {
                _ = try archive.extract(entry) { chunk in
                    data.append(chunk)
                }
                completion(.success(data))
            } catch {
                completion(.failure(error))
            }
        }
    }

    /// 异步拉取 ZIP 内文件数据
    public func extract(path: String) async throws -> Data {
        try await withCheckedThrowingContinuation { continuation in
            self.extract(path: path) { result in
                continuation.resume(with: result)
            }
        }
    }

    /// 释放 ZIP 句柄与内存索引
    public func release() {
        ioQueue.async { [weak self] in
            self?.archive = nil
            self?.currentZipPath = nil
            self?.entriesMap.removeAll()
            print("[ZipArchiveService] 已释放 ZIP 资源与索引")
        }
    }
}
