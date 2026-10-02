import Foundation
import SwiftUI
import Combine
import ZIPFoundation
import Kingfisher
#if canImport(UIKit)
import UIKit
#endif

@MainActor
public final class AppEnvironment: ObservableObject {
    @Published public var isDatabaseReady: Bool = false
    @Published public var dbPath: String? = nil
    @Published public var imageZipPath: String? = nil
    @Published public var imagePhysicalRoot: String? = nil

    // 冷启动与装载状态
    @Published public var isColdBootMounting: Bool = true
    @Published public var mountingProgressText: String = "正在装载核心数据库..."
    @Published public var mountingSubText: String = "单机私有 · 零网络依赖 · 极速离线"

    // 交互与提示状态
    @Published public var isImportingDb: Bool = false
    @Published public var alertMessage: String? = nil
    @Published public var showAlert: Bool = false

    // 隐私与防窥
    @Published public var isPrivacyProtectionEnabled: Bool = true
    @Published public var isPrivacyModeActive: Bool = false
    @Published public var isPrivacyMaskActive: Bool = false
    @Published public var isBiometricLockEnabled: Bool = false
    @Published public var isUnlocked: Bool = true

    // 布局与偏好
    @Published public var layoutColumns: Int = 2
    @Published public var use3DFlipCards: Bool = false

    // 在线更新
    @Published public var availableUpdate: GitHubReleaseInfo? = nil
    @Published public var isCheckingUpdates: Bool = false

    private let updateService = GitHubUpdateService()
    private let biometricService = BiometricAuthService.shared
    private let defaults = UserDefaults.standard

    private let kDbBookmark = "gpdb_bookmark_db"
    private let kImagesBookmark = "gpdb_bookmark_images"
    private let kPrivacyEnabled = "gpdb_pref_privacy_enabled"
    private let kPrivacyActive = "gpdb_pref_privacy_active"
    private let kBiometricEnabled = "gpdb_pref_biometric_enabled"
    private let kLayoutColumns = "gpdb_pref_layout_columns"
    private let k3DFlip = "gpdb_pref_3d_flip"

    private var activeDbSecurityUrl: URL?
    private var activeImagesSecurityUrl: URL?

    public init() {
        self.isPrivacyProtectionEnabled = defaults.object(forKey: kPrivacyEnabled) as? Bool ?? true
        self.isPrivacyModeActive = defaults.bool(forKey: kPrivacyActive)
        self.isBiometricLockEnabled = defaults.bool(forKey: kBiometricEnabled)
        self.layoutColumns = defaults.object(forKey: kLayoutColumns) as? Int ?? 2
        self.use3DFlipCards = defaults.bool(forKey: k3DFlip)
        self.isUnlocked = !isBiometricLockEnabled

        configureImageCache()

        // 异步执行极速冷启动装载，绝不阻塞主线程
        Task { [weak self] in
            await self?.performInitialMount()
        }
    }

    private func configureImageCache() {
        // 限制 Kingfisher 内存缓存上限为 120MB，防止海量大图撑爆内存导致发热和卡顿
        ImageCache.default.memoryStorage.config.totalCostLimit = 120 * 1024 * 1024
        ImageCache.default.memoryStorage.config.countLimit = 150
        // 限制磁盘缓存为 1GB
        ImageCache.default.diskStorage.config.sizeLimit = 1000 * 1024 * 1024
        // 后台清理过期缓存
        DispatchQueue.global(qos: .utility).async {
            ImageCache.default.cleanExpiredDiskCache()
        }
    }

    // MARK: - 书签持久化与自动挂载

    public func mountDatabase(url: URL) {
        isImportingDb = true
        defer { isImportingDb = false }

        let isScoped = url.startAccessingSecurityScopedResource()
        defer {
            if isScoped {
                url.stopAccessingSecurityScopedResource()
            }
        }

        do {
            guard let docsUrl = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first else {
                throw NSError(domain: "GPDb", code: 500, userInfo: [NSLocalizedDescriptionKey: "无法访问应用沙盒 Documents 目录"])
            }

            let originalName = url.lastPathComponent
            let targetFileName = originalName.isEmpty ? "GPDb.db" : originalName
            let targetUrl = docsUrl.appendingPathComponent(targetFileName)

            // 如果选中的不是沙盒内的同路径文件，安全协调拷贝到本地 Documents
            if url.standardizedFileURL.path != targetUrl.standardizedFileURL.path {
                if FileManager.default.fileExists(atPath: targetUrl.path) {
                    try? FileManager.default.removeItem(at: targetUrl)
                }

                var coordinatorError: NSError?
                var copyError: Error?
                let coordinator = NSFileCoordinator()
                coordinator.coordinate(readingItemAt: url, options: .withoutChanges, error: &coordinatorError) { readUrl in
                    do {
                        try FileManager.default.copyItem(at: readUrl, to: targetUrl)
                    } catch {
                        copyError = error
                    }
                }
                if let err = coordinatorError ?? copyError {
                    throw err
                }
            }

            // 初始化沙盒内的高性能 SQLite
            try DatabaseHolder.shared.initialize(at: targetUrl.path)
            self.dbPath = targetUrl.path
            withAnimation(.spring()) {
                self.isDatabaseReady = true
            }
        } catch {
            print("挂载数据库失败: \(error)")
            self.alertMessage = "数据库挂载失败: \(error.localizedDescription)"
            self.showAlert = true
        }
    }

    public func mountImages(url: URL) {
        activeImagesSecurityUrl?.stopAccessingSecurityScopedResource()
        activeImagesSecurityUrl = nil

        let isScoped = url.startAccessingSecurityScopedResource()
        if isScoped {
            activeImagesSecurityUrl = url
        }

        if let bookmarkData = try? url.bookmarkData(options: .minimalBookmark, includingResourceValuesForKeys: nil, relativeTo: nil) {
            defaults.set(bookmarkData, forKey: kImagesBookmark)
        }

        setupImagesPath(path: url.path)

        // 校验压缩包是否可顺利开启
        if url.path.hasSuffix(".zip") {
            do {
                _ = try Archive(url: URL(fileURLWithPath: url.path), accessMode: .read, pathEncoding: nil)
            } catch {
                self.alertMessage = "ZIP 图库读取异常: \(error.localizedDescription)"
                self.showAlert = true
            }
        }
    }

    private func tryMountDatabase(path: String) {
        do {
            try DatabaseHolder.shared.initialize(at: path)
            self.dbPath = path
            self.isDatabaseReady = true
        } catch {
            print("挂载数据库失败: \(error)")
            self.alertMessage = "挂载数据库失败: \(error.localizedDescription)"
            self.showAlert = true
        }
    }

    private func setupImagesPath(path: String) {
        if path.hasSuffix(".zip") {
            self.imageZipPath = path
            self.imagePhysicalRoot = nil
            ZipArchiveService.shared.setZipPath(path)
        } else {
            self.imagePhysicalRoot = path
            self.imageZipPath = nil
            ZipArchiveService.shared.release()
        }
    }

    // MARK: - 初始化本地目录与自动探测

    private func setupInitialDocumentsDirectory() {
        guard let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first else { return }

        let guideFile = docs.appendingPathComponent("使用说明-请将数据放入此目录.txt")
        if !FileManager.default.fileExists(atPath: guideFile.path) {
            let guideContent = """
            【GPDb 离线影库管理 - 本地存储目录】

            欢迎使用 GPDb iOS 客户端！
            您可以通过电脑连接（访达/iTunes）、隔空投送 (AirDrop)、或在 iPhone 系统的【文件】App 中，直接将数据放入本目录（我的 iPhone -> GPDb）：

            1. 核心数据库文件：
               - 推荐命名为 GPDb.db（亦支持 movies.db 或任意 SQLite .db 文件）
               - 放置后回到 App，系统将自动识别并立即载入。

            2. 离线缓存海报图库：
               - 推荐命名为 GPDb_Images.zip（无需解压）
               - 放置后回到 App，将自动挂载海报与剧照加速通道。

            3. 全平台通用备份：
               - 可直接放置从 macOS / Android / Windows 客户端导出的 JSON 备份文件，进入设置页面一键恢复。

            注意：请勿删除正在使用中的 GPDb.db 文件。
            """
            try? guideContent.write(to: guideFile, atomically: true, encoding: .utf8)
        }
    }

    public func performInitialMount() async {
        isColdBootMounting = true
        mountingProgressText = "正在扫描本地数据库..."
        mountingSubText = "单机私有 · 零网络依赖 · 极速离线"

        await autoDetectAndMountAsync()

        // 保证平滑过渡动画，给 UI 极短时间呈现标志性呼吸动效
        try? await Task.sleep(nanoseconds: 350_000_000)

        withAnimation(.easeInOut(duration: 0.35)) {
            self.isColdBootMounting = false
        }
    }

    public func autoDetectDocumentsDirectory() {
        Task {
            await autoDetectAndMountAsync()
        }
    }

    private func autoDetectAndMountAsync() async {
        setupInitialDocumentsDirectory()
        guard let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first else { return }

        // 1. 自动探测数据库文件
        if !isDatabaseReady {
            let candidateDbNames = ["GPDb.db", "movies.db", "gevi.db"]
            var foundDbPath: String? = nil
            for name in candidateDbNames {
                let file = docs.appendingPathComponent(name)
                if FileManager.default.fileExists(atPath: file.path) {
                    foundDbPath = file.path
                    break
                }
            }

            // 若候选文件名不存在，遍历探测所有 .db / .sqlite 文件
            if foundDbPath == nil, let items = try? FileManager.default.contentsOfDirectory(at: docs, includingPropertiesForKeys: nil) {
                for item in items {
                    let ext = item.pathExtension.lowercased()
                    if ext == "db" || ext == "sqlite" || ext == "sqlite3" {
                        foundDbPath = item.path
                        break
                    }
                }
            }

            if let dbToMount = foundDbPath {
                let dbFileName = URL(fileURLWithPath: dbToMount).lastPathComponent
                self.mountingProgressText = "正在挂载 \(dbFileName)..."
                self.mountingSubText = "已配置 WAL 模式与 256MB 内存映射"

                do {
                    try DatabaseHolder.shared.initialize(at: dbToMount)
                    self.dbPath = dbToMount
                    self.isDatabaseReady = true
                } catch {
                    print("自动检测数据库失败: \(error)")
                }
            }
        }

        // 2. 否则通过安全书签恢复外部文件
        if !isDatabaseReady {
            restoreSecurityScopedBookmarks()
        }

        // 3. 自动探测图片库
        if imageZipPath == nil && imagePhysicalRoot == nil {
            let zipCandidate = docs.appendingPathComponent("GPDb_Images.zip")
            let folderCandidate = docs.appendingPathComponent("image_cache")
            if FileManager.default.fileExists(atPath: zipCandidate.path) {
                setupImagesPath(path: zipCandidate.path)
            } else if FileManager.default.fileExists(atPath: folderCandidate.path) {
                setupImagesPath(path: folderCandidate.path)
            } else if let items = try? FileManager.default.contentsOfDirectory(at: docs, includingPropertiesForKeys: nil) {
                for item in items where item.pathExtension.lowercased() == "zip" {
                    setupImagesPath(path: item.path)
                    break
                }
            }
        }
    }

    private func restoreSecurityScopedBookmarks() {
        // 恢复数据库书签
        if let data = defaults.data(forKey: kDbBookmark) {
            var isStale = false
            if let url = try? URL(resolvingBookmarkData: data, options: .withoutUI, relativeTo: nil, bookmarkDataIsStale: &isStale) {
                if url.startAccessingSecurityScopedResource() {
                    activeDbSecurityUrl = url
                }
                tryMountDatabase(path: url.path)
            }
        }

        // 恢复图库书签
        if let data = defaults.data(forKey: kImagesBookmark) {
            var isStale = false
            if let url = try? URL(resolvingBookmarkData: data, options: .withoutUI, relativeTo: nil, bookmarkDataIsStale: &isStale) {
                if url.startAccessingSecurityScopedResource() {
                    activeImagesSecurityUrl = url
                }
                setupImagesPath(path: url.path)
            }
        }
    }

    // MARK: - 交互操作

    public func togglePrivacyMode() {
        isPrivacyModeActive.toggle()
        defaults.set(isPrivacyModeActive, forKey: kPrivacyActive)
        #if canImport(UIKit)
        let impact = UIImpactFeedbackGenerator(style: .medium)
        impact.impactOccurred()
        #endif
    }

    public func handleAppBecameActive() {
        isPrivacyMaskActive = false
        // 切回前台时，自动检测是否有从外部/文件App新放入的数据
        if !isDatabaseReady || (imageZipPath == nil && imagePhysicalRoot == nil) {
            autoDetectDocumentsDirectory()
        }
        if isBiometricLockEnabled && !isUnlocked {
            Task {
                await authenticate()
            }
        }
    }

    public func authenticate() async {
        let success = await biometricService.authenticate()
        if success {
            self.isUnlocked = true
        }
    }

    public func checkForUpdates() async {
        isCheckingUpdates = true
        if let release = await updateService.checkLatestRelease() {
            self.availableUpdate = release
        }
        isCheckingUpdates = false
    }

    public func updateLayoutColumns(_ cols: Int) {
        self.layoutColumns = cols
        defaults.set(cols, forKey: kLayoutColumns)
    }

    public func toggle3DFlipCards() {
        self.use3DFlipCards.toggle()
        defaults.set(self.use3DFlipCards, forKey: k3DFlip)
    }
}
