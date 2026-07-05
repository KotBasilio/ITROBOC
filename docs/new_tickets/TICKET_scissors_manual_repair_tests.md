# Ticket: Lock Scissors manual repair behavior

Snapshot context: `524265c` Scissors rewrite.

## Intent

Scissors is now the selected-hand repair cockpit, not only card removal. It can manually add missing/unreadable/no-barcode cards from a 52-card grid and can move a card from another hand into the selected hand as an immediate sure-like correction.

The goal of this ticket is to lock the reducer/controller behavior with tests so future workbees do not accidentally change manual repair semantics.

## Non-goals

- Do not make Scissors manual add auto-advance.
- Do not make Scissors manual add immediately auto-fill fourth hand.
- Do not remove the living/playful Beetle Mind names.
- Do not reintroduce the legacy Mock screen as a current product surface.

## Required tests

### Core / reducer

Add tests in `EditBoardReducerTest.kt` or the closest existing reducer test file.

1. **Manual add clears pending duplicate candidate**
   - Given a pending `DuplicateOverrideCandidate` exists.
   - When `addManualCardToSelectedHand(...)` adds an unrelated unassigned card.
   - Then the card is added, selected-hand history is appended, and `duplicateOverrideCandidate == null`.

2. **Manual move from another hand is atomic and history-safe**
   - Given card `A` is in East and selected seat is North.
   - Given history contains `AddedCardRecord(East, A)`.
   - When North manually adds `A`.
   - Then `A` is removed from East, added to North, old East history is removed, and `AddedCardRecord(North, A)` is appended.

3. **Manual remove after manual add removes matching history**
   - Given selected hand manually adds card `A`.
   - When `removeCardFromSelectedHand(...)` removes `A`.
   - Then selected hand no longer has `A`, and matching selected-hand history is removed.

4. **Manual add does not auto-fill immediately**
   - Set up the manual-repair scenario where, after a manual add/move, another selected hand could be completed by the remaining deck.
   - When `addManualCardToSelectedHand(...)` runs.
   - Then no auto-fill occurs from that manual action alone.

5. **Seat selection remains the manual-repair auto-fill trigger**
   - After the same repair setup, selecting the complementary seat through controller/reducer path should trigger `tryAutoFillFourthHand(...)` when its conditions are met.

### App / controller

6. **Manual add blanks Beetle Mind**
   - Given `thoughts` / pending ponder state is non-idle.
   - When `onManualAddCard(...)` runs.
   - Then mind state is reset before/with manual repair, and final state/message reflects the manual add/move.

## Optional tiny cleanup

- Remove the duplicate `.background(...)` call in `SeatBorderLegendChip`.
- Consider an explicit `blankMind()` when the camera eye is closed by opening Scissors/Swap, if field testing shows stale thoughts after returning from modal screens. Keep this separate from the required behavior unless Architect asks for it.
