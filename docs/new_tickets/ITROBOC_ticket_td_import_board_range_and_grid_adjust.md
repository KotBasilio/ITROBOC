# ITROBOC Ticket — TD PBN Import: Board Range Validation and Grid Adjustment

## Goal

Make TD PBN import robust around board numbers:

1. Reject weird or unsupported board numbers.
2. Accept boards `1..39`.
3. Automatically adjust `Total boards in grid` upward when imported boards require more visible slots.

## Context

TD session export/import now supports cumulative PBN exchange. This is useful for real club workflows. The current overview grid has been fixed to 30 boards, while import can encounter board numbers outside that visible range.

With the new configurable grid setting, import should cooperate with the visible board range instead of creating unreachable imported boards.

## Board Number Policy

For MVP:

```text
valid board numbers: 1..39
invalid board numbers: <= 0 or > 39
```

Invalid/weird boards should be rejected/skipped during import, not inserted into session state.

Examples:

```text
Board 0   -> reject
Board -1  -> reject
Board 40  -> reject
Board 99  -> reject
Board 1   -> accept
Board 31  -> accept and expand grid if needed
Board 39  -> accept and expand grid if needed
```

## Grid Adjustment Policy

If import accepts a board number greater than the current visible grid size, increase `totalBoardsInGrid` to the smallest allowed grid size that can display the largest accepted board.

Allowed grid sizes:

```text
15, 18, 21, 24, 27, 30, 33, 36, 39
```

Examples:

```text
current grid = 30, import Board 31 -> grid becomes 33
current grid = 30, import Board 33 -> grid becomes 33
current grid = 30, import Board 34 -> grid becomes 36
current grid = 24, import Board 39 -> grid becomes 39
current grid = 36, import Board 39 -> grid becomes 39
current grid = 39, import Board 39 -> grid stays 39
```

If import contains only invalid boards above 39, do not expand the grid.

## Import Behavior

Current good behavior should be preserved:

- Complete imported boards add or overwrite existing complete boards.
- Invalid/partial PBN blocks are ignored/skipped.
- Existing partial scanned boards are not destroyed unless a complete imported board with the same board number intentionally overwrites that board.
- Selected seat should be preserved for existing board edit state where applicable.

Add a user-visible import result message if practical:

```text
Imported 8 boards. Skipped 2 invalid boards. Grid expanded to 36.
```

If detailed messaging is too much for MVP, at least log skipped invalid board numbers in debug output.

## Suggested Helpers

```kotlin
const val MaxSupportedImportedBoard = 39
const val MinSupportedImportedBoard = 1

fun isSupportedTdBoardNumber(boardNumber: Int): Boolean =
    boardNumber in 1..39

fun gridSizeForBoard(boardNumber: Int): Int? =
    AllowedTdBoardGridSizes.firstOrNull { it >= boardNumber }
```

## Acceptance Criteria

- Import accepts complete boards numbered `1..39`.
- Import rejects/skips boards numbered `<= 0` or `> 39`.
- Import does not create session state for rejected board numbers.
- Import expands `totalBoardsInGrid` to the smallest allowed size needed for the largest accepted imported board.
- Import does not expand the grid for rejected board numbers.
- Overview shows newly imported boards after grid expansion.
- Filled/completed counter updates against the adjusted grid total.
- Add tests for:
  - importing Board 31 expands grid 30 -> 33
  - importing Board 34 expands grid 30 -> 36
  - importing Board 39 expands grid 30 -> 39
  - importing Board 40 is skipped and does not expand grid
  - mixed import with Board 32 and Board 99 expands only to 33 and skips 99
