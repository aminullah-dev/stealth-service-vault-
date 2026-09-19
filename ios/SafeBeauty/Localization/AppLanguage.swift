import SwiftUI
import UIKit

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
            Self.applyUIKitDirection(current)
        }
    }

    private init() {
        current = AppLanguage.current
        Self.applyUIKitDirection(current)
    }

    /// Tells UIKit which way the app reads.
    ///
    /// `.environment(\.layoutDirection, …)` is a SwiftUI value, and a `Menu`
    /// is not a SwiftUI view: iOS presents it as a UIMenu in its own context,
    /// which reads the app's UIKit direction and never sees the environment.
    /// So the city and neighbourhood dropdowns — the two controls a customer
    /// uses to find a salon near her — opened left-to-right in Dari and
    /// Pashto, ticks on the wrong side and every district name flush against
    /// the wrong edge.
    ///
    /// The comment this file used to carry said UIView.appearance "would fight
    /// the deliberate left-to-right islands this app already uses for phone
    /// numbers, prices and booking codes". That was a reasonable fear and it
    /// is wrong, which was settled by building a throwaway app with both in
    /// it: a forced-RTL appearance with an LTR-forced TextField and booking
    /// code inside it leaves both islands exactly as they were, because
    /// SwiftUI sets semanticContentAttribute per view from the environment and
    /// a per-view value beats the appearance proxy. The proxy only reaches
    /// what SwiftUI never touches — which is the menu, which is the bug.
    ///
    /// Read when a UIView is created, so this is set before any view exists
    /// (init) and again on a language change. A menu builds its views fresh on
    /// every presentation, so the next open is already correct without
    /// rebuilding the tree.
    private static func applyUIKitDirection(_ lang: AppLanguage) {
        UIView.appearance().semanticContentAttribute =
            lang.layoutDirection == .rightToLeft ? .forceRightToLeft : .forceLeftToRight
    }
}

/// Re-applies the app's direction and locale.
///
/// RootView sets both on the view tree, and a `.sheet` does not inherit them:
/// every modal in this app — register, KYC, booking, review, support, the chat
/// with the salon, all fourteen of them — rendered LEFT-TO-RIGHT while the app
/// behind it was in Dari or Pashto. It is not subtle once seen: the close
/// button sits on the wrong side, and in the chat a woman's own messages
/// appeared on the side reserved for the person she is talking to.
///
/// Applied at the root of each sheet's content rather than relying on
/// `applyUIKitDirection` above: that sets the UIKit direction, which is what a
/// UIMenu reads, but a sheet's content is SwiftUI and takes its direction from
/// the environment — which a sheet does not inherit. The two are separate
/// mechanisms and both are needed.
private struct AppDirection: ViewModifier {
    @State private var lang = LanguageStore.shared

    func body(content: Content) -> some View {
        content
            .environment(\.layoutDirection, lang.current.layoutDirection)
            .environment(\.locale, lang.current.locale)
    }
}

extension View {
    /// Put this on the content of every `.sheet`.
    func appDirection() -> some View { modifier(AppDirection()) }
}
