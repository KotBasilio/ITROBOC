# Walkthrough - Admin Actions (Deck Profile Management)

I have replaced the `Admin actions` stub with a functional (in-memory) screen for managing Deck Profiles. This establishes the UI pattern for future persistence and calibration work.

## Changes

### New Admin Actions Screen
- **Profile Listing**: A scrollable list showing available profiles. The active one is highlighted and marked with a tick icon.
- **In-Memory Selection**: Clicking a profile row immediately updates the active selection in the UI state.
- **Add New Profile**: A dialog-driven workflow to create new custom profiles by name.
- **Protected Deletion**: A confirmation dialog for deleting the active profile. Built-in profiles (like the demo) are protected from deletion with a clear warning message.
- **Action Buttons**: A dedicated bottom bar with Export, Import, Edit, Delete, and Settings buttons.

### Implementation Details
- Created [AdminActionsScreen.kt](file:///C:/home/ITROBOC/app/src/main/java/org/itroboc/app/AdminActionsScreen.kt) for the screen UI.
- Created [AdminProfileUiModels.kt](file:///C:/home/ITROBOC/app/src/main/java/org/itroboc/app/AdminProfileUiModels.kt) for lightweight UI state management.
- Updated [MainActivity.kt](file:///C:/home/ITROBOC/app/src/main/java/org/itroboc/app/MainActivity.kt) to route to the new screen.

### Documentation
- Updated [README.md](file:///C:/home/ITROBOC/README.md) to reflect the new Admin shell capabilities.

## Verification Summary

### Automated Tests
- Ran `./gradlew :core:test`: **Passed**.
- Ran `./gradlew :app:assembleDebug`: **Passed**.

### Visual Verification
- Verified the screen layout using Compose Preview.

![Admin Actions Preview](file:///C:/home/ITROBOC/.artifacts/20260620-131052-a3c1177a-1c2f-46cf-82fc-fbf470e24043/AdminActionsPreview.png)

## Manual Test Result
1. **Add**: Created "Tournament Pack A" -> Success.
2. **Select**: Toggled between Built-in and Custom -> Active state updated.
3. **Delete**: Tried deleting Built-in -> Blocked with message. Deleted Custom -> Removed from list, first profile became active.
4. **Navigation**: Back to Main Menu -> Success.
