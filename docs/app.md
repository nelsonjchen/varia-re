# RideWatch prototype

Android 16+ (minSdk/targetSdk/compileSdk 36), using the user’s Pixel 10 as baseline. An initial independently written app, based on
static Varia 5.1.3 analysis. Physical connection, battery read and a live empty stream were observed with
the user’s radar on Pixel 10 Pro/Android 17 in v0.6.1. Vehicle range/speed and
background reliability remain unverified.

## Current testing behavior — v0.6

This section overrides the earlier prototype lifecycle/motion descriptions below.
RideWatch uses its own foreground service and stays running when backgrounded or
when the screen is off. Pair once; opening the app connects unless paused.
**Pause** on the screen or notification stops monitoring, audio, reconnects and
BLE/group connections. **Resume** restarts the last ride/host/join mode. Pausing
persists when the app is reopened. Use Pause when switching to Garmin for testing.
Installing RideWatch does not replace Garmin's separate app.

Motion arming, companion wake and boot reminders are temporarily disabled. Normal
in-session BLE retries and START_STICKY remain. OS force-stop still stops the app.
This is not a promise of simultaneous RVR315 Bluetooth access by both apps.

## Build and install

Requires JDK 17 and Android SDK platform 36. Gradle 8.11.1 is pinned by wrapper and
SHA-256; Android Gradle Plugin 8.10.1 is pinned. On this Mac:

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

Set `sdk.dir` in ignored `local.properties` if the SDK is elsewhere. The wrapper
fetches dependencies on first run. No Garmin binaries are required to build.

Output: `app/build/outputs/apk/debug/app-debug.apk`. This is debug-signed for local
sideloading, not a Play Store/release build. Install with Android's package installer
or `adb -s <device> install -r app/build/outputs/apk/debug/app-debug.apk`.

## Graphical radar screen (v0.3)

Cars move up a distance lane toward the fixed rider icon as reported range falls.
The banner/edge rails turn amber for detections and red for fast approaches.
A fresh stream with no targets is green; disconnected/stale/error states are gray
and clear the vehicle icons. Horizontal placement does not encode traffic lanes.
The nearest distance and total target count remain visible; overlapping detections
can share a marker position, so the count is authoritative. Group distances refer
to the host rider, labeled HOST. Portrait and landscape have separate layouts.

Use **Start ride / Stop ride** on the main screen. **Controls** contains pairing,
motion arming, spoken-alert switches, group hosting/joining and **Preview radar
display**. The preview is explicitly marked SIMULATION and ends with data loss.
Live radar and preview keep the screen awake while the Activity is visible.
Speech settings still persist and numeric radar-speed speech remains experimental.

## Modes

- **Pair radar / enable nearby reconnect:** Android companion-device chooser,
  filtered by radar service UUID. Saves the selected address and requests Android 16 association-ID device-presence observation. This is app association, not a firmware
  modification or a promise of an encrypted Bluetooth bond.
- **Start ride:** foreground BLE service; saved-address connection or filtered
  discovery, subscription, standard battery read, retries up to 30 seconds apart.
  A 15-second missing-notification watchdog reconnects. The UI already marks data
  stale at 2 seconds. Battery is a one-time connection reading.
- **Arm motion:** foreground GPS monitoring, approximately 2-second samples.
  Require `speed - speedAccuracy > 1.78816 m/s` for at least five seconds, speed
  accuracy <=1 m/s, recent samples, and gaps <=4 seconds. Once triggered, stop GPS
  and connect radar. This initial implementation prioritizes explicit behavior;
  it is not a low-power activity-recognition implementation.
- **Spoken alerts:** announce new threats/escalation; coalesce targets, throttle
  repeats, flush obsolete speech, and announce loss of a previously live stream.
  Numeric "radar speed" speech is optional and off by default. No ground-speed
  estimate or claim that this is validated closing speed.
- **Host/join:** local Wi-Fi/hotspot group, one radar owner and up to eight peers.
  Host creates a random join code; peers enter host IP and code. UDP port 43157,
  AES-GCM authenticated encryption, bounded packet sizes, session/sequence replay
  rejection, three-second wire expiry and receive timeout. Keep clocks automatic.
  Router client isolation/firewall rules can prevent delivery. No cellular relay,
  account service, group GPS tracking, or internet deployment is included.
- **Preview:** labeled synthetic approach and loss-of-data sequence. Refuses to
  replace active monitoring. It is not a real sensor feed.

## Lifecycle behavior and limits

`START_STICKY` requests service restart; it cannot defeat force-stop or all OEM
battery policies. Device-presence wake is implemented through a companion service
and only allowed after the user has started/enabled monitoring. Stop disables it.
Presence wake can reconnect when a previously motion-armed app has died, without
starting a location service from the background; that is deliberately a radar
presence trigger, not proof of motion.

After reboot the app posts a resume reminder if monitoring was enabled. It does
not silently start GPS at boot. Denied notification permission means no visible
reminder. Android 16 companion presence and foreground restrictions need tests on the Pixel
10. The earlier API-30 emulator checks cover v0.1 only, not current builds. v0.3 has Android-16 emulator UI checks, not physical BLE checks.
If upgrading from v0.1, use Pair radar again to register association-ID observation.

The app distinguishes disconnected, awaiting data, live, stale, and radar error.
"No targets detected" means a fresh stream with no currently tracked targets,
not a guarantee of an empty road. Group targets are relative to the host's radar;
tracks from multiple radars are not merged. Shared-code holders can send group
messages; this is a cooperative small-group prototype, not per-user authorization.

## Code map

- `core/`: independent decoder, tracker, speech policy, motion gate, encrypted
  group packet codec, synthetic tests.
- `app/`: native Android screen, BLE adapter, foreground service, companion/boot
  integration, TTS, LAN transport, and real-socket group unit test.
- `reports/2026-09-19_apk_protocol.md`: input provenance and obfuscated source map.

No proprietary source, assets, APK, or external GPL implementation is distributed
with the app. The inspected binaries/reference checkouts stay in ignored folders.

## Next physical validation

1. Record phone model/OS, RVR315 firmware, and Garmin version. Close Garmin for the
   initial one-client test; do not reset the radar.
2. Select the actual RVR315, verify discovery/subscription and battery, then record
   labeled notifications. Confirm error frames and numeric speed semantics.
3. Test screen off, background, range loss/return, radar power cycle, Bluetooth
   toggling, process death, task dismissal, reboot and explicit force-stop separately.
4. Test real TTS delivery with music/headphones and multiple approaching targets.
5. Measure armed overnight battery use; replace continuous GPS with a qualified
   activity/presence wake strategy if it is too costly.
6. Test two phones on a hotspot, clocks, packet loss, host stop, wrong join code,
   background networking, and peer distance. Confirm stale state appears promptly.

## Sound (v0.4)

Controls → Sound & speech offers independent **Varia-style tones**, **Spoken
vehicle alerts**, and **Speak radar speed · experimental** switches. **Test tones**
plays each generated sound while monitoring is stopped. These are synthesized
approximations; no original Garmin audio is included. Tones default on and the
existing speech preference is retained. Tones precede speech; newer events cancel
old pending audio. Volume/output follow Android media/navigation routing, with
temporary music ducking requested. Calls, focus denial, mute and unavailable TTS
can prevent audio. Real Pixel/headphone routing and ducking remain untested.

Connection, normal approach, fast approach, a fresh stream becoming empty, and
data loss have distinct sounds. A stale/disconnected stream never plays the
no-target tone. See [audio research](../reports/2026-09-19_audio.md).


v0.5 masters tones for audibility: stronger sustain, soft saturation and peak
normalization, with full per-stream playback gain. This deliberately trades a
clean chime timbre for higher average signal level. It does not change the
phone's volume setting or compress TTS. Physical speaker audibility remains to
be tested; digital levels alone cannot establish audibility in traffic.
