# ITROBOC Ticket — TD Overview: Flexible Total Boards Grid

## Goal

Make the TD Overview board grid configurable so tournament directors can choose how many boards are visible in the session grid, while preserving the current clean three-row layout.

## Context

The current TD Overview grid is fixed to 30 boards. This is good for many club events, but real sessions may use fewer or more boards. Import/export logic is now moving toward accepting board numbers beyond 30, so the visible grid should not hide valid imported boards.

There is already a **Settings** button on the TD screen. Use it to expose a small controlled setting rather than a free-form text field.

## UX Requirement

Add a TD setting:

```text
Total boards in grid
```

Expose it as a button group or selectable chips, not an edit field.

Allowed values:

```text
15, 18, 21, 24, 27, 30, 33, 36, 39
```

Rules:

```text
minimum = 15
maximum = 39
default = 30
values must be divisible by 3
```

The reason for divisible-by-3 values: the overview grid should always keep three visually balanced rows.

## Expected Behavior

1. New sessions default to `totalBoardsInGrid = 30`.
2. TD can open Settings and select another allowed value.
3. The overview grid renders boards `1..totalBoardsInGrid`.
4. Filled/completed count should use the configured total:
   - Example: `Filled: 12/24`
5. Existing board states outside the currently visible range should not be deleted automatically.
6. If the TD later increases the grid size again, previously imported/scanned boards in that range should reappear.

## Data / State Notes

Prefer storing this in TD session-level UI/session state, not as a global app preference unless Bob already has a clear settings model.

Possible model field:

```kotlin
data class TdSessionState(
    val boards: Map<Int, BoardEditState>,
    val totalBoardsInGrid: Int = 30,
    ...
)
```

If there is already a TD settings state object, add it there instead.

## Validation

When setting `totalBoardsInGrid`, reject or clamp values outside the allowed set. Prefer rejecting invalid internal values in reducer/helper code so UI and import behavior stay predictable.

Suggested helper:

```kotlin
val AllowedTdBoardGridSizes = listOf(15, 18, 21, 24, 27, 30, 33, 36, 39)

fun normalizeTdBoardGridSize(value: Int): Int =
    if (value in AllowedTdBoardGridSizes) value else 30
```

Or use a stricter result if Bob prefers explicit invalid-state handling.

## Acceptance Criteria

- TD Overview grid no longer hardcodes `1..30`.
- Default visible grid is still 30 boards.
- Settings UI offers only: `15, 18, 21, 24, 27, 30, 33, 36, 39`.
- Grid always renders three rows cleanly.
- Filled/completed counter uses the configured total.
- Boards outside the current visible range are preserved, not deleted.
- Existing tests for 30-board behavior still pass.
- Add tests for at least:
  - default = 30
  - selecting 24 shows boards 1..24
  - selecting 39 shows boards 1..39
  - completed counter uses configured total
  - hidden-but-existing board state is preserved when grid size decreases
