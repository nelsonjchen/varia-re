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

### 2026-09-20 correction: navigation/media volume

User chose navigation audio again. v0.10.1 applies the configurable minimum and
restoration to media volume instead; alarm settings untouched. Emulator 4→12→4
start/Pause checked. Physical listening and manual-volume controls pending.
See [media-volume report](../reports/2026-09-20_media_volume.md).

### Battery freshness finding (2026-09-20)

Production currently reads Battery Level only on subscription, so disconnect
speech can use an old connection-time percentage. Explicit test uses sample84%.
Next: periodic serialized refresh plus sample age, then physical discharge check;
do not infer sensor accuracy from an unchanged cached reading.

Battery refresh implemented in v0.10.4: immediate plus serialized 60-second reads,
valid samples update display/disconnect speech. Installed Pixel; repeated physical
callbacks/discharge validation still pending. Sample age UI remains future work.
See reports/2026-09-20_battery_refresh.md.

Official app battery strategy recovered: initial read plus notifications, not a
fixed interval in its setup sequence. See reports/2026-09-20_official_battery.md.
Matching subscription and measuring radar-driven cadence remain next work.

### 2026-09-20 notification matching and broader comparison

v0.10.5 now reads then subscribes to Battery Level, polls only on unsupported/
failed subscription. Installed Pixel; physical CCCD/notification capture pending.
Audit: reports/2026-09-20_divergence_audit.md. Next diagnostics gaps are target
indication (3205) and device model/firmware reads. Parser scale/ID/threat/range and
expiry thresholds match static legacy evidence; expiry evaluation, error recovery,
reconnect and audio/lifecycle policies are not exact parity.

### 2026-09-27 multi-vehicle speech

v0.11.0 counts group changes then repeats priority speed; lone approaches remain
numbers-only. Synthetic transition tests added; Pixel install/listening pending.

### 2026-09-28 battery correctness follow-up

Constant84% still unverified. v0.11.2 fixes omitted indication support, adds minute
read-back regardless of subscription success, request timeout/reconnect and sample
age display/logging.36 tests/build/lint pass; Pixel install/capture pending.
See reports/2026-09-28_battery_reliability.md. Need raw repeated successful reads
before attributing unchanged percentages to firmware or claiming the symptom fixed.
