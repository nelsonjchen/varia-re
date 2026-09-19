# First physical connection and address correction — v0.6.1

## Failure and root cause

User reported a Bluetooth exception after manually installing v0.6. Wireless ADB
paired successfully after restarting the stale local ADB server; initial pairing
failed and mDNS was empty until restart. Only the user-identified Pixel was used.
Phone identifies as Pixel 10 Pro, Android 17; min/target/compile remain API36.
Runtime Bluetooth scan/connect and notification permissions were granted.

A diagnostics build retained the installed app's settings and logged the caught
exception. BluetoothAdapter.getRemoteDevice threw IllegalArgumentException:
its input was a lowercase address saved from AssociationInfo.getDeviceMacAddress
().toString(). Android's Bluetooth address parser expects uppercase hex. This
failed before any GATT connection attempt. Raw address, network pairing details,
preferences and logs stay in ignored artifacts/captures/pixel-bluetooth/.

## Fix

BluetoothAddress canonicalizes whitespace/case using Locale.ROOT and validates
six hexadecimal octets. Apply at CDM save and when loading old saved addresses;
persist the canonical value for existing installs. Malformed values now show a
pair-again message without an endless retry loop. Keep caught exception logging
under RideWatchBle for future diagnostics. No reset, re-pair, firmware changes,
permission grants or setting changes to the physical radar were needed.

## Validation

18 JVM tests, debug assembly and lint pass, including new lowercase/mixed-case,
blank, whitespace and malformed-address regression cases. Installed v0.6.1/7 on
the user's Pixel with adb install -r, retaining app data, and opened it.
Actual phone screen shows radar battery 84%, LIVE/no targets, and
“Connected · receiving legacy radar measurements”. New app-process filtered
Bluetooth exception log empty. Evidence: fixed-screen.png, exception.log,
preferences.xml and after.log under the ignored capture directory; build log
artifacts/research/bluetooth-fix-build.log.

This verifies physical connection, battery read and receipt of decoder-recognized
measurement notifications. It does not validate moving-vehicle range/speed,
alert timing, raw protocol bytes, long-term background reliability or simultaneous
Garmin/RideWatch use. RVR315 model is user supplied; firmware not recorded.
App left running and connected as requested, not paused or force-stopped.
