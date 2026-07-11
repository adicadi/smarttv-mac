import AppKit
import ApplicationServices

/// Maps validated remote-protocol command messages onto app actions.
/// Pairing enforcement happens in RemoteServer before anything reaches here.
@MainActor
final class CommandRouter {
    private let appState: AppState
    private var didPromptAccessibility = false

    init(appState: AppState) {
        self.appState = appState
    }

    /// Handles the payload of a `{"type":"command", ...}` message.
    func handle(_ json: [String: Any]) {
        let direction = json["direction"] as? String
        switch json["action"] as? String ?? "" {
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
        case "pointer_move":
            movePointer(
                dx: json["dx"] as? Double ?? 0,
                dy: json["dy"] as? Double ?? 0
            )
        case "pointer_click":
            clickPointer()
        case "scroll":
            scroll(dy: json["dy"] as? Double ?? 0)
        case "text":
            if let value = json["value"] as? String, !value.isEmpty {
                typeText(value)
            }
        case "key":
            if let value = json["value"] as? String {
                pressKey(named: value)
            }
        default:
            break
        }
    }

    // MARK: - Volume

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

    // MARK: - Pointer & keyboard injection (CGEvent)
    // Requires the Accessibility permission; the first use prompts for it.

    /// True when the app may post synthetic input events. Prompts the user
    /// (once) via the system Accessibility dialog when it can't.
    private func ensureInputPermission() -> Bool {
        if AXIsProcessTrusted() { return true }
        if !didPromptAccessibility {
            didPromptAccessibility = true
            let options = [kAXTrustedCheckOptionPrompt.takeUnretainedValue(): true] as CFDictionary
            AXIsProcessTrustedWithOptions(options)
            appState.errorMessage =
                "Trackpad/keyboard from the remote needs the Accessibility permission: System Settings → Privacy & Security → Accessibility → enable SmartTV."
        }
        return false
    }

    private func currentPointerLocation() -> CGPoint {
        CGEvent(source: nil)?.location ?? .zero
    }

    /// Clamps a point to the union of all active displays so the cursor
    /// can't be flung off-screen.
    private func clamp(_ point: CGPoint) -> CGPoint {
        var displays = [CGDirectDisplayID](repeating: 0, count: 16)
        var count: UInt32 = 0
        CGGetActiveDisplayList(16, &displays, &count)
        var best: CGPoint?
        var bestDistance = CGFloat.greatestFiniteMagnitude
        for display in displays.prefix(Int(count)) {
            let bounds = CGDisplayBounds(display)
            let clamped = CGPoint(
                x: min(max(point.x, bounds.minX), bounds.maxX - 1),
                y: min(max(point.y, bounds.minY), bounds.maxY - 1)
            )
            let distance = abs(clamped.x - point.x) + abs(clamped.y - point.y)
            if distance < bestDistance {
                bestDistance = distance
                best = clamped
            }
        }
        return best ?? point
    }

    private func movePointer(dx: Double, dy: Double) {
        guard ensureInputPermission() else { return }
        let current = currentPointerLocation()
        let target = clamp(CGPoint(x: current.x + dx, y: current.y + dy))
        CGEvent(
            mouseEventSource: nil, mouseType: .mouseMoved,
            mouseCursorPosition: target, mouseButton: .left
        )?.post(tap: .cghidEventTap)
    }

    private func clickPointer() {
        guard ensureInputPermission() else { return }
        let location = currentPointerLocation()
        for type in [CGEventType.leftMouseDown, .leftMouseUp] {
            CGEvent(
                mouseEventSource: nil, mouseType: type,
                mouseCursorPosition: location, mouseButton: .left
            )?.post(tap: .cghidEventTap)
        }
    }

    private func scroll(dy: Double) {
        guard ensureInputPermission() else { return }
        CGEvent(
            scrollWheelEvent2Source: nil, units: .pixel,
            wheelCount: 1, wheel1: Int32(dy), wheel2: 0, wheel3: 0
        )?.post(tap: .cghidEventTap)
    }

    /// Types arbitrary text by attaching a unicode string to synthetic key
    /// events — works regardless of keyboard layout.
    private func typeText(_ text: String) {
        guard ensureInputPermission() else { return }
        for character in text {
            let chars = Array(String(character).utf16)
            for keyDown in [true, false] {
                let event = CGEvent(keyboardEventSource: nil, virtualKey: 0, keyDown: keyDown)
                event?.keyboardSetUnicodeString(stringLength: chars.count, unicodeString: chars)
                event?.post(tap: .cghidEventTap)
            }
        }
    }

    private func pressKey(named name: String) {
        guard ensureInputPermission() else { return }
        let virtualKey: CGKeyCode?
        switch name {
        case "return": virtualKey = 36
        case "backspace", "delete": virtualKey = 51
        case "space": virtualKey = 49
        case "escape": virtualKey = 53
        default: virtualKey = nil
        }
        guard let virtualKey else { return }
        for keyDown in [true, false] {
            CGEvent(keyboardEventSource: nil, virtualKey: virtualKey, keyDown: keyDown)?
                .post(tap: .cghidEventTap)
        }
    }
}
