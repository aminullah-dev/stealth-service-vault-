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
}
