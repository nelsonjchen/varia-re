# APK acquisition and first inspection

Prefer the version installed on the user's phone, since background behavior can
differ by release. Current environment has ADB but no attached device. Enable USB
debugging and authorize this Mac on the phone before running these commands.

```sh
ADB="$HOME/Library/Android/sdk/platform-tools/adb"
"$ADB" devices -l
# If multiple devices exist, add -s <serial> to each subsequent adb command.
"$ADB" shell pm list packages | rg -i 'garmin|varia'
# Replace <package> with the observed Varia package, not an assumed identifier.
"$ADB" shell pm path <package>
"$ADB" shell dumpsys package <package>
```

Pull **every** `package:` path returned by `pm path`, including split APKs, into
`artifacts/apk/<version>/` using `adb pull <remote-path> <local-path>`. Do not copy
the literal `package:` prefix. Hash each with `shasum -a 256 <apk>`.
Use Android SDK `apksigner verify --print-certs <apk>` to verify signatures and
record the certificate digest; record base and split identity and tool versions.
Signature validity alone does not establish Garmin provenance: compare to a known
installed official build when obtaining binaries from mirrors.

With JADX installed, decode the base APK into a hash-named local artifact directory.
Inspect split contents separately where needed. Record decompiler errors. Start at
the manifest and search narrowly for:

```text
6a4e3200  6a4e3203  BluetoothGatt  onCharacteristicChanged
setCharacteristicNotification  writeDescriptor  connectGatt
startForeground  onStartCommand  onTaskRemoved  onDestroy
BOOT_COMPLETED  CompanionDevice  PendingIntent  WakeLock
```

Follow actual call sites into parser and lifecycle logic; UUID search hits alone
do not establish model support. Inspect native libraries only if the call chain
enters JNI. Check bytecode where decompiler control flow is uncertain.

For runtime evidence, first record phone/OS/app/radar versions and the exact
scenario. Enable Bluetooth HCI snoop logging through Android developer options
if needed, reproduce a short connection session, and collect the available snoop
or bugreport under `artifacts/captures/`. Availability/export differs by phone.
Keep a local timestamped event log so packet frames can be associated with app
state. Do not clear app data or reset radar pairing to collect an initial baseline.
