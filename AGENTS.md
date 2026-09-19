# Working in varia-re

## Purpose and scope

Reverse engineer the Garmin Varia Android app for interoperability with the user's
RVR315. Priorities are radar data and alert semantics, spoken approach alerts,
reliable background connection/reconnection, motion-triggered activation above
4 mph, and joined-phone/group notifications. These are research directions, not
a settled app specification. Keep camera and newer-radar features secondary.

## Start each session

Read this file, `CURRENT_STATE.md`, `docs/research_queue.md`, and the newest relevant
file in `journal/`. Inspect `git status --short`. Use those durable files to resume;
do not depend on chat history or load an entire decompiler tree. Preserve unrelated
changes. Instructions from sibling repositories are examples, not rules here.

## Required journal and handoff

- Append an entry to `journal/YYYY-MM-DD.md` for every substantive work session,
  including partial/blocked sessions, before yielding to the user.
- Record time with timezone, objective, actions/commands, evidence paths,
  findings with confidence, validation performed, failures/blockers, and the
  exact next step. Record failed approaches so later work does not repeat them.
- Keep entries chronological; do not rewrite old conclusions silently. Append
  corrections and link superseding evidence.
- Update `CURRENT_STATE.md` when capabilities, blockers, or the next step change.
  Keep it short and current. Update the research queue as work progresses.
- Put detailed findings in focused `reports/` files and link them from the journal.
  Journaling is required even if no code changes or successful result occurred.

## Evidence discipline

- Label claims as official documentation, static APK evidence, device capture,
  third-party research, hypothesis, or proposed design. Do not present one as another.
- For APK evidence record origin, acquisition time, package/version/versionCode,
  SHA-256, signing-certificate digest, splits, and analysis tool versions. Cite
  class/method and file location, with a bytecode check for ambiguous decompilation.
- For captures record app/Android versions, phone model, radar model/firmware,
  test setup, timestamps, and whether results are synthetic or observed.
- Do not transfer RearVue 820/RTL515 packet semantics to RVR315 without evidence.
  In particular, distinguish relative closing speed from ground speed.
- Preserve raw inputs locally under ignored `artifacts/`; commit concise provenance,
  independently written notes/scripts, and small sanitized fixtures when useful.
  Keep addresses, account data, location traces, keys, and full bugreports untracked.
- Pin external research to commits when using it substantively. Record applicable
  licenses before copying code; a public repository is not automatically reusable.
- Validate parser work against raw packets, malformed inputs, units, and state
  transitions. Synthetic tests do not establish hardware compatibility.
- An empty/stale radar stream is not proof that the road is clear. Distinguish
  disconnected, stale, healthy/no targets, and active threats in proposed designs.

## Tools and workflow

- Prefer `uv` for Python environments/runs and `pnpm` for JavaScript tooling.
- Prefer `rg` for targeted searches. Keep commands reproducible and outputs bounded.
- macOS uses zsh: avoid special/read-only variable names such as `status` and `path`;
  use descriptive names. Quote paths and handle unmatched globs deliberately.
- If Git reports `Unable to create '.git/index.lock': Operation not permitted`,
  retry the command. Do not blindly remove locks or other Git internals.
- Do not make firmware changes, reset pairing, or modify device settings merely
  to inspect them. Prefer passive captures and narrowly scoped connection probes.
- Do not promise an unkillable Android process. Check OS/target-SDK restrictions
  and distinguish process death, task dismissal, force-stop, reboot, and OEM policy.
- Keep research bounded; do not build a complete app before the protocol and
  lifecycle questions needed for its features are resolved.

## App continuation

- Read `docs/app.md` and `reports/2026-09-19_apk_protocol.md` before changing the
  decoder or connection setup. Preserve the distinction between static evidence
  and physical validation; numeric speed stays experimental until captured.
- Build with JDK 17, SDK 36, and the checked-in Gradle wrapper. Canonical check:
  `./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`.
- Keep the protocol/policy core Android-independent. Use monotonic time for local
  freshness. Do not allow malformed/diagnostic frames to keep a dead stream live.
- Retain replay/expiry/authentication checks on group traffic. Reject old callback
  generations after mode changes; distinguish host-relative targets in the UI.
- Use an explicitly selected emulator/device serial for ADB. Never assume the
  first attached device is disposable. Task-created AVDs are `ridewatch-api30` (historical) and `ridewatch-api36`.

## Device baseline (user directive, 2026-09-19)

- User owns a Pixel 10 and permits it as the minimum supported baseline. RideWatch
  uses Android 16 / API 36 for minSdk, targetSdk and compileSdk. Do not reintroduce
  older-Android compatibility branches without a concrete new requirement.
- Use association-ID presence observation and `onDevicePresenceEvent`, modern GATT
  value callbacks and descriptor writes, and non-exported runtime receivers.
- The historical API-30 emulator checks apply to v0.1 only. The current APK cannot
  install there; do not cite those checks as validation of the Pixel 10 build.

## Temporary testing lifecycle (user directive, 2026-09-19)

Retain RideWatch's foreground service for screen-off/background monitoring.
Pause must release BLE and stop audio/reconnects, persist across opening the app,
and only an explicit user start/resume action may clear it. v0.6 disables motion/presence/boot wake
through TESTING_LIFECYCLE; do not re-enable these during side-by-side testing
without a new user request. See reports/2026-09-19_testing_lifecycle.md.


## Kotlin implementation (user directive, 2026-09-19)

- Production app and core code lives under `src/main/kotlin`; use Kotlin for new
  implementation. Kotlin 2.2.21, AGP8.10.1, Gradle8.11.1, JVM17, SDK36.
- Existing Java regression tests deliberately remain unchanged as migration
  oracles; new disconnect tests use Kotlin. Do not mistake these for unconverted
  production code. Keep Java-compatible core record APIs while those tests remain.
- Unexpected BLE loss is a direct connection event, independent of stream health.
  Announce the retained battery once per lost connection, not every retry. Pause
  and mode changes remain silent and release audio. Never report a guessed battery.
