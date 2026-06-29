# ITROBOC `bfm` / `brm` Orientation Design Update

## Summary

This document is an update for `bfm_brm_orientation_design.md`.

`bfm` and `brm` are orientation-bearing raw-signature families:

```text
bfmHHHH
brmHHHH
```

The prefix is part of the raw signature identity.

Internal nicknames:

- `bfm` = beetle forward meal
- `brm` = beetle reverse meal

Production rule:

> Preserve orientation. Do not canonicalize.

## Why orientation matters

Physical playing cards can be read from opposite ends. The bottom corner is a rotated counterpart of the top corner.

Because of this, a barcode observed from one end may produce the forward signature family, while observing the paired/opposite end can produce the reverse family.

This is not an error. It is part of the physical design space.

## Criss-cross pairs

Some cards may have criss-cross relationships:

```text
Card A: bfmX / brmY
Card B: bfmY / brmX
```

This is safe if and only if the full token is preserved.

Unsafe lookup key:

```text
canonical(X,Y)
```

Therefore, `bfm` and `brm` must not be stripped or merged.

## DeckProfile invariant

A DeckProfile maps raw signatures to CardIds.

The invariant remains:

> One full raw signature maps to at most one CardId.

These are distinct and legal:

```text
bfm1255 -> SA
brm1255 -> DT
```

Because `bfm1255` and `brm1255` are different raw signatures.

## Built-in observed profile

The built-in observed Grid13 profile may contain both aliases for each card:

```kotlin
addObserved(CardId(Suit.SPADES, Rank.ACE), "bfm1255", "brm1549")
```

This is acceptable because the built-in profile is curated evidence. It intentionally includes both physical-orientation channels.

Do not interpret this as a rule that any live scan should automatically add its reverse.

## Live Admin calibration

When Admin::Edit sees a barcode and the user assigns it to a selected card, the app should assign the actual observed raw signature.

It should not automatically invent the reverse alias.

If a user wants both aliases, they should be obtained by actual scan, import, curated profile, or explicit tool-assisted action.

## Read-only preview

Read-only Admin preview may show both `bfm` and `brm` visualizations for a card/profile entry.

This preview is an inspection tool; it must not mutate DeckProfileEditor state.

## Sentinel interaction

Both `bfm` and `brm` payloads should obey the Grid13 sentinel/control-bit shape:

```text
10.........01
```

Mask:

```kotlin
(value and 0x1803) == 0x1001
```

If a token fails this check, the UI should display it as suspicious. Do not hide or “pretty-fix” the preview. Suggested warning palette: soft pink / black.

## Implementation notes

- Keep prefix parsing strict.
- Keep hex uppercase and fixed-width.
- Keep `bfm` and `brm` as separate families.
- Never use `min(forward, reverse)` canonicalization for lookup.
- Add tests for duplicate-token rejection.
- Add tests for known criss-cross pairs.
- Add tests proving `bfmX` and `brmX` can map differently.
