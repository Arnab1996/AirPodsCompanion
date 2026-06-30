# AirBridge

A companion app that brings AirPods and Beats features to Android.

[![Release](https://img.shields.io/github/v/release/Arnab1996/AirPodsCompanion?include_prereleases&logo=github)](https://github.com/Arnab1996/AirPodsCompanion/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/Arnab1996/AirPodsCompanion/total?logo=github&label=Downloads)](https://github.com/Arnab1996/AirPodsCompanion/releases)
![Android](https://img.shields.io/badge/Android-10%2B-3DDC84?logo=android&logoColor=white)

No ads, no analytics, no account.

> Not affiliated with Apple. AirPods and Beats are trademarks of Apple Inc.

## Features

* Battery for both buds and the case, with charging status.
* Noise control: Off, Noise Cancellation, Transparency, Adaptive.
* Personalized Volume and Conversation Awareness.
* Adjustable chime volume.
* Automatic ear detection that pauses when you take a bud out and resumes when both are back in.
* Head gestures: nod to answer a call, shake to decline.
* Find My AirPods with a proximity meter and haptics.
* A Dynamic Island-style popup on connect and when you open the case.
* Low-battery alerts at a threshold you set.
* Home and lock-screen battery widget.
* Wear OS dashboard, tile, and battery complication.
* Android Auto battery and noise-control pane.
* Tasker and MacroDroid broadcasts for automation.
* Nearby devices: battery for any AirPods or Beats around you, no pairing needed.
* Rename, forget, and resume music on connect.
* Light and dark themes.

Active control (noise modes, ear detection, head gestures) is tuned for AirPods Pro 2. Battery and detection work across every model below.

## Supported devices

AirBridge identifies 26 models from their Bluetooth advertisement.

**AirPods**

* AirPods (1st, 2nd, 3rd, 4th generation)
* AirPods 4 (ANC)
* AirPods Pro, Pro 2, Pro 2 (USB-C), Pro 3
* AirPods Max, Max (USB-C), Max (2nd generation)

**Beats**

* Powerbeats 3, Powerbeats 4, Powerbeats Pro, Powerbeats Pro 2
* Beats X, Solo 3, Solo 4, Solo Pro, Solo Buds
* Beats Studio Buds, Studio Buds+, Studio Pro
* Beats Flex, Fit Pro

## Download

| Source | |
|--------|--|
| GitHub Releases (signed APK) | [Latest release](https://github.com/Arnab1996/AirPodsCompanion/releases/latest) |

Download the APK and sideload it. Every release is signed with the same key, so updates install over the top.

## Requirements

* Android 10 (API 29) or later.
* Pair the AirPods in **Settings → Bluetooth** first.
* Battery and nearby detection work on any device.
* Active control opens a Bluetooth L2CAP socket through a hidden API. This works without root on OxygenOS 16 (for example, the OnePlus 13); other ROMs may need root or Xposed.
* On Android 13+, a sideloaded build needs **App info → ⋮ → Allow restricted settings** before you can grant the overlay permission used by the popup.

## How it works

AirBridge uses two channels.

**Passive — BLE advertisement.** It reads the Apple proximity advertisement (company ID `0x004C`, the "Nearby" beacon) for battery, model, charging, and case state. No connection or pairing is needed; this is how it shows nearby devices and case battery.

**Active — AACP over L2CAP.** It opens a classic Bluetooth socket at PSM `0x1001` using reflection and `HiddenApiBypass`, the same channel Apple's own devices use. After a five-step handshake it reads packets by opcode:

| Opcode | Carries |
|--------|---------|
| `0x04` | Battery |
| `0x06` | Ear detection |
| `0x09` | Control commands (noise mode, Conversation Awareness, …) |
| `0x17` | Head tracking |
| `0x19` | Stem press |
| `0x1D` | Device info |
| `0x4B` | Conversation Awareness level |

A foreground service holds the connection and publishes state to the UI through `StateFlow`.

## Building

```bash
git clone git@github.com:Arnab1996/AirPodsCompanion.git
cd AirPodsCompanion
./gradlew :app:installDebug      # phone
./gradlew :wear:installDebug     # Wear OS
```

Releases run on GitHub Actions: push a `v*` tag and the workflow builds and publishes a signed APK.

## Project layout

```
me.arnabsaha.airpodscompanion/
├── ble/scanner/     BLE advertisement scanning and parsing
├── ble/transport/   L2CAP socket and AACP protocol
├── protocol/        packet codec and byte constants
├── service/         foreground service and packet dispatcher
├── ui/              Compose dashboard, Dynamic Island popup, theme
├── widgets/         Glance battery widget
└── auto/            Android Auto
wear/                Wear OS app (separate Gradle module)
```

## Get help

Open a [GitHub issue](https://github.com/Arnab1996/AirPodsCompanion/issues).

## Acknowledgements

* [OpenPods](https://github.com/adolfintel/OpenPods) — the BLE advertisement format.
* [LibrePods](https://github.com/kavishdevar/librepods) by @kavishdevar — AACP and L2CAP protocol research.
* [CAPod](https://github.com/d4rken-org/capod) by @d4rken — feature inspiration and the verified model-ID set.
* Martin, J., Alpuche, D., Bodeman, K., et al. *Handoff All Your Privacy: A Review of Apple's Bluetooth Low Energy Continuity Protocol* (PoPETs 2019) — the advertisement structure.

## License

For educational and interoperability purposes.
