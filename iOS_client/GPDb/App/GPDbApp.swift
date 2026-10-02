import SwiftUI
import UniformTypeIdentifiers

@main
struct GPDbApp: App {
    @StateObject private var environment = AppEnvironment()
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            ZStack {
                if !environment.isUnlocked {
                    BiometricLockView()
                        .environmentObject(environment)
                } else if environment.isColdBootMounting {
                    DatabaseLoadingView()
                        .environmentObject(environment)
                        .transition(.opacity)
                } else if environment.isDatabaseReady {
                    AppNavigation()
                        .environmentObject(environment)
                        .transition(.opacity)
                } else {
                    SetupView()
                        .environmentObject(environment)
                        .transition(.opacity)
                }

                // 隐私保护高斯模糊遮罩层 (对标 Android FLAG_SECURE)
                if environment.isPrivacyMaskActive {
                    PrivacyProtectionView()
                }
            }
            .animation(.easeInOut(duration: 0.35), value: environment.isColdBootMounting)
            .animation(.easeInOut(duration: 0.35), value: environment.isDatabaseReady)
            .onAppear {
                environment.isPrivacyMaskActive = false
            }
            .onChange(of: scenePhase) { _, newPhase in
                switch newPhase {
                case .background, .inactive:
                    if environment.isPrivacyProtectionEnabled {
                        environment.isPrivacyMaskActive = true
                    }
                case .active:
                    environment.handleAppBecameActive()
                @unknown default:
                    break
                }
            }
            .task {
                // 启动 3 秒后静默巡检 GitHub API 最新版本
                try? await Task.sleep(nanoseconds: 3_000_000_000)
                await environment.checkForUpdates()
            }
        }
    }
}

struct BiometricLockView: View {
    @EnvironmentObject var environment: AppEnvironment

    var body: some View {
        VStack(spacing: 20) {
            Image(systemName: "faceid")
                .font(.system(size: 64))
                .foregroundStyle(.tint)
            Text("私密影库已锁定")
                .font(.title2.bold())
            Text("请验证 Face ID / Touch ID 以解锁")
                .font(.subheadline)
                .foregroundStyle(.secondary)
            Button("轻触解锁") {
                Task { await environment.authenticate() }
            }
            .buttonStyle(.borderedProminent)
            .tint(.amber)
        }
        .padding()
        .task {
            await environment.authenticate()
        }
    }
}

struct SetupView: View {
    @EnvironmentObject var environment: AppEnvironment
    @State private var showDbPicker: Bool = false
    @State private var showImagesPicker: Bool = false

    private var dbContentTypes: [UTType] {
        var types: [UTType] = [.item, .data, .database]
        if let db = UTType(filenameExtension: "db") { types.append(db) }
        if let sqlite = UTType(filenameExtension: "sqlite") { types.append(sqlite) }
        if let sqlite3 = UTType(filenameExtension: "sqlite3") { types.append(sqlite3) }
        return types
    }

    var body: some View {
        VStack(spacing: 24) {
            Image(systemName: "film.stack")
                .font(.system(size: 68))
                .foregroundStyle(Color.amber)

            VStack(spacing: 8) {
                Text("欢迎使用 GPDb 离线影视库")
                    .font(.title2.bold())
                Text("单机私有、零网络依赖、移动掌上影库")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }

            if environment.isImportingDb {
                HStack(spacing: 12) {
                    ProgressView()
                        .tint(.amber)
                    Text("正在安全导入并校验数据库...")
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(.primary)
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 12)
                .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 14))
                .overlay(
                    RoundedRectangle(cornerRadius: 14)
                        .stroke(Color.amber.opacity(0.3), lineWidth: 1)
                )
                .padding(.vertical, 4)
            }

            VStack(spacing: 12) {
                Button {
                    showDbPicker = true
                } label: {
                    HStack {
                        Image(systemName: "cylinder.split.1x2")
                        Text(environment.isDatabaseReady ? "数据库已挂载 ✓" : "导入 GPDb.db 数据库文件")
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                }
                .buttonStyle(.borderedProminent)
                .tint(.amber)
                .disabled(environment.isImportingDb)

                Button {
                    showImagesPicker = true
                } label: {
                    HStack {
                        Image(systemName: "photo.stack")
                        Text(environment.imageZipPath != nil || environment.imagePhysicalRoot != nil ? "图库已挂载 ✓" : "导入 GPDb_Images.zip 压缩图库")
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                }
                .buttonStyle(.bordered)

                VStack(spacing: 6) {
                    Text("💡 提示：您也可以在系统的【文件】App ->【我的 iPhone】->【GPDb】文件夹中直接放入 GPDb.db 与 GPDb_Images.zip")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)

                    Button {
                        environment.autoDetectDocumentsDirectory()
                        if !environment.isDatabaseReady {
                            environment.alertMessage = "未在 GPDb 目录中检测到有效数据库。请确认已将 GPDb.db 放入【文件】App ->【我的 iPhone】->【GPDb】。"
                            environment.showAlert = true
                        }
                    } label: {
                        Label("立即扫描本地 GPDb 目录", systemImage: "arrow.clockwise")
                            .font(.caption.bold())
                    }
                    .padding(.top, 2)
                }
                .padding(.top, 6)
            }
            .padding(.horizontal, 32)
        }
        .padding()
        .alert("系统提示", isPresented: $environment.showAlert) {
            Button("好", role: .cancel) {}
        } message: {
            Text(environment.alertMessage ?? "")
        }
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
                environment.alertMessage = "选取数据库失败: \(error.localizedDescription)"
                environment.showAlert = true
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
                environment.alertMessage = "选取图库失败: \(error.localizedDescription)"
                environment.showAlert = true
            }
        }
    }
}

