import Foundation

// GENERATED from app/src/main/java/com/safebeauty/app/util/Areas.kt by
// scripts/gen-areas.py. Do not hand-edit — regenerate instead.
//
// Areas.kt is the single source of truth; this is its third checked copy, after
// functions/lib/areas.js. AreasParityTests fails if the two drift, the way
// functions/test/areas.test.js does for the server copy.
//
// Why it has to exist at all: the stable ASCII key is what Firestore stores and
// what filtering compares, and it is never what a customer should read. iOS was
// printing the key.

/// The levels of an Afghan city address, which are not interchangeable.
///
/// ناحیه is the municipal district. گذر is the formal unit below it. محله is
/// the informal name people actually use. A form that offers a district and a
/// گذر side by side, as if they were alternatives, is how a salon ends up filed
/// at a level nobody searches.
public enum AreaKind: String, Sendable {
    case district = "DISTRICT"
    case guzar = "GUZAR"
    case neighbourhood = "NEIGHBOURHOOD"
}

public struct Area: Identifiable, Hashable, Sendable {
    public let key: String
    public let fa: String
    public let en: String
    public let kind: AreaKind
    /// The district this sits in, "" where the pairing is not sourced.
    public let parent: String

    public var id: String { key }
    public var cityKey: String { Areas.cityOf(key) }

    /// Place names are proper nouns: Dari and Pashto are written in the same
    /// script, so one Persian-script label serves both.
    public func label(english: Bool) -> String { english ? en : fa }
}

public struct City: Identifiable, Hashable, Sendable {
    public let key: String
    public let fa: String
    public let en: String
    /// False until the city's real districts are known. Nothing offers it.
    public let live: Bool

    public var id: String { key }
    public func label(english: Bool) -> String { english ? en : fa }
}

public enum Areas {
    public static let kabul = "KABUL"

    public static let cities: [City] = [
        City(key: "KABUL", fa: "کابل", en: "Kabul", live: true),
        City(key: "HERAT", fa: "هرات", en: "Herat", live: true),
        City(key: "MAZAR", fa: "مزار شریف", en: "Mazar-e Sharif", live: true),
        City(key: "JALALABAD", fa: "جلال\u{200C}آباد", en: "Jalalabad", live: true),
    ]

    /// Key prefix → city key. The prefix is the first segment of an area key.
    private static let cityByPrefix: [String: String] = [
        "KBL": "KABUL",
        "HRT": "HERAT",
        "MZR": "MAZAR",
        "JAL": "JALALABAD",
    ]

    /// The city an area key belongs to, read off its prefix.
    ///
    /// Herat's first district is numbered one too. An unqualified key would be a
    /// value nobody reading it later could interpret, and any aggregation
    /// grouping by area alone would add two cities together.
    public static func cityOf(_ areaKey: String) -> String {
        cityByPrefix[areaKey.split(separator: "_").first.map(String.init) ?? ""] ?? ""
    }

    public static let liveCities: [City] = cities.filter(\.live)

    public static func cityLabel(_ cityKey: String, english: Bool) -> String {
        cities.first { $0.key == cityKey }?.label(english: english) ?? cityKey
    }

    /// Kabul's 22 municipal districts and the neighbourhoods people search by,
    /// then Herat's, Mazar's and Jalalabad's.
    ///
    /// Herat's rural districts (ولسوالی‌ها) are deliberately absent: they are
    /// not part of the city, and listing them beside its neighbourhoods would
    /// offer a salon an address in the wrong administrative unit.
    public static let areas: [Area] = [
        Area(key: "KBL_D1_OldCity", fa: "ناحیه ۱ – شهر کهنه", en: "District 1 – Old City", kind: .district, parent: ""),
        Area(key: "KBL_D2_ShahreNaw", fa: "ناحیه ۲ – شهرنو", en: "District 2 – Shahr-e Naw", kind: .district, parent: ""),
        Area(key: "KBL_D3_KarteChar", fa: "ناحیه ۳ – کارته چهار", en: "District 3 – Karte Char", kind: .district, parent: ""),
        Area(key: "KBL_D4_KoteSangi", fa: "ناحیه ۴ – کوته سنگی", en: "District 4 – Kote Sangi", kind: .district, parent: ""),
        Area(key: "KBL_D5_Company", fa: "ناحیه ۵ – کمپنی", en: "District 5 – Company", kind: .district, parent: ""),
        Area(key: "KBL_D6_Darulaman", fa: "ناحیه ۶ – دارالامان", en: "District 6 – Darulaman", kind: .district, parent: ""),
        Area(key: "KBL_D7_ChihilSutun", fa: "ناحیه ۷ – چهلستون", en: "District 7 – Chihil Sutun", kind: .district, parent: ""),
        Area(key: "KBL_D8_KarteNaw", fa: "ناحیه ۸ – کارته نو", en: "District 8 – Karte Naw", kind: .district, parent: ""),
        Area(key: "KBL_D9_Makroryan", fa: "ناحیه ۹ – مکروریان", en: "District 9 – Makroryan", kind: .district, parent: ""),
        Area(key: "KBL_D10_WazirAkbarKhan", fa: "ناحیه ۱۰ – وزیراکبرخان", en: "District 10 – Wazir Akbar Khan", kind: .district, parent: ""),
        Area(key: "KBL_D11_KhairKhana", fa: "ناحیه ۱۱ – خیرخانه", en: "District 11 – Khair Khana", kind: .district, parent: ""),
        Area(key: "KBL_D12_AhmadShahBaba", fa: "ناحیه ۱۲ – احمد شاه بابا مینه", en: "District 12 – Ahmad Shah Baba Mina", kind: .district, parent: ""),
        Area(key: "KBL_D13_DashteBarchi", fa: "ناحیه ۱۳ – دشت برچی", en: "District 13 – Dasht-e Barchi", kind: .district, parent: ""),
        Area(key: "KBL_D14_RahmanMina", fa: "ناحیه ۱۴ – رحمان مینه", en: "District 14 – Rahman Mina", kind: .district, parent: ""),
        Area(key: "KBL_D15_KhwajaBughra", fa: "ناحیه ۱۵ – خواجه بغرا", en: "District 15 – Khwaja Bughra", kind: .district, parent: ""),
        Area(key: "KBL_D16", fa: "ناحیه ۱۶", en: "District 16", kind: .district, parent: ""),
        Area(key: "KBL_D17", fa: "ناحیه ۱۷", en: "District 17", kind: .district, parent: ""),
        Area(key: "KBL_D18_Bagrami", fa: "ناحیه ۱۸ – بگرامی", en: "District 18 – Bagrami", kind: .district, parent: ""),
        Area(key: "KBL_D19", fa: "ناحیه ۱۹", en: "District 19", kind: .district, parent: ""),
        Area(key: "KBL_D20", fa: "ناحیه ۲۰", en: "District 20", kind: .district, parent: ""),
        Area(key: "KBL_D21", fa: "ناحیه ۲۱", en: "District 21", kind: .district, parent: ""),
        Area(key: "KBL_D22", fa: "ناحیه ۲۲", en: "District 22", kind: .district, parent: ""),
        Area(key: "KBL_Shahr_e_Naw", fa: "شهرنو", en: "Shahr-e Naw", kind: .neighbourhood, parent: ""),
        Area(key: "KBL_Wazir_Akbar_Khan", fa: "وزیراکبرخان", en: "Wazir Akbar Khan", kind: .neighbourhood, parent: ""),
        Area(key: "KBL_Shirpur", fa: "شیرپور", en: "Shirpur", kind: .neighbourhood, parent: ""),
        Area(key: "KBL_Bibi_Mahro", fa: "بی\u{200C}بی مهرو", en: "Bibi Mahro", kind: .neighbourhood, parent: ""),
        Area(key: "KBL_Taimani", fa: "تایمنی", en: "Taimani", kind: .neighbourhood, parent: ""),
        Area(key: "KBL_Qala_e_Fathullah", fa: "قلعه فتح\u{200C}الله", en: "Qala-e Fathullah", kind: .neighbourhood, parent: ""),
        Area(key: "KBL_Karte_Seh", fa: "کارته سه", en: "Karte Seh", kind: .neighbourhood, parent: ""),
        Area(key: "KBL_Karte_Char", fa: "کارته چهار", en: "Karte Char", kind: .neighbourhood, parent: ""),
        Area(key: "KBL_Karte_Naw", fa: "کارته نو", en: "Karte Naw", kind: .neighbourhood, parent: ""),
        Area(key: "KBL_Karte_Parwan", fa: "کارته پروان", en: "Karte Parwan", kind: .neighbourhood, parent: ""),
        Area(key: "KBL_Karte_Mamurin", fa: "کارته مامورین", en: "Karte Mamurin", kind: .neighbourhood, parent: ""),
        Area(key: "KBL_Dehbori", fa: "دهبوری", en: "Dehbori", kind: .neighbourhood, parent: ""),
        Area(key: "KBL_Kote_Sangi", fa: "کوته سنگی", en: "Kote Sangi", kind: .neighbourhood, parent: ""),
        Area(key: "KBL_Deh_Mazang", fa: "دهمزنگ", en: "Deh Mazang", kind: .neighbourhood, parent: ""),
        Area(key: "KBL_Pul_e_Surkh", fa: "پل سرخ", en: "Pul-e Surkh", kind: .neighbourhood, parent: ""),
        Area(key: "KBL_Silo", fa: "سیلو", en: "Silo", kind: .neighbourhood, parent: ""),
        Area(key: "KBL_Darulaman", fa: "دارالامان", en: "Darulaman", kind: .neighbourhood, parent: ""),
        Area(key: "KBL_Chihil_Sutun", fa: "چهلستون", en: "Chihil Sutun", kind: .neighbourhood, parent: ""),
        Area(key: "KBL_Makroryan", fa: "مکروریان", en: "Makroryan", kind: .neighbourhood, parent: ""),
        Area(key: "KBL_Wazirabad", fa: "وزیرآباد", en: "Wazirabad", kind: .neighbourhood, parent: ""),
        Area(key: "KBL_Khair_Khana", fa: "خیرخانه", en: "Khair Khana", kind: .neighbourhood, parent: ""),
        Area(key: "KBL_Khair_Khana_1", fa: "حصه اول خیرخانه", en: "Khair Khana Part 1", kind: .neighbourhood, parent: ""),
        Area(key: "KBL_Khair_Khana_2", fa: "حصه دوم خیرخانه", en: "Khair Khana Part 2", kind: .neighbourhood, parent: ""),
        Area(key: "KBL_Qala_e_Wahed", fa: "قلعه واحد", en: "Qala-e Wahed", kind: .neighbourhood, parent: ""),
        Area(key: "KBL_Khwaja_Bughra", fa: "خواجه بغرا", en: "Khwaja Bughra", kind: .neighbourhood, parent: ""),
        Area(key: "KBL_Company", fa: "کمپنی", en: "Company", kind: .neighbourhood, parent: ""),
        Area(key: "KBL_Dasht_e_Barchi", fa: "دشت برچی", en: "Dasht-e Barchi", kind: .neighbourhood, parent: ""),
        Area(key: "KBL_Afshar", fa: "افشار", en: "Afshar", kind: .neighbourhood, parent: ""),
        Area(key: "KBL_Pul_e_Khoshk", fa: "پل خشک", en: "Pul-e Khoshk", kind: .neighbourhood, parent: ""),
        Area(key: "KBL_Bagrami", fa: "بگرامی", en: "Bagrami", kind: .neighbourhood, parent: ""),
        Area(key: "KBL_Pul_e_Charkhi", fa: "پل چرخی", en: "Pul-e Charkhi", kind: .neighbourhood, parent: ""),
        Area(key: "KBL_Ahmad_Shah_Baba", fa: "احمد شاه بابا مینه", en: "Ahmad Shah Baba Mina", kind: .neighbourhood, parent: ""),
        Area(key: "KBL_Rahman_Mina", fa: "رحمان مینه", en: "Rahman Mina", kind: .neighbourhood, parent: ""),
        Area(key: "KBL_Khoshhal_Khan", fa: "خوشحال خان مینه", en: "Khoshhal Khan Mina", kind: .neighbourhood, parent: ""),
        Area(key: "KBL_Arzan_Qimat", fa: "ارزان قیمت", en: "Arzan Qimat", kind: .neighbourhood, parent: ""),
        Area(key: "KBL_Shah_Shahid", fa: "شاه شهید", en: "Shah Shahid", kind: .neighbourhood, parent: ""),
        Area(key: "KBL_Sarai_Ghazni", fa: "سرای غزنی", en: "Sarai Ghazni", kind: .neighbourhood, parent: ""),
        Area(key: "KBL_Murad_Khani", fa: "مراد خانی", en: "Murad Khani", kind: .neighbourhood, parent: ""),
        Area(key: "KBL_Bagh_e_Bala", fa: "باغ بالا", en: "Bagh-e Bala", kind: .neighbourhood, parent: ""),
        Area(key: "KBL_Alauddin", fa: "علاءالدین", en: "Alauddin", kind: .neighbourhood, parent: ""),
        Area(key: "KBL_Qala_e_Musa", fa: "قلعه موسی", en: "Qala-e Musa", kind: .neighbourhood, parent: ""),
        Area(key: "KBL_Tapa_e_Salam", fa: "تپه سلام", en: "Tapa-e Salam", kind: .neighbourhood, parent: ""),
        Area(key: "HRT_D01", fa: "ناحیه اول", en: "District 1", kind: .district, parent: ""),
        Area(key: "HRT_D02", fa: "ناحیه دوم", en: "District 2", kind: .district, parent: ""),
        Area(key: "HRT_D03", fa: "ناحیه سوم", en: "District 3", kind: .district, parent: ""),
        Area(key: "HRT_D04", fa: "ناحیه چهارم", en: "District 4", kind: .district, parent: ""),
        Area(key: "HRT_D05", fa: "ناحیه پنجم", en: "District 5", kind: .district, parent: ""),
        Area(key: "HRT_D06", fa: "ناحیه ششم", en: "District 6", kind: .district, parent: ""),
        Area(key: "HRT_D07", fa: "ناحیه هفتم", en: "District 7", kind: .district, parent: ""),
        Area(key: "HRT_D08", fa: "ناحیه هشتم", en: "District 8", kind: .district, parent: ""),
        Area(key: "HRT_D09", fa: "ناحیه نهم", en: "District 9", kind: .district, parent: ""),
        Area(key: "HRT_D10", fa: "ناحیه دهم", en: "District 10", kind: .district, parent: ""),
        Area(key: "HRT_D11", fa: "ناحیه یازدهم", en: "District 11", kind: .district, parent: ""),
        Area(key: "HRT_D12", fa: "ناحیه دوازدهم", en: "District 12", kind: .district, parent: ""),
        Area(key: "HRT_D13", fa: "ناحیه سیزدهم", en: "District 13", kind: .district, parent: ""),
        Area(key: "HRT_D14", fa: "ناحیه چهاردهم", en: "District 14", kind: .district, parent: ""),
        Area(key: "HRT_D15", fa: "ناحیه پانزدهم", en: "District 15", kind: .district, parent: ""),
        Area(key: "HRT_BaghMurad", fa: "باغ مراد", en: "Bagh-e Murad", kind: .neighbourhood, parent: "HRT_D01"),
        Area(key: "HRT_SiMetra", fa: "سی متره", en: "Si Metra", kind: .neighbourhood, parent: "HRT_D01"),
        Area(key: "HRT_BaghZaghan", fa: "باغ زاغان", en: "Bagh-e Zaghan", kind: .neighbourhood, parent: "HRT_D09"),
        Area(key: "HRT_Arabha", fa: "عرب\u{200C}ها", en: "Arab-ha", kind: .neighbourhood, parent: "HRT_D09"),
        Area(key: "HRT_Chaharsuk", fa: "چهارسونک", en: "Chaharsuk", kind: .neighbourhood, parent: "HRT_D09"),
        Area(key: "HRT_Mohebzada", fa: "محب\u{200C}زاده", en: "Mohebzada", kind: .neighbourhood, parent: "HRT_D09"),
        Area(key: "HRT_RafaBaradaran", fa: "رفا و برادران", en: "Rafa wa Baradaran", kind: .neighbourhood, parent: "HRT_D09"),
        Area(key: "HRT_Arbabzadaha", fa: "ارباب\u{200C}زاده\u{200C}ها", en: "Arbabzada-ha", kind: .neighbourhood, parent: "HRT_D09"),
        Area(key: "HRT_Firozabad", fa: "فیروزآباد", en: "Firozabad", kind: .neighbourhood, parent: "HRT_D10"),
        Area(key: "HRT_Karabad", fa: "کارآباد", en: "Karabad", kind: .neighbourhood, parent: "HRT_D10"),
        Area(key: "HRT_PayanAb", fa: "پایان آب", en: "Payan-e Ab", kind: .neighbourhood, parent: "HRT_D10"),
        Area(key: "HRT_Shaidayi", fa: "شیدایی", en: "Shaidayi", kind: .neighbourhood, parent: "HRT_D15"),
        Area(key: "JAL_D01", fa: "ناحیه اول", en: "District 1", kind: .district, parent: ""),
        Area(key: "JAL_D02", fa: "ناحیه دوم", en: "District 2", kind: .district, parent: ""),
        Area(key: "JAL_D03", fa: "ناحیه سوم", en: "District 3", kind: .district, parent: ""),
        Area(key: "JAL_D04", fa: "ناحیه چهارم", en: "District 4", kind: .district, parent: ""),
        Area(key: "JAL_D05", fa: "ناحیه پنجم", en: "District 5", kind: .district, parent: ""),
        Area(key: "JAL_D06", fa: "ناحیه ششم", en: "District 6", kind: .district, parent: ""),
        Area(key: "JAL_D07", fa: "ناحیه هفتم", en: "District 7", kind: .district, parent: ""),
        Area(key: "JAL_D08", fa: "ناحیه هشتم", en: "District 8", kind: .district, parent: ""),
        Area(key: "JAL_D09", fa: "ناحیه نهم", en: "District 9", kind: .district, parent: ""),
        Area(key: "MZR_D01", fa: "ناحیه اول", en: "District 1", kind: .district, parent: ""),
        Area(key: "MZR_D02", fa: "ناحیه دوم", en: "District 2", kind: .district, parent: ""),
        Area(key: "MZR_D03", fa: "ناحیه سوم", en: "District 3", kind: .district, parent: ""),
        Area(key: "MZR_D04", fa: "ناحیه چهارم", en: "District 4", kind: .district, parent: ""),
        Area(key: "MZR_D05", fa: "ناحیه پنجم", en: "District 5", kind: .district, parent: ""),
        Area(key: "MZR_D06", fa: "ناحیه ششم", en: "District 6", kind: .district, parent: ""),
        Area(key: "MZR_D07", fa: "ناحیه هفتم", en: "District 7", kind: .district, parent: ""),
        Area(key: "MZR_D08", fa: "ناحیه هشتم", en: "District 8", kind: .district, parent: ""),
        Area(key: "MZR_D09", fa: "ناحیه نهم", en: "District 9", kind: .district, parent: ""),
        Area(key: "MZR_D10", fa: "ناحیه دهم", en: "District 10", kind: .district, parent: ""),
        Area(key: "MZR_D11", fa: "ناحیه یازدهم", en: "District 11", kind: .district, parent: ""),
        Area(key: "MZR_D12", fa: "ناحیه دوازدهم", en: "District 12", kind: .district, parent: ""),
        Area(key: "MZR_GuzarQarghan", fa: "گذر قرغان", en: "Guzar-e Qarghan", kind: .guzar, parent: "MZR_D02"),
        Area(key: "MZR_GuzarSeDukan", fa: "گذر سه\u{200C}دکان", en: "Guzar-e Se Dukan", kind: .guzar, parent: "MZR_D03"),
        Area(key: "MZR_JoyAjar", fa: "جوی آجر", en: "Joy-e Ajar", kind: .neighbourhood, parent: "MZR_D04"),
        Area(key: "MZR_Faqirabad", fa: "فقیرآباد", en: "Faqirabad", kind: .neighbourhood, parent: "MZR_D05"),
        Area(key: "MZR_GuzarHayat", fa: "گذر حیات", en: "Guzar-e Hayat", kind: .guzar, parent: "MZR_D06"),
        Area(key: "MZR_GuzarTokhta", fa: "گذر توخته", en: "Guzar-e Tokhta", kind: .guzar, parent: "MZR_D07"),
        Area(key: "MZR_DashtShor", fa: "دشت شور", en: "Dasht-e Shor", kind: .neighbourhood, parent: "MZR_D08"),
        Area(key: "MZR_KhalidBinWalid", fa: "پروژه خالد بن ولید", en: "Khalid bin Walid Project", kind: .neighbourhood, parent: "MZR_D08"),
        Area(key: "MZR_GuzarSadeqiya", fa: "گذر صادقیه", en: "Guzar-e Sadeqiya", kind: .guzar, parent: "MZR_D10"),
    ]

    /// Stable keys only (parallel to `areas`); what gets stored and filtered.
    public static let keys: [String] = areas.map(\.key)

    public static func areasIn(_ cityKey: String) -> [Area] {
        areas.filter { $0.cityKey == cityKey }
    }

    public static func districtsIn(_ cityKey: String) -> [Area] {
        areasIn(cityKey).filter { $0.kind == .district }
    }

    /// Neighbourhoods known to sit in `districtKey`. Empty where none are
    /// sourced — Kabul's 42 have no parent recorded and are left empty rather
    /// than guessed, because putting a salon in a district it is not in would be
    /// a wrong answer no one could see.
    public static func neighbourhoodsIn(_ districtKey: String) -> [Area] {
        areas.filter { $0.kind != .district && $0.parent == districtKey }
    }

    /// True when `key` is a ناحیه — the level a salon's districtKey holds.
    public static func isDistrict(_ key: String) -> Bool {
        areas.first { $0.key == key }?.kind == .district
    }

    /// Every area a customer can filter by in `cityKey`: its ناحیه‌ها, and under
    /// each, the گذرها and محله‌ها recorded inside it.
    ///
    /// Ordered district-then-its-children so the list reads as an address rather
    /// than as two alphabetical lists stapled together.
    public static func filterableIn(_ cityKey: String) -> [Area] {
        districtsIn(cityKey).flatMap { [$0] + neighbourhoodsIn($0.key) }
    }

    /// Only the گذرها of `districtKey` — the formal unit, not the colloquial one.
    public static func guzarsIn(_ districtKey: String) -> [Area] {
        areas.filter { $0.kind == .guzar && $0.parent == districtKey }
    }

    /// Every salon in production stored its district before the keys carried a
    /// city, so "D9_Makroryan" is what is on the document and "KBL_D9_Makroryan"
    /// is what this list holds. The server resolves these through LEGACY_KEYS;
    /// without the same map here the customer reads the raw key on the card.
    /// Kabul only — it was the only city when those rows were written.
    private static let legacyKeys: [String: String] = Dictionary(
        uniqueKeysWithValues: areas
            .filter { $0.key.hasPrefix("KBL_") }
            .map { (String($0.key.dropFirst("KBL_".count)), $0.key) }
    )

    /// What `key` is called in this list, for a value that may be a pre-prefix
    /// key or free text the salon typed.
    ///
    /// The server's normalizeDistrict has a third branch that fuzzy-matches free
    /// text; that one stays server-side, where it runs once at write time rather
    /// than on every row of every list.
    public static func canonicalKey(_ key: String) -> String {
        areas.contains { $0.key == key } ? key : (legacyKeys[key] ?? key)
    }

    /// The label for `key`, or `key` itself when it is not one.
    ///
    /// Falling back to the key is deliberate: older salons hold free text like
    /// "خیرخانه مینه ناحیه ۱۷", and showing what the salon actually wrote is
    /// better than showing nothing.
    public static func labelForKey(_ key: String, english: Bool) -> String {
        areas.first { $0.key == canonicalKey(key) }?.label(english: english) ?? key
    }
}
