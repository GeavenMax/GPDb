# GPDb Mobile for Android (项目独立工程目录)

本项目文件夹为 **GPDb Android 移动客户端** 的专用根目录。与桌面 macOS 客户端（`desktop_client/`）实行完全的代码与数据隔离，支持双端独立并行开发与构建。

---

## 目录导航

- [开发架构与设计规范 Wiki (CODING_WIKI.md)](./CODING_WIKI.md)
- [编译计划项目书与构建指导 (COMPILATION_PLAN.md)](./COMPILATION_PLAN.md)

---

## 核心设计要点

1. **两端代码与配置隔离**：
   - 移动端所有的 Gradle 构建配置、Android 资源清单 (`AndroidManifest.xml`)、移动专属触控 UI 及 NDK 脚本均存放于此目录，严禁向外污染 `desktop_client/`。
2. **数据与存储隔离**：
   - Android 遵循严格的 Scoped Storage（分区存储）与 Storage Access Framework (SAF) 权限模型；
   - 运行阶段支持挂载外部存储中已有的 `GPDb.db` 与 `image_cache/`，或单机独立创建私有数据库。
3. **Rust 核心复用**：
   - 核心 SQLite 查询算法复用根目录的 `gpdb-core`，通过 NDK 交叉编译生成 `.so` 动态库。
