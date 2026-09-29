# All-day connection waiting — RideWatch v0.12.0 / 25

## Design and evidence

Official Android guidance distinguishes direct GATT connection attempts from
`autoConnect=true`, which waits for an available peripheral and supports automatic
reconnection. Both need a living app process:
https://developer.android.com/develop/connectivity/bluetooth/ble/background
Reviewed 2026-09-29. This change is our design, not a claim about Garmin's exact
reconnect policy. Power savings and reconnection latency are not yet measured.

- Open/Resume makes a direct saved-device attempt with a 20-second timeout.
- After timeout/loss, create an auto-connect client. Waiting for an absent radar
  has no application timeout and no repeating direct attempts.
- An established connection's loss closes the old GATT client before registering
  another auto-connect client. This rejects callbacks from the old connection.
  Pending auto-connect absence callbacks (success, connection timeout, remote
  termination: status0/8/19) retain the waiting registration. Other errors recreate
  it with exponential delay1/2/4/8/16/32/64/120seconds, capped120seconds.
- Successful radar subscription resets error backoff. Discovery/subscription still
  has a20s deadline. The15s stream watchdog only runs after subscription, checking
  every5s; malformed/diagnostic input cannot revive a dead stream. Battery request
  timeout/recovery and minute read-back from v0.11.2 remain.
- Pause removes handlers, unregisters Bluetooth state observation, closes the
  GATT/scan client and stops location/audio/service. No motion/boot/presence wake
  was enabled. Bluetooth-off waits for adapter-on events rather than polling.
- Reopening an active app requests a direct attempt only if transport is absent;
  a healthy connection is left alone. Rotation/permission dialogs retain the
  existing opening guard. A fresh scan callback per search rejects old results.
- Fused high-accuracy rider-speed requests run only with a connected radar and
  enabled speed speech. Disconnect stops them immediately and clears cached speed.
  Reconnection restores fast service ticks immediately; absent-radar local mode
  ticks every5s instead of500ms. Group/motion cadence remains unchanged.

## Validation and limits

Canonical JDK17 core/app tests (41 passed), assemble and lint pass, including five new pure
policy tests covering absent radar, status classification, backoff/reset, and
explicit reconnect. This does not simulate Android's radio behavior.

Device session: Pixel10Pro, Android17; user explicitly reports RVR315 is off.
Radar firmware unavailable. Raw ADB observations are private under
`artifacts/research/2026-09-29-autoconnect/`. No device pairing or Bluetooth settings
were reset. Existing Pixel app was v0.11.1/23; installed v0.12.0/25 over it.
Observed foreground service and no active RideWatch location request in dumpsys
location (historical location records must not be confused with active requests).
Device evidence (PDT):
- 01:00:41 direct attempt, timeout01:01:01, auto-connect registered01:01:02.
  One waiting registration persisted until deliberate Pause at01:02:39 (~97s),
  with no repeated connection attempts in the live log. Earlier Bluetooth client
  history also shows the old build recreating clients every50s while absent.
- Pause exercised the notification's `stop` service action under the app UID:
  service absent and no registered RideWatch GATT client afterward. This checks
  the action handler, not the notification button's touch interaction.
- Home/reopen produced a new direct attempt01:02:48 and auto-connect01:03:09.
  Backgrounded and turned screen off; foreground service/waiting client retained.
- Active location registration sections contain no RideWatch requests while
  waiting. Historical GPS work attribution from the previous app version remains
  in dumpsys and is not an active request.
- Installed version verified as0.12.0/25. No radar connection or fresh battery
  reading is expected with the radar off.

Physical radar return, fresh subscriptions/battery changes, loss announcements,
Bluetooth toggles, process death and overnight power remain hardware tests. No
battery-life percentage or guaranteed reconnect latency is claimed. Force-stop
still prevents operation; auto-connect does not make the service unkillable.
