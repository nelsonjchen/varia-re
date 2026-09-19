# Foreground service and manual pause — v0.6

## Evidence and intended behavior

Official RVR315 manual says an already paired radar reconnects when the Varia app
is opened and the radar is active/in range:
https://www8.garmin.com/manuals/webhelp/GUID-8A0398B5-1411-401F-9A3B-6F9B956A122A/EN-US/Varia_RVR315_OM_EN-US.pdf

Static Varia 5.1.3/157 evidence: decompiled
`com/garmin/android/apps/variamobile/data/location/MonitorDevicesService.java`
`onStartCommand` returns 1 (START_STICKY). Manifest identifies a connected-device
foreground service. Provenance is in the APK protocol report. This does not
establish every physical/OEM lifecycle behavior.

User wants similar service-based operation during side-by-side testing, plus
Pause. RideWatch retains its own connected-device foreground service, notification,
BLE reconnect loop, audio and group handling. It does not use Garmin's service.
Both apps keep separate packages; this is not a claim that simultaneous BLE
clients are supported by the RVR315. Pause RideWatch to release its connection
when switching to Garmin.

## Changes

- Open after pairing: connect using the saved mode unless explicitly paused.
  Resume restores the last ride/host/join mode; old motion sessions become ride.
- Home, another app and screen off: service stays active. START_STICKY retained
  for ordinary process reclamation. Force-stop/reboot guarantees are not implied.
- Pause in the screen or notification: persist paused=true/enabled=false, stop
  foreground service, disconnect/close BLE, cancel scans/retries, close group
  socket, stop location and audio, clear targets, remove notification.
- Paused state survives reopening/process recreation. Only explicit Resume or
  starting another mode clears pause. Queued permission results cannot resume
  after the Activity has paused/cancelled its pending mode.
- Temporary TESTING_LIFECYCLE constant disables companion-triggered monitoring,
  boot reminders and motion arming. Pairing saves association/address without
  enabling presence observation. Old presence callbacks are ignored. Existing
  motion implementation remains for later re-enablement.
- Notification only updates when its displayed text changes, avoiding 2Hz churn.

## Checks

Canonical 16 tests, assembly and lint pass. API36 task emulator-5560:
foreground service with connected-device type observed after Resume, Home and
screen-off. Notification Pause removed service. Reopening after force-stop left
paused state intact and no service. Resume started it again; in-app Pause also
checked. Main paused screen captured. No AndroidRuntime errors.

Evidence: artifacts/research/lifecycle-{running,background,screen-off,paused,
paused-reopen,ui-paused}.txt, lifecycle-paused.png, lifecycle-notification.png,
lifecycle-build.log and lifecycle-errors.log. First automation tap missed the
freshly launched UI; repeated after inspecting bounds. Initial notification XML
capture could not reach idle while the old 2Hz update ran; screenshot inspection
worked, and the repeated updates were then removed.

No physical phone, RVR315 connection handoff, long screen-off duration, reboot,
companion callback delivery or OS process-reclamation/restart test. No claim of
an unkillable process. The next test is actual Pixel 10 pairing, background alert,
Pause → Garmin → RideWatch Resume with the RVR315.
