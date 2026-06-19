# ITROBOC

ITROBOC stands for Independent Tool for Reading Observed Barcodes On Cards.

The project is planned as an Android-first tool for bridge tournament directors. It will eventually turn barcode observations from physical playing cards into canonical card IDs, assemble hands and boards, validate results, and export PBN.

## Current milestone

The repository currently contains a pure Kotlin/JVM core scaffold with:

- card, suit, and rank domain models
- canonical card ID parsing and formatting
- hand and board validation logic
- deck-profile signature mapping
- basic PBN export
- unit tests runnable from WSL

## Running tests

Run tests from the repository root with:

```bash
./gradlew test
```

## Not in scope yet

This milestone does not add Android UI, CameraX, OpenCV, barcode image processing, or device integration code.
