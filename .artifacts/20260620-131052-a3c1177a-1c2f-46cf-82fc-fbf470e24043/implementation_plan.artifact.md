# Ticket 3 — Polish Admin::Edit behavior and docs

This plan finalizes the `Admin::Edit` prototype by polishing the status messaging and ensuring clear communication during the calibration flow.

## User Review Required

> [!NOTE]
> I've already adjusted the card buttons to be 4 times shorter as requested, ensuring the 52-card grid fits vertically on the screen.

## Proposed Changes

### Android App (:app)

#### [AdminEditScreen.kt](file:///C:/home/ITROBOC/app/src/main/java/org/itroboc/app/AdminEditScreen.kt)
- Enhance `lastResultMessage` logic to use more descriptive strings:
    - "No card selected" (though unlikely with default selection).
    - "No signature detected" (mock result).
    - "Signature 0x1234 assigned to ♦A".
    - "Alias 0x1234 already exists for ♦A".
    - "CONFLICT: 0x1234 is already mapped to ♣7".
    - "Removed alias 0x1234".
- Add a "Profile complete!" message when all 52 cards are mapped.

### Documentation

#### [README.md](file:///C:/home/ITROBOC/README.md)
- Finalize notes on `Admin::Edit`:
    - Mention support for aliases/multiple signatures.
    - Clarify that real camera/storage are future work.

## Verification Plan

### Automated Tests
- `./gradlew :core:test`
- `./gradlew :app:assembleDebug`

### Manual Verification
1. Open Admin Edit.
2. Select a card, Scan -> Verify success message.
3. Scan same card again -> Verify "already exists" message.
4. Select different card, try to scan/enter same signature -> Verify "CONFLICT" message.
5. Click alias -> Confirm removal -> Verify "Removed" message.
6. Fill all cards (mock) -> Verify "Profile complete!" message.
