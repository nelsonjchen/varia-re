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
  Numeric speech is optional and off by default on fresh installs. v0.7.0 repeats
  an estimated car road speed every two seconds; see the rider-speed section below.
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
vehicle alerts**, and **Repeat estimated car speed** switches. **Test tones**
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


## Vibration and individual tests (v0.6.2)

Vibration defaults on independently of tones/speech. Approach: two 180ms pulses
separated by 120ms. Urgent: three 300ms pulses separated by 100ms. Lost data: one
650ms pulse. Connected and fresh-empty events do not vibrate. Patterns use the
same deduplicated state events as tones; Pause/mode changes cancel vibration.
Android notification-vibration/DND settings are respected.

Controls → Sound & vibration has a Vibration alerts toggle. Controls → Test
alerts (also available from the sound settings) opens a persistent panel with a
separate replay button for each event. Monitoring must be paused. Tests play the
selected tone and, if enabled, its vibration pattern; closing/backgrounding the
panel cancels playback. These are synthetic tests, not simulated radar detection.


v0.6.3 speed speech is only “X mph”, at 1.4× TTS rate, with no vehicle/group
preface. Tone still precedes speech when tones are enabled. Experimental speed
interpretation is unchanged; the settings label retains that qualification.
Speed speech was enabled on the user's Pixel on request; default remains off
for other fresh installs. Non-speed speech is also read at the faster rate.


## Rider speed and repeating numbers (v0.7.0)

Speed speech now says only the rounded number (still mph, at 1.4× speech rate).
While targets remain LIVE, it repeats every two seconds using the latest reading.
With multiple targets it selects highest threat, then nearest distance, then ID;
it does not combine different vehicles or queue a list of speeds. Empty/stale
radar stops numeric repetition. Radar-loss warnings remain verbal.

Estimated road speed = radar speed + rider speed. Google Play services Fused
Location Provider supplies rider speed at a requested one-second high-accuracy
interval. Precise location permission is requested on local ride/host start;
denial leaves BLE/tones working. Accept only finite nonnegative speed with
reported speed accuracy <=1.5 m/s and monotonic age <=3 seconds. Missing/poor
readings contribute zero rider speed (user preference), so radar-only numbers
continue. The UI explicitly reports assumed zero or the measured rider speed.
This fallback underestimates road speed when the rider is actually moving. Google does not guarantee sensor mix or speed
accuracy; this estimate assumes radar speed is relative closing speed for a
same-direction vehicle behind. That reference and road accuracy remain unverified.

Location runs in the existing foreground ride service only while speed speech is
enabled. Pause/destruction removes updates and clears cached speed; background
monitoring keeps updates. No coordinates are retained or logged by RideWatch,
no background-location permission, no new wake/motion behavior. Resume after a
permission change to enable the location foreground service. Group peers do not
speak estimates because the current wire protocol lacks the radar host's rider
speed; adding the receiving phone's speed would be incorrect. Group tones remain.
Controls → Test alerts includes a numeric “35” preview.

See [implementation evidence and validation](../reports/2026-09-19_speed_fusion.md).


## Kotlin and disconnect announcements (v0.8.0)

All production app/core code now lives in `src/main/kotlin`, using Kotlin 2.2.21
with the existing JVM17/SDK36 baseline. The programmatic graphical UI, BLE packet
format, encrypted group protocol, preferences, Pause lifecycle, and speed speech
remain compatible. Existing Java tests remain regression oracles; new tests use
Kotlin. No Compose/coroutine or build-tool migration was bundled into this change.

Unexpected Bluetooth loss now triggers the lost chime directly, even before the
first measurement, independently of an earlier stale-stream warning. It says
“Disconnected. Last battery 84 percent” using the last valid reading in that ride
session. Unknown battery says only “Radar disconnected”; 0% is a valid battery.
Retries without an intervening connection stay quiet. The last known percentage
remains on screen. A new session clears it so a different radar cannot inherit it.
Tones/speech/vibration respect their existing switches. Pause/mode switches are
intentional shutdowns and remain silent.

Disconnect audio has priority over routine reconnect/numeric sounds until its
speech finishes. Sound loading and TTS initialization get bounded waits; temporary
focus denial gets a bounded retry for this event. Android audio-focus loss and
Pause still cancel playback. Controls → Test alerts includes a clearly labeled
sample disconnect + 84% battery preview.

[Migration evidence and remaining device check](../reports/2026-09-19_kotlin_disconnect.md).


## Gentler tones and quicker speed speech (v0.8.1)

All five chimes use gentler saturation (drive1 instead of4) and less added harmonic
(.08 instead of.18). Peak headroom/cadence stay the same; average level is lower.
Numeric speed now refreshes every **one second**. If a number is still speaking
(or queued behind a chime), a redundant numeric refresh is skipped rather than
cutting off speech or accumulating a queue. Loss/escalation alerts can still
interrupt; the priority disconnect/battery sequence remains protected.

Rechecked Garmin5.1.3 `je/h0.java` target decoder: unsigned byte2 ×0.25, passed
to `je/p0.java`, whose string explicitly labels it `speedMetersPerSecond`. Our
conversion divides m/s by .44704 for mph. Raw80 =20m/s ≈45mph before adding rider
speed. This confirms scaling/units in static evidence, not physical accuracy.
[Garmin's explanation](https://support.garmin.com/en-CA/?faq=0WK0iSND1W8GonJSGgJjU5)
also describes fast approach relative to the bike; this supports but does not
physically validate our interpretation of the RVR315 BLE field.


## mindflakes.com namespace (v0.8.2)

Android namespace/application ID: `com.mindflakes.ridewatch`. App classes and
tests use this package; core classes/tests use `com.mindflakes.ridewatch.core`.
The private update-broadcast action follows the new package too.

Android sees this as a new app. At the user's request the old package was
uninstalled from the Pixel and a fresh v0.8.2/14 installed; no settings or
companion association were migrated. Open Controls → Pair radar for setup.
Historical reports/journal references retain the package used at their time.
