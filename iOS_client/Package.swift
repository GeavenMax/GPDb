// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "GPDb",
    platforms: [
        .iOS(.v17),
        .macOS(.v14)
    ],
    products: [
        .library(
            name: "GPDbCore",
            targets: ["GPDbCore"]
        ),
    ],
    dependencies: [
        // GRDB.swift - 高性能 SQLite 数据库映射，对标 Android Room
        .package(url: "https://github.com/groue/GRDB.swift.git", from: "6.29.0"),
        // ZIPFoundation - 纯 Swift ZIP 流式直接读取，对标 Android ZipFile / ZipImageFetcher
        .package(url: "https://github.com/weichsel/ZIPFoundation.git", from: "0.9.19"),
        // Kingfisher - 业界成熟图片加载与三级缓存管理，对标 Android Coil 3
        .package(url: "https://github.com/onevcat/Kingfisher.git", from: "7.12.0")
    ],
    targets: [
        .target(
            name: "GPDbCore",
            dependencies: [
                .product(name: "GRDB", package: "GRDB.swift"),
                .product(name: "ZIPFoundation", package: "ZIPFoundation"),
                .product(name: "Kingfisher", package: "Kingfisher")
            ],
            path: "GPDb"
        ),
    ]
)
