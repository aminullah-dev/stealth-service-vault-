import SwiftUI

/// The brand, taken from BRANDING.md rather than sampled off a screenshot, so
/// iOS and Android are the same product rather than two that resemble each
/// other.
enum Brand {
    static let ink    = Color(hex: 0x8B3A47)   // headings, primary text on cream
    static let accent = Color(hex: 0xB76E79)   // the rose everything is built on
    static let gold   = Color(hex: 0xD4A853)   // money, badges, emphasis
    static let cream  = Color(hex: 0xFFF7FB)   // the light ground
    static let deep   = Color(hex: 0x7A2F3D)   // the dark end of the gradient
    static let petal  = Color(hex: 0xEBA9C0)   // the light end

    /// The signature gradient: petal → accent → deep.
    static let gradient = LinearGradient(
        colors: [petal, accent, deep],
        startPoint: .topLeading, endPoint: .bottomTrailing
    )

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

extension Color {
    init(hex: UInt32) {
        self.init(
            .sRGB,
            red:   Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue:  Double(hex & 0xFF) / 255,
            opacity: 1
        )
    }
}
