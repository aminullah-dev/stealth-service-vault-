import SwiftUI
import SafeBeautyCore

/// The brand, taken from BRANDING.md rather than sampled off a screenshot, so
/// iOS and Android are the same product rather than two that resemble each
/// other.
@MainActor
enum Brand {
    /// Every colour now comes from the palette in force, rather than from six
    /// constants pinned to Rose in light mode.
    ///
    /// These were `static let` and every screen already reads them as
    /// `Brand.ink`, `Brand.accent` and so on — so turning them into lookups is
    /// what themes the whole app at once without touching a single call site.
    /// The values are unchanged for Rose light: they were that palette all
    /// along, which is why the two platforms matched on one theme and only one.
    private static var p: Palette { ThemeStore.shared.palette }

    static var ink: Color    { p.deepRose }      // headings, primary text
    static var accent: Color { p.roseGold }      // the hue everything is built on
    static var gold: Color   { p.warmGold }      // money, badges, emphasis
    static var cream: Color  { p.elegantCream }  // the ground
    static var deep: Color   { p.deeperRose }    // the dark end of the gradient
    static var petal: Color  { p.petalPink }     // the light end

    /// Text below the headline level. Android carries three roles here because
    /// a dark grey literal disappears on a dark background; iOS was using
    /// opacity on `ink` for the same job, which does the same thing wrong.
    static var textStrong: Color { p.textStrong }
    static var textMuted: Color  { p.textMuted }
    static var textFaint: Color  { p.textFaint }

    /// Semantic state, separate from the brand hue so a red still reads as a
    /// warning in every theme.
    static var danger: Color  { p.dangerRed }
    static var warning: Color { p.warningOrange }
    static var success: Color { p.availableGreen }

    /// The surface a card sits on. White is only correct in light mode.
    static var surface: Color { p.dashboardSurface }

    static var isDark: Bool { p.isDark }

    /// The signature gradient, from the palette's own stops.
    static var gradient: LinearGradient {
        LinearGradient(colors: p.brandRose,
                       startPoint: .topLeading, endPoint: .bottomTrailing)
    }

    /// Type.
    ///
    /// Vazirmatn is not a preference — the system default renders Arabic script
    /// badly enough that it is visible at a glance, and this app is read in
    /// Dari and Pashto first. Falls back to the system face until the font
    /// files are added to the bundle, so a missing font degrades rather than
    /// crashes.
    static func font(_ size: CGFloat, _ weight: Font.Weight = .regular) -> Font {
        let name = switch weight {
        case .bold, .heavy, .black: "Vazirmatn-Bold"
        case .medium, .semibold:    "Vazirmatn-Medium"
        default:                    "Vazirmatn-Regular"
        }
        return UIFont(name: name, size: size) == nil
            ? .system(size: size, weight: weight)
            : .custom(name, size: size)
    }
}
