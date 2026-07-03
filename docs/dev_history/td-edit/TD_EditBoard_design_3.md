# TD::EditBoard Design 3 — Current State

Audience: future AI instances working on ITROBOC.
Status: lean current-state design note, not a ticket list.
Last aligned with: source snapshot `6664b2d` plus local review decisions after recovery-controls implementation.

---

## 1. Product purpose

`TD::EditBoard` is the tournament-director workflow for building duplicate bridge board deals from observed barcode-marked physical cards.

The user goal is not barcode fascination. The user goal is:

```text
physical cards -> decoded card identities -> validated board -> usable PBN
```

The screen exists to reduce TD hand-burden during real club/tournament conditions.

Load-bearing rules:

```text
Every scan interaction should reduce the TD's cognitive load, or it has failed.
Wrong card is worse than missed card.
Mistakes must be locally recoverable.
```

A missed scan costs another hand movement. A wrong accepted card mutates the board, damages trust, and forces cleanup. Current recovery controls exist because real tournament/card handling includes both scanner false positives and human seat mistakes.

---

## 2. Mode split

The project uses this product split:

```text
Admin = explanation
TD    = verdict
```

Admin screens may expose scan evidence, diagnostics, grids, warnings, alias details, and calibration reasoning.

TD screens should give operational answers:

- card added
- duplicate / already known
- unknown signature
- hand complete
- board complete
- recovery action completed
- export/import status

TD should not require the user to interpret forensic scanner details during tournament work.

---

## 3. Current module ownership

### `:core`

Owns bridge/domain truth and pure edit rules:

- `CardId`, `Suit`, `Rank`, `Seat`
- `HandState`, `BoardState`, `BoardEditState`
- `AddedCardRecord`, `DuplicateOverrideCandidate`
- `BoardProgressSummary`, `HandProgressSummary`
- `DuplicateBoardMetadata`
- `PbnExporter`, `PbnExportOptions`
- `DeckProfile` lookup model
- `EditBoardReducer`
- older/secondary pure scan presentation helpers: `TdScanAccumulator`, `TdScanSessionPresentation`

### `:vision`

Owns barcode decoding from image data to raw signatures.

TD currently uses `Grid13VerdictDecoder` through `AdminEditCameraFrameDecoder` as a camera-frame adapter.

Important boundary:

```text
Scanner does not know card meaning.
```

The scanner returns raw signatures. Deck profiles map signatures to `CardId`.

### `:app`

Owns Android state, CameraX, Compose screens, session grid, import/export/share, and profile selection.

Important app files:

- `MainActivity.kt` / `AppNavigation()` — in-memory app state and routing
- `TdOverviewScreen.kt` — board grid, import/export/settings
- `EditBoardScreen.kt` — live board cockpit and recovery controls
- `TdSessionState.kt` — current TD session state
- `TdSessionExchange.kt` — cumulative PBN export/import
- `TdSessionShareManager.kt` — Android share intent
- `BarcodeOrientationMode.kt` — current TD orientation mode projection

---

## 4. Session state

Current session state lives in memory inside `AppNavigation()`.

```kotlin
data class TdSessionState(
    val boards: Map<Int, BoardEditState> = emptyMap(),
    val totalBoardsInGrid: Int = 30,
)
```

Default board grid size is `30`.

Allowed grid sizes:

```text
15, 18, 21, 24, 27, 30, 33, 36, 39
```

`highestNonEmptyBoardNumber` is computed from boards whose `boardState.totalCardCount() > 0`.

`updateGridSize(newSize)` must enforce both:

```kotlin
require(newSize in ALLOWED_GRID_SIZES)
require(newSize >= highestNonEmptyBoardNumber)
```

Reason: no valid non-empty board may become hidden by shrinking the overview grid. Hidden work is a cognitive-load violation.

Empty `BoardEditState` entries above the visible grid are allowed to exist in the map, but export and overview status only consider boards within `totalBoardsInGrid`.

---

## 5. TD overview screen

`TdOverviewScreen` is the TD session hub.

Current behavior:

- shows `TD Actions`
- displays a lazy board grid from board `1` to `sessionState.totalBoardsInGrid`
- grid uses 3 rows by setting `columns = totalBoardsInGrid / 3`
- each board button shows `Empty`, `Partial`, or `Complete`
- displays `Filled: completeCount / totalBoardsInGrid`
- displays the active profile name
- provides `Import`, `Export`, and `Settings`

Board button status:

```text
Empty    = no board state or 0 cards
Partial  = some cards but not complete
Complete = `BoardProgressSummary.boardComplete`
```

Settings opens a full-screen settings surface. It offers only allowed board counts that are `>= highestNonEmptyBoardNumber`.

---

## 6. Import/export session exchange

`TdSessionExchange.exportCompleteBoards(sessionState)`:

- exports only complete boards
- ignores boards whose `boardNumber > totalBoardsInGrid`
- sorts by board number
- returns `null` if no complete boards are available
- joins multiple PBN boards with a blank line

`TdSessionExchange.exportCompleteBoard(boardState, boardNumber)`:

- derives duplicate-board metadata using `DuplicateBoardMetadata.forBoardNumber(boardNumber)`
- passes dealer/vulnerability into `PbnExporter`

Current MVP PBN output emits:

```text
[Event "ITROBOC Export"]
[Board "N"]
[Dealer "..."]
[Vulnerable "..."]
[Deal "..."]
```

Current MVP PBN output intentionally does **not** emit `[Site "..."]`, even though `PbnExportOptions.site` still exists in the type.

`TdSessionExchange.importCumulative(sessionState, rawText)`:

- parses tagged PBN blocks
- imports only complete boards
- preserves selected seat for an overwritten board when possible
- supports board numbers `1..39`
- ignores unsupported board numbers, partial blocks, malformed blocks, and empty blocks
- expands `totalBoardsInGrid` upward to the first allowed grid size that can show the largest valid imported board
- does not shrink the grid

Examples:

```text
Board 31 -> grid expands to 33
Board 34 -> grid expands to 36
Board 39 -> grid expands to 39
Board 40 -> ignored
Board 0  -> ignored
```

`TdSessionShareManager` writes the export text to `td-session-export.pbn` and builds an Android `ACTION_SEND` intent with:

- `Intent.EXTRA_STREAM`
- `Intent.EXTRA_TEXT`
- `Intent.EXTRA_SUBJECT`
- `FLAG_GRANT_READ_URI_PERMISSION`

---

## 7. EditBoard screen layout

`EditBoardScreen` is currently a landscape-first cockpit with large field-readable labels.

The screen is arranged as a 3x3-ish table.

Top row:

```text
Board controls | North hand | Last scanned | Status
```

Middle row:

```text
West hand + Back + Swap | Camera or BoardCompleteView | East hand + Undo + Scissors
```

Bottom row:

```text
Feed mode | South hand | Orientation | SureArea
```

Hand panels:

- show suit-grouped cards in bridge suit order
- use large text
- pale yellow means empty/partial
- green means complete
- selected hand has a visible blue border
- tapping a hand selects it and may trigger fourth-hand auto-fill

West panel currently contains:

- `Back`
- West hand display
- `Swap`

East panel currently contains:

- `Undo`
- East hand display
- `Scissors`

The bottom-right area is `SureArea`, not `PBN preview`.

Clear button behavior:

- label is always `Clear`
- if selected hand has cards: clears only that hand
- if selected hand is empty: opens confirmation to clear the whole board

The label is intentionally short for larger font / field readability:

```text
Clear is there; context applies.
```

---

## 8. Scan flow in TD

Current TD live scan path:

```text
CameraX frame
-> AdminEditCameraFrameDecoder(Grid13VerdictDecoder)
-> BarcodeDecodeResult.Found
-> DetectedSignature.viewedAs(orientationMode)
-> DeckProfile.lookup(signature)
-> EditBoardReducer.applyScannedCard(...)
-> BoardEditState mutation via onBoardEditStateChange
```

`BarcodeOrientationMode` currently has:

```text
BFM  -> use raw detected signature
BRM  -> replace prefix `bfm` with `brm`, preserving visible payload
AUTO -> unresolved placeholder, returns null
```

Current default orientation in `AppNavigation()` is `BRM`.

Important orientation rule:

```text
bfm/brm are feed-direction families, not min/max canonical forms.
```

They are not arbitrary canonicalization labels. Future work must not reinterpret reverse orientation as bit reversal unless the physical model explicitly changes.

---

## 9. Stream debounce and unknown throttling

`EditBoardScreen.handleScan(signature)` currently applies a simple stream debounce:

```text
same raw signature within 1000 ms -> suppressed
```

This is a feed-path protection, not a decoder rule.

Unknown signatures are throttled:

- unknown counter increments
- main status updates at most once every 3000 ms
- message includes the signature, total unknown count, and a profile/orientation hint

This keeps unknown-signature noise from flooding the main TD status area.

---

## 10. Pure edit rules

`EditBoardReducer` is the current pure rules owner for board mutation.

`EditBoardReducer.applyScannedCard(editState, card, signature)` handles:

- same-hand duplicate: no board change, reports OK/already present
- cross-hand duplicate: no board change, creates `DuplicateOverrideCandidate`
- selected hand already complete: no card added; selected seat advances
- valid card: added to selected hand
- auto-advance after selected hand reaches 13 cards
- auto-fill check after mutation/advance
- last scanned card reporting

Current seat order:

```text
North -> East -> South -> West -> North
```

`tryAutoFillFourthHand(editState)`:

- runs only when the selected hand is empty
- requires all three other hands complete
- computes remaining cards from the 52-card deck
- fills selected hand if exactly 13 cards remain
- leaves selection on the auto-filled seat
- reports board complete
- does **not** add auto-filled cards to `addHistory`

Auto-fill cards are removable via `Scissors`; they are not expected to be undone via `Undo`.

---

## 11. Recovery controls

The recovery controls exist to make scan/human mistakes locally recoverable without restarting the board.

### Undo

`Undo` is standard LIFO undo for the **currently selected hand**.

Pure rule:

```kotlin
EditBoardReducer.undoAddForSelectedHand(editState)
```

Behavior:

- finds the last `AddedCardRecord` whose `seat == selectedSeat`
- removes that card from that hand
- removes that history record
- clears any pending `duplicateOverrideCandidate`
- if no history exists for selected hand, returns “Nothing to undo...”

Important no-fix decision:

- after a hand reaches 13 cards, selection auto-advances to the next seat
- therefore immediate `Undo` affects the new selected seat, not the just-completed seat
- this is accepted because the usual next hand is empty, so the button is disabled; the TD can tap the previous hand if needed

### Scissors

`Scissors` opens a full-screen selected-hand card view.

Pure rule:

```kotlin
EditBoardReducer.removeCardFromSelectedHand(editState, card)
```

Behavior:

- only removes cards from the currently selected hand
- each displayed card is clickable
- removing a card clears matching history for that selected hand/card
- removing a card clears any pending `duplicateOverrideCandidate`
- useful for manual cleanup and for auto-filled fourth-hand cards

### Swap

`Swap` opens a full-screen seat chooser and swaps the currently selected hand with another hand.

Pure rule:

```kotlin
EditBoardReducer.swapSelectedHandWith(editState, targetSeat)
```

Behavior:

- swaps the two hand contents
- clears all `addHistory`
- clears any pending `duplicateOverrideCandidate`
- keeps selected seat unchanged

Reason: after a hand swap, historical “added card” records no longer have reliable undo semantics.

### I'm sure / SureArea

`SureArea` is the bottom-right TD recovery area.

`I'm sure` is enabled only when:

```kotlin
duplicateOverrideCandidate?.targetSeat == selectedSeat
```

Pure rule:

```kotlin
EditBoardReducer.confirmDuplicateOverride(editState)
```

Use case:

- scanner sees a card for the selected hand
- the same card is already present in another hand
- reducer rejects normal add and creates a duplicate override candidate
- TD says `I'm sure` when the previous occurrence was a false positive

Behavior:

- if no candidate exists: no mutation
- if candidate is stale: clears the candidate and reports no longer actionable
- if candidate is valid: moves the card from existing seat to target seat
- updates history by removing the existing-seat record for that card and appending a target-seat record
- clears the candidate

Stale cases include:

- existing seat no longer contains the card
- target seat already contains the card
- target hand is complete

---

## 12. LastScannedCardArea and StatusArea

`LastScannedCardArea` displays:

- title: `Last scanned`
- last accepted/scanned `CardId`, if present
- otherwise the pending duplicate candidate card, if present

This lets a rejected duplicate candidate remain visible while `I'm sure` is available.

`StatusArea` displays:

- `Cards: X/52`
- current barcode orientation mode
- recent scan/callback cadence as `FPS` or idle count as `IDLE`
- last result message

Current status is string-based rather than a typed TD event model.

---

## 13. Board complete state

A board is complete when `BoardProgressSummary.from(boardState).boardComplete` is true.

When the current board is complete:

- scan mutation is ignored by `handleScan`
- the central camera area switches to `BoardCompleteView`
- camera feed is no longer shown in the center
- `BoardCompleteView` shows a large PBN preview
- side recovery controls remain reachable outside the central area
- clearing/removing a card returns the board to incomplete state and scanning can resume

There is no separate side `PBNArea` in the current layout.

This visual transition is part of the TD workflow: it tells the TD the board has landed.

Implementation rule:

```text
Do not mutate Compose state directly inside a render branch.
```

If scan-rate state needs reset on board completion, use `LaunchedEffect` / effect-side logic, not assignment inside the `BoardCompleteView` composition branch.

---

## 14. Scan rate telemetry

`EditBoardScreen` maintains `scansPerSecond` as field telemetry.

Current meaning:

- positive value: recent callback cadence derived from inter-scan deltas over about the last second
- negative value: idle-loop counter while no recent deltas exist

This is not a formal benchmark. It is a practical field signal.

Current UI label says `FPS`, but the design meaning is closer to recent scan/callback cadence.

---

## 15. Current placeholders / non-goals

These are current facts, not active tickets in this file:

- session state is in-memory only
- `AUTO` orientation mode is disabled/unresolved
- `snap` feed mode is shown disabled
- layout is landscape/tablet-first
- Admin diagnostics are intentionally richer than TD diagnostics
- `TdScanAccumulator` and `TdScanSessionPresentation` still exist as pure scan/presentation helpers, but live `EditBoardScreen` currently uses `EditBoardReducer` for board mutation
- current TD status is string-based; typed event/status model is a possible future improvement, not current doctrine

Do not convert these facts into architecture doctrine unless they become permanent.

---

## 16. Test anchors

Tests should protect behavior, not historical ticket wording.

Important behavior anchors:

- `EditBoardReducerTest`
  - add new card
  - reject same-hand duplicate without mutation
  - reject cross-hand duplicate without mutation and create candidate
  - auto-advance after hand completion
  - auto-fill fourth hand
  - clear selected hand
  - clear board
  - LIFO undo for selected hand
  - scissors/removal clears selected hand card and matching history
  - swap clears history and pending duplicate candidate
  - duplicate override valid move
  - duplicate override stale cases

- `TdSessionStateTest`
  - default grid size
  - allowed grid update
  - reject unsupported grid size
  - reject grid shrink that hides non-empty boards
  - compute highest non-empty board

- `TdSessionExchangeTest`
  - export complete boards only
  - import complete boards cumulatively
  - preserve selected seat when overwriting an imported board
  - ignore partial/malformed/unsupported boards
  - import expands grid for valid board numbers up to 39
  - export ignores boards outside current grid range

- `TdSessionShareManagerTest`
  - share intent includes stream, text, subject, and URI permission

- `PbnExporterTest`
  - align with current MVP output: no `[Site "..."]` unless that product decision changes again

---

## 17. Lean-doc rule for this directory

`docs/dev_history/td-edit/` should not be a graveyard of completed tickets.

Current policy:

```text
when ticket done -> gone
```

Keep this file as the current design anchor.
Move old ticket files, stale handoffs, and implementation plans out of the active TD edit directory, or delete/archive them outside the current working context.

Architecture belongs in `docs/architecture.md`.
Current TD design belongs here.
Actionable future work belongs in separate ticket files only while it is actually pending.
