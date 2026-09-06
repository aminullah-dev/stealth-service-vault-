import SafeBeautyCore

/// Area and city names in the reader's language.
///
/// `Areas` lives in SafeBeautyCore, which knows nothing about `AppLanguage` —
/// deliberately, so the list itself stays testable with `swift test` and has no
/// opinion about how the app stores a preference. Place names are proper nouns
/// and Dari and Pashto share a script, so one flag is the whole dependency.
extension Areas {
    private static var english: Bool { AppLanguage.current == .english }

    /// What to print where a salon document holds a district.
    ///
    /// The stored value is a key like "KBL_Shirpur", and iOS was printing it.
    /// Android has always shown «شیرپور» here, so the same salon read as two
    /// different places depending on which phone opened it.
    static func label(_ key: String) -> String { labelForKey(key, english: english) }

    static func cityName(_ key: String) -> String { cityLabel(key, english: english) }

    /// One method per type rather than a protocol both conform to: `Area` and
    /// `City` are generated, and a protocol conformance is a thing the
    /// generator would have to keep emitting.
    static func label(of area: Area) -> String { area.label(english: english) }
    static func label(of city: City) -> String { city.label(english: english) }

    /// A salon's address at every level it actually has.
    ///
    /// The two fields hold different levels — «ناحیه ۱۷» and «خیرخانه» — and
    /// showing only the district made the card disagree with the chip that had
    /// just filtered to that salon. Both, joined, is also how the address was
    /// written in the first place: this salon's owner typed
    /// «خیرخانه مینه ناحیه ۱۷» before anything resolved it into two keys.
    static func address(district: String, areaKey: String) -> String {
        let district = label(district)
        guard !areaKey.isEmpty else { return district }
        let area = label(areaKey)
        if district.isEmpty { return area }
        return area == district ? district : "\(district) – \(area)"
    }
}

/// A category key in the reader's language.
///
/// The stored value is "Hair", which is not a word any Dari-reading customer
/// would recognise as a filter. Falls back to the key for a category the server
/// learns before this build does — showing "Waxing" is worse than showing
/// «واکس», and far better than a chip that has no text at all.
extension Categories {
    static func label(_ key: String) -> String {
        switch key {
        case "Hair": L.categoryHair.t
        case "Makeup": L.categoryMakeup.t
        case "Nails": L.categoryNails.t
        case "Skincare": L.categorySkincare.t
        case "Eyebrows": L.categoryEyebrows.t
        default: key
        }
    }
}
