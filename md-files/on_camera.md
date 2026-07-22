# Camera and Beetle Mind: remaining work

Last audited against source snapshot: `a335245`.

This file is only a remaining-work list. Remove an item when it lands. Preserve
the architectural relay:

```text
camera/vision sees -> Beetle Mind trusts or hesitates
                   -> controller routes an accepted signature
                   -> reducer mutates board truth
                   -> Compose shows the result
```

## 1. Extract Beetle Mind from `EditBoardController`

Consensus, unknown handling, thought/dream state, and accepted-signature
debounce still live inside
[`EditBoardController.kt`](../app/src/main/java/org/itroboc/app/EditBoardController.kt).
Extract them into a small pure Kotlin class in `:app`, with typed input and
output models.

The mind should own evidence-trust policy:

- configurable Found consensus, using the current allowed range `6..2` and
  default `4` frames;
- pondering signature/count and thought presentation;
- Ambiguous, NotFound, and conversion-failure reset/uncertainty behavior;
- unknown signatures may appear in thought but never become accepted scans;
- accepted-signature debounce;
- dream/reset behavior for `Eyes`, `Hands`, `Wind`, and `Joker`.

The mind must not own:

- CameraX, `ImageProxy`, permissions, or Compose;
- `BoardState`, reducer calls, or scan legality;
- PBN, storage, or Scissors behavior.

`EditBoardController` should translate camera evidence into mind input, publish
the resulting thought, and route only a typed accepted signature through the
existing deck-profile/reducer path. Manual add must continue to bypass
stabilization and blank the mind.

Add `BeetleMindTest` coverage independent of Compose and CameraX for consensus,
threshold changes, unknowns, ambiguous/reset behavior, dreams, and debounce.
Keep controller integration tests proving that only accepted output reaches the
reducer.

Done means the evidence-trust state no longer lives in the controller and the
existing TD scan behavior is unchanged.

## 2. Finish the neutral `CameraFrameDecoder` migration

[`CameraFrameDecoder.kt`](../app/src/main/java/org/itroboc/app/CameraFrameDecoder.kt)
currently provides only a typealias. Admin and TD production call sites still
construct and type against `AdminEditCameraFrameDecoder`.

Finish the migration:

1. make production and test call sites use `CameraFrameDecoder`;
2. rename or replace the backing implementation so shared infrastructure no
   longer has an Admin-owned class name;
3. keep Admin on `Grid13SlowDecoder` and TD on `Grid13VerdictDecoder` through
   decoder injection;
4. remove the compatibility alias once no legacy references remain;
5. update current architecture/product wording when the migration lands.

Do not change ROI extraction, decoder behavior, or scan outcomes as part of the
naming migration.

Done means `rg AdminEditCameraFrameDecoder` finds no production or test usage.

## 3. Extract the duplicated camera preview/analyzer component

[`AdminEditScreen.kt`](../app/src/main/java/org/itroboc/app/AdminEditScreen.kt)
and
[`EditBoardScreen.kt`](../app/src/main/java/org/itroboc/app/EditBoardScreen.kt)
still contain near-duplicate private `CameraPreview` and
`BarcodeGuideOverlay` implementations.

Extract one shared `:app` camera composable after the neutral decoder migration.
Keep the existing request seam rather than introducing a second mode model
without need:

```kotlin
consumeScanRequest: () -> Boolean
```

Admin must continue to consume one requested frame with `compareAndSet`; TD must
continue to accept the stream while its camera surface is active.

The shared component must preserve:

- one analysis executor and `STRATEGY_KEEP_ONLY_LATEST`;
- guaranteed `ImageProxy` closure;
- latest callback capture via `rememberUpdatedState`;
- exact-size reusable ROI pixel storage;
- analyzer-thread decode and Main-thread outcome delivery;
- lifecycle bind/unbind and executor shutdown;
- the current centered guide overlay.

Keep screen-owned concerns outside the component: permission prompts, Admin
calibration state, TD Beetle Mind/controller handling, board completion, and
modal navigation.

Run the full unit suite after extraction. Archy will verify Admin snap and TD
stream camera behavior visually in the IDE; do not add automated rendering as
a substitute.

Done means only one production implementation owns the CameraX preview,
analyzer lifecycle, ROI buffer, and guide overlay.
