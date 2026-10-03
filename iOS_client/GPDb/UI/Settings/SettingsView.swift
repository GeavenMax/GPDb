import SwiftUI
import UniformTypeIdentifiers

public struct SettingsView: View {
    @EnvironmentObject private var environment: AppEnvironment
    private let userRepo = UserRepository()
    private let analyticsRepo = UserAnalyticsRepository.shared

    @State private var showDbPicker: Bool = false
    @State private var showImagesPicker: Bool = false
    @State private var showExportFile: Bool = false
    @State private var showImportPicker: Bool = false
    @State private var exportData: Data? = nil
    @State private var alertMessage: String? = nil
    @State private var showAlert: Bool = false

    private var dbContentTypes: [UTType] {
        var types: [UTType] = [.item, .data, .database]
        if let db = UTType(filenameExtension: "db") { types.append(db) }
        if let sqlite = UTType(filenameExtension: "sqlite") { types.append(sqlite) }
        if let sqlite3 = UTType(filenameExtension: "sqlite3") { types.append(sqlite3) }
        return types
    }

    public var body: some View {
        NavigationStack {
            Form {
                // 1. 数据源与物理挂载
                Section(header: Text("离线数据源挂载"), footer: Text("提示：您可以在 iPhone 系统【文件】App ->【我的 iPhone】->【GPDb】文件夹中直接放入 GPDb.db 和 GPDb_Images.zip，无需手动挑选，App 启动或切回前台时将自动识别挂载。")) {
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("核心数据库 (GPDb.db)")
                                .font(.subheadline.bold())
                            Text(environment.dbPath ?? "未挂载")
                                .font(.caption)
                                .foregroundStyle(environment.isDatabaseReady ? .green : .secondary)
                                .lineLimit(1)
                        }
                        Spacer()
                        Button("重新选择") {
                            showDbPicker = true
                        }
                        .buttonStyle(.bordered)
                        .disabled(environment.isImportingDb)
                    }

                    if environment.isImportingDb {
                        HStack(spacing: 8) {
                            ProgressView()
                                .scaleEffect(0.9)
                            Text("正在导入并校验数据库...")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }

                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("媒体海报图库")
                                .font(.subheadline.bold())
                            Text(environment.imageZipPath ?? environment.imagePhysicalRoot ?? "未挂载 (GPDb_Images.zip)")
                                .font(.caption)
                                .foregroundStyle(environment.imageZipPath != nil || environment.imagePhysicalRoot != nil ? .green : .secondary)
                                .lineLimit(1)
                        }
                        Spacer()
                        Button("选择图库") {
                            showImagesPicker = true
                        }
                        .buttonStyle(.bordered)
                    }

                    Button {
                        environment.autoDetectDocumentsDirectory()
                        if environment.isDatabaseReady {
                            alertMessage = "扫描完成！数据库已就绪" + (environment.imageZipPath != nil ? "，图库已挂载。" : "。")
                        } else {
                            alertMessage = "未在 GPDb 目录中检测到数据库。请将 GPDb.db 放入【文件】App ->【我的 iPhone】->【GPDb】目录后重试。"
                        }
                        showAlert = true
                    } label: {
                        Label("扫描【文件 App】GPDb 目录", systemImage: "arrow.clockwise")
                    }
                }

                // 2. 外观主题
                Section(header: Text("外观主题"), footer: Text("支持暗色模式、浅色模式或跟随系统自动切换。当前默认使用暗色模式。")) {
                    Picker("界面外观", selection: Binding(
                        get: { environment.appTheme },
                        set: { environment.setAppTheme($0) }
                    )) {
                        ForEach(AppThemeMode.allCases) { mode in
                            Label(mode.title, systemImage: mode.icon)
                                .tag(mode)
                        }
                    }
                    .pickerStyle(.segmented)
                    .padding(.vertical, 4)
                }

                // 3. 隐私与安全
                Section("隐私与安全保护") {
                    Toggle("启用 Face ID / Touch ID 安全锁", isOn: $environment.isBiometricLockEnabled)
                        .onChange(of: environment.isBiometricLockEnabled) { _, newValue in
                            UserDefaults.standard.set(newValue, forKey: "gpdb_pref_biometric_enabled")
                        }

                    Toggle("退后台毛玻璃遮罩防窥", isOn: $environment.isPrivacyProtectionEnabled)
                        .onChange(of: environment.isPrivacyProtectionEnabled) { _, newValue in
                            UserDefaults.standard.set(newValue, forKey: "gpdb_pref_privacy_enabled")
                        }

                    Toggle("全局海报即时防窥打码", isOn: $environment.isPrivacyModeActive)
                        .onChange(of: environment.isPrivacyModeActive) { _, newValue in
                            UserDefaults.standard.set(newValue, forKey: "gpdb_pref_privacy_active")
                        }
                }

                // 4. 影库统计与探索洞察
                Section("影库统计与探索洞察") {
                    NavigationLink {
                        LibraryAnalyticsView()
                    } label: {
                        Label("探索洞察、专注时长与阅览统计", systemImage: "chart.bar.xaxis")
                    }
                }

                // 5. 数据备份与跨端迁移 (通用 JSON)
                Section("全平台通用数据备份 (四端互通)") {
                    Button {
                        Task { await exportUniversalBackup() }
                    } label: {
                        Label("导出用户配置与收藏 (gpdb_universal_backup)", systemImage: "square.and.arrow.up")
                    }

                    Button {
                        showImportPicker = true
                    } label: {
                        Label("导入通用备份恢复数据", systemImage: "square.and.arrow.down")
                    }
                }

                // 5. 软件关于与更新
                Section("关于与在线更新") {
                    HStack {
                        Text("当前版本")
                        Spacer()
                        Text("v2.16.1")
                            .foregroundStyle(.secondary)
                    }

                    Button {
                        Task {
                            await environment.checkForUpdates()
                            if let release = environment.availableUpdate {
                                alertMessage = "发现新版本 \(release.tagName)！\n\n\(release.body)"
                            } else {
                                alertMessage = "当前已是最新版本！"
                            }
                            showAlert = true
                        }
                    } label: {
                        HStack {
                            Text("检查新版本更新")
                            Spacer()
                            if environment.isCheckingUpdates {
                                ProgressView()
                            }
                        }
                    }

                    Link(destination: URL(string: "https://t.me/gpdbnews")!) {
                        HStack {
                            Text("官方 Telegram 频道")
                            Spacer()
                            Image(systemName: "arrow.up.right.square")
                                .foregroundStyle(.secondary)
                        }
                    }
                }
            }
            .navigationTitle("设置")
            .fileImporter(
                isPresented: $showDbPicker,
                allowedContentTypes: dbContentTypes,
                allowsMultipleSelection: false
            ) { result in
                switch result {
                case .success(let urls):
                    if let url = urls.first {
                        environment.mountDatabase(url: url)
                    }
                case .failure(let error):
                    alertMessage = "选取数据库失败: \(error.localizedDescription)"
                    showAlert = true
                }
            }
            .fileImporter(
                isPresented: $showImagesPicker,
                allowedContentTypes: [.zip, .folder],
                allowsMultipleSelection: false
            ) { result in
                switch result {
                case .success(let urls):
                    if let url = urls.first {
                        environment.mountImages(url: url)
                    }
                case .failure(let error):
                    alertMessage = "选取图库失败: \(error.localizedDescription)"
                    showAlert = true
                }
            }
            .fileImporter(
                isPresented: $showImportPicker,
                allowedContentTypes: [.json],
                allowsMultipleSelection: false
            ) { result in
                if case .success(let urls) = result, let url = urls.first {
                    Task { await importUniversalBackup(url: url) }
                }
            }
            .fileExporter(
                isPresented: $showExportFile,
                document: BackupDocument(data: exportData ?? Data()),
                contentType: .json,
                defaultFilename: "GPDb_Universal_Backup_\(Date().formatted(date: .numeric, time: .omitted)).json"
            ) { result in
                if case .success = result {
                    alertMessage = "通用备份已成功导出！可直接在 Android、macOS 与 Windows 端无损恢复。"
                    showAlert = true
                }
            }
            .alert("系统提示", isPresented: $showAlert) {
                Button("确定", role: .cancel) {}
            } message: {
                Text(alertMessage ?? "")
            }
        }
    }

    private func exportUniversalBackup() async {
        do {
            let favs = try await userRepo.exportFavorites()
            let analytics = analyticsRepo.getAnalyticsBackup()
            let data = try UserBackupService.shared.exportUniversalBackup(favorites: favs, analytics: analytics)
            self.exportData = data
            self.showExportFile = true
        } catch {
            alertMessage = "导出备份失败: \(error.localizedDescription)"
            showAlert = true
        }
    }

    private func importUniversalBackup(url: URL) async {
        guard url.startAccessingSecurityScopedResource() else { return }
        defer { url.stopAccessingSecurityScopedResource() }

        do {
            let data = try Data(contentsOf: url)
            let backup = try UserBackupService.shared.parseUniversalBackup(from: data)
            let favCount = try await userRepo.importFavorites(backup.favorites)
            if let ana = backup.analytics {
                analyticsRepo.restoreAnalytics(ana)
            }
            alertMessage = "成功恢复 \(favCount) 条收藏与专注洞察记录！"
            showAlert = true
        } catch {
            alertMessage = "导入备份解析失败: \(error.localizedDescription)"
            showAlert = true
        }
    }
}

private struct BackupDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.json] }
    var data: Data

    init(data: Data) {
        self.data = data
    }

    init(configuration: ReadConfiguration) throws {
        self.data = configuration.file.regularFileContents ?? Data()
    }

    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        return FileWrapper(regularFileWithContents: data)
    }
}
