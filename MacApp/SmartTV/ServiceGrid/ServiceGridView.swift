import SwiftUI

/// TV-style launcher grid. Focus is driven entirely by discrete commands
/// (AppState.moveFocus/select) so the remote and keyboard share one path;
/// mouse clicks also work as a convenience.
struct ServiceGridView: View {
    @EnvironmentObject var appState: AppState

    private var columns: [GridItem] {
        Array(repeating: GridItem(.flexible(), spacing: 28), count: appState.gridColumns)
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Image(systemName: "tv.fill")
                Text("SmartTV")
                    .font(.largeTitle.weight(.bold))
                Spacer()
                Text(Date.now, style: .time)
                    .font(.title3.monospacedDigit())
                    .foregroundStyle(.secondary)
            }
            .padding(.horizontal, 60)
            .padding(.top, 40)

            ScrollViewReader { proxy in
                ScrollView {
                    LazyVGrid(columns: columns, spacing: 28) {
                        ForEach(Array(appState.services.enumerated()), id: \.element.id) { index, service in
                            ServiceTile(service: service, isFocused: index == appState.focusedIndex)
                                .id(service.id)
                                .onTapGesture {
                                    appState.focusedIndex = index
                                    appState.select()
                                }
                        }
                    }
                    .padding(60)
                }
                .onChange(of: appState.focusedIndex) { _ in
                    if let id = appState.focusedService?.id {
                        withAnimation(.easeOut(duration: 0.2)) {
                            proxy.scrollTo(id, anchor: .center)
                        }
                    }
                }
            }
        }
        .foregroundStyle(.white)
    }
}

private struct ServiceTile: View {
    let service: StreamingService
    let isFocused: Bool

    var body: some View {
        VStack(spacing: 14) {
            Image(systemName: service.icon)
                .font(.system(size: 44))
                .frame(height: 52)
            Text(service.name)
                .font(.title3.weight(.medium))
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity)
        .frame(height: 150)
        .background(
            RoundedRectangle(cornerRadius: 18)
                .fill(Color.white.opacity(isFocused ? 0.22 : 0.08))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 18)
                .strokeBorder(Color.white.opacity(isFocused ? 0.9 : 0), lineWidth: 3)
        )
        .scaleEffect(isFocused ? 1.06 : 1.0)
        .animation(.spring(response: 0.25, dampingFraction: 0.8), value: isFocused)
    }
}
