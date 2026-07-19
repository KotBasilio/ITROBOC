# TICKET 2026-07-17 — Compose typography inventory and style proposal

Status: ready for workbee
Owner lane: Android UI / Compose audit
Repo branch: `vacation`
Requested by: Archy
Prepared by: Selyn

## Decision: split this into two stages

This should **not** be one direct implementation ticket.

Use this ticket as **Stage 1: inventory + proposal only**. After Archy reviews and approves the semantic style list, create a separate Stage 2 implementation ticket.

Reason:

```text
fontSize values are visible UI taste
semantic role names become design vocabulary
Archy should approve the vocabulary before code churn
```

## Goal

Inventory repeated `fontSize` usages in Jetpack Compose `.kt` UI sources, group them by semantic UI role, and propose a central `ItrobocTextStyles` style list for Archy review.

No implementation refactor in this ticket unless Archy explicitly approves the proposed list first.

## Background

Archy noticed many local font-size settings in Kotlin UI sources. Local sizes are not automatically wrong, but repeated semantic roles should probably move toward a central style/token object.

Preferred direction:

```kotlin
object ItrobocTextStyles {
    // semantic TextStyle handles here
}
```

Prefer product-semantic names over raw-size names.

Good naming direction:

```text
TdSeatLabel
TdCardRank
TdHandHeader
TdVerdict
AdminDebugLine
ScannerStatus
ActionButtonText
```

Avoid names like:

```text
BigText
Font18
SmallGoldThing
```

## Stage 1 scope — audit only

1. Search Compose UI `.kt` sources for repeated `fontSize = ...sp` usages.
2. Record each occurrence with:
   - file path
   - local composable / approximate context
   - current size
   - visible UI role, inferred from nearby code
3. Group occurrences by semantic role.
4. Identify likely one-off layout calibrations that should **not** be centralized yet.
5. Propose a first-pass `ItrobocTextStyles` list for review.

## Expected deliverable

Add or update a Markdown note under `docs/new_tickets/` or another review-friendly docs path with:

```text
Observed fontSize inventory
Semantic grouping table
Proposed ItrobocTextStyles names
Suggested TextStyle values
Excluded one-offs with reason
Open questions for Archy
```

The workbee may include a small Kotlin sketch in the Markdown note, but should not modify implementation files in Stage 1.

## Review gate

Stop after the proposal and ask Archy to review:

```text
Do these semantic style names match the product language?
Which roles should be merged/split?
Which sizes are intentional TD readability choices?
Which sizes are accidental drift?
```

## Stage 2 — follow-up ticket after review

After Archy approves the semantic list, create a separate implementation ticket to:

1. Add central typography/style object, likely near existing UI/theme code.
2. Replace obvious repeated roles with `ItrobocTextStyles.*`.
3. Preserve screen-specific exceptions where local calibration is meaningful.
4. Run build/tests as appropriate.
5. Keep changes small and reviewable.

## Non-goals for Stage 1

- Do not redesign the whole UI.
- Do not replace every local `fontSize` mechanically.
- Do not change colors, spacing, layout, or behavior.
- Do not introduce a full design system beyond the typography proposal.
- Do not edit implementation files yet.

## Acceptance criteria

- Inventory covers relevant Compose UI `.kt` files.
- Proposed style names are semantic, not size-based.
- One-offs are explicitly separated from repeated roles.
- Archy can review the proposal without reading every source file.
- No Kotlin implementation files are changed in this Stage 1 ticket.

## Field note

This is a Daedalus-style measure task: not spectacular, but it gives the UI wings better proportion.
