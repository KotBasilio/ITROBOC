# Implementation Plan - TD::EditBoard Recovery Controls

The goal is to implement "recovery controls" (Undo, Scissors, Swap, and I'm sure) in the `TD::EditBoard` screen. This involves updating core models and logic, and then exposing these actions in the UI.

## Proposed Changes

### core

#### [BoardEditState.kt](file:///C:/home/ITROBOC/core/src/main/kotlin/org/itroboc/core/BoardEditState.kt)

Add `AddedCardRecord`, `DuplicateOverrideCandidate` and update `BoardEditState`.

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
) {
    val highestNonEmptyBoardNumber: Int
        get() = ... // To be implemented later if needed in core, or keep in app. 
                    // Actually, the ticket says "highest non-empty board", 
                    // but it's referring to the session's boards.
}
```

Wait, the "highest non-empty board number" is about the entire session (set of boards), not a single `BoardEditState`. That should be in `TdSessionState`.

#### [HandState.kt](file:///C:/home/ITROBOC/core/src/main/kotlin/org/itroboc/core/HandState.kt)

Add `removeCard`.

```kotlin
    fun removeCard(card: CardId): HandState {
        return HandState(cards - card)
    }
```

#### [BoardState.kt](file:///C:/home/ITROBOC/core/src/main/kotlin/org/itroboc/core/BoardState.kt)

Add `removeCard` and `swapHands`.

```kotlin
    fun removeCard(seat: Seat, card: CardId): BoardState {
        return copy(hands = hands + (seat to hands.getValue(seat).removeCard(card)))
    }

    fun swapHands(a: Seat, b: Seat): BoardState {
        val handA = hands.getValue(a)
        val handB = hands.getValue(b)
        return copy(hands = hands + (a to handB) + (b to handC)) // error in my draft, fixing now
    }
```
Corrected `swapHands`:
```kotlin
    fun swapHands(a: Seat, b: Seat): BoardState {
        val handA = hands.getValue(a)
        val handB = hands.getValue(b)
        return copy(hands = hands + (a to handB) + (b to handA))
    }
```

#### [EditBoardReducer.kt](file:///C:/home/ITROBOC/core/src/main/kotlin/org/itroboc/core/EditBoardReducer.kt)

- Update `applyScannedCard` to track history and set `duplicateOverrideCandidate` on cross-hand duplicates.
- Implement `undoAddForSelectedHand`.
- Implement `removeCardFromSelectedHand`.
- Implement `swapSelectedHandWith`.
- Implement `confirmDuplicateOverride`.
- Update `clearSelectedHand` and `clearBoard` to reset recovery state.

### app

#### [TdSessionState.kt](file:///C:/home/ITROBOC/app/src/main/java/org/itroboc/app/TdSessionState.kt)

(Already updated in previous step)
- Add `highestNonEmptyBoardNumber` property.

#### [EditBoardScreen.kt](file:///C:/home/ITROBOC/app/src/main/java/org/itroboc/app/EditBoardScreen.kt)

- Add buttons for "Undo", "Scissors", "Swap", "I'm sure".
- Implement `TdSettingsScreen` (full-screen instead of dialog).
- Implement "Scissors" view (full-screen modal).
- Implement "Swap" view (full-screen modal).

## Verification Plan

### Automated Tests
- Extend `EditBoardReducerTest` in `core`.
- Run `./gradlew :core:test` and `./gradlew :app:testDebugUnitTest`.

### Manual Verification
- Verify in Compose Previews.
- Manual test in the app.
