# AirBridge — Development TODO

> **App Name**: AirBridge (formerly AirPods Companion)
> **Golden Rule**: Every response updates this file. What's done gets checked. What's next is stated.

## Phase 1: Project Setup & Foundation [COMPLETE]
- [x] Analyze LibrePods source code (cloned to /tmp/LibrePods)
- [x] Extract and document full AACP protocol spec → `memory/librepods-protocol.md`
- [x] Create Android project structure at `~/IdeaProjects/AirPodsCompanion`
- [x] All build files (settings, gradle, version catalog, properties)
- [x] `AndroidManifest.xml` — all BT/Location/FG-service/overlay/phone permissions
- [x] `MainActivity.kt` — permission request flow
- [x] `AirPodsService.kt` — foreground service + notification channel
- [x] `BootReceiver.kt` — auto-start on boot
- [x] Theme, protocol constants, gradle wrapper, resource stubs

## Phase 2: BLE Scanner & Manufacturer Data Parser [COMPLETE]
- [x] `AirPodsAdvertisement.kt`, `ManufacturerDataParser.kt`, `AirPodsScanner.kt`
- [x] Scanner integrated into service, StateFlow emissions
- [x] Scanner UI with device cards, battery, RSSI, status chips
- [x] Verified on OnePlus 13 — scanner detects AirPods Pro 2 ✓

## Phase 3: L2CAP Connection & AACP Handshake [COMPLETE]
- [x] `L2capSocketFactory.kt` — HiddenApiBypass with 4 constructor fallbacks (matches LibrePods)
- [x] `AacpTransport.kt` — full handshake sequence + reconnect with exponential backoff
- [x] `AacpPacketCodec.kt` — encode/decode AACP packets
- [x] `BluetoothCryptography.kt` — AES/ECB RPA verification
- [x] Service: connectToDevice() searches **bondedDevices** (not BLE ad address)
- [x] UI: Connect button + connection state chip
- [ ] **YOU DO**: Pair AirPods in Android Bluetooth settings, then tap Connect in app

## Phase 3.5: Branding [COMPLETE]
- [x] Renamed app to "AirBridge" (all strings, notification, UI)
- [x] New launcher icon: earbuds + bridge arc design (dark navy background)

## Phase 4: Protocol Event Handlers [COMPLETE]
- [x] Packet dispatcher — routes by opcode in AirPodsService
- [x] Battery handler (0x04) — parses L/R/Case + charging, updates notification
- [x] Ear detection (0x06) — auto play/pause via AudioManager
- [x] ANC handler (0x09/0x0D) — tracks mode state
- [x] Conversational awareness (0x4B), stem press (0x19), device info (0x1D)
- [x] Proximity keys response (0x31) — placeholder for future IRK storage

## Phase 5: UI — Main Dashboard [COMPLETE]
- [x] Full dashboard with circular battery gauges (animated fill arcs)
- [x] Ear detection dots (green=in, gray=out)
- [x] ANC segmented control (Off/ANC/Transparency/Adaptive)
- [x] Settings rows (Conversational Awareness, Adaptive Volume, Ear Detection)
- [x] Bonded device name displayed as header
- [x] Scanner screen with model deduplication (keeps strongest RSSI per model)
- [x] Notification shows "L: X% R: Y% Case: Z%" when connected
- [x] Status chips with Apple system colors
- [x] Proper typography using MaterialTheme throughout
- [x] windowInsetsPadding for status bar (no more hardcoded 60dp)
- [ ] `ui/screens/AirPodsSettingsScreen.kt` — settings list (iOS-style)
- [ ] Navigation with slide transitions

## Phase 6: UI — Settings Sub-Screens
- [ ] NoiseControlSettings, TransparencySettings, ConversationalAwareness
- [ ] AdaptiveVolume, StemConfig, HeadTracking
- [ ] HearingAid (enrollment + adjustments + audiogram)
- [ ] Accessibility, Rename, Debug (packet log viewer)

## Phase 7: Connection Popup & Widgets
- [ ] Floating overlay popup (SYSTEM_ALERT_WINDOW) — iOS-style case-open animation
- [ ] Home screen battery widget
- [ ] Noise control widget
- [ ] Quick Settings tile for ANC toggle

## Phase 8: Advanced Features
- [ ] **Head Tracking / Spatial Audio**: Start/stop packets known, orientation from bytes 43-48
  - 10-sample calibration, pitch/yaw from o2/o3 values, 50ms polling
  - Drive Android spatial audio APIs with head orientation data
- [ ] **Head Gesture Detection**: Nod=yes, shake=no
  - Confidence scoring: amplitude(40%), rhythm(20%), alternation(20%), isolation(20%)
  - Peak threshold 400 units, direction change 150-175, min 3-4 extremes
  - Use for call answer/reject, accessibility shortcuts
- [ ] **Hearing Aid Mode** (requires Apple VendorID / Xposed):
  - ATT Handle 0x2A, 104-byte structure with per-ear EQ, amplification, tone, ANR
  - Audiogram input screen, frequency-specific adjustments
- [ ] **Transparency EQ Customization** (requires Apple VendorID / Xposed):
  - ATT Handle 0x18, 100-104 bytes, 8-band per-ear EQ
  - Real-time slider adjustments with 100ms debounce
- [ ] **Cross-Device / Smart Routing**: RFCOMM relay between devices
  - UUID: 1abbb9a4-10e4-4000-a75c-8953c5471342
  - Battery/ANC/ear data relay, device takeover UI
- [ ] **Dynamic Island Popup**: Animated overlay on case-open detection
  - Scale+translate animation, drag interaction, auto-close 4.5s
  - Connection types: CONNECTED, TAKING_OVER, MOVED_TO_REMOTE
- [ ] **Find My AirPods**: Play sound on individual buds
- [ ] **Live Listen**: Use AirPods mic as hearing amplifier (not in LibrePods, needs research)
- [ ] Root/Xposed hooks (for Apple VendorID, L2CAP on non-OxygenOS16)

## Phase 9: UI/UX Overhaul (pending review agent results)
- [ ] Apply UI/UX review findings
- [ ] Apple-level polish and animations
- [ ] Proper Typography hierarchy
- [ ] Better empty states and micro-interactions

## Phase 10: Polish & Production
- [ ] Error handling across all protocol handlers
- [ ] Battery in persistent notification (with inline controls)
- [ ] ProGuard rules for reflection classes
- [ ] Automated testing guide

---

## Current Status
**Phase 1-3 + Branding + Critical Bug Fixes: COMPLETE**

### Bug Fixes Applied (from code review):
- [x] **F-4**: Lid open bit polarity inverted → fixed (0 = open)
- [x] **F-3**: Left/right battery flip when primaryIsLeft=false → fixed
- [x] **M-1**: CoroutineScope leak in AacpTransport → SupervisorJob now cancelled in disconnect()
- [x] **M-3**: CoroutineScope leak in AirPodsService → serviceJob cancelled in onDestroy()
- [x] **T-1**: Race condition in scanner → atomic `update {}` on StateFlow
- [x] **T-2**: Non-volatile `isScanning` flag → now `@Volatile`
- [x] **L-1**: No connection timeout → added `withTimeout(8s)` on sock.connect()
- [x] **B-1**: LOW_LATENCY scan draining battery → switched to BALANCED
- [x] **PF-3**: fetchUuidsWithSdp async misuse → use cached UUIDs only

### UI Fixes Applied (from UI/UX review):
- [x] Created `Type.kt` with Apple-aligned type scale
- [x] Disabled dynamic color — always Apple blue branding
- [x] Fixed light theme primary from wrong indigo to correct #007AFF
- [x] Added dark theme adaptive blue #0A84FF
- [x] Replaced all Material green/red/orange with Apple system colors
- [x] Created `values-night/themes.xml` for proper dark mode
- [x] Added typography to MaterialTheme

## Next Action
**Phase 4: Protocol Event Handlers** — I will write the packet dispatcher and handlers now.
**Pending**: Advanced features research agent completing (spatial audio, head gestures, hearing aid)
