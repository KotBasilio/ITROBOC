# ITROBOC Product Context

Last aligned with source snapshot: `d7318ca`.

ITROBOC means **Independent Tool for Reading Observed Barcodes On Cards**.

It is an Android-first assistant app for bridge Tournament Directors. The user is a bridge TD and technical organizer. The real-world bottleneck is manually converting physical dealt cards into digital bridge hand records.

The app goal is:

```text
physical barcode-marked playing cards
-> decoded card IDs
-> validated bridge hands/board
-> PBN export
```

## Real-world workflow

The TD needs to build bridge hand data from real physical cards. The cards have machine-readable barcode-like markings. The app helps the TD scan cards using a phone/tablet camera.

Current TD-side workflow is guided and cumulative:

1. Open TD overview.
2. Choose a board.
3. Select a seat: North, East, South, or West.
4. Show cards/barcodes to the camera in stream mode.
5. The app reads the barcode ROI and extracts a raw Grid13 signature.
6. The active Deck Profile maps the raw signature to a canonical `CardId`.
7. The selected hand accumulates unique cards.
8. Unknown signatures are reported but do not change board state.
9. Duplicates already present in another hand are rejected and can be corrected with `I'm sure` when the previous occurrence was a false positive.
10. Human/scan mistakes can be recovered with `Undo`, `Scissors`, `Swap`, and `Clear`.
11. When a board is complete, the central view shows a PBN preview.
12. TD overview can export all complete boards as cumulative PBN.

The app does not need one perfect scan. It should support fast iterative convergence.

Success is not “decode every visible card perfectly in one frame.”

Success is:

```text
help the TD reach 13 correct cards per hand quickly and safely.
```

## Current product principles

```text
Admin = explanation; TD = verdict.
Wrong card is worse than missed card.
Scanner does not know card meaning.
Profiles map raw signatures to cards.
Orientation is data.
TD flow must reduce hand-burden and babysitting.
```

## Admin side

Admin defines and inspects Deck Profiles.

A Deck Profile maps observed raw barcode signatures to canonical cards, for example:

```text
bfm1549 -> SA
brm.... -> D8
```

Deck Profiles allow support for different physical deck types without changing app code.

Admin::Edit can calibrate profile entries from CameraX frames. It shows barcode evidence, Grid13 preview, aliases, and profile-edit controls.

Admin read-only mode provides visual inspection of profile/card mappings.

## TD side

TD uses an existing Deck Profile to build boards.

Current TD surfaces:

- TD overview with dynamic board grid, Import, Export, Settings;
- TD::EditBoard cockpit with four hands, camera area, orientation control, status, last scanned card, and recovery controls.

Current TD recovery controls:

- `Undo`: LIFO undo for currently selected hand;
- `Scissors`: full-screen selected-hand card removal;
- `Swap`: swap selected hand with another seat;
- `I'm sure`: move a rejected duplicate card from previous seat to selected seat;
- `Clear`: context-sensitive selected-hand clear or whole-board clear confirmation.

## PBN

PBN export is gated on complete boards: four 13-card hands and 52 unique cards.

Current MVP PBN export intentionally does not emit `[Site "..."]`.

TD overview cumulative export includes complete visible boards only, sorted by board number, and shares via Android share intent including `EXTRA_TEXT`.

TD import accepts complete PBN boards numbered `1..39`, ignores unsupported/partial blocks, and expands the visible board grid to fit valid imported boards.

## Current state

Grounded current state:

- app has Main, TD, Admin, and Mock entry surfaces;
- Admin::Edit can calibrate profiles from CameraX frames;
- `grid13-v2` preserves `bfm`/`brm` physical orientation;
- built-in observed profile has complete 52-card coverage;
- golden manifest follows observed profile and derives bits/run form consistently;
- fast verdict decoder uses row-major projection and logical-width scratch-buffer discipline;
- TD overview supports flexible board grid sizes 15..39 in 3-board increments;
- TD import expands grid and rejects unsupported board numbers;
- TD::EditBoard is live, with recovery controls for common field errors;
- board completion shows central PBN preview;
- cumulative PBN export/share works from TD overview.

## Development style

Keep core logic pure and testable.

Do not put Android UI, CameraX, or device-specific logic into the core package.

Keep tickets temporary. When a ticket is done, absorb only durable product/architecture truth into current docs.

Prefer current lean docs over historical ticket piles.
