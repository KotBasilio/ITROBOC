# ITROBOC `grid13-v1` Tickets for Bob/Codex

Status: proposed implementation sequence  
Related design doc: `docs/grid13_design.md`  
Goal: move barcode decoding from fragile width-only bars to compact `grid13-v1` raw signatures: `bfmHHHH` / `brmHHHH`.

## Progress

- Ticket 1: Done in `f47a4dd` — signature formatting helpers.
- Ticket 2: Done in `428173c` — sheet resources and analyzer skeleton.
- Fixture cleanup: Done in `5709062` — canonical PNG location is `vision/src/test/resources/barcode-sheets/`.
- Ticket 3a: Done in `0516c89` — measurement primitives: ink channel, projection, thresholding, runs, active span.
- Ticket 3b: Done in `deab98d` — compose primitives into static barcode measurements with RL2/Grid13 output.
- Ticket 4: Done in `2e919a1` — golden manifest from `docs/barcode-sheets/deep-research-report.md`; analyzer-to-golden tuning remains explicit validation work.
  The historical `C8` mismatch (from early pixel-width clustering) is resolved; the current Grid13 model is collision-free.
- Ticket 5: Done in `63fe0d7` — degradation tests with report output at `vision/build/reports/grid13-degradation-report.txt`.
  Current report shows mild skew is unstable and needs later ROI/rectification work before live TD scanning.
- Ticket 6: Done — Admin::Edit live scan integration now uses the `grid13-v1` decoder and emits `bfmHHHH` signatures with reverse token, bits, RL2, run, span, confidence, and warning evidence in debug output.
  Current live camera path still derives ink from luma-only `GrayImage`; min-RGB/YUV-aware extraction remains a later seam if red-suit scans prove unstable.
- Ticket 7: Done — Admin::Edit alias chips stay compact and open a details dialog with retained session scan evidence plus a remove action.
- Ticket 8: Done — Deck Profile metadata now carries a signature model; custom calibration profiles default to `grid13-v1`, while the built-in demo profile is explicitly marked as synthetic.
- Ticket 9: Done — Admin::Edit JSONL logs now include compact signatures plus richer Grid13 evidence, ROI/crop fields, threshold fields, ambiguity flag, scanline-agreement placeholder, and deck-profile match count.
- Ticket 10: Done — final Grid13 implementation handoff lives in `md-files/grid13-v1-barcode-model.md`.

## Ticket 1 — Add `grid13-v1` signature formatting helpers

### Goal

Create small pure Kotlin helpers for converting 13-bit Grid13 strings into compact raw signature tokens.

### Scope

Module: `:vision` preferred. If Bob thinks token formatting belongs elsewhere, keep it pure and Android-free.

### Requirements

Implement helpers equivalent to:

```kotlin
fun grid13BitsToHex(bits13: String): String
fun forwardMealSignature(bits13: String): String   // "bfm" + hex
fun reverseMealSignature(bits13: String): String   // "brm" + hex
fun reverseBits(bits13: String): String
```

Rules:

- input bits must be exactly 13 chars;
- only `0` and `1` allowed;
- left-pad to 16 bits before hex conversion;
- output hex must be exactly four uppercase hex digits;
- `bfm` means forward Grid13 token, internally nicknamed “beetle forward meal”;
- `brm` means reverse Grid13 token, internally nicknamed “beetle reverse meal.”

### Tests

Add tests for:

- valid 13-bit inputs;
- invalid length;
- invalid characters;
- leading zero preservation in 4-digit hex;
- reverse-bit conversion;
- no canonical min/max behavior.

### Acceptance

- Helpers exist and are covered by tests.
- `bfm`/`brm` outputs are compact and deterministic.
- `./gradlew :vision:test` passes.

---

## Ticket 2 — Add barcode sheet resources and analyzer skeleton

### Goal

Put the five clean barcode sheets into `:vision` test resources and create a deterministic JVM-side analyzer entry point.

### Scope

Module: `:vision` only.

### Input resources

Add:

```text
vision/src/test/resources/barcode-sheets/
  Master.png
  spades.png
  hearts.png
  diamonds.png
  clubs.png
```

### Requirements

Create a test/helper that can:

- load the PNG resources;
- identify or manually slice the 13 barcode rows per suit sheet;
- label rows in rank order: `A K Q J T 9 8 7 6 5 4 3 2`;
- associate suit sheets with suit labels: `S H D C`;
- produce 52 labeled crop measurements.

Manual row slicing is acceptable for this ticket if it is stable and documented. Automatic segmentation can be improved later.

### Tests

- each suit sheet yields exactly 13 barcode crops;
- suit symbol at bottom is not included as a barcode crop;
- all 52 card IDs are represented exactly once.

### Acceptance

- Analyzer skeleton can enumerate 52 labeled barcode crops.
- No Android dependency is introduced.
- `./gradlew :vision:test` passes.

---

## Ticket 3 — Implement static-image barcode measurement pipeline

### Goal

For each clean sheet crop, compute black/white run evidence and Grid13 signatures.

### Scope

Module: `:vision` only.

### Requirements

Implement pure functions for:

```kotlin
ink = 255 - min(R, G, B)
project crop to 1D signal
threshold 1D signal
extract black runs
extract internal white gaps
compute active span from first black edge to last black edge
compute RL2 debug signature
compute Grid13 forward/reverse bits
compute bfm/brm tokens
```

The exact API can differ, but the output must include:

```text
cardId
blackRunsPx
whiteGapsPx
blackRunEdges
blackRunCentersPx
activeStartX
activeEndX
activeSpanPx
rl2
grid13FwdBits
grid13RevBits
grid13FwdHex
grid13RevHex
rawSignature        // bfmHHHH
reverseSignature    // brmHHHH
confidence
warnings
```

### Important design notes

- RL2 is debug/explanation, not the sole production signature.
- Grid13 forward is the proposed production identity.
- Do not canonicalize forward/reverse.
- Do not bake CardId semantics into the decoder.

### Acceptance

- Analyzer produces a readable report/table for all 52 clean sheet crops.
- Report includes RL2 and Grid13 values.
- `./gradlew :vision:test` passes.

---

## Ticket 4 — Generate and validate a golden manifest

### Goal

Lock down the clean-sheet `grid13-v1` behavior with a golden manifest.

### Scope

Module: `:vision` test resources.

### Requirements

Generate a manifest such as:

```text
vision/src/test/resources/barcode-sheets/grid13-v1-golden.json
```

Suggested row fields:

```json
{
  "cardId": "SA",
  "rawSignature": "bfm1549",
  "reverseSignature": "brm1295",
  "grid13FwdBits": "1010101001001",
  "grid13RevBits": "1001001010101",
  "rl2": "B1-W1-B1-W1-B2-W1-B2-W2-B2-W2-B1",
  "blackRunsPx": [3,4,5,5,5,3],
  "whiteGapsPx": [4,4,3,7,8],
  "activeSpanPx": 53
}
```

Values above are illustrative; use Bob’s analyzer output.

### Tests

- all 52 `rawSignature` values are unique;
- all `rawSignature` values match `^bfm[0-9A-F]{4}$`;
- all `reverseSignature` values match `^brm[0-9A-F]{4}$`;
- forward/reverse canonicalization is explicitly not used;
- historical two-class pixel-width RL2 collision `D2` / `C8` is documented if reproduced.

### Acceptance

- Golden manifest is committed.
- Tests validate 52/52 forward uniqueness.
- Tests fail if someone accidentally canonicalizes orientation.
- `./gradlew :vision:test` passes.

---

## Ticket 5 — Add degradation tests for `grid13-v1`

### Goal

Check whether Grid13 remains stable under realistic camera imperfections.

### Scope

Module: `:vision` tests.

### Degradations to test

At minimum:

- small horizontal crop shifts;
- small vertical crop shifts;
- mild blur;
- exposure brightening/darkening;
- JPEG compression artifacts;
- mild perspective/skew if easy;
- red-suit comparison using min-RGB ink versus plain luminance.

### Requirements

For each degradation class, report:

- stability rate;
- confidence changes;
- common failure modes.

Do not overfit thresholds silently. If degraded scans fail, keep the report honest.

### Acceptance

- Degradation tests exist.
- Failures are visible and diagnostic.
- No Android dependency is introduced.
- `./gradlew :vision:test` passes, or intentionally unstable tests are marked/report-only rather than blocking.

---

## Ticket 6 — Update live camera decoder to emit `grid13-v1` measurements

### Goal

Replace or augment the current fragile width-pattern decoder with the new Grid13 measurement output.

### Scope

Modules: `:vision` and `:app` integration.

### Requirements

For each Admin::Edit scan, decoder should produce:

```text
rawSignature = bfmHHHH
reverseSignature = brmHHHH
grid13FwdBits
grid13RevBits
rl2
blackRunsPx
whiteGapsPx
activeSpanPx
confidence
warnings
```

Existing blackRuns logging should remain.

### Behavior

- Use `rawSignature` / `bfm...` as the normal assignable alias.
- Do not automatically add `reverseSignature` as an alias.
- If confidence is low, show Ambiguous/low-confidence state rather than silently assigning.
- Preserve selected-card Admin calibration flow.

### Acceptance

- Same-card repeated scans produce stable `bfm...` in normal orientation.
- Debug log contains both compact token and detailed evidence.
- `./gradlew :vision:test`, `./gradlew :core:test`, and `./gradlew :app:assembleDebug` pass.

---

## Ticket 7 — Update Admin::Edit alias chips and details

### Goal

Keep visible aliases compact while making debug details accessible.

### Scope

Module: `:app`.

### Requirements

Normal alias chips should display compact strings such as:

```text
bfm1549
```

If an alias chip is clicked, show or log details such as:

```text
Model: grid13-v1
Forward bits: 1010101001001
Reverse token: brm1295
RL2: B1-W1-B1-W1-B2-W2-B1
Black runs: [3,4,5,5,5,3]
White gaps: [4,4,3,7,8]
Confidence: 0.93
```

Keep current remove-alias confirmation behavior.

### Acceptance

- Chips remain short and readable.
- Clicking an alias still works.
- Debug details are available without polluting the main UI.
- `./gradlew :app:assembleDebug` passes.

---

## Ticket 8 — Add Deck Profile metadata for signature model

### Goal

Profiles should declare which raw-signature model they use.

### Scope

Module: `:core`, with app usage as needed.

### Requirements

Add or use existing metadata field equivalent to:

```text
signatureModel = "grid13-v1"
```

Rules:

- built-in demo profile may remain synthetic and should not be broken;
- real calibrated profiles should use `grid13-v1`;
- raw signature strings remain opaque to core mapping logic;
- no duplicate raw signature may map to multiple cards.

### Acceptance

- Profile metadata can carry `grid13-v1`.
- Conflict checks still work.
- Existing core tests pass.
- `./gradlew :core:test` passes.

---

## Ticket 9 — Update debug JSONL export

### Goal

Make scan logs useful for future Selyn/Architect/Bob analysis.

### Scope

Module: `:app`, using fields from `:vision`.

### Required fields

Add or preserve:

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

### Acceptance

- JSONL contains compact signature and rich evidence.
- Old `blackRuns` field is preserved if possible.
- Log export still works from Admin::Edit.
- `./gradlew :app:assembleDebug` passes.

---

## Ticket 10 — Write/update project docs

### Goal

Document `grid13-v1` clearly enough that future Bob/Selyn/Architect can resume without rediscovering the model.

### Scope

Docs only.

### Requirements

Add a doc such as:

```text
md-files/grid13-v1-barcode-model.md
```

Include:

- why black-bar-only decoding was abandoned;
- Grid13 model;
- `bfmHHHH` / `brmHHHH` raw signature format;
- the “beetle forward meal” / “beetle reverse meal” nickname;
- why orientation must not be canonicalized;
- why Deck Profile owns semantics;
- debug field list;
- known limitations.

### Acceptance

- Document exists.
- It is technical enough for implementation.
- It keeps playful nicknames but does not require them to understand the system.

---

## Recommended implementation order

1. Ticket 1 — signature formatting helpers.
2. Ticket 2 — sheet resources and analyzer skeleton.
3. Ticket 3 — static measurement pipeline.
4. Ticket 4 — golden manifest and uniqueness tests.
5. Ticket 5 — degradation tests.
6. Ticket 6 — live camera decoder integration.
7. Ticket 9 — debug JSONL expansion.
8. Ticket 7 — Admin alias chip details.
9. Ticket 8 — Deck Profile metadata.
10. Ticket 10 — final docs.

Bob may merge small neighboring tickets if convenient, but should avoid a single heroic commit.

## Test commands

Run as appropriate:

```bash
./gradlew :vision:test
./gradlew :core:test
./gradlew :app:assembleDebug
```

## Selyn seam-pins

- Do not store only RL2; it has an observed collision.
- Do not canonicalize forward/reverse orientation.
- Do not automatically add reverse aliases.
- Do not teach `:core` barcode semantics.
- Do not claim `grid13-v1` is the official manufacturer encoding.
- Keep the UI alias chip compact: `bfmHHHH`, not long binary strings.
