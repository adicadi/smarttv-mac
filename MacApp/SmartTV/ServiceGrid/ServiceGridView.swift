import SwiftUI

/// TV-style launcher grid: a dark, ambient-lit theme with soft corner glows,
/// a glassy top bar with a live clock and a real search-filter field, and
/// per-service hue-tinted gradient tiles. Focus is driven entirely by
/// discrete commands (AppState.moveFocus/select) so the remote and keyboard
/// share one path; mouse clicks also work.
struct ServiceGridView: View {
    @EnvironmentObject var appState: AppState

    private var columns: [GridItem] {
        Array(repeating: GridItem(.flexible(), spacing: 24), count: appState.gridColumns)
    }

    private var visibleServices: [StreamingService] {
        appState.filteredServices
    }

    var body: some View {
        ZStack {
            LinearGradient(
                colors: [Color.DesignPalette.gridBackgroundTop, Color.DesignPalette.gridBackgroundBottom],
                startPoint: .top, endPoint: .bottom
            )
            .ignoresSafeArea()
            cornerGlows

            VStack(spacing: 0) {
                topBar

                ScrollViewReader { proxy in
                    ScrollView {
                        LazyVGrid(columns: columns, spacing: 24) {
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

                navHint
                    .padding(.bottom, 22)
            }
        }
    }

    private var cornerGlows: some View {
        ZStack {
            Circle()
                .fill(Color.DesignPalette.warmGlow)
                .frame(width: 420, height: 420)
                .blur(radius: 90)
                .position(x: -60, y: -40)
            Circle()
                .fill(Color.DesignPalette.coolGlow)
                .frame(width: 480, height: 480)
                .blur(radius: 100)
                .position(x: UIScreenWidthProxy.width - 60, y: UIScreenWidthProxy.height - 60)
        }
        .allowsHitTesting(false)
    }

    private var topBar: some View {
        HStack {
            HStack(spacing: 12) {
                ZStack {
                    RoundedRectangle(cornerRadius: 10)
                        .fill(LinearGradient(
                            colors: [Color.DesignPalette.accent, Color.DesignPalette.accent.opacity(0.6)],
                            startPoint: .topLeading, endPoint: .bottomTrailing
                        ))
                        .frame(width: 34, height: 34)
                    Image(systemName: "tv.fill")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(.white)
                }
                Text("SmartTV")
                    .font(.system(size: 22, weight: .bold))
                    .foregroundStyle(Color.DesignPalette.gridInk)
            }

            Spacer()

            HStack(spacing: 14) {
                HStack(spacing: 8) {
                    Image(systemName: "magnifyingglass")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(Color.DesignPalette.gridSubtext)
                    TextField("Search…", text: $appState.searchQuery)
                        .textFieldStyle(.plain)
                        .font(.system(size: 13))
                        .foregroundStyle(Color.DesignPalette.gridInk)
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 9)
                .frame(width: 200)
                .background(Color.DesignPalette.searchFieldBG, in: Capsule())
                .overlay(Capsule().strokeBorder(Color.DesignPalette.gridGlassBorder, lineWidth: 1))

                Text(Date.now, style: .time)
                    .font(.system(size: 14, weight: .semibold).monospacedDigit())
                    .foregroundStyle(Color.DesignPalette.gridInk)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 9)
                    .background(Color.DesignPalette.searchFieldBG, in: Capsule())
                    .overlay(Capsule().strokeBorder(Color.DesignPalette.gridGlassBorder, lineWidth: 1))
            }
        }
        .padding(.horizontal, 48)
        .padding(.top, 32)
        .padding(.bottom, 12)
    }

    private var navHint: some View {
        HStack(spacing: 18) {
            Label("Navigate", systemImage: "arrow.up.and.down.and.arrow.left.and.right")
            Label("Select", systemImage: "return")
        }
        .font(.system(size: 12, weight: .medium))
        .foregroundStyle(Color.DesignPalette.gridSubtext)
        .padding(.horizontal, 18)
        .padding(.vertical, 9)
        .background(.ultraThinMaterial, in: Capsule())
        .overlay(Capsule().strokeBorder(Color.DesignPalette.gridGlassBorder, lineWidth: 1))
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

    private var gradientTop: Color { .serviceAccentTop(hue: hue, opacity: isFocused ? 1 : 0.7) }
    private var gradientBottom: Color { .serviceAccentBottom(hue: hue, opacity: isFocused ? 1 : 0.7) }

    var body: some View {
        VStack(spacing: 14) {
            ZStack {
                Circle()
                    .fill(.white.opacity(0.16))
                    .frame(width: 56, height: 56)
                Image(systemName: service.icon)
                    .font(.system(size: 24, weight: .semibold))
                    .foregroundStyle(.white)
            }
            Text(service.name)
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(.white)
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity)
        .frame(height: 148)
        .background(
            RoundedRectangle(cornerRadius: 18)
                .fill(LinearGradient(colors: [gradientTop, gradientBottom], startPoint: .top, endPoint: .bottom))
        )
        .overlay(
            // Faint top sheen for a glassy, lit-from-above feel.
            RoundedRectangle(cornerRadius: 18)
                .fill(LinearGradient(colors: [.white.opacity(0.16), .clear], startPoint: .top, endPoint: .center))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 18)
                .strokeBorder(.white.opacity(isFocused ? 0.9 : 0.12), lineWidth: isFocused ? 2 : 1)
        )
        .shadow(color: gradientBottom.opacity(isFocused ? 0.55 : 0), radius: isFocused ? 22 : 0, y: 10)
        .scaleEffect(isFocused ? 1.06 : 1.0)
        .animation(.spring(response: 0.25, dampingFraction: 0.8), value: isFocused)
    }
}
