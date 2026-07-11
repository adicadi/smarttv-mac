import Foundation

/// Generates pairing PINs and persists device tokens so a phone only has to
/// pair once. Tokens live in Application Support alongside services.json.
final class PairingManager {
    private let fileURL: URL
    private var tokens: Set<String>
    private(set) var activePIN: String?

    init() {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        let dir = base.appendingPathComponent("SmartTV", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        fileURL = dir.appendingPathComponent("paired-devices.json")
        if let data = try? Data(contentsOf: fileURL),
           let stored = try? JSONDecoder().decode(Set<String>.self, from: data) {
            tokens = stored
        } else {
            tokens = []
        }
    }

    /// Starts a pairing attempt: generates a 4-digit PIN the UI must display.
    func beginPairing() -> String {
        let pin = String(format: "%04d", Int.random(in: 0...9999))
        activePIN = pin
        return pin
    }

    func endPairing() {
        activePIN = nil
    }

    /// Validates a submitted PIN; on success mints and persists a new token.
    func redeemPIN(_ pin: String) -> String? {
        guard let activePIN, pin == activePIN else { return nil }
        self.activePIN = nil
        let token = UUID().uuidString
        tokens.insert(token)
        persist()
        return token
    }

    func isValidToken(_ token: String) -> Bool {
        tokens.contains(token)
    }

    private func persist() {
        if let data = try? JSONEncoder().encode(tokens) {
            try? data.write(to: fileURL, options: .atomic)
        }
    }
}
