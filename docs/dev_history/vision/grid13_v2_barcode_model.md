# ITROBOC Grid13-v2 Barcode Model

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
canonical = min(forwardBits, reverseBits)
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

Run-length strings such as `B1-W1-B2-W2-B1` remain useful for explaining why the Grid13 cells were inferred.

## UI display

Clickable Admin alias chips should show compact tokens:

```text
bfm12A5
brm14A9
```

For barcode preview widgets, render the actual bits represented by the token. If the token violates the sentinel rule, do not hide that truth; show the barcode preview in a warning palette, such as soft pink / black, and optionally add a short warning label.

## Historical note

Earlier drafts mentioned a `C8` mismatch. That was a historical transcription/modeling scar. It is no longer a current known limitation.

## Recommended tests

Core/profile tests:

1. Every built-in observed alias matches `^(bfm|brm)[0-9A-F]{4}$`.
2. Every built-in observed alias passes the sentinel mask after intended normalization.
3. Every full alias token maps to exactly one CardId.
4. `bfm` and `brm` are treated as distinct raw-signature families.
5. Known criss-cross pairs remain distinct.
6. No forward/reverse canonicalization is used for DeckProfile lookup.

Vision tests:

1. Decoder emits compact `bfmHHHH` signatures.
2. Decoder retains pre-sentinel and post-sentinel bits in debug.
3. Sentinel repair is visible in debug when applied.
4. Invalid/noisy candidates are not silently treated as clean.
5. Repeated scans of the same barcode produce stable normalized signatures.

UI/manual checks:

1. Alias chips remain compact.
2. Admin read-only preview displays both orientation channels clearly.
3. Sentinel-invalid preview tokens are rendered with a warning palette, not hidden.
