# Ticket 2 — Add ImageAnalysis frame tap and debug state

This plan enables the Admin::Edit screen to receive and process real camera frames via `ImageAnalysis`, reporting debug information when "Scan" is pressed.

## Proposed Changes

### Android App (:app)

#### [AdminEditScreen.kt](file:///C:/home/ITROBOC/app/src/main/java/org/itroboc/app/AdminEditScreen.kt)
- Add state to track frame debug info:
    ```kotlin
    var frameDebugInfo by remember { mutableStateOf<String?>(null) }
    var scanRequested by remember { mutableStateOf(false) }
    ```
- Update `CameraPreview` to accept an `ImageAnalysis.Analyzer` and a configuration:
    - Use `STRATEGY_KEEP_LATEST`.
    - Ensure `ImageProxy` is always closed.
- Implement the analyzer logic:
    - When `scanRequested` is true, extract frame metadata (width, height, rotation, timestamp).
    - Update `frameDebugInfo`.
    - Set `scanRequested` to false (process only one frame per click).
- Update the UI to display `frameDebugInfo` in the status area.
- Update the "Scan" button to set `scanRequested = true`.
- Maintain existing mock signature assignment alongside frame capture for now.

---

## Verification Plan

### Automated Tests
- `./gradlew :core:test` (ensure no regressions)
- `./gradlew :app:assembleDebug`

### Manual Verification
1. Open Admin Actions -> Edit.
2. Grant camera permission if needed.
3. Observe live camera feed.
4. Click **Scan**.
5. Verify debug text appears/updates: e.g., `Frame: 1280x720 rot=90`.
6. Verify "Last scan frame received" indicator (if added).
7. Verify that mock signature assignment still occurs as before.
