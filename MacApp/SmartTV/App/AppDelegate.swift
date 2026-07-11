import AppKit
import SwiftUI

/// Owns the single main window, moves it between displays, and wires together
/// display detection, the remote server and command routing.
@MainActor
final class AppDelegate: NSObject, NSApplicationDelegate {
    private(set) var appState = AppState()
    private var window: NSWindow!
    private let displayMonitor = DisplayMonitor()
    private var remoteServer: RemoteServer!
    private var commandRouter: CommandRouter!
    private var keyMonitor: Any?

    /// The display the window is currently fullscreened on, if any.
    private var kioskDisplayID: CGDirectDisplayID?

    func applicationDidFinishLaunching(_ notification: Notification) {
        makeWindow()

        commandRouter = CommandRouter(appState: appState)
        remoteServer = RemoteServer(router: commandRouter, appState: appState)
        remoteServer.start()

        appState.onStateChange = { [weak self] in
            self?.remoteServer.broadcastState()
        }

        displayMonitor.onEvent = { [weak self] event in
            Task { @MainActor in
                self?.handleDisplayEvent(event)
            }
        }
        displayMonitor.start()

        installKeyMonitor()

        // If launched (e.g. by the login item) while an external display is
        // already attached, go straight to kiosk mode on it.
        if let external = DisplayMonitor.currentExternalScreen() {
            enterKiosk(on: external)
        } else {
            window.center()
            window.makeKeyAndOrderFront(nil)
            NSApp.activate(ignoringOtherApps: true)
        }
    }

    func applicationWillTerminate(_ notification: Notification) {
        if let keyMonitor { NSEvent.removeMonitor(keyMonitor) }
        remoteServer.stop()
        displayMonitor.stop()
    }

    // MARK: - Window / kiosk management

    private func makeWindow() {
        let content = ContentView().environmentObject(appState)
        window = KioskWindow(
            contentRect: NSRect(x: 0, y: 0, width: 1280, height: 800),
            styleMask: [.titled, .closable, .miniaturizable, .resizable],
            backing: .buffered,
            defer: false
        )
        window.title = "SmartTV"
        window.contentView = NSHostingView(rootView: content)
        window.isReleasedWhenClosed = false
    }

    private func handleDisplayEvent(_ event: DisplayEvent) {
        switch event.kind {
        case .connected:
            guard !event.isBuiltin, let screen = event.screen else { return }
            enterKiosk(on: screen)
        case .disconnected:
            // Only react if the display we were fullscreen on went away.
            guard event.displayID == kioskDisplayID else { return }
            exitKiosk()
        }
    }

    /// Borderless fullscreen on the given screen (kiosk-style; avoids the
    /// macOS fullscreen-space animation and Spaces interactions).
    private func enterKiosk(on screen: NSScreen) {
        kioskDisplayID = screen.displayID
        window.styleMask = [.borderless]
        window.setFrame(screen.frame, display: true)
        window.level = .normal
        window.makeKeyAndOrderFront(nil)
        NSApp.presentationOptions = [.autoHideDock, .autoHideMenuBar]
        NSApp.activate(ignoringOtherApps: true)
    }

    /// Restore a normal desktop window on the built-in display.
    private func exitKiosk() {
        kioskDisplayID = nil
        NSApp.presentationOptions = []
        window.styleMask = [.titled, .closable, .miniaturizable, .resizable]
        let home = NSScreen.screens.first { $0.isBuiltin } ?? NSScreen.main
        if let home {
            let size = NSSize(width: 1280, height: 800)
            let origin = NSPoint(
                x: home.visibleFrame.midX - size.width / 2,
                y: home.visibleFrame.midY - size.height / 2
            )
            window.setFrame(NSRect(origin: origin, size: size), display: true)
        }
        window.makeKeyAndOrderFront(nil)
    }

    // MARK: - Local keyboard input (same discrete command path as the remote)

    /// Borderless windows refuse key status by default, which silently eats
    /// all keyboard input in kiosk mode (no typing into login forms).
    final class KioskWindow: NSWindow {
        override var canBecomeKey: Bool { true }
        override var canBecomeMain: Bool { true }
    }

    private func installKeyMonitor() {
        keyMonitor = NSEvent.addLocalMonitorForEvents(matching: .keyDown) { [weak self] event in
            guard let self else { return event }
            // While a web view is on screen, let keys through to the page,
            // except Escape which always returns to the grid.
            if case .playing = self.appState.screen {
                if event.keyCode == 53 { // Escape
                    self.appState.goHome()
                    return nil
                }
                return event
            }
            switch event.keyCode {
            case 123: self.appState.moveFocus(.left); return nil
            case 124: self.appState.moveFocus(.right); return nil
            case 125: self.appState.moveFocus(.down); return nil
            case 126: self.appState.moveFocus(.up); return nil
            case 36, 76: self.appState.select(); return nil // Return / Enter
            case 53: return nil // Escape on grid: no-op
            default: return event
            }
        }
    }
}
