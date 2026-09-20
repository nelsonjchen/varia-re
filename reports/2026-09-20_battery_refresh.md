# Battery refresh — v0.10.4 / 20

Code finding: BleRadar previously read 180f/2a19 only after the radar CCCD write.
A connected ride could therefore retain its initial battery percentage for hours.

Fix: immediate read remains; completed callbacks schedule another read after
60 seconds. Rejected requests and synchronous Bluetooth exceptions retry after
60 seconds. Only one read is pending at a time. Pause/disconnect removes the timer
and pending state; old GATT callbacks are rejected. Successful one-byte 0..100
values update the existing listener/display/DisconnectPolicy. Invalid/failed reads
preserve last-known battery. Battery success never refreshes radar stream health.
Missing battery service does not generate polling traffic. If Android never
returns an accepted read callback, polling waits for it or a connection reset;
we do not overlap operations or tear down a healthy radar stream for battery alone.

Canonical JDK17 Gradle tests/build/lint pass (existing 30-test suite). Reviewed
callback serialization, failure retry and cleanup paths; no new scheduling tests.
Installed to user's explicitly selected paired Pixel 10 Pro over wireless ADB,
versionName 0.10.4/versionCode20 verified; foreground service running. No new battery
callback appeared in this process's filtered log during the check, so periodic
physical reads/discharge accuracy are not yet verified. No radar power/pairing
settings changed. Next: connected session lasting over a minute, confirm repeated
RideWatchBle battery-read logs and compare battery over a real discharge interval.
