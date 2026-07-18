# ITROBOC Grid13-v2 Barcode Model

## Status

This document is the canonical active v2 design spec and durable implementation handoff for ITROBOC's current barcode signature model.

`grid13-v2` is the active raw-signature model for observed physical barcode marks on cards.

Core principle:

> Visible runs are evidence; Grid13 cells are identity; `bfm`/`brm` prefixes preserve orientation.

The scanner outputs raw signatures. Deck Profiles map those raw signatures to semantic card IDs such as `SA`, `DQ`, or `C7`.

The decoder must not directly know card meaning.

Related deeper docs:

* `docs/dev_history/vision/grid13_design.md` — original design and rationale.
* `docs/dev_history/vision/barcodes/deep-research-report.md` — source research table and algorithm notes.
* `docs/dev_history/admin-edit/Admin_EditProfile_design.md` — Admin::Edit calibration workflow and debug-log behavior.

## Product context

ITROBOC reads barcode-like markings printed on physical bridge playing cards. The app is designed around this separation:

```text
camera crop -> barcode decoder -> raw signature -> Deck Profile -> CardId -> bridge hand / PBN
```

The raw signature should be stable, compact, and suitable for display as a clickable Admin alias chip.

Grid13 is practical, not a claim about the manufacturer's official encoding.

## Why not black-run width only

The early tempting model was:

```text
black run widths -> signature
```

That was too fragile.

Observed barcodes are better treated as an alternating black/white signal:

```text
black run + white gap + black run + white gap ...
```

The important shift:

```text
black bars are evidence
white gaps are evidence too
```

A wider black region behaves like repeated `1` cells. A wider white region behaves like repeated `0` cells.

This makes the barcode a bounded binary strip, not merely a list of visible black sticks.

## Grid13 decoding overview

The intended decoder flow is:

1. Receive a barcode crop or ROI.
2. Convert image data into a 1D ink signal.
3. Detect black/ink runs and white gaps.
4. Find the active span from the left boundary black region to the right boundary black region.
5. Divide the active span into 13 equal cells.
6. Threshold each cell into `1` for ink / black, or `0` for no ink / white.
7. Produce a 13-bit candidate.
8. Validate or normalize the four sentinel/control cells.
9. Encode the candidate as a compact hex token.
10. Return raw signatures with orientation-family prefixes.

The raw run evidence remains valuable for debugging, but identity is the normalized 13-cell occupancy pattern.

## Bit numbering

The 13 visual cells are numbered left-to-right for visual reasoning:

```text
cell0 cell1 ... cell12
```

For integer/hex encoding, the rightmost cell is the least significant bit:

```text
leftmost visual cell  -> bit12
rightmost visual cell -> bit0
```

So the 13-bit value is packed into a 16-bit integer with the top three bits unused.

Example:

```text
grid13FwdBits = 1010101001001
grid13FwdHex  = 1549
rawSignature  = bfm1549
```

## Sentinel / control-bit structure

A valid Grid13 barcode is expected to have this outer structure:

```text
10.........01
```

That means:

* `bit12 = 1` — left boundary black/control cell
* `bit11 = 0` — white gap after left boundary
* `bit1  = 0` — white gap before right boundary
* `bit0  = 1` — right boundary black/control cell

Numeric mask:

```kotlin
(value and 0x1803) == 0x1001
```

The leftmost and rightmost black cells act as both visual boundaries and width/calibration evidence for the barcode strip.

## Control-bit normalization policy

ITROBOC intentionally uses control-bit normalization.

If the measured Grid13 candidate is close enough to be useful, but its four outer control cells are malformed, the decoder may normalize those four cells to the expected sentinel structure:

```text
10.........01
```

This is the “proper sandwich” policy: if the meal looks incomplete but the detected barcode span is otherwise reliable, normalize the bread/sentinel cells and preserve the middle payload.

However, normalization must be reported as evidence, not hidden:

```text
sentinelRepairApplied = true
warnings += ...
```

The preview/rendering side must show actual token bits and warnings where relevant; it must not silently make invalid tokens look valid.

## Grid13 procedure

A diagnostic implementation may expose the following stages:

```text
image crop
-> ink/luma projection
-> active span
-> black runs and white gaps
-> 13-cell sampling
-> pre-sentinel bit candidate
-> sentinel validation / repair
-> final 13-bit value
-> raw signature(s)
```

A fast TD implementation may skip some diagnostic objects, but should preserve the same meaning:

```text
visible runs are evidence
normalized cells are identity
```

## Raw signature format

Aliases use compact tokens:

```text
bfmHHHH
brmHHHH
```

Where:

* `bfm` = forward orientation family / beetle forward meal
* `brm` = reverse orientation family / beetle reverse meal
* `HHHH` = exactly four uppercase hex digits, representing the 13-bit occupancy value left-padded to 16 bits

The beetle names are friendly internal nicknames. Technically, `bfm` and `brm` are compact signature-model prefixes.

The prefix is part of the identity.

## Forward and reverse signatures

Both `bfm` and `brm` are valid raw-signature families. Do not reduce them to a single canonical value.

Do not do this:

```text
canonical = min(forwardBits, reverseBits)
```

Reverse-canonicalization reduced uniqueness in analysis and can collapse physically meaningful differences.

## Why both `bfm` and `brm` are needed

Physical cards have two meaningful ends. A card can be inspected from one end or from the opposite rotated end.

Some cards form criss-cross pairs where one card's forward payload is another card's reverse payload. This is safe only if the prefix is preserved.

Safe:

```text
Card A: bfmX / brmY
Card B: bfmY / brmX
```

Unsafe:

```text
canonical(X, Y) -> both cards collide
```

Orientation is data.

## Deck Profile boundary

Core Deck Profiles store raw signatures as opaque strings:

```text
Map<RawSignature, CardId>
```

Core does not parse barcode pixels and does not infer card meaning from `bfm...` or `brm...`.

Deck Profile metadata can declare the signature model:

```text
signatureModel = "grid13-v2"
```

Core invariant:

> One full raw signature maps to at most one CardId.

The prefix is part of the raw signature, so these are distinct:

```text
bfm1549
brm1549
```

Current profile model notes:

* Custom calibration profiles default to `grid13-v2`.
* The default built-in profile is currently `builtin-observed-v1`.
* `builtin-observed-v1` contains visually verified `bfm...` and `brm...` aliases for all 52 cards.
* The built-in demo profile is explicitly synthetic and is not a real physical deck mapping.

## Live calibration vs curated built-in profile

Live Admin calibration should not automatically invent a reverse alias from one observed scan.

If the camera observes `bfm12A5`, the app should assign that observed raw signature only, unless the user or a curated import explicitly provides the opposite orientation alias.

The built-in observed profile is different: it may contain both `bfm` and `brm` aliases if both were physically verified or curated from trusted evidence.

Current rule:

```text
live scan stores observed rawSignature only
curated/built-in profile may store both orientation-family aliases
```

This avoids turning one observation into an unverified claim about the opposite physical orientation.

## Current Admin::Edit flow

Admin::Edit currently uses Grid13 for one-frame calibration scans:

```text
CameraX frame
-> centered guide ROI
-> luma GrayImage
-> Grid13SlowDecoder
-> BarcodeDecodeResult
-> DeckProfileEditor.assign(rawSignature, selectedCard)
```

Behavior:

* `Found` assigns the observed raw signature to the selected card.
* `NotFound` does not mutate profile state.
* `Ambiguous` does not mutate profile state.
* conflicts are reported, not silently overwritten.
* aliases are shown as compact Admin chips.

Admin may explain. TD mode should remain verdict-oriented.

## Debug evidence / fields

The decoder and Admin debug logs should retain lower-level evidence where available:

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
grid13FwdBitsPreSentinel
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
sentinelRepairApplied
```

Run-length strings such as `B1-W1-B2-W2-B1` remain useful for explaining why the Grid13 cells were inferred.

Important current placeholders:

* `roiAngleDeg` is `0.0` for the current rectangular guide crop.
* `scanlineAgreement` is currently `null`; multi-scanline agreement is not implemented yet.

## UI display

Clickable Admin alias chips should show compact tokens:

```text
bfm12A5
brm14A9
```

For barcode preview widgets, render the actual bits represented by the token. If the token violates the sentinel rule, do not hide that truth; show the barcode preview in a warning palette, such as soft pink / black, and optionally add a short warning label.

## Validation fixtures

Barcode sheet PNG fixtures live in:

```text
vision/src/test/resources/barcode-sheets/
```

The golden manifest lives at:

```text
vision/src/test/resources/barcode-sheets/grid13-v2-golden.json
```

The test fixture purpose is not just “can one barcode be decoded?” The important deck-level question is:

```text
do all 52 cards produce distinct stable signatures?
```

## Recommended tests

Core/profile tests:

1. Every built-in observed alias matches `^(bfm|brm)[0-9A-F]{4}$`.
2. Every built-in observed alias passes the sentinel mask after intended normalization.
3. Every full alias token maps to exactly one `CardId`.
4. `bfm` and `brm` are treated as distinct raw-signature families.
5. Known criss-cross pairs remain distinct.
6. No forward/reverse canonicalization collapses distinct cards.
7. Custom profile JSON preserves signature metadata and aliases.

Vision tests:

1. Golden manifest has 52-card coverage.
2. Each fixture card decodes to the expected Grid13 token.
3. Run evidence is retained for diagnostics.
4. Degradation tests report shifts, blur, exposure, JPEG, skew, and red-suit channel comparison where available.
5. Sentinel repair reports warnings/evidence rather than hiding repair.
6. Fast decoder and slow diagnostic decoder agree on supported fixture cases.

Admin/UI tests:

1. Alias chips display `bfm...` / `brm...` compactly.
2. Read-only preview renders actual token bits.
3. Sentinel-invalid tokens render with warning styling.
4. Live calibration stores observed raw signature only.
5. Built-in/curated profiles may contain verified opposite-orientation aliases.

## Known limitations

Known seams:

* Live Android scan currently uses luma-only `GrayImage`, while static research prefers `ink = 255 - min(R,G,B)`.
* Mild skew is unstable in degradation tests and needs later ROI/rectification work.
* `scanlineAgreement` is not implemented yet.
* `roiAngleDeg` is a placeholder.
* TD multi-card scanning is not implemented yet.
* Real profile persistence/import/export is not implemented yet.
* The C8 static analyzer mismatch was a historical artifact of early pixel-width clustering; the current Grid13 model is collision-free on the 52-card set.
* The decoder should not auto-store reverse aliases during live calibration, but the built-in observed profile may contain both `bfm` and `brm` if they were physically verified.

## Historical note

Earlier drafts mentioned a `C8` mismatch. That was a historical transcription/modeling scar. It is no longer a current known limitation.

## Next likely work

Good next seams after this documentation pass:

* improve live ROI extraction and orientation/rectification;
* add multi-scanline measurement and fill `scanlineAgreement`;
* investigate luma versus RGB/YUV ink extraction on real red-suit scans;
* improve Admin profile persistence/import/export;
* later, adapt Grid13 from Admin calibration into TD multi-card scan workflow.
