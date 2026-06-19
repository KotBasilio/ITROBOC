# ITROBOC Architecture

## Scope of the current milestone

The current milestone builds a pure Kotlin/JVM core that models bridge cards, hand and board state, deck-profile lookup, and basic PBN export. It intentionally excludes Android UI, camera capture, barcode image processing, and device-specific integration.

## Admin side

The administrative workflow will define and maintain deck profiles. A deck profile maps an observed raw barcode signature to a canonical bridge card identifier such as `SA` or `D8`. In the future, calibration tools will let an operator scan a known physical deck, inspect the raw signatures, resolve ambiguous observations, and save the signature-to-card mapping used by tournament decks.

## TD side

The tournament-director workflow will guide a user through building hands from cumulative scans. As card observations arrive, the system will:

- translate raw signatures through the active deck profile
- accumulate cards into hand and board state
- reject duplicates and incomplete states
- surface validation issues clearly
- export a complete board as PBN when all 52 cards are known

## Future pipeline

The planned end-to-end pipeline is:

`camera frame -> barcode ROI -> raw signature -> deck profile lookup -> card ID -> hand/board state -> PBN`

The current core project starts at the `raw signature -> card ID` stage and carries the domain model through `PBN`.
