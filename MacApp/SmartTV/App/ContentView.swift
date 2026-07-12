import SwiftUI

/// Root view: shows the service grid or the active streaming web view, with
/// pairing-PIN, loading/error, and volume-HUD overlays on top. Visual
/// language follows the "SmartTV Remote Prototype" design.
struct ContentView: View {
    @EnvironmentObject var appState: AppState

    var body: some View {
        ZStack {
            switch appState.screen {
            case .grid:
                ServiceGridView()
            case .playing(let service):
                playingScreen(service)
            }

            if appState.volumeVisible {
                volumeHUD
                    .transition(.opacity)
            }

            if let pin = appState.pairingPIN {
                pairingOverlay(pin: pin)
                    .transition(.opacity)
            }
        }
        .animation(.easeOut(duration: 0.2), value: appState.volumeVisible)
        .animation(.easeOut(duration: 0.2), value: appState.pairingPIN)
    }

    // MARK: - Playing screen

    @ViewBuilder
    private func playingScreen(_ service: StreamingService) -> some View {
        ZStack {
            StreamingWebView(webView: appState.webViewController.webView(for: service))
                .ignoresSafeArea()

            if appState.isLoadingService {
                stateOverlay(service) {
                    VStack(spacing: 18) {
                        ProgressView()
                            .controlSize(.large)
                            .tint(.white)
                        Text("Loading \(service.name)…")
                            .font(.system(size: 19, weight: .semibold))
                            .foregroundStyle(.white)
                    }
                }
            } else if let error = appState.errorMessage {
                stateOverlay(service) {
                    VStack(spacing: 14) {
                        Text("⚠️").font(.system(size: 44))
                        Text("Playback Failed")
                            .font(.system(size: 20, weight: .bold))
                            .foregroundStyle(.white)
                        Text(error)
                            .font(.system(size: 15))
                            .foregroundStyle(.white.opacity(0.85))
                            .multilineTextAlignment(.center)
                            .lineLimit(4)
                            .frame(maxWidth: 520)
                    }
                }
            }
        }
    }

    /// Full-bleed overlay tinted by the service's accent hue, with the
    /// standard "◀ Back  ⌂ Home" hint pinned to the bottom.
    private func stateOverlay<Content: View>(
        _ service: StreamingService, @ViewBuilder content: () -> Content
    ) -> some View {
        let hue = appState.hue(for: service)
        return ZStack {
            LinearGradient(
                colors: [
                    Color(hue: hue / 360, saturation: 0.55, brightness: 0.4),
                    Color(hue: hue / 360, saturation: 0.35, brightness: 0.14),
                ],
                startPoint: .top, endPoint: .bottom
            )
            .ignoresSafeArea()

            content()

            VStack {
                Spacer()
                Text("◀ Back      ⌂ Home")
                    .font(.system(size: 13))
                    .foregroundStyle(.white.opacity(0.7))
                    .padding(.bottom, 22)
            }
        }
    }

    // MARK: - Pairing overlay

    private func pairingOverlay(pin: String) -> some View {
        ZStack {
            Color.DesignPalette.darkBackdrop.ignoresSafeArea()
            VStack(spacing: 22) {
                Text("Pair Your Remote")
                    .font(.system(size: 22, weight: .semibold))
                    .foregroundStyle(.white)
                HStack(spacing: 16) {
                    ForEach(Array(pin), id: \.self) { char in
                        Text(String(char))
                            .font(.system(size: 34, weight: .bold, design: .monospaced))
                            .foregroundStyle(.white)
                            .frame(width: 56, height: 72)
                            .background(Color.DesignPalette.pinBoxEmpty, in: RoundedRectangle(cornerRadius: 12))
                    }
                }
                Text("Enter this PIN on AndroidRemote to connect")
                    .font(.system(size: 14))
                    .foregroundStyle(Color(hex: 0x8C99AA))
            }
        }
    }

    // MARK: - Volume HUD

    private var volumeHUD: some View {
        VStack {
            HStack(spacing: 12) {
                Text("🔊").font(.system(size: 16))
                ZStack(alignment: .leading) {
                    Capsule().fill(Color.white.opacity(0.2)).frame(width: 160, height: 6)
                    Capsule().fill(Color.white)
                        .frame(width: 160 * CGFloat(appState.volumeLevel) / 100, height: 6)
                }
                Text("\(appState.volumeLevel)")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(.white)
                    .monospacedDigit()
                    .frame(width: 28)
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 12)
            .background(Color.DesignPalette.volumeHUDBG, in: RoundedRectangle(cornerRadius: 14))
            .shadow(color: .black.opacity(0.4), radius: 24, y: 8)
            .padding(.top, 26)
            Spacer()
        }
    }
}
