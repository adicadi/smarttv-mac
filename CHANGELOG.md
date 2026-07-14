# Changelog

## [1.0.0] - 2026-07-14

First tagged release of both the macOS SmartTV app and its Android remote.

### macOS SmartTV app (`com.smarttv.app`)

- Grid launcher for your streaming services (YouTube, Netflix, Prime Video,
  Apple TV+, Apple Music, Twitch, and a bundled "My Cinema" TMDB/Vidking
  experience), each running in its own persistent `WKWebView` so logins
  survive relaunches.
- Auto-enters kiosk/fullscreen mode on an external HDMI display when one is
  connected, and restores the desktop on unplug; handles clamshell mode
  correctly.
- Embedded Bonjour-advertised WebSocket server with PIN pairing and
  token-based silent reconnect for the Android remote.
- D-pad navigation, play/pause toggle, seek ±10s, fullscreen toggle, system
  volume control, trackpad (move/click/scroll), on-screen keyboard text
  entry, and voice dictation — all forwarded from the remote.
- YouTube points at `youtube.com/tv`, Google's own D-pad-navigable "Living
  Room" build, so remote arrow keys drive real spatial navigation instead of
  only affecting the video player.
- Voice dictation is typed straight into YouTube's own search box and
  submitted automatically when YouTube is on screen; other services fall
  back to typing wherever OS keyboard focus already is.
- Background playback is paused (not just hidden) whenever you leave a
  service, so audio doesn't keep playing invisibly.
- Dark, ambient-lit grid UI: gradient background with soft corner glows,
  glassy search/clock pills, gradient service tiles with a hue-tinted focus
  glow.
- Launches at login (`SMAppService`), editable service list via
  `~/Library/Application Support/SmartTV/services.json`.

### Android remote (`com.adicadi.smarttv`, formerly `com.smarttv.remote`)

- Discovers the Mac automatically via mDNS/Bonjour (NSD) — no manual IP
  entry.
- One-time PIN pairing, then silent token-based reconnect.
- Firestick-style d-pad ring (up/down/left/right + center select), Back/
  Home/Fullscreen buttons, skip ±10s row, volume rocker, mic button (speech-
  to-text), trackpad and on-screen keyboard overlays.
- Built on OkHttp's WebSocket client as the only third-party dependency;
  UI is programmatic classic Android Views (no Compose).

### Shared protocol

- LAN-only JSON-over-WebSocket protocol (`docs/protocol.md`), covering
  navigation, selection, back/home, volume, seek, fullscreen, pointer/
  scroll, keyboard text and voice text, plus the pairing/auth handshake.

### Known risks (carried into this release, not yet solved)

- DRM playback support depends on the macOS/WebKit version and must be
  verified per streaming service while signed in.
- "Which screen is the TV" falls back to *largest screen, only when 2+
  screens are attached* — no user-configurable choice yet.
- Release builds are self-signed (not notarized), so macOS Gatekeeper will
  warn on first launch; the Android APK is signed with a dedicated release
  key (not on the Play Store).
