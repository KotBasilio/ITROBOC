# TICKET — PBN Header & DDS/Gold Preservation (DELEGATION READY) 🗺️✨🖋️

**Context:** This ticket is for an adjacent agent/worker to implement. The Selyn-Braid team will provide the architectural review.

## Goal
Enhance PBN export with professional headers and ensure that double-dummy solver (DDS) data is preserved during import/export cycles.

---

## Task 1 — Emit Polite PBN File Header
Add a standard PBN header to cumulative session exports.

**Desired Header:**
```pbn
% PBN 2.1
% EXPORT
%Content-type: text/x-pbn; charset=ISO-8859-1
%Creator: ITROBOC
```

**Implementation Details:**
- Apply only in `TdSessionExchange.exportCompleteBoards(...)`.
- Ensure it appears exactly once at the top of the file.
- Do not add to single-board previews.

---

## Task 2 — Import and Preserve DDS/Gold Fields
When importing PBN, collect and store double-dummy solver fields to ensure they survive a re-export.

**Supported Fields to Preserve:**
- `[DoubleDummyTricks "..."]`
- `[OptimumResultTable "..."]` (including the 20 following data rows)
- `[OptimumScore "..."]`

**Implementation Details:**
- Update the PBN parser in `TdSessionExchange` to handle multi-line table data.
- Store this "gold" data in `BoardEditState` or a new metadata holder.
- Emit these fields in the board-level `PbnExporter` output if they exist.

**Note on DDS:** The actual calculation of these fields is delegated to the "nerdy beetle" (🐞 DDS module). This ticket is only about **preservation** of existing data.

---

## Acceptance Criteria
1. Cumulative export starts with the polite header.
2. Importing a file with DDS fields and then exporting it results in those fields being perfectly preserved.
3. The parser does not choke on multi-line table rows.
4. No change to existing card/board logic.
