# ITROBOC Tickets after EditBoard Review

Context: review of implementation pack `f746540`.

---

## Ticket 1 — Stop scanning when board is complete and show big PBN in center

### Goal

When the current board becomes complete, stop active scan feeding and switch the central area from camera/scanner to a large PBN display.

### Motivation

Once all 52 cards are assigned, further scan processing is no longer useful and may create noisy status updates. A completed board should visually “settle” and present the result clearly.

### Required behavior

When the board becomes complete:

- stop accepting new stream scan mutations for this board
- central camera area should switch to a board-complete view
- show PBN in a large readable font in the central area
- Status/PBN side zones may still show supporting information, but the central area should clearly say the board is complete
- selection may stay on the final/auto-filled hand

Suggested central text:

```text
Board complete

[PBN preview in large monospaced font]
```

### Notes

This is compatible with the current rule that Back silently preserves session state.

If the user clears a hand or clears the board, scanning can become active again.

### Acceptance criteria

- Complete a board by scan/auto-fill.
- Camera/feed stops mutating the board.
- Central area shows large PBN.
- Clearing a hand returns the board to incomplete state and scanning can resume.

---

## Ticket 2 — Add simple stream debounce

### Goal

Reduce repeated processing/noisy statuses while the same barcode remains visible in stream mode.

### Motivation

In stream mode, the analyzer can see the same card across many consecutive frames. BoardState rejects duplicates, but the UI may still receive repeated “already scanned” or repeated status churn.

### Required behavior

Add a simple debounce layer for stream mode.

Acceptable MVP policy:

- remember the last processed raw token and timestamp
- suppress the same raw token for a short window, e.g. 800–1500 ms

Better future policy:

- suppress the same raw token until it disappears or changes
- then allow it again

### Scope

This debounce should protect the feed path, not the low-level decoder. The decoder may continue producing debug results.

### Status behavior

Suppressed repeated detections should not overwrite useful Status text.

Optionally expose a small debug note somewhere:

```text
Repeated scan suppressed: bfm15A9
```

but do not flood the main Status area.

### Acceptance criteria

- Hold one card under camera.
- It is added at most once.
- Status does not churn repeatedly with duplicate messages.
- Move to a different card: it can be accepted.
- Return to the first card after debounce/disappearance: it can be processed again if legal.

---

## Ticket 3 — Extract EditBoard update rules into pure logic and unit-test them

### Goal

Move the important EditBoard state-transition rules out of Composable-local functions and into pure Kotlin logic that can be unit-tested.

### Motivation

EditBoard now contains real domain behavior:

- add scanned card to selected hand
- reject duplicates
- reject full-hand additions
- auto-advance after 13th card
- auto-fill forced fourth hand
- clear selected hand
- clear whole board
- stop feed when board complete

This logic should be testable without Compose and without camera.

### Suggested structure

Exact names are flexible, but something like this would work:

```kotlin
data class EditBoardUpdate(
    val state: BoardEditState,
    val message: String?,
    val boardCompleted: Boolean = false,
)

fun applyScannedCardToEditBoard(
    state: BoardEditState,
    card: CardId,
): EditBoardUpdate

fun tryAutoFillFourthHand(
    state: BoardEditState,
): EditBoardUpdate

fun clearSelectedHand(
    state: BoardEditState,
): EditBoardUpdate

fun clearBoard(
    state: BoardEditState,
): EditBoardUpdate
```

or a reducer-style API:

```kotlin
fun reduceEditBoard(
    state: BoardEditState,
    action: EditBoardAction,
): EditBoardUpdate
```

### Required rules to test

#### Add card

- adding a new card to selected incomplete hand succeeds
- adding a card already in selected hand does nothing
- adding a card already in another hand does nothing
- adding to a complete hand does nothing

#### Auto-advance

- when selected hand receives its 13th card, selection advances:
  `North -> East -> South -> West`

#### Auto-fill

- when selected hand is empty and other three hands are complete, fill selected hand with remaining 13 cards
- after auto-fill, selection stays on the auto-filled hand
- auto-fill does not run if selected hand is nonempty
- auto-fill does not run if fewer/more than exactly 13 cards remain
- auto-fill does not run if already-assigned cards are invalid/duplicated

#### Clear

- `Clear hand` clears selected hand only
- `Clear board` clears all hands after confirmation path
- clearing selected hand makes board incomplete again

#### Board complete

- completed board has 52 unique cards and four complete hands
- completed board should stop feed mutations until user clears something

### Acceptance criteria

- Core EditBoard rules are unit-tested without Compose.
- Compose screen delegates to these pure rules rather than duplicating them.
- Behavior remains the same from user perspective.

---

## Ticket 4 — Keep UnknownSignature quiet but non-stale

### Goal

Preserve the intentional anti-flood behavior for unknown signatures, while avoiding stale/misleading Status.

### Context

Unknown signatures were silenced because they can flood Status during live scanning. That is reasonable.

The remaining risk is stale Status: if the last visible message says “Added SA” and then the camera repeatedly sees unknown tokens, the user may not realize the current stream is unknown.

### Suggested MVP behavior

Do not update Status on every unknown frame.

Instead, keep a throttled unknown indicator:

- update at most once every few seconds
- or show a small secondary line/counter
- do not overwrite a valuable recent success immediately

Example:

```text
Unknown signature seen: bfm16AD
Check orientation/profile.
```

or compact:

```text
Unknown signatures suppressed: 7
```

### Acceptance criteria

- Unknown-signature floods do not spam Status.
- Status is not misleadingly stale forever.
- A TD can tell when the scanner is seeing unknown codes.

This ticket is lower priority than debounce and pure rule extraction.

---

## Ticket 5 — Show scans-per-second in TD::EditBoard::StatusArea

Goal
Add a lightweight live scans-per-second indicator to TD::EditBoard::StatusArea so Archy can run the app on device and observe current scan throughput during real camera use.

Why
Before optimizing Grid13 scanning further, we want an on-screen performance signal grounded in actual runtime behavior. This should help compare builds and scanner changes quickly during manual playtesting.

Scope
- TD::EditBoard only
- Show current scans-per-second in StatusArea
- Keep UI simple and readable
- No deep redesign of the screen
- No persistence needed

Definition
- “scan” here means one completed decoder attempt / analysis cycle that reaches the current TD stream-scanning result path
- Prefer measuring actual completed scan cycles, not camera frame delivery rate
- If there is already a natural event where a scan result is emitted, hook into that

Requested behavior
- Add a text line in StatusArea, for example:
  - `FPS 7.8`
  - that short line meaning not literally `frames per second`, but `Scan rate: 7.8 / s`
- Update continuously while stream scanning is active
- Smooth the value enough to avoid wild flicker
  - recommended: rolling 1-second window, or EMA-style smoothing
- When no recent scans happened, show `No scans`
- Keep formatting stable to one decimal place

Implementation guidance
- Keep logic local and simple
- Prefer a small UI-facing state holder or helper rather than scattering timing code
- Avoid polluting core domain logic with Android/UI timing concerns
- If possible, measure timestamps at the point where scan results are already processed in TD::EditBoard flow
- Make sure recomposition churn stays reasonable

Acceptance criteria
- While TD stream scanning is running, StatusArea shows live scans-per-second
- The number changes in response to real runtime throughput
- It reflects decoder attempt throughput, not just camera preview FPS
- App still builds and existing behavior remains intact

Nice-to-have
- If there is a clean place, also make it easy to later display:
  - average scan latency in ms, i.e. `FPS 7.8; L120`
  - accepted vs rejected scans, i.e. `FPS 7.8; L120; R23%`

Notes
- This is for manual measurement by Archy on device
- Simplicity is preferred over overengineering
- Preserve existing UI style unless a tiny label addition needs minor spacing tweaks

---

## EditBoard Tickets rehash / backlog 

EBT-1. Clarify/remove signatureFor vs viewedAs:
   EditBoard uses viewedAs(mode). Add tests for viewedAs. Rename or remove signatureFor if it represents only debug reverseSignature.

EBT-2. Complete-board mode:
   stop scan mutations when board complete and show large PBN in center.

EBT-3. Preserve auto-fill status:
   do not overwrite the fourth-hand auto-fill message.

EBT-4. Add stream debounce:
   suppress repeated same token/card while it remains visible.

EBT-5. Extract EditBoard reducer:
   pure-test add, duplicate, auto-advance, auto-fill, clear-hand, clear-board, board-complete rules.

EBT-6. Clear stale UI feedback:
   after Clear hand / Clear board, update Status and clear Last scanned if appropriate.

EBT-7. Use semantic boardComplete helper in UI:
   prefer BoardProgressSummary.boardComplete or equivalent over raw totalCardCount == 52.

EBT-8. Show scans-per-second.

---

## EditBoard Tickets Backlog Progress

MOVE `EBT` TICKETS HERE ONCE DONE.

