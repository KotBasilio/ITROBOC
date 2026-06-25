# ITROBOC Camera and Decoding Tickets

These tickets are intentionally small. Do not combine them into one heroic commit.

The goal is to introduce camera and decoding in layers while keeping `:core`, `:vision`, and `:app` responsibilities clean.

## Progress

- Ticket 1: Done 
- Ticket 2: Done 
- Ticket 3: Done 
- Ticket 4: Done 
- Ticket 5: Deferred

## Ticket 1 — Add CameraX preview shell to Admin::Edit

### Goal

Replace the mock camera preview placeholder in Admin::Edit with a real CameraX preview area.

No decoding yet.

### Requirements

1. Add CameraX dependencies to `:app`.

2. Add camera permission to the Android manifest.

3. Admin::Edit should request camera permission when needed.

4. If permission is granted:

   * show a live camera preview in the existing preview area.

5. If permission is denied:

   * show a clear placeholder message, for example:
     `Camera permission is required for scanning.`
   * keep the rest of Admin::Edit usable.

6. Keep existing mock signature input and mock Scan behavior working.

7. Add a visible guide rectangle or simple overlay inside the preview area if reasonably easy.

   * This is only a visual alignment guide.
   * It does not need to crop or decode anything yet.

8. Do not add image decoding.

9. Do not add TD camera scanning.

10. Do not move Android dependencies into `:core`.

### Acceptance criteria

* App builds.
* Admin::Edit opens.
* Camera permission flow works.
* Live preview appears on physical device/emulator with camera support.
* Mock Scan still works.
* `:core` remains Android-free.

### Commands

Run:

```text
./gradlew :core:test
./gradlew :app:assembleDebug
```

---

## Ticket 2 — Add ImageAnalysis frame tap and debug state

### Goal

Make Admin::Edit able to receive camera frames and report basic debug information after Scan is pressed.

No barcode decoding yet.

### Requirements

1. Add CameraX `ImageAnalysis` use case in `:app`.

2. Use a keep-latest strategy for analysis.

3. Always close `ImageProxy` after processing.

4. Add simple frame debug state:

   * width
   * height
   * rotation
   * timestamp or frame counter
   * whether the latest frame was captured because the user pressed Scan

5. The `Scan` button should request processing of the next available frame.

6. After one frame is processed for the request, stop treating every frame as a scan result.

   * Continuous decoding is not required.

7. Display debug text somewhere in Admin::Edit, for example:

   * `Frame: 1280x720 rot=90`
   * `Last scan frame received`

8. Keep mock signature assignment available.

   * The frame tap should not replace the mock scanner yet.

9. Do not add real barcode decoding.

10. Do not add TD multi-card camera scanning.

### Acceptance criteria

* App builds.
* Camera preview still works.
* Pressing Scan causes the app to process one camera frame.
* Debug frame information updates.
* ImageProxy is closed safely.
* Mock signature workflow still works.

### Commands

Run:

```text
./gradlew :core:test
./gradlew :app:assembleDebug
```

---

## Ticket 3 — Add pure `:vision` module and static barcode decoder prototype

### Goal

Create a new pure Kotlin/JVM `:vision` module for barcode decoding experiments.

No Android dependency in `:vision`.

The first decoder may operate on synthetic/static grayscale data. It does not need to solve real camera images yet.

### Requirements

1. Add a new Gradle module `:vision`.

2. `:vision` should be a pure Kotlin/JVM module.

3. `:vision` must not depend on Android, Compose, CameraX, or `ImageProxy`.

4. Add basic data types, for example:

   * `GrayImage`
   * `BarcodeRoi`
   * `DetectedSignature`
   * `BarcodeDecodeResult`
   * `BarcodeDebugInfo`

5. Add a simple decoder interface:

   * `BarcodeDecoder`
   * `decode(image: GrayImage): BarcodeDecodeResult`

6. Implement a first simple black-bar decoder prototype.

   * It may assume the barcode is already cropped and roughly aligned.
   * It may use thresholding and black-run detection.
   * It should return a raw signature string and confidence/debug info.

7. Add tests using synthetic grayscale data.

   * simple clear bar pattern decodes
   * blank image returns NotFound
   * noisy or ambiguous pattern returns NotFound or Ambiguous
   * debug info exposes detected black runs or normalized pattern

8. Do not integrate this into Admin::Edit yet.

9. Do not add Android code in this ticket.

### Acceptance criteria

* `:vision` exists as a pure module.
* Unit tests pass.
* Decoder can process synthetic barcode-like data.
* No Android dependency leaks into `:vision` or `:core`.

### Commands

Run:

```text
./gradlew :core:test
./gradlew :vision:test
./gradlew :app:assembleDebug
```

---

## Ticket 4 — Integrate Admin::Edit Scan with camera frame crop and decoder

### Goal

Connect Admin::Edit camera scanning to the pure decoder pipeline.

The user should be able to press Scan, process one camera frame crop, decode a raw signature, and assign it to the selected card through existing profile editing logic.

### Requirements

1. In `:app`, add an adapter from CameraX frame data to the `:vision` image format.

2. Use the preview guide rectangle as the intended barcode ROI.

3. On Scan:

   * request one frame
   * crop/convert the ROI into a `GrayImage`
   * pass it to `:vision` decoder
   * if decoder returns Found, use the raw signature for Admin::Edit assignment
   * show the detected signature and confidence
   * if decoder returns NotFound/Ambiguous, show a clear message and do not mutate profile state

4. Keep mock signature input as a fallback/debug path.

   * It can be hidden behind a debug toggle or remain visible for now.

5. Preserve existing Admin::Edit behavior:

   * selected card
   * aliases
   * conflict handling
   * alias removal
   * auto-advance
   * save/back

6. Do not implement TD multi-card scanning.

7. Do not implement automatic card detection.

8. Do not implement persistence.

9. Keep responsibilities separated:

   * `:app`: CameraX, ImageProxy conversion, ROI crop, UI
   * `:vision`: barcode decode from plain image data
   * `:core`: profile editing and semantic mapping

### Acceptance criteria

* App builds and runs.
* Admin::Edit camera preview works.
* Pressing Scan attempts to decode one ROI from one frame.
* On successful decode, selected card receives the raw signature as mapping/alias.
* On decode failure, profile state is unchanged.
* Debug information shows detected signature/confidence or failure reason.
* Existing mock path still works for testing.

### Commands

Run:

```text
./gradlew :core:test
./gradlew :vision:test
./gradlew :app:assembleDebug
```

---

## Future Ticket 5 — TD multi-barcode scan

Not now.

Later, after Admin::Edit single-barcode scanning works:

* detect multiple barcode candidates in a frame
* decode each candidate
* deduplicate raw signatures
* pass list to `TdScanAccumulator.scanMany(...)`
* present calm TD batch feedback

This ticket must wait until single-barcode Admin::Edit scanning is stable.
