# Battery reliability corrections — v0.11.2 / 24

User reports constant84% over multiple rides. Cause is not yet established by raw
physical reads. Rechecked Varia5.1.3/157 using the provenance in the original
protocol report: je/e.a → a8/f0.f parses unsigned byte0, matching our decoding.
Deeper transport code ki/c (subscription worker) prefers indication CCCD02 00
when property0x20 exists, otherwise notification01 00 for property0x10. Our prior
NOTIFY-only implementation was incomplete. No proprietary source copied.

Corrections:
- Support both property types, preferring indications like Garmin. Added tested
  Android-independent selection in BatteryStatus.
- Keep serialized60s read-back even after successful subscription; notification
  silence no longer suppresses refresh. This is our deliberate robustness measure,
  not a recovered Garmin polling interval. Failure callbacks retry normally.
- A read/CCCD request accepted but unanswered for30s closes/retries the connection,
  rather than leaving the operation pending forever. Old-client callbacks cannot
  complete a new operation. This can interrupt monitoring and announce connection
  loss for a wedged optional battery operation; it never submits overlapping GATT
  requests. Pause/close cancels poll and timeout tasks.
- Valid reports refresh a monotonic sample timestamp even when value is unchanged.
  Invalid reports do not refresh age. UI replaces inaccurate "at connection" label
  with just-read/minutes-ago, marking readings stale at3min. Last valid percentage
  survives failed refresh and transport loss; a new ride resets it.
- Diagnostics include property flags, selected CCCD, completion result and raw
  battery byte/percent/source/time. No addresses/serials/location included.

Validation: canonical JDK17 Gradle tests/build/lint pass,36 tests including3 new
battery tests for notify/indicate preference, unchanged-value freshness, rejected
values, valid percentages including0 and session reset. Reviewed callback success/
failure/timeout paths and timer teardown. These are static/unit checks, not physical
GATT integration tests. Pixel absent from ADB; unrelated attached TV untouched.
No install or on-radar validation claimed. APK ready for deployment.

Next: Pixel + powered radar. Confirm property/CCCD selection, initial raw read,
notification and repeated minute reads. If all successful fresh reads remain0x54,
84 is coming from the radar; compare Garmin on the same charge state. This build
makes that distinction observable but does not prove that firmware is at fault.
