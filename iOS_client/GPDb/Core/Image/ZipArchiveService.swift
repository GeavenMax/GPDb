import Foundation
import ZIPFoundation

/// ZIP 归档持久化单例与极速 O(1) 随机寻址管理器 (深度对标 Android ZipHolder)
public final class ZipArchiveService: @unchecked Sendable {
    public static let shared = ZipArchiveService()

    private let lock = NSLock()
    private let ioQueue = DispatchQueue(label: "com.gpdb.zip.io", qos: .userInitiated)

    private var archive: Archive?
    private var currentZipPath: String?
    private var entriesMap: [String: Entry] = [:]
    
    private var _isIndexed: Bool = false
    private var _isIndexing: Bool = false

    public var isIndexed: Bool {
        lock.lock()
        defer { lock.unlock() }
        return _isIndexed
    }

    public var isIndexing: Bool {
        lock.lock()
        defer { lock.unlock() }
        return _isIndexing
    }

    private init() {}

    /// 设置并挂载 ZIP 路径，在后台工具线程构建 O(1) 索引字典，零阻塞冷启动与主线程
    public func setZipPath(_ path: String) {
        lock.lock()
        if self.currentZipPath == path && self.archive != nil && self._isIndexed {
            lock.unlock()
            return
        }
        guard FileManager.default.fileExists(atPath: path) else {
            print("[ZipArchiveService] 目标 ZIP 不存在: \(path)")
            lock.unlock()
            return
        }
        self.currentZipPath = path
        self._isIndexing = true
        self._isIndexed = false
        lock.unlock()

        print("[ZipArchiveService] 开始挂载并索引 ZIP 归档 (后台并发线程): \(path)")
        let startTime = Date()

        DispatchQueue.global(qos: .utility).async { [weak self] in
            guard let self = self else { return }

            guard let newArchive = try? Archive(url: URL(fileURLWithPath: path), accessMode: .read, pathEncoding: nil) else {
                print("[ZipArchiveService] 打开 ZIP 失败")
                self.lock.lock()
                self.archive = nil
                self.currentZipPath = nil
                self.entriesMap.removeAll()
                self._isIndexing = false
                self._isIndexed = false
                self.lock.unlock()
                return
            }

            self.lock.lock()
            self.archive = newArchive
            self.lock.unlock()

            // 后台遍历 Central Directory 构建 O(1) 索引表，完全不占用主线程与 IO 锁
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

            self.lock.lock()
            self.entriesMap = map
            self._isIndexed = true
            self._isIndexing = false
            self.lock.unlock()

            let cost = Date().timeIntervalSince(startTime)
            print("[ZipArchiveService] ZIP 挂载完成: \(map.count) 个条目索引，耗时: \(String(format: "%.2f", cost))s")
        }
    }

    /// 极速 O(1) 检查 ZIP 中是否存在目标路径的条目 (微秒级无锁/短锁判定，杜绝任何阻塞)
    public func hasEntry(path: String) -> Bool {
        lock.lock()
        defer { lock.unlock() }

        // 如果仍在后台索引且字典尚未构建完成，乐观返回 true，防止冷启动时阻塞并误过滤头像/剧照
        if !_isIndexed && _isIndexing {
            return true
        }

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

    /// O(1) 寻址极速提取文件数据
    public func extract(path: String, completion: @escaping (Result<Data, Error>) -> Void) {
        lock.lock()
        guard let archive = self.archive else {
            lock.unlock()
            completion(.failure(NSError(domain: "ZipService", code: 404, userInfo: [NSLocalizedDescriptionKey: "ZIP 归档未初始化"])))
            return
        }

        var targetEntry = self.entriesMap[path]
        if targetEntry == nil {
            let stripped = path.replacingOccurrences(of: "image_cache/", with: "")
            targetEntry = self.entriesMap[stripped]
        }
        
        // 若尚未完成全量字典构建，尝试单条检索
        if targetEntry == nil && !_isIndexed {
            targetEntry = archive[path] ?? archive["image_cache/\(path)"]
        }
        lock.unlock()

        guard let entry = targetEntry else {
            completion(.failure(NSError(domain: "ZipService", code: 404, userInfo: [NSLocalizedDescriptionKey: "ZIP 中未找到目标条目: \(path)"])))
            return
        }

        ioQueue.async {
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
        lock.lock()
        self.archive = nil
        self.currentZipPath = nil
        self.entriesMap.removeAll()
        self._isIndexed = false
        self._isIndexing = false
        lock.unlock()
        print("[ZipArchiveService] 已释放 ZIP 资源与索引")
    }
}
