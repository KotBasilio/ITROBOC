# Walkthrough - Main Menu and Navigation

I have added a main menu as the entry point for the ITROBOC Android app, routing the existing fake scan screen under "Mock actions" and providing stubs for "TD actions" and "Admin actions".

## Changes

### Navigation Structure
- Introduced [Screen.kt](file:///C:/home/ITROBOC/app/src/main/java/org/itroboc/app/Screen.kt) to manage app-level navigation state.
- [MainActivity.kt](file:///C:/home/ITROBOC/app/src/main/java/org/itroboc/app/MainActivity.kt) now orchestrates switching between screens based on the current state.

### New Screens
- **Main Menu**: [MainMenuScreen.kt](file:///C:/home/ITROBOC/app/src/main/java/org/itroboc/app/MainMenuScreen.kt) provides the entry point with buttons for TD, Admin, and Mock actions.
- **Stub Screens**: [StubActionScreen.kt](file:///C:/home/ITROBOC/app/src/main/java/org/itroboc/app/StubActionScreen.kt) is used for "TD actions" and "Admin actions" as placeholders.
- **Mock TD Screen**: [MockTdScreen.kt](file:///C:/home/ITROBOC/app/src/main/java/org/itroboc/app/MockTdScreen.kt) now houses the existing fake scan workflow, extracted from `MainActivity.kt` for better modularity.

### Documentation
- Updated [README.md](file:///C:/home/ITROBOC/README.md) to reflect the new app structure.

## Verification Summary

### Automated Tests
- Ran `./gradlew :core:test`: **Passed (38 tests)**.
- Ran `./gradlew :app:assembleDebug`: **Passed**.

### Visual Verification
- Rendered a Compose Preview for the `MainMenuScreen` to ensure the UI layout is correct.

![Main Menu Preview](file:///C:/home/ITROBOC/.artifacts/20260620-131052-a3c1177a-1c2f-46cf-82fc-fbf470e24043/MainMenuPreview.png)
