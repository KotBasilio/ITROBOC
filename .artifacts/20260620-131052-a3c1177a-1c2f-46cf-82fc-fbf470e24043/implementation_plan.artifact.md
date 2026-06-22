# Ticket 1 — Add CameraX preview shell to Admin::Edit

This plan replaces the mock camera placeholder in the profile calibration workshop with a real CameraX preview area, including permission handling and a visual guide.

## Proposed Changes

### Android App (:app)

#### [build.gradle.kts](file:///C:/home/ITROBOC/app/build.gradle.kts)
- Add CameraX dependencies:
    - `androidx.camera:camera-camera2:1.4.1`
    - `androidx.camera:camera-lifecycle:1.4.1`
    - `androidx.camera:camera-view:1.4.1`

#### [AndroidManifest.xml](file:///C:/home/ITROBOC/app/src/main/AndroidManifest.xml)
- Add `<uses-permission android:name="android.permission.CAMERA" />`
- Add `<uses-feature android:name="android.hardware.camera" android:required="false" />`

#### [AdminEditScreen.kt](file:///C:/home/ITROBOC/app/src/main/java/org/itroboc/app/AdminEditScreen.kt)
- Implement `CameraPreview` composable using `AndroidView` and `PreviewView`.
- Implement `CameraPermissionHandler` to manage the request and show placeholders on denial.
- Add `BarcodeGuideOverlay` to draw a simple target rectangle over the preview.
- Replace the dark gray box with the live camera feed.

---

## Verification Plan

### Automated Tests
- `./gradlew :core:test` (ensure no regressions)
- `./gradlew :app:assembleDebug`

### Manual Verification
1. Launch app -> Admin Actions -> Edit.
2. Observe camera permission request.
3. **Grant permission**: Verify live camera feed appears in the upper-right area.
4. Verify guide rectangle is visible over the feed.
5. **Deny permission**: Verify a message "Camera permission is required for scanning" is shown instead.
6. Click "Scan" (mock): Verify it still works and assigns mock IDs.
