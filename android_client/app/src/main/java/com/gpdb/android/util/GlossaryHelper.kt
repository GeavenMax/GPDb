package com.gpdb.android.util

import java.util.regex.Pattern

object GlossaryHelper {

    private val TERMS: Map<String, String> = mapOf(
        "Auburn" to "赤褐色",
        "Back" to "背部",
        "Balding" to "秃顶",
        "Beard" to "胡须",
        "Biceps" to "肱二头肌",
        "Black" to "黑色",
        "Blond" to "金色",
        "Blue" to "蓝色",
        "Blue/Green" to "蓝/绿",
        "Body Builder" to "健美身材",
        "Brown" to "棕色",
        "Butt" to "臀部",
        "Calf" to "小腿",
        "Caramel" to "焦糖色",
        "Chest" to "胸部",
        "Chin" to "下巴",
        "Curly" to "卷发",
        "Cut" to "已割",
        "Dark Blond" to "深金色",
        "Dark Brown" to "深棕色",
        "Deltoid" to "三角肌",
        "Dick" to "阴茎",
        "Finger" to "手指",
        "Forearm" to "前臂",
        "Goatee" to "山羊胡",
        "Gray" to "灰色",
        "Green" to "绿色",
        "Groin" to "腹股沟",
        "Gymnast" to "体操型",
        "Hand" to "手部",
        "Hazel" to "淡褐色",
        "Heavy" to "偏重",
        "Hip" to "髋部",
        "Jaw Line" to "下颌线",
        "Light" to "偏瘦",
        "Light Brown" to "浅棕色",
        "Long" to "长发",
        "Lower Lip" to "下唇",
        "Medium" to "中等",
        "Muscular" to "肌肉发达",
        "Mustache" to "小胡子",
        "Neck" to "颈部",
        "None" to "无",
        "Normal" to "正常",
        "Olive" to "橄榄色",
        "Red" to "红色",
        "Shaved" to "剃光/光滑",
        "Short" to "短发",
        "Side" to "侧面",
        "Side Burns" to "鬓角",
        "Smooth" to "光滑无毛",
        "Stomach" to "腹部",
        "Stubble" to "胡茬",
        "Swimmer" to "游泳运动员型",
        "Tattoos" to "纹身",
        "Thick" to "粗壮",
        "Thigh" to "大腿",
        "Trim" to "匀称",
        "Uncut" to "未割",
        "White" to "白皙",
        "Wrist" to "手腕",
        "Caucasian" to "白人",
        "Athletic" to "运动健美",
        "Average" to "匀称中等",
        "Hairy" to "体毛浓密",
        "Trimmed" to "修剪整齐",
        "Top" to "攻 (Top)",
        "Bottom" to "受 (Bottom)",
        "Versatile" to "两全 (Vers)",
        "Versatile/Top" to "偏攻 (Vers-Top)",
        "Versatile/Bottom" to "偏受 (Vers-Bottom)"
    )

    private val UNIT_TERMS: Map<String, String> = mapOf(
        "cm" to "厘米",
        "ft" to "英尺",
        "in" to "英寸",
        "kg" to "千克",
        "lbs" to "磅"
    )

    private val MEASURE_PATTERN = Pattern.compile("(\\d+[\\d.\\-]*)\\s*(ft|in|lbs|kg|cm)(?![A-Za-z])", Pattern.CASE_INSENSITIVE)

    fun translate(term: String?, isChinese: Boolean = true): String {
        if (term.isNullOrBlank()) return ""
        if (!isChinese) return term
        val trimmed = term.trim()
        TERMS[trimmed]?.let { return it }

        // Case-insensitive lookup
        val entry = TERMS.entries.firstOrNull { it.key.equals(trimmed, ignoreCase = true) }
        if (entry != null) return entry.value

        // Try measurement replacement
        return trMeasure(trimmed, isChinese)
    }

    fun trMeasure(value: String?, isChinese: Boolean = true): String {
        if (value.isNullOrBlank()) return ""
        if (!isChinese) return value
        val matcher = MEASURE_PATTERN.matcher(value)
        val sb = StringBuffer()
        while (matcher.find()) {
            val digits = matcher.group(1) ?: ""
            val unit = (matcher.group(2) ?: "").lowercase()
            val unitZh = UNIT_TERMS[unit] ?: unit
            matcher.appendReplacement(sb, "$digits$unitZh")
        }
        matcher.appendTail(sb)
        return sb.toString()
    }

    fun trTattoo(raw: String?, isChinese: Boolean = true): String {
        if (raw.isNullOrBlank()) return ""
        if (!isChinese) return raw
        return raw.split(",").map { entry ->
            val trimmed = entry.trim()
            if (trimmed.isEmpty()) return@map ""
            val colon = trimmed.indexOf(":")
            if (colon == -1) {
                translate(trimmed, isChinese)
            } else {
                val head = trimmed.substring(0, colon).trim()
                val tail = trimmed.substring(colon)
                val words = head.split(" ")
                if (words.size == 1) {
                    translate(head, isChinese) + tail
                } else {
                    val translatedHead = words.map { translate(it, isChinese) }.joinToString(" ")
                    translatedHead + tail
                }
            }
        }.filter { it.isNotBlank() }.joinToString("，")
    }

    fun getCleanLabel(key: String, isChinese: Boolean = true): String {
        return if (isChinese) {
            when (key) {
                "height" -> "身高"
                "weight" -> "体重"
                "build", "bodyType" -> "体型"
                "hair" -> "发色"
                "eyes" -> "瞳色"
                "facialHair" -> "胡须"
                "bodyHair" -> "体毛"
                "skin" -> "肤色"
                "dickSize" -> "生理尺寸"
                "foreskin" -> "包皮"
                "tattoos" -> "纹身"
                else -> key
            }
        } else {
            when (key) {
                "height" -> "Height"
                "weight" -> "Weight"
                "build", "bodyType" -> "Build"
                "hair" -> "Hair"
                "eyes" -> "Eyes"
                "facialHair" -> "Facial Hair"
                "bodyHair" -> "Body Hair"
                "skin" -> "Skin"
                "dickSize" -> "Dick Size"
                "foreskin" -> "Foreskin"
                "tattoos" -> "Tattoos"
                else -> key
            }
        }
    }
}
