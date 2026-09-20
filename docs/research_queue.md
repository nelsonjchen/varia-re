# Research queue

| ID | Status | Question / deliverable | Evidence |
| --- | --- | --- | --- |
| R01 | Done, mirror provenance bounded | Acquire and identify APK | 5.1.3/157, verified signature, SHA-256; protocol report |
| R02 | Initial pass done | Manifest/services/permissions/target SDK | target 35, MonitorDevicesService connectedDevice, START_STICKY |
| R03 | Static + physical connection smoke check | Discovery/subscription/reconnect | UUIDs and notification dispatch recovered; own GATT client built |
| R04 | Static done; physical pending | ID, range, threat, speed, heartbeat | APK parser plus raw fallback; synthetic tests; need RVR315 captures |
| R05 | Runtime pending | Explain user's background termination | Garmin already has foreground service; need actual phone diagnostics |
| R06 | Prototype built | Activation above 4 mph | GPS accuracy/dwell/gap gate tested; overnight power pending |
| R07 | Tones + TTS built | Audio | Static Garmin mapping/measurements; synthesized chimes, serialized TTS, focus/stream emulator check; audible road test pending |
| R08 | LAN prototype built | Joined-phone alerts | AES-GCM, replay/expiry, two-peer loopback test; physical networking pending |
| R09 | Next with hardware | Validate Pixel 10 / Android 16 lifecycle | Association-ID presence wake, screen off, process death, OEM policy, reboot/force-stop |

First hardware session: record versions, discovery and normal connection, stationary
heartbeats, a controlled pass, disconnect and reconnect. Use stationary setup or
an observer for labels. Do not conflate task dismissal with force-stop. Keep raw
captures private; promote only measured claims to hardware-verified status.

Graphical UI v0.3 has API36 emulator evidence in `design-qa.md`. Physical
RVR315 range/alert/reconnect validation remains the next step.

v0.6 testing profile: foreground service retained; persistent Pause/Resume added.
Motion, presence wake and boot reminders disabled pending the user ending this
testing phase. See `reports/2026-09-19_testing_lifecycle.md`.

First physical Pixel 10 Pro / Android 17 smoke check passed after v0.6.1 address
case fix: connected, battery read, live empty stream. Vehicle pass/raw packet
capture and long-term lifecycle behavior still pending.

### 2026-09-19 implementation update: estimated road-speed speech

v0.7.0 repeats numbers and adds Google fused rider speed; unknown rider speed
contributes zero per user request. Local Pixel provider/foreground lifecycle
observed; moving road-speed accuracy remains unvalidated. Group peers need host
rider speed in the authenticated wire format before speaking road estimates.
See [speed fusion report](../reports/2026-09-19_speed_fusion.md).

### 2026-09-19 implementation update: Kotlin and direct disconnect alerts

v0.8.0 migrates production app/core to Kotlin and announces direct transport loss
plus last-known battery, independent of stream freshness. 26 tests and API36
emulator audio/service checks pass. Physical Pixel update and disconnect
speaker/headphone check pending its return to wireless ADB. See
[migration/disconnect report](../reports/2026-09-19_kotlin_disconnect.md).


### 2026-09-20: open-to-connect and UI polish

v0.9 treats app opening as a connection request even after Pause. API36 emulator
checks cover fresh launch, Home/reopen, Pause and rotation, plus portrait/wide/
320dp UI. Physical Pixel update pending ADB connection. Motion/presence/boot wake
remain disabled. See [polish report](../reports/2026-09-20_polish.md).

### 2026-09-20: alarm-volume controls

v0.10 moves tones/TTS to alarm volume with a configurable start minimum and
conditional restoration on Pause. Emulator volume/focus checks and 30 tests pass;
Pixel listening/routing and force-stop recovery behavior remain hardware work.
See [alarm-volume report](../reports/2026-09-20_alarm_volume.md).
