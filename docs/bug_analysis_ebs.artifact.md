# Bug Analysis: Card Scanning Regressions in EditBoardScreen

I've analyzed the two regressions reported after the vision analyzer fix. Here are the findings and the proposed path forward.

## 1. Selected Seat Resets to North
**Symptom:** Every time a card is scanned, the selected seat jumps back to North.
**Analysis:** In `EditBoardScreen.kt`, the `selectedSeat` variable is derived from the `boardEditState` passed as a parameter:

```kotlin
val selectedSeat = boardEditState.selectedSeat
```

However, when a scan happens, the `handleScan` function is called, which calls `EditBoardReducer.applyScannedCard(boardEditState, ...)` and then triggers `onBoardEditStateChange`.

The bug is likely in how the state is hoisted in `MainActivity.kt`. If the `sessionState` update doesn't correctly preserve the `selectedSeat` when updating the `boards` map, or if the `boardEditState` is being reconstructed with default values (where `selectedSeat` defaults to `NORTH`), we see this reset.

## 2. Cards Don't Accumulate
**Symptom:** Scanned cards appear momentarily but don't stay in the hand.
**Analysis:** This is a classic "state loss" issue. In `EditBoardScreen.kt`, `handleScan` uses the *current* `boardEditState` to calculate the next state:

```kotlin
val update = EditBoardReducer.applyScannedCard(boardEditState, ...)
onBoardEditStateChange(update.state)
```

If `boardEditState` is "stale" (e.g., if multiple scans happen quickly and the UI hasn't recomposed with the new state yet), each `handleScan` call starts from an old version of the board.

## Proposed Fixes

### A. Use `rememberUpdatedState` for the board state
We need to ensure `handleScan` always uses the absolute latest state. While Compose handles this usually, the high frequency of scans from the analyzer thread can lead to race conditions where `boardEditState` in the closure is older than the one in the `TdSessionState`.

### B. Verify Reducer Logic
I've checked `EditBoardReducer.kt`. It correctly uses `.copy(boardState = updatedBoard)` and preserves other fields. It *does* implement auto-advance, which moves the seat to the `next()` one when a hand reaches 13 cards. If a hand is *already* complete, it also auto-advances.

### C. Manual UI Polish Preservation
**Fi ✂️💋:** I've triple-checked `MainMenuScreen.kt`. Your manual changes are safe! We're only touching the core logic and the `EditBoardScreen`.

## Bob's C++ Wisdom 🧩
**Bob ☕🧩:** This feels like a "dirty bit" or "atomic swap" issue. We're sending updates to the "main thread" (the UI), but the analyzer keeps firing. We should probably use a more robust state update pattern or ensure the `onBoardEditStateChange` lambda is captured correctly.

---

**Trace 🫖🧭:** We're ready to update the implementation plan to fix these two regressions. Shall we proceed?
