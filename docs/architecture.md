# Architecture

Two apps, one LAN, no cloud.

```text
┌────────────────────────── Mac ──────────────────────────┐      ┌───── Android ─────┐
│  SmartTV.app (Swift, SwiftUI + AppKit)                  │      │  AndroidRemote     │
│                                                         │      │  (Kotlin)          │
│  DisplayMonitor ──▶ AppDelegate ──▶ kiosk window        │      │                    │
│   (NSScreen diff)    (move/fullscreen across displays)  │      │  BonjourDiscovery  │
│                                                         │◀─────│   (NsdManager)     │
│  ServiceGridView ◀─▶ AppState ◀─▶ WebViewController     │ mDNS │                    │
│   (tile focus)       (screen/    (per-service           │      │  RemoteSocketClient│
│                       focus)      WKWebViews)           │◀────▶│   (OkHttp WS)      │
│                         ▲                               │  WS  │                    │
│  RemoteServer ──▶ CommandRouter                         │ JSON │  RemotePadScreen   │
│   (NWListener + WebSocket, Bonjour `_smarttv._tcp`,     │      │   (d-pad UI)       │
│    PIN pairing via PairingManager)                      │      │                    │
└─────────────────────────────────────────────────────────┘      └────────────────────┘
```

## macOS app

- **App/** — `SmartTVApp` is the `@main` entry; `AppDelegate` owns the single
  NSWindow so it can be moved/borderless-fullscreened programmatically
  (SwiftUI window scenes don't give enough control). `AppState` is the shared
  observable model: current screen (grid vs. playing), focused tile, pairing
  PIN, visible error. Keyboard arrows/Enter feed the *same* discrete command
  path the remote uses.
- **DisplayDetection/** — subscribes to
  `NSApplication.didChangeScreenParametersNotification` and diffs
  `NSScreen.screens` snapshots keyed by `CGDirectDisplayID`. Built-in display
  identified with `CGDisplayIsBuiltin`. Connect of a non-built-in display →
  kiosk mode on exactly that screen; disconnect of the kiosk display →
  restore a normal window on the built-in screen. On Macs with no built-in
  display, a screen only counts as the TV when 2+ screens are attached
  (largest wins).
- **ServiceGrid/** — grid backed by a user-editable JSON file at
  `~/Library/Application Support/SmartTV/services.json` (seeded with defaults
  on first run). Fully drivable with up/down/left/right/select.
- **WebViewContainer/** — one persistent `WKWebView` per service (default
  `WKWebsiteDataStore`, so logins survive relaunches; autoplay allowed).
  WKWebView is deliberate: WebKit's native FairPlay/EME handles DRM streaming
  far more reliably than a Chromium embed. Navigation failures and fatal
  `<video>` errors (the DRM-failure signature) surface as a visible error
  banner, never a silent black screen. Media is explicitly paused (all
  `<video>`/`<audio>` elements) whenever it shouldn't be audible/visible even
  though the WKWebView keeps running: returning to the grid, the window
  becoming occluded (another app's fullscreen Space covering it, minimized,
  etc. — detected via `NSWindow.didChangeOcclusionStateNotification`), and on
  HDMI-disconnect kiosk exit.
- **RemoteServer/** — `NWListener` with `NWProtocolWebSocket` (zero
  third-party deps), advertised over Bonjour. First contact from an unknown
  phone puts a 4-digit PIN on the TV; a redeemed PIN mints a persistent
  device token (`paired-devices.json`). Unpaired connections get no command
  access. `CommandRouter` maps protocol messages to `AppState` actions;
  volume goes to *system* output volume via `NSAppleScript`.
- **LaunchHelper/** — `SMAppService.mainApp` registration (macOS 13+) so the
  app is always running at login and can catch HDMI plug events; a
  `LaunchAgent.plist` is bundled as the manual fallback for older systems.
  Deviation from the original spec: there is no separate helper binary — the
  app itself is the login item (see README "Deviations").

## Android app

- `BonjourDiscovery` (NsdManager) finds `_smarttv._tcp`, distinguishing
  "nothing on the LAN" from "found, needs pairing".
- `RemoteSocketClient` (OkHttp WebSocket — the one allowed third-party dep)
  handles token auth, PIN pairing, reconnect, and state messages.
- `RemotePadScreen` renders the full v1 control surface: d-pad + select,
  back, home, volume up/down. No keyboard, no pointer.

## Trust & security model

LAN-only. Bonjour advertisement never leaves the local network; there is no
internet-facing listener. Commands require a paired connection; pairing
requires physical sight of the TV screen (the PIN). Device tokens are random
UUIDs stored on both ends; unpairing = deleting
`~/Library/Application Support/SmartTV/paired-devices.json`.
