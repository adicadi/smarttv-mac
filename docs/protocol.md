# SmartTV Remote Protocol

JSON text messages over a WebSocket connection on the LAN. The Mac advertises
the endpoint via Bonjour/mDNS as service type `_smarttv._tcp` (the port is
carried in the Bonjour record; no fixed port, no manual IP entry).

Implemented identically by `MacApp/SmartTV/RemoteServer/` (Network.framework)
and `AndroidRemote/.../connection/RemoteSocketClient.kt` (OkHttp WebSocket).

## Connection lifecycle

1. Android discovers `_smarttv._tcp` via NSD and opens a WebSocket.
2. If the phone has a stored device token it sends `auth`; on
   `auth_result: accepted` it may immediately send commands.
3. Otherwise (or after a rejected `auth`) it sends `hello`; the TV displays a
   4-digit PIN on screen. The phone submits it with `pair`.
4. On `pair_result: accepted` the phone stores `deviceToken` for silent
   reconnects and may start sending commands.
5. Any `command` from an unpaired connection is rejected with
   `{"type":"error","message":"not_paired"}`.

## Android → Mac

```json
{ "type": "hello" }
{ "type": "auth", "token": "<stored device token>" }
{ "type": "pair", "pin": "4821" }
{ "type": "command", "action": "navigate", "direction": "up|down|left|right" }
{ "type": "command", "action": "select" }
{ "type": "command", "action": "back" }
{ "type": "command", "action": "home" }
{ "type": "command", "action": "volume", "direction": "up|down" }
{ "type": "command", "action": "pointer_move", "dx": 12.5, "dy": -3.0 }
{ "type": "command", "action": "pointer_click" }
{ "type": "command", "action": "scroll", "dy": 40.0 }
{ "type": "command", "action": "text", "value": "stranger things" }
{ "type": "command", "action": "voice_text", "value": "stranger things" }
{ "type": "command", "action": "key", "value": "return|backspace|space|escape" }
{ "type": "command", "action": "seek", "direction": "back|forward" }
{ "type": "command", "action": "fullscreen" }
```

`pointer_move` deltas are in screen points. `text` types the string wherever
keyboard focus is on the Mac (search fields etc.); `voice_text` is the same
dictated-speech payload but tagged separately from `text` so the Mac can
special-case it — currently, while YouTube is on screen it's typed into
YouTube's own search box and submitted, otherwise it's handled exactly like
`text`. Pointer, scroll, `text`, `voice_text` (fallback path) and `key` are
injected as system events (CGEvent) and require the SmartTV app to be
granted **Accessibility** permission on the Mac — the first such command
triggers the system prompt and shows a hint in the TV UI.

## Mac → Android

```json
{ "type": "pair_result", "status": "accepted", "deviceToken": "…" }
{ "type": "pair_result", "status": "rejected" }
{ "type": "auth_result", "status": "accepted" }
{ "type": "auth_result", "status": "rejected" }
{ "type": "state", "screen": "grid", "focusedServiceId": "netflix" }
{ "type": "state", "screen": "playing", "serviceId": "netflix" }
{ "type": "error", "message": "not_paired" }
```

`state` is pushed to every paired client whenever the TV's screen or focus
changes, so the remote UI reflects reality rather than assuming its commands
succeeded.

## Semantics on the Mac

| Command            | On grid                    | While playing                                            |
| ------------------ | -------------------------- | -------------------------------------------------------- |
| navigate           | moves tile focus           | synthetic arrow-key press into the page                  |
| select             | opens focused service      | toggles play/pause on the active video[^select-fallback] |
| back               | no-op                      | web-view back, or grid if no history                     |
| home               | no-op (already home)       | returns to grid (web view kept alive)                    |
| volume             | system output volume ±7    | system output volume ±7                                  |
| pointer_move/click | moves/clicks system cursor | moves/clicks system cursor                               |
| scroll             | scrolls under cursor       | scrolls under cursor                                     |
| text / key         | types into focused element | types into focused element                               |
| voice_text         | types into focused element | YouTube: search box + submit; others: same as `text`     |
| seek               | no-op (no video on grid)   | skips the active video ±10s                              |
| fullscreen         | no-op (no video on grid)   | toggles fullscreen on the active video                   |

[^select-fallback]: Falls back to a synthetic Enter keypress when there's no `<video>` element to toggle — e.g. still browsing a page's own UI (My Cinema's grid) or a site with no player on screen yet.
