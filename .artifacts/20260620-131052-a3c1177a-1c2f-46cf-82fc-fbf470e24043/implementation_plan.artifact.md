# Add Main Menu and Navigation

This plan adds a main menu as the entry point for the ITROBOC Android app, routing the existing fake scan screen under "Mock actions" and providing stubs for "TD actions" and "Admin actions".

## Proposed Changes

### Android App (:app)

#### [NEW] [Screen.kt](file:///C:/home/ITROBOC/app/src/main/java/org/itroboc/app/Screen.kt)
- Define `sealed class Screen` for navigation state.

#### [NEW] [MainMenuScreen.kt](file:///C:/home/ITROBOC/app/src/main/java/org/itroboc/app/MainMenuScreen.kt)
- Implement `MainMenuScreen` with buttons for TD, Admin, and Mock actions.

#### [NEW] [StubActionScreen.kt](file:///C:/home/ITROBOC/app/src/main/java/org/itroboc/app/StubActionScreen.kt)
- Implement a generic `StubActionScreen` with placeholder text and a back button.

#### [NEW] [MockTdScreen.kt](file:///C:/home/ITROBOC/app/src/main/java/org/itroboc/app/MockTdScreen.kt)
- Move `FakeTdScreen` and its related helper composables/classes from `MainActivity.kt`.

#### [MainActivity.kt](file:///C:/home/ITROBOC/app/src/main/java/org/itroboc/app/MainActivity.kt)
- Update `MainActivity` to use a `currentScreen` state and switch between the new screens.
- Remove `FakeTdScreen` and moved logic.

---

### Documentation

#### [README.md](file:///C:/home/ITROBOC/README.md)
- Update to reflect the new app entry point and navigation structure.

## Verification Plan

### Automated Tests
- Run `./gradlew :core:test` to ensure no regressions in domain logic.
- Run `./gradlew :app:assembleDebug` to ensure the app builds.

### Manual Verification
1. Launch the app.
2. Verify "Main Menu" is shown with three buttons.
3. Click "TD actions" -> Verify stub page -> Click "Back" -> Verify Main Menu.
4. Click "Admin actions" -> Verify stub page -> Click "Back" -> Verify Main Menu.
5. Click "Mock actions" -> Verify existing fake TD screen works (seat selection, scan buttons, etc.) -> Click "Back" (if added) or navigate back via state.
