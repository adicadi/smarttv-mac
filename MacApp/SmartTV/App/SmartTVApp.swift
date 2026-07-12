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
    /// True while the active service's page is still loading.
    @Published var isLoadingService = false
    /// Text driving the grid's real search-filter field. Resets focus so
    /// stale positions from the unfiltered list can't be selected.
    @Published var searchQuery = "" {
        didSet { if searchQuery != oldValue { focusedIndex = 0 } }
    }
    @Published var volumeVisible = false
    @Published var volumeLevel = 50

    private var volumeHideWorkItem: DispatchWorkItem?

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
                self?.isLoadingService = false
            }
        }
        webViewController.onLoadingChange = { [weak self] loading in
            Task { @MainActor in
                self?.isLoadingService = loading
            }
        }
    }

    /// Services after the grid's search filter; empty query returns all.
    /// Focus navigation and selection are always relative to this list, not
    /// the unfiltered one, so a filtered-out tile can never be selected.
    var filteredServices: [StreamingService] {
        guard !searchQuery.isEmpty else { return services }
        return services.filter { $0.name.localizedCaseInsensitiveContains(searchQuery) }
    }

    var focusedService: StreamingService? {
        let list = filteredServices
        guard list.indices.contains(focusedIndex) else { return nil }
        return list[focusedIndex]
    }

    /// Stable, visually distinct accent hue (0-360) derived from a service's
    /// id, used to color its tile and its "now loading" background.
    func hue(for service: StreamingService) -> Double {
        let hash = service.id.unicodeScalars.reduce(UInt32(5381)) { ($0 << 5) &+ $0 &+ $1.value }
        return Double(hash % 360)
    }

    // MARK: - Volume HUD

    func showVolumeHUD(level: Int) {
        volumeLevel = level
        volumeVisible = true
        volumeHideWorkItem?.cancel()
        let workItem = DispatchWorkItem { [weak self] in self?.volumeVisible = false }
        volumeHideWorkItem = workItem
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5, execute: workItem)
    }

    // MARK: - Discrete navigation (shared by keyboard and remote)

    func moveFocus(_ direction: MoveDirection) {
        // While a service is on screen, forward d-pad moves into the page as
        // arrow keys (drives cinema.html and any arrow-aware streaming UI).
        if case .playing = screen {
            webViewController.sendArrowKey(direction)
            return
        }
        let list = filteredServices
        guard !list.isEmpty else { return }
        var index = focusedIndex
        switch direction {
        case .left: index -= 1
        case .right: index += 1
        case .up: index -= gridColumns
        case .down: index += gridColumns
        }
        if list.indices.contains(index) {
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
            // OK acts like a TV remote's center button: toggles play/pause
            // on the active video, falling back to a synthetic Enter
            // keypress for non-video UI (e.g. selecting a card in My Cinema's
            // browse grid, or a site's own focus model like YouTube TV).
            webViewController.togglePlayPauseOrSelect()
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

    func seek(direction: String) {
        guard case .playing = screen else { return }
        webViewController.seek(direction: direction)
    }

    func goHome() {
        // The web view keeps running behind the grid (so a service can
        // resume where it left off), but its media must not keep
        // playing/sounding while it's off screen.
        webViewController.pauseCurrent()
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
