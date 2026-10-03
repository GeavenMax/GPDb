# GPDb Android 客户端核心技术架构说明书

---

## 1. 架构概览与技术分层

GPDb Android 客户端基于现代 Android 官方应用架构规范开发，全面使用 Kotlin 与声明式 Jetpack Compose：

```
[ UI 表现层 (Presentation Layer) ]
  - Jetpack Compose 1.7+ (Material 3)
  - Navigation Compose (Screen 强类型安全路由)
  - Lifecycle ViewModel + StateFlow (单向数据流 UDF)
  - Coil 2.6+ 离线图片渲染与局部高斯模糊遮盖

[ 业务逻辑与仓储层 (Domain & Repository Layer) ]
  - BrowseRepository / MovieRepository / HomeFeedRepository
  - UserRepository (我的收藏与通用备份)
  - UserAnalyticsRepository (时长与偏好统计)
  - AppUpdateManager (GitHub Releases 在线检查与静默升级)

[ 数据持久化与存储层 (Data Layer) ]
  - Room 2.6+ (Schema v5, TRUNCATE 日志模式)
  - 架构自愈兜底 (GpdbDatabase.ensureSchemaCompatibility)
  - Storage Access Framework (SAF) 目录树授权
  - Jetpack DataStore Preferences (UI 偏好配置)
```

---

## 2. 存储与 SQLite 特别适配规范 (FUSE 机制)

现代 Android 设备的内部共享存储（`/storage/emulated/0/`）通过 FUSE（用户空间文件系统）虚拟化驱动提供。
FUSE 无法正确响应 POSIX 共享内存锁与文件映射（`mmap -shm / -wal`），若直接对外部挂载文件启用 WAL 模式，将触发 Linux 内核 SELinux 拒绝并导致 `SQLiteCantOpenException`。

**核心准则**：
1. Room 构建时必须显式调用 `.setJournalMode(RoomDatabase.JournalMode.TRUNCATE)`；
2. 在 `onOpen` 回调中执行 `PRAGMA journal_mode = TRUNCATE;` 与 `PRAGMA synchronous = NORMAL;`；
3. 杜绝直接拷贝整个数十 GB 的主数据库，通过 SAF 持久化 URI 树授权直连读取；
4. 用户外部挂载的数据库在初始化前必须触发 `ensureSchemaCompatibility`，使用原生 SQLite 直连探针自动补齐新字段与索引，防止 Room `TableInfo` 校验抛出不可恢复的致命异常。
