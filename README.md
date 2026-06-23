# ITROBOC

ITROBOC stands for Independent Tool for Reading Observed Barcodes On Cards.

The project is planned as an Android-first tool for bridge tournament directors. It will eventually turn barcode observations from physical playing cards into canonical card IDs, assemble hands and boards, validate results, and export PBN.

## Current milestone

The repository currently contains:

- a pure Kotlin/JVM `:core` module with:
  - card, suit, and rank domain models
  - canonical card ID parsing and formatting
  - hand and board validation logic
  - deck-profile signature mapping and signature-model metadata
  - a built-in 52-card demo/reference deck profile with opaque synthetic signatures
  - single-scan and batch-scan accumulator logic for TD workflow
  - scan severity classification for future UI messaging
  - hand and board progress summaries for future TD display
  - batch presentation summaries for a future TD screen
  - basic PBN export
- a minimal Android `:app` shell with:
  - a main menu as the entry point
  - an Admin actions screen for Deck Profile management:
    - profile listing with active selection
    - in-memory Add/Delete functionality
    - active profile retained while moving between app screens
    - placeholders for profile export/import
    - a functional Admin::Edit calibration workshop:
      - 4x13 mapping grid with status coloring
      - real CameraX preview plus one-frame scan attempts for Admin calibration
      - shared scan guide / ROI crop aimed at one barcode strip
      - experimental `grid13-v1` barcode signatures such as `bfm1549`
      - mock scanner fallback with conflict detection
      - support for aliases (multiple signatures per card)
      - auto-advance calibration flow
      - on-screen frame/decode debug text
      - JSONL scan debug logging with share/export action
  - a TD actions screen featuring a 30-board session overview:
    - 3x10 grid of boards 1..30
    - color-coding by board status (green for complete, yellow for empty/partial)
    - mock board scan screen per board
    - active profile name shown in the overview
    - placeholders for session import/export
  - a "Mock actions" screen featuring the fake TD workflow:
    - seat selection
    - free-text fake signature batch input
    - preset fake-batch buttons
    - current active profile context shared from the app shell
    - hand status, latest batch messages, grouped hand cards, board progress, and gated PBN preview

Broader product direction and workflow notes live in [docs/product_context.md](docs/product_context.md).

For details on the signature mapping scheme used in the fake shell, see [md-files/signatures.md](md-files/signatures.md).

For the current barcode signature model and Grid13 implementation handoff, see [md-files/grid13-v1-barcode-model.md](md-files/grid13-v1-barcode-model.md).

## Built-in deck profiles

The app currently ships with two built-in profiles:

- `builtin-observed-v1` is the default active profile. It was generated from Admin::Edit scan logs and uses `grid13-v1` aliases such as `bfm1255` plus retained `brm...` reverse aliases. The initial `S6`/`C6` collision was resolved by a focused rescan.
- `builtin-demo-bridge52-v1` is a synthetic demo/reference mapping only. It uses opaque raw signatures such as `0x1001 -> SA`, carries explicit synthetic signature-model metadata, and remains useful for tests plus the fake Android TD shell.

Neither built-in profile is a claim about official WinDup, Jannersten, or any other physical barcode mapping. Real calibrated deck profiles are expected to come from admin-side calibration and observation.

## Running tests

Run tests from the repository root with:

```bash
./gradlew test
```

## Not in scope yet

This milestone still does not add TD multi-card scanning, OpenCV, production-grade barcode decoding, rotation/perspective handling, persistent profile storage, or device-wide session persistence.

The Android app now includes early Admin-side camera capture and a pure `:vision` `grid13-v1` decoder prototype, but the broader TD workflow still uses fake signatures and the current camera path should be treated as an experimental calibration loop rather than finished scanning.
