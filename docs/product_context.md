# ITROBOC Product Context

ITROBOC means Independent Tool for Reading Observed Barcodes On Cards.

It is an Android-first assistant app for bridge tournament directors. The user is a bridge TD and technical organizer. The real-world bottleneck is manually converting physical dealt cards into digital bridge hand records.

The app’s goal is:

physical barcode-marked playing cards -> decoded card IDs -> validated bridge hands/board -> PBN export

## Real-world workflow

The TD needs to build bridge hand data from real physical cards. Cards have machine-readable barcode-like markings. The app should help the TD scan cards using a phone camera.

The expected TD-side workflow is guided and cumulative:

1. Select a seat: North, East, South, or West.
2. Place cards in a recommended layout, likely several staggered columns with barcode areas visible.
3. Preview the camera frame.
4. Press a Scan button.
5. The app extracts all recognizable raw barcode signatures from that captured frame.
6. The core maps signatures to canonical card IDs using the active Deck Profile.
7. Only new unique cards are added to the selected hand.
8. Already-known cards are ignored safely.
9. Unknown signatures are reported but do not change state.
10. Cross-hand duplicates are reported as warnings/errors.
11. The TD adjusts cards/camera and scans again until the hand reaches 13 cards.
12. Repeat for all four seats.
13. Export the complete board as PBN.

The app does not need one perfect scan. It should support fast iterative convergence.

Success is not “decode every visible card perfectly in one frame.”
Success is “help the TD reach 13 correct cards per hand quickly and safely.”

## Architecture

The system has two conceptual sides.

### Admin side

The Admin side defines Deck Profiles.

A Deck Profile describes how observed raw barcode signatures map to canonical cards, for example:

raw signature -> card ID
sig-dq -> DQ
0x4242 -> DQ

Deck Profiles should allow future support for different physical deck types. If the club buys another deck with a different barcode scheme, the app should be able to learn or configure another profile rather than requiring app code changes.

Deck Profile metadata also records the raw-signature model, for example `grid13-v2` for calibrated camera profiles. Built-in demo profiles may use synthetic signature models and must not be mistaken for real physical deck mappings.

### TD side

The TD side uses an existing Deck Profile to build hands and boards.

The TD side should be simple, forgiving, and responsive. It should clearly show:

* current seat
* current hand count, e.g. 7/13
* cards already accumulated
* result of the latest scan
* unknown signatures
* duplicates inside the same hand
* duplicates already assigned to another seat
* when a hand is complete
* when the board is complete and ready for PBN export

## Core principles

Keep core logic pure and testable.

Do not put Android UI, camera, CameraX, OpenCV, or device-specific logic into the core package.

Core logic should model bridge-domain concepts explicitly:

* Suit
* Rank
* CardId
* Seat
* HandState
* BoardState
* DeckProfile
* scan result / scan batch result
* PBN export

Prefer typed results over booleans or strings. For example, scanning should distinguish:

* Added(card)
* UnknownSignature(signature)
* AlreadyInThisHand(card)
* AlreadyOnBoard(card, existingSeat)
* HandAlreadyComplete(seat)

Failed or ignored scans should not silently mutate state.

The raw signature should remain available in scan reports/logs where useful. This will help debug and refine Deck Profiles later.

## Development order

1. Pure Kotlin/JVM core and tests.
2. TD scan accumulator and batch scan logic.
3. Hand/board progress summaries for future UI.
4. Deck Profile persistence/import/export.
5. PBN export refinement.
6. Android app shell.
7. TD-side UI.
8. Camera frame capture.
9. Barcode region/signature extraction.
10. Admin calibration UI.

Camera and Android UI should wait until the core workflow is stable and tested.

## Current frontier

The earlier development order has now advanced through the first Android,
camera, Admin calibration, and Grid13 milestones.

Current grounded state:

* the app has Main, TD, Admin, and Mock entry surfaces;
* Admin::Edit can calibrate profiles from a CameraX frame;
* `grid13-v2` preserves `bfm`/`brm` physical orientation;
* the built-in observed profile has complete 52-card coverage;
* its orientation was visually checked against the physical deck;
* the golden manifest follows the observed profile and derives bits/run form
  consistently;
* Admin::Edit read-only mode provides a visual barcode inspection card.

The next major implementation frontier is `TD::EditBoard`: replacing the
current Board Scan placeholder with a real board/hand editing and scanning
workflow that consumes the active Deck Profile and existing pure-core scan
models.
