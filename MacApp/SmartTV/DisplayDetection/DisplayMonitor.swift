import AppKit

/// Watches for displays being connected/disconnected by diffing
/// NSScreen.screens on every screen-parameter change notification.
@MainActor
final class DisplayMonitor {
    var onEvent: ((DisplayEvent) -> Void)?

    private var snapshot: [CGDirectDisplayID: Bool] = [:] // id -> isBuiltin
    private var observer: NSObjectProtocol?

    func start() {
        snapshot = Self.currentSnapshot()
        observer = NotificationCenter.default.addObserver(
            forName: NSApplication.didChangeScreenParametersNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            Task { @MainActor in
                self?.diff()
            }
        }
    }

    func stop() {
        if let observer { NotificationCenter.default.removeObserver(observer) }
        observer = nil
    }

    private func diff() {
        let current = Self.currentSnapshot()
        defer { snapshot = current }

        // Newly connected displays: emit with the live NSScreen so the caller
        // can target exactly the screen that appeared in this diff.
        for (id, isBuiltin) in current where snapshot[id] == nil {
            let screen = NSScreen.screens.first { $0.displayID == id }
            onEvent?(DisplayEvent(kind: .connected, displayID: id, isBuiltin: isBuiltin, screen: screen))
        }
        for (id, isBuiltin) in snapshot where current[id] == nil {
            onEvent?(DisplayEvent(kind: .disconnected, displayID: id, isBuiltin: isBuiltin, screen: nil))
        }
    }

    private static func currentSnapshot() -> [CGDirectDisplayID: Bool] {
        var result: [CGDirectDisplayID: Bool] = [:]
        for screen in NSScreen.screens {
            guard let id = screen.displayID else { continue }
            result[id] = screen.isBuiltin
        }
        return result
    }

    /// The external screen to use if one is already attached at launch.
    /// On Macs with no built-in display (mini/Studio) every screen is
    /// "external"; in that case require a second screen before treating one
    /// as the TV, and prefer the largest.
    static func currentExternalScreen() -> NSScreen? {
        let screens = NSScreen.screens
        let externals = screens.filter { !$0.isBuiltin }
        if externals.count == screens.count {
            // No built-in display at all: only kiosk if there are 2+ screens.
            guard screens.count > 1 else { return nil }
        }
        return externals.max { a, b in
            a.frame.width * a.frame.height < b.frame.width * b.frame.height
        }
    }
}
