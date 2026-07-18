# Grid13-v2 Barcode Model

This note is the durable implementation handoff for ITROBOC's current barcode signature model.

Related deeper docs:

* `docs/dev_history/vision/grid13_design.md` — original design and rationale.
* `docs/dev_history/vision/grid13_v2_barcode_model.md` — canonical active v2 design spec.
* `docs/dev_history/vision/barcodes/deep-research-report.md` — source research table and algorithm notes.
* `docs/dev_history/admin-edit/Admin_EditProfile_design.md` — Admin::Edit calibration workflow and debug-log behavior.

## Summary

`grid13-v2` is ITROBOC's current practical raw-signature model for one cropped barcode strip.

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

## Status

This document describes the current working barcode model for ITROBOC.

`grid13-v2` is the active raw-signature model for observed physical barcode marks on cards.

Core principle:

> Visible runs are evidence; Grid13 cells are identity; `bfm`/`brm` prefixes preserve orientation.

The scanner outputs raw signatures. Deck Profiles map those raw signatures to semantic card IDs such as `SA`, `DQ`, or `C7`.

The decoder must not directly know card meaning.

## Product context

ITROBOC reads barcode-like markings printed on physical bridge playing cards. The app is designed around this separation:

```text
camera crop -> barcode decoder -> raw signature -> Deck Profile -> CardId -> bridge hand / PBN
```

The raw signature should be stable, compact, and suitable for display as a clickable Admin alias chip.

## Grid13 decoding overview

The intended decoder flow is:

1. Receive a barcode crop or ROI.
2. Convert image data into a 1D ink signal.
3. Detect black/ink runs and white gaps.
4. Find the active span from the left boundary black region to the right boundary black region.
5. Divide the active span into 13 equal cells.
6. Threshold each cell into `1` for ink / black, or `0` for no ink / white.
7. Apply Grid13 control-bit normalization.
8. Encode the 13-bit payload as a compact alias token.

Example:

```text
bits: 1001010100101
hex:  12A5
alias: bfm12A5
```

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

## Sentinel / control-bit structure

A valid Grid13 barcode is expected to have this outer structure:

```text
10.........01
```

That means:

- `bit12 = 1` — left boundary black/control cell
- `bit11 = 0` — white gap after left boundary
- `bit1  = 0` — white gap before right boundary
- `bit0  = 1` — right boundary black/control cell

Numeric mask:

```kotlin
(value and 0x1803) == 0x1001
```

## Control-bit normalization policy

ITROBOC intentionally uses control-bit normalization.

If the measured Grid13 candidate is close enough to be useful, but its four outer control cells are malformed, the decoder may normalize those four cells to the expected sentinel structure:

```text
10.........01
```

This is the “proper sandwich” policy: if the meal looks incomplete but the detected barcode evidence is otherwise coherent, make a proper sandwich and keep the warning.

This must not hide evidence. The decoder/debug model should retain:

- pre-normalization bits
- post-normalization bits
- whether sentinel repair was applied
- why repair was applied
- warnings / confidence

Recommended debug fields:

```text
grid13FwdBitsPreSentinel
grid13FwdBits
grid13RevBits
sentinelValid
sentinelIssues
sentinelRepairApplied
sentinelRepairReason
warnings
confidence
```

## Raw signature format

Aliases use compact tokens:

```text
bfmHHHH
brmHHHH
```

Where:

- `bfm` = forward orientation family
- `brm` = reverse orientation family
- `HHHH` = exactly four uppercase hex digits

Internal nickname:

- `bfm` = beetle forward meal
- `brm` = beetle reverse meal

The nickname is playful, but the technical meaning is serious: the prefix preserves orientation.

## Forward and reverse signatures

Both `bfm` and `brm` are valid raw-signature families. Do not reduce them to a single canonical value.

Do not do this:

```text
canonical = min(forwardBits, reverseBitsSlow)
```

The prefix is part of the identity.

## Why both `bfm` and `brm` are needed

Physical cards have two meaningful ends. A card can be inspected from one end or from the opposite rotated end.

Some cards form criss-cross pairs where one card’s forward payload is another card’s reverse payload. This is safe only if the prefix is preserved.

Safe:

```text
Card A: bfmX / brmY
Card B: bfmY / brmX
```

Unsafe:

```text
canonical(X, Y) -> both cards
```

Therefore:

> Preserve orientation explicitly. Never erase it.

## Deck Profile policy

A Deck Profile may contain both `bfm...` and `brm...` aliases for one card, as long as they are explicitly stored as separate raw signatures.

Core invariant:

> One full raw signature maps to at most one CardId.

The prefix is part of the raw signature, so these are distinct:

```text
bfm1549
brm1549
```

## Live calibration vs curated built-in profile

Live Admin calibration should not automatically invent a reverse alias from one observed scan.

If the camera observes `bfm12A5`, the app should assign that observed raw signature only, unless the user or a curated import explicitly provides the opposite orientation alias.

The built-in observed profile is different: it may contain both `bfm` and `brm` aliases because those aliases are curated as known physical-orientation evidence.

```text
Live scan: assign what was actually observed.
Curated built-in profile: may contain both bfm and brm.
```

## Debug evidence

The decoder should retain lower-level evidence:

```text
blackRunsPx
whiteGapsPx
rl2
activeSpanPx
grid13FwdBitsPreSentinel
grid13FwdBits
grid13RevBits
rawSignature
reverseSignature
sentinelRepairApplied
confidence
warnings
```

`rl2` is a readable run-length form derived from the normalized Grid13 bit string, not the raw pixel widths.

Example:

```text
bits  = 1001010100101
rl2   = B1 W2 B1 W1 B1 W1 B1 W2 B1 W1 B1
token = bfm12A5
```

Pixel measurements should remain available for diagnostics, but they are evidence, not profile identity.

## Golden manifest

The observed physical profile has a golden manifest with all 52 cards and expected decoder evidence.

The golden manifest should protect:

- 52 unique forward signatures
- 52 reverse/orientation signatures where applicable
- sentinel-valid normalized tokens
- collision-free profile aliases
- stable `rl2` derived from normalized bits
- lower-level run evidence for debugging

If decoder math changes, update the manifest only after verifying the new outputs are intended and still collision-free.

## Decoder obligations

A Grid13-v2 decoder must:

1. Preserve orientation as `bfm` / `brm` families.
2. Keep visible run evidence for debug.
3. Store normalized 13-cell tokens as primary alias identity.
4. Retain pre-normalization evidence when sentinel repair happens.
5. Reject malformed candidates that are too far from the expected structure.
6. Avoid mapping raw signatures directly to semantic card IDs.

## Strict gate

The current practical strict gate rejects implausible Grid13 occupancy candidates before returning a found verdict:

- black run length >= 3 is rejected;
- white run length >= 4 is rejected.

This gate applies to normalized/evaluated 13-cell candidates, not directly to raw pixel runs.

The observed physical 52-card manifest fits within:

```text
B1..B2
W1..W3
```

## Read-only preview policy

Admin read-only preview should render the actual token bits for aliases.

It must not silently repair or reinterpret invalid tokens during preview.

If a profile contains a sentinel-invalid token, preview should show that faithfully and warn rather than creating a prettier barcode that is not actually in the profile.

## Tests to keep green

Useful test families:

- value-to-bits / bits-to-value conversion
- sentinel mask validation
- sentinel normalization warning behavior
- raw signature formatting/parsing
- bfm/brm distinction
- Grid13 strict run gate
- golden manifest uniqueness
- Admin read-only preview using actual token bits
- profile mapping preserves multiple aliases per card

## Non-goals

Grid13-v2 is not:

- a generic barcode standard decoder;
- an OCR system;
- a semantic card classifier;
- a guarantee that all printed decks share one profile;
- a reason to remove Admin calibration.

The deck profile layer remains essential.
