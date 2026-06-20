# ITROBOC

ITROBOC stands for Independent Tool for Reading Observed Barcodes On Cards.

The project is planned as an Android-first tool for bridge tournament directors. It will eventually turn barcode observations from physical playing cards into canonical card IDs, assemble hands and boards, validate results, and export PBN.

## Current milestone

The repository currently contains:

- a pure Kotlin/JVM `:core` module with:
  - card, suit, and rank domain models
  - canonical card ID parsing and formatting
  - hand and board validation logic
  - deck-profile signature mapping
  - single-scan and batch-scan accumulator logic for TD workflow
  - scan severity classification for future UI messaging
  - hand and board progress summaries for future TD display
  - batch presentation summaries for a future TD screen
  - basic PBN export
- a minimal Android `:app` shell with:
  - one fake TD screen
  - seat selection
  - free-text fake signature batch input
  - preset fake-batch buttons
  - hand status, latest batch messages, grouped hand cards, board progress, and gated PBN preview

Broader product direction and workflow notes live in [docs/product_context.md](docs/product_context.md).

## Running tests

Run tests from the repository root with:

```bash
./gradlew test
```

## Not in scope yet

This milestone still does not add camera capture, CameraX, OpenCV, barcode image processing, or device integration code. The Android app shell uses fake signatures only and delegates scan/domain logic to the pure core module.
