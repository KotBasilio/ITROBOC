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
  - a built-in 52-card demo/reference deck profile with opaque synthetic signatures
  - single-scan and batch-scan accumulator logic for TD workflow
  - scan severity classification for future UI messaging
  - hand and board progress summaries for future TD display
  - batch presentation summaries for a future TD screen
  - basic PBN export
- a minimal Android `:app` shell with:
  - a main menu as the entry point
  - stub screens for TD and Admin actions
  - a "Mock actions" screen featuring the fake TD workflow:
    - seat selection
    - free-text fake signature batch input
    - preset fake-batch buttons
    - hand status, latest batch messages, grouped hand cards, board progress, and gated PBN preview

Broader product direction and workflow notes live in [docs/product_context.md](docs/product_context.md).

For details on the signature mapping scheme used in the fake shell, see [md-files/signatures.md](md-files/signatures.md).

## Demo deck profile

The built-in deck profile is a demo/reference mapping only. It uses synthetic opaque raw signatures such as `0x1001 -> SA`, carries explicit profile metadata, and exists to support tests plus the fake Android TD shell.

It is not a claim about real WinDup, Jannersten, or any other physical barcode mapping. Real deck profiles are expected to come later from admin-side calibration and observation.

## Running tests

Run tests from the repository root with:

```bash
./gradlew test
```

## Not in scope yet

This milestone still does not add camera capture, CameraX, OpenCV, barcode image processing, or device integration code. The Android app shell uses fake signatures only and delegates scan/domain logic to the pure core module.
