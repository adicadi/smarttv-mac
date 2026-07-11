import Foundation
import Network

/// Embedded WebSocket server (Network.framework, zero dependencies).
/// Advertises itself over Bonjour as `_smarttv._tcp`, enforces PIN pairing,
/// then forwards commands to CommandRouter and broadcasts UI state back.
@MainActor
final class RemoteServer {
    static let serviceType = "_smarttv._tcp"

    private let router: CommandRouter
    private let appState: AppState
    private let pairing = PairingManager()

    private var listener: NWListener?
    private var connections: [ObjectIdentifier: ClientConnection] = [:]

    init(router: CommandRouter, appState: AppState) {
        self.router = router
        self.appState = appState
    }

    // MARK: - Lifecycle

    func start() {
        let params = NWParameters.tcp
        let ws = NWProtocolWebSocket.Options()
        ws.autoReplyPing = true
        params.defaultProtocolStack.applicationProtocols.insert(ws, at: 0)

        do {
            let listener = try NWListener(using: params)
            listener.service = NWListener.Service(
                name: Host.current().localizedName ?? "SmartTV",
                type: Self.serviceType
            )
            listener.newConnectionHandler = { [weak self] connection in
                Task { @MainActor in
                    self?.accept(connection)
                }
            }
            listener.stateUpdateHandler = { state in
                NSLog("RemoteServer listener state: \(String(describing: state))")
            }
            listener.start(queue: .main)
            self.listener = listener
        } catch {
            NSLog("RemoteServer failed to start: \(error)")
        }
    }

    func stop() {
        for (_, client) in connections { client.connection.cancel() }
        connections.removeAll()
        listener?.cancel()
        listener = nil
    }

    // MARK: - Connections

    private func accept(_ connection: NWConnection) {
        let client = ClientConnection(connection: connection)
        connections[ObjectIdentifier(client)] = client

        connection.stateUpdateHandler = { [weak self, weak client] state in
            guard let client else { return }
            if case .failed = state { Task { @MainActor in self?.drop(client) } }
            if case .cancelled = state { Task { @MainActor in self?.drop(client) } }
        }
        connection.start(queue: .main)
        receiveLoop(client)
    }

    private func drop(_ client: ClientConnection) {
        connections.removeValue(forKey: ObjectIdentifier(client))
        // If the client vanished mid-pairing, take the PIN off the TV screen.
        if appState.pairingPIN != nil && !connections.values.contains(where: { !$0.isPaired }) {
            pairing.endPairing()
            appState.pairingPIN = nil
        }
    }

    private func receiveLoop(_ client: ClientConnection) {
        client.connection.receiveMessage { [weak self, weak client] data, _, _, error in
            guard let self, let client else { return }
            Task { @MainActor in
                if let data, !data.isEmpty {
                    self.handleMessage(data, from: client)
                }
                if error == nil {
                    self.receiveLoop(client)
                } else {
                    client.connection.cancel()
                }
            }
        }
    }

    // MARK: - Protocol handling

    private func handleMessage(_ data: Data, from client: ClientConnection) {
        guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let type = json["type"] as? String else { return }

        switch type {
        case "pair":
            handlePair(json, from: client)
        case "auth":
            // Silent reconnect with a previously issued device token.
            if let token = json["token"] as? String, pairing.isValidToken(token) {
                client.isPaired = true
                send(["type": "auth_result", "status": "accepted"], to: client)
                sendState(to: client)
            } else {
                send(["type": "auth_result", "status": "rejected"], to: client)
                // Client should fall back to PIN pairing.
                startPairingDisplay()
            }
        case "command":
            // Reject anything from an unpaired connection.
            guard client.isPaired else {
                send(["type": "error", "message": "not_paired"], to: client)
                return
            }
            router.handle(json)
        case "hello":
            // A new client with no token announces itself: show a PIN.
            if !client.isPaired {
                startPairingDisplay()
            }
        default:
            break
        }
    }

    private func handlePair(_ json: [String: Any], from client: ClientConnection) {
        guard let pin = json["pin"] as? String, let token = pairing.redeemPIN(pin) else {
            send(["type": "pair_result", "status": "rejected"], to: client)
            return
        }
        client.isPaired = true
        appState.pairingPIN = nil
        send(["type": "pair_result", "status": "accepted", "deviceToken": token], to: client)
        sendState(to: client)
    }

    private func startPairingDisplay() {
        let pin = pairing.beginPairing()
        appState.pairingPIN = pin
    }

    // MARK: - Outbound

    /// Pushes the current screen/focus state to all paired clients.
    func broadcastState() {
        let payload = statePayload()
        for client in connections.values where client.isPaired {
            send(payload, to: client)
        }
    }

    private func sendState(to client: ClientConnection) {
        send(statePayload(), to: client)
    }

    private func statePayload() -> [String: Any] {
        switch appState.screen {
        case .grid:
            return [
                "type": "state",
                "screen": "grid",
                "focusedServiceId": appState.focusedService?.id ?? ""
            ]
        case .playing(let service):
            return ["type": "state", "screen": "playing", "serviceId": service.id]
        }
    }

    private func send(_ payload: [String: Any], to client: ClientConnection) {
        guard let data = try? JSONSerialization.data(withJSONObject: payload) else { return }
        let metadata = NWProtocolWebSocket.Metadata(opcode: .text)
        let context = NWConnection.ContentContext(identifier: "text", metadata: [metadata])
        client.connection.send(
            content: data,
            contentContext: context,
            isComplete: true,
            completion: .contentProcessed { _ in }
        )
    }
}

/// Per-connection pairing state. Only touched on the main queue (the
/// listener and all connections are started with queue: .main).
final class ClientConnection: @unchecked Sendable {
    let connection: NWConnection
    var isPaired = false

    init(connection: NWConnection) {
        self.connection = connection
    }
}
