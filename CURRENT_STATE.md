# Current state

Updated: 2026-09-19, America/Los_Angeles.

## Delivered

- Android **RideWatch v0.2** app, Pixel 10 baseline (Android 16/API 36); [build/use guide](docs/app.md).
- APK: `app/build/outputs/apk/debug/app-debug.apk` (generated, ignored).
- Decompiled Varia 5.1.3/157. Recovered legacy UUIDs, target ID/threat masks,
  distance, quarter-m/s speed scaling, errors and freshness behavior.
  [Provenance and source map](reports/2026-09-19_apk_protocol.md).
- Standalone BLE service, reconnects, stale/error states, optional TTS speed,
  motion arming, companion presence integration and encrypted LAN group prototype.
- JVM tests, real UDP socket test, SDK-36 build and lint. API-30 emulator checks
  are historical v0.1 evidence only. Current v0.2 has not run on-device.
  [Validation and limits](reports/2026-09-19_validation.md).
- Bootstrap committed as `6617106`; subsequent app/research work follows it.

## Evidence boundary

No physical phone or RVR315 tested. Decompiled app evidence is not a hardware
capture. Speed is labeled "radar speed" pending validation of its reference/sign.
Companion wake and current-Android background survival remain unverified on-device.
Motion mode uses continuous GPS while armed; reboot posts a resume reminder.
Group mode requires the same Wi-Fi/hotspot and synchronized clocks.

## Continue here

Read `docs/app.md`, the protocol report, and newest journal entry. The next useful
step is RVR315 capture and lifecycle/power measurements when hardware becomes
available. Improve diagnostics/low-power wake based on those results, not guesses.
Raw APK, decompilation, captures, tools and external checkouts remain ignored.
