# ITROBOC Camera and Barcode Decoding Design

## Purpose

This document describes the planned path for adding camera-based barcode scanning to ITROBOC.

The goal is to move from mock raw signatures to real signatures observed from physical playing cards, while preserving the existing architecture:

`raw signature -> Deck Profile -> CardId`

The scanner/camera side must not decide that a barcode means `DA`, `C7`, or any other card. It should only produce raw signatures. The semantic mapping belongs to the active Deck Profile.

## Main architectural rule

Keep responsibilities separated:

* `:core` owns bridge/domain logic.
* `:app` owns Android UI, CameraX, permissions, and device interaction.
* `:vision` should eventually own pure barcode image/signature decoding.

Recommended module structure:

```text
:core
  CardId, Suit, Rank, Seat
  DeckProfile, DeckProfileEditor
  scan accumulator/session/presentation
  PBN export

:vision
  grayscale/cropped image representation
  barcode ROI data
  bar/run detection
  raw signature decoding
  confidence/debug output
  unit tests with synthetic/static image data

:app
  Compose UI
  CameraX preview
  camera permissions
  Android ImageProxy / Bitmap adapters
  calls :vision decoder
  sends raw signatures to :core
```

## Why not put the decoder into `:core`?

The core module should stay pure bridge workflow and profile logic.

Barcode image decoding is not bridge-domain logic. It is computer vision / signal extraction. It deals with pixels, thresholding, ROIs, rotations, confidence, and debug traces.

Putting this into `:core` would make the core less clear and harder to reason about. A separate `:vision` module keeps the decoder testable and reusable without Android UI.

The Android-specific pieces must not enter `:vision` either, if avoidable. `:vision` should work with simple image data types, not with `ImageProxy`, `Context`, `Bitmap`, or Compose.

## Intended data flow

For Admin::Edit single-card calibration:

```text
CameraX preview
-> user aligns barcode in guide rectangle
-> user presses Scan
-> app captures/analyzes one frame
-> app crops/normalizes ROI
-> vision decoder produces raw signature
-> Admin::Edit assigns raw signature to selected CardId
-> DeckProfileEditor updates profile mapping
```

For future TD multi-card scan:

```text
Camera frame
-> detect multiple barcode candidates
-> decode each candidate to raw signature
-> deduplicate raw signatures
-> TdScanAccumulator.scanMany(...)
-> TD presentation model
```

TD multi-card detection is later. The first useful camera work should happen inside Admin::Edit, where the user scans one selected card at a time.

## First camera target: Admin::Edit

Admin::Edit is the safest place to start because the user already selected the intended semantic card.

Example workflow:

1. User selects `♦A`.
2. User places the physical `♦A` card under the camera.
3. User aligns the barcode inside a guide rectangle.
4. User presses `Scan`.
5. App decodes a raw signature, for example `0x367A`.
6. App assigns that signature to `♦A` through `DeckProfileEditor`.
7. If Auto-advance is enabled, app selects the next unmapped card.

This avoids the harder problem of detecting many cards in one frame.

## Camera strategy

Use CameraX in `:app`.

The first camera implementation should:

* request camera permission
* show a live preview in Admin::Edit
* show a visible guide rectangle / target area
* keep mock signature input available during early development
* not implement multi-card detection
* not implement TD camera scanning yet

The initial scan button should process only one frame or the latest available frame. Continuous background decoding is not required at first.

## Decoder strategy

The decoder should be developed in layers.

### Layer 1: Frame access

Make sure the app can receive camera frames and report debug information:

* frame width
* frame height
* rotation
* timestamp
* whether a frame was received after pressing Scan

No decoding yet.

### Layer 2: ROI extraction

Extract a crop from the guide rectangle area.

The crop should be converted into a simple image representation usable by pure code.

Possible pure data type:

```kotlin
data class GrayImage(
    val width: Int,
    val height: Int,
    val pixels: ByteArray
)
```

The app may convert Android camera data into this type. The decoder should not depend on Android classes.

### Layer 3: Simple barcode decoder

A first decoder can assume the barcode is inside the crop and roughly aligned.

Basic process:

1. Convert to grayscale.
2. Threshold black/white.
3. Project pixel darkness across one axis.
4. Detect black bar runs.
5. Normalize bars into a stable pattern.
6. Return a raw signature string plus confidence/debug data.

Example output:

```kotlin
data class DetectedSignature(
    val rawSignature: String,
    val confidence: Double,
    val bounds: BarcodeBounds? = null,
    val debug: BarcodeDebugInfo? = null
)
```

The exact raw signature format may evolve. At first, consistency matters more than final format. The same physical barcode should produce the same raw signature under reasonable conditions.

### Layer 4: Integration

Admin::Edit should use decoder output as if it came from the mock scanner.

The mapping action remains the same:

```text
Detected raw signature + selected CardId -> DeckProfileEditor.assign(...)
```

## Data layout

Suggested `:vision` data types:

```kotlin
data class GrayImage(
    val width: Int,
    val height: Int,
    val pixels: ByteArray
)

data class BarcodeRoi(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
)

data class DetectedSignature(
    val rawSignature: String,
    val confidence: Double,
    val bounds: BarcodeBounds? = null,
    val debug: BarcodeDebugInfo? = null
)

data class BarcodeBounds(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
)

data class BarcodeDebugInfo(
    val threshold: Int? = null,
    val blackRuns: List<IntRange> = emptyList(),
    val normalizedPattern: String? = null
)
```

Suggested decoder interface:

```kotlin
interface BarcodeDecoder {
    fun decode(image: GrayImage): BarcodeDecodeResult
}

sealed class BarcodeDecodeResult {
    data class Found(val signature: DetectedSignature) : BarcodeDecodeResult()
    data class NotFound(val reason: String) : BarcodeDecodeResult()
    data class Ambiguous(
        val candidates: List<DetectedSignature>,
        val reason: String
    ) : BarcodeDecodeResult()
}
```

For early single-card Admin::Edit, one `Found` signature is enough.

For future TD multi-card scan, the system can later support a list of candidates.

## Relationship to DeckProfile

The decoder only produces raw signatures.

It does not know cards.

Correct:

```text
0x367A -> decoder raw signature
DeckProfile maps 0x367A -> DA
```

Incorrect:

```text
decoder detects DA
```

This separation must remain intact.

## Debugging strategy

Camera/decoder work should expose diagnostic information early.

Useful debug displays:

* latest frame size
* latest frame rotation
* latest detected raw signature
* confidence
* cropped ROI preview, later if easy
* decode failure reason

The UI may be calm, but the underlying data should remain truthful.

Principle:

**Core preserves truth; UI decides calmness.**

For this subsystem:

**Vision preserves signal evidence; UI decides how much to show.**

## Development boundaries

Do not combine these in one commit:

* CameraX preview
* frame analyzer
* decoder algorithm
* Admin::Edit integration
* TD multi-card scan

Build in layers. Each layer should compile and be manually testable before the next one.

## Out of scope for the first camera phase

Not included yet:

* TD multi-card barcode detection
* automatic card rectangle detection
* automatic perspective correction
* persistence of real profiles
* full calibration wizard
* cloud sync
* scanner performance optimization
* real WinDup/Jannersten mapping claims

## First milestone

The first meaningful milestone is:

Admin::Edit shows real camera preview, still uses mock signature input, and the app builds/runs on device.

The second milestone is:

Admin::Edit can process one camera frame and report debug frame data.

The third milestone is:

Admin::Edit can decode one barcode-like crop into a raw signature and assign it to the selected card.

Only after that should TD multi-card scanning begin.
