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

    /// An unselected chip/pill's own background — Android's `ChipInactive`,
    /// generated onto `Palette.chipInactive` for exactly this and, until
    /// 2026-09-09, never exposed here. Every unselected filter chip and the
    /// support chat's "their message" bubble used a bare `Color.white`
    /// instead, which is correct on Rose light and wrong on all twelve other
    /// theme/mode combinations — a stark white pill on a dark screen, reported
    /// from a real TestFlight build on a dark theme.
    static var chipInactive: Color { p.chipInactive }

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
    ///
    /// **Scales with the reader's text size.** `Font.custom(_:size:)` is fixed
    /// forever; `Font.custom(_:size:relativeTo:)` is the same face tied to a
    /// text style, so it grows and shrinks with Settings › Display & Brightness
    /// › Text Size and with the accessibility sizes beyond it. Every one of
    /// this app's text styles came through here, so all of them ignored that
    /// setting completely — a woman who had enlarged her phone's text saw no
    /// difference at all on iPhone, while Android has always scaled because its
    /// sizes are in `sp`. She is the reason this app exists; she should not
    /// have to squint at it.
    ///
    /// The style is picked by size rather than named at each call site, so
    /// nothing else had to change: the point sizes here are already a scale,
    /// and mapping them onto the nearest system style keeps the relative
    /// proportions the design was drawn with.
    static func font(_ size: CGFloat, _ weight: Font.Weight = .regular) -> Font {
        let name = switch weight {
        case .bold, .heavy, .black: "Vazirmatn-Bold"
        case .medium, .semibold:    "Vazirmatn-Medium"
        default:                    "Vazirmatn-Regular"
        }
        let style = textStyle(for: size)
        return UIFont(name: name, size: size) == nil
            ? .system(size: size, weight: weight)
            : .custom(name, size: size, relativeTo: style)
    }

    /// The system text style each of the app's sizes scales against.
    ///
    /// Bands rather than exact matches, because the design uses half-points
    /// (13.5, 14.5) that no system style has. What matters is that a caption
    /// scales like a caption and a title like a title — pinning everything to
    /// `.body` would make the small print grow faster than the headings it
    /// sits under.
    private static func textStyle(for size: CGFloat) -> Font.TextStyle {
        switch size {
        case ..<11.5:  .caption2
        case ..<13:    .caption
        case ..<14.5:  .footnote
        case ..<16:    .subheadline
        case ..<17.5:  .body
        case ..<20:    .title3
        case ..<26:    .title2
        default:       .title
        }
    }
}
