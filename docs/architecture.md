# ITROBOC Architecture

## Scope of the current milestone

The current milestone builds a pure Kotlin/JVM core that models bridge cards, hand and board state, deck-profile lookup, TD scan accumulation, batch scan summaries, and basic PBN export. It also includes a minimal Android shell that exercises the core with fake signatures and displays TD-facing summaries. Camera capture, barcode image processing, and device-specific scanning integration are still intentionally excluded.

## Admin side

The administrative workflow will define and maintain deck profiles. A deck profile maps an observed raw barcode signature to a canonical bridge card identifier such as `SA` or `D8`. In the future, calibration tools will let an operator scan a known physical deck, inspect the raw signatures, resolve ambiguous observations, and save the signature-to-card mapping used by tournament decks.

For now, the project includes a built-in demo/reference deck profile with opaque synthetic signatures plus explicit metadata such as profile ID, display name, built-in/demo flags, and notes. That profile is intentionally fake and exists only to support tests and the Android shell without pretending to know any real WinDup/Jannersten barcode mapping.

## TD side

The tournament-director workflow will guide a user through building hands from cumulative scans. As card observations arrive, the system will:

- translate raw signatures through the active deck profile
- accumulate cards into hand and board state
- process batches of observed signatures from one scan action
- reject duplicates and incomplete states
- surface validation issues clearly
- provide summary counts and hand progress for future UI
- export a complete board as PBN when all 52 cards are known

The current Android shell already exercises this flow with fake-signature input, seat selection, TD-friendly summaries, grouped hand display, board progress, gated PBN preview, and visible active-profile labeling. That shell is intentionally a thin adapter over the core, not a second implementation of scan logic. Its default profile comes from the core built-in demo/reference mapping.

## Future pipeline

The planned end-to-end pipeline is:

`camera frame -> barcode ROI -> raw signature -> deck profile lookup -> card ID -> hand/board state -> PBN`

The current project starts at the `raw signature -> card ID` stage, supports ordered batch accumulation for one scan action, exposes TD-friendly presentation summaries, and carries the domain model through `PBN`. The Android shell currently plugs in at the `raw signature list -> core scan flow` boundary using fake signatures only.
