import SwiftUI
import SafeBeautyCore

/// The six brands Android offers, in Android's own order.
///
/// Not invented here: these are the themes customers already have on the other
/// platform, and a woman who picks Lavender on her phone and sees Rose on an
/// iPhone would reasonably think it is a different product.
public enum AppBrandTheme: String, CaseIterable, Identifiable, Sendable {
    case rose, lavender, sage, ocean, honey, maroon
    public var id: String { rawValue }

    /// The generated palette's Kotlin name for this brand and mode.
    fileprivate func paletteKey(dark: Bool) -> String {
        rawValue.prefix(1).uppercased() + rawValue.dropFirst() + (dark ? "Dark" : "Light")
    }
}

/// Which theme is showing, and whether it is dark.
///
/// Shaped like LanguageStore for the same reason: UserDefaults is not
/// observable, and the app learned that the hard way when a language change
/// retranslated the words and left the layout mirrored the old way.
@MainActor
@Observable
public final class ThemeStore {
    public static let shared = ThemeStore()

    private static let brandKey = "safebeauty.brand"
    private static let modeKey  = "safebeauty.appearance"

    /// Dark can follow the system, which is what most people expect from a
    /// phone, or be pinned — a woman reading in bright sun may want light even
    /// when her phone has gone dark for the evening.
    public enum Appearance: String, CaseIterable, Identifiable, Sendable {
        case system, light, dark
        public var id: String { rawValue }
    }

    public var brand: AppBrandTheme {
        didSet {
            guard brand != oldValue else { return }
            UserDefaults.standard.set(brand.rawValue, forKey: Self.brandKey)
        }
    }

    public var appearance: Appearance {
        didSet {
            guard appearance != oldValue else { return }
            UserDefaults.standard.set(appearance.rawValue, forKey: Self.modeKey)
        }
    }

    /// Set from the environment on every render of RootView, because
    /// `colorScheme` is not readable from a plain object.
    public var systemIsDark = false

    public var isDark: Bool {
        switch appearance {
        case .system: systemIsDark
        case .light: false
        case .dark: true
        }
    }

    public var palette: Palette {
        allPalettes[brand.paletteKey(dark: isDark)] ?? paletteRoseLight
    }

    /// Anything that must rebuild when the look changes can key off this.
    public var identity: String { "\(brand.rawValue)-\(isDark)" }

    private init() {
        let raw = UserDefaults.standard.string(forKey: Self.brandKey) ?? ""
        brand = AppBrandTheme(rawValue: raw) ?? .rose
        let mode = UserDefaults.standard.string(forKey: Self.modeKey) ?? ""
        appearance = Appearance(rawValue: mode) ?? .system
    }
}
