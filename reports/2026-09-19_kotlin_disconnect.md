# Kotlin migration and disconnect audio — RideWatch v0.8.0 / 12

## Request and diagnosis

The user reported a missing disconnect sound, requested the last-known battery
spoken on disconnect, and authorized converting the app to Kotlin while away.
No contemporaneous phone audio log was captured, so the exact cause of that
reported silence is not established. Code inspection identified concrete gaps:

- Old lost-tone/speech policies required a prior LIVE stream; dropping the BLE
  link before its first measurement did not announce a loss.
- Stale data could consume the only lost tone before the eventual BLE disconnect.
- Service cleared the battery in its disconnect callback.
- A later play request unconditionally stopped current audio; a rapid reconnect
  could interrupt a disconnect chime/speech sequence.
- Unloaded SoundPool samples and unready TTS could silently drop a one-shot event.

## Changes

All 19 production Java files are replaced by Kotlin under app/core
`src/main/kotlin`. A new core DisconnectPolicy independently tracks connection
and last valid battery. Kotlin 2.2.21 is pinned, retaining AGP8.10.1, Gradle8.11.1,
JDK/JVM17, SDK36 and the package/signing identity. Native Views/Canvas, BLE ownership,
foreground lifecycle, encrypted group format, permissions, preferences, speed
fusion and original synthesized WAV resources remain. No Compose/coroutine or
build-DSL rewrite. The 22 preexisting Java tests remain unchanged regression
oracles for the migrated implementation; four new Kotlin tests cover loss.
Core records retain Java-compatible record APIs through Kotlin @JvmRecord.

BLE connected is now reported at GATT transport connection, before discovery,
so discovery/subscription failure still reports a genuine connection loss.
Unexpected loss directly triggers LOST chime/vibration and “Disconnected. Last
battery 84 percent” using the last valid battery in this ride session. Unknown
battery produces “Radar disconnected”; zero is valid. Battery survives retry
attempts and is cleared on a new session/mode to avoid leaking another radar's
reading. Last-known battery stays visible when disconnected. Duplicate failed
attempts don't repeat the announcement; a subsequent genuine connection can.
Intentional Pause/service teardown remains silent, stops audio, and releases BLE.

Disconnect audio has priority over routine tick/reconnect events. It waits up to
two seconds for samples/focus and up to three seconds for TTS initialization;
voice playback has the existing ten-second hard timeout. Pause, audio focus loss,
and explicit shutdown can still cancel it. Existing tone/speech/vibration switches
are respected. The sample “Disconnect · sample battery 84%” test uses the same
policy/audio priority path without pretending to be a hardware event.

## Validation

Clean JDK17 Gradle build: `:core:clean :app:clean :core:test
:app:testDebugUnitTest :app:assembleDebug :app:lintDebug`. 25 core tests + one UDP
integration test pass. Lint has no errors; existing localization/resource/version
advisories remain. Suppressed the Kotlin lint ImplicitSamInstance false positive
on stopService(Intent): Android resolves a service component, not Intent identity.
New tests check loss before measurements, battery retention/update/unknown/0%,
repeated failed reconnects, stale-then-disconnect and silent intentional resets.

API36 task emulator `ridewatch-api36` / emulator-5560, synthetic validation:

- Installed Kotlin APK over previous app data. Paused preference survived upgrade.
- Inspected rendered Test alerts dialog with the new sample battery action.
- Triggered sample disconnect, immediately tapped Connected. Log shows one LOST
  priority event, granted focus, nonzero SoundPool stream, speech start and
  completion; no interrupting connected event. A subsequent Connected tap plays
  normally. Emulator ran with `-no-audio`; this proves playback/TTS callbacks, not
  physical speaker audibility or volume.
- Denied precise-location prompt: radar still starts as connectedDevice FGS
  (type0x10) and scans. No actual radar was attached to the emulator.
- Home leaves the foreground service active. Pause and reopening were checked
  separately; see local service/UI capture files.

Physical Pixel wireless ADB was unavailable during this session (user left).
The v0.8 APK is built but **not installed on the Pixel**. Actual radar power-off,
Bluetooth loss and speaker/headphone audibility with the new behavior need a
physical check when it reconnects. Do not claim emulator tests validate RF loss.

## Evidence and tooling

Ignored local artifacts/research: kotlin-core-build.log, kotlin-build.log,
kotlin-final-build.log, kotlin-clean-build.log, kotlin-format.log,
kotlin-audio-log.txt, kotlin-home.xml, kotlin-test-alerts.xml/png,
kotlin-location-denied.xml, kotlin-resume-service.txt,
kotlin-background-service.txt, kotlin-before-pause.xml, kotlin-paused-service.txt,
kotlin-reopened.xml. APK: app/build/outputs/apk/debug/app-debug.apk.

A first compiler pass caught nullable ByteArray handling in the decoder; explicit
null/nonempty require fixes the smart cast and retains malformed-input behavior.
ktfmt download attempts used a nonexistent jar-with-dependencies classifier;
Maven directory inspection resolved the correct with-dependencies jar. Formatted
with ktfmt0.64 (tool kept ignored). A couple of UI taps immediately after launch or
dialog dismissal preceded the new screen being ready; fresh UI dumps were read
before retrying. No outcome was inferred from those missed taps.

Build compatibility references: [Kotlin Gradle configuration](https://kotlinlang.org/docs/gradle-configure-project.html)
and [Android Kotlin support](https://developer.android.com/build/kotlin-support).
