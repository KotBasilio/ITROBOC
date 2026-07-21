# ITROBOC Architecture

Last aligned with source snapshot: `e118faa`.

Status: post-MVP. ITROBOC has survived first real tournament use, produced usable PBN from physical club cards, and now has TD-side recovery controls plus a lightweight Beetle Mind stabilization layer for common scan/human errors. PBN exports now include professional file-level headers and preserve double-dummy solver (DDS) metadata during import/export cycles.

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
- TD should show concise operational results: accepted card, skipped duplicate, manual repair result, board complete, export available, and visible in-progress stabilization thought when a scan has not landed yet.
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

- screens: Main, TD overview, TD edit board, Scissors selected-hand repair, Admin actions/edit/read-only
- `AdminEditCameraSupport` and CameraX frame adapters
- `EditBoardController`
- `EditBoardScreen`
- `ScissorsScreen`
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

## 6.5 TD stabilization layer

Between TD verdict decode and board mutation, the app now has a lightweight stabilization layer in `EditBoardController`.

Current flow:

```text
CameraScanOutcome.Decoded
-> BarcodeDecodeResult.Found
-> viewedAs(orientationMode)
-> processPondering(signature)
-> repeated matching founds build consensus
-> only after consensus: handleScan(...)
-> DeckProfile.lookup(signature)
-> EditBoardReducer.applyScannedCard(...)
```

This is intentionally not semantic guessing. It stabilizes repeated found verdicts before they reach board state.

Current unknown-signature rule:

```text
unknowns may be seen in thoughts,
but they do not enter handleScan()
and do not produce an operational TD status result.
```

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
Top:    BoardControlsArea (with 🪲 mascot) | North hand | LastScannedCardArea | StatusArea
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
- `Thoughts: ...` from the stabilization layer (`0`, tentative `?`/`??`/`???`, ambiguous `~`, or accepted `.`);
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

### Scissors / selected-hand repair

`Scissors` opens `ScissorsScreen`, a full-screen selected-hand repair cockpit. It is not removal-only anymore.

The screen has two operational halves:

```text
left:  Add cards       52-card table / manual repair grid
right: Remove cards    selected hand, grouped by suit
```

The add table uses the same TD-readable ownership language as Admin-style card grids:

- green fill = card is already here, in the selected hand;
- orange fill = card is in another hand;
- gray fill = card is unassigned;
- seat-colored border = current owner seat when there is one.

Click behavior:

- card already in selected hand: disabled/no-op;
- unassigned card: add it to selected hand manually;
- card in another hand: atomically move it into selected hand, semantically similar to `I'm sure`;
- selected hand already has 13 cards: block add and ask TD to remove one first.

Manual add/move is for scratched barcodes, missing barcodes, replaced physical cards, and late discovery that a previous card placement was wrong.

Reducer semantics for manual add/move:

- clear duplicate candidate;
- unassigned manual add appends `AddedCardRecord(selectedSeat, card)`;
- move from another hand removes matching old history for old seat/card and appends selected-seat history;
- manual add/move does **not** auto-advance selected seat;
- manual add/move does **not** trigger fourth-hand auto-fill.

Removing a card from the right pane:

- removes only from selected hand;
- removes matching selected-hand history for that card;
- clears duplicate candidate;
- keeps the Scissors screen open.

This is the main recovery route for unreadable physical cards and auto-filled fourth-hand card corrections.

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

`tryAutoFillFourthHand(...)` fills the selected hand with the remaining deck when all other hands are complete and the selected hand can be completed by the remaining cards.

Auto-filled cards:

- are not added to `addHistory`;
- are not expected to be undone via `Undo`;
- can be removed via `Scissors`;
- are intentionally not produced directly by Scissors manual add/move.

Current trigger policy:

- ordinary scanned add may try auto-fill after a hand lands;
- explicit seat selection calls `tryAutoFillFourthHand(...)`;
- manual Scissors repair does not auto-fill immediately.

This supports the field case where the TD manually moves one card into a hand, closes Scissors, selects the complementary hand, and lets that selected hand auto-complete from the remaining deck.

This is an accepted product decision.

## 12. Compose/state/typography rules

Avoid mutating Compose state directly inside render branches.

### State durability

ITROBOC manages state durability at two levels:

1. **Configuration Change (Rotation)**: App-level state (navigation, profiles, TD session) is hoisted into `ItrobocMainViewModel`. This ensures that data is not lost when the device is rotated.
2. **Process Death & Restarts**: A silent **Autosave** system triggers a PBN export to internal storage whenever a hand is completed. On startup, the app automatically attempts to restore the most recent configured autosave file.

Settings for autosave (enabled/disabled, file prefix) are persisted in `SharedPreferences`.

### Typography

ITROBOC uses a central semantic typography layer in `ItrobocTextStyles.kt`.

- **Prefer semantic roles** over raw `fontSize` or `FontWeight` in `.kt` UI files.
- **BigVisible**: Primary anchor for titles and major actions (40sp Bold).
- **CockpitBasic**: Standard size for cockpit controls (32sp).
- **SettingsBasic**: Anchor for settings labels and grids (36sp Bold).
- **TdHand**: Unified style for card displays in hands (24sp).

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
- Scissors manual add handles unassigned card, already-here no-op, other-hand atomic move, and full-hand block.
- Scissors manual add/move clears duplicate candidate and preserves intended history semantics.
- Scissors manual add does not auto-fill immediately; seat selection remains the explicit manual-repair auto-fill trigger.

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

- Snap feed mode is disabled/placeholder.
- TD screen layout is field-driven and may remain visually blunt while ergonomics are tested.
- PBN preview inside `BoardCompleteView` is central-only; side PBN area is intentionally removed.
- Legacy `MockActions`/`MockTdScreen` code may still exist temporarily, but the current main menu does not expose the Mock screen and current docs should not present it as an active product surface.

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
