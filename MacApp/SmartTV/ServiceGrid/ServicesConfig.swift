import Foundation

/// Loads/saves the user-editable service list from
/// ~/Library/Application Support/SmartTV/services.json.
/// Seeds a sensible default set on first run; users edit the JSON (or a
/// future in-app editor) to add/remove/reorder services without a rebuild.
final class ServicesConfig {
    private let fileURL: URL

    init() {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        let dir = base.appendingPathComponent("SmartTV", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        fileURL = dir.appendingPathComponent("services.json")
    }

    func load() -> [StreamingService] {
        if let data = try? Data(contentsOf: fileURL),
           let services = try? JSONDecoder().decode([StreamingService].self, from: data),
           !services.isEmpty {
            return services
        }
        let defaults = Self.defaultServices
        save(defaults)
        return defaults
    }

    func save(_ services: [StreamingService]) {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        if let data = try? encoder.encode(services) {
            try? data.write(to: fileURL, options: .atomic)
        }
    }

    static let defaultServices: [StreamingService] = [
        StreamingService(id: "youtube", name: "YouTube", url: URL(string: "https://www.youtube.com")!, icon: "play.rectangle.fill"),
        StreamingService(id: "netflix", name: "Netflix", url: URL(string: "https://www.netflix.com/browse")!, icon: "n.square.fill"),
        StreamingService(id: "primevideo", name: "Prime Video", url: URL(string: "https://www.primevideo.com")!, icon: "p.square.fill"),
        StreamingService(id: "disneyplus", name: "Disney+", url: URL(string: "https://www.disneyplus.com")!, icon: "d.square.fill"),
        StreamingService(id: "hotstar", name: "JioHotstar", url: URL(string: "https://www.hotstar.com")!, icon: "h.square.fill"),
        StreamingService(id: "appletv", name: "Apple TV+", url: URL(string: "https://tv.apple.com")!, icon: "appletv.fill"),
        StreamingService(id: "spotify", name: "Spotify", url: URL(string: "https://open.spotify.com")!, icon: "music.note"),
        StreamingService(id: "twitch", name: "Twitch", url: URL(string: "https://www.twitch.tv")!, icon: "gamecontroller.fill"),
    ]
}
