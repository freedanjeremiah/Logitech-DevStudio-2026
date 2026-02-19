# MX SpatialBridge — WebSocket Sync Protocol

Port `58432` (TCP, WebSocket, text frames, UTF-8 JSON)

---

## Discovery

The glasses advertise via mDNS:
- **Service type:** `_mxspatialbridge._tcp`
- **Port:** `58432`

The desktop plugin (C# / Makaretu.Dns) scans for this service type automatically.
No IP configuration required.

---

## Plugin → Glasses messages

### ping
```json
{ "type": "ping" }
```
Sent every 5 seconds. Glasses must reply with `pong`.

### action
```json
{
  "type":   "action",
  "action": "select",
  "params": {}
}
```
Drive the AR interface directly from the desktop plugin or Actions Ring bubble.

**Valid actions:**

| action           | params                              | description                        |
|------------------|-------------------------------------|------------------------------------|
| `select`         | —                                   | Confirm focused element            |
| `back`           | —                                   | Navigate back                      |
| `scroll`         | `delta` (int), `axis` (v/h)        | Scroll by N units                  |
| `scroll-reset`   | `axis`                              | Jump to start of list              |
| `zoom`           | `percent` (10–400)                  | Set absolute zoom                  |
| `zoom-reset`     | —                                   | Return to 100%                     |
| `set-brightness` | `percent` (0–100)                   | Set display brightness             |
| `switch-layer`   | `target` (next/prev/0..3)           | Switch AR workspace layer          |
| `launch-app`     | `appId` (browser/maps/camera/…)    | Open app on glasses                |
| `workspace-next` | —                                   | Move to next AR workspace          |
| `workspace-prev` | —                                   | Move to previous AR workspace      |

### profile-update
```json
{
  "type":    "profile-update",
  "profile": { ...SpatialProfile... }
}
```
Hot-reloads the input mapping on the glasses with zero downtime. The `profile` payload must match the shared SpatialProfile schema.

---

## Glasses → Plugin messages

### pong
```json
{ "type": "pong" }
```

### status
```json
{
  "type":    "status",
  "battery": 85,
  "layer":   1,
  "app":     "browser"
}
```
Sent on connect and whenever glasses state changes. All fields are optional except `type`. Used to update the Actions Ring status bubble.

### ack
```json
{ "type": "ack", "ref": "profile-update" }
```
Acknowledgement after a profile has been hot-reloaded.

---

## Profile JSON schema

```json
{
  "name":              "string",
  "version":           "string",
  "scrollSensitivity": 1.0,
  "zoomStep":          5,
  "defaultBrightness": 70,
  "mappings": {
    "<eventId>": {
      "action":     "string",
      "parameters": { "key": "value" }
    }
  }
}
```

### Event IDs

| ID               | Source                              |
|------------------|-------------------------------------|
| `dial-cw`        | MX Dial clockwise rotation          |
| `dial-ccw`       | MX Dial anti-clockwise rotation     |
| `dial-press`     | MX Dial centre press                |
| `btn-0`..`btn-N` | MX Creative Console keypad buttons  |
| `thumb-wheel-up` | MX Master 4 thumb wheel up          |
| `thumb-wheel-dn` | MX Master 4 thumb wheel down        |
| `gesture-left`   | MX Master 4 gesture button left     |
| `gesture-right`  | MX Master 4 gesture button right    |
