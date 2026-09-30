package com.gpdb.android.data.db.entities

// ============================================================
//  PerformerPbcProfile — 演员 PBC 百科全息档案数据模型
// ============================================================
data class PerformerPbcProfile(
    val performerId: Long,
    val pbcUrl: String,
    val pbcId: String? = null,
    val birthName: String? = null,
    val aliases: String? = null,
    val careerStart: Int? = null,
    val careerEnd: Int? = null,
    val careerStatus: String? = null,
    val bio: String? = null,
    val birthDate: String? = null,
    val birthPlace: String? = null,
    val ethnicity: String? = null,
    val astrology: String? = null,
    val height: String? = null,
    val weight: String? = null,
    val dickSize: String? = null,
    val foreskin: String? = null,
    val tattoos: String? = null,
    val piercings: String? = null,
    val roles: String? = null,
    val socialLinks: String? = null,
    val externalIds: String? = null,
    val tags: String? = null,
    val imageUrl: String? = null,
    val scrapedAt: String? = null
)
