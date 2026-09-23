# GPDb Android 客户端编译计划项目书 (Compilation Plan)

---

## 1. 项目概况与编译目标

- **项目名称**：GPDb Mobile for Android (项目代号: `GPDb-Android`)
- **适用系统**：Android 8.0+ (API Level 26+)，主推荐 Android 12 ~ Android 15 (API Level 31~35)
- **目标架构**：
  - `arm64-v8a`（主流真机手机、平板、折叠屏，优先级最高）
  - `x86_64`（Android Studio 模拟器调试专用）
  - `armeabi-v7a`（旧型设备向下兼容，可选）
- **产物形态**：
  - 开发阶段：Debug APK (`app-debug.apk`)
  - 生产阶段：Release APK (`app-release.apk`) 及 Google Play 规范的 AAB (`app-release.aab`)

---

## 2. 编译环境与工具链依赖 (Prerequisites)

在开始编译前，编译主机（macOS / Linux）需准备如下开发环境：

| 依赖组件 | 最低版本要求 | 作用说明 |
| :--- | :--- | :--- |
| **Java Development Kit (JDK)** | OpenJDK 17 或 21 | Android Gradle 插件 (AGP 8.x+) 强制要求 |
| **Android Studio & SDK** | Iguana / Jellyfish (API 34+) | Android 平台 SDK、Platform-tools (adb) |
| **Android NDK** | r26b (26.1.10909125) 或更高 | 编译 Rust 底层代码为 Android 原生 C 库 (`.so`) |
| **Rust 工具链** | Rust 1.78.0+ | `cargo`, `rustc` |
| **Android 交叉编译 Target** | `rustup` 包含目标 | `aarch64-linux-android`, `x86_64-linux-android` |
| **Node.js & npm** | Node.js 20+ LTS | 构建移动端前端资源与打包管线 |

### 环境初始化命令速查

```bash
# 1. 添加 Rust Android 交叉编译平台目标
rustup target add aarch64-linux-android
rustup target add x86_64-linux-android
rustup target add armv7-linux-androideabi

# 2. 检查 Android 环境变量（macOS 示例，置于 ~/.zshrc）
export ANDROID_HOME="$HOME/Library/Android/sdk"
export NDK_HOME="$ANDROID_HOME/ndk/26.1.10909125"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
```

---

## 3. 分阶段实施里程碑与时间表 (Milestones)

```
[阶段一: 基础骨架与跨平台 NDK 编译] ────► [阶段二: SAF 存储驱动与本地数据库加载]
                 │                                            │
                 ▼                                            ▼
[阶段三: 触控交互与移动 UI 全量适配] ────► [阶段四: 图片离线缓存与性能调优]
                 │                                            │
                 ▼                                            ▼
       [阶段五: 签名、构建与双端数据隔离验证测试]
```

### 阶段一：基础骨架初始化与 Rust NDK 跨平台编译
- 初始化 `android_client/` 项目骨架，配置独立 `package.json` 与 Android 工程模板。
- 将 `gpdb-core` 配置为可编译输出 C 动态链接库 (`cdylib`)，供 Android JNI 调用。
- 验证在 Android Studio 模拟器中启动空白宿主并成功执行 Rust 返回的 SQLite 版本号测试。

### 阶段二：SAF (Storage Access Framework) 驱动与本地数据库挂载
- 实现 Android 原生系统文件/文件夹选取器（`ACTION_OPEN_DOCUMENT_TREE`），获取包含 `gevi.db` 与 `image_cache/` 的目录授权。
- 使用 `rusqlite` 与 Android Scoped Storage fd 桥接，支持直接读取外部存储中的 63,000+ 电影库数据。
- 保证只读访问安全，杜绝误删用户数据库。

### 阶段三：移动端触控交互与界面重构
- 引入滑动抽屉（Drawer）、底部导航栏（Bottom Navigation）、横向滑动卡片列表。
- 优化长列表的虚拟滚动（Virtual List），确保在包含 10 万+ 条目时依然以 60/120Hz 满帧率流畅滚动。
- 适配竖屏与平板横屏响应式网格布局。

### 阶段四：离线媒体加载与移动端内存优化
- 实现 Android 端自定义图片加载适配器，重定向海报与演员头像到本地 SAF 缓存目录。
- 引入图片动态降采样与硬件加速位图解码，避免多图拼图或快速滑动时发生 OOM。

### 阶段五：Release 打包、代码混淆与自签名
- 配置 Android Gradle ProGuard / R8 混淆规则，保护底层逻辑并压缩安装包体积。
- 生成专有发布密钥库 (`gpdb-release.keystore`)，签署正式发布版 APK。

---

## 4. 详细编译与构建指令集

### 4.1 编译 Rust NDK 本地库

```bash
# 在 android_client 目录下执行交叉编译
cargo build --target aarch64-linux-android --release
cargo build --target x86_64-linux-android --release
```

### 4.2 前端资源构建

```bash
cd android_client
npm install
npm run build
```

### 4.3 生成 Android 签名 APK

```bash
cd android_client/app
./gradlew assembleRelease
# 输出路径: android_client/app/build/outputs/apk/release/app-release.apk
```

### 4.4 调试与真机一键安装

```bash
adb devices
adb install -r android_client/app/build/outputs/apk/debug/app-debug.apk
```

---

## 5. 风险评估与应对措施

| 潜在风险 | 影响程度 | 解决方案与应对策略 |
| :--- | :---: | :--- |
| **海量离线图片导致 SAF 读取速度慢** | 中 | 建立内存与应用专属私有目录的双层轻量索引，首次扫描后缓存 URI 映射表，避免每次反复遍历 DocumentFile。 |
| **低内存设备（3GB/4GB）OOM** | 高 | 启用 `android:largeHeap="true"`，在 Glide / Coil 中强制限制海报缓存为 `RGB_565` 格式（节省 50% 内存），且可视区域外图片即时回收。 |
| **Android 版本碎片化** | 低 | 最低支持至 Android 8.0（覆盖目前市场上 97% 以上的活跃设备），利用 Jetpack 兼容库抹平系统差异。 |
