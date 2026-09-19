# Current state

Updated: 2026-09-19, America/Los_Angeles.

## Delivered

- Android **RideWatch v0.6.4** app, Pixel 10 baseline (Android 16/API 36); [build/use guide](docs/app.md).
- APK: `app/build/outputs/apk/debug/app-debug.apk` (generated, ignored).
- Decompiled Varia 5.1.3/157. Recovered legacy UUIDs, target ID/threat masks,
  distance, quarter-m/s speed scaling, errors and freshness behavior.
  [Provenance and source map](reports/2026-09-19_apk_protocol.md).
- Standalone BLE service, reconnects, stale/error states, optional TTS speed,
  motion arming, companion presence integration and encrypted LAN group prototype.
- Adaptive launcher icon: white bicycle/amber radar on navy; separate white
  notification symbol and themed-icon silhouette.
- Vibration alerts enabled by default, with individual replay buttons under
  Controls → Test alerts; Android reports approach/urgent/lost vibration
  patterns completed on the physical Pixel. Perceived strength not yet rated.
- Loudness-mastered synthesized Varia-style chimes, optional speech after tones, temporary music
  ducking and manual sound previews. [Audio research](reports/2026-09-19_audio.md).
- Graphical distance lane with bike/car icons, threat banner, nearest distance,
  host-relative group labeling and a Controls panel. [Visual QA](design-qa.md).
- JVM tests, real UDP socket test, SDK-36 build and lint. Android-16 emulator graphical preview, stale/offline states and responsive
  layout checked. First physical connection/battery/live empty stream verified on Pixel 10 Pro
  (Android 17) with user’s radar. API-30 checks are historical v0.1 only.
  [Validation and limits](reports/2026-09-19_validation.md).
- Bootstrap committed as `6617106`; subsequent app/research work follows it.

## Current testing lifecycle

Foreground service stays active in background/screen off; Pause stops it and
releases BLE/audio. Pause persists until Resume. Opening after pairing connects
unless paused. Motion and companion wake plus boot reminders are disabled for
this testing build. [Lifecycle checks](reports/2026-09-19_testing_lifecycle.md).

## Evidence boundary

Physical Pixel 10 Pro/Android 17 connection now observed: battery 84%, live
empty stream after Bluetooth address fix. No controlled vehicle pass or raw
packet capture yet. Decompiled app evidence is not a hardware capture. Speed remains experimental pending reference/sign validation; spoken output is
now just “X mph” at 1.4× rate, per user preference.
Companion wake and current-Android background survival remain unverified on-device.
Motion mode and boot reminders are currently disabled for testing.
Group mode requires the same Wi-Fi/hotspot and synchronized clocks.

## Continue here

Read `docs/app.md`, the protocol report, and newest journal entry. The next useful
step is RVR315 capture and lifecycle/power measurements when hardware becomes
available. Improve diagnostics/low-power wake based on those results, not guesses.
Raw APK, decompilation, captures, tools and external checkouts remain ignored.
