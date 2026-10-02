import Foundation

/// 中英双语影库词汇与生理指标术语转换器 (深度对标 Android GlossaryHelper)
public enum GlossaryHelper {

    private static let terms: [String: String] = [
        "Auburn": "赤褐色",
        "Back": "背部",
        "Balding": "秃顶",
        "Beard": "胡须",
        "Biceps": "肱二头肌",
        "Black": "黑色",
        "Blond": "金色",
        "Blonde": "金色",
        "Blue": "蓝色",
        "Blue/Green": "蓝/绿",
        "Body Builder": "健美身材",
        "Brown": "棕色",
        "Butt": "臀部",
        "Calf": "小腿",
        "Caramel": "焦糖色",
        "Chest": "胸部",
        "Chin": "下巴",
        "Curly": "卷发",
        "Cut": "已割",
        "Dark Blond": "深金色",
        "Dark Brown": "深棕色",
        "Deltoid": "三角肌",
        "Dick": "阴茎",
        "Finger": "手指",
        "Forearm": "前臂",
        "Goatee": "山羊胡",
        "Gray": "灰色",
        "Grey": "灰色",
        "Green": "绿色",
        "Groin": "腹股沟",
        "Gymnast": "体操型",
        "Hand": "手部",
        "Hazel": "淡褐色",
        "Heavy": "偏重",
        "Hip": "髋部",
        "Jaw Line": "下颌线",
        "Light": "偏瘦",
        "Light Brown": "浅棕色",
        "Long": "长发",
        "Lower Lip": "下唇",
        "Medium": "中等",
        "Muscular": "肌肉发达",
        "Mustache": "小胡子",
        "Neck": "颈部",
        "None": "无",
        "Normal": "正常",
        "Olive": "橄榄色",
        "Red": "红色",
        "Shaved": "剃光/光滑",
        "Short": "短发",
        "Side": "侧面",
        "Side Burns": "鬓角",
        "Smooth": "光滑无毛",
        "Stomach": "腹部",
        "Stubble": "胡茬",
        "Clean Shaven": "无须光滑",
        "Swimmer": "游泳运动员型",
        "Tattoos": "纹身",
        "Thick": "粗壮",
        "Thigh": "大腿",
        "Trim": "匀称",
        "Uncut": "未割",
        "White": "白皙",
        "Wrist": "手腕",
        "Caucasian": "白人",
        "Athletic": "运动健美",
        "Average": "匀称中等",
        "Hairy": "体毛浓密",
        "Trimmed": "修剪整齐",
        "Top": "攻 (Top)",
        "Bottom": "受 (Bottom)",
        "Versatile": "两全 (Vers)",
        "Versatile/Top": "偏攻 (Vers-Top)",
        "Versatile/Bottom": "偏受 (Vers-Bottom)",
        "Slim": "修长苗条",
        "Bear": "熊族健壮",
        "Daddy": "成熟大叔",
        "Twink": "清秀少年",
        "Stocky": "粗壮厚实",
        "Circumcised": "已割",
        "Intact": "未割"
    ]

    private static let unitTerms: [String: String] = [
        "cm": "厘米",
        "ft": "英尺",
        "in": "英寸",
        "kg": "千克",
        "lbs": "磅"
    ]

    private static let measureRegex: NSRegularExpression? = {
        try? NSRegularExpression(pattern: "(\\d+[\\d.\\-]*)\\s*(ft|in|lbs|kg|cm)(?![A-Za-z])", options: .caseInsensitive)
    }()

    /// 翻译术语 (不区分大小写查找)
    public static func translate(_ term: String?, isChinese: Bool = true) -> String {
        guard let term = term?.trimmingCharacters(in: .whitespacesAndNewlines), !term.isEmpty else { return "" }
        if !isChinese { return term }

        if let direct = terms[term] { return direct }

        // 不区分大小写匹配
        for (k, v) in terms {
            if k.caseInsensitiveCompare(term) == .orderedSame {
                return v
            }
        }

        // 尝试尺寸度量翻译
        let measured = trMeasure(term, isChinese: isChinese)
        if measured != term { return measured }

        return term
    }

    /// 翻译度量衡 (如 "6 ft 1 in" -> "6英尺 1英寸", "180 lbs" -> "180磅")
    public static func trMeasure(_ value: String?, isChinese: Bool = true) -> String {
        guard let value = value?.trimmingCharacters(in: .whitespacesAndNewlines), !value.isEmpty else { return "" }
        if !isChinese { return value }

        guard let regex = measureRegex else { return value }
        let nsString = value as NSString
        let matches = regex.matches(in: value, options: [], range: NSRange(location: 0, length: nsString.length))

        if matches.isEmpty { return value }

        var result = value
        for match in matches.reversed() {
            guard match.numberOfRanges >= 3 else { continue }
            let digitsRange = match.range(at: 1)
            let unitRange = match.range(at: 2)
            let digits = nsString.substring(with: digitsRange)
            let unit = nsString.substring(with: unitRange).lowercased()
            let unitZh = unitTerms[unit] ?? unit

            let fullRange = match.range(at: 0)
            if let swiftRange = Range(fullRange, in: result) {
                result.replaceSubrange(swiftRange, with: "\(digits)\(unitZh)")
            }
        }

        return result
    }

    /// 翻译纹身描述 (如 "Arm: Dragon, Chest: Tribal")
    public static func trTattoo(_ raw: String?, isChinese: Bool = true) -> String {
        guard let raw = raw?.trimmingCharacters(in: .whitespacesAndNewlines), !raw.isEmpty else { return "" }
        if !isChinese { return raw }

        let parts = raw.components(separatedBy: ",")
        let translated = parts.map { part -> String in
            let trimmed = part.trimmingCharacters(in: .whitespacesAndNewlines)
            if trimmed.isEmpty { return "" }

            if let colonIdx = trimmed.firstIndex(of: ":") {
                let head = String(trimmed[..<colonIdx]).trimmingCharacters(in: .whitespacesAndNewlines)
                let tail = String(trimmed[trimmed.index(after: colonIdx)...])
                let translatedHead = translate(head, isChinese: true)
                return "\(translatedHead):\(tail)"
            } else {
                return translate(trimmed, isChinese: true)
            }
        }.filter { !$0.isEmpty }

        return translated.isEmpty ? raw : translated.joined(separator: "，")
    }

    /// 格式化星座 (如 "Aries" -> "白羊座 ♈")
    public static func formatAstro(_ astro: String?) -> String? {
        guard let astro = astro?.trimmingCharacters(in: .whitespacesAndNewlines), !astro.isEmpty else { return nil }
        switch astro.lowercased() {
        case "aries": return "白羊座 ♈"
        case "taurus": return "金牛座 ♉"
        case "gemini": return "双子座 ♊"
        case "cancer": return "巨蟹座 ♋"
        case "leo": return "狮子座 ♌"
        case "virgo": return "处女座 ♍"
        case "libra": return "天秤座 ♎"
        case "scorpio": return "天蝎座 ♏"
        case "sagittarius": return "射手座 ♐"
        case "capricorn": return "摩羯座 ♑"
        case "aquarius": return "水瓶座 ♒"
        case "pisces": return "双鱼座 ♓"
        default: return astro
        }
    }

    /// 格式化生日并追加当前年龄
    public static func formatBirth(_ birth: String?) -> String? {
        guard let birth = birth?.trimmingCharacters(in: .whitespacesAndNewlines), !birth.isEmpty else { return nil }
        let pattern = "^(\\d{4})"
        if let match = birth.range(of: pattern, options: .regularExpression) {
            let yearStr = String(birth[match])
            if let birthYear = Int(yearStr) {
                let currentYear = Calendar.current.component(.year, from: Date())
                let age = currentYear - birthYear
                if age >= 18 && age <= 99 {
                    return "\(birth) (\(age)岁)"
                }
            }
        }
        return birth
    }

    /// 获取规范的中文标签
    public static func getCleanLabel(_ key: String, isChinese: Bool = true) -> String {
        guard isChinese else { return key }
        switch key.lowercased() {
        case "height": return "身高"
        case "weight": return "体重"
        case "build", "bodytype": return "体型"
        case "hair": return "发色"
        case "eyes": return "瞳色"
        case "facialhair": return "胡须"
        case "bodyhair": return "体毛"
        case "skin": return "肤色"
        case "dicksize", "penissize": return "生理尺寸"
        case "foreskin": return "包皮"
        case "tattoos": return "纹身"
        case "birthdate": return "生日"
        case "astrology": return "星座"
        case "birthplace": return "籍贯"
        case "ethnicity": return "族裔"
        default: return key
        }
    }
}
