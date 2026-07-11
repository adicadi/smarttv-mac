import SwiftUI

/// Root view: shows the service grid or the active streaming web view,
/// with pairing-PIN and error overlays on top.
struct ContentView: View {
    @EnvironmentObject var appState: AppState

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            switch appState.screen {
            case .grid:
                ServiceGridView()
            case .playing(let service):
                StreamingWebView(webView: appState.webViewController.webView(for: service))
                    .ignoresSafeArea()
            }

            if let pin = appState.pairingPIN {
                pairingOverlay(pin: pin)
            }

            if let error = appState.errorMessage {
                errorBanner(error)
            }
        }
    }

    private func pairingOverlay(pin: String) -> some View {
        VStack(spacing: 12) {
            Text("Pair Remote")
                .font(.title2.weight(.semibold))
            Text(pin)
                .font(.system(size: 64, weight: .bold, design: .monospaced))
                .kerning(12)
            Text("Enter this PIN on your phone")
                .foregroundStyle(.secondary)
        }
        .padding(40)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 24))
        .transition(.opacity)
    }

    private func errorBanner(_ message: String) -> some View {
        VStack {
            Spacer()
            HStack(spacing: 10) {
                Image(systemName: "exclamationmark.triangle.fill")
                    .foregroundStyle(.yellow)
                Text(message)
                Button("Back to Home") { appState.goHome() }
            }
            .padding(16)
            .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 14))
            .padding(.bottom, 40)
        }
    }
}
