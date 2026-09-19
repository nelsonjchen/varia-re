# Varia 5.1.3 static analysis and implementation contract

## Input / reproducibility

Acquired 2026-09-19 around 08:46 PDT from
`https://d.apkpure.net/b/APK/com.garmin.android.apps.variamobile?version=latest`.
Local input: `artifacts/apk/varia-clean.apk` (single APK, contains six DEX files).
Package `com.garmin.android.apps.variamobile`, versionName **5.1.3**, versionCode
**157**, minSdk 28, targetSdk 35, compileSdk 36.
SHA-256: `187985bfc7ba6209b3c763d71acc9c28530d95ee250715425d66a75412526d87`.

Android build-tools 36.0.0 `apksigner verify --print-certs` succeeded. Certificate
SHA-256: `84ff2bfa395c5a21e53ae29633e4a6e68b6b5c6707bb6084c25b70f866b71729`;
subject O=Garmin International, L=Olathe, ST=KS, C=US. The signature establishes
integrity under this certificate, not independently verified official distribution;
no installed Play build was available for comparison. Do not install this input.

JADX 1.5.6, `jadx -d artifacts/decompiled/varia -j 4 <apk>` produced 178
errors across the whole app. Focused legacy parser output is readable; a second
`--single-class je.h0 --decompilation-mode fallback --no-res` pass verifies masks
and scaling in raw instructions (`artifacts/research/h0-fallback.java`).
Use the input hash to reproduce paths; obfuscated class names are version-specific.

## Independently recovered legacy contract (static evidence)

Paths below are under `artifacts/decompiled/varia/sources/`.

| Finding | Evidence |
| --- | --- |
| Radar service `6a4e3200-667b-11e3-949a-0800200c9a66` | `fe/b.java` static initializer and `a(int)` UUID constructor, argument 12800 |
| Legacy notify characteristic `6a4e3203-667b-11e3-949a-0800200c9a66` | `fe/b.java`, argument 12803; `he/q0.java` notification command |
| Alternate V2 characteristic ends `3204` | `fe/b.java`, argument 12804; separate handler, not needed for RVR315 prototype |
| Legacy delivery routes to handler ID 4 | `fe/x0.java:c`, radar service and legacy characteristic comparisons |
| Header bit 0 denotes error; error packet must have length 2; byte 1 is unsigned code | `je/g0.java:a` |
| Target count `(length-1)/3`; event ID header upper nibble | `je/i0.java:invokeSuspend` |
| Three-byte target begins at `1 + 3*i` | `je/h0.java:invokeSuspend` |
| ID is first target byte AND 0x3f | `je/h0.java` and raw fallback |
| Threat is first byte bits 7..6: 2=Normal, 3=High, others=AllClear | `je/h0.java`; named levels in `je/g1.java` |
| Distance is unsigned byte 1, meters; app discards distance <=0 or >154 | `je/h0.java`, `je/p0.java:toString` |
| Speed is unsigned byte 2 multiplied by 0.25 m/s | `je/h0.java`, raw fallback; `je/p0.java:toString` names units |
| No-data monitor fires after 2000 ms | `je/j0.java` |
| Tracks age out after 500 ms for range <5m, otherwise 2000 ms | `je/e1.java`; watcher map in `je/c1.java` |
| Basic command list reads model, subscribes legacy measurement, reads firmware | `fe/y0.java:a`; `he/m0.java`, `he/q0.java`, `he/l0.java` |

These observations justify a standalone GATT subscriber and synthetic parser tests.
They do **not** prove physical speed accuracy/sign/reference on this RVR315 firmware.
The initial app calls it "radar speed" and makes numeric speech optional. No vehicle
ground-speed estimate is implemented. Need a controlled RVR315 capture to promote
that to "closing speed" with confidence.

Our parser intentionally rejects malformed lengths rather than silently ignoring
trailing bytes. It follows Garmin's bit-0 error dispatch for even measurement
headers and requires an exact `1 + 3N` shape. The public six-byte diagnostic shape
is classified unsupported, not allowed to refresh health. An initial implementation
restricted the low nibble to 2; that was removed during review because the APK does
not impose it and it could reject fragments on the RVR315. Heartbeats maintain
stream health but do not immediately delete all tracks.

## Background findings

Decoded manifest declares `MonitorDevicesService` with `connectedDevice` foreground
service type. Its `onCreate` and `onStartCommand` promote the service to foreground;
`onStartCommand` returns 1 (`START_STICKY`). Promotion catches/logs exceptions.
It also declares background location and boot permissions, with several receivers
from dependencies. Those permissions do not prove automatic radar recovery on boot.
The observed user problem is therefore not explained by simply "Garmin has no
foreground service". Runtime OS/OEM/app evidence is still needed.

## External cross-checks (not copied code)

- `partymola/bike-radar-docs` commit `5dfe756abd5f118f2963ab6d77f9014da3077e29`:
  README/PROTOCOL notes focus on 820; protocol notes GPL-3.0-or-later; selected
  decoder files separately Apache-2.0. Its proposed 7-bit V1 target ID and flag
  interpretation differ from this app's legacy handler. Not adopted.
- `Wunderfitz/harbour-tacho` commit `cc7e23ab7c97a11d32acbf7688ed5233060d7dba`:
  GPLv3 project; `src/variaconnectivity.cpp` treats full byte as ID and raw third
  byte as speed. Not adopted. No source from either repository copied into app.

## Build direction

Native Android app with a JVM-testable protocol core, foreground BLE service,
serialized GATT setup, bounded reconnection, fresh/stale/error states, deduplicated
TTS, explicit motion-armed mode, and opt-in companion association for presence wake.
Group prototype uses a phone as radar owner and an explicit local-network join;
no unsupported assumption of simultaneous BLE central connections.
