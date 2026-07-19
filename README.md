# ITROBOC

ITROBOC stands for Independent Tool for Reading Observed Barcodes On Cards.

The project is planned as an Android-first tool for bridge tournament directors. It turns barcode observations from physical playing cards into canonical card IDs, assembles hands and boards, validates results, and exports PBN.

## Current milestone

The repository currently contains:

- a pure Kotlin/JVM `:core` module with:
  - card, suit, and rank domain models
  - canonical card ID parsing and formatting
  - hand and board validation logic
  - deck-profile signature mapping and signature-model metadata
  - a built-in 52-card demo/reference deck profile with opaque synthetic signatures
  - single-scan and batch-scan accumulator logic for TD workflow
  - scan severity classification for UI messaging
  - hand and board progress summaries for TD display
  - batch presentation summaries
  - PBN export logic
- a functional Android `:app` shell with:
  - a main menu with two large current entry actions: Admin and TD eye
  - an Admin actions screen for Deck Profile management:
    - profile listing with active selection
    - in-memory Add/Delete functionality
    - active profile retained while moving between app screens
    - functional profile export/import (JSON)
    - a functional Admin::Edit calibration workshop:
      - 4x13 mapping grid with status coloring
      - real CameraX preview plus one-frame scan attempts for Admin calibration
      - shared scan guide / ROI crop aimed at one barcode strip
      - `grid13-v2` barcode signatures such as `bfm1549`
      - support for aliases (multiple signatures per card)
      - auto-advance calibration flow
      - on-screen frame/decode debug text
      - JSONL scan debug logging with share/export action
  - a TD actions screen featuring a dynamic session overview:
    - 3-row board grid with configurable visible board count
    - color-coding by board status (green for complete, yellow for empty/partial)
    - active profile name shown in the overview
  - a functional **TD::EditBoard** scanning cockpit:
    - 3x3 spatial table layout (landscape-first)
    - real-time CameraX "stream" scanning with Grid13 decoder
    - persistent session-backed board state
    - suit-grouped hand displays with accessibility-tuned fonts
    - active seat selection with auto-advance (N -> E -> S -> W)
    - **Auto-fill "Fourth Hand"**: automatically calculates and assigns the final 13 cards when the other three hands are complete
    - orientation-aware scanning with `bfm`/`brm` mode toggle
    - detailed scan feedback, board progress status, and Beetle Mind stabilization thoughts
    - recovery controls: Undo, Scissors, Swap, I'm sure, and Clear
    - Scissors selected-hand repair screen with add/remove card halves and a 52-card manual repair grid
    - gated PBN preview triggered upon board completion
    - functional PBN export/share and local **Save** action via Android Storage Access Framework
    - PBN import with preservation of **double-dummy solver (DDS)** metadata
  - legacy Mock code may still exist temporarily, but the Mock screen is not a current main-menu product surface.

Broader product direction and workflow notes live in [docs/product_context.md](docs/product_context.md).

For the current barcode signature model and Grid13 implementation handoff, see [docs/dev_history/vision/grid13_v2_barcode_model.md](docs/dev_history/vision/grid13_v2_barcode_model.md).

## Built-in deck profiles

The app currently ships with two built-in profiles:

- `builtin-observed-v1` is the default active profile. It was generated from Admin::Edit scan logs and uses `grid13-v2` aliases. 
- `builtin-demo-bridge52-v1` is a synthetic demo/reference mapping only. It uses opaque raw signatures such as `0x1002 -> SK`, carries explicit synthetic signature-model metadata, and remains useful for tests plus the fake Android TD shell.

Neither built-in profile is a claim about official WinDup, Jannersten, or any other physical barcode mapping. Real calibrated deck profiles are expected to come from admin-side calibration and observation.

## Running tests

Run tests from the repository root with:

```bash
./gradlew test
```

## Not in scope yet

This milestone still does not add TD multi-card "snap" scanning, OpenCV, perspective handling, persistent profile storage to disk, or device-wide session persistence across app restarts.
