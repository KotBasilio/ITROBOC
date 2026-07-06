🧭 The beetle hot-path is not one straight tunnel. It is a small relay across a few lanes.

**1. Effects touching the 🪲 hot-path**

In [EditBoardScreen.kt](/home/miron/proj/ITROBOC/app/src/main/java/org/itroboc/app/EditBoardScreen.kt):

- `LaunchedEffect(Unit)` near the top of `EditBoardScreen`
  - Purpose: request camera permission once if missing.
  - Relation to hot-path: indirect only. It opens the gate for camera work, but is not per-frame.

- `LaunchedEffect(dreamTopic)` in `CentralArea`
  - Purpose: set beetle dream topic like `Eyes`, `Hands`, `Wind`, `Joker`.
  - Relation to hot-path: UI-state reaction, not frame-by-frame decoding.

- `DisposableEffect(lifecycleOwner, cameraProviderFuture, analysisExecutor)` in `CameraPreview`
  - Purpose: bind camera preview + analyzer when the composable enters, unbind and shut down when it leaves.
  - Relation to hot-path: this is the big structural bridge. It installs the live analyzer.

- `LaunchedEffect(controller)` in `rememberEditBoardController`
  - Purpose: run `controller.runMetricsLoop()`.
  - Relation to hot-path: yes, adjacent. It updates `scansPerSecond` and `scansIdleCount` every 500 ms based on scan deltas.

Also important, though not an effect:
- `rememberUpdatedState(consumeScanRequest)` and `rememberUpdatedState(onScanProcessed)` inside `CameraPreview`
  - These keep the analyzer using the latest lambdas without rebuilding the whole camera pipeline.
  - This is very much hot-path hygiene.

**2. The actual hot-path execution lanes**

There are basically `3` relevant lanes.

1. `UI / Main thread`
2. `Camera analysis thread`
3. `Compose effect coroutines` which usually also run on Main unless switched

**3. The live frame path**

The hottest path is:

1. CameraX produces a frame.
2. `ImageAnalysis.setAnalyzer(analysisExecutor) { imageProxy -> ... }` runs on the single-thread executor in [EditBoardScreen.kt:645](/home/miron/proj/ITROBOC/app/src/main/java/org/itroboc/app/EditBoardScreen.kt:645).
3. Analyzer checks `currentConsumeScanRequest()`.
4. Analyzer computes ROI and reuses/resizes `reusableRoiPixels`.
5. Analyzer calls `frameDecoder.decode(imageProxy, reusableRoiPixels)`.
6. `AdminEditCameraFrameDecoder.decode(...)` in [AdminEditCameraSupport.kt](/home/miron/proj/ITROBOC/app/src/main/java/org/itroboc/app/AdminEditCameraSupport.kt:58) copies ROI pixels and runs the barcode decoder.
7. Analyzer posts result back to Main:
   - `mainExecutor.execute { currentOnScanProcessed(scanOutcome) }`
8. On Main, `controller.handleCameraScan(scanOutcome)` runs in [EditBoardController.kt](/home/miron/proj/ITROBOC/app/src/main/java/org/itroboc/app/EditBoardController.kt:58).
9. Controller updates:
   - `scanDeltas`
   - `thoughts`
   - consensus state
   - maybe `lastResultMessage`
   - maybe board state via reducer
10. State changes trigger recomposition of the UI.

So the per-frame decode work is not on the UI thread. The controller handling currently is.

**4. What runs on which lane**

`Camera analysis thread`:
- `setAnalyzer(...)`
- ROI calculation
- ROI luma copy
- verdict decoder / slow decoder work
- `reusableRoiPixels` reuse

`Main/UI thread`:
- Compose recomposition
- `handleCameraScan(...)`
- reducer calls from accepted scans
- button clicks like clear / undo / scissors / seat click
- `mainExecutor` callback delivery from analyzer
- permission launcher callback

`Compose effect phase`:
- `LaunchedEffect(Unit)` permission request
- `LaunchedEffect(dreamTopic)` dream updates
- `LaunchedEffect(controller)` metrics loop startup
- `DisposableEffect(...)` camera bind/unbind lifecycle

Again: effect phase is about timing/permission to do side effects, not automatically about another thread.

**5. So is there one hot-path or several?**

🧩 Several, but one of them is the true heat core.

The true performance-critical core is:
- analyzer executor
- ROI extraction
- decoder logic
- hop back to main

Then there is a secondary adjacent path:
- controller processing on Main
- consensus / debounce / unknown handling
- metrics bookkeeping

Then there is a tertiary UI-reactive layer:
- recomposition
- dream effects
- status painting

So the beetle is really a relay creature:
- `eye` on analyzer thread
- `mind` mostly on Main
- `face` in Compose

**6. Which effect matters most to performance?**

For raw SPS:
- `DisposableEffect` itself is not the expensive part, but it installs the expensive machinery.
- `LaunchedEffect(controller)` metrics loop is cheap but continuously active.
- `LaunchedEffect(dreamTopic)` is tiny.
- the real cost is not the effect API; it is the analyzer callback body and decode path.

**7. A practical simplified map**

- `LaunchedEffect(Unit)`:
  - “ask for camera once”

- `DisposableEffect(...)`:
  - “mount the camera machinery”

- analyzer executor:
  - “see frame, crop ROI, decode”

- `mainExecutor.execute { ... }`:
  - “hand result back to UI world”

- `handleCameraScan(...)`:
  - “ponder / accept / reject / mutate state”

- recomposition:
  - “redraw cockpit”

- `LaunchedEffect(dreamTopic)`:
  - “closed-eye symbolic state update after composition”

- `LaunchedEffect(controller)`:
  - “heartbeat for SPS display”

🫖 If you want, next I can draw this as a tiny ASCII sequence diagram:
`CameraX -> analyzer thread -> decoder -> main executor -> controller -> recomposition`
with notes on where ambiguity, consensus, and dream-state live.