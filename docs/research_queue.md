# Research queue

| ID | Status | Question / deliverable | Evidence |
| --- | --- | --- | --- |
| R01 | Done, mirror provenance bounded | Acquire and identify APK | 5.1.3/157, verified signature, SHA-256; protocol report |
| R02 | Initial pass done | Manifest/services/permissions/target SDK | target 35, MonitorDevicesService connectedDevice, START_STICKY |
| R03 | Initial pass done | Discovery/subscription/reconnect | UUIDs and notification dispatch recovered; own GATT client built |
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
