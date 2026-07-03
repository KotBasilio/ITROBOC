# PLAN 2026-07-03 — TD::EditBoard Recovery Controls

Audience: Bob / future AI implementation instance.
Status: actionable ticket bundle.
Scope: `TD::EditBoard` recovery after scan errors or human hand-assignment errors.

Archy request:

```text
Add four buttons on EditBoardScreen.kt:
- Undo
- Scissors
- Swap
- I'm sure
```

Product reason:

```text
A TD must recover from mistakes without clearing the whole board.
Wrong accepted card is worse than missed card, but once a wrong card exists, recovery must be fast and visible.
```

Keep the existing product beams active:

```text
Admin = explanation; TD = verdict.
Every scan interaction should reduce TD cognitive load.
The real center is Archy / TD at a live tournament table.
```

---

## 0. Current-code observations

Inspected file: `EditBoardScreen.kt` as provided by Archy.

Relevant current areas:

- `LastScannedCardArea(cardId)`:
  - shows only the last successfully accepted `CardId`;
  - has no representation for rejected duplicate / pending override / recovery candidate.

- `StatusArea(boardState, orientationMode, message, sps)`:
  - shows `Cards: N/52`;
  - shows `Mode: ... | FPS/IDLE ...`;
  - shows free-text `lastResultMessage` when present;
  - status is currently string-based, not a structured event model.

- `BoardCompleteView(boardState, boardNumber)`:
  - replaces the central camera area when the board is complete;
  - shows a large `Board Complete` label and complete-board PBN;
  - recovery controls outside the central camera area can still remain visible if added to side/top/bottom zones;
  - clearing a hand/board already returns the board to incomplete and scanning resumes.

Current pure edit layer:

- `EditBoardReducer.applyScannedCard(...)` handles add, duplicate rejection, hand-full auto-advance, and fourth-hand autofill.
- `clearSelectedHand(...)` and `clearBoard(...)` exist.
- No pure operation currently exists for single-card removal, undo, whole-hand swap, or duplicate override.
- `BoardEditState` currently has only `boardNumber`, `boardState`, and `selectedSeat`.
- `HandState` stores cards as a `Set<CardId>`, so scan/add order is not available unless a separate history is added.

Current docs coverage:

- `TD_EditBoard_design_2.md` describes layout, status area, scan flow, pure edit rules, and board-complete behavior.
- It does not yet describe recovery controls, pending duplicate override, undo history, scissors removal, or hand swapping.
- After implementation, update that design file with a concise new section: `TD recovery controls`.

---

## 1. Ticket: core recovery model and reducer operations

### Purpose

Move recovery behavior into testable pure logic instead of burying it in Compose callbacks.

### Recommended state additions

Prefer extending `BoardEditState` with minimal recovery state:

```kotlin
data class AddedCardRecord(
    val seat: Seat,
    val card: CardId,
)

data class DuplicateOverrideCandidate(
    val card: CardId,
    val signature: String,
    val existingSeat: Seat,
    val targetSeat: Seat,
)

data class BoardEditState(
    val boardNumber: Int,
    val boardState: BoardState = BoardState(),
    val selectedSeat: Seat = Seat.NORTH,
    val addHistory: List<AddedCardRecord> = emptyList(),
    val duplicateOverrideCandidate: DuplicateOverrideCandidate? = null,
)
```

Rationale:

- `Undo` needs add order; `HandState` is a set and cannot answer that.
- `I'm sure` needs the last cross-hand duplicate rejection to remain actionable.
- Keeping this in `BoardEditState` preserves state when navigating away/back.

If Bob prefers not to extend `BoardEditState`, an app-layer wrapper may be used, but the pure reducer should still be testable.

### Required pure helpers

Add pure domain helpers as needed:

```kotlin
fun HandState.removeCard(card: CardId): HandState
fun BoardState.removeCard(seat: Seat, card: CardId): BoardState
fun BoardState.swapHands(a: Seat, b: Seat): BoardState
```

Optional helper:

```kotlin
fun BoardState.moveCard(card: CardId, from: Seat, to: Seat): BoardState
```

### Required reducer operations

Add operations to `EditBoardReducer` or a focused companion reducer:

```kotlin
fun undoAddForSelectedHand(editState: BoardEditState): EditBoardUpdate
fun removeCardFromSelectedHand(editState: BoardEditState, card: CardId): EditBoardUpdate
fun swapSelectedHandWith(editState: BoardEditState, targetSeat: Seat): EditBoardUpdate
fun confirmDuplicateOverride(editState: BoardEditState): EditBoardUpdate
```

### Semantics

#### Add scan

On successful add:

- append `AddedCardRecord(selectedSeat, card)` to `addHistory`;
- clear `duplicateOverrideCandidate`;
- preserve current auto-advance / auto-fill behavior.

On same-hand duplicate:

- no board mutation;
- no history mutation;
- clear or preserve pending override? Prefer clear, because this is not an actionable cross-hand conflict.

On cross-hand duplicate:

- no board mutation;
- set `duplicateOverrideCandidate = DuplicateOverrideCandidate(card, signature, existingSeat, selectedSeat)`;
- message should make the conflict clear: `Skipped: ♦8 -- West has it. Use I'm sure to move it to North.`

#### Undo

Archy wrote: `Undo add; works for selected hand; LIFO`.

- implement literal LIFO for selected hand: remove the oldest added card still present in the selected hand; or
- ask/confirm before changing to LIFO.

Undo behavior:

- if no matching selected-hand history entry exists: no mutation, status says nothing to undo;
- remove the chosen card from selected hand;
- remove that history entry from `addHistory`;
- clear `duplicateOverrideCandidate`;
- if board was complete, board becomes incomplete and camera can resume.

#### Scissors / manual remove

`removeCardFromSelectedHand(editState, card)`:

- only removes if selected hand contains card;
- removes all matching history records for that selected hand/card, or at least the oldest/current chosen record consistently;
- clears `duplicateOverrideCandidate`;
- returns clear status: `Removed ♦8 from North.`

#### Swap

`swapSelectedHandWith(editState, targetSeat)`:

- swaps the entire selected hand with the target hand;
- selected seat may remain unchanged unless UI/Archy says otherwise;
- clear `duplicateOverrideCandidate`;
- recommended MVP simplification: clear `addHistory` after a whole-hand swap.

Reason: after whole-hand swap, old add history becomes semantically confusing. A richer future version can remap history records, but MVP should avoid misleading Undo.

Status example:

```text
Swapped North and West hands.
```

#### I'm sure

`confirmDuplicateOverride(editState)` uses `duplicateOverrideCandidate`.

Enabled only when candidate exists and still matches current board state:

- `existingSeat` still contains `card`;
- `targetSeat` does not contain `card`;
- target hand has space unless moving within replacement semantics is explicitly designed;
- selected seat should normally equal `targetSeat`; if not, either reject or switch back to `targetSeat` consistently.

Behavior:

- remove card from `existingSeat`;
- add card to `targetSeat`;
- append `AddedCardRecord(targetSeat, card)` or clear history consistently;
- clear `duplicateOverrideCandidate`;
- status example: `Moved ♦8 from West to North. Marked previous West hit as false positive.`

If the candidate is stale:

- no mutation;
- clear the candidate;
- status says: `No longer actionable: duplicate context changed.`

### Auto-fill interaction

Any operation that removes a card from a complete board or hand must allow the board to become incomplete again.

Any operation that creates the condition “three hands complete, selected hand empty” may call `tryAutoFillFourthHand(...)`, but do not auto-fill after every recovery action unless tests make the behavior explicit. Conservative MVP: recovery operations do **not** auto-fill automatically except where current existing flow already does.

### Acceptance tests

Add or extend `EditBoardReducerTest`:

- successful scan appends add history;
- cross-hand duplicate stores `DuplicateOverrideCandidate`;
- `confirmDuplicateOverride` moves card from existing seat to target seat;
- stale duplicate override is rejected safely;
- undo selected hand removes the intended FIFO/LIFO card per confirmed semantics;
- undo does not affect another hand;
- scissors remove deletes selected card;
- swap exchanges two whole hands;
- clear hand/board clears or safely invalidates recovery state;
- recovery from board complete returns board to incomplete.

---

## 2. Ticket: Compose recovery controls on `EditBoardScreen`

### Purpose

Expose four TD recovery actions as large field-friendly buttons.

Requested buttons:

```text
Undo
Scissors
Swap
I'm sure
```

Use Archy’s screenshot as approximate placement guidance, not pixel doctrine.

### Layout guidance

Landscape cockpit currently has:

```text
Top row:    Board controls | North hand | Last scanned | Status
Middle row: West/back      | Camera/complete | East
Bottom row: Feed mode      | South hand | Orientation | PBN
```

Place buttons so they stay accessible when the board is complete. Do not put all recovery controls inside the central camera area, because `BoardCompleteView` replaces that area.

Approximate placements from Archy sketch:

- `Undo`: right side, near East hand / upper-right recovery zone.
- `Scissors`: right side, near East hand / lower-right recovery zone.
- `Swap`: left side, near West hand / lower-left recovery zone.
- `I'm sure`: upper-right status/control zone.

Exact Compose layout may differ if it keeps large touch targets and avoids crowding.

### Button enablement

#### Undo

Enabled when selected hand has at least one actionable add-history entry.

Label may remain `Undo` for MVP. Tooltip/help text not required.

#### Scissors

Enabled when selected hand is non-empty.

On click:

- modal surface full-screen for selected hand;
- display all cards in selected hand, grouped by suit in bridge order -- size of as in "Last scanned": fontSize = 50.sp;
- each card is clickable/removable;
- include close/cancel action;
- after removal, update state and status.

Dialog title example:

```text
Remove card from North
```

#### Swap

Enabled when selected hand is non-empty, or always enabled if swapping empty hands is harmless. Prefer enabled when there is something useful to swap.

On click:

- show chooser of the other three seats: fontSize = 50.sp;
- clicking target calls `swapSelectedHandWith(targetSeat)`;
- include cancel action;
- status reports the swap.

Example use case:

```text
TD scanned 13 cards into North, then realizes those physical cards were West.
Selected seat = North, Swap -> West.
```

#### I'm sure

Enabled only when current `BoardEditState.duplicateOverrideCandidate != null` and candidate target matches the selected hand.

Use it for this case:

```text
Scan says: cannot add ♦8 to North because West already has it.
TD knows West was a false positive.
I'm sure -> move ♦8 from West to North.
```

When disabled, it may remain visible but visually disabled.

### Status / last scanned updates

Use existing `lastResultMessage` for MVP, unless Bob decides to introduce a structured status model.

Required status messages:

- undo success / nothing to undo;
- scissors removal success;
- swap success;
- duplicate override success;
- duplicate override stale / unavailable.

`LastScannedCardArea` should remain “last accepted/scanned card” unless Bob explicitly changes its meaning. It should not silently show a card that was merely rejected unless UI copy makes that distinction clear.

### Board complete interaction

If board is complete:

- central area remains `BoardCompleteView`;
- recovery buttons remain available;
- Undo / Scissors / Swap / I'm sure can mutate board state;
- after mutation makes the board incomplete, central camera area returns.

### Accessibility / field usability

- Buttons must be large enough for tablet/landscape use.
- Avoid small icon-only controls for MVP; text labels are okay.
- Do not add animations as part of this ticket.

### Acceptance tests / previews

Depending on current UI test level, add at least Compose preview states or UI tests for:

- empty board: recovery controls mostly disabled;
- selected hand with cards: Undo/Scissors/Swap enabled;
- duplicate candidate present: `I'm sure` enabled;
- board complete: recovery controls still visible and enabled as applicable.

---

## 3. Ticket: update current TD design docs after implementation

### Purpose

Keep `docs/dev_history/td-edit/TD_EditBoard_design_2.md` current after recovery controls land.

Do **not** keep completed ticket files in `docs/dev_history/td-edit/` after the behavior is absorbed.

### Required doc updates

Add a concise section, likely after current scan flow / pure edit rules:

```text
TD recovery controls
```

Document:

- `Undo` semantics and whether it is literal FIFO or corrected LIFO;
- `Scissors` selected-hand card removal;
- `Swap` selected-hand whole-hand exchange;
- `I'm sure` duplicate override semantics;
- recovery state ownership (`BoardEditState` or app wrapper);
- whether add history is preserved, cleared, or remapped after swap/clear/removal;
- board-complete recovery behavior;
- status/last-scanned interpretation.

Update test anchors:

- add reducer tests for recovery actions;
- add any UI tests/previews Bob creates.

### Doc hygiene rule

When this ticket bundle is done:

```text
implementation ticket -> gone
current behavior -> absorbed into TD_EditBoard_design_2.md
architecture.md -> update only if module ownership/invariants change
```

This behavior is mostly TD design-level, not global architecture, unless Bob introduces a new cross-module recovery-state owner.

---

## 4. Non-goals for this bundle

Do not implement in this cycle unless Archy asks separately:

- full structured TD event rail/history;
- sound/haptics;
- Admin diagnostics for recovery actions;
- persistence of TD session across app restarts;
- AUTO orientation;
- snap mode;
- drag-and-drop card editing;
- traveller scoring / contract entry.

---

## 5. Final implementation note

This ticket bundle is about recovery instruments, not scanner cleverness.

The UI should make the TD feel:

```text
If the beetle or I made a mistake, I can recover locally.
I do not need to clear the board.
I do not need to distrust the whole session.
```

That is the field payoff.
