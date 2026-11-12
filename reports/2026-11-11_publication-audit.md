# RideWatch pre-publication sensitive-data audit — 2026-11-11

## Result

No sensitive-data publication blocker was found in the reviewed tracked files
or reachable Git history. This is a source/history disclosure review, not a
full application security assessment or a guarantee that every possible secret
format is detectable. At the time of this audit the GitHub repository was private. No visibility,
release, pairing, or device changes were made as part of the audit.
The owner subsequently authorized publication; see the November 11 journal.

## Scope and checks

- 107 tracked files at HEAD `d795b34`.
- All 35 reachable commits, with 385 historical blob versions examined.
- Gitleaks v8.30.1, downloaded from its official GitHub release and verified
  against the release checksum: `git --log-opts=--all --redact=100` reported
  zero findings. Scanner output and reports stay outside the repository in
  `/tmp/mindflakes-ridewatch-audit/`.
- A separate Gitleaks directory scan of all local journals, including the two
  existing untracked journal files, also reported zero findings.
- Additional privacy checks covered Bluetooth/MAC addresses, likely Android
  serials, private IPv4 addresses, email addresses, precise coordinate pairs,
  personal paths, and sensitive/proprietary artifact paths in Git history.
- Manual review covered build configuration, manifest/backup exclusions,
  group-key creation/storage, network paths, logging, acquisition notes,
  artifact policy and asset provenance.

## Matches reviewed

- Bluetooth address matches are the synthetic address-normalization fixtures
  in `BluetoothAddressTest.java`, including their earlier package locations.
- Private IPv4 matches are the example hotspot address in the group-join UI,
  including earlier versions of the same placeholder.
- One historical current-state entry contains the author's local checkout
  path. This is incidental local-path metadata, not a credential or device ID.
- No live Bluetooth addresses, physical device serials, GPS traces, account
  tokens, signing credentials or hardcoded shared group keys were identified
  in the reviewed tracked content.

## Excluded material and assets

No APKs, decompiled vendor trees, packet captures, device bugreports, local
Android properties, environment files or signing keystores were found in the
reachable tracked history. Ignore checks confirm the relevant raw-artifact,
capture, log, build-output, environment and signing-key paths are excluded.
They should remain local and ignored when publishing.

The five checked-in WAV chimes were regenerated in a temporary directory from
`scripts/generate_alert_tones.py`; every generated file exactly matched the
checked-in SHA-256. The Material Icons assets have their source attribution and
Apache-2.0 license recorded in `THIRD_PARTY_NOTICES.md` and
`LICENSE-material-icons`. The Gradle wrapper JAR is the other tracked binary.

Group join keys are generated with SecureRandom at runtime, rather than
embedded in the repository. App backup/data extraction excludes shared
preferences. This observation is not a complete review of group-mode security.

## Release housekeeping

These are not sensitive-data blockers:

- The README described an older build/install state; it was refreshed for
  v0.12.2 during the owner-authorized publication follow-up.
- There is no project-wide source license at the repository root; the existing
  root license covers the Material Icons assets. Decide the source license
  separately before presenting this as an open-source release.
- Preserve the existing local-only journals rather than accidentally including
  unrelated work during a broad stage operation.

No app build/tests were run for this read-only source audit; no app code changed.
