# AirBridge

An open-source AirPods companion for Android — battery, noise control, ear detection, head gestures, and Find My, on your phone, watch, and car.

AirBridge works two ways. It decodes the public Apple proximity advertisement for battery and model without connecting. For control, it opens an AACP session over a Bluetooth L2CAP channel.

> Not affiliated with Apple. AirPods and Beats are trademarks of Apple Inc.

## Features

- **Battery** — live left, right, and case levels with charging status.
- **Noise control** — Off, Noise Cancellation, Transparency, and Adaptive.
- **Audio** — Personalized Volume, Conversation Awareness, and chime volume.
- **Automatic Ear Detection** — pauses when you remove a bud, resumes when both are back in.
- **Head gestures** — nod to answer a call, shake to decline.
- **Find My AirPods** — a signal-strength meter with haptics to walk you to a bud.
- **Dynamic Island popup** — battery, ANC, charging, and codec at a glance; auto-dismisses.
- **Low-battery alerts** at a threshold you choose.
- **Glance widget** for the home and lock screen.
- **Wear OS** — a watch dashboard, a tile, and a battery complication.
- **Android Auto** — battery and noise control on the car display.
- **Automation** — broadcasts for Tasker and MacroDroid.
- **Nearby devices** — shows AirPods and Beats around you with battery, no pairing needed.
- **Convenience** — rename, forget, and resume music on connect.
- **Liquid-glass interface** in light and dark.

## Supported devices

AirBridge identifies 26 models from their Bluetooth advertisement.

**AirPods** — 1st, 2nd, 3rd, and 4th generation; 4th generation (ANC); Pro; Pro 2; Pro 2 (USB-C); Pro 3; Max; Max (USB-C); Max (2nd generation).

**Beats** — Powerbeats 3, Powerbeats 4, Powerbeats Pro, Powerbeats Pro 2, Beats X, Solo 3, Solo 4, Solo Pro, Solo Buds, Studio Buds, Studio Buds+, Studio Pro, Flex, Fit Pro.

Battery and detection work across the list. Active control (noise modes, ear detection, head gestures) is tuned for AirPods Pro 2.

## Requirements

- Android 10 (API 29) or later.
- Pair the AirPods in Android Settings → Bluetooth first.
- Passive battery and detection work on any device.
- Active control opens a Bluetooth L2CAP socket through a hidden API. This works without root on OxygenOS 16 (such as the OnePlus 13); other ROMs may need root or Xposed.

## Install

Download the signed APK from [Releases](https://github.com/Arnab1996/AirPodsCompanion/releases/latest) and sideload it.

## Build

```bash
./gradlew :app:installDebug      # phone
./gradlew :wear:installDebug     # Wear OS
```

GitHub Actions builds and publishes a signed release on any `v*` tag.

## How it works

1. **BLE scanner** — reads the Apple advertisement (company ID `0x004C`, "Nearby" beacon) for battery, model, and case state. No connection needed.
2. **L2CAP connection** — opens a classic Bluetooth socket at PSM `0x1001` using reflection and `HiddenApiBypass`, the same channel Apple devices use.
3. **AACP protocol** — runs a five-step handshake, then dispatches incoming packets by opcode: battery `0x04`, ear detection `0x06`, control `0x09`, head tracking `0x17`, stem press `0x19`, device info `0x1D`, conversation awareness `0x4B`.

Built with Kotlin and Jetpack Compose. A foreground service holds the connection and exposes state through `StateFlow`.

## Credits

- Protocol research from [LibrePods](https://github.com/kavishdevar/LibrePods).
- Advertisement format from the [OpenPods](https://github.com/adolfintel/OpenPods) project.
- Feature set inspired by [CAPod](https://github.com/d4rken-org/capod).

AirBridge is independent and not affiliated with Apple.

## License

For educational and interoperability purposes.
