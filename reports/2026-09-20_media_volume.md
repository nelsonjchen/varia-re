# Navigation/media volume — v0.10.1 / 17

Supersedes the alarm-routing design in 2026-09-20_alarm_volume.md at the user's
request. SoundPool and TTS use USAGE_ASSISTANCE_NAVIGATION_GUIDANCE again;
RideVolume and foreground volume buttons use STREAM_MUSIC. The configurable
minimum remains enabled at 80%, once per session. This is shared with music and
videos. Pause/preview teardown conditionally restore the previous media level.

Media has a separate persisted session ledger, so no old alarm baseline can be
applied to media. The alarm build was never installed on the Pixel. The app no
longer writes alarm volume. Cleaner WAVs, transient audio focus and existing
volume-policy behavior are unchanged. Abrupt process termination still prevents
immediate restoration; DND, route changes and manual-adjustment observation have
the limitations documented in the prior report.

Validation: canonical JDK17 Gradle tests/build/lint pass (30-test suite, unchanged
core tests reused). API36 task emulator, explicit emulator-5560: start raised
media 4/15→12/15; Pause restored 4/15. Private media ledger recorded original=4,
raised=12. Audio dump captured under ignored artifacts/research/media-volume/.
Additional volume-down key probes during monitoring logged STREAM_MUSIC requests
but did not change the reported level on this image; do not claim these as a
successful manual-override test for this build. Existing policy regression tests
pass; Pixel manual buttons and actual listening remain next. No Pixel install.
