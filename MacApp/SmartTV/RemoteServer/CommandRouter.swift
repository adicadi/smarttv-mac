import AppKit

/// Maps validated remote-protocol command messages onto app actions.
/// Pairing enforcement happens in RemoteServer before anything reaches here.
@MainActor
final class CommandRouter {
    private let appState: AppState

    init(appState: AppState) {
        self.appState = appState
    }

    /// Handles a `{"type":"command", ...}` message.
    func handle(action: String, direction: String?) {
        switch action {
        case "navigate":
            if let direction, let dir = MoveDirection(rawValue: direction) {
                appState.moveFocus(dir)
            }
        case "select":
            appState.select()
        case "back":
            appState.back()
        case "home":
            appState.goHome()
        case "volume":
            adjustVolume(up: direction == "up")
        default:
            break
        }
    }

    /// System output volume via AppleScript — streaming sites don't expose a
    /// controllable <video> volume from outside their own player chrome.
    private func adjustVolume(up: Bool) {
        let delta = up ? 7 : -7
        let source = """
        set currentVolume to output volume of (get volume settings)
        set volume output volume (currentVolume + (\(delta)))
        """
        if let script = NSAppleScript(source: source) {
            var error: NSDictionary?
            script.executeAndReturnError(&error)
            if let error {
                NSLog("Volume AppleScript failed: \(error)")
            }
        }
    }
}
