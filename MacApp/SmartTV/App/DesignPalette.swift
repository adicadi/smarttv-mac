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

    /// Approximates oklch(0.55 0.14 hue) — a mid-saturation service accent.
    static func serviceAccent(hue: Double, opacity: Double = 1) -> Color {
        Color(hue: hue / 360, saturation: 0.5, brightness: 0.8, opacity: opacity)
    }

    enum DesignPalette {
        static let gridInk = Color(hex: 0x10192D)
        static let gridSubtext = Color(hex: 0x8E9BAE)
        static let searchFieldBG = Color(hex: 0xE8ECF2)
        static let warmGlow = Color(hex: 0xFFC44A, opacity: 0.5)
        static let coolGlow = Color(hex: 0xD2DCF0, opacity: 0.75)

        static let darkBackdrop = Color(hex: 0x0A0A0A, opacity: 0.55)
        static let pinBoxEmpty = Color(hex: 0x2E323A)
        static let accent = Color(hex: 0x5B5FEF)

        static let volumeHUDBG = Color(hex: 0x24272C, opacity: 0.92)
    }
}
