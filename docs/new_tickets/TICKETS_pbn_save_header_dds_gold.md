# TICKET — PBN Save, polite header, and DDS/gold preservation

Snapshot context: after `3d85beb`, before next Bob implementation pass.

## Background

Current ITROBOC can export/share cumulative PBN and can import PBN from local storage. The next PBN maturity step is to treat PBN files as first-class local files and preserve useful analysis fields produced by bridge solvers.

A reference PBN file shows this file-level header:

```pbn
% PBN 2.1
% EXPORT
%Content-type: text/x-pbn; charset=ISO-8859-1
```

It also contains double-dummy / solver fields per board:

```pbn
[DoubleDummyTricks "43367433678a9668a966"]
[OptimumResultTable "Declarer;Denomination\2R;Result\2R"]
N NT  4
N  S  3
...
W  C  6
[OptimumScore "EW 4S; -620"]
```

These fields are gold for future visualization. For now, the goal is import/export preservation, not UI display.

---

## Ticket 1 — Add local Save PBN action

### Goal

Add a TD-facing local save action for cumulative session PBN export, separate from the existing share/export flow.

### Desired behavior

- Add a `Save PBN` / `Save` action near the existing export/share action.
- Generate the same cumulative PBN content used for session export.
- Launch Android Storage Access Framework `CreateDocument`.
- Pre-fill a useful `.pbn` filename, for example:
  - `itroboc_session_YYYY-MM-DD_HHmm.pbn`
  - or `itroboc_boards_YYYY-MM-DD_HHmm.pbn`
- Write the PBN file to the returned `Uri`.
- Show success/failure/cancel feedback.
- Existing share/export behavior remains unchanged.

### Implementation direction

- Use `ActivityResultContracts.CreateDocument(...)` from the `:app` UI/controller layer.
- Keep URI/file writing out of `:core` and out of reducers.
- Avoid broad storage permissions for this ticket.
- Use Android picker rather than silent write-to-Downloads.
- If the file declares `charset=ISO-8859-1`, keep content ASCII/ISO-safe. UTF-8 bytes are equivalent for the current ASCII PBN content, but do not introduce non-ASCII text while claiming ISO-8859-1.

### Acceptance criteria

- TD can save cumulative PBN as a local `.pbn` file.
- Canceling the picker does not crash or mutate session state.
- Saved file content matches exported/shared PBN content.
- Existing share/export still works.
- No Android storage permission is added for this ticket.

### Non-goals

- Silent one-tap write directly to Downloads.
- DDS visualization.

---

## Ticket 2 — Emit polite PBN file header

### Goal

Add a standard-looking PBN header to cumulative session exports and saved files.

### Desired header

Emit this at the start of cumulative PBN output:

```pbn
% PBN 2.1
% EXPORT
%Content-type: text/x-pbn; charset=ISO-8859-1
```

Optional additional creator line is acceptable if desired:

```pbn
%Creator: ITROBOC
```

### Scope decision

- Cumulative session export/save/share should include the header once at the top.
- Single-board preview inside TD board-complete view may remain headerless if that is clearer for the cockpit.
- Preserve the existing decision that MVP board export does not emit `[Site "..."]` unless explicitly revisited.

### Implementation direction

- Prefer a session-level export wrapper in `TdSessionExchange.exportCompleteBoards(...)` or a small helper near it.
- Avoid duplicating the header before every board block.
- Keep board-level `PbnExporter.export(...)` focused on board blocks unless it becomes useful to add an explicit option such as `includeFileHeader = false`.

### Acceptance criteria

- Cumulative export starts with the PBN header exactly once.
- Board blocks remain separated by blank lines.
- Existing import ignores `%` comment/header lines safely.
- Export/share/save all use the same cumulative text.
- Tests lock the header presence and no repeated header per board.

---

## Ticket 3 — Import and preserve DDS/gold fields

### Goal

When importing PBN, preserve supported double-dummy solver fields and emit them again when exporting the same boards.

### Supported fields

Per imported board, preserve:

```pbn
[DoubleDummyTricks "..."]
[OptimumResultTable "..."]
<20 table rows, usually Declarer x Denomination>
[OptimumScore "..."]
```

Reference table row shape:

```pbn
N NT  4
N  S  3
N  H  3
N  D  6
N  C  7
...
W  C  6
```

`DoubleDummyTricks` values may contain:

- digits `0..9`,
- letters such as `a`, `b`, `c`, `d` for 10..13,
- `-` or `*` for unavailable/unknown/missing values.

For this ticket, preservation is more important than full interpretation.

### Parser warning

Current import parsing mostly collects tag lines. `OptimumResultTable` is a multi-line PBN section: the tag is followed by 20 non-tag rows. The parser must not terminate the board block just because table rows are not bracketed tags.

### Suggested data shape

A simple raw-preservation model is enough for v1:

```kotlin
data class PbnDoubleDummyData(
    val doubleDummyTricks: String? = null,
    val optimumResultTableHeader: String? = null,
    val optimumResultTableRows: List<String> = emptyList(),
    val optimumScore: String? = null,
)
```

Attach it to the imported board state in a board-level place, for example `BoardEditState`, or another board metadata holder used by `TdSessionState`.

### Desired behavior

- Import complete supported boards as before.
- If DDS/gold fields exist in the same board block, store them with that board.
- Export imported boards with the same DDS/gold fields after normal board tags.
- If a board has no DDS/gold fields, export remains valid and unchanged except for the new file-level header.
- Unsupported/partial board blocks continue to be ignored as before.

### Ordering recommendation

For exported board blocks, use stable order similar to the reference:

```pbn
[Event "ITROBOC Export"]
[Board "4"]
[Dealer "W"]
[Vulnerable "All"]
[Deal "W:..."]
[DoubleDummyTricks "..."]
[OptimumResultTable "Declarer;Denomination\2R;Result\2R"]
N NT  4
...
W  C  6
[OptimumScore "EW 4S; -620"]
```

Do not reintroduce `[Site "..."]` as part of this ticket.

### Acceptance criteria

- Importing a PBN file containing DDS/gold fields preserves those fields for complete valid boards.
- Exporting after import emits `DoubleDummyTricks`, `OptimumResultTable` including its 20 rows, and `OptimumScore` for those boards.
- A round-trip test with the provided reference board preserves the DDS/gold fields textually enough for downstream bridge tools.
- `OptimumResultTable` rows are not lost by the parser.
- Missing/empty fields are handled safely, for example `[OptimumScore ""]` survives if imported.
- Boards with placeholder DDT strings like `********************` or `--------------------` do not crash import.
- Existing complete-board import/export tests still pass.

### Non-goals

- Calculating DDS locally.
- Validating DDS correctness against the deal.
- Visualizing DDS/gold fields in the TD UI.
- Editing DDS/gold fields manually.

---

## Suggested Bob implementation order

1. Ticket 2: add header to cumulative export and lock tests.
2. Ticket 3: update parser/block model for multi-line `OptimumResultTable`, preserve DDS/gold, lock round-trip tests.
3. Ticket 1: add Android Save action using the now-polished cumulative export text.

The save action can be done earlier if desired, but it becomes more satisfying after the exported text is already polite and gold-preserving.
