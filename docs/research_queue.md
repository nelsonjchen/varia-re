# Research queue

| ID | Status | Question / deliverable | Evidence required |
| --- | --- | --- | --- |
| R01 | Next: input needed | Acquire and identify the Android app | APK/splits, SHA-256, version, certificate, origin |
| R02 | Pending R01 | Map manifest, services, receivers, permissions, target SDK | Decoded manifest with source references |
| R03 | Pending R01 | Map RVR315 discovery, pairing, subscriptions, reconnect path | GATT UUIDs and app call graph |
| R04 | Pending R03 + hardware | Decode target IDs, distance, threat, speed, heartbeat | APK parser plus labeled RVR315 notification captures |
| R05 | Pending R02 | Explain background disconnect / termination behavior | Service lifecycle plus time-correlated phone diagnostics |
| R06 | Proposed | Design motion activation above 4 mph | OS permission/lifecycle review and power measurements |
| R07 | Proposed | Design TTS from available measurements | Verified units, freshness, ID continuity, audio policy |
| R08 | Proposed | Design joined-phone alert relay | BLE concurrency test; relay latency/loss and stale-state tests |

First hardware session: record versions; capture discovery and a normal connection;
observe stationary/no targets, one controlled pass, disconnect, and reconnect.
Record screen-off behavior separately. Keep captures private and use an observer
or stationary setup for labeled passes. Later test overnight idle, reboot,
Bluetooth toggling, task dismissal, process death, and explicit force-stop as
distinct cases. Record results rather than assuming all are the same failure.
