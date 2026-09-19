# varia-re / RideWatch

Garmin Varia Android interoperability research and an initial standalone Android
app for the **RVR315**. Includes spoken radar alerts, reconnection, motion arming,
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
# JDK 17 and Android SDK 35 required
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

**Built and emulator-tested; not yet validated with a physical RVR315.**
Motion arming currently uses GPS. Group mode uses the same Wi-Fi/hotspot. Background
survival is subject to Android/OEM policy; this is not an unkillable service.

Proprietary inputs and generated decompilations are local, ignored research
artifacts. No Garmin code/assets are bundled in the independently written app.
