# Initial reconnaissance — 2026-09-19

## Scope and evidence boundary

Target: user's Garmin Varia RVR315. This session establishes the workspace and
research leads. **No Garmin APK has been analyzed and no RVR315 packets captured.**
The user reports background termination and forgetting to reopen the app; the
cause has not been diagnosed on their phone.

## Official RVR315 behavior

Garmin documents Bluetooth connection to the phone, approaching-vehicle threat
display, vibration options, and radar battery information. It describes automatic
connection after initial pairing when the app is opened and the radar is active
and within range. That does not establish recovery after process death.

Source: [Garmin RVR315 manual](https://www8.garmin.com/manuals/webhelp/GUID-8A0398B5-1411-401F-9A3B-6F9B956A122A/EN-US/Varia_RVR315_OM_EN-US.pdf),
accessed 2026-09-19.

## BLE leads, not an RVR315 specification

[partymola/bike-radar-docs](https://github.com/partymola/bike-radar-docs) describes
the `6a4e3200` service family, V1 characteristic `6a4e3203`, and V2 `6a4e3204`.
Its stated hardware validation is RearVue 820; other models remain untested there.
It also disputes interpreting a V1 field as approach speed on its 820 captures.
That disagreement makes the actual RVR315 speed field a priority for verification.
The repository has mixed licenses; check file-level terms before reuse.

[rale/radarble](https://github.com/rale/radarble) also targets the 820 and describes
a newer per-target data layout. Do not assume its pairing requirements, lateral
measurements, object dimensions, or V2 stream exist on the RVR315.

These README-level sources were read on 2026-09-19. No code imported, repository
revision pinned, or byte layout adopted. Pin commits before substantive comparison.

## Background reliability

Android's [background BLE guidance](https://developer.android.com/develop/connectivity/bluetooth/ble/background)
states that process death closes a connection. `autoConnect=true` supports
reconnection while the relevant client exists; it is not a process resurrection
mechanism. Documented options include filtered PendingIntent scans, companion
device presence, and a `connectedDevice` foreground service for sustained work.

Android also applies [foreground-service start restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start).
A proposed architecture must identify its permitted start path and satisfy the
applicable permissions. Merely declaring a foreground service is insufficient.
These are platform constraints, not findings about Garmin's implementation.

## Feature implications / proposed experiments

| User idea | Initial direction | Unresolved evidence |
| --- | --- | --- |
| Spoken approach speed | Announce fresh threats, then validated closing speed; coalesce updates and expire queued speech | Does RVR315 transmit numeric speed? Units, sign, saturation, target IDs? |
| Persistent connection | Separate ride connection from idle presence detection; explicit reconnect state and notification | Garmin lifecycle, phone/OS policy, viable wake path and battery impact |
| Activate above 4 mph | Threshold is 1.78816 m/s; evaluate motion hint then qualified speed samples, hysteresis and dwell | Location/activity permissions, wake restrictions, false triggers and energy cost |
| Joined phones | Prototype one radar-connected phone relaying timestamped events to joined peers | RVR315 BLE connection limit, peer transport, latency and dropout behavior |

These are proposals, not implementation commitments. Speed activation must not
require an already-running high-rate GPS loop without measuring its cost. Radar
presence may be a better first wake signal; motion can gate an active ride.

For TTS, closing speed and ground speed are distinct. Estimating vehicle ground
speed from rider speed requires compatible direction, timing, and geometry; do
not silently sum arbitrary values. Do not invent numeric speed from threat color.

For groups, transmit source identity, ride/session ID, sequence, freshness/expiry,
connection health, and validated target measurements. Avoid duplicate announcements
on retries. A rear rider's radar threat is relative to that rider, not every peer.
Do not assume target IDs match between different radars. On lost relay or sensor
data, report stale/disconnected state instead of interpreting silence as clear.

## Local observations

- Workspace was empty and not a Git repository; initialized locally this session.
- Read Reolink README/current state and workflow excerpts as organizational examples.
- `adb devices -l`: no devices attached.
- `uv` available; ADB at the Android SDK path; JADX/apktool absent from PATH.
- Bounded APK/Varia-name search in Downloads returned no matching files.
- [APKMirror Varia listing](https://www.apkmirror.com/apk/garmin/garmin-varia/)
  is an acquisition lead only. No listed binary downloaded or verified.

Next: acquire an APK and record provenance, then map the manifest, GATT parser,
and background service before attributing any behavior to Garmin code.
