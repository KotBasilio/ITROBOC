# Implement Admin Actions Screen (Deck Profile Management)

This plan replaces the `Admin actions` stub with a minimal interactive screen for managing Deck Profiles, supporting in-memory add/delete operations and profile selection.

## User Review Required

> [!NOTE]
> All profile changes (Add/Delete/Selection) are currently **in-memory only**. They will reset when the app process is killed.

## Proposed Changes

### Android App (:app)

#### [NEW] [AdminProfileUiModels.kt](file:///C:/home/ITROBOC/app/src/main/java/org/itroboc/app/AdminProfileUiModels.kt)
- Define `ProfileListItem` data class.
- Define `AdminProfileUiState` to hold the list of profiles and the current selection.

#### [NEW] [AdminActionsScreen.kt](file:///C:/home/ITROBOC/app/src/main/java/org/itroboc/app/AdminActionsScreen.kt)
- Implement `AdminActionsScreen` with:
    - Scrollable list of profiles with tick marks for the active one.
    - "Add new" button at the end of the list.
    - Dialog for adding a new profile name.
    - Bottom row of 5 buttons: Export, Import, Edit, Delete, Settings.
    - Confirmation dialog for deleting the active profile.
    - Protection logic to prevent deleting built-in profiles.

#### [MainActivity.kt](file:///C:/home/ITROBOC/app/src/main/java/org/itroboc/app/MainActivity.kt)
- Update `AppNavigation` to route `Screen.AdminActions` to `AdminActionsScreen`.

---

### Documentation

#### [README.md](file:///C:/home/ITROBOC/README.md)
- Update to mention the new Admin profile management shell.

## Verification Plan

### Automated Tests
- `./gradlew :core:test`
- `./gradlew :app:assembleDebug`

### Manual Verification
1. Open "Admin actions" from Main Menu.
2. Verify "Built-in Demo Bridge 52" is present and active (✓).
3. Click "Add new", enter "Custom 1", click OK -> Verify it appears and becomes active.
4. Click the demo profile -> Verify tick moves to it.
5. Click "Delete" while "Custom 1" is active -> Confirm -> Verify it is gone.
6. Click "Delete" while "Built-in Demo Bridge 52" is active -> Verify "cannot be deleted" message.
7. Verify all 5 bottom buttons are visible.
8. Verify "Back" returns to Main Menu.
