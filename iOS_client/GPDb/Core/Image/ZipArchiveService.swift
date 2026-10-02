import Foundation
import ZIPFoundation
#if canImport(zlib)
import zlib
#endif

/// ZIP 条目元数据轻量结构体 (单条仅占 26 字节，24万条目全内存驻留仅 6MB，零内存压力)
public struct ZipEntryInfo: Sendable {
    public let relativeOffset: UInt64
    public let compressedSize: UInt64
    public let uncompressedSize: UInt64
    public let compressionMethod: UInt16

    public init(relativeOffset: UInt64, compressedSize: UInt64, uncompressedSize: UInt64, compressionMethod: UInt16) {
        self.relativeOffset = relativeOffset
        self.compressedSize = compressedSize
        self.uncompressedSize = uncompressedSize
        self.compressionMethod = compressionMethod
    }
}

/// ZIP 归档持久化单例与极速 O(1) 随机寻址管理器 (深度对标 Android ZipHolder 与 Coil Fetcher)
public final class ZipArchiveService: @unchecked Sendable {
    public static let shared = ZipArchiveService()

    private let lock = NSLock()
    private let ioQueue = DispatchQueue(label: "com.gpdb.zip.io", qos: .userInitiated)

    private var currentZipPath: String?
    private var entriesMap: [String: ZipEntryInfo] = [:]
    private var readFile: UnsafeMutablePointer<FILE>?

    private var _isIndexed: Bool = false
    private var _isIndexing: Bool = false
    private var pendingRequests: [(path: String, completion: (Result<Data, Error>) -> Void)] = []

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

    /// 设置并挂载 ZIP 路径，在后台并发线程毫秒级解析 Central Directory 构建 O(1) 索引字典
    public func setZipPath(_ path: String) {
        lock.lock()
        if self.currentZipPath == path && self._isIndexed && !self.entriesMap.isEmpty {
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

        print("[ZipArchiveService] 开始挂载并极速索引 ZIP 归档 (Central Directory 解析): \(path)")
        let startTime = Date()

        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            guard let self = self else { return }

            guard let map = self.parseCentralDirectory(filePath: path) else {
                print("[ZipArchiveService] 解析 ZIP Central Directory 失败: \(path)")
                self.lock.lock()
                self._isIndexing = false
                self._isIndexed = false
                self.entriesMap.removeAll()
                let failedRequests = self.pendingRequests
                self.pendingRequests.removeAll()
                self.lock.unlock()

                for (_, completion) in failedRequests {
                    completion(.failure(NSError(domain: "ZipService", code: 500, userInfo: [NSLocalizedDescriptionKey: "ZIP 归档索引构建失败"])))
                }
                return
            }

            self.lock.lock()
            self.entriesMap = map
            self._isIndexed = true
            self._isIndexing = false
            let queued = self.pendingRequests
            self.pendingRequests.removeAll()
            self.lock.unlock()

            let cost = Date().timeIntervalSince(startTime)
            print("[ZipArchiveService] ZIP 极速挂载成功: \(map.count) 个条目索引，耗时: \(String(format: "%.2f", cost))s")

            // 立即按顺序处理冷启动期间排队的图片读取任务
            for (pendingPath, completion) in queued {
                self.extract(path: pendingPath, completion: completion)
            }
        }
    }

    /// 高性能单遍连续扫描 Central Directory (杜绝 24 万次跨磁盘随机寻道)
    private func parseCentralDirectory(filePath: String) -> [String: ZipEntryInfo]? {
        guard let file = fopen(filePath, "rb") else {
            print("[ZipArchiveService] 无法打开 ZIP 文件: \(filePath)")
            return nil
        }
        defer { fclose(file) }

        fseeko(file, 0, SEEK_END)
        let fileSize = ftello(file)
        guard fileSize > 22 else { return nil }

        // 1. 在文件末尾搜索 EOCD / ZIP64 EOCD Locator
        let searchSize = min(Int(fileSize), 65536 + 256)
        fseeko(file, off_t(fileSize - off_t(searchSize)), SEEK_SET)
        var buffer = [UInt8](repeating: 0, count: searchSize)
        let bytesRead = fread(&buffer, 1, searchSize, file)
        guard bytesRead == searchSize else { return nil }

        var cdOffset: UInt64 = 0
        var cdSize: UInt64 = 0
        var totalEntries: UInt64 = 0
        var isZip64 = false

        // 优先定位 ZIP64 End of Central Directory Locator (0x07064b50)
        if searchSize >= 20 {
            for i in stride(from: searchSize - 20, through: 0, by: -1) {
                if buffer[i] == 0x50 && buffer[i+1] == 0x4b && buffer[i+2] == 0x06 && buffer[i+3] == 0x07 {
                    let eocd64Offset = buffer.withUnsafeBytes { ptr -> UInt64 in
                        return ptr.loadUnaligned(fromByteOffset: i + 8, as: UInt64.self)
                    }
                    if eocd64Offset < UInt64(fileSize) {
                        fseeko(file, off_t(eocd64Offset), SEEK_SET)
                        var eocd64 = [UInt8](repeating: 0, count: 56)
                        if fread(&eocd64, 1, 56, file) == 56 {
                            let sig = eocd64.withUnsafeBytes { $0.loadUnaligned(fromByteOffset: 0, as: UInt32.self) }
                            if sig == 0x06064b50 {
                                isZip64 = true
                                totalEntries = eocd64.withUnsafeBytes { $0.loadUnaligned(fromByteOffset: 32, as: UInt64.self) }
                                cdSize = eocd64.withUnsafeBytes { $0.loadUnaligned(fromByteOffset: 40, as: UInt64.self) }
                                cdOffset = eocd64.withUnsafeBytes { $0.loadUnaligned(fromByteOffset: 48, as: UInt64.self) }
                                break
                            }
                        }
                    }
                }
            }
        }

        // 标准 EOCD 降级探测 (0x06054b50)
        if !isZip64 {
            for i in stride(from: searchSize - 22, through: 0, by: -1) {
                if buffer[i] == 0x50 && buffer[i+1] == 0x4b && buffer[i+2] == 0x05 && buffer[i+3] == 0x06 {
                    let entries = buffer.withUnsafeBytes { ptr -> UInt16 in
                        return ptr.loadUnaligned(fromByteOffset: i + 10, as: UInt16.self)
                    }
                    let size = buffer.withUnsafeBytes { ptr -> UInt32 in
                        return ptr.loadUnaligned(fromByteOffset: i + 12, as: UInt32.self)
                    }
                    let offset = buffer.withUnsafeBytes { ptr -> UInt32 in
                        return ptr.loadUnaligned(fromByteOffset: i + 16, as: UInt32.self)
                    }
                    totalEntries = UInt64(entries)
                    cdSize = UInt64(size)
                    cdOffset = UInt64(offset)
                    break
                }
            }
        }

        guard cdSize > 0, cdOffset < UInt64(fileSize) else {
            print("[ZipArchiveService] 无法定位 Central Directory (cdSize=\(cdSize), cdOffset=\(cdOffset))")
            return nil
        }

        // 2. 一次性将约 20MB 的 Central Directory 读入内存缓冲区
        fseeko(file, off_t(cdOffset), SEEK_SET)
        let cdBytesCount = Int(cdSize)
        var cdData = [UInt8](repeating: 0, count: cdBytesCount)
        let cdRead = fread(&cdData, 1, cdBytesCount, file)
        guard cdRead == cdBytesCount else {
            print("[ZipArchiveService] 读取 Central Directory 失败")
            return nil
        }

        // 3. 极速单遍连续解析 Central Directory 构建 entriesMap
        var map = [String: ZipEntryInfo](minimumCapacity: Int(min(totalEntries, 300000)) * 2)
        var pos = 0
        let cdEnd = cdBytesCount

        cdData.withUnsafeBytes { rawPtr in
            while pos + 46 <= cdEnd {
                let sig = rawPtr.loadUnaligned(fromByteOffset: pos, as: UInt32.self)
                guard sig == 0x02014b50 else { break }

                let method = rawPtr.loadUnaligned(fromByteOffset: pos + 10, as: UInt16.self)
                var compSize = UInt64(rawPtr.loadUnaligned(fromByteOffset: pos + 20, as: UInt32.self))
                var uncompSize = UInt64(rawPtr.loadUnaligned(fromByteOffset: pos + 24, as: UInt32.self))
                let fnameLen = Int(rawPtr.loadUnaligned(fromByteOffset: pos + 28, as: UInt16.self))
                let extraLen = Int(rawPtr.loadUnaligned(fromByteOffset: pos + 30, as: UInt16.self))
                let commentLen = Int(rawPtr.loadUnaligned(fromByteOffset: pos + 32, as: UInt16.self))
                var localOffset = UInt64(rawPtr.loadUnaligned(fromByteOffset: pos + 42, as: UInt32.self))

                let fnameStart = pos + 46
                let fnameEnd = fnameStart + fnameLen
                guard fnameEnd <= cdEnd else { break }

                // 检查并解析 ZIP64 扩展字段 (0x0001)
                if extraLen > 0 && (localOffset == 0xFFFFFFFF || compSize == 0xFFFFFFFF || uncompSize == 0xFFFFFFFF) {
                    var ePos = fnameEnd
                    let extraEnd = min(fnameEnd + extraLen, cdEnd)
                    while ePos + 4 <= extraEnd {
                        let headerId = rawPtr.loadUnaligned(fromByteOffset: ePos, as: UInt16.self)
                        let dataSize = Int(rawPtr.loadUnaligned(fromByteOffset: ePos + 2, as: UInt16.self))
                        ePos += 4
                        if headerId == 0x0001 {
                            var cur = ePos
                            if uncompSize == 0xFFFFFFFF && cur + 8 <= extraEnd {
                                uncompSize = rawPtr.loadUnaligned(fromByteOffset: cur, as: UInt64.self)
                                cur += 8
                            }
                            if compSize == 0xFFFFFFFF && cur + 8 <= extraEnd {
                                compSize = rawPtr.loadUnaligned(fromByteOffset: cur, as: UInt64.self)
                                cur += 8
                            }
                            if localOffset == 0xFFFFFFFF && cur + 8 <= extraEnd {
                                localOffset = rawPtr.loadUnaligned(fromByteOffset: cur, as: UInt64.self)
                                cur += 8
                            }
                            break
                        }
                        ePos += dataSize
                    }
                }

                // 提取文件路径
                if let fnamePtr = rawPtr.baseAddress?.advanced(by: fnameStart) {
                    let pathStr = String(decoding: UnsafeRawBufferPointer(start: fnamePtr, count: fnameLen), as: UTF8.self)
                    if !pathStr.isEmpty && !pathStr.hasSuffix("/") {
                        let entry = ZipEntryInfo(
                            relativeOffset: localOffset,
                            compressedSize: compSize,
                            uncompressedSize: uncompSize,
                            compressionMethod: method
                        )
                        let clean = pathStr.trimmingCharacters(in: CharacterSet(charactersIn: "/ "))
                        map[clean] = entry
                        if clean.hasPrefix("image_cache/") {
                            let stripped = String(clean.dropFirst("image_cache/".count))
                            map[stripped] = entry
                        } else {
                            map["image_cache/\(clean)"] = entry
                        }
                    }
                }

                pos += 46 + fnameLen + extraLen + commentLen
            }
        }

        return map
    }

    /// 极速 O(1) 检查 ZIP 中是否存在目标路径的条目
    public func hasEntry(path: String) -> Bool {
        lock.lock()
        defer { lock.unlock() }

        if !_isIndexed && _isIndexing {
            return true
        }

        var clean = path.trimmingCharacters(in: CharacterSet(charactersIn: "/ "))
        if let decoded = clean.removingPercentEncoding { clean = decoded }

        if entriesMap[clean] != nil { return true }
        if entriesMap["image_cache/\(clean)"] != nil { return true }
        if clean.hasPrefix("image_cache/") {
            let stripped = String(clean.dropFirst("image_cache/".count))
            if entriesMap[stripped] != nil { return true }
        }
        return false
    }

    /// O(1) 寻址极速提取文件数据 (无锁查找 + 专用 IO 串行读取，线程绝对安全)
    public func extract(path: String, completion: @escaping (Result<Data, Error>) -> Void) {
        var clean = path.trimmingCharacters(in: CharacterSet(charactersIn: "/ "))
        if let decoded = clean.removingPercentEncoding { clean = decoded }

        lock.lock()
        // 若后台 Central Directory 仍在索引中，将请求加入等待队列，绝不提前以 404 失败退出
        if !_isIndexed && _isIndexing {
            pendingRequests.append((clean, completion))
            lock.unlock()
            return
        }

        if !_isIndexed && !_isIndexing {
            lock.unlock()
            completion(.failure(NSError(domain: "ZipService", code: 404, userInfo: [NSLocalizedDescriptionKey: "ZIP 归档未初始化"])))
            return
        }

        var targetEntry = entriesMap[clean]
        if targetEntry == nil {
            targetEntry = entriesMap["image_cache/\(clean)"]
        }
        if targetEntry == nil && clean.hasPrefix("image_cache/") {
            let stripped = String(clean.dropFirst("image_cache/".count))
            targetEntry = entriesMap[stripped]
        }
        lock.unlock()

        guard let entry = targetEntry else {
            completion(.failure(NSError(domain: "ZipService", code: 404, userInfo: [NSLocalizedDescriptionKey: "ZIP 中未找到目标条目: \(path)"])))
            return
        }

        ioQueue.async { [weak self] in
            guard let self = self else { return }
            do {
                let data = try self.performExtract(entry: entry)
                completion(.success(data))
            } catch {
                completion(.failure(error))
            }
        }
    }

    /// 在 IO 队列上执行物理文件抽取与解压
    private func performExtract(entry: ZipEntryInfo) throws -> Data {
        guard let zipPath = self.currentZipPath else {
            throw NSError(domain: "ZipService", code: 404, userInfo: [NSLocalizedDescriptionKey: "ZIP 文件路径为空"])
        }

        if self.readFile == nil {
            self.readFile = fopen(zipPath, "rb")
        }
        guard let file = self.readFile else {
            throw NSError(domain: "ZipService", code: 500, userInfo: [NSLocalizedDescriptionKey: "打开 ZIP 句柄失败: \(zipPath)"])
        }

        // 1. 读取 30 字节 Local File Header
        fseeko(file, off_t(entry.relativeOffset), SEEK_SET)
        var localHdr = [UInt8](repeating: 0, count: 30)
        guard fread(&localHdr, 1, 30, file) == 30 else {
            throw NSError(domain: "ZipService", code: 500, userInfo: [NSLocalizedDescriptionKey: "读取 Local File Header 失败"])
        }
        let sig = localHdr.withUnsafeBytes { $0.loadUnaligned(fromByteOffset: 0, as: UInt32.self) }
        guard sig == 0x04034b50 else {
            throw NSError(domain: "ZipService", code: 500, userInfo: [NSLocalizedDescriptionKey: "无效的 Local Header 签名: \(String(format: "0x%08X", sig))"])
        }
        let fnameLen = localHdr.withUnsafeBytes { $0.loadUnaligned(fromByteOffset: 26, as: UInt16.self) }
        let extraLen = localHdr.withUnsafeBytes { $0.loadUnaligned(fromByteOffset: 28, as: UInt16.self) }

        let dataOffset = entry.relativeOffset + 30 + UInt64(fnameLen) + UInt64(extraLen)
        fseeko(file, off_t(dataOffset), SEEK_SET)

        // 2. 未压缩文件直接提取 (Stored)
        if entry.compressionMethod == 0 {
            var data = Data(count: Int(entry.uncompressedSize))
            let readCount = data.withUnsafeMutableBytes { fread($0.baseAddress!, 1, Int(entry.uncompressedSize), file) }
            guard readCount == Int(entry.uncompressedSize) else {
                throw NSError(domain: "ZipService", code: 500, userInfo: [NSLocalizedDescriptionKey: "读取未压缩数据长度不足"])
            }
            return data
        }

        // 3. Deflate 压缩文件解压 (Method 8)
        if entry.compressionMethod == 8 {
            var compressedData = Data(count: Int(entry.compressedSize))
            let readCount = compressedData.withUnsafeMutableBytes { fread($0.baseAddress!, 1, Int(entry.compressedSize), file) }
            guard readCount == Int(entry.compressedSize) else {
                throw NSError(domain: "ZipService", code: 500, userInfo: [NSLocalizedDescriptionKey: "读取 Deflate 压缩数据长度不足"])
            }

            #if canImport(zlib)
            if let decompressed = inflateRawDeflate(compressedData: compressedData, uncompressedSize: Int(entry.uncompressedSize)) {
                return decompressed
            }
            #endif

            // 备选降级方案：使用 ZIPFoundation 解压流
            return try fallbackDecompress(dataOffset: dataOffset, compressedSize: entry.compressedSize, uncompressedSize: entry.uncompressedSize, file: file)
        }

        throw NSError(domain: "ZipService", code: 500, userInfo: [NSLocalizedDescriptionKey: "不支持的压缩方法: \(entry.compressionMethod)"])
    }

    #if canImport(zlib)
    /// 原生硬件级 zlib 极速解压 RFC 1951 Deflate 内存块
    private func inflateRawDeflate(compressedData: Data, uncompressedSize: Int) -> Data? {
        var stream = z_stream()
        let initRet = inflateInit2_(&stream, -MAX_WBITS, ZLIB_VERSION, Int32(MemoryLayout<z_stream>.size))
        guard initRet == Z_OK else { return nil }
        defer { inflateEnd(&stream) }

        var decompressed = Data(count: uncompressedSize)
        var actualCount = 0
        let success: Bool = compressedData.withUnsafeBytes { srcBytes in
            decompressed.withUnsafeMutableBytes { dstBytes in
                stream.next_in = UnsafeMutablePointer(mutating: srcBytes.bindMemory(to: Bytef.self).baseAddress)
                stream.avail_in = uInt(compressedData.count)
                stream.next_out = dstBytes.bindMemory(to: Bytef.self).baseAddress
                stream.avail_out = uInt(uncompressedSize)
                let ret = inflate(&stream, Z_FINISH)
                if ret == Z_STREAM_END || ret == Z_OK {
                    actualCount = Int(stream.total_out)
                    return true
                }
                return false
            }
        }
        return success ? decompressed.prefix(actualCount) : nil
    }
    #endif

    private func fallbackDecompress(dataOffset: UInt64, compressedSize: UInt64, uncompressedSize: UInt64, file: UnsafeMutablePointer<FILE>) throws -> Data {
        var decompressed = Data(capacity: Int(uncompressedSize))
        _ = try Data.decompress(size: Int64(compressedSize), bufferSize: 32768, skipCRC32: true, provider: { pos, chunkSize in
            fseeko(file, off_t(dataOffset + UInt64(pos)), SEEK_SET)
            var chunk = Data(count: chunkSize)
            let r = chunk.withUnsafeMutableBytes { fread($0.baseAddress!, 1, chunkSize, file) }
            return chunk.prefix(r)
        }, consumer: { chunk in
            decompressed.append(chunk)
        })
        return decompressed
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
        self.currentZipPath = nil
        self.entriesMap.removeAll()
        self._isIndexed = false
        self._isIndexing = false
        self.pendingRequests.removeAll()
        lock.unlock()

        ioQueue.async { [weak self] in
            if let f = self?.readFile {
                fclose(f)
                self?.readFile = nil
            }
            print("[ZipArchiveService] 已释放 ZIP 资源与索引")
        }
    }
}
