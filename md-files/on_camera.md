# Camera and Beetle Mind: remaining work

Last audited: 2026-07-22, after the shared camera support cleanup.

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
