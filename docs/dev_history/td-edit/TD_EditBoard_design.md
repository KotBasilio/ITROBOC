# TD::EditBoard Design

Last aligned with source snapshot: `e118faa`.

This document is the current-state design anchor for `TD::EditBoard`. It replaces earlier TD EditBoard ticket/history notes. It should stay lean: when a ticket is completed, remove ticket archaeology and keep only current behavior, invariants, accepted decisions, and test anchors.

## 1. Purpose

`TD::EditBoard` is the live Tournament Director cockpit for building one duplicate bridge board from physical barcode-marked cards.

Main flow:

```text
physical card barcode
-> camera frame / ROI
-> raw signature
-> active DeckProfile lookup
-> CardId
-> selected hand / BoardEditState
-> complete board
-> PBN preview/export path
```

Primary product goal:

```text
Help TD reach 52 correct cards quickly and safely.
```

The screen must reduce babysitting. It should not make the TD debug the scanner during club work.

## 2. Load-bearing rules

```text
TD = verdict.
Wrong card is worse than missed card.
A verdict must be visible.
Board complete means: stop scanning; trust the landing.
Recovery controls should make human/scan mistakes locally fixable.
```

Admin can explain. TD should decide and show what happened.

## 3. Current file ownership

Relevant code:

```text
:app
  EditBoardScreen.kt
  EditBoardController.kt
  BeetleMind.kt
  ScissorsScreen.kt
  TdSessionState.kt
  TdSessionExchange.kt
  TdSessionShareManager.kt

:core
  BoardEditState.kt
  BoardState.kt
  HandState.kt
  EditBoardReducer.kt
  PbnExporter.kt
  DuplicateBoardMetadata.kt

:vision
  Grid13VerdictDecoder.kt
  GrayImage.kt
  BarcodeModels.kt
```

`EditBoardScreen` owns the main Compose cockpit and camera UI.

`ScissorsScreen` owns the full-screen selected-hand repair cockpit.

`BeetleMind` is the pure Kotlin evidence-trust state machine. It owns typed
evidence, consensus, thought/dream state, unknown hesitation, and accepted-card
debounce.

`EditBoardController` bridges camera outcomes into typed mind evidence, resolves
profiles, publishes mind thoughts, routes accepted card scans into reducer
calls, and owns status messages and scan-rate metrics.

`EditBoardReducer` owns pure board mutation logic.

`TdSessionExchange` owns complete-board PBN export/import for TD session exchange.

## 4. Current cockpit layout

The screen is a 3-row cockpit.

```text
Top row:
BoardControlsArea | North hand | LastScannedCardArea | StatusArea

Middle row:
West hand + Clear + Swap | CentralArea | East hand + Undo + Scissors

Bottom row:
Feed mode | South hand | Orientation | SureArea
```

There is no side `PBNArea` in the current layout.

PBN preview appears in the central area only when the board is complete.

## 5. Top row

### BoardControlsArea

Shows current board number and Back. 

Observed design choice (v1): A laconic text-only header to maximize vertical space for recovery controls.

### North HandArea

Shows North hand and selected-hand border when North is selected.

### LastScannedCardArea

Displays:

- `Last scanned` label;
- last scanned accepted/reported card if available;
- otherwise pending duplicate candidate card.

When idle and no card is present, this area displays a large, friendly 🪲 mascot icon to anchor the branding without crowding the status metrics.

### StatusArea

Displays:

- total card count: `Cards: n/52`;
- SPS/IDLE telemetry;
- `Thoughts` status published by the controller from typed `BeetleThought` state;
- last result message.

Current message source is string-based from `EditBoardController` / `EditBoardReducer`.

## 6. Middle row

### WestArea

West hand panel also hosts:

- `Clear`
- `Swap`

`Clear` is intentionally short. Context applies:

- if selected hand has cards, clear selected hand;
- if selected hand is empty, ask confirmation before clearing entire board.

`Swap` opens a full-screen seat chooser and is enabled when selected hand has cards.

### CentralArea

Central area shows one of:

- camera permission message;
- modal placeholder while `Scissors` or `Swap` full-screen UI is open;
- `BoardCompleteView` when the board is complete;
- otherwise CameraX preview + barcode guide overlay.

The current shared camera guide is a thin blade: 20% of frame width, 3% of
frame height, and no more than 10 pixels high. `BarcodeCameraScanner` computes
one ROI for buffer sizing and passes that exact ROI into `CameraFrameDecoder`;
the overlay uses the same guide specification in display coordinates. Manual
field verification found fewer uncertain reads that asked for a closer card.

### BoardCompleteView

When complete, central area shows:

- `Board Complete`;
- PBN preview generated through `TdSessionExchange.exportCompleteBoard(...)`.

This view is a landing surface: the TD can stop scanning and trust that the board has landed.

### EastArea

East hand panel also hosts:

- `Undo`
- `Scissors`

`Undo` is enabled if `addHistory` contains at least one card for the currently selected hand.

`Scissors` is available as selected-hand repair. It can be useful even when the selected hand is empty, because it can add unreadable or no-barcode cards manually.

## 7. Bottom row

### FeedModeArea

Current active mode is stream. Snap remains disabled/placeholder.

### South HandArea

Shows South hand and selected-hand border when South is selected.

### OrientationArea

Shows barcode orientation mode. `AUTO` exists in the enum but is disabled in the current UI.

### SureArea

Bottom-right recovery area.

`I'm sure` is enabled only when:

```text
boardEditState.duplicateOverrideCandidate?.targetSeat == selectedSeat
```

It confirms a duplicate override: the TD says the old occurrence was a false positive and the candidate card belongs in the selected hand.

## 8. Controller behavior

`EditBoardController` owns UI-facing mutable state:

- current `BoardEditState` input;
- active `DeckProfile`;
- orientation mode;
- scan deltas / SPS / IDLE telemetry;
- last result message;
- last scanned card;
- the Compose-facing presentation string mirrored from `BeetleThought`.

`BeetleMind` owns the actual pondering signature/count, thought/dream state,
consensus, and accepted-card debounce. The controller no longer carries a
parallel copy of that evidence-trust state.

Camera scan flow:

```text
CameraScanOutcome
-> controller applies viewedAs(orientationMode)
-> controller resolves DeckProfile lookup
-> typed BeetleEvidence
-> BeetleMind.observe(...)
-> BeetleThought + optional AcceptedCardScan
-> controller checks board completion
-> EditBoardReducer.applyScannedCard(...)
-> onBoardEditStateChange(update.state)
-> lastResultMessage / lastScannedCard
```

If the board is complete, scans are ignored.

Current stabilization rule:

- repeated `Found` verdicts must reach consensus before they mutate board state;
- Beetle Mind uses configurable Found-stability consensus. TD Settings exposes this as Perception: Shy ↔ Bold. Allowed values are 6,5,4,3,2 frames; default is 2;
- this is stabilization, not semantic inference;
- unknown signatures intentionally do not stabilize.

Current eye/mind note:

- `Scissors`/`Swap` replace the camera surface with a modal placeholder, so the beetle eye is effectively closed while the full-screen repair/seat chooser is open;
- `onManualAddCard(...)` calls `BeetleMind.reset()` before manual repair;
- other manual operations currently do not blank the mind by design: eye is eye, pocket is pocket.

Current unknown rule:

- unknown signatures may appear in `thoughts`;
- unknown signatures intentionally never stabilize;
- they never produce an `AcceptedCardScan`;
- they do not emit operational unknown-pocket messages in TD status.

SPS state reset belongs in controller/effect-side logic, not inside a composable render branch.

Current `updateMetrics()` clears `scanDeltas` while board is complete.

## 9. Reducer behavior

### Add scanned card

`EditBoardReducer.applyScannedCard(...)`:

1. If selected hand already contains card:
   - no mutation;
   - clear duplicate candidate;
   - OK message.
2. If another hand contains card:
   - no mutation;
   - create `DuplicateOverrideCandidate`;
   - show skip message with `I'm sure` hint.
3. If selected hand is complete:
   - no card add;
   - auto-advance selected seat.
4. Otherwise:
   - add card to selected hand;
   - append `AddedCardRecord(selectedSeat, card)`;
   - clear duplicate candidate;
   - auto-advance if hand completes;
   - try fourth-hand auto-fill if applicable.

### Auto-fill fourth hand

When other hands are complete and the selected hand can be completed by the remaining deck cards, the reducer fills the selected hand with those remaining cards.

Accepted decision:

- auto-filled cards are not added to `addHistory`;
- `Undo` does not peel auto-filled cards;
- `Scissors` is the recovery path for auto-filled cards;
- manual Scissors add/move does not auto-fill immediately.

Current trigger policy:

- scanned-card add can try fourth-hand auto-fill as part of the normal scan landing path;
- explicit seat selection can try fourth-hand auto-fill;
- manual Scissors repair waits for the TD to choose the next seat/step.

### Undo

`Undo` is standard LIFO undo for the currently selected hand.

It:

- finds the last `AddedCardRecord` with `seat == selectedSeat`;
- removes that card from that hand;
- removes that one history record;
- clears duplicate candidate;
- reports message.

Accepted decision:

- after hand completion, auto-advance may select the next hand;
- immediate `Undo` may be disabled because the new selected hand is empty;
- TD can reselect the previous hand when needed.

### Scissors / selected-hand repair

`Scissors` opens `ScissorsScreen`, a full-screen selected-hand repair view.

It now has two halves:

```text
Add cards     | Remove cards
52-card grid  | selected hand cards
```

Purpose:

- remove a wrong card found late in the selected hand;
- add a card whose barcode is scratched, missing, or physically replaced;
- move a card from another hand into the selected hand when the earlier placement was wrong.

Left pane / add table:

- shows all 52 cards as suit columns and rank rows;
- uses green fill for cards already in selected hand;
- uses orange fill for cards in another hand;
- uses gray fill for unassigned cards;
- uses seat-colored borders to show the owner seat;
- disables/no-ops cards already in the selected hand.

`EditBoardReducer.addManualCardToSelectedHand(...)` owns the domain behavior:

1. If selected hand already contains the card:
   - no board mutation;
   - clear duplicate candidate;
   - report already-here message.
2. If selected hand has 13 cards:
   - no mutation;
   - clear duplicate candidate;
   - ask TD to remove one first.
3. If another hand contains the card:
   - atomically remove from old seat and add to selected seat;
   - remove old matching history record for old seat/card;
   - append selected-seat `AddedCardRecord`;
   - clear duplicate candidate;
   - report move message.
4. If card is unassigned:
   - add it to selected hand;
   - append selected-seat `AddedCardRecord`;
   - clear duplicate candidate;
   - report manual add message.

Manual Scissors add/move intentionally does not auto-advance and does not auto-fill immediately. The TD remains in control of seat selection during manual repair.

Right pane / remove cards:

- shows selected-hand cards grouped by suit;
- each visible card rank is clickable;
- removing a card removes it from selected hand;
- removes matching selected-hand history for that card;
- clears duplicate candidate;
- keeps Scissors open.

### Swap

`Swap` opens a full-screen seat chooser.

Swapping:

- swaps selected hand with target hand;
- clears all `addHistory`;
- clears duplicate candidate;
- keeps selected seat as-is.

Clearing all history is intentional: after a swap, old history no longer maps cleanly.

### I'm sure / duplicate override

`confirmDuplicateOverride(...)`:

- requires a pending candidate;
- rejects stale candidate if existing seat no longer contains card;
- rejects stale candidate if target already contains card;
- rejects stale candidate if target hand is complete;
- otherwise removes the card from existing seat and adds it to target seat;
- removes matching old history for existing seat/card;
- appends history for target seat/card;
- clears candidate.

This recovers from false positives where a card was previously accepted into the wrong hand.

## 10. Clear behavior

The visible label is simply:

```text
Clear
```

Context applies.

`onClearClick()`:

- if selected hand has cards: clear selected hand immediately;
- if selected hand is empty: open confirmation dialog for clearing entire board.

The confirmation dialog still says `Clear entire board?` and confirm button says `Clear board`.

## 11. Compose/state safety

Rules:

- Do not mutate Compose state in ordinary composition branches.
- Use controller state, reducer state, `LaunchedEffect`, or effect-side loops for mutation.
- External `modifier` should normally be consumed by the outermost container only; inner child layout should start from fresh `Modifier` unless deliberate.

Current relevant fixes:

- `CentralArea` no longer assigns `scanDeltas = emptyList()` inside the board-complete branch.
- `SureArea` is now a direct `Button` consuming the caller modifier; there is no nested `Column` reusing the same external modifier.

## 12. PBN behavior

`BoardCompleteView` uses:

```kotlin
TdSessionExchange.exportCompleteBoard(boardState, boardNumber)
```

Cumulative export from TD overview uses complete boards only, sorted by board number, and shares via Android share intent.

Current MVP PBN output intentionally does not emit `[Site "..."]`.

## 13. Tests / regression anchors

Important current TD/recovery tests should cover:

- selected-hand LIFO undo across mixed history;
- duplicate override happy path;
- stale duplicate override when existing seat no longer contains card;
- stale duplicate override when target already contains card;
- stale duplicate override when target hand is complete;
- swap clears duplicate candidate and history;
- scissors removal clears candidate and matching history only;
- scissors manual add of an unassigned card appends selected-hand history and clears duplicate candidate;
- scissors manual move from another hand removes old matching history and appends selected-hand history;
- manual add after manual remove keeps history coherent;
- manual Scissors add/move does not auto-fill immediately;
- clear selected hand preserves other-hand history;
- grid resize rejects invalid sizes and sizes below highest non-empty board;
- import accepts 1..39, rejects outside range, and expands grid;
- share intent includes `Intent.EXTRA_TEXT`.

## 14. Accepted current rough edges

Accepted no-fix items:

- immediate Undo after auto-advance may be disabled until TD reselects previous hand;
- auto-filled fourth-hand cards are not undo-history items;
- `Clear` relies on context rather than long label;
- snap feed mode remains disabled;
- legacy Mock screen is no longer a current entry surface and should not be presented as current TD workflow.

## 15. Future direction hints

Possible future improvements, not current tickets by default:

- explicit blank-mind-on-eye-close behavior if field testing shows stale thought after opening/closing modal screens;
- more explicit status event rail;
- more field-friendly status wording;
- haptic/sound feedback;
- copy/share action directly from board-complete view;
- snap mode;
- configurable status fade / persistence.

Keep future TD changes judged by:

```text
Does this reduce the TD's hand-burden?
Does this avoid making the TD babysit the beetle?
```
