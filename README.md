# AirBridge

**Unlock your AirPods' full potential on Android.**

AirBridge is a native Android app that brings Apple-exclusive AirPods features to Android devices by reverse-engineering the Apple Audio Protocol (AAP). It communicates directly with AirPods over Bluetooth L2CAP, bypassing standard Android limitations to deliver the full AirPods experience.

<p align="center">
  <img src="icons/airpods (1).png" width="120" alt="AirBridge">
</p>

## Features

### Working Now (v0.1.0)

| Feature | Status | Description |
|---------|--------|-------------|
| BLE Scanner | ✅ | Detects AirPods via Apple 0x004C manufacturer data |
| Battery Status | ✅ | Live Left/Right/Case battery from AACP protocol |
| ANC Control | ✅ | Off / Noise Cancellation / Transparency / Adaptive |
| Ear Detection | ✅ | Auto play/pause when earbuds are removed or inserted |
| Stem Press | ✅ | Double-press = next track, Triple-press = previous |
| Conversational Awareness | ✅ | Toggle auto volume reduction when speaking |
| Adaptive Volume | ✅ | Toggle environment-aware volume adjustment |
| Rename AirPods | ✅ | Change your AirPods name from within the app |
| Head Tracking | ✅ | Start/stop spatial audio head tracking |
| Notification Battery | ✅ | Persistent notification showing L/R/Case percentages |
| Boot Auto-Start | ✅ | Service restarts automatically on device boot |

### Planned Features

- Transparency mode EQ customization (8-band per ear)
- Hearing Aid mode with audiogram input
- Head gesture detection (nod to answer, shake to decline)
- Dynamic Island-style connection popup
- Cross-device audio routing
- Home screen battery widget
- Quick Settings tile for ANC toggle
- Find My AirPods (play sound on individual buds)
- System battery metadata integration (show in Android BT settings)

## Device Compatibility

| Device | Status |
|--------|--------|
| AirPods Pro 2 (USB-C) | ✅ Fully tested |
| AirPods Pro 2 (Lightning) | ✅ Should work |
| AirPods Pro (1st gen) | ⚠️ Basic features |
| AirPods 3 / 4 / 4 ANC | ⚠️ Basic features |
| AirPods Max | ⚠️ Basic features |
| AirPods 1 / 2 | ⚠️ Battery only |

## Requirements

- **Android 13+** (API 33)
- **No root required** on OxygenOS 16 (OnePlus 13, etc.)
- Root/Xposed needed on other ROMs for L2CAP socket creation
- AirPods must be paired in Android Bluetooth settings first

## How It Works

AirBridge uses three layers of Bluetooth communication:

1. **BLE Scanner** — Detects AirPods advertisements with Apple manufacturer data (Company ID `0x004C`), parsing battery levels, model, and ear/case status from the "Nearby" beacon.

2. **L2CAP Connection** — Creates a classic Bluetooth L2CAP socket at PSM `0x1001` using reflection + `HiddenApiBypass` to access the hidden `BluetoothSocket` constructor. This is the same protocol channel Apple devices use.

3. **AACP Protocol** — Sends the 5-step handshake sequence (handshake → feature flags → notification request → proximity keys → EQ enable) and then enters a read loop to process incoming packets by opcode:
   - `0x04` Battery info
   - `0x06` Ear detection
   - `0x09` Control commands (ANC, conversational awareness, etc.)
   - `0x17` Head tracking data
   - `0x19` Stem press events
   - `0x1D` Device information
   - `0x4B` Conversational awareness level

## Building

```bash
# Clone
git clone git@github.com:Arnab1996/AirPodsCompanion.git
cd AirPodsCompanion

# Build debug APK
ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew assembleDebug

# Install on connected device
adb install app/build/outputs/apk/debug/app-debug.apk
```

Or open in Android Studio and click Run.

## Setup

1. **Pair your AirPods** in Android Settings → Bluetooth (hold the case button until LED flashes white)
2. **Install AirBridge** and grant all permissions (Bluetooth, Notifications, Phone)
3. **Open the AirPods case** near your phone — the app will detect them
4. **Tap Connect** — the AACP handshake will establish and the dashboard appears

## Architecture

```
me.arnabsaha.airpodscompanion/
├── ble/
│   ├── scanner/          # BLE advertisement scanning & parsing
│   │   ├── AirPodsScanner.kt
│   │   ├── ManufacturerDataParser.kt
│   │   └── AirPodsAdvertisement.kt
│   └── transport/        # L2CAP socket & AACP protocol
│       ├── L2capSocketFactory.kt
│       └── AacpTransport.kt
├── protocol/
│   ├── aap/              # Packet codec
│   │   └── AacpPacketCodec.kt
│   └── constants/        # Protocol byte definitions
│       ├── AacpConstants.kt
│       └── AppleBleConstants.kt
├── service/
│   └── AirPodsService.kt # Foreground service + packet dispatcher
├── ui/theme/             # Material3 theme with Apple colors
├── utils/                # Bluetooth cryptography (RPA verification)
├── receivers/            # Boot receiver
└── MainActivity.kt       # Compose UI (scanner, dashboard, settings)
```

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose + Material3
- **Bluetooth**: Android BLE API + L2CAP via reflection
- **Architecture**: Service-bound with StateFlow reactive state
- **Dependencies**: Accompanist Permissions, HiddenApiBypass, Compose Navigation

## Credits

Protocol reverse engineering based on the [LibrePods](https://github.com/kavishdevar/LibrePods) project by kavishdevar.

## License

This project is for educational and interoperability purposes.
