import Foundation
import GRDB

/// 电影分类术语表，映射 `category_glossary`
public struct CategoryGlossaryRecord: Codable, FetchableRecord, TableRecord, PersistableRecord {
    public static let databaseTableName = "category_glossary"

    public var term: String
    public var zh: String
    public var updatedAt: String?

    enum CodingKeys: String, CodingKey {
        case term
        case zh
        case updatedAt = "updated_at"
    }
}

/// 演员属性术语表，映射 `attr_glossary`
public struct AttrGlossaryRecord: Codable, FetchableRecord, TableRecord, PersistableRecord {
    public static let databaseTableName = "attr_glossary"

    public var en: String
    public var zh: String
    public var updatedAt: String?

    enum CodingKeys: String, CodingKey {
        case en
        case zh
        case updatedAt = "updated_at"
    }
}
