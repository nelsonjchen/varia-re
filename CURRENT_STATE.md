# Current state

Updated: 2026-10-03, America/Los_Angeles.

## Delivered

- v0.12.2 installed Pixel October3:100% media target, routine ducking and urgent
  transient focus, output checks and actual-volume/mute warnings.48 tests/build/
  lint and emulator volume/focus checks pass; Pixel actual media25/25 observed.
  Physical listening/headphone switching pending.
  [Audio reliability](reports/2026-10-03_audio_reliability.md).

- v0.12.0 adds Android-managed absent-radar waiting, bounded stack-error recovery,
  immediate reopen attempts and stops rider-speed location while disconnected.
  Installed Pixel September29; 41 tests/build/lint and radar-off waiting/Pause/
  reopen checks pass. Real return and overnight power pending. [Connection design](reports/2026-09-29_autoconnect.md).

- Android **RideWatch v0.12.2** app, Pixel 10 baseline (Android 16/API 36); [build/use guide](docs/app.md).
- v0.11.0 adds count-change speech for multiple vehicles; lone approaches remain
  numbers-only. Installed on Pixel as v0.11.1 at 08:19 PDT September 27; road listening pending.
- Test/preview actions now explain that no radar is needed and offer Pause & test
  or Pause & preview, then launch automatically after service teardown.
- v0.11.2 supports battery indications/notifications plus independent60s reads,
  request timeout and visible sample age. Included in installed v0.12.0;
  constant84% cause remains unverified while radar is off.
  [Battery corrections](reports/2026-09-28_battery_reliability.md).
- BLE/behavior audit identified missing target-indication and model/firmware reads;
  [other differences and priorities](reports/2026-09-20_divergence_audit.md).
- Turning off speed repeat mutes normal ride TTS; disconnect/last-known battery
  speech remains enabled under the master speech switch.
- Navigation/media-volume tones/TTS, configurable start minimum (default 80%), conditional
  restoration on Pause and preview close. 30 tests/build/lint plus emulator
  volume/focus checks pass; Installed on Pixel at 09:40 PDT; listening checks pending.
  [Audio controls](reports/2026-09-20_media_volume.md).
- APK: `app/build/outputs/apk/debug/app-debug.apk` (generated, ignored).
- v0.9 reconnects on opening (including after Pause), removes the name header,
  and styles primary/secondary action buttons. Emulator UI/lifecycle checks pass;
  included in the v0.10.5 Pixel install.
- Package/namespace: `com.mindflakes.ridewatch` (core `.core`). Old
  `dev.nelson.ridewatch` uninstalled at user request; fresh app needs radar pairing.
- All production code is Kotlin. 26 tests, build/lint and API36 emulator checks
  pass. v0.10.5/21 installed on the physical Pixel at 09:40 PDT on September 20; disconnect audibility
  still needs a real radar check.
- Direct BLE-disconnect chime + last-known battery speech; retry attempts stay
  quiet, and reconnect sounds cannot truncate the battery announcement.
  [Migration and disconnect checks](reports/2026-09-19_kotlin_disconnect.md).
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
- Gentler synthesized Varia-style chimes (v0.8.1 backs off saturation/harmonics),
  optional speech after tones, temporary music
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
releases BLE/audio. Resume or explicitly reopening starts a connection attempt.
Rotation/permission dialogs preserve Pause. A fresh unpaired app can scan for
the radar after permissions are granted. Motion and companion wake plus boot reminders are disabled for
this testing build. [Lifecycle checks](reports/2026-09-19_testing_lifecycle.md).

## Evidence boundary

Physical Pixel 10 Pro/Android 17 connection now observed: battery 84%, live
empty stream after Bluetooth address fix. No controlled vehicle pass or raw
packet capture yet. Decompiled app evidence is not a hardware capture. Speed remains experimental pending reference/sign validation; spoken output is
now numbers-only at 1.4× rate, repeated every second. Google fused rider
speed is added for an estimated car road speed; readings must be <=3 seconds old
and report speed accuracy <=1.5 m/s. Unavailable rider speed contributes zero,
per user preference, with an explicit UI indication.
[Speed fusion and physical checks](reports/2026-09-19_speed_fusion.md).
Companion wake and current-Android background survival remain unverified on-device.
Motion mode and boot reminders are currently disabled for testing.
Group mode requires the same Wi-Fi/hotspot and synchronized clocks.

## Continue here

Read `docs/app.md`, the protocol report, and newest journal entry. The next useful
step is RVR315 capture and lifecycle/power measurements when hardware becomes
available. Improve diagnostics/low-power wake based on those results, not guesses.
Raw APK, decompilation, captures, tools and external checkouts remain ignored.
