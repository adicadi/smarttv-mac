# smarttv-mac

Turn a Mac + any HDMI display into a smart TV, controlled from your phone.

- **MacApp/** — `SmartTV.xcodeproj`: native macOS shell (Swift, SwiftUI +
  AppKit, WKWebView). Shows a grid of your streaming services, plays them in
  embedded web views with persistent logins, auto-fullscreens on the external
  display when HDMI is plugged in, and restores the desktop on unplug.
- **AndroidRemote/** — Android Studio project (Kotlin): discovers the Mac via
  Bonjour/mDNS, pairs once with an on-screen PIN, then acts as a d-pad remote.
- **docs/** — [architecture](docs/architecture.md) and the shared
  [command protocol](docs/protocol.md).

Everything is LAN-only. No cloud, no accounts beyond the streaming services.

## Build & run

### Mac (requires Xcode 16+, macOS 13+)

```
open MacApp/SmartTV.xcodeproj   # then ⌘R
```

First run seeds `~/Library/Application Support/SmartTV/services.json` — edit
that file to add/remove/reorder services (id, name, url, SF Symbol icon);
changes are picked up on next launch, no rebuild.

Enable "launch at login" via `LoginItemManager.register()` (macOS 13+
`SMAppService`); on older systems copy
`MacApp/SmartTV/LaunchHelper/LaunchAgent.plist` into `~/Library/LaunchAgents`.

Controls on the Mac itself: arrow keys move tile focus, Return selects,
Escape returns to the grid from a service.

### Android (requires Android Studio / SDK 36, minSdk 26)

```
cd AndroidRemote && ./gradlew assembleDebug
# APK at app/build/outputs/apk/debug/app-debug.apk
```

Phone and Mac must be on the same Wi-Fi/LAN. The app finds the TV
automatically; first connection shows a PIN on the TV screen — type it on the
phone once, after that it reconnects silently.

## Deviations from the original spec

- **No separate LaunchHelper binary.** The app itself registers as the login
  item (`SMAppService.mainApp`) and starts as a normal desktop app; if an
  external display is attached (at launch or later) it immediately enters
  kiosk mode on it. A second helper process would duplicate the display
  monitor for little gain in a non-App-Store app.
- **`hello`/`auth` messages added to the protocol** beyond the spec'd
  `pair`/`command` set — needed so the TV knows when to display a PIN and so
  a paired phone can reconnect silently with its token.
- **Android UI is programmatic classic Views, not Compose** — keeps the
  dependency footprint to exactly one third-party library (OkHttp for
  WebSocket, as allowed by the dependency policy).
- **`ContentView.swift` added** under `MacApp/SmartTV/App/` (root view that
  switches grid/web view and hosts PIN + error overlays).

## Known risks (carried forward, not solved)

- **DRM playback** per service depends on the macOS/WebKit version. Failures
  surface as a visible error banner, but whether Netflix/Disney+ actually
  play must be verified on the target machine while signed in.
- **Macs without a built-in display** (mini/Studio): "which screen is the
  TV" falls back to *largest screen, only when 2+ screens are attached*
  (`DisplayMonitor.currentExternalScreen()`); a user-configured choice would
  be better.
- **SMAppService** persistence across reboots should be verified on the
  target macOS version.
