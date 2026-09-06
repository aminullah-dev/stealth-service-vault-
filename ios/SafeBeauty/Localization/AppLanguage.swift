import SwiftUI

/// The three languages, and which way each one reads.
///
/// Android keeps every user-facing string in AppStrings.kt with a value in all
/// three blocks, and the rule there is that a key appears four times or it is
/// not done. iOS uses .strings catalogues for the same reason, and this enum is
/// what selects between them.
///
/// Dari is the default rather than English. That is not a courtesy: the product
/// is used in Dari, and defaulting to English means every screen gets its first
/// look in a language and a direction that no user has.
enum AppLanguage: String, CaseIterable, Identifiable {
    case dari = "fa"
    case pashto = "ps"
    case english = "en"

    var id: String { rawValue }

    /// What the language calls itself. Never a translation of the name — a
    /// person scanning a picker looks for their own word, not yours.
    var endonym: String {
        switch self {
        case .dari: "دری"
        case .pashto: "پښتو"
        case .english: "English"
        }
    }

    var layoutDirection: LayoutDirection {
        switch self {
        case .dari, .pashto: .rightToLeft
        case .english: .leftToRight
        }
    }

    var locale: Locale { Locale(identifier: rawValue) }

    /// Whether tracking may be applied to text in this language.
    ///
    /// Arabic script is connected: letter-spacing breaks the joins and makes a
    /// word look misspelled rather than styled. BRANDING.md states this and it
    /// is the kind of rule a designer applies globally without knowing.
    var allowsLetterSpacing: Bool { self == .english }

    private static let storageKey = "safebeauty.language"

    /// The chosen language, or the best match for the phone's own settings.
    static var current: AppLanguage {
        get {
            if let raw = UserDefaults.standard.string(forKey: storageKey),
               let lang = AppLanguage(rawValue: raw) {
                return lang
            }
            // Falls back to Dari rather than English for an unrecognised
            // locale: a woman in Kabul whose phone is set to Urdu is far more
            // likely to read Dari than English.
            for code in Locale.preferredLanguages {
                if code.hasPrefix("fa") || code.hasPrefix("prs") { return .dari }
                if code.hasPrefix("ps") { return .pashto }
                if code.hasPrefix("en") { return .english }
            }
            return .dari
        }
        set { UserDefaults.standard.set(newValue.rawValue, forKey: Self.storageKey) }
    }
}

/// The chosen language, as one observable value the whole app shares.
///
/// It needs to be shared because `AppLanguage.current` is a UserDefaults
/// property, and nothing observes UserDefaults: RootView held its own @State
/// copy and applied `layoutDirection` and `locale` from it, while the picker in
/// Profile wrote through to UserDefaults and updated a *different* @State. So
/// changing the language from the account tab retranslated the text and left
/// the layout mirrored the old way until the app was relaunched — the one place
/// it is most obviously wrong, since Dari and Pashto are right-to-left and
/// English is not.
///
/// Same shape as AuthService.shared, so it is read the same way at the call site.
@MainActor
@Observable
final class LanguageStore {
    static let shared = LanguageStore()

    var current: AppLanguage {
        didSet {
            guard current != oldValue else { return }
            AppLanguage.current = current   // the durable copy
            // And the server's copy, which decides what language a PUSH is
            // written in. Without this she changes the language, the app
            // switches, and her notifications keep arriving in the old one.
            PushService.shared.writeLanguage()
        }
    }

    private init() { current = AppLanguage.current }
}
