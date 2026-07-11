import Foundation
import ServiceManagement

/// Registers SmartTV to launch at login so DisplayMonitor is always alive to
/// catch HDMI connect events. On macOS 13+ this uses SMAppService; the
/// bundled LaunchAgent.plist is the manual fallback for older systems (copy
/// it to ~/Library/LaunchAgents and fix the executable path).
///
/// Deviation from a separate helper binary: the app itself is the login item.
/// It launches into the regular desktop; if an external display is already
/// attached (or appears later) AppDelegate immediately enters kiosk mode.
enum LoginItemManager {
    static func register() throws {
        if #available(macOS 13.0, *) {
            try SMAppService.mainApp.register()
        } else {
            throw NSError(
                domain: "SmartTV", code: 1,
                userInfo: [NSLocalizedDescriptionKey:
                    "macOS < 13: install LaunchHelper/LaunchAgent.plist into ~/Library/LaunchAgents manually."]
            )
        }
    }

    static func unregister() throws {
        if #available(macOS 13.0, *) {
            try SMAppService.mainApp.unregister()
        }
    }

    static var isRegistered: Bool {
        if #available(macOS 13.0, *) {
            return SMAppService.mainApp.status == .enabled
        }
        return false
    }
}
