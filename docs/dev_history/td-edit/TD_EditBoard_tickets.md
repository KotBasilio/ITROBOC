# Bob Tickets: TD::EditBoard MVP

## Ticket 1 — Add TD::EditBoard 9-zone layout shell

### Goal

Create the first landscape layout shell for `TD::EditBoard`.

### Layout

Use the 9 conceptual areas:

```text
Board controls      North hand       Status

West hand           Camera           East hand

Orientation         South hand       Feed mode
```

The areas do not need to be equal size.

### Required behavior

- Show board number in Board controls area.
- Show placeholder hand panels for North/East/South/West.
- Show central camera placeholder.
- Show Status placeholder.
- Show Orientation controls: `bfm`, `brm`, `auto`.
- Show Feed mode controls: `stream`, `snap`.
- No Save button.

### Notes

This screen is landscape-first. Do not over-solve phone portrait in this ticket.

### Acceptance

- User can navigate to EditBoard for a selected board.
- The visual structure matches the 9-zone cockpit sketch.
- Existing Board Overview navigation still works.

---

## Ticket 2 — Add session-backed board edit state

### Goal

Make EditBoard state belong to the current TD session, not only transient Composable state.

### Required behavior

- App has a current session containing multiple boards.
- EditBoard edits one board by board number.
- Back silently preserves board state in the current session.
- Incomplete boards are preserved too.
- Board Overview can reflect board completion/partial state.

### Implementation note

Exact type names are flexible, but conceptually:

```kotlin
SessionState(
    boards: Map<BoardNumber, BoardEditState>
)
```

or equivalent.

### Acceptance

- Add cards to Board N.
- Back to overview.
- Reopen Board N.
- Cards are still there.

---

## Ticket 3 — Implement hand panels, selection, and active border

### Goal

Show four spatial hand panels and allow the TD to select the active hand.

### Required behavior

- North/East/South/West panels are spatially positioned around camera.
- Tapping a hand selects it.
- Selected hand has a very visible border/glow/outline.
- Scans feed the selected hand.
- Seat name labels inside the hand panels are not required.

### Hand content

Show suit-grouped cards only:

```text
♠ A K 7
♥ Q 9 3
♦ —
♣ J T 6
```

Do not show counts inside hand panels. Counts belong in Status.

### Hand colors

- yellow/pale yellow = empty or partial
- green = complete

Do not use red conflict hand state. Invalid scans are rejected and shown in Status.

### Acceptance

- User can tap each hand.
- Active hand is obvious.
- Hand contents render suit-grouped cards.

---

## Ticket 4 — Implement dynamic Clear hand / Clear board

### Goal

Replace fixed Clear behavior with selected-hand-aware behavior.

### Required behavior

If selected hand has cards:

- button label: `Clear hand`
- clears selected hand only
- no board-wide clear

If selected hand is empty:

- button label: `Clear board`
- asks confirmation
- if confirmed, clears the entire board

### Confirmation text

Suggested:

```text
Clear entire board?

This removes all cards from Board N.

Cancel / Clear board
```

### Acceptance

- Nonempty selected hand clears only that hand.
- Empty selected hand triggers whole-board confirmation.
- Cancel does nothing.
- Confirm clears all four hands.

---

## Ticket 5 — Implement stream feed path with mock scan first

### Goal

Create the scan feed logic independent of camera source.

### Required behavior

A scan result attempts to add a decoded CardId to the selected hand.

Rules:

- If selected hand has fewer than 13 cards and card is not on board, add it.
- If card is already in selected hand, do nothing and report Status.
- If card is already in another hand, do nothing and report Status.
- If selected hand is complete, do nothing and report Status.

### Status examples

```text
Added SK to North.
```

```text
Already in East: DQ.
No change.
```

```text
North already has 13 cards.
No change.
```

### Acceptance

- A mock scan/debug button can feed known CardIds.
- Duplicate handling works.
- No illegal card duplication is stored.

---

## Ticket 6 — Auto-advance selected hand after 13th card

### Goal

When a hand becomes complete, automatically move selection to the next hand.

### Order

```text
North -> East -> South -> West -> North
```

### Required behavior

- If North receives its 13th card, selection moves to East.
- If East completes, selection moves to South.
- If South completes, selection moves to West.
- If West completes, selection may remain on West or cycle to North only if that is useful; prefer stable behavior if board is complete.

### Acceptance

- Completing a hand advances selection.
- Status reports the completed hand and new selected hand.

---

## Ticket 7 — Auto-fill fourth hand from remaining cards

### Goal

Avoid scanning the fourth pile when it is mathematically forced.

### Required behavior

When selected hand is empty and the other three hands are complete:

- compute all unassigned cards from the 52-card deck
- if exactly 13 remain, assign them to selected hand
- keep selection on the auto-filled hand
- report Status

### Status example

```text
West auto-filled from remaining 13 cards.
Board complete.
```

### Preconditions

Only auto-fill if:

- selected hand is empty
- exactly three other hands are complete
- already assigned cards are valid and unique
- exactly 13 cards remain unassigned

Otherwise do not auto-fill.

### Acceptance

- Scan/fill three complete hands.
- Select empty fourth hand.
- Fourth hand auto-fills.
- Board becomes complete.

---

## Ticket 8 — Implement Status area behavior

### Goal

Use top-right Status area for progress/debug until board completion, then PBN.

### Before board complete

Status may show:

- selected hand
- per-hand counts
- board total count
- last raw signature
- last decoded CardId
- duplicate/rejection messages
- unknown signature
- orientation/feed placeholders
- auto-fill messages

### After board complete

Status shows:

- Board complete
- PBN preview

### Acceptance

- PBN is not shown while board incomplete.
- Status gives useful scan feedback.
- PBN appears when board complete.

---

## Ticket 9 — Wire Grid13 camera stream into EditBoard feed path

### Goal

Use existing Grid13 camera decoder as an input source for the same feed path used by mock scan.

### Required behavior

- In `stream` mode, stable camera detections attempt to feed selected hand.
- Orientation `bfm` uses `bfm...` lookup.
- Orientation `brm` uses `brm...` lookup.
- Unknown signature reports Status and does not mutate board.
- Duplicate/card-already-on-board reports Status and does not mutate board.

### Debounce

Suppress repeated detections of the same visible card so one card does not get repeatedly processed.

Exact debounce policy may be simple for MVP.

### Acceptance

- Scanning a real card adds it to selected hand.
- Repeated same-card detection does not spam mutations.
- Orientation selection affects lookup family.

---

## Ticket 10 — Placeholder behavior for `auto` orientation and `snap` feed mode

### Goal

Make future modes visible but not misleading.

### Orientation

`auto` is placeholder for now.

Preferred behavior:

- disabled radio option

Alternative acceptable behavior:

- selectable but Status says `Auto orientation not implemented yet`
- no automatic guessing

### Feed mode

`snap` is placeholder for future TD multi-barcode scan.

Preferred behavior:

- disabled radio option

Alternative acceptable behavior:

- selectable but Status says `Snap mode not implemented yet`
- no multi-barcode behavior yet

### Acceptance

- User cannot mistake placeholder modes for working features.

---

## Ticket 11 — Board Overview integration

### Goal

Ensure Board Overview reflects session board state.

### Required behavior

Board Overview should indicate board status from current session:

- empty
- partial
- complete

The exact visual colors may reuse existing overview colors.

### Acceptance

- Edit a board partially.
- Back to overview.
- Board shows partial.
- Complete board.
- Back to overview.
- Board shows complete.
