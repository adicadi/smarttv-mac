import AppKit

/// A single display topology change, produced by diffing NSScreen.screens
/// snapshots across didChangeScreenParametersNotification callbacks.
struct DisplayEvent {
    enum Kind {
        case connected
        case disconnected
    }

    let kind: Kind
    let displayID: CGDirectDisplayID
    let isBuiltin: Bool
    /// Present for `.connected` events; nil for `.disconnected` (the screen
    /// no longer exists by the time the event is delivered).
    let screen: NSScreen?
}

extension NSScreen {
    /// The CoreGraphics display ID backing this screen.
    var displayID: CGDirectDisplayID? {
        deviceDescription[NSDeviceDescriptionKey("NSScreenNumber")] as? CGDirectDisplayID
    }

    var isBuiltin: Bool {
        guard let id = displayID else { return false }
        return CGDisplayIsBuiltin(id) != 0
    }
}
