# Graphical radar QA — 2026-09-19

final result: passed

## Reference and scope

Visual reference: Garmin's original Varia radar view, from its official manual:
https://www8.garmin.com/manuals/webhelp/GUID-C41F445D-457F-447D-88C8-FE286BF157E9/EN-US/GUID-2FCA4AB5-4821-4CD1-BD11-9553CA4DE902.html

Local source: `artifacts/research/varia-reference.jpg` (1201×1076).
Local implementation: `artifacts/research/graphical-final.png` (1080×2424).
Full-view comparison: `artifacts/research/graphical-comparison.png`.
Reference app region cropped to 474×1011; both captures normalized to 800px high
for comparison. Native emulator density is 420 dpi (2.625 px/dp), approximately
411×923 dp. This is an original-like native radar screen, not a pixel-identical
Garmin clone. Source shows three detections; our synthetic scenario has two.
No Garmin image is shipped. Standard Material vectors replace proprietary icons.

## Findings and fixes

- P2, first landscape pass: stacked portrait layout squeezed the distance scale,
  overlapping labels. Evidence: `graphical-landscape.png` (2424×1080).
  Fixed with separate two-column layout: controls/status left, radar right.
  Rechecked `graphical-landscape-fixed.png` at the same size and offline state:
  readable range labels, visible controls, no overlapping radar text.
- No remaining actionable P0/P1/P2 issue in checked viewports/states.

## Fidelity surfaces

- Typography: native sans-serif, 24sp threat banner, strong nearest-distance
  hierarchy, quieter scale/captions. No wrapping/clipping in checked portrait.
- Layout: preserves fixed cyclist above a long vertical vehicle lane; main
  controls remain visible. Adds nearest range, count and explicit state copy.
- Color: dark background, amber detection and red fast-approach banner/edge rails;
  gray stale/offline states. Green is reserved for a fresh empty stream.
- Assets: sharp licensed Material bike/car vectors. Car icon is front-facing
  instead of Garmin's overhead silhouette; intentional standard-icon choice.
- Copy: simulation stays conspicuous; stale never claims the road is clear.
  Group targets label the rider HOST. Numeric speed is still experimental TTS.

## Checks and evidence

- Android 16 / API36 Google APIs ARM64 emulator, task AVD `ridewatch-api36`, serial
  `emulator-5560`. Installed current v0.3 debug APK and opened Activity.
- Controls opens; Preview starts; End preview resets to offline.
- `graphical-final.png`: amber multi-vehicle approach; `graphical-fast.png`: red
  fast approach at 40m; `graphical-stale.png`: expired stream, no target icons.
- `graphical-small.png`: offline at 840×1680 / 320×640dp, controls visible.
- Landscape fixed capture as above. Portrait recaptured after landscape fix.
- Full-view comparison sufficient for simple vector UI; separate crop analysis
  was unnecessary. No photos/textures or custom brand illustration to compare.
- Build, 13 existing tests and lint pass. AndroidRuntime error log empty.

No physical radar, real BLE visual updates, live group mode, sunlight legibility,
TalkBack audio or enlarged-font interaction validated. Overlapping detections can
share an icon position; total count remains visible. Range is plotted without
inventing lateral position or extrapolating a vehicle's motion.

## 2026-09-20 — v0.9 screen polish

Removed persistent wordmark; styled session/control buttons with native ripples,
visible keyboard focus and 56dp targets. Compared baseline portrait/landscape,
then inspected final portrait/landscape and 320dp narrow rendering; labels fit.
Tab order is Pause/Resume then Controls. Open-to-connect and rotation-preserved
Pause checked on API36 emulator. See reports/2026-09-20_polish.md for scoped
validation and ignored screenshot paths; Pixel install pending connection.
