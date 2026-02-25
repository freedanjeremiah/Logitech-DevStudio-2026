# MX SpatialBridge

> Turn Logitech MX Creative Console into a precision spatial controller for AR smart glasses — desktop-grade input for the next computing platform.

Built for the **[Logitech DevStudio 2026](https://devstudiologitech2026.devpost.com/)** hackathon.
Category: **MX Creative Console + MX Master 4 · Actions Ring: Innovate with the Actions SDK**

---

## What it does

MX SpatialBridge bridges Logitech's MX peripheral ecosystem with Android-based AR waveguide glasses, creating a new product category: **precision spatial input**.

| Input                         | AR action                                       |
|-------------------------------|-------------------------------------------------|
| MX Dial rotation (CW)         | Scroll down / zoom in                           |
| MX Dial rotation (CCW)        | Scroll up / zoom out                            |
| MX Dial press                 | Select / confirm                                |
| Keypad button 0               | Back                                            |
| Keypad button 1               | Switch AR layer (home → apps → settings → …)   |
| Keypad buttons 2–N            | Launch specific AR apps                         |
| MX Master 4 thumb wheel up    | Next AR workspace                               |
| MX Master 4 thumb wheel down  | Previous AR workspace                           |
| MX Master 4 gesture left/right| Horizontal pan                                  |

Configurations sync to the glasses over local WiFi in **< 40 ms** via WebSocket.
Auto-discovery via **mDNS** — no IP addresses to configure.

---

## Architecture

```
┌─────────────────────────────────┐         ┌──────────────────────────────┐
│   Logitech Options+ (Windows)   │         │   AR Glasses (Android AOSP)  │
│                                 │         │                              │
│  ┌─────────────────────────┐   │  WiFi   │  ┌────────────────────────┐  │
│  │  MX SpatialBridge       │◄──┼─────────┼─►│  ProfileSyncServer     │  │
│  │  Logi Actions SDK Plugin│   │WebSocket│  │  (WebSocket :58432)    │  │
│  │                         │   │         │  └──────────┬─────────────┘  │
│  │  Commands:              │   │  mDNS   │             │ hot-reload     │
│  │  • ArSelectCommand      │◄──┼─────────┼─►  mDNS     │               │
│  │  • ArBackCommand        │   │discovery│  ┌──────────▼─────────────┐  │
│  │  • ArSwitchLayerCommand │   │         │  │  InputMapperEngine     │  │
│  │  • ArLaunchAppCommand   │   │         │  │  (profile → actions)   │  │
│  │  • ArConnectGlasses     │   │         │  └──────────┬─────────────┘  │
│  │                         │   │         │             │                │
│  │  Adjustments:           │   │  BT HID │  ┌──────────▼─────────────┐  │
│  │  • ArScrollAdjustment   │◄──┼─────────┼──│  MxHidInputService    │  │
│  │  • ArZoomAdjustment     │   │ USB OTG │  │  (HID events)          │  │
│  │  • ArBrightnessAdj.     │   │         │  └──────────┬─────────────┘  │
│  │                         │   │         │             │                │
│  │  Sync:                  │   │         │  ┌──────────▼─────────────┐  │
│  │  • GlassesDiscovery     │   │         │  │  ArActionDispatcher    │  │
│  │  • ProfileSyncClient    │   │         │  │  (Android system calls)│  │
│  └─────────────────────────┘   │         │  └────────────────────────┘  │
│                                 │         │                              │
│  Actions Ring bubbles:          │         │  Bluetooth HID ←──────────  │
│  • AR Select  • AR Back         │         │  USB-C OTG   ←──────────    │
│  • AR Layer   • AR Connect ●    │         │       ▲           ▲         │
└─────────────────────────────────┘         └───────┼───────────┼─────────┘
                                                     │           │
                                          ┌──────────┴──┐ ┌─────┴──────────┐
                                          │ MX Creative │ │  MX Master 4   │
                                          │   Console   │ │  Thumb Wheel   │
                                          │   Dialpad   │ │  Gesture Btn   │
                                          └─────────────┘ └────────────────┘
```

---

## Repository structure

```
mx-spatialbridge/
├── plugin/                        # C# Logi Actions SDK plugin
│   ├── src/
│   │   ├── Plugin.cs              # MxSpatialBridgePlugin (main class)
│   │   ├── Commands/
│   │   │   ├── ArSelectCommand.cs
│   │   │   ├── ArBackCommand.cs
│   │   │   ├── ArSwitchLayerCommand.cs
│   │   │   ├── ArLaunchAppCommand.cs
│   │   │   └── ArConnectGlassesCommand.cs
│   │   ├── Adjustments/
│   │   │   ├── ArScrollAdjustment.cs
│   │   │   ├── ArZoomAdjustment.cs
│   │   │   └── ArBrightnessAdjustment.cs
│   │   ├── Sync/
│   │   │   ├── GlassesDiscovery.cs    # mDNS auto-discovery
│   │   │   └── ProfileSyncClient.cs   # WebSocket client + heartbeat
│   │   └── Profiles/
│   │       ├── ProfileManager.cs
│   │       └── SpatialProfile.cs      # Shared JSON profile schema
│   └── metadata/
│       └── LoupedeckPackage.yaml      # Plugin manifest
│
├── android/                       # Android app for AR glasses
│   └── app/src/main/
│       ├── java/com/logitech/mxspatialbridge/
│       │   ├── MxSpatialBridgeApp.java
│       │   ├── hid/
│       │   │   ├── MxHidInputService.java   # BT HID + USB OTG
│       │   │   └── HidEventParser.java      # HID event → event ID
│       │   ├── mapper/
│       │   │   ├── InputMapperEngine.java   # event ID → AR action
│       │   │   └── ArActionDispatcher.java  # AR action → Android API
│       │   ├── sync/
│       │   │   ├── ProfileSyncServer.java   # WebSocket server
│       │   │   └── NsdAdvertiser.java       # mDNS advertisement
│       │   └── ui/
│       │       ├── ArOverlayActivity.java   # Transparent HID capture
│       │       └── ControlMappingActivity.java
│       └── AndroidManifest.xml
│
├── profiles/
│   └── default-spatial-profile.json   # Default input mapping
│
└── docs/
    └── protocol.md                # WebSocket protocol spec
```

---

## Build & run

### Desktop plugin (Windows)

**Prerequisites:**
- Windows 10/11
- .NET 8 SDK
- Logitech Options+ installed
- LogiPluginTool: `dotnet tool install --global LogiPluginTool`

```bash
# Install plugin and start with hot-reload
cd plugin/src
dotnet build
logiplugintool install
dotnet watch build   # hot-reload on save
```

The plugin appears as **"MX SpatialBridge"** in Logitech Options+.
Actions Ring shows AR control bubbles automatically.

### Android app (AR glasses)

**Prerequisites:**
- Android Studio Hedgehog or later
- Android SDK 34, NDK not required
- AR glasses running Android 10+ (AOSP or compatible)

```bash
cd android
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Launch **MX SpatialBridge** on the glasses and note the IP address shown.

---

## Connection flow

1. Glasses app starts `ProfileSyncServer` — WebSocket on port `58432`
2. Glasses app starts `NsdAdvertiser` — announces `_mxspatialbridge._tcp` via mDNS
3. Desktop plugin starts `GlassesDiscovery` — scans for `_mxspatialbridge._tcp` (mDNS port 5353)
4. Plugin finds glasses → opens WebSocket connection to port 58432 (10s timeout, 30s keep-alive)
5. Glasses sends initial `status` message
6. Plugin pushes active `SpatialProfile` via `profile-update`
7. Every subsequent MX input event streams an `action` message to the glasses
8. Profile changes in Options+ sync instantly (< 40 ms round-trip)

---

## Actions Ring integration

MX SpatialBridge registers dedicated bubbles in the **Actions Ring** overlay:

| Bubble          | Type       | Function                                  |
|-----------------|------------|-------------------------------------------|
| AR Select       | Command    | Select focused AR element                 |
| AR Back         | Command    | Navigate back                             |
| AR Layer →      | Command    | Switch to next AR workspace layer         |
| AR Browser      | Command    | Launch browser on glasses                 |
| AR Connect ●    | Command    | Connection status indicator + re-scan     |
| AR Scroll       | Adjustment | Dial rotation → AR scroll                 |
| AR Zoom         | Adjustment | Dial rotation → AR zoom (reset = 100%)    |
| AR Brightness   | Adjustment | Dial rotation → display brightness        |

The **AR Connect** bubble changes colour (green = connected, grey = searching),
giving users real-time connection feedback from within the Actions Ring. Bubbles appear automatically when the plugin is loaded in Options+.

---

## Why physical input wins for spatial computing

| Metric            | Camera gesture  | MX peripheral      |
|-------------------|-----------------|---------------------|
| Accuracy          | ~15 mm          | Sub-mm (haptic det.)|
| Latency           | 80–150 ms       | < 20 ms (BT HID)   |
| Fatigue           | Gorilla arm     | Natural desk use    |
| Low-light support | Fails           | Always works        |
| Social acceptability | Disruptive | Invisible           |

---

## Tech stack

**Plugin:** C# · .NET 8 · Logi Actions SDK · Makaretu.Dns.Multicast · System.Net.WebSockets

**Android:** Java · Android SDK 34 · Java-WebSocket · Gson · Android NSD · USB HID host · Bluetooth HID

**Protocol:** WebSocket · mDNS/Bonjour · JSON (local WiFi only)

**Hardware:** MX Creative Console · MX Master 4 · Android waveguide AR glasses

---

## Licence

MIT — see [LICENSE](LICENSE). Built for Logitech DevStudio 2026.
