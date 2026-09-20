# Alarm volume — v0.10.0 / 16

## Implementation

User approved alarm-volume playback and automatic minimum ride volume. Both
SoundPool tones and TTS now use USAGE_ALARM. The Activity's volume buttons control
STREAM_ALARM. Existing gentler WAVs and temporary MAY_DUCK focus behavior remain.
No permanent audio focus is requested just by opening/starting monitoring.

Controls → Sound & vibration → Volume contains an automatic-raise switch and
0–100% minimum slider, default enabled/80%. Percent rounds up to a device volume
step (80% is 6/7 on the test emulator). This never lowers an already louder alarm
stream. Settings apply to the next monitoring session or paused sound preview.
Preview uses the existing per-event test buttons, and raises only when played.

RideVolume owns one adjustment per service session, not per reconnect or alert.
It starts only after foreground-service startup succeeds. It records the baseline
before raising, observes system volume changes, and restores on service teardown
only when the current level still matches and no intervening change was observed.
Preview closes its lease on dismiss/background/start monitoring. Hardware-button
changes are not fought. MODIFY_AUDIO_SETTINGS added; DND policy access is neither
requested nor bypassed. System-policy denial is caught without crashing.

The private persisted ledger preserves baseline and observed override across
process recreation. Reboot invalidates it using BOOT_COUNT. Force-stop/process
kill cannot execute immediate cleanup: volume may remain raised until a later
session closes. Changes made away and back while the process is dead cannot be
observed. Volume is a shared system setting, including wake-up alarms; UI explains
this. No claim of guaranteed restoration after abrupt termination or equal SPL
to the Clock app.

## Validation (synthetic emulator, not Pixel audibility)

JDK17 canonical Gradle tests/build/lint pass: 30 tests, including four new volume
policy tests for quantization, initial mute, restoration, manual override and
recovered override. API36 Google APIs ARM64 task emulator ridewatch-api36,
explicit serial emulator-5560:

- Actual key events set alarm level to 3/7; monitoring start raised it to 6/7;
  Pause restored 3/7.
- Manual volume down during monitoring persisted as overridden=true; Pause
  preserved the manual setting.
- Paused number preview raised 3→6, and closing the dialog restored 3.
- dumpsys audio identifies both SoundPool and Google TTS playback as USAGE_ALARM.
  Number 35 logged start/completion; focus stack empty after completion.
- Volume dialog screenshot inspected: slider, automatic switch, shared-alarm
  explanation and test action visible. No physical loudness or Bluetooth-route
  validation. Pixel wireless ADB is off by user report; no install attempted.

Initial shell `cmd media_session volume --set 2` did not establish the expected
level on this image. Switched to actual volume-key events and verified reads;
do not treat those initial shell attempts as successful volume tests.

Ignored evidence: artifacts/research/alarm-volume/volume.png,
audio-after-speech.txt, audio-log.txt, UI helper. Next: install v0.10 on Pixel and
listen to approach/urgent/numbers/disconnect using intended output route, verify
Pause/manual-volume behavior and normal music recovery.
