# ITROBOC Architecture

Last aligned with source bundle: `918a15c`.
Audience: AI instances entering the repo for design, review, implementation, or handoff work.
Status: post-MVP. ITROBOC has survived first real tournament use and produced usable PBN from physical club cards.

## One-line purpose

ITROBOC is an Android-first bridge TD assistant that converts physical barcode-marked playing cards into validated duplicate-bridge board data and exports complete boards as PBN.

```text
physical cards -> barcode strip -> raw signature -> Deck Profile -> CardId -> BoardState -> PBN
```

The product is not a generic bridge scorer. Its current center of gravity is the real-world bottleneck of building hand records from physical dealt cards.

## Current product truth

The project is no longer greenfield. Treat it as post-MVP hardening.

The MVP has already been field-tested in a live club tournament. It processed a substantial part of a Wednesday session and produced usable PBN. That matters architecturally: prefer preserving proven field behavior over re-inventing cleaner but untested abstractions.

Current major surfaces:

- `Admin::Actions`: deck-profile selection, add/delete, import/export, entry into calibration.
- `Admin::Edit`: barcode/profile calibration and inspection workshop.
- `TD::Overview`: session board grid, import/export, board status overview.
- `TD::EditBoard`: real-time board/hand scanning cockpit.
- `Mock`: legacy fake-signature TD flow for dry-run and smoke testing.

The current default built-in deck profile is `builtin-observed-v1`, not the synthetic demo profile.

## Load-bearing maxims

These are product and architecture rules, not decoration.

### Admin = explanation; TD = verdict

Admin may be rich, diagnostic, inspectable, and slow. TD must be fast, forgiving, and decisive under tournament pressure.

Admin is allowed to show debug evidence, barcode runs, Grid13 bits, aliases, scan logs, and why a scan behaved as it did.

TD should show operational results: what card was accepted, what was skipped, whether the board is complete, and whether PBN is ready.

### Scanner does not know card meaning

The barcode decoder produces raw signatures only.

Correct:

```text
camera crop -> bfm1549 -> Deck Profile -> SA
```

Incorrect:

```text
camera crop -> SA
```

Card meaning belongs to `DeckProfile`. Vision must remain card-semantic-free.

### Wrong card is worse than missed card

In TD mode, a missed scan is recoverable by adjusting the card/camera and trying again. A wrong mutation can poison a board. Therefore strict rejection and uncertainty are acceptable costs.

### Complete-board export only

PBN export is gated on complete, valid boards: four 13-card hands and 52 unique cards.

Partial boards may exist in session state and in UI, but should not be exported as complete PBN.

### Orientation is data

`bfm...` and `brm...` are orientation-bearing raw-signature families. Do not erase, canonicalize, sort, or merge them.

Bad:

```text
canonical = min(bfmPayload, brmPayload)
```

Good:

```text
bfm1549 -> SA
brm1255 -> SA
```

The prefix is part of the raw signature identity.

### Visible runs are evidence; normalized Grid13 cells are identity

Black/white runs, pixel widths, and run forms are diagnostic evidence. The stored alias is the normalized Grid13 token such as `bfm1549` or `brm1255`.

## Repository/module layout

The Gradle project has three active modules:

```text
:core    pure Kotlin/JVM bridge domain and profile logic
:vision  pure Kotlin/JVM image/signature decoding logic
:app     Android / Compose / CameraX UI and device adapters
```

Repository-level docs and collaboration guidance live outside the modules:

```text
docs/product_context.md
docs/architecture.md
docs/MVP-state-reached-handoff.md
docs/dev_history/**
docs/new_tickets/**
md-files/field.md
AGENTS.md
README.md
```

`docs/new_tickets/**` is the active place for ticket details. This architecture document should remain clean and should not duplicate ticket specs.

## Module responsibilities

### `:core`

`core` owns bridge-domain truth and profile mapping. It must stay Android-free and vision-free.

Main responsibilities:

- canonical bridge primitives: `Suit`, `Rank`, `CardId`, `Seat`
- hand and board state: `HandState`, `BoardState`, `BoardEditState`
- board progress summaries: `HandProgressSummary`, `BoardProgressSummary`
- profile mapping: `DeckProfile`, `DeckProfileMetadata`, `DeckProfileEditor`
- built-in profiles: `BuiltInDeckProfiles`
- TD scan accumulation: `TdScanAccumulator`, `TdScanResult`, `TdBatchScanReport`
- TD presentation summaries: `TdScanSessionPresentation`
- pure board-edit reducer: `EditBoardReducer`
- duplicate-board metadata: `DuplicateBoardMetadata`, `BoardVulnerability`
- PBN export: `PbnExporter`, `PbnExportOptions`

Core rules:

- keep logic deterministic and unit-testable;
- prefer typed results over string-only status;
- reject illegal duplicate cards;
- validate complete boards before export;
- never depend on Android classes, CameraX, Compose, or pixel/image types.

Important current reducer behavior:

- adding a new card to selected incomplete hand succeeds;
- adding a card already in selected hand is treated as harmless/no mutation;
- adding a card already on another hand is skipped/no mutation;
- adding to a complete selected hand does not mutate and auto-advances selection;
- completing a hand auto-advances `N -> E -> S -> W -> N`;
- if three hands are complete and the selected fourth hand is empty, the fourth hand can be auto-filled from the remaining 13 cards;
- clearing selected hand and clearing board are pure core operations.

### `:vision`

`vision` owns pure barcode image/signature extraction. It must stay Android-free and card-semantic-free.

Main responsibilities:

- image carrier: `GrayImage`
- barcode result model: `BarcodeDecoder`, `BarcodeDecodeResult`, `DetectedSignature`, `BarcodeDebugInfo`, `BarcodeRoi`, `BarcodeBounds`
- Grid13 measurement primitives: ink conversion, projection, thresholds, run extraction, active span
- Grid13 signature helpers: sentinel checks, bit normalization, reverse bits, `bfm` / `brm` token formatting
- slow/explanation path: `Grid13SlowDecoder`, `Grid13SlowBarcodeMeasurement`
- fast/verdict path: `Grid13VerdictDecoder`
- golden-manifest and degradation tests

Vision rules:

- output raw signatures, never `CardId`;
- preserve enough debug in slow/Admin path to explain calibration;
- keep verdict/TD path free of debug payloads;
- keep accepted verdict signatures aligned with slow-path signatures for known/golden barcodes;
- prefer rejection over unsafe acceptance.

### `:app`

`app` owns Android UI, CameraX, permissions, share intents, in-memory navigation/session/profile state, and adapters between Android frames and pure modules.

Main responsibilities:

- navigation: `MainActivity`, `AppNavigation`, `Screen`
- profile/admin UI: `AdminActionsScreen`, `AdminEditScreen`, `AdminProfileUiModels`, `AdminReadOnlyCardPreview`
- camera adapter/support: `AdminEditCameraSupport`, `AdminEditCameraFrameDecoder`, ROI extraction from `ImageProxy`
- TD board workflow UI: `TdOverviewScreen`, `EditBoardScreen`, `TdSessionState`
- TD import/export/share: `TdSessionExchange`, `TdSessionShareManager`
- orientation selection: `BarcodeOrientationMode`, `DetectedSignature.viewedAs(...)`
- mock workflow: `MockTdScreen`

App rules:

- Compose may own UI state, but domain decisions should delegate to `core` reducers/models where possible;
- CameraX `ImageProxy` conversion must stay in `app`, not leak into `vision`;
- session/profile state is currently in-memory at `AppNavigation` level;
- app-level state currently does not survive process death/application relaunch unless exported/imported manually;
- Android share/export concerns stay in `app`.

## End-to-end flows

### Admin calibration / profile editing

```text
Admin::Edit screen
-> selected CardId
-> CameraX preview
-> centered guide ROI
-> ImageProxy luma ROI extraction
-> GrayImage
-> Grid13SlowDecoder
-> BarcodeDecodeResult
-> DeckProfileEditor.assign(rawSignature, selectedCard)
-> aliases/profile state
-> optional debug JSONL share
```

Admin uses the slow/explanation decoder by default. This is intentional.

Key Admin behaviors:

- a 4x13 card grid selects the card being calibrated;
- `Scan` consumes one next camera frame;
- successful `Found` assigns the observed `rawSignature` to the selected card;
- `Ambiguous` and `NotFound` do not mutate the profile;
- auto-advance can move to the next unmapped card;
- aliases are shown as compact chips;
- complete profiles can enter read-only visual inspection mode;
- debug logs contain frame, ROI, threshold, bits, runs, sentinel, confidence, and profile-match evidence.

### TD live board editing

```text
TD::Overview
-> select board number
-> TD::EditBoard
-> selected seat
-> CameraX stream frame
-> centered guide ROI
-> GrayImage
-> Grid13VerdictDecoder
-> DetectedSignature.rawSignature
-> BarcodeOrientationMode.viewedAs(...)
-> DeckProfile.lookup(...)
-> EditBoardReducer.applyScannedCard(...)
-> BoardEditState
-> BoardState
-> PBN preview when complete
```

TD uses the verdict decoder. This is intentional.

Key TD behaviors:

- landscape-first 3x3 cockpit layout;
- North / East / South / West panels spatially surround the camera area;
- tapping a hand selects the active seat;
- scans feed the selected seat;
- card displays are suit-grouped;
- active hand has a visible border;
- hand completion colors the hand green;
- repeated same raw token is debounced for a short window;
- unknown signatures are throttled to avoid status flood;
- `bfm` / `brm` orientation mode is manually selectable;
- `auto` orientation exists in UI/model but deliberately does not guess yet;
- once the board is complete, the central camera area switches to a board-complete/PBN view;
- clearing a hand makes the board incomplete again and scanning can resume.

### TD cumulative PBN exchange

```text
session state -> complete boards only -> PBN blocks sorted by board number -> Android share intent
```

```text
imported PBN text -> tagged blocks -> complete board parse -> merge into session state
```

Current import/export behavior:

- export skips incomplete boards;
- export returns `null` when no complete boards exist;
- exported boards are sorted by board number;
- duplicate-board `Dealer` and `Vulnerable` are derived from board number;
- import ignores empty/partial/invalid blocks;
- complete imported boards add or overwrite board state;
- selected seat is preserved for an existing board when it is overwritten by import;
- session state remains in memory unless user exports/imports.

## Deck profiles

A `DeckProfile` is an opaque raw-signature-to-card dictionary:

```kotlin
Map<String, CardId>
```

Metadata includes:

- `profileId`
- `displayName`
- `isBuiltIn`
- `isDemo`
- `notes`
- `signatureModel`

Current signature models:

- `grid13-v2`: observed physical-card barcode signatures.
- `synthetic-demo-bridge52-v1`: fake/demo signatures for tests and mock flows.

Current built-ins:

- `builtin-observed-v1`
  - default profile;
  - real observed `grid13-v2` aliases;
  - complete 52-card coverage;
  - includes `bfm` and `brm` orientation-bearing aliases;
  - not an official claim about manufacturer mapping.
- `builtin-demo-bridge52-v1`
  - synthetic profile;
  - useful for tests and fake flows;
  - must not be treated as real physical barcode mapping.

Profile invariants:

- one raw signature maps to at most one `CardId`;
- a card may have multiple aliases;
- profile completeness means every one of the 52 cards has at least one alias;
- core treats raw signatures as opaque strings;
- `signatureModel` explains expected token shape but does not move barcode logic into core.

## Grid13-v2 signature model

Grid13-v2 is the current practical model for one cropped barcode strip.

Conceptual pipeline:

```text
GrayImage
-> ink signal
-> column projection
-> adaptive threshold
-> black runs and white gaps
-> active span
-> 13 equal cells
-> 13-bit occupancy
-> sentinel normalization
-> hex payload
-> bfm/brm raw signature
```

Bit/cell model:

```text
left visual cell  -> bit12
right visual cell -> bit0
```

Sentinel/control structure:

```text
10.........01
```

Numeric check:

```kotlin
(value and 0x1803) == 0x1001
```

That means:

- `bit12 = 1`
- `bit11 = 0`
- `bit1 = 0`
- `bit0 = 1`

Control-bit normalization is deliberate. The decoder may normalize malformed outer control cells to the expected sentinel frame while keeping evidence/warnings in debug output on the slow path.

Strict run gate:

- reject raw candidates with black run length `>= 3`;
- reject raw candidates with white run length `>= 4`.

Durable formula:

```text
Physical card -> orientation -> bits -> token -> run form
```

## Slow path vs verdict path

### `Grid13SlowDecoder`

Use it for Admin, calibration, explanations, debug logs, golden/oracle alignment, and future diagnostics.

It returns rich `BarcodeDebugInfo` including:

- threshold;
- black run ranges;
- black/white run pixel widths;
- active span;
- pre/post sentinel bits;
- forward/reverse hex;
- reverse signature;
- `rl2` run form;
- sentinel repair flags/reasons;
- warnings.

### `Grid13VerdictDecoder`

Use it for TD runtime.

It independently computes the minimal answer needed for field mutation:

- projection;
- threshold;
- black run count/span;
- 13-bit value;
- strict invalid-run rejection;
- sentinel normalization;
- confidence;
- `rawSignature`.

Verdict path intentionally returns `debug = null` for accepted signatures.

Verdict contract:

- accepted known/golden barcode should produce the same `rawSignature` as slow path;
- confidence does not need to equal slow-path confidence;
- `debug` stays absent;
- unsafe candidates are rejected or ambiguous rather than accepted.

## Camera and ROI architecture

Android camera access lives in `:app`.

Current camera adapter flow:

```text
CameraX ImageAnalysis
-> ImageProxy
-> centeredBarcodeRoi(...)
-> luma plane ROI extraction
-> GrayImage(width, height, pixels)
-> BarcodeDecoder.decode(...)
```

Important current optimization:

- ROI luma extraction copies only the guide ROI, not the whole frame.

Current guide:

- centered rectangle;
- width fraction `0.20`;
- height fraction `0.08`.

Threading model:

- CameraX analyzer runs on a dedicated single-thread executor;
- decoding runs on that analyzer executor;
- only the scan outcome is posted back to the main executor for Compose state mutation.

## TD state model

Current TD session state:

```kotlin
data class TdSessionState(
    val boards: Map<Int, BoardEditState> = emptyMap()
)
```

Current board edit state:

```kotlin
data class BoardEditState(
    val boardNumber: Int,
    val boardState: BoardState = BoardState(),
    val selectedSeat: Seat = Seat.NORTH
)
```

State ownership:

- `AppNavigation` owns current screen, active profile state, edited profiles, `TdSessionState`, and barcode orientation mode;
- `TdOverviewScreen` reads/writes `TdSessionState` through callbacks;
- `EditBoardScreen` receives one `BoardEditState` and writes updates through callback;
- incomplete boards are kept in the in-memory session map;
- navigation back to overview preserves board state;
- application relaunch persistence is not implemented yet.

## PBN architecture

Core PBN export lives in `PbnExporter`.

PBN export requires complete board validation.

Default export fields:

```text
[Event "ITROBOC Export"]
[Site "Local"]
[Board "N"]          optional when board number is provided
[Dealer "..."]
[Vulnerable "..."]   optional when vulnerability is provided
[Deal "D:..."]
```

Deal order starts from the dealer and proceeds clockwise through `Seat.next()`.

For TD session export, `TdSessionExchange.exportCompleteBoard(...)` derives duplicate-board metadata from board number:

- dealer cycle: `N, E, S, W, ...`
- vulnerability cycle over 16 boards:
  - `None, NS, EW, All, NS, EW, All, None, EW, All, None, NS, All, None, NS, EW`

Import is intentionally conservative: it only accepts blocks that parse into complete valid boards.

## Testing architecture

Tests are part of the architecture. Do not bypass them when changing behavior.

### Core tests cover

- `CardId` parsing;
- `HandState` and `BoardState` validity;
- duplicate rejection;
- board and hand progress summaries;
- deck profile lookup, metadata, serialization, editor conflicts, and completeness;
- built-in observed/default profile invariants;
- duplicate-board dealer/vulnerability cycle;
- TD scan accumulator typed outcomes and batch behavior;
- `EditBoardReducer` add/duplicate/full/auto-advance/auto-fill/clear behavior;
- PBN export.

### Vision tests cover

- Grid13 primitive functions;
- Grid13 bit/signature helpers;
- slow measurement/decoder behavior;
- sentinel normalization;
- strict invalid-run rejection;
- golden manifest coverage and observed-profile parity;
- verdict decoder parity against slow/golden signatures;
- degradation and sheet-analysis behavior.

### App tests cover

- Admin alias/evidence display;
- camera ROI extraction and ROI-only luma copy;
- profile UI models;
- read-only card/barcode preview helpers;
- orientation mode `viewedAs(...)` behavior;
- TD session import/export.

## Current limitations and non-goals

Keep this section architectural. Detailed tasks belong in `docs/new_tickets/**`.

Current limitations:

- TD overview grid is still structurally fixed around the current UI model unless changed by ticketed work;
- `AUTO` barcode orientation is intentionally unresolved;
- TD multi-card snap/OpenCV/perspective scanning is not implemented;
- profile and session persistence are in-memory except explicit import/export;
- app process death/relaunch recovery is not implemented;
- vision is currently centered-ROI based and assumes the barcode strip is aligned with the guide;
- verdict path is optimized enough for MVP but not allocation-free or benchmark-tuned;
- Android share/export behavior may need compatibility improvements in ticketed work.

Non-goals for this architecture file:

- do not embed full ticket specs here;
- do not replace `docs/product_context.md`;
- do not replace dev-history handoffs;
- do not document every UI pixel;
- do not treat symbolic collaboration notes as technical requirements.

## Guidance for future AI instances

Before changing code:

1. Read this file.
2. Read `docs/product_context.md`.
3. Read `docs/MVP-state-reached-handoff.md` if entering post-MVP work cold.
4. Read the relevant ticket under `docs/new_tickets/**` if the task references one.
5. Inspect the current source before assuming docs are newer than code.

When implementing:

- preserve module boundaries;
- keep core pure;
- keep vision card-semantic-free;
- keep Android adapters in app;
- keep Admin explanation-rich and TD verdict-focused;
- prefer small, reviewable patches;
- add or update tests for behavior changes;
- run `./gradlew test` when a full checkout with wrapper is available.

When uncertain:

- choose missed scan over wrong card;
- choose explicit raw-signature evidence over hidden inference;
- choose preserving `bfm`/`brm` orientation over clever canonicalization;
- choose pure reducer logic over Compose-local domain decisions;
- choose ticket files for future work rather than bloating architecture.

## Re-entry summary

ITROBOC is a real, post-MVP Android/Kotlin bridge TD tool.

Its core architecture is:

```text
:vision decodes barcode crops into raw signatures
:core maps signatures through Deck Profiles and owns bridge board truth
:app adapts Android camera/UI/session/share workflows around those pure modules
```

The current durable product split is:

```text
Admin = explanation
TD    = verdict
```

The main safety principle is:

```text
missed card is acceptable; wrong card mutation is dangerous
```

The main barcode principle is:

```text
visible runs are evidence; normalized Grid13 cells are identity; orientation is data
```
