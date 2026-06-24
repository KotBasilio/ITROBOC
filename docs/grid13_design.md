# ITROBOC Barcode Signature Design: `grid13-v1`

Status: proposed design for Bob/Codex implementation  
Project: ITROBOC — Independent Tool for Reading Observed Barcodes On Cards  
Scope: `:vision` decoder model, Deck Profile raw-signature format, debug logging, Admin calibration UX

## 1. Executive summary

The current best model for ITROBOC barcode decoding is **not** “read black bar widths only.”

The useful model is:

```text
camera crop -> ink signal -> black/white runs -> active barcode span -> 13-cell occupancy -> compact raw signature
```

The visible bars and gaps are still important, but mostly as **evidence and debug explanation**. The production identity should be a normalized 13-cell forward signature.

Use this as the design principle:

```text
visible runs are evidence
normalized cells are identity
```

The raw signature stored in Deck Profiles should be compact and UI-friendly:

```text
bfmHHHH
```

where:

- `bfm` = forward Grid13 signature, internally nicknamed **beetle forward meal**;
- `HHHH` = exactly four uppercase hexadecimal digits encoding the 13-bit Grid13 forward bitstring, left-padded to 16 bits before hex conversion.

Reverse/debug form:

```text
brmHHHH
```

where:

- `brm` = reverse Grid13 signature, internally nicknamed **beetle reverse meal**;
- reverse orientation must be preserved explicitly, not canonicalized away.

Do **not** canonicalize `forward` and `reverse` by taking min/max. Earlier analysis showed that reverse-canonicalization destroys uniqueness.

The Deck Profile continues to own all semantic meaning:

```text
raw signature -> CardId
```

The decoder must not know that a signature means `SA`, `DQ`, etc.

## 2. Evidence behind this design

The uploaded barcode sheets contained all 52 card barcode crops: one master sheet and four suit-specific sheets. The second analysis pass measured them directly and found:

- most cards have 5 or 6 visible black runs;
- endpoints are always narrow black bars;
- black runs are effectively bimodal: narrow and wide;
- white gaps also behave like narrow/wide/multi-unit spans;
- strict two-class run-length decoding (`RL2`) is nearly sufficient, but has one observed collision (`D2` and `C8`);
- normalized 13-cell forward occupancy (`Grid13`) produced 52 unique signatures on the clean sheet set;
- reverse-canonicalization reduced uniqueness, so orientation must not be discarded.

Interpretation:

- Thin black behaves like one or more `1` cells.
- Wide black behaves like adjacent `1` cells.
- Thin white behaves like one `0` cell.
- Wide/multi white behaves like adjacent `0` cells.
- But the raw stored identity should be normalized fixed-cell occupancy rather than the visible run-length explanation alone.

## 3. Goals

### 3.1 Production goals

- Produce a stable raw signature from a cropped barcode strip.
- Keep raw signatures short enough to display as clickable chips in Admin UI.
- Preserve orientation explicitly.
- Keep Deck Profile mapping independent from the vision model.
- Log enough debug evidence to diagnose bad scans.
- Keep `:vision` pure Kotlin/JVM and Android-free.

### 3.2 Research/validation goals

- Reproduce the static-sheet results inside the repo with deterministic tests/tools.
- Verify that `grid13-v1` gives 52 unique forward signatures on the provided sheets.
- Verify known `RL2` limitations instead of hiding them.
- Test degradation: blur, exposure, JPEG artifacts, small crop shifts, mild skew.

## 4. Non-goals

Do not implement these in this step:

- official WinDup/Jannersten semantic mapping;
- suit/rank factorization;
- OCR;
- TD multi-card scanning;
- automatic arbitrary-orientation decoding;
- automatic storage of both forward and reverse aliases;
- claiming that the physical barcode is officially “13-bit.”

`Grid13` is a practical normalized signature model, not a claim about the manufacturer’s official internal encoding.

## 5. Terminology

### 5.1 Active barcode span

The horizontal span from the first detected black bar edge to the last detected black bar edge inside the barcode ROI.

### 5.2 Ink channel

A darkness signal designed to make black and dark-red marks both visible. Prefer:

```text
ink = 255 - min(R, G, B)
```

This is preferable to plain luminance because red-suit barcodes may appear reddish/brown and weaker in grayscale.

### 5.3 Grid13 run form (`rl2`)

Human-readable alternating run-length form derived exactly from the normalized
13 Grid13 cells.

Example:

```text
B1-W1-B1-W1-B2-W2-B1
```

Meaning:

- `Bn`: `n` adjacent black cells;
- `Wn`: `n` adjacent white cells.

The observed deck's golden manifest contains black runs only through `B2` and
no `B3`; `W3` is valid and occurs. The generic formatter preserves any exact
run length measured from a 13-cell candidate. Historical research used a
separate two-class pixel-width RL2
quantizer and found a `D2`/`C8` collision. That result remains useful research
history, but the current manifest `rl2` field is the exact Grid13-cell run form
and is collision-free across the 52 forward signatures.

The field keeps the historical name `rl2` for file/log compatibility even
though its current values are exact cell counts rather than two-class labels.

### 5.4 Grid13

A normalized fixed-cell occupancy model.

Procedure:

1. Find active barcode span.
2. Divide it into 13 equal cells.
3. Measure ink/black occupancy in each cell.
4. Threshold each cell to `0` or `1`.
5. Produce a 13-bit string.

Example:

```text
grid13FwdBits = 1010101001001
```

### 5.5 `bfm` and `brm`

Compact raw signature prefixes:

```text
bfmHHHH = beetle forward meal, forward Grid13 token
brmHHHH = beetle reverse meal, reverse Grid13 token
```

The docs may mention the nickname cheerfully, but the token itself is also short enough to be treated as an opaque machine ID.

For outsiders, `bfm`/`brm` are just compact signature-model prefixes.

## 6. Raw signature format

### 6.1 Format

```text
bfmHHHH
brmHHHH
```

where:

- prefix is exactly `bfm` or `brm`;
- `HHHH` is exactly four uppercase hexadecimal digits;
- the hex encodes a 13-bit occupancy string left-padded to 16 bits.

### 6.2 Bit-to-hex conversion

Given a 13-bit string:

```text
1010101001001
```

left-pad to 16 bits:

```text
0001010101001001
```

convert to uppercase hex:

```text
1549
```

forward raw signature:

```text
bfm1549
```

reverse raw signature, using reversed bits:

```text
brm1295
```

The exact reverse example above is illustrative; implementation should compute it.

### 6.3 Storage rules

Deck Profiles store raw signatures as opaque strings. Core should not parse `bfm`/`brm` unless a future validation helper is explicitly added.

Recommended Deck Profile metadata:

```text
signatureModel = "grid13-v1"
```

Each alias remains compact:

```text
bfm1549
```

Do not store verbose aliases like:

```text
grid13Fwd-1010101001001
```

because Admin UI displays assigned aliases as clickable chips.

### 6.4 Orientation policy

Do not canonicalize forward/reverse forms.

Bad:

```text
canonical = min(grid13Fwd, grid13Rev)
```

Reason: reverse-canonicalization created collisions in the sheet analysis.

For the first production path, the UI should encourage a stable scan orientation and use `bfm...` as the primary assignable alias.

`brm...` should exist as:

- debug evidence;
- future explicit reverse-orientation support;
- optionally manually stored alias only if Deck Profile conflict checks pass.

Do not automatically add both `bfm` and `brm` as aliases for every card.

## 7. Decoder pipeline

### 7.1 High-level pipeline

```text
ROI image
  -> ink channel
  -> optional normalization
  -> 1D projection
  -> thresholding
  -> black run extraction
  -> active span detection
  -> white gap extraction
  -> RL2 debug quantization
  -> Grid13 occupancy
  -> bfm/brm token creation
  -> confidence scoring
```

### 7.2 Ink channel

Use:

```text
ink = 255 - min(R, G, B)
```

For already-gray input, use a compatible darkness representation.

### 7.3 Projection

Collapse barcode ROI into a 1D signal. Recommended first implementation:

- rotate/rectify only if existing ROI data already supports it;
- otherwise assume current Admin guide supplies a nearly horizontal strip;
- take median or robust mean ink value across rows for each x column.

### 7.4 Thresholding

Threshold the 1D signal to identify dark/ink columns.

Recommended:

- adaptive threshold based on crop signal distribution;
- keep threshold value/mode in debug output;
- avoid “tuning until a result appears” without lowering confidence.

### 7.5 Run extraction

Extract black runs and internal white gaps:

```text
blackRunsPx = [widths...]
whiteGapsPx = [gaps between black runs...]
```

Also preserve run edges and centers for debug:

```text
blackRunEdges = [start..end, ...]
blackRunCentersPx = [...]
```

### 7.6 Endpoint checks

The first and last black runs are expected to be narrow. If an endpoint is wide or unstable, confidence should fall sharply.

### 7.7 Grid13 run form

Compute `rl2` from the final forward Grid13 bits by counting exact adjacent
black and white cells. Keep measured pixel widths separately in
`blackRunsPx` and `whiteGapsPx`; they must not redefine the logical run form.

### 7.8 Grid13 normalization

Compute active span from first black edge to last black edge.

Divide active span into 13 equal cells. For each cell, compute ink occupancy or mean darkness. Threshold cell score into `0`/`1`.

Output:

```text
grid13FwdBits
grid13RevBits
grid13FwdHex
grid13RevHex
rawSignature = bfm + grid13FwdHex
reverseSignature = brm + grid13RevHex
```

## 8. Result model proposal

Keep names flexible, but the model should include this information.

```kotlin
data class BarcodeMeasurement(
    val rawSignature: String,          // e.g. "bfm1549"
    val reverseSignature: String,      // e.g. "brm1295"
    val signatureModel: String,        // "grid13-v1"

    val grid13FwdBits: String,
    val grid13RevBits: String,
    val grid13FwdHex: String,
    val grid13RevHex: String,

    val rl2: String,
    val blackRunsPx: List<Int>,
    val whiteGapsPx: List<Int>,
    val blackRunEdges: List<IntRange>,
    val blackRunCentersPx: List<Double>,

    val activeStartX: Int,
    val activeEndX: Int,
    val activeSpanPx: Int,

    val confidence: Double,
    val ambiguous: Boolean,
    val warnings: List<String>
)
```

Existing `DetectedSignature` / `BarcodeDecodeResult` can wrap or adapt this instead of using this exact class name.

## 9. Confidence model

Use a confidence score with clear components:

```text
contrastScore
endpointScore
runQuantizationMargin
scanlineAgreement
grid13Stability
deckProfileUniqueness
```

Confidence caps:

- cap if endpoint bars are not clearly narrow;
- cap if crop needs aggressive threshold tuning;
- cap if adjacent scanlines/mini-bands disagree;
- cap if profile lookup finds multiple possible matches.

## 10. Debug logging

Admin debug JSONL should include at least:

```text
timestampMillis
selectedCard
frameWidth
frameHeight
frameRotation
roi
cropWidthPx
cropHeightPx
roiAngleDeg
thresholdMode
thresholdValue
activeStartX
activeEndX
activeSpanPx
blackRuns
blackRunsPx
whiteGapsPx
blackRunCentersPx
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
warnings
deckProfileMatchCount
```

In debug builds, consider saving:

- tiny 1D signal snapshot;
- visualization of cell boundaries;
- cropped ROI image.

## 11. Admin UI implications

Assigned aliases are clickable chips. Therefore, compact aliases matter.

Preferred chip:

```text
bfm1549
```

Optional chip click/details panel can show:

```text
Model: grid13-v1
Forward bits: 1010101001001
Reverse: brm1295
RL2: B1-W1-B1-W1-B2-W2-B1
Black runs: [3,4,5,5,5,3]
White gaps: [4,4,3,7,8]
Confidence: 0.93
```

Do not display long binary IDs directly as the normal chip label.

## 12. Testing strategy

### 12.1 Static sheet fixtures

Place fixtures under:

```text
vision/src/test/resources/barcode-sheets/
  Master.png
  spades.png
  hearts.png
  diamonds.png
  clubs.png
```

### 12.2 Golden manifest

Generate and commit a golden manifest only after Bob’s analyzer reproduces the measured values.

Possible format:

```json
{
  "signatureModel": "grid13-v1",
  "cards": {
    "SA": {
      "grid13FwdBits": "1010101001001",
      "rawSignature": "bfm1549",
      "rl2": "B1-W1-B1-W1-B2-W1-B2-W2-B2-W2-B1"
    }
  }
}
```

### 12.3 Required tests

- suit sheets segment into exactly 13 barcode rows;
- bottom suit pips are not mistaken for barcode rows;
- `grid13Fwd` has 52 unique values on the golden sheet set;
- historical pixel-width RL2 collision `D2`/`C8` remains documented;
- current manifest `rl2` exactly matches `grid13FwdBits`;
- forward/reverse canonicalization is not used;
- bit-to-hex formatting produces exact four uppercase hex digits;
- degradation tests cover blur, exposure, JPEG artifacts, small crop shifts, and mild skew;
- min-RGB ink channel works better or at least no worse than plain luminance on red suits.

## 13. Module boundaries

`:vision`:

- image buffers;
- ink channel;
- projection;
- run extraction;
- RL2/Grid13 measurement;
- raw signature token generation;
- pure tests and fixtures.

`:app`:

- CameraX;
- permissions;
- ROI guide;
- ImageProxy conversion;
- Admin UI display;
- debug log export.

`:core`:

- CardId, DeckProfile, aliases, validation;
- no image/vision logic;
- raw signatures remain opaque strings.

## 14. Migration note

Existing demo signatures may remain synthetic. Do not break the built-in demo profile just to introduce `grid13-v1`.

Real calibrated profiles should carry metadata:

```text
signatureModel = "grid13-v1"
```

Future signature models can coexist by using different prefixes/metadata.

## 15. Final recommendation

Implement `grid13-v1` as the next ITROBOC barcode signature model.

Use:

```text
bfmHHHH
```

as the normal stored/displayed alias.

Keep:

```text
brmHHHH
RL2
blackRunsPx
whiteGapsPx
```

as explicit evidence/debug/future-orientation support.

The beetle’s practical rule:

```text
Find the barcode sandwich crusts, slice the meal into 13 bites, record which bites contain ink.
```
