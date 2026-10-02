import Foundation
import GRDB
import SQLite3

/// GRDB 数据库单例管理与 SQLite Schema 架构自愈
public final class DatabaseHolder {
    public static let shared = DatabaseHolder()

    private var dbQueue: DatabaseQueue?
    private var activePath: String?

    public var isReady: Bool {
        return dbQueue != nil
    }

    public var database: DatabaseQueue? {
        return dbQueue
    }

    private init() {}

    /// 挂载并初始化外部 SQLite 数据库
    public func initialize(at path: String) throws {
        if activePath == path && dbQueue != nil {
            return
        }

        // 1. 前置 Schema 自愈检查 (对齐 Android 端 GpdbDatabase.ensureSchemaCompatibility)
        ensureSchemaCompatibility(at: path)

        // 2. 检查路径是否具备写权限
        let isWritable = FileManager.default.isWritableFile(atPath: path)

        var config = Configuration()
        config.qos = .userInitiated
        config.foreignKeysEnabled = true
        config.readonly = !isWritable
        config.prepareDatabase { db in
            if isWritable {
                try? db.execute(sql: "PRAGMA journal_mode = WAL;")
                try? db.execute(sql: "PRAGMA synchronous = NORMAL;")
            }
            try? db.execute(sql: "PRAGMA temp_store = MEMORY;")
            try? db.execute(sql: "PRAGMA mmap_size = 268435456;") // 256MB 内存映射
            try? db.execute(sql: "PRAGMA cache_size = -64000;")   // 64MB 页面缓存
            try? db.execute(sql: "PRAGMA read_uncommitted = 1;")
        }

        let queue = try DatabaseQueue(path: path, configuration: config)

        // 3. 预执行轻量 probe 查询验证连接有效性
        try queue.read { db in
            guard try db.tableExists("movies") else {
                throw NSError(domain: "GPDb", code: 400, userInfo: [NSLocalizedDescriptionKey: "数据库中未找到 movies 影片表，请确保选择的是 GPDb / GEVI 离线数据库文件"])
            }
        }

        self.dbQueue = queue
        self.activePath = path
    }

    /// 前置自愈机制：就地补齐缺失字段与扩展表，彻底杜绝闪退
    public func ensureSchemaCompatibility(at path: String) {
        var db: OpaquePointer?
        guard sqlite3_open(path, &db) == SQLITE_OK, let db = db else { return }
        defer { sqlite3_close(db) }

        // 极速前置检查：若关键表 user_favorites 已存在且 performers.pbc_url 存在，直接 0ms 返回，避免重复编译全量 DDL
        var checkStmt: OpaquePointer?
        var userFavExists = false
        if sqlite3_prepare_v2(db, "SELECT 1 FROM sqlite_master WHERE type='table' AND name='user_favorites';", -1, &checkStmt, nil) == SQLITE_OK {
            if sqlite3_step(checkStmt) == SQLITE_ROW {
                userFavExists = true
            }
            sqlite3_finalize(checkStmt)
        }

        var hasPbcUrl = false
        var hasSjUrl = false
        var colStmt: OpaquePointer?
        if sqlite3_prepare_v2(db, "PRAGMA table_info(performers);", -1, &colStmt, nil) == SQLITE_OK {
            while sqlite3_step(colStmt) == SQLITE_ROW {
                if let namePtr = sqlite3_column_text(colStmt, 1) {
                    let colName = String(cString: namePtr)
                    if colName == "pbc_url" { hasPbcUrl = true }
                    if colName == "sj_url" { hasSjUrl = true }
                }
            }
            sqlite3_finalize(colStmt)
        }

        if userFavExists && hasPbcUrl && hasSjUrl {
            return
        }

        if !hasPbcUrl {
            sqlite3_exec(db, "ALTER TABLE performers ADD COLUMN pbc_url TEXT;", nil, nil, nil)
        }
        if !hasSjUrl {
            sqlite3_exec(db, "ALTER TABLE performers ADD COLUMN sj_url TEXT;", nil, nil, nil)
        }

        // 2. 检查并补全必需扩展表结构
        let ddl = """
        CREATE TABLE IF NOT EXISTS user_favorites (
            entity_type TEXT NOT NULL,
            entity_key TEXT NOT NULL,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            PRIMARY KEY (entity_type, entity_key)
        );
        CREATE TABLE IF NOT EXISTS user_movie_data (
            movie_id INTEGER PRIMARY KEY,
            rating REAL,
            status TEXT,
            notes TEXT,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        );
        CREATE TABLE IF NOT EXISTS user_tags (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT UNIQUE NOT NULL,
            color TEXT DEFAULT '#f59e0b',
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        );
        CREATE TABLE IF NOT EXISTS performer_pbc_profiles (
            performer_id INTEGER PRIMARY KEY,
            pbc_url TEXT NOT NULL,
            pbc_name TEXT NOT NULL,
            birth_name TEXT,
            aliases TEXT,
            birth_date TEXT,
            age INTEGER,
            astrology TEXT,
            birth_place TEXT,
            country TEXT,
            nationality TEXT,
            ethnicity TEXT,
            languages TEXT,
            career_start TEXT,
            career_status TEXT,
            height TEXT,
            weight TEXT,
            penis_size TEXT,
            foreskin TEXT,
            hair TEXT,
            eyes TEXT,
            build TEXT,
            skin TEXT,
            ass_type TEXT,
            butt TEXT,
            body_hair TEXT,
            facial_hair TEXT,
            tattoos TEXT,
            piercings TEXT,
            roles_json TEXT,
            performance_tags TEXT,
            social_links_json TEXT,
            external_ids_json TEXT,
            image_url TEXT,
            bio TEXT,
            pbc_last_edited TEXT,
            scraped_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (performer_id) REFERENCES performers(id) ON DELETE CASCADE
        );
        CREATE TABLE IF NOT EXISTS performer_sj_profiles (
            performer_id INTEGER PRIMARY KEY,
            sj_url TEXT NOT NULL,
            sj_name TEXT NOT NULL,
            model_id TEXT,
            tagline TEXT,
            aliases TEXT,
            years_active TEXT,
            decades TEXT,
            studios TEXT,
            nationality TEXT,
            height TEXT,
            weight TEXT,
            hair TEXT,
            eyes TEXT,
            build TEXT,
            dick_size TEXT,
            dick_type TEXT,
            foreskin TEXT,
            position TEXT,
            sexuality TEXT,
            zodiac TEXT,
            age TEXT,
            shoe TEXT,
            body_hair TEXT,
            tattoos TEXT,
            piercings TEXT,
            social_links_json TEXT,
            image_url TEXT,
            bio TEXT,
            filmography_json TEXT,
            scraped_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (performer_id) REFERENCES performers(id) ON DELETE CASCADE
        );
        """
        sqlite3_exec(db, ddl, nil, nil, nil)
    }

    /// 释放数据库连接
    public func release() {
        self.dbQueue = nil
        self.activePath = nil
    }
}
