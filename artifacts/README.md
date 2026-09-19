# Local evidence store

Everything here except this README is ignored. Suggested layout:

```text
artifacts/apk/<version>/       original base and split APKs
artifacts/decompiled/<hash>/  generated sources and decoded resources
artifacts/captures/<date>/    HCI snoop, logcat, bugreports, screen recordings
artifacts/tools/              local analysis tool distributions
```

Keep a concise, sanitized provenance record in `reports/` for each analyzed input:
origin, acquisition timestamp, hash, package/version, certificate digest, tool
versions, commands and local paths. Raw device captures can contain unrelated
Bluetooth traffic, identifiers, and location data; do not commit them wholesale.
