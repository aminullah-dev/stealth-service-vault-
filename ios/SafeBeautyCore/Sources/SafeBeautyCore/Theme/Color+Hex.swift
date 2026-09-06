import SwiftUI

public extension Color {
    /// Lives in the package rather than the app because the generated palettes
    /// are here and need it. 0xRRGGBB, the form Android's Color.kt uses with
    /// its alpha byte dropped — every palette entry there is fully opaque.
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
