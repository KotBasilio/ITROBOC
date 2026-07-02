# ITROBOC Ticket — TD PBN Export: Include Intent.EXTRA_TEXT Alongside File Attachment

## Goal

Improve Android share compatibility for TD PBN export by including the PBN text both as a `.pbn` attachment and as inline share text.

## Context

`TdSessionShareManager` currently shares exported PBN as a file via `Intent.EXTRA_STREAM`. This is correct and useful for file-oriented targets.

Some Android share targets, especially messaging apps, note apps, email composers, and lightweight text workflows, behave better if the same content is also available in `Intent.EXTRA_TEXT`.

For club workflows, TDs may want to quickly send PBN through messaging apps without manually opening/copying the attachment.

## Required Change

When creating the export share intent, keep the existing `.pbn` attachment behavior and additionally include:

```kotlin
putExtra(Intent.EXTRA_TEXT, exportText)
```

The intent should still include:

```kotlin
putExtra(Intent.EXTRA_STREAM, uri)
type = "application/octet-stream" // or better PBN/text MIME if already chosen
addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
```

If the current MIME type is generic, consider whether `text/plain` or a bridge-specific filename with `.pbn` works better for target apps. Do not break existing attachment behavior.

## Expected Behavior

- Share sheet still offers file-capable targets.
- Exported `.pbn` file is still attached.
- Text-capable targets can receive the PBN as inline text.
- The inline text matches exactly the exported file contents.
- Existing export result text remains sorted and contains only complete boards.

## Acceptance Criteria

- `TdSessionShareManager` includes `Intent.EXTRA_TEXT = exportText` in the share intent.
- Existing `Intent.EXTRA_STREAM` file sharing still works.
- Export filename remains `.pbn`.
- Export text in the attachment and `EXTRA_TEXT` are identical.
- Add/update a test if intent construction is testable.
- If intent construction is not currently testable, add a small helper that builds the share intent from `(uri, exportText, filename)` and test that helper.
