import SwiftUI

/// TV-style launcher grid, following the "SmartTV Remote Prototype" design:
/// light theme with soft corner glows, a top bar with a live clock and a
/// real search-filter field, and per-service hue-tinted tiles. Focus is
/// driven entirely by discrete commands (AppState.moveFocus/select) so the
/// remote and keyboard share one path; mouse clicks also work.
struct ServiceGridView: View {
    @EnvironmentObject var appState: AppState

    private var columns: [GridItem] {
        Array(repeating: GridItem(.flexible(), spacing: 22), count: appState.gridColumns)
    }

    private var visibleServices: [StreamingService] {
        appState.filteredServices
    }

    var body: some View {
        ZStack {
            Color.white.ignoresSafeArea()
            cornerGlows

            VStack(spacing: 0) {
                topBar

                ScrollViewReader { proxy in
                    ScrollView {
                        LazyVGrid(columns: columns, spacing: 22) {
                            ForEach(Array(visibleServices.enumerated()), id: \.element.id) { index, service in
                                ServiceTile(
                                    service: service,
                                    hue: appState.hue(for: service),
                                    isFocused: index == appState.focusedIndex
                                )
                                .id(service.id)
                                .onTapGesture {
                                    // `index` is the tile's position in visibleServices
                                    // (== filteredServices), which focusedIndex is
                                    // always interpreted against.
                                    appState.focusedIndex = index
                                    appState.select()
                                }
                            }
                        }
                        .padding(48)
                    }
                    .onChange(of: appState.focusedIndex) { _ in
                        if let id = appState.focusedService?.id {
                            withAnimation(.easeOut(duration: 0.2)) {
                                proxy.scrollTo(id, anchor: .center)
                            }
                        }
                    }
                }

                Text("◀ ▶ Navigate      ⏎ Select")
                    .font(.system(size: 12))
                    .foregroundStyle(Color.DesignPalette.gridSubtext)
                    .padding(.bottom, 20)
            }
        }
    }

    private var cornerGlows: some View {
        ZStack {
            Circle()
                .fill(Color.DesignPalette.warmGlow)
                .frame(width: 260, height: 260)
                .blur(radius: 40)
                .position(x: -20, y: -20)
            Circle()
                .fill(Color.DesignPalette.coolGlow)
                .frame(width: 320, height: 320)
                .blur(radius: 40)
                .position(x: UIScreenWidthProxy.width - 40, y: UIScreenWidthProxy.height - 40)
        }
        .allowsHitTesting(false)
    }

    private var topBar: some View {
        HStack {
            HStack(spacing: 10) {
                Image(systemName: "tv.fill")
                    .foregroundStyle(Color.DesignPalette.gridInk)
                Text("SmartTV")
                    .font(.system(size: 22, weight: .bold))
                    .foregroundStyle(Color.DesignPalette.gridInk)
            }

            Spacer()

            HStack(spacing: 14) {
                TextField("Search…", text: $appState.searchQuery)
                    .textFieldStyle(.plain)
                    .font(.system(size: 13))
                    .foregroundStyle(Color.DesignPalette.gridInk)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .frame(width: 200)
                    .background(Color.DesignPalette.searchFieldBG, in: RoundedRectangle(cornerRadius: 8))

                Text(Date.now, style: .time)
                    .font(.system(size: 14, weight: .semibold).monospacedDigit())
                    .foregroundStyle(Color.DesignPalette.gridInk)
            }
        }
        .padding(.horizontal, 48)
        .padding(.top, 32)
        .padding(.bottom, 8)
    }
}

/// Approximate corner-glow anchor since GeometryReader would require
/// restructuring the ZStack; a fixed reference sized for typical kiosk
/// displays is a reasonable trade-off for a decorative flourish.
private enum UIScreenWidthProxy {
    static let width: CGFloat = NSScreen.main?.frame.width ?? 1920
    static let height: CGFloat = NSScreen.main?.frame.height ?? 1080
}

private struct ServiceTile: View {
    let service: StreamingService
    let hue: Double
    let isFocused: Bool

    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: service.icon)
                .font(.system(size: 34))
                .foregroundStyle(.white)
            Text(service.name)
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(.white)
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity)
        .frame(height: 130)
        .background(
            RoundedRectangle(cornerRadius: 14)
                .fill(Color.serviceAccent(hue: hue, opacity: isFocused ? 1 : 0.55))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .strokeBorder(Color.DesignPalette.gridInk, lineWidth: isFocused ? 2 : 0)
        )
        .scaleEffect(isFocused ? 1.06 : 1.0)
        .shadow(color: .black.opacity(isFocused ? 0.2 : 0), radius: 10, y: 4)
        .animation(.spring(response: 0.25, dampingFraction: 0.8), value: isFocused)
    }
}
