# Implementation Plan - Full-Screen TD Settings

The goal is to replace the `TdSettingsDialog` (implemented as an `AlertDialog`) with a full-screen settings view to improve the user experience and utilize more screen space.

## Proposed Changes

### app

#### [TdOverviewScreen.kt](file:///C:/home/ITROBOC/app/src/main/java/org/itroboc/app/TdOverviewScreen.kt)

- Rename `TdSettingsDialog` to `TdSettingsScreen`.
- Modify `TdSettingsScreen` to be a full-screen Composable using `Surface` and a `Scaffold` or a simple `Column` with `fillMaxSize`.
- In `TdOverviewScreen`, replace the `if (showSettingsDialog)` block with a conditional that displays `TdSettingsScreen` over the entire UI when `showSettingsDialog` is true.

```kotlin
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TdSettingsScreen(
    currentSize: Int,
    minAllowedSize: Int,
    onDismiss: () -> Unit,
    onSizeSelected: (Int) -> Unit
) {
    val allowedSizes = TdSessionState.ALLOWED_GRID_SIZES.filter { it >= minAllowedSize }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TD Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Button(onClick = onDismiss) {
                    Text("Close")
                }
            }

            // ... (rest of the settings UI)
        }
    }
}
```

## Verification Plan

### Automated Tests
- No new automated tests needed as the logic is the same, just the UI layout changes.
- Run existing tests: `./gradlew :app:testDebugUnitTest`

### Manual Verification
- Render the `TdSettingsScreen` using `render_compose_preview` to ensure it looks correct as a full-screen view.
- Manually verify the transition in the app if possible.
