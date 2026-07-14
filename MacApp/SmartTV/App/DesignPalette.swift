import SwiftUI

/// Hex/HSB approximations of the "SmartTV Remote Prototype" design's OKLCH
/// palette — SwiftUI has no native OKLCH color space, so these are the
/// closest visual match rather than a perceptual conversion.
extension Color {
    init(hex: UInt32, opacity: Double = 1) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: opacity
        )
    }

    /// A service tile's accent as a top→bottom gradient pair, derived from
    /// its hue so every tile reads as one family regardless of which
    /// streaming service it represents.
    static func serviceAccentTop(hue: Double, opacity: Double = 1) -> Color {
        Color(hue: hue / 360, saturation: 0.55, brightness: 0.95, opacity: opacity)
    }
    static func serviceAccentBottom(hue: Double, opacity: Double = 1) -> Color {
        Color(hue: hue / 360, saturation: 0.75, brightness: 0.38, opacity: opacity)
    }

    enum DesignPalette {
        // Grid (home screen) — dark "TV at night" theme.
        static let gridBackgroundTop = Color(hex: 0x14161F)
        static let gridBackgroundBottom = Color(hex: 0x05060A)
        static let gridInk = Color.white
        static let gridSubtext = Color(hex: 0x99A2B5)
        static let gridGlass = Color(hex: 0xFFFFFF, opacity: 0.06)
        static let gridGlassBorder = Color(hex: 0xFFFFFF, opacity: 0.12)
        static let searchFieldBG = Color(hex: 0xFFFFFF, opacity: 0.08)
        static let warmGlow = Color(hex: 0xFF9142, opacity: 0.30)
        static let coolGlow = Color(hex: 0x5B7CFF, opacity: 0.32)

        static let darkBackdrop = Color(hex: 0x0A0A0A, opacity: 0.55)
        static let pinBoxEmpty = Color(hex: 0x2E323A)
        static let accent = Color(hex: 0x5B5FEF)

        static let volumeHUDBG = Color(hex: 0x24272C, opacity: 0.92)
    }
}
