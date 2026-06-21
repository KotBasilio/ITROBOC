# Implement TD Overview and Board Scan Mock

This plan establishes the first real TD Actions screen with a 30-board overview grid and a mock board scanning workflow.

## User Review Required

> [!NOTE]
> Navigation between `TdActions` and `BoardScan` is handled via a simple state stack or by explicit back actions to maintain the requested simplicity.

## Proposed Changes

### Android App (:app)

#### [Screen.kt](file:///C:/home/ITROBOC/app/src/main/java/org/itroboc/app/Screen.kt)
- Add `data class BoardScan(val boardNumber: Int) : Screen()` to support navigating to specific boards.

#### [NEW] [TdOverviewScreen.kt](file:///C:/home/ITROBOC/app/src/main/java/org/itroboc/app/TdOverviewScreen.kt)
- Implement `TdOverviewScreen` containing:
    - 3x10 grid of buttons (1-30).
    - Color-coding logic (Green for Complete, Yellow for Empty/Partial).
    - "Import Session" and "Export Session" stub buttons.
    - Navigation to `BoardScan`.
- Define `BoardUiStatus` enum.

#### [NEW] [BoardScanScreen.kt](file:///C:/home/ITROBOC/app/src/main/java/org/itroboc/app/BoardScanScreen.kt)
- Implement a placeholder screen for scanning a specific board.
- Includes a "Back" button to return to `TdActions`.

#### [MainActivity.kt](file:///C:/home/ITROBOC/app/src/main/java/org/itroboc/app/MainActivity.kt)
- Update `AppNavigation` to route `Screen.TdActions` to `TdOverviewScreen` and handle `Screen.BoardScan`.

---

### Documentation

#### [README.md](file:///C:/home/ITROBOC/README.md)
- Update "Current milestone" or "a minimal Android `:app` shell" section to mention the 30-board overview.

## Verification Plan

### Automated Tests
- `./gradlew :core:test`
- `./gradlew :app:assembleDebug`

### Manual Verification
1. Open "TD actions" from Main Menu.
2. Verify 3x10 grid of 30 buttons.
3. Verify some buttons are Green/Yellow (mock data).
4. Click a board button (e.g., Board 7) -> Verify "Board 7" scan placeholder.
5. Click "Back" in Board Scan -> Return to TD actions.
6. Verify "Import/Export" buttons are present.
