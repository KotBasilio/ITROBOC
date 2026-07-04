# ITROBOC Architecture

Last aligned with source snapshot: `d7318ca`.

Status: post-MVP. ITROBOC has survived first real tournament use, produced usable PBN from physical club cards, and now has TD-side recovery controls for common scan/human errors.

This document is written for future AI instances. It is a current architecture map, not a ticket list and not a historical diary. Completed tickets should not remain here unless they changed a durable invariant, ownership rule, or system boundary.

## 1. Product shape

ITROBOC means **Independent Tool for Reading Observed Barcodes On Cards**.

It is an Android-first bridge Tournament Director assistant:

```text
physical barcode-marked cards
-> camera/luma ROI
-> Grid13 raw signature
-> Deck Profile lookup
-> CardId
-> BoardState / BoardEditState
-> complete board validation
-> PBN export
```

The real-world user is a TD under tournament pressure. The app exists to reduce repetitive manual hand-record work, not to demonstrate barcode cleverness for its own sake.

Core product rule:

```text
The real center is the TD at a live tournament table.
```

## 2. Load-bearing maxims

These are architectural beams. Do not optimize against them accidentally.

```text
Admin = explanation; TD = verdict.
Wrong card is worse than missed card.
Scanner does not know card meaning.
Profiles map raw signatures to cards.
Orientation is data.
Visible runs are evidence; normalized Grid13 cells are identity.
TD flow must reduce hand-burden and babysitting.
A verdict must be visible.
Board complete means: stop scanning; trust the landing.
```

Interpretation:

- Admin may show diagnostics, calibration evidence, grids, aliases, raw signatures, and reasons.
- TD should show concise operational results: accepted card, skipped duplicate, unknown signature, board complete, export available.
- A missed scan costs another hand movement. A wrong accepted card corrupts trust and board state.
- Barcode/vision code emits signatures. Core/profile code assigns card meaning.
- `bfm` and `brm` are feed-direction families, not arbitrary min/max canonicalization.

## 3. Module ownership

### `:core`

Pure Kotlin/JVM domain and reducer layer.

Owns:

- bridge domain: `Suit`, `Rank`, `CardId`, `Seat`
- board domain: `HandState`, `BoardState`, `BoardEditState`
- duplicate metadata: `DuplicateBoardMetadata`, `BoardVulnerability`
- deck profiles: `DeckProfile`, `DeckProfileEditor`, `BuiltInDeckProfiles`
- TD edit reducer: `EditBoardReducer`
- PBN export: `PbnExporter`, `PbnExportOptions`
- pure summaries: `HandProgressSummary`, `BoardProgressSummary`, `TdScanSessionPresentation`

Must not own:

- Android UI
- CameraX
- Compose
- `ImageProxy`
- Android share/import APIs
- visual diagnostics

### `:vision`

Android-free barcode image interpretation.

Owns:

- `GrayImage`
- `BarcodeDecoder`
- decode result models
- Grid13 measurement primitives
- slow diagnostic Grid13 decoder
- fast verdict Grid13 decoder
- golden manifest tests for observed barcode profile

Must not own:

- bridge card meaning
- deck profile lookup
- Android camera APIs
- TD state

### `:app`

Android shell, Compose UI, CameraX adapters, session import/export/share.

Owns:

- screens: Main, TD overview, TD edit board, Admin actions/edit/read-only, mock surfaces
- `AdminEditCameraSupport` and CameraX frame adapters
- `EditBoardController`
- `EditBoardScreen`
- `TdSessionState`
- `TdSessionExchange`
- `TdSessionShareManager`
- Android import/export/share intents

Must not move core domain rules into Compose branches.

### `Project/docs/**`

Human/AI handoff layer. Keeps current maps and durable design anchors.

Architecture carries beams. Design docs carry current behavior. Tickets carry temporary work. Tests carry executable evidence. Dev history carries meaningful road-dust only.

## 4. Core domain invariants

### Cards and hands

- A `CardId` is suit + rank.
- A `HandState` is a set of unique cards.
- A hand cannot exceed 13 cards.
- A `BoardState` contains all four seats.
- A card cannot appear twice on the board.
- A complete board has four complete 13-card hands and 52 unique cards.

### PBN

`PbnExporter.export(...)` requires complete board validation.

Current MVP PBN output intentionally emits:

```text
[Event "..."]
[Board "..."]       when board number is supplied
[Dealer "N|E|S|W"]
[Vulnerable "..."]  when vulnerability is supplied
[Deal "..."]
```

Current MVP PBN output intentionally does **not** emit `[Site "..."]`, even though `PbnExportOptions.site` still exists in the type.

Duplicate board metadata is provided by `DuplicateBoardMetadata.forBoardNumber(boardNumber)` and feeds dealer/vulnerability into TD export.

## 5. Deck Profile architecture

Deck Profiles are the translation boundary between raw observed signatures and card identities.

```text
raw signature -> CardId
```

Examples:

```text
bfm1549 -> SA
brm.... -> D8
```

`scanner/vision` code must not know that a signature means `SA` or `D8`.

A profile contains metadata such as profile id/name and signature model. The current observed physical profile is `grid13-v2`-style and preserves physical orientation through `bfm` / `brm` families.

Built-in observed profile has 52-card coverage and is aligned with a golden manifest.

## 6. Grid13 / barcode architecture

### Slow path

The slow Grid13 path is diagnostic/explanatory. It may allocate richer measurement/debug objects. It belongs mainly to Admin and tests.

Use it when the app needs to explain barcode evidence.

### Verdict path

`Grid13VerdictDecoder` is the fast TD path.

Current behavior:

```text
GrayImage
-> width-bound reusable projection buffer
-> row-major projection accumulation
-> active black span
-> 13-cell candidate
-> strict Grid13 cell-run gate
-> sentinel normalization
-> raw signature
-> confidence/verdict
```

Important implementation invariant:

```text
Reusable scratch buffers must be scanned only over the current logical image width.
```

After a wide frame then narrow frame, stale scratch tail data must not influence detection. Loops that analyze projection data should use `0 until image.width`; final run closure should use `commitRun(image.width)`.

### Strict cell-run gate

The strict gate applies to the **13-cell bit candidate**, not raw pixel runs:

```text
reject 13-cell candidates with black run length >= 3
reject 13-cell candidates with white run length >= 4
```

### Orientation

Orientation is part of the signature family:

```text
same visible payload + chosen feed direction = bfmHHHH or brmHHHH
```

Do not replace this with min/max canonicalization or blind bit reversal.

## 7. Admin architecture

Admin is the explanation side.

### Admin::Actions

Profile management surface:

- shows active/imported profiles;
- supports profile selection;
- imports/exports profile JSON;
- adds/deletes imported profiles;
- opens edit/read-only profile surfaces.

### Admin::Edit

Calibration and evidence workspace:

```text
CameraX ImageProxy
-> centered barcode ROI
-> luma GrayImage
-> slow Grid13 decode/evidence
-> signature assignment to selected card / orientation
-> DeckProfileEditor update
```

Admin::Edit can show diagnostic detail that TD should not show.

The CameraX adapter extracts only the ROI luma data. Dense `pixelStride == 1` paths use row bulk copy; non-unit pixel stride falls back to per-pixel extraction.

### Admin read-only / preview

Read-only profile inspection shows card/signature mapping and Grid13-style visual evidence. It is for understanding and verification, not TD operation.

## 8. TD session architecture

### `TdSessionState`

Current shape:

```kotlin
data class TdSessionState(
    val boards: Map<Int, BoardEditState> = emptyMap(),
    val totalBoardsInGrid: Int = 30,
)
```

Allowed grid sizes:

```text
15, 18, 21, 24, 27, 30, 33, 36, 39
```

`updateGridSize(newSize)` enforces:

```text
newSize in ALLOWED_GRID_SIZES
newSize >= highestNonEmptyBoardNumber
```

This prevents valid non-empty boards from becoming hidden state.

### TD overview

The TD overview shows a dynamic board grid with three rows and `totalBoardsInGrid / 3` columns.

It shows:

- board buttons for visible board numbers;
- color state for empty/partial/complete;
- filled complete-board count;
- active profile;
- Import / Export / Settings.

Settings only offers allowed sizes large enough not to hide non-empty boards.

### Cumulative PBN exchange

Export:

```text
session state
-> complete boards only
-> board number <= totalBoardsInGrid
-> PBN blocks sorted by board number
-> Android share intent with EXTRA_STREAM and EXTRA_TEXT
```

Import:

```text
raw PBN text
-> tagged blocks
-> complete board parse
-> board number policy 1..39
-> merge into TdSessionState
-> expand grid to smallest allowed size that can show imported boards
```

Invalid, partial, or unsupported board blocks are ignored and counted.

## 9. TD::EditBoard architecture

TD::EditBoard is the field cockpit.

Current layout is a 3-row cockpit:

```text
Top:    Board controls | North hand | LastScannedCardArea | StatusArea
Middle: West hand + Clear + Swap | CentralArea | East hand + Undo + Scissors
Bottom: Feed mode | South hand | Orientation | SureArea
```

The right-bottom area is `SureArea`; there is no side `PBNArea`.

PBN preview appears only in the central `BoardCompleteView` when the board is complete.

### Camera / central area

`CentralArea` shows:

- permission message if camera permission is absent;
- modal placeholder while full-screen recovery UI is open;
- `BoardCompleteView` when board is complete;
- otherwise CameraX preview + barcode guide overlay.

`BoardCompleteView` displays a large PBN preview produced through `TdSessionExchange.exportCompleteBoard(...)`.

### Status and last scanned card

`LastScannedCardArea` displays:

- `Last scanned` label;
- the last scanned card if available;
- otherwise pending duplicate candidate card if relevant.

`StatusArea` displays:

- total card count `Cards: n/52`;
- SPS/IDLE telemetry;
- placeholder `Thoughts: 0`;
- last result message.

### Selected hand

Card additions and most recovery controls operate on `selectedSeat`.

Seat cards are rendered in bridge suit order. Completed hands are visually distinct.

### Feed mode

Current TD feed mode is effectively stream mode. Snap mode remains disabled/placeholder.

## 10. TD reducer and recovery controls

`EditBoardReducer` owns pure board mutation decisions.

### Add scanned card

`applyScannedCard(editState, card, signature)`:

- if card is already in selected hand: no board mutation, OK message;
- if card exists in another hand: no board mutation, create `DuplicateOverrideCandidate`;
- if selected hand is complete: no mutation, auto-advance selected seat;
- otherwise add card to selected hand;
- record `AddedCardRecord(selectedSeat, card)`;
- clear duplicate candidate;
- auto-advance after hand completion;
- try fourth-hand auto-fill when applicable.

### Duplicate override / `I'm sure`

`SureArea` enables `I'm sure` only when pending candidate target seat is the currently selected seat.

`confirmDuplicateOverride(editState)`:

- requires a pending candidate;
- rejects stale candidate if existing seat no longer has card;
- rejects stale candidate if target already has card;
- rejects stale candidate if target hand is complete;
- otherwise moves the card from existing seat to target seat;
- removes matching old history record for existing seat/card;
- adds new history record for target seat/card;
- clears candidate.

This is for false-positive recovery: the TD is saying the previous occurrence was wrong.

### Undo

`Undo` is standard LIFO undo for the **currently selected hand**.

It:

- finds the last `AddedCardRecord` whose seat equals selected seat;
- removes that card from that hand;
- removes that single history record;
- clears duplicate candidate.

Accepted no-fix behavior:

- after a hand completes, auto-advance may select the next hand;
- immediate `Undo` therefore applies to the new selected hand and may be disabled if that hand has no history;
- TD can reselect the previous hand if needed.

### Scissors

`Scissors` opens a full-screen selected-hand view.

Each visible card is clickable. Removing a card:

- removes only from the selected hand;
- removes matching history for that selected seat/card;
- clears duplicate candidate;
- does not dismiss the screen automatically.

This is the main recovery route for auto-filled fourth-hand cards.

### Swap

`Swap` opens a full-screen seat chooser.

Swapping:

- swaps currently selected hand with target hand;
- clears all `addHistory` because old history no longer maps cleanly;
- clears duplicate candidate;
- preserves selected seat.

### Clear

The button label is intentionally short:

```text
Clear
```

Context applies:

- if selected hand has cards, clear selected hand;
- if selected hand is empty, ask confirmation for clearing entire board.

## 11. Auto-fill fourth hand

`tryAutoFillFourthHand(...)` fills the selected hand with the remaining deck only when all other hands are complete and selected hand is empty.

Auto-filled cards:

- are not added to `addHistory`;
- are not expected to be undone via `Undo`;
- can be removed via `Scissors`.

This is an accepted product decision.

## 12. Compose/state rules

Avoid mutating Compose state directly inside render branches.

Example rule:

```text
Do not assign scanDeltas = emptyList() inside the BoardCompleteView composition branch.
```

If scan-rate state needs completion reset, keep it in controller/effect-side logic (`updateMetrics`, `LaunchedEffect`, or equivalent), not in plain composition.

Modifier rule:

- external `modifier` belongs to the outermost composable container;
- inner child containers should usually start with fresh `Modifier` unless deliberately propagating caller sizing.

## 13. Testing map

Important test anchors:

- `CardIdTest`
- `HandStateTest`
- `BoardStateTest`
- `BoardProgressSummaryTest`
- `DeckProfile*Test`
- `PbnExporterTest`
- `EditBoardReducerTest`
- `TdSessionStateTest`
- `TdSessionExchangeTest`
- `TdSessionShareManagerTest`
- `EditBoardControllerTest`
- `AdminEditCameraSupportTest`
- `Grid13*Test`

Specific current regression anchors:

- PBN exporter does not emit `[Site "..."]`.
- TD grid cannot shrink below highest non-empty board.
- Import accepts boards `1..39` and expands grid to fit.
- Share intent includes `Intent.EXTRA_TEXT`.
- Verdict decoder scratch buffers handle wide -> narrow image widths.
- ROI extraction keeps dense and non-unit pixel stride paths covered.
- Undo is LIFO within selected hand across mixed history.
- Duplicate override stale cases clear candidate without mutation.
- Swap clears duplicate candidate.
- Scissors removal clears candidate and matching history.

## 14. Current limitations / non-goals

Current non-goals:

- no traveller scoring;
- no contract/result entry;
- no Bridgemate replacement in MVP;
- no online service dependency;
- no generic retail barcode decoding;
- no TD-side diagnostic deep dive;
- no automatic correction of all user mistakes.

Known placeholders / current rough edges:

- `Thoughts: 0` is a placeholder in TD status.
- Snap feed mode is disabled/placeholder.
- TD screen layout is field-driven and may remain visually blunt while ergonomics are tested.
- PBN preview inside `BoardCompleteView` is central-only; side PBN area is intentionally removed.

## 15. Guidance for future AI instances

When re-entering this project:

1. Start from current docs and source snapshot, not old tickets.
2. Preserve field-proven behavior unless explicitly changing it.
3. Keep tickets temporary and actionable.
4. When a ticket lands, absorb only durable truth into architecture/design docs.
5. Do not move card meaning into scanner/vision.
6. Do not make TD mode explain like Admin mode.
7. Prefer missed scan over wrong card.
8. Ask: does this reduce the TD's hand-burden?

The beetle is not fluff. It encodes UX: small, focused, suspicious, helpful, and not the main event.
