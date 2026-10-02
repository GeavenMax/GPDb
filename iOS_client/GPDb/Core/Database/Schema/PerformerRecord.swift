import Foundation
import GRDB

/// 演员主表实体记录，映射 `performers`
public struct PerformerRecord: Codable, FetchableRecord, TableRecord, PersistableRecord, Identifiable {
    public static let databaseTableName = "performers"

    public var id: Int64
    public var name: String
    public var hair: String?
    public var eyes: String?
    public var bodyHair: String?
    public var facialHair: String?
    public var height: String?
    public var weight: String?
    public var build: String?
    public var skin: String?
    public var dickSize: String?
    public var foreskin: String?
    public var tattoos: String?
    public var notes: String?
    public var imageUrl: String?
    public var bftvUrl: String?
    public var pbcUrl: String?
    public var sjUrl: String?
    public var scrapedAt: String?

    enum CodingKeys: String, CodingKey {
        case id
        case name
        case hair
        case eyes
        case bodyHair = "body_hair"
        case facialHair = "facial_hair"
        case height
        case weight
        case build
        case skin
        case dickSize = "dick_size"
        case foreskin
        case tattoos
        case notes
        case imageUrl = "image_url"
        case bftvUrl = "bftv_url"
        case pbcUrl = "pbc_url"
        case sjUrl = "sj_url"
        case scrapedAt = "scraped_at"
    }
}

/// 演员 PBC 维基全息扩展档案，映射 `performer_pbc_profiles`
public struct PerformerPbcProfileRecord: Codable, FetchableRecord, TableRecord, PersistableRecord {
    public static let databaseTableName = "performer_pbc_profiles"

    public var performerId: Int64
    public var pbcUrl: String
    public var pbcName: String
    public var birthName: String?
    public var aliases: String?
    public var birthDate: String?
    public var age: Int?
    public var astrology: String?
    public var birthPlace: String?
    public var country: String?
    public var nationality: String?
    public var ethnicity: String?
    public var languages: String?
    public var careerStart: String?
    public var careerStatus: String?
    public var height: String?
    public var weight: String?
    public var penisSize: String?
    public var foreskin: String?
    public var hair: String?
    public var eyes: String?
    public var build: String?
    public var skin: String?
    public var assType: String?
    public var butt: String?
    public var bodyHair: String?
    public var facialHair: String?
    public var tattoos: String?
    public var piercings: String?
    public var rolesJson: String?
    public var performanceTags: String?
    public var socialLinksJson: String?
    public var externalIdsJson: String?
    public var imageUrl: String?
    public var bio: String?
    public var pbcLastEdited: String?
    public var scrapedAt: String?

    enum CodingKeys: String, CodingKey {
        case performerId = "performer_id"
        case pbcUrl = "pbc_url"
        case pbcName = "pbc_name"
        case birthName = "birth_name"
        case aliases
        case birthDate = "birth_date"
        case age
        case astrology
        case birthPlace = "birth_place"
        case country
        case nationality
        case ethnicity
        case languages
        case careerStart = "career_start"
        case careerStatus = "career_status"
        case height
        case weight
        case penisSize = "penis_size"
        case foreskin
        case hair
        case eyes
        case build
        case skin
        case assType = "ass_type"
        case butt
        case bodyHair = "body_hair"
        case facialHair = "facial_hair"
        case tattoos
        case piercings
        case rolesJson = "roles_json"
        case performanceTags = "performance_tags"
        case socialLinksJson = "social_links_json"
        case externalIdsJson = "external_ids_json"
        case imageUrl = "image_url"
        case bio
        case pbcLastEdited = "pbc_last_edited"
        case scrapedAt = "scraped_at"
    }

    public var isActive: Bool {
        careerStatus?.lowercased().contains("active") == true
    }
}

/// 演员 SmutJunkies 扩展档案，映射 `performer_sj_profiles`
public struct PerformerSjProfileRecord: Codable, FetchableRecord, TableRecord, PersistableRecord {
    public static let databaseTableName = "performer_sj_profiles"

    public var performerId: Int64
    public var sjUrl: String
    public var sjName: String
    public var modelId: String?
    public var tagline: String?
    public var aliases: String?
    public var yearsActive: String?
    public var decades: String?
    public var studios: String?
    public var nationality: String?
    public var height: String?
    public var weight: String?
    public var hair: String?
    public var eyes: String?
    public var build: String?
    public var dickSize: String?
    public var dickType: String?
    public var foreskin: String?
    public var position: String?
    public var sexuality: String?
    public var zodiac: String?
    public var age: String?
    public var shoe: String?
    public var bodyHair: String?
    public var tattoos: String?
    public var piercings: String?
    public var socialLinksJson: String?
    public var imageUrl: String?
    public var bio: String?
    public var filmographyJson: String?
    public var scrapedAt: String?
    public var updatedAt: String?

    enum CodingKeys: String, CodingKey {
        case performerId = "performer_id"
        case sjUrl = "sj_url"
        case sjName = "sj_name"
        case modelId = "model_id"
        case tagline
        case aliases
        case yearsActive = "years_active"
        case decades
        case studios
        case nationality
        case height
        case weight
        case hair
        case eyes
        case build
        case dickSize = "dick_size"
        case dickType = "dick_type"
        case foreskin
        case position
        case sexuality
        case zodiac
        case age
        case shoe
        case bodyHair = "body_hair"
        case tattoos
        case piercings
        case socialLinksJson = "social_links_json"
        case imageUrl = "image_url"
        case bio
        case filmographyJson = "filmography_json"
        case scrapedAt = "scraped_at"
        case updatedAt = "updated_at"
    }
}
