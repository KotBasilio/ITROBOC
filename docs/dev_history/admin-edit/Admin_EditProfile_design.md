# Admin::EditProfile Design

Last aligned with source snapshot: `5a39c7e`.

Audience: future AI instances working on ITROBOC.
Scope: current-state design anchor for `docs/dev_history/admin-edit/Admin_EditProfile_design.md`.
Status: post-MVP Admin calibration / inspection design.

This file should stay lean: current behavior, invariants, ownership, and design intent only. Completed tickets and old migration notes should not live here.

## Purpose

`Admin::Edit` is the deck-profile calibration and inspection workshop.

Its job is to build, inspect, and debug mappings from observed raw barcode signatures to semantic bridge cards.

Load-bearing rule:

```text
Scanner does not know card meaning.
Deck Profile assigns meaning.
```

Correct flow:

```text
camera/decoder -> raw signature, e.g. bfm1549 -> DeckProfile -> CardId, e.g. SA
```

Incorrect flow:

```text
camera/decoder -> SA
```

The Admin area may be rich, diagnostic, and slower. It is allowed to explain. TD mode must remain verdict-oriented and field-fast.

## Product role

Admin exists to make Deck Profiles trustworthy.

The current default profile is the built-in observed profile:

```text
builtin-observed-v1
signatureModel = grid13-v2
```

That profile contains visually verified Grid13 aliases for all 52 cards. Each card currently has `bfm...` and `brm...` orientation-family aliases, yielding 104 raw-signature mappings.

Admin can also create/import/export custom profiles. Profile persistence is still in-memory for the current app runtime; JSON import/export is the current portable storage path.

## Module ownership

### `:core`

Core owns profile truth:

- `DeckProfile`
- `DeckProfileMetadata`
- `DeckProfileEditor`
- `DeckProfileEditResult`
- `BuiltInDeckProfiles`
- `CardId`, `Suit`, `Rank`

Core profile invariants:

- lookup is `rawSignature -> CardId`;
- one raw signature maps to at most one card;
- one card may have multiple aliases/raw signatures;
- `bfm...` and `brm...` aliases are distinct raw signatures;
- conflicts are reported, not silently overwritten;
- removing the last alias makes the card unmapped;
- completeness for Admin read-only availability means every canonical card has at least one alias.

Core must remain Android-free, Compose-free, CameraX-free, and vision-free.

### `:vision`

Vision owns raw-signature decoding.

Admin currently uses the slow/explanation-oriented Grid13 path through `BarcodeDecoder` / `Grid13SlowDecoder` via the Android adapter in `:app`.

Vision may emit debug evidence such as:

- signature model;
- forward/reverse raw tokens;
- Grid13 bit strings;
- RL2 form;
- run widths/gaps;
- confidence;
- warnings;
- sentinel repair/normalization evidence.

Vision must not map signatures to cards.

### `:app`

App owns Android/Compose/device integration:

- `AdminActionsScreen`
- `AdminEditScreen`
- `AdminProfileUiModels`
- `AdminReadOnlyCardPreview`
- `AdminAliasDetails`
- `AdminEditCameraSupport`
- `CameraFrameDecoder`
- `AdminEditScanDebugLogManager`

App owns:

- CameraX preview and frame capture;
- ROI guide overlay;
- luma ROI extraction into pure `GrayImage`;
- user-visible profile list/edit UI;
- in-memory profile selection/edit state;
- JSON import/export through Android document pickers;
- debug-log sharing through Android share/file-provider APIs.

Naming note:

```text
CameraFrameDecoder = neutral shared camera/analyzer name
AdminEditCameraFrameDecoder = current backing implementation / compatibility name
```

New shared camera/analyzer work should use `CameraFrameDecoder`.

## Admin::Actions current behavior

`Admin::Actions` is the profile-management entry point.

Current responsibilities:

- display profiles;
- show active profile;
- show built-in/demo markers;
- show signature model when present;
- select active profile;
- add a custom empty profile;
- delete active custom profile;
- reject deletion of built-in profiles;
- export active profile as JSON;
- import profile JSON and add it as a custom profile;
- navigate into `Admin::Edit`.

Current initial profiles:

- `Built-in Observed v1` (`builtin-observed-v1`, `grid13-v2`, default active);
- `Built-in Demo Bridge 52` (`builtin-demo-bridge52-v1`, synthetic debug/demo profile).

Custom profiles created from the UI:

- start empty;
- are marked non-built-in and non-demo;
- use `grid13-v2` as their signature model.

Imported profiles:

- are parsed with `DeckProfile.fromJson`;
- receive a new app-local custom profile id;
- become active;
- are stored in the current in-memory profile map.

The current app does not provide durable automatic profile persistence beyond import/export.

## Admin::Edit current workflow

The main calibration workflow is:

1. Choose a Deck Profile in `Admin::Actions`.
2. Enter `Admin::Edit`.
3. Select a target card in the 52-card grid.
4. Present one barcode strip to the camera guide.
5. Press `Scan`.
6. App waits for the next analyzer frame.
7. The centered guide ROI is cropped from the luma plane.
8. `:vision` decodes that ROI.
9. On `Found`, the raw signature is assigned to the selected card through `DeckProfileEditor`.
10. On conflict, unknown, or ambiguous decode, profile state does not change.
11. If auto-advance is enabled and assignment succeeds, selection advances to the first currently unmapped card.
12. Press `Save` to commit the edited profile to app-level in-memory state.

There is no separate Assign button. `Scan` means:

```text
capture next frame -> decode ROI -> assign found raw signature to selected card
```

## Layout

The screen is split into two panes.

Left pane:

- title: `Card Mapping`;
- `Read only` toggle;
- `Auto-advance` toggle;
- 52-card grid.

Grid shape:

```text
columns: ♠ ♥ ♦ ♣
rows:    A K Q J T 9 8 7 6 5 4 3 2
```

Visual status:

- green card = mapped;
- yellow card = unmapped;
- selected card = extra black border independent of mapped/unmapped color.

Right pane:

- camera preview or read-only inspection card;
- selected-card status;
- alias summary and alias chips;
- frame/decode/debug text;
- result message;
- debug log path;
- `Save`, `Back`, and `Share Debug Log` controls.

`Read only` is enabled only when every card has at least one alias. If the profile is incomplete, the UI explains that read-only unlocks after all 52 cards have at least one alias.

## Read-only inspection mode

Read-only mode is an inspection mode, not an editing mode.

When `isReadOnly == true`:

- CameraX is not bound for scanning;
- scan request state is cleared;
- `Scan` is disabled;
- `Mock` is disabled;
- `Save` is disabled;
- alias details can still be opened;
- alias removal is not offered;
- `Back` returns without a save/discard prompt if there is no dirty state.

Read-only mode displays `AdminReadOnlyCardPreview` for the selected card.

The read-only preview:

- selects the first usable `bfm...` alias and first usable `brm...` alias for the card;
- renders an upright and a 180-degree card-corner view;
- renders barcode strips from the encoded 13-bit Grid13 token;
- keeps `bfm` and `brm` physically/orientationally distinct;
- shows missing barcode areas when an orientation-family alias is absent;
- renders sentinel-invalid tokens faithfully but with warning styling.

Important rule:

```text
Preview must show actual token bits. It must not silently repair invalid preview tokens.
```

## Editing mode

Editing mode is active when `isReadOnly == false`.

In editing mode:

- CameraX preview is active if camera permission is granted;
- `Scan` is enabled when camera permission is granted;
- `Mock` is enabled as a debug fallback;
- alias removal is available from alias details;
- `Save` commits `editor.toDeckProfile()` to app-level in-memory state;
- `Back` asks whether to save or discard when there are unsaved changes.

The current editor is created with:

```kotlin
activeDeckProfile.toEditor()
```

and remembered per active profile id.

## Camera/scan path

Admin live scan path:

```text
CameraX ImageProxy
-> frame debug metadata
-> centered barcode ROI from adminScanGuideSpec
-> luma-only GrayImage crop
-> CameraFrameDecoder / slow Grid13 decode
-> BarcodeDecodeResult
-> DeckProfileEditor assignment on Found
-> debug JSONL record
```

Current guide spec:

```kotlin
widthFraction = 0.20f
heightFraction = 0.08f
```

The same guide spec is used for:

- visible overlay rectangle;
- actual ROI crop sent to vision.

This keeps the visible mouth and the analyzed mouth aligned.

The guide is intentionally small and strip-focused. It is tuned for one barcode strip, not a full-card OCR region.

Current extraction is luma-only. This is intentional for current MVP simplicity. Static research paths may still discuss RGB/min-RGB ink; if red-suit instability appears in live calibration, RGB/YUV-aware extraction is a future seam, not current behavior.

## Decode outcomes

Admin handles `BarcodeDecodeResult` as follows:

### `Found`

- use `signature.rawSignature` as the profile alias;
- store current-session alias evidence in `scanEvidenceByAlias`;
- call `editor.assign(rawSignature, selectedCard)`;
- report assigned/already-existing/conflict status;
- mark profile dirty only on a new assignment;
- auto-advance only after a new assignment when auto-advance is enabled.

### `NotFound`

- show the failure reason;
- do not mutate profile state.

### `Ambiguous`

- show the ambiguous/candidate description;
- do not mutate profile state.

### Conversion failure

- show the conversion/crop/extraction error;
- do not mutate profile state.

The Admin path may explain failures. TD mode should be much less verbose.

## Alias behavior

Aliases are raw signatures mapped to a `CardId`.

Current examples:

```text
bfm1549 -> SA
brm1255 -> SA
```

Both aliases may point to the same card and must remain distinct.

Alias chips in `Admin::Edit`:

- show all aliases for the selected card;
- open an alias details dialog when tapped.

Alias details:

- always show the alias token;
- show retained scan evidence only for aliases scanned during the current edit session;
- show a clear note when no current-session evidence is retained;
- allow removal only outside read-only mode.

Removing an alias calls `editor.remove(alias)`, marks the editor dirty, and does not remove any other alias for that card.

## Auto-advance behavior

After a successful new assignment, if `Auto-advance` is enabled, selection moves to the first unmapped card in deck order.

Deck order is:

```text
Suit.entries × Rank.entries
```

This is currently first-unmapped, not necessarily next-unmapped relative to the previously selected card.

If all cards are mapped, Admin reports profile completion.

## Mock behavior

`Mock` remains a debug fallback in editing mode.

Current behavior:

- generates a random synthetic hex signature like `0x1A2B`;
- assigns it to the selected card through the same `applySignature` helper;
- reports `Mock fallback used`.

Mock is not the real calibration path and should not be used to infer physical barcode behavior.

## Save/back behavior

`Save` commits the current editor into app-level in-memory profile state:

```text
activeProfileId -> updated DeckProfile
```

It then returns to `Admin::Actions`.

If the editor is dirty and not read-only, `Back` opens a save/discard dialog. If the editor is clean or read-only, `Back` returns to `Admin::Actions` immediately.

## Debug logging

Admin keeps a JSON Lines debug log for camera scan attempts.

Current behavior:

- log is cleared once on app process start by `AdminEditScanDebugLogStartup.clearOnce`;
- one camera scan attempt appends one JSONL record;
- log path is shown in the UI;
- `Share Debug Log` exposes the log via Android FileProvider/share intent;
- no durable database/log browser exists inside the app.

Log records include frame metadata, ROI, decode result, raw signature/confidence on success, failure reasons, Grid13 evidence when present, warnings, ambiguity data, and deck-profile match count at scan time.

`scanlineAgreement` currently remains `null` until multi-scanline analysis exists.

## Profile JSON import/export

Admin profile JSON is handled through `DeckProfile.toJson()` and `DeckProfile.fromJson()`.

Export:

- uses Android `CreateDocument("application/json")`;
- writes the active profile JSON;
- suggests a filename from the active profile display name.

Import:

- uses Android `OpenDocument()`;
- accepts JSON/text/general MIME types;
- parses a Deck Profile;
- assigns a fresh app-local custom id;
- marks it non-built-in and non-demo;
- adds it to profile state and makes it active.

The current JSON parser is lightweight/manual. It is sufficient for the current controlled profile format, not a general JSON framework.

## Current non-goals

Admin::Edit currently does not own:

- TD board scanning flow;
- PBN export;
- scoring/traveller calculations;
- durable automatic profile persistence;
- profile database/schema migrations;
- multi-scanline agreement;
- RGB/min-RGB live extraction;
- full-card OCR;
- automatic reverse-alias invention during live calibration;
- generic retail barcode decoding.

## Testing anchors

Current important test coverage lives in:

```text
:core
- DeckProfileTest
- DeckProfileEditorTest
- DeckProfileSerializationTest

:app
- AdminProfileUiModelsTest
- AdminAliasDetailsTest
- AdminReadOnlyCardPreviewTest
- AdminEditCameraSupportTest
```

Useful behaviors protected by tests include:

- raw-signature lookup;
- profile metadata and JSON round-trip;
- built-in observed profile coverage and sentinel-valid alias format;
- `bfm`/`brm` identity distinction;
- editor assign/conflict/remove/completeness behavior;
- new custom profile model;
- alias evidence display;
- read-only Grid13 token preview conversion and sentinel-invalid detection;
- ROI calculation and luma ROI extraction;
- debug JSONL fields.

## Documentation hygiene rule

This file should remain the single current-state design anchor for Admin EditProfile.

When an Admin ticket is completed:

- remove or archive the ticket details;
- update this file only if the completed work changed current behavior, ownership, invariants, or non-goals;
- do not preserve every historical step here.

Architecture carries beams. Tests carry evidence. Tickets carry unfinished work. This file carries the current Admin design shape.
