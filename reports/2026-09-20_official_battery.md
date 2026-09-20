# Official app battery update strategy

Static APK evidence, Varia 5.1.3/157, same input/hash/signature/tool provenance as
2026-09-19_apk_protocol.md. Not an on-air RVR315 timing capture.

Under artifacts/decompiled/varia/sources:
- fe/x0.a selects fe/y0.e for SIMPLE devices (fe/o1.b enum).
- fe/y0.e includes shared command sequence f().
- fe/y0.f includes he/k0 battery read and he/j0 battery notification enable.
- he/k0 references fe/b battery service 180f and Battery Level 2a19.
- he/j0 passes true to he/g1.a, which creates ge/d EnableNotificationBleCommand.
- fe/x0.c routes Battery Level callbacks to handler 0; je/e.a parses and updates
  the level for valid 0..100 values.

Conclusion: initial read plus device-driven notifications, not an app-chosen
fixed polling interval in this setup path. Actual notification timing/change
threshold is radar firmware behavior, not established by this code. RideWatch's
60-second polling interval was an independent implementation choice. Next: mirror
battery subscription with serialized CCCD operations and retain bounded polling
as fallback if notification support is unavailable; verify actual RVR315 cadence.

JADX 1.5.6 fallback instruction output for he.j0 confirms constructor passes
constant1 (enable=true) and the battery service/characteristic UUIDs. Reproduce:
`jadx --single-class he.j0 --single-class-output artifacts/research/battery-notify-fallback.java --decompilation-mode fallback --no-res artifacts/apk/varia-clean.apk`.
This resolves the normal decompiler's constructor-order warning.
