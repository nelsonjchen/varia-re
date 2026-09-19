# Repeating estimated car speed — v0.7.0 / 11

## Implemented behavior

Numeric TTS is just a number in mph, at the existing 1.4× rate. Every two seconds
with LIVE targets, choose highest threat, nearest distance, then lowest ID. Use
that target's latest radar speed plus the latest usable rider speed; round only
after adding in m/s and converting to mph. Targets are not averaged together.
Empty/stale snapshots stop repetition; a lost-radar warning remains verbal.
Tone/vibration policies remain event-based, rather than repeating every number.
The audio player flushes obsolete speech and retains transient ducking.

Rider speed comes from Google Play services Fused Location Provider 21.3.0,
high-accuracy request every second, no batching or initial cached fix. Accept
finite nonnegative speed with reported speed accuracy <=1.5 m/s, age <=3 seconds,
and a nonfuture monotonic timestamp. Ignore older callbacks/readings. Accurate
zero is valid. Missing speed/accuracy, poor accuracy or expired readings use
**zero rider speed**, explicitly requested by the user during implementation.
This means radar-only numbers continue while waiting. The UI distinguishes
measured rider speed from assumed zero; unknown is not classified as stationary.
If the rider is moving during a dropout, this fallback underestimates road speed.

Location collection runs only with speed speech enabled during a local ride or
host session. It uses the location + connectedDevice foreground service types.
Precise/coarse permission is requested together; denial doesn't prevent radar.
A location foreground-start SecurityException falls back to connectedDevice.
Pause, service destruction, or disabling speed/speech removes location updates
and clears the cached speed. Callback identity rejects late prior sessions.
No coordinates are retained or logged by app code. Motion/presence/boot wake
remain disabled in the existing testing profile. No background location grant.

Group wire format is unchanged: it carries radar data, not host rider speed.
Peers suppress numeric estimates and label why; using the receiving rider's
speed would be wrong. Host numeric estimates work locally. Group tones continue.
A numbers-only “35” preview is available in Test alerts.

## Evidence and limits

Official API documentation: Google may combine multiple sensors for location,
but does not guarantee sensor selection, interval or accuracy. This is not a
promise of exact car ground speed. Android speed is m/s; speed accuracy describes
an estimated 68% confidence interval, not a hard error bound.

- [Google LocationRequest](https://developers.google.com/android/reference/com/google/android/gms/location/LocationRequest)
- [FusedLocationProviderClient](https://developers.google.com/android/reference/com/google/android/gms/location/FusedLocationProviderClient)
- [Android Location](https://developer.android.com/reference/android/location/Location)

Design assumption: legacy target speed is relative closing speed of a vehicle
behind traveling in the rider's direction. Static evidence establishes scaling,
not a controlled physical validation of that reference. Addition also ignores
radial angle/turning effects. The user reported an outdoor test of the earlier
app, but supplied no ground-truth speed measurements. No claim of road-speed
accuracy or stationary classification is made from that report.

## Validation

JDK17 canonical Gradle checks pass: 21 core tests + one app UDP test, debug APK,
Android lint. New deterministic tests cover repetition, changing speed, target
selection independent of packet order, neutral targets, empty/stale cessation,
missing/future/expired/out-of-order/low-quality readings, valid stationary zero,
and requested unknown-to-zero fallback. Existing event speech/loss tests pass.

Installed final APK on the explicitly selected physical Pixel 10 Pro / Android
17, preserving app data. Observed precise/coarse permissions granted, radar
connected/live-empty with battery 84%, location FGS types 0x18, and Google Play
services high-accuracy one-second provider registrations attributed to RideWatch.
Initial UI showed waiting for accurate speed. Final UI shows “Rider speed assumed
0 · Waiting for accurate rider speed” and resumed/live radar. This is a real
missing-speed observation, not a physical moving-speed validation.

Pause on the first v0.7.0 installation removed the service and RideWatch provider
requests (checked active registration section, not historical logs). Final APK
adds requested zero fallback; service teardown unchanged. No permission-denial
or moving-vehicle TTS test was performed on the phone. No global location
settings, mock-location source or permission grants were changed through ADB.

Local ignored evidence: artifacts/research/speed-build.log, speed-package.txt,
speed-service-active.txt, speed-location-active.txt, speed-service-paused.txt,
speed-location-paused.txt, speed-permission.xml, speed-paused.xml, and
speed-final-paused.xml (despite filename, final capture is resumed/live).

Dependency attempt: 21.4.0 built, but AGP8.10 lint emitted Kotlin metadata 2.3 vs
2.1 incompatibility diagnostics. Pinned 21.3.0 to retain the existing toolchain;
final build/lint is clean apart from the preexisting audio API deprecation note.
