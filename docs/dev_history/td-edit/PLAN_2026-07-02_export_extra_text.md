# Implementation Plan - Include Intent.EXTRA_TEXT in PBN Export

The goal is to include the PBN text in `Intent.EXTRA_TEXT` when sharing from `TdSessionShareManager`, in addition to the existing `.pbn` file attachment. This improves compatibility with various share targets like messaging apps.

## Proposed Changes

### app

#### [TdSessionShareManager.kt](file:///C:/home/ITROBOC/app/src/main/java/org/itroboc/app/TdSessionShareManager.kt)

- Refactor `createShareIntent` to use a helper method for building the `Intent`.
- Add `putExtra(Intent.EXTRA_TEXT, exportText)` to the `Intent`.

```kotlin
    fun createShareIntent(exportText: String): Intent {
        exportFile.writeText(exportText)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            exportFile,
        )
        return buildShareIntent(uri, exportText, "ITROBOC-export.pbn")
    }

    internal fun buildShareIntent(uri: Uri, exportText: String, subject: String): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, exportText)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
```

#### [build.gradle.kts](file:///C:/home/ITROBOC/app/build.gradle.kts)

- Add `testImplementation("io.mockk:mockk:1.13.12")` to the dependencies.

#### [NEW] [TdSessionShareManagerTest.kt](file:///C:/home/ITROBOC/app/src/test/kotlin/org/itroboc/app/TdSessionShareManagerTest.kt)

- Add a unit test to verify that `buildShareIntent` correctly populates the `Intent` with `EXTRA_STREAM`, `EXTRA_TEXT`, and `EXTRA_SUBJECT`.

## Verification Plan

### Automated Tests
- Run `:app:testDebugUnitTest` to ensure all tests pass, including the new `TdSessionShareManagerTest`.
- Task: `./gradlew :app:testDebugUnitTest`

### Manual Verification
- Verify the code changes in `TdSessionShareManager.kt`.
- Verify the new test in `TdSessionShareManagerTest.kt`.
