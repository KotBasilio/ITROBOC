# ITROBOC TD::EditBoard Design

## Status

This document describes the intended first implementation design for `TD::EditBoard`.

`TD::EditBoard` is a landscape-first, board-centered scanning screen for editing one bridge board inside the current TD session.

Design motto:

> The board is a table; the beetle is in the middle; each scan feeds one seated hand.

This screen is not primarily a PBN editor. It is a spatial board-feeding cockpit.

## Product intent

The TD needs to convert physical dealt cards into a digital board record quickly and safely.

The target workflow is:

```text
Open board -> select hand -> scan barcode cards into that hand -> auto-advance -> auto-fill fourth hand when forced -> return to board overview
```

The camera/scanner should behave like a small focused field unit: compact, persistent, close to surfaces, and precise.

## Layout

The screen has 9 conceptual areas, arranged as a 3x3 board cockpit:

```text
Board controls      North hand       Status

West hand           Camera           East hand

Orientation         South hand       Feed mode
```

The areas are not equal in size.

The camera is the central active area. The four hands surround it according to bridge table geometry.

## Landscape-first

This design is landscape/tablet-first.

Phone portrait can be handled later with a fallback layout, likely camera-first with tabs/sections for hands and status.

For now, Bob should implement the landscape layout shell cleanly and keep responsive seams visible rather than over-solving portrait.

## Session model

`TD::EditBoard` edits one board inside a current session.

The app should hold a current session structure conceptually like:

```kotlin
SessionState(
    boards: Map<BoardNumber, BoardEditState>
)
```

The exact type names may differ.

Important rule:

> Board editing must not be only local Composable state that disappears on Back.

Changes should update the current session state immediately or through a ViewModel/session store.

There is no per-board Save button.

Back silently preserves the current board state in the current session and returns to Board Overview.

Incomplete boards are saved too.

## Board controls area

Top-left area.

Contains:

- large board label, e.g. `Board 7`
- optional small session/debug line
- `Clear hand` / `Clear board`
- `Back`

No `Save` button.

### Dynamic Clear button

The same control changes meaning depending on the selected hand.

If the selected hand contains one or more cards:

```text
Clear hand
```

Action: clear only the selected hand.

If the selected hand is empty:

```text
Clear board
```

Action: attempt to clear the entire board, with confirmation.

### Clear board confirmation

Whole-board clear must ask for confirmation.

Suggested dialog:

```text
Clear entire board?

This removes all cards from Board N.

Cancel / Clear board
```

The selected-hand clear may be immediate, unless Bob thinks consistency suggests a confirmation. Current design preference: selected-hand clear can be immediate.

### Back behavior

Back always returns to Board Overview and silently preserves the current board state in session memory.

No save/discard prompt is needed under the current design because there is no separate per-board save step.

## Hand areas

Four hand areas surround the camera:

- North at top-center
- West at middle-left
- East at middle-right
- South at bottom-center

The TD knows the seats spatially, so seat name labels are not required inside the hand areas.

However, the selected hand must be very visually obvious.

### Hand display content

Each hand area should show suit-grouped cards only.

Example:

```text
♠ A K 7
♥ Q 9 3
♦ —
♣ J T 6
```

Do not show counts inside hand areas. Counts and progress belong in Status.

### Hand state color

Use background state color:

- yellow / pale yellow = empty or partial
- green = complete

Do not use red/pink as a hand conflict state.

Invalid or duplicate scans are rejected and reported in Status. A hand should not accept a card that is already elsewhere.

### Selected hand indication

The selected hand must have a very visible active border, glow, or strong outline.

This is important even if the spatial layout is obvious.

The center camera/status overlay may also show:

```text
Feeding: North
```

or equivalent.

## Hand selection

The TD selects the active hand by tapping North/East/South/West.

Scans go into the currently selected hand.

Selection order for auto-advance:

```text
North -> East -> South -> West -> North
```

When a hand receives its 13th card, selection automatically advances to the next hand in that order.

After auto-fill completes the last hand, selection stays on that auto-filled hand.

## Auto-fill fourth hand

When the selected hand is empty and the other three hands are complete, the app should auto-fill the selected hand with the remaining 13 cards from the 52-card deck.

Example:

```text
North complete
East complete
South complete
West selected and empty
```

Then West is auto-filled with every card not already assigned to North/East/South.

Status should report:

```text
West auto-filled from remaining 13 cards.
Board complete.
```

This avoids scanning the fourth pile when bridge logic makes it forced.

Auto-fill should only happen when:

- selected hand is empty
- exactly three other hands are complete
- the set of already assigned cards is valid
- exactly 13 cards remain unassigned

If these conditions are not met, do not auto-fill.

## Camera area

Center area.

The camera area is the active scanning zone.

It should include:

- live camera preview or placeholder
- small guide rectangle / beetle mouth
- selected-hand indication if helpful
- last scan summary if helpful

The camera area should stay focused and precise, not greedy.

## Scan behavior

### Stream mode

`stream` is the active MVP feed mode.

In stream mode, the scanner tries to add a newly decoded card to the currently selected hand.

Expected behavior:

1. Camera detects raw signature.
2. Orientation setting determines lookup family.
3. Deck Profile maps raw signature to CardId.
4. App attempts to add CardId to selected hand.
5. Status reports result.
6. If hand reaches 13, auto-advance selection.
7. If selected empty hand is now forced as fourth hand, auto-fill.

### Duplicate handling

If scanned card is already in the selected hand:

- do nothing
- Status says something like `Already in selected hand`

If scanned card is already in another hand:

- do nothing
- Status says something like `Already in East`
- do not create a conflict state in the target hand

If selected hand is already complete:

- do not add
- Status says selected hand is complete
- optionally auto-advance to the next hand if that feels safe

### Debounce / stability

Live stream scanning should suppress repeated detections of the same visible card.

Recommended but not over-specified:

- ignore exact repeated raw signature for a short debounce window
- or require the raw signature to disappear/change before adding the same CardId again
- Status can still show repeated detection without mutating state

## Orientation area

Bottom-left.

Controls barcode orientation family.

Options:

```text
bfm
brm
auto
```

For now:

- `bfm` is implemented
- `brm` is implemented
- `auto` is a placeholder

`auto` should preferably be disabled until implemented. If selectable, Status must clearly say:

```text
Auto orientation not implemented yet.
```

Do not make `auto` silently guess yet.

### Orientation meaning

`bfm` and `brm` are raw-signature families. They are not canonicalized.

When orientation is `bfm`, the scanner/lookup should use the `bfm...` token.

When orientation is `brm`, the scanner/lookup should use the `brm...` token.

This preserves physical orientation identity.

## Feed mode area

Bottom-right.

Options:

```text
stream
snap
```

For now:

- `stream` is the active mode
- `snap` is a placeholder

`snap` is reserved for a future TD multi-barcode scan workflow, connected conceptually to the future vision ticket for multi-barcode scanning.

For now, `snap` should preferably be disabled. If selectable, Status must clearly say:

```text
Snap mode not implemented yet.
```

## Status area

Top-right.

This replaces the previous “PBN area.”

Status is a general board/debug/progress area.

PBN appears here only when the entire board is complete.

Before completion, Status may show:

- selected hand
- hand counts/progress
- board completion progress
- last raw signature
- last decoded card
- duplicate/rejected scan messages
- unknown signature
- orientation/feed mode placeholders
- auto-fill messages
- camera/decoder debug summary

Examples:

```text
Feeding: North
North: 7/13
Board: 31/52
Last: bfm12A5 -> SK
```

```text
Already in East: DQ
No change.
```

```text
Unknown signature: bfm16AD
Check profile or orientation.
```

```text
West auto-filled from remaining 13 cards.
Board complete.
```

When board is complete, Status should show a PBN preview and validation message.

Example:

```text
Board complete.

[PBN preview here]
```

## PBN behavior

PBN is not the primary editing surface.

PBN preview appears in Status only after the board is complete.

Before the board is complete, Status focuses on scan/debug/progress.

PBN export/session export can be implemented elsewhere or later.

## Data / domain behavior

The board state should reject illegal placement.

A card can appear at most once on a board.

Adding a card already present elsewhere does nothing and reports to Status.

Adding a card already present in selected hand does nothing and reports to Status.

Hand completion is exactly 13 cards.

Board completion is all four hands complete and 52 unique cards assigned.

## Suggested MVP implementation path

### Step 1 — Layout shell

Implement the 9-zone layout using placeholder content.

No camera logic changes required.

### Step 2 — Session-backed board state

Add or wire a session-level board store so EditBoard state survives Back.

### Step 3 — Hand selection and clear behavior

Implement selected hand, active border, dynamic Clear hand / Clear board, and whole-board confirmation.

### Step 4 — Mock scan feed

Use a mock scan input or existing debug scanner to add decoded cards to selected hand. Validate duplicate behavior, auto-advance, and auto-fill.

### Step 5 — Wire existing camera stream

Connect real Grid13 scan results to the same feed path used by mock scan.

### Step 6 — Status and PBN

Add Status content and show PBN only when board complete.

## Non-goals for first pass

Do not implement TD multi-barcode snap mode yet.

Do not implement auto orientation yet.

Do not optimize phone portrait layout yet.

Do not make PBN the main editing surface.

Do not allow conflicts to be stored in hand state.

## Acceptance criteria

- Screen has the 9 conceptual areas in landscape.
- Four hands are spatially positioned around camera.
- Tapping a hand selects it.
- Selected hand has a visible active border.
- Scans/mock scans add to selected hand.
- Duplicate scans do not mutate board state.
- Hand reaching 13 cards auto-advances selection.
- Empty selected fourth hand auto-fills when other three hands are complete.
- Clear hand clears selected hand.
- Clear board appears when selected hand is empty and asks confirmation.
- Back preserves board state in session and returns to Board Overview.
- Status area shows progress/debug before completion.
- Status area shows PBN only when board complete.
- Orientation `auto` and feed `snap` are placeholders.
