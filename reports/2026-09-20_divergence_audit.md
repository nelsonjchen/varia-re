# RVR315 interoperability divergence audit — v0.10.5

## Scope and evidence

Focused comparison of connection setup, legacy parser/tracking, errors, battery,
audio and lifecycle. Static Garmin Varia 5.1.3/157 evidence; APK hash, certificate,
origin and JADX 1.5.6 provenance in 2026-09-19_apk_protocol.md. Source paths below
are relative to ignored artifacts/decompiled/varia/sources. This is not a complete
app equivalence claim or a hardware notification-timing capture. No firmware,
pairing, or radar settings changed. Decompiled code/assets are not copied.

## Battery change delivered

Initial Battery Level read followed by enabling notifications, matching the
strategy in fe/y0.f → he/k0 + he/j0 (fallback-bytecode confirmation in official
battery report). BLE owner serializes the battery read completion before its CCCD
write; distinguishes radar/battery descriptor callbacks. Battery notifications
update both display and retained disconnect battery. They never refresh radar
stream health. Pause/retry discards subscription state and timers; old clients
cannot update the new session.

Only when notify property/CCCD is missing, write submission fails, or the device
rejects the subscription do we use the one-minute polling fallback. Successful
subscription means no periodic polling. This fallback is our resilience choice,
not recovered Garmin behavior. Unsupported/failed battery setup does not terminate
a healthy radar connection. Accepted operations without callbacks wait for callback
or connection reset rather than submit overlapping operations.

## Findings

| Area | Official evidence | RideWatch / implication |
| --- | --- | --- |
| Initial setup order | SIMPLE path fe/x0.a → fe/y0.e: target-indication read, serial read, battery read, battery notification, model read, radar notification, firmware read | Radar subscription first, then battery read/subscription. Intentional radar-first ordering; battery failure cannot prevent monitoring. |
| Target-indication setting | he/s0 reads legacy service characteristic 3205; je/a1 handles enabled/disabled/invalid | Not read or surfaced. Material diagnostic gap: a radar-side disabled setting cannot currently be distinguished using that setting. Next priority: read/display, without automatically changing it. |
| Device identity | he/n0, he/m0, he/l0 read serial/model/firmware (180a: 2a25/2a24/2a26) | Not read. UI's RVR315 label is assumed, not device-verified. Firmware missing from runtime diagnosis/captures. Add model/firmware; avoid storing raw serial unnecessarily. |
| Legacy target decoding | je/h0: ID mask63, threat2/3 mapping, range1..154, speed byte×0.25 | Matches LegacyDecoder. Do not change units/reference based on this audit; absolute car speed remains our estimate. |
| Target expiry | je/e1: 500ms below5m, otherwise2000ms; watcher repeats at125ms | Same thresholds in RadarTracker, but service evaluates every500ms. Display/alert expiry can be coarser. We also filter zero range, matching legacy je/h0; generic je/p0 gone predicate alone is not proof legacy zero-range removal occurs. |
| Missing data | je/j0 raises data-flow error2989 after2000ms | STALE after2000ms matches basic threshold. Separate transport watchdog reconnects after >15s, checked every5s; no proof that this recovery cadence matches Garmin. |
| Error recovery | je/g0.a clears prior error and returns on first non-error frame | RideWatch immediately processes first valid measurement and clears error. Could show recovery one frame earlier. Garmin downstream system-state recovery/reconnect timing not fully traced. |
| Malformed frames | je/i0 counts targets by integer division; je/g0 requires two-byte errors | We reject trailing partial targets, frames >244 bytes; six-byte diagnostics unsupported. Deliberate stricter validation; existing synthetic tests cover this. |
| Error presentation | Garmin dispatches typed system errors via je/g0/jd state machinery | We show generic ERROR/code and use our own recovery, so troubleshooting is less specific. No unsupported error meanings inferred. |
| Audio routing/queue | ce/i: usageMEDIA, five SoundPool streams; ce/k and yf/h: optional transient ducking; yf/r timing windows, priority pending alert | Navigation usage (still media volume), one stream, own chime/TTS sequencing and priority disconnect. These are not exact Garmin alert timing; generated WAVs intentionally differ. User's minimum media volume and spoken speed/battery are custom behavior. |
| Lifecycle | MonitorDevicesService foreground connectedDevice, START_STICKY; see lifecycle report | Foreground/START_STICKY retained, plus user-requested Pause/reopen semantics. Motion/presence/boot triggers remain disabled for testing; no claim of exact Garmin lifecycle parity. |
| Device families | fe/o1 and fe/x0 classify SIMPLE/CAMERA/E_RTL/V2/FRONT service sets | RVR315 legacy only. V2/charging/camera/light/group-device behavior is outside current target, not silently inherited. |

## What to address next

1. Read target indication and model/firmware as serialized, optional diagnostics.
2. Physically verify battery CCCD acceptance and unsolicited update(s), recording
   sample times and actual radar firmware. Notification intervals may depend on
   firmware/value changes; an unchanged percentage is not itself a stale stream.
3. Compare error recovery, reconnection and target-expiry timing with captures.
   The 30-second timers in fe/d1 are in bonding branches, not evidence for a
   general reconnect interval. Avoid copying unrelated camera/Wi-Fi timers.

## Validation and limits

JDK17 canonical Gradle suite/build/lint passes (30 existing tests; core unchanged).
Manually reviewed battery paths: read accepted/rejected/exception, invalid read,
notify capability missing, CCCD success/failure, notification dispatch, stop/retry
and stale client rejection. No new synthetic BLE integration test added.
Installed v0.10.5/versionCode21 on the explicitly selected paired Pixel 10 Pro and
verified package version. No battery callback in current-process filtered log
during check, so live subscription acceptance and notification cadence remain
unverified. Earlier physical battery reads do not validate this new subscription.
