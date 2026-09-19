# Current state

Updated: 2026-09-19, America/Los_Angeles.

## Completed

- Initialized Git and research layout with required session journaling.
- Established RVR315 scope and feature-oriented research queue.
- Read Garmin RVR315 documentation, public BLE research, and Android background
  Bluetooth guidance; [initial report](reports/2026-09-19_initial_recon.md).
- Located Android platform tools at
  `/Users/nelson/Library/Android/sdk/platform-tools/adb`.

## Evidence limits / blockers

- No APK is present in this workspace; bounded Downloads search found none.
- `adb devices -l` returned no attached devices.
- No Garmin app decompilation, RVR315 packet capture, or connection test yet.
- `jadx` and `apktool` were not found on PATH. `uv` is available.
- Phone model, Android version, installed Varia version, and radar firmware unknown.

## Next step

Acquire a Varia APK, preferably all installed splits from the user's Android phone
using [the acquisition workflow](docs/acquisition.md). Hash and identify it before
analysis. Then inspect the manifest and follow radar UUID references into the GATT
subscription/parser and service lifecycle. Public mirror listing discovered but
no binary downloaded or provenance verified. Public 820 findings remain leads only.
