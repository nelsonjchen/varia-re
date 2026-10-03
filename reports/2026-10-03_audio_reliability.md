# Navigation audio reliability — v0.12.2 / 27

## Official guidance and chosen behavior

Reviewed October3,2026:
- https://developer.android.com/media/optimize/audio-focus
- https://developer.android.com/reference/android/media/AudioManager#getAudioDevicesForAttributes(android.media.AudioAttributes)
- https://developer.android.com/reference/android/media/MediaRouter#ROUTE_TYPE_LIVE_AUDIO

On phones, transient MAY_DUCK focus lets Android lower other music players while
the requester plays, then restore them when it abandons focus. Automatic ducking
excludes speech content; podcasts may pause instead. Android Auto can apply its
own vehicle routing/mixing policy, so identical perceived behavior is not assured.
We retain navigation usage/media volume. Full volume is a setting, not measured
sound pressure or a guarantee of audibility through every output.

## Implementation

RadarAudio now uses MAY_DUCK for routine chimes/numbers and TRANSIENT (request a
brief interruption) for urgent/lost/disconnect events. Chime and TTS phases use
matching sonification/speech attributes. Request-attempt and event-generation
checks reject obsolete focus callbacks. Denied urgent focus retries within2s;
a cleared/non-live urgent snapshot cancels pending/active urgent audio. Disconnect
speech keeps its own priority/exemption when speed repeat is off. Service passes
original event tone even when tones are disabled so speech keeps urgent focus.

RideVolume queries anticipated navigation output via getAudioDevicesForAttributes,
not the list of connected devices alone. AudioDeviceCallback and live-audio
MediaRouter callbacks trigger debounced checks (400ms/2s) without active discovery.
An existing service tick checks at most every5s; before-playback checks catch
already-connected output switches. No additional periodic wake alarm was added.
New output gets its own current baseline and target; a previous route's baseline
is never restored onto a different route. Only the current route is restored on
Pause; volume on an output already left may remain raised. Route query is a policy
prediction, not proof of actual playback, and can report unknown.

Actual media index and mute state are read back after raises and before playback.
A below-target/mute warning appears in the main detail, foreground notification,
and test-alert panel. This describes achieved volume without guessing why the
system limited it. Manual changes remain permitted; no per-alert volume reset or
loop fights those changes. Reopen/radar return/new route still apply the configured
minimum. Android DND, focus locks and hardware output limits remain relevant.

## Validation

JDK17 canonical core/app tests, assemble and lint pass:48 tests, including three
focus lifecycle tests (phase/callback isolation, cancellation, retry deadline).
API36 ridewatch-api36 emulator, serial emulator-5560:
- Actual media4/15 →15/15 on opening; Pause restores4/15.
- Changed media via Settings slider to7/15 while monitoring. Warning notification
  reports Phone speaker media7/15,target15/15; Pause retains manual7/15.
- Sound preview raises7→15 and closing restores7.
- Routine chime logs granted MAY_DUCK/SONIFICATION; urgent chime granted
  TRANSIENT/SONIFICATION; numeric preview MAY_DUCK/SPEECH completes. Disconnect
  preview has TRANSIENT in both chime and speech phases and completes.
- Callback/timer cleanup reviewed; no new wake/motion behavior. Emulator stopped
  after checks. Synthetic focus tests do not establish other apps' compliance or
  physical Bluetooth/safe-volume behavior.

Pixel10Pro Android17: updated from v0.12.0/25 to v0.12.2/27, package version
verified. Opening starts foreground monitoring; actual media25/25 (baseline0,
raised25, no override, anticipated phone-speaker route). No physical road/audio
listening or headphone-switch test in this session. Raw private evidence under
artifacts/research/2026-10-03-audio/.

## Failed test approaches

CALLBACK_FLAG_PASSIVE_DISCOVERY is absent from public framework SDK36; used flags0
(no active scan) instead. Emulator shell volume setter was ignored: AppOps records
com.android.server.media as invalid for UID2000. Used actual Settings slider for
volume changes. Pinned AOSP explanatory source (no code copied): framework/base
99b01a65cc4c104933788b3143285ab6bae65827, VolumeCtrl.java and AudioService.java.
Initial rapid hardware-volume key presses only showed the volume panel, not a
changed media index. Actual readbacks were required before declaring tests passed.
