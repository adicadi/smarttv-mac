import SwiftUI

@main
struct SmartTVApp: App {
    @NSApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    var body: some Scene {
        // The main window is created and managed by AppDelegate so it can be
        // moved/fullscreened across displays freely. Settings is a placeholder scene.
        Settings {
            EmptyView()
        }
    }
}

/// Top-level UI state shared between the grid, the web view container and the
/// remote command router. All mutations happen on the main actor.
@MainActor
final class AppState: ObservableObject {
    enum Screen: Equatable {
        case grid
        case playing(StreamingService)
    }

    @Published var screen: Screen = .grid
    @Published var services: [StreamingService] = []
    @Published var focusedIndex: Int = 0
    /// PIN currently displayed for a pairing attempt, if any.
    @Published var pairingPIN: String?
    /// Visible, non-fatal error (e.g. DRM playback failure).
    @Published var errorMessage: String?

    /// Number of columns the grid renders; navigation math must match the layout.
    let gridColumns = 4

    let servicesConfig = ServicesConfig()
    let webViewController = WebViewController()

    /// Called whenever screen/focus changes so RemoteServer can broadcast state.
    var onStateChange: (() -> Void)?

    init() {
        services = servicesConfig.load()
        webViewController.onPlaybackError = { [weak self] message in
            Task { @MainActor in
                self?.errorMessage = message
            }
        }
    }

    var focusedService: StreamingService? {
        guard services.indices.contains(focusedIndex) else { return nil }
        return services[focusedIndex]
    }

    // MARK: - Discrete navigation (shared by keyboard and remote)

    func moveFocus(_ direction: MoveDirection) {
        // While a service is on screen, forward d-pad moves into the page as
        // arrow keys (drives cinema.html and any arrow-aware streaming UI).
        if case .playing = screen {
            webViewController.sendArrowKey(direction)
            return
        }
        guard !services.isEmpty else { return }
        var index = focusedIndex
        switch direction {
        case .left: index -= 1
        case .right: index += 1
        case .up: index -= gridColumns
        case .down: index += gridColumns
        }
        if services.indices.contains(index) {
            focusedIndex = index
            notifyStateChange()
        }
    }

    func select() {
        switch screen {
        case .grid:
            guard let service = focusedService else { return }
            errorMessage = nil
            webViewController.load(service)
            screen = .playing(service)
            notifyStateChange()
        case .playing:
            // Forward "select" into the page as a synthetic Enter key press so
            // sites with their own focus model (e.g. YouTube TV UI) respond.
            webViewController.sendEnterKey()
        }
    }

    func back() {
        switch screen {
        case .grid:
            break
        case .playing:
            if webViewController.canGoBack {
                webViewController.goBack()
            } else {
                goHome()
            }
        }
        notifyStateChange()
    }

    func goHome() {
        screen = .grid
        errorMessage = nil
        notifyStateChange()
    }

    func reloadServices() {
        services = servicesConfig.load()
        focusedIndex = min(focusedIndex, max(0, services.count - 1))
        notifyStateChange()
    }

    private func notifyStateChange() {
        onStateChange?()
    }
}

enum MoveDirection: String {
    case up, down, left, right
}
