# Pixel 10 / Android 16 baseline

User confirmed a Pixel 10 and authorized it as the minimum supported baseline.
RideWatch v0.2 (versionCode 2) now uses API 36 for min/target/compile SDK. This
requires Android 16 or newer; it does not restrict installation to a Pixel model.

## Changes

- Upgraded AGP to 8.10.1, which supports API 36 with the existing Gradle 8.11.1
  wrapper and Java 17. Installed SDK platform 36 locally.
- Removed old permission declarations, OS-version branches, legacy GATT callback
  overloads, deprecated mutable descriptor writes, conditional companion-service
  resources, and Java library backport dependency.
- Uses value-bearing GATT callbacks and `writeDescriptor(descriptor, value)`.
- Uses association-ID `ObservingDevicePresenceRequest` and
  `onDevicePresenceEvent`. Only appearance/connection for the saved association
  may wake enabled monitoring; disappearance, unrelated associations, active
  services and joined-peer mode do not trigger a restart.
- Pairing requests both Bluetooth connect and scan permissions before registering
  presence observation. A partial denial does not loop permission prompts.
- Uses non-exported receiver registration directly; removed pre-33 signature
  permission and its lint suppression. Uses modern system-bar/cutout insets.
- Existing v0.1 installs should pair again to register the new observation request.

## Validation

Canonical Gradle checks pass: 13 existing tests, debug APK assembly, and lint
(with existing English-string warnings). APK metadata confirms minSdk 36,
targetSdk 36, version 0.2.0/2. No API-version compatibility branches remain in app
sources. `git diff --check` passes.

APK SHA-256: `5de43a781807789859e1f8cc70907f97e62bca9af7241e125473cffc65b1d926`.
Build log: `artifacts/research/pixel10-build.log` (ignored).

No Pixel 10 or API-36 emulator runtime test was performed. The earlier API-30
emulator validation is historical and cannot be reused as v0.2 validation.
These changes simplify the lifecycle and use current APIs; they do not establish
measured overnight reliability or remove force-stop/background-location limits.

## Primary references (read 2026-09-19)

- [Pixel 10 specifications](https://store.google.com/mx/product/pixel_10_specs?hl=es-419): launches with Android 16.
- [Android companion-device pairing](https://developer.android.com/develop/connectivity/bluetooth/companion-device-pairing): association and presence integration.
- [DevicePresenceEvent](https://developer.android.com/reference/android/companion/DevicePresenceEvent): API-36 event types and association identity.
- [CompanionDeviceManager](https://developer.android.com/reference/android/companion/CompanionDeviceManager): presence observation API.
- [AGP 8.10 compatibility](https://developer.android.com/build/releases/agp-8-10-0-release-notes): API 36, Gradle and Java requirements.
