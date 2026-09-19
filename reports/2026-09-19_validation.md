# Prototype validation — 2026-09-19

## Verified locally

- JDK 17 / Gradle 8.11.1 / AGP 8.9.2; SDK 35 build produces a debug APK.
- JVM tests: decoder packed fields, scaling and range filtering; even-header
  fragments, malformed/diagnostic/error packets; heartbeat/track expiry and stale
  stream; speech deduplication/escalation/loss; motion duration, accuracy, stale
  samples, gaps and spikes; AES-GCM round trip/tamper/wrong-key/expiry/skew.
- Actual loopback UDP test: two separate peers receive the host snapshot using
  the app's transport implementation. This is not a two-phone radio test.
- Android lint passes with warnings about English strings and versioned attributes.
  No baseline suppressing errors. A guarded pre-33 receiver uses a signature
  permission; its lint false positive has a narrow method annotation.
- Created an isolated `ridewatch-api30` ARM64 emulator from an existing system
  image; did not reuse or change the user's other AVD.
- APK install, cold activity launch, UI inspection, synthetic approach display,
  and stale-state display succeeded. No AndroidRuntime crash was observed.
- Started monitoring from the screen, backgrounded the app, and observed
  `isForeground=true`, `startRequested=true`, `stopIfKilled=false` in dumpsys.
- Motion mode registers a GPS request at 2-second intervals and remains active
  while backgrounded. Explicit Stop removes the running service.

Local (ignored) evidence: `artifacts/research/app-build.log`,
`service-background.txt`, `motion-background.txt`, `home.png`, `demo-start.png`,
`demo-stale.png`, and `motion-armed.png`. Standard XML test reports are under
`core/build/test-results/test/` and `app/build/test-results/testDebugUnitTest/`.

## Not verified

Physical BLE discovery/subscription, RVR315 numeric measurements, actual motion
trigger from phone GPS, spoken audio through headphones, real phone-to-phone
networking, companion presence wake, OEM overnight survival, Android 14/15 runtime
service restrictions, and real sensor firmware behavior. No physical phone/radar
was available. API-30 emulator does not validate API-31+ companion integration.

## Issues caught and fixed

- App parser evidence contradicts public V1 assumptions: use 6-bit IDs, packed
  threat bits, and 0.25 m/s speed units; leave physical speed interpretation open.
- Low-nibble-2-only filtering removed: not justified by Garmin's bit-0 dispatch.
- Sparse motion samples no longer qualify as sustained movement.
- Companion service disabled below API 31; modern association callback used on 33+.
- Signature permission protects app state broadcasts on pre-33 Android.
- Simulation scrolls to its results and cannot override enabled live monitoring.
- Group test waits for each peer separately, not two packets on one peer.
- Companion callbacks do not restart an already-active ride or a joined-peer mode.

## Final build

13 tests pass (12 core, 1 real-socket app test). Build and lint pass. The final
APK installs and cold-launches on the isolated emulator without AndroidRuntime
errors. SHA-256: `ac95cbc9e47e42d76147700327894b13eb5995e5cc06adb2439bfa2ee0678147`.
The checksum is specific to this debug build/signing key, not a reproducible-build
claim. Gradle distribution checksum is pinned in wrapper properties.
