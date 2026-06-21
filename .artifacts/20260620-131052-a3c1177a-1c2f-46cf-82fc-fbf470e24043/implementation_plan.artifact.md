# Ticket 2 — Add Admin::Edit UI shell with mock scanner

This plan adds the first `Admin::Edit` screen in `:app`, using the editable profile logic from core and a mock signature source.

## Proposed Changes

### Android App (:app)

#### [Screen.kt](file:///C:/home/ITROBOC/app/src/main/java/org/itroboc/app/Screen.kt)
- Add `object AdminEdit : Screen()` for the editing screen.

#### [NEW] [AdminEditScreen.kt](file:///C:/home/ITROBOC/app/src/main/java/org/itroboc/app/AdminEditScreen.kt)
- Implement `AdminEditScreen` with:
    - Left part: 4x13 grid of 52 card buttons.
    - Colors: Green (mapped), Yellow (unmapped).
    - Thicker border for the selected card.
    - Right part:
        - Mock camera preview placeholder (upper 66%).
        - Selected card status (name, mapped status).
        - Alias list for the selected card.
        - Controls row: Scan, Save, Back, Auto-advance toggle.
- `Scan` logic:
    - Generate a mock signature (e.g., "mock-0x1234").
    - Call `editor.assign()`.
    - Handle results (auto-advance on success if enabled).

#### [MainActivity.kt](file:///C:/home/ITROBOC/app/src/main/java/org/itroboc/app/MainActivity.kt)
- Update `AppNavigation` to route `Screen.AdminEdit` to `AdminEditScreen`.

#### [AdminActionsScreen.kt](file:///C:/home/ITROBOC/app/src/main/java/org/itroboc/app/AdminActionsScreen.kt)
- Add navigation to `Screen.AdminEdit` when "Edit" is clicked.

---

### Documentation

#### [README.md](file:///C:/home/ITROBOC/README.md)
- Mention the new `Admin::Edit` mock calibration screen.

## Verification Plan

### Automated Tests
- `./gradlew :app:assembleDebug`

### Manual Verification
1. Main Menu -> Admin Actions -> Select Profile -> Edit.
2. Verify 52-card grid (4x13).
3. Select a card -> Verify highlight.
4. Toggle Auto-advance.
5. Click "Scan" -> Verify mapping updates (Green) and selection moves (if enabled).
6. Verify "Back" returns to Admin Actions.
