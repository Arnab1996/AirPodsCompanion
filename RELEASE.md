# Release Notes

All notable changes to AirBridge, newest first. Versions map to `v*` git tags, which
trigger the GitHub Actions release build.

## Unreleased

### Fixed
- Head shake ("no") is more sensitive — an ordinary shake registers, not just a vigorous one
- AirBridge stops (leaves "Active apps") when you swipe it away while not connected
- Dynamic Island dismiss no longer jumps back down after a swipe — smoother
- Find My screen and the confirm/rename dialogs compacted
- Overlay permission screen explains the Android 13+ "Allow restricted settings" step for sideloaded builds
- Battery widget shows a preview in the widget picker and updates passively over BLE (no connection needed)
- Find My no longer crashes — added the missing VIBRATE permission the proximity haptics needed

### Changed
- README expanded — badges, full supported-model list, download table, protocol/opcode details, project layout, and credits
- Compressed the dashboard — tighter cards, smaller battery gauges and section gaps (less scrolling)
- Press-and-Hold rebuilt as a compact popup (~35% smaller)
- Consolidated to a single battery widget (removed the duplicate)
- Release APK is now named `AirBridge-<version>.apk` instead of `app-release.apk`

## v0.2.0 — 2026-06-30

CapOd-inspired functional additions (passive detection) on top of the active feature set.

### Added
- Broad device-model detection — 26 AirPods/Beats models identified from the BLE advertisement
- Nearby devices + passive battery in the picker (model, L/R/case battery, signal — no connection needed)
- Passive case battery and a case-open popup while connected, via an optional low-power background scan
- Connection activity (Playing / On a call) in the dashboard header and nearby cards
- "Background Battery Updates" setting to control the connected-state scan

### Changed
- README rewritten with the current feature set and supported-models list

## v0.1.1 — 2026-06-30

First properly **signed** release (release keystore). No functional changes from 0.1.0 — the v0.1.0 APK was debug-signed.

> Upgrading from v0.1.0: uninstall it first (the debug and release signing keys differ, so it won't update in place). Updates from 0.1.1 onward install over the top.

## v0.1.0 — 2026-06-29

First tagged release — an AirPods Pro companion for Android (phone + Wear OS).

### Added
- Liquid-glass dashboard with neumorphic battery gauges and a segmented ANC control
- Wear OS dashboard restyle (gradient + glass; no real-time blur, to save battery)
- Dynamic Island popup — battery, ANC, ear state, charging ⚡, codec badge; springy entry and auto-dismiss
- Find My AirPods (RSSI proximity), low-battery alerts, Glance home/lock widget, Wear tiles + complication
- Forget This Device (true unpair via `removeBond`) behind a confirm dialog
- Opt-in "Resume Music on Connect"
- Bluetooth-off screen instead of a misleading "pair first" prompt

### Changed
- One self-syncing foreground notification (was two); drops out of "Active apps" when Bluetooth is off
- Dashboard holds through brief reconnects and reverts to the picker once both buds are cased
- Device info parsed by content, so the real model number shows

### Fixed
- Audio instability caused by a post-connect settings burst (reverted)
- Head-shake gesture detection thresholds
- Stale "ANC: Off" label on the notification

### Build
- GitHub Actions builds the APK and publishes a release on every `v*` tag

[v0.2.0]: https://github.com/Arnab1996/AirPodsCompanion/releases/tag/v0.2.0
[v0.1.1]: https://github.com/Arnab1996/AirPodsCompanion/releases/tag/v0.1.1
[v0.1.0]: https://github.com/Arnab1996/AirPodsCompanion/releases/tag/v0.1.0
