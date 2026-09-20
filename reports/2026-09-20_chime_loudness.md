# Chime loudness estimates

Local file measurements, not a Pixel sound-pressure measurement or listening
trial. Garmin inputs are the five original raw MP3s from Varia5.1.3/157; provenance
in 2026-09-19_apk_protocol.md. RideWatch inputs are current res/raw alert WAVs.
No input normalization or modifications. Tool: FFmpeg9.0.1 ebur128 (true peak
enabled). Reproduce with `uv run python scripts/compare_chime_loudness.py`.
Raw numeric evidence: ignored artifacts/research/audio/loudness-comparison.json.

Primary metric is maximum momentary loudness: K-weighted400ms windows reported
at100ms increments. These subsecond chimes are too short for meaningful3s
short-term loudness or loudness range. Integrated loudness is also recorded but
sensitive to clip length/gating. No playback target normalization applied.
EBU reference: https://tech.ebu.ch/loudness

| Event | Garmin max momentary LUFS | RideWatch max momentary LUFS | Difference (ours minus Garmin) |
| --- | ---: | ---: | ---: |
| Approach | -13.1 | -13.2 | -0.1 LU |
| Urgent | -12.8 | -12.2 | +0.6 LU |
| Connected | -10.7 | -9.2 | +1.5 LU |
| Disconnected | -9.8 | -8.9 | +0.9 LU |
| No vehicles | -29.4 | -10.8 | +18.6 LU |

These are native-channel measurements: Garmin's first four are stereo; ours mono.
Thus the table is a file-level benchmark, not proof of identical playback loudness.
For a second comparison, explicitly downmixing both to mono before measuring gives
Garmin -18.7/-18.3/-16.5/-15.7/-29.4 LUFS respectively; ours unchanged. Channel
rendering materially affects these sounds. Phone speaker processing, Bluetooth,
Android stream gain, surrounding wind/traffic and tone spectrum affect perception.
Do not interpret mono fold-down loss as a proven phase defect or phone behavior.

Whole-file mono RMS and an operational active-region mono RMS are recorded in the
JSON. Active means10ms windows within40dB of the strongest window; it is not a
human hearing threshold. Long quiet tails make whole-file RMS misleading, and
mono RMS should not be directly compared to native stereo LUFS.

Finding: no evidence from these file measurements that RideWatch's approach or
urgent chime has a major inherent loudness deficit relative to Garmin. Biggest
imbalance is our much louder no-vehicles chime. That does not determine how audible
either is on a bicycle. Next: same-route/same-volume Pixel listening comparison;
calibrated acoustic recording if actual SPL/perceived-level matching is needed.
No sounds or application behavior changed.
