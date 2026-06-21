# Ticket 1 — Add Editable Deck-Profile Core Model

This plan implements the core domain logic for editing Deck Profile mappings in the `:core` module. It follows the "truth first" principle, ensuring that the profile editing logic is robust and testable before the UI is built.

## User Review Required

> [!NOTE]
> I'll be introducing a `DeckProfileEditor` class to manage the mutable state during an editing session, rather than making `DeckProfile` itself mutable. This keeps the core model clean and supports the "work on a copy" pattern for built-in profiles.

## Proposed Changes

### Core Module (:core)

#### [NEW] [DeckProfileEditResult.kt](file:///C:/home/ITROBOC/core/src/main/kotlin/org/itroboc/core/DeckProfileEditResult.kt)
- Define typed outcomes for assignment operations:
    - `Assigned(card, signature)`
    - `AlreadyAssignedToSelected(card, signature)`
    - `SignatureConflict(signature, existingCard, selectedCard)`
    - `NoCardSelected`

#### [NEW] [DeckProfileEditor.kt](file:///C:/home/ITROBOC/core/src/main/kotlin/org/itroboc/core/DeckProfileEditor.kt)
- Manage a mutable `Map<String, CardId>`.
- Provide methods for:
    - `assign(signature, card)`: Returns `DeckProfileEditResult`.
    - `remove(signature)`: Removes a mapping.
    - `getAliases(card)`: Returns all signatures mapped to a card.
    - `isMapped(card)`: Returns boolean.
    - `mappedCardCount()`: Returns count of unique cards with at least one mapping.
    - `isComplete()`: Returns true if all 52 cards are mapped.
    - `toDeckProfile()`: Produces a new immutable `DeckProfile`.

#### [DeckProfile.kt](file:///C:/home/ITROBOC/core/src/main/kotlin/org/itroboc/core/DeckProfile.kt)
- Add `toEditor()` extension or method to initialize a `DeckProfileEditor` from an existing profile.

---

### Tests

#### [NEW] [DeckProfileEditorTest.kt](file:///C:/home/ITROBOC/core/src/test/kotlin/org/itroboc/core/DeckProfileEditorTest.kt)
- Test suite covering all requirements from Ticket 1:
    - First signature assignment.
    - Adding aliases.
    - Handling duplicate assignments (harmless).
    - Handling conflicts (blocked).
    - Removing aliases.
    - Completeness and card counting.

## Verification Plan

### Automated Tests
- Run `./gradlew :core:test` to verify the new editor logic and ensure no regressions.
- Specific command: `./gradlew :core:test --tests "org.itroboc.core.DeckProfileEditorTest"`

### Manual Verification
- None required for this pure-core ticket; tests are the primary source of truth.
