# varia-re / RideWatch

Garmin Varia Android interoperability research and an initial standalone Android
Kotlin app for the **RVR315**. Includes spoken radar alerts, reconnection, motion arming,
and a local-network group notification prototype.

## Start here

- [Build, install and use RideWatch](docs/app.md)
- [Current state](CURRENT_STATE.md)
- [Varia APK analysis and protocol evidence](reports/2026-09-19_apk_protocol.md)
- [Validation and remaining hardware checks](reports/2026-09-19_validation.md)
- [Research queue](docs/research_queue.md)
- [Agent workflow / required journals](AGENTS.md)
- [Session journal](journal/2026-09-19.md)
- [Initial research](reports/2026-09-19_initial_recon.md)
- [Artifact policy](artifacts/README.md) and [APK acquisition](docs/acquisition.md)

```sh
# JDK 17 and Android SDK 36 required
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Current build

**RideWatch v0.12.2 (versionCode 27), Android 16/API 36 and newer.**
The Kotlin app was installed on the owner's Pixel 10 Pro on October 3, 2026.
The owner uses it while riding a scooter, with the radar on a CamelBak and
spoken alerts playing while the phone stays in a pocket.

The latest recorded development checks passed 48 tests, debug assembly and
lint, plus emulator volume/audio-focus checks. See the
[audio reliability report](reports/2026-10-03_audio_reliability.md) and
[current state](CURRENT_STATE.md) for the exact evidence and remaining checks.
Numeric speed interpretation is still experimental; a successful connection
and useful everyday use do not replace controlled reference measurements.

Monitoring uses a foreground service and persistent notification. Pause
releases BLE/audio; reopening explicitly requests a connection. Android-managed
waiting handles an absent radar. Motion/presence/boot wake remain temporarily
disabled for the testing profile. Group mode is a same-Wi-Fi/hotspot prototype.
Background survival remains subject to Android/OEM policy.

[Project story and riding experience](https://mindflakes.com/posts/2026/10/03/ridewatch-bike-radar/).

Proprietary inputs and generated decompilations are local, ignored research
artifacts. No Garmin code/assets are bundled in the independently written app.

The owner supplied the goals and riding feedback; Codex did the investigation
and implementation under that direction.

## Publication review

The [November 11 source/history audit](reports/2026-11-11_publication-audit.md)
found no sensitive-data blockers in the 107 tracked files and 35 commits
reviewed. Raw vendor APKs, decompilations, captures and local device settings
remain ignored. Keep those artifacts out of public commits.
