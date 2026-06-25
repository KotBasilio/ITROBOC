# Grid13-v1 Barcode Model

This note is the durable implementation handoff for ITROBOC's current barcode signature model.

Related deeper docs:

* `docs/grid13_design.md` — original design and rationale.
* `docs/grid13_tickets.md` — implementation ticket trail.
* `docs/barcode-sheets/deep-research-report.md` — source research table and algorithm notes.
* `docs/admin_edit.md` — Admin::Edit calibration workflow and debug-log behavior.

## Summary

`grid13-v1` is ITROBOC's current practical raw-signature model for one cropped barcode strip.

The central principle is:

```text
visible runs are evidence
normalized cells are identity
```

The decoder does not decide that a barcode means `SA`, `DQ`, or any other card. It only produces a raw signature. A Deck Profile maps that raw signature to a canonical card ID.

```text
camera/fixture crop -> raw signature -> Deck Profile lookup -> CardId
```

## Why Not Black-Run Width Only

Early decoding based only on visible black-bar widths was too fragile:

* visible black runs are useful, but thresholding and crop variation change exact widths;
* red-suit marks can be weaker under grayscale/luma-only input;
* the historical two-class pixel-width `RL2` explanation had an observed collision on the 52-card sheet set;
* static sheet analysis showed that normalized 13-cell occupancy produced 52 unique forward signatures.

So ITROBOC keeps black/white runs as diagnostic evidence, but stores the normalized Grid13 token as the primary alias.

The current golden manifest's `rl2` field is derived from exact runs in
`grid13FwdBits`, not from pixel-width clustering. It therefore permits `W3`,
has no observed `B3`, and is collision-free for the 52 forward signatures.
Measured widths remain available independently as `blackRunsPx` and
`whiteGapsPx`.

## Grid13 Procedure

For a barcode ROI:

1. Convert pixels to an ink/darkness signal.
2. Project the ROI into a 1D column signal.
3. Threshold the projection to find black columns.
4. Extract black runs and internal white gaps.
5. Define the active span from the first black run edge to the last black run edge.
6. Divide the active span into 13 equal cells.
7. Convert each cell to `1` or `0` by ink occupancy/mean.
8. Build a 13-bit forward string.
9. Reverse that string for reverse/debug orientation evidence.
10. Convert each 13-bit string to a 4-digit uppercase hex token.

Current implementation lives mainly in `:vision`:

* `Grid13Signature.kt`
* `Grid13MeasurementPrimitives.kt`
* `Grid13BarcodeMeasurement.kt`
* `Grid13BarcodeDecoder.kt`

## Raw Signature Format

Forward token:

```text
bfmHHHH
```

Reverse/debug token:

```text
brmHHHH
```

Meanings:

* `bfm` = beetle forward meal, the forward Grid13 token.
* `brm` = beetle reverse meal, the reverse Grid13 token.
* `HHHH` = 13-bit occupancy encoded as four uppercase hex digits after left-padding to 16 bits.

The beetle names are friendly internal nicknames. Technically, `bfm` and `brm` are just compact signature-model prefixes.

Example:

```text
grid13FwdBits = 1010101001001
grid13FwdHex  = 1549
rawSignature  = bfm1549
```

## Orientation Policy

Do not canonicalize forward and reverse signatures.

Bad:

```text
canonical = min(forward, reverse)
```

Why: reverse-canonicalization reduced uniqueness in analysis. The app should encourage stable scan orientation and use `bfm...` as the normal assignable alias.

Current rule:

* assign/store `rawSignature`, usually `bfmHHHH`;
* log `reverseSignature`, usually `brmHHHH`, for diagnosis;
* do not automatically add the reverse token as an alias.

## Deck Profile Boundary

Core Deck Profiles store raw signatures as opaque strings:

```text
Map<RawSignature, CardId>
```

Core does not parse barcode pixels and does not infer card meaning from `bfm...`.

Deck Profile metadata can declare the signature model:

```text
signatureModel = "grid13-v1"
```

Current profile model notes:

* Custom calibration profiles default to `grid13-v1`.
* The default built-in profile is currently `builtin-observed-v1`, generated from `scanned_attempt1.jsonl` plus a focused `S6`/`C6` rescan.
* `builtin-observed-v1` contains `bfm...` forward aliases and `brm...` reverse aliases for retained cards.
* The initial `S6`/`C6` collision was resolved by `6S-and-6C.jsonl`: `S6` is retained as `bfm1669` / `brm12CD`, and `C6` is retained as the palindrome pair `bfm164D` / `brm164D`.
* The built-in demo profile is explicitly synthetic: `synthetic-demo-bridge52-v1`.
* The built-in demo profile's `0x1001`-style signatures are not real physical deck mappings.

## Current Admin::Edit Flow

Admin::Edit currently uses Grid13 for one-frame calibration scans:

```text
CameraX frame
-> centered guide ROI
-> luma GrayImage
-> Grid13BarcodeDecoder
-> BarcodeDecodeResult
-> DeckProfileEditor.assign(rawSignature, selectedCard)
```

Behavior:

* `Found` assigns the forward raw signature to the selected card.
* `NotFound` does not mutate the profile.
* `Ambiguous` does not mutate the profile.
* Low-confidence Grid13 measurements are reported as `Ambiguous`.
* Alias chips stay compact, such as `bfm1549`.
* A clicked alias can show retained session scan evidence when available.

## Debug Fields

Admin::Edit JSONL logs are designed for diagnosis. Current records may include:

```text
timestampMillis
selectedCard
frameWidth
frameHeight
frameRotation
frameTimestampNanos
roi
cropWidthPx
cropHeightPx
roiAngleDeg
decodeResultType
thresholdMode
thresholdValue
activeStartX
activeEndX
activeSpanPx
blackRuns
blackRunsPx
whiteGapsPx
blackRunCentersPx
normalizedPattern
rl2
grid13FwdBits
grid13RevBits
grid13FwdHex
grid13RevHex
rawSignature
reverseSignature
signatureModel
scanlineAgreement
confidence
ambiguous
ambiguousCandidates
warnings
failureReason
deckProfileMatchCount
```

Important current placeholders:

* `roiAngleDeg` is `0.0` for the current rectangular guide crop.
* `scanlineAgreement` is currently `null`; multi-scanline agreement is not implemented yet.

## Validation Fixtures

Barcode sheet PNG fixtures live in:

```text
vision/src/test/resources/barcode-sheets/
```

The golden manifest lives at:

```text
vision/src/test/resources/barcode-sheets/grid13-v1-golden.json
```

Current tests cover:

* token formatting and reverse-bit conversion;
* sheet resource enumeration;
* measurement primitives;
* static Grid13 measurement composition;
* golden manifest uniqueness and known collision documentation;
* degradation reporting for shifts, blur, exposure, JPEG, skew, and red-suit channel comparison;
* live decoder behavior for found, not found, and ambiguous/low-confidence outcomes;
* Admin debug log fields.

## Known Limitations

Grid13 is practical, not a claim about the manufacturer's official encoding.

Known seams:

* Live Android scan currently uses luma-only `GrayImage`, while static research prefers `ink = 255 - min(R,G,B)`.
* Mild skew is unstable in degradation tests and needs later ROI/rectification work.
* `scanlineAgreement` is not implemented yet.
* `roiAngleDeg` is a placeholder.
* TD multi-card scanning is not implemented yet.
* Real profile persistence/import/export is not implemented yet.
* The C8 static analyzer mismatch was a historical artifact of early pixel-width clustering; the current Grid13 model is collision-free on the 52-card set.
* The decoder should not auto-store reverse aliases during live calibration, but the built-in observed profile may contain both `bfm` and `brm` if they were physically verified.

## Next Likely Work

Good next seams after this documentation pass:

* improve live ROI extraction and orientation/rectification;
* add multi-scanline measurement and fill `scanlineAgreement`;
* investigate luma versus RGB/YUV ink extraction on real red-suit scans;
* improve Admin profile persistence/import/export;
* later, adapt Grid13 from Admin calibration into TD multi-card scan workflow.
