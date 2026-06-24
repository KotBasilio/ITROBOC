# ITROBOC Admin::Edit Profile Design

## Purpose

`Admin::Edit` is the profile calibration workshop.

The screen edits the currently selected Deck Profile. Its job is to let a user assign observed raw barcode signatures to semantic bridge cards.

The key architectural rule remains:

**The scanner produces raw signatures. The Deck Profile assigns meaning.**

So the scanner/image decoder should not know that a pattern means `DA` or `C7`. It only produces a raw signature. The profile maps that signature to a canonical `CardId`.

## Main workflow

The intended user flow is:

1. Open `Admin actions`.
2. Select a Deck Profile.
3. Press `Edit`.
4. The `Admin::Edit` screen opens.
5. User selects a card on the left, for example `♦A`.
6. User presents the physical card to the camera / mock scanner.
7. User presses `Scan`.
8. The app obtains one raw signature.
9. The app assigns that signature to the selected card.
10. If that card already has other signatures, the new signature is added as an alias.
11. If auto-advance is enabled, selection moves to the next unmapped card.
12. User repeats until all 52 cards are mapped.
13. User presses `Save`.

## Layout

The screen is split into left and right parts.

### Left part: 52-card mapping grid

The left part displays 52 card buttons.

Use 4 columns and 13 rows:

* Columns: `♠`, `♥`, `♦`, `♣`
* Rows: `A`, `K`, `Q`, `J`, `T`, `9`, `8`, `7`, `6`, `5`, `4`, `3`, `2`

Each button represents one `CardId`.

Card button colors:

* Green = card has at least one raw signature mapped to it.
* Yellow = card has no mapping assigned.

The currently selected card should have an additional visual marker independent of color, such as border/glow/thicker outline.

### Right part: scanner and controls

The right part is split vertically.

Top area: camera preview, about 66% of the right part.

Read-only mode is enabled by default. It keeps CameraX unbound and replaces the
camera preview with a visual inspection card when the selected card has Grid13
aliases. The card shows upright and 180-degree corner marks plus `bfm` and
`brm` aliases rendered as tall 13-cell black/white barcodes. Their physical
positions communicate orientation, so the inspection card omits redundant
text labels and compact tokens. This supports manual comparison against the
physical deck without changing profile data.

The current prototype now uses a real CameraX preview for Admin-side calibration.

A small centered scan guide rectangle is drawn over the preview. The same guide spec is used for the visible overlay and the actual ROI crop sent to the decoder, so the "mouth" the user sees and the crop the app analyzes stay aligned.

The guide is intentionally small and currently tuned for one barcode strip rather than a broad card region. The exact fractions may still be adjusted as manual testing teaches us more.

Below the preview: mapping controls and selected-card status.

## Controls

Keep controls minimal.

Required buttons:

* `Scan`
* `Save`
* `Back`
* `Share Debug Log`

Required options:

* `Read only` toggle
* `Auto-advance` toggle

Current UI placement:

* `Read only` and `Auto-advance` sit beside the `Card Mapping` title on the left pane.
* `Scan` sits near `Selected: <card>` and is visually stronger than `Mock`.
* `Mock` remains available as a fallback/debug path.

### Scan behavior

`Scan` performs the full action:

`scan/read raw signature -> assign to selected card`

If the selected card already has mappings, the scanned signature is added as an alias.

There is no separate `Assign` button and no separate `Add alias` button.

Current prototype behavior:

* one button press requests one camera frame
* the app crops the centered guide ROI from the frame
* the app converts that ROI into a pure grayscale image
* the pure `:vision` decoder returns `Found`, `NotFound`, or `Ambiguous`
* on `Found`, the raw signature is assigned through existing profile-edit logic
* on `NotFound` or `Ambiguous`, profile state does not change

The raw signature format currently remains synthetic/debug-oriented rather than a claim about any real physical deck encoding.

### Save behavior

`Save` commits the edited profile state to app-level state.

Persistence is not required yet. Saving may remain in-memory for this phase.

### Back behavior

`Back` returns to `Admin actions`.

If there are unsaved changes, it is acceptable to either:

* ask for confirmation, or
* keep it simple for this phase and discard/keep according to existing in-memory behavior.

Prefer simple behavior unless the implementation is already clean.

### Auto-advance

If `Auto-advance` is enabled, after a successful new assignment the selected card moves to the next unmapped card.

If no unmapped cards remain, selection stays or moves to a sensible final state.

Current prototype note:

* Known issue: the current mock implementation jumps to the first unmapped card in deck order, not yet the next unmapped card relative to the current selection.

## Selected card status area

Show the currently selected card, for example:

`Selected: ♦A`

Show whether the card is mapped plus the currently known aliases.

Example:

`Mapped • Aliases: 0x367A, 0x367B`

Each alias/signature should be clickable.

Clicking an alias opens an alias details dialog. For aliases scanned during the current edit session, the dialog can show retained decoder evidence such as:

* signature model
* forward/reverse Grid13 bits
* reverse token
* RL2
* black and white run widths
* confidence
* warnings

Older aliases may only show the compact alias plus a note that no scan evidence is retained in the current session.

The same dialog keeps the existing remove action for the selected alias.

Buttons:

* `Remove`
* `Close`

On `Remove`, remove only that alias.

There is no separate `Clear selected mapping` button.

## Debug feedback and logging

The current prototype keeps lightweight debug feedback visible on screen:

* frame width / height / rotation / timestamp
* latest decoder outcome
* latest assignment/conflict message
* log file path

In addition, each camera scan attempt is appended to a JSON Lines debug log. The user can export/share that log through `Share Debug Log`.

Transient share/export status text may be hidden in the UI when space is tight. The durable retrieval affordances are the visible log path plus `Share Debug Log`.

The debug log is cleared once when the app process starts, so a manual testing session begins with a fresh file instead of accumulating old scans across launches.

Current camera scan attempts use the `grid13-v1` decoder and produce compact forward raw signatures such as `bfm1549`. The reverse/debug token such as `brm1255` is logged for diagnosis, but it is not automatically added as a profile alias.

The current live camera path derives its ink signal from the luma ROI already supplied to `:vision`. The static research path prefers min-RGB ink, so red-suit instability may point to a future RGB/YUV-aware extraction seam.

Current log content includes at least:

* selected card at scan-request time
* frame dimensions and rotation
* ROI pixel rectangle, crop width/height, and current ROI angle placeholder
* decode result type
* found raw signature and confidence when present
* failure reason when present
* threshold mode/value when present
* signature model, forward/reverse Grid13 bits, forward/reverse compact tokens, RL2, active span, and run debug info when present
* ambiguity flag and ambiguous candidates when present
* scanline-agreement field, currently `null` until multi-scanline analysis exists
* deck-profile match count at scan time

## Data layout and invariants

The canonical profile lookup should remain raw-signature-oriented:

`Map<RawSignature, CardId>`

This is ideal for runtime scanning because the scanner gives us a raw signature and we need a fast lookup.

For editing and display, derive a card-oriented view:

`CardId -> List<RawSignature>`

This derived view is used to show aliases for the selected card and to color the 52-card grid.

Important invariants:

1. A single raw signature maps to at most one card.
2. A card may have zero, one, or many raw signatures.
3. A card is considered mapped if it has at least one raw signature.
4. A profile is complete when all 52 cards have at least one raw signature.
5. Removing the last alias from a card makes that card unmapped.
6. Built-in demo profiles should not be mutated directly unless the app deliberately creates an editable custom copy.

## Assignment outcomes

When `Scan` produces a raw signature and a card is selected, profile editing should return a typed result, not just boolean/string.

Suggested outcomes:

* `Assigned(card, signature)`
  Signature was previously unmapped and is now assigned to the selected card.

* `AlreadyAssignedToSelected(card, signature)`
  Signature was already mapped to the selected card. This is harmless.

* `AddedAlias(card, signature)`
  Optional separate outcome if the selected card already had other aliases and this signature was newly added.

* `SignatureConflict(signature, existingCard, selectedCard)`
  Signature is already mapped to a different card. Do not silently reassign.

* `NoCardSelected`
  Scan cannot assign because no card is selected.

* `NoSignatureDetected`
  Mock/camera produced no usable raw signature.

For a first implementation, fewer outcomes are acceptable if the behavior is clear and tested. But conflict must not silently overwrite.

## Conflict behavior

If the scanned signature is already mapped to another card, do not overwrite automatically.

Show a clear warning such as:

`0x367A is already mapped to ♣7.`

Future UI may support explicit reassignment, but this first phase should simply block silent reassignment.

## Built-in profile behavior

The built-in demo profile is a reference/demo profile. It should normally be protected.

Recommended behavior:

* If editing a built-in profile, work on an in-memory editable copy.
* On Save, either:

  * create a custom profile copy, or
  * for this early phase, keep the edited profile as app-local in-memory state.

Do not permanently mutate the built-in profile definition in core.

## Out of scope

Do not implement yet:

* persistence/database
* file import/export
* full admin calibration wizard
* cloud sync
* complex navigation framework
* TD multi-card scanning
* full rotation/perspective correction
* production-grade barcode decoding tuned to real deck physics

The current build already includes early CameraX integration and an experimental pure decoder path. Those are still exploratory calibration tools, not finished production scanning.

## Acceptance behavior

At the end of the phase:

* The user can open Admin actions.
* The user can select a profile.
* The user can open Edit.
* The user sees a 52-card grid.
* Green/yellow status reflects whether each card has at least one mapping.
* The user can select a card.
* The user can request one camera scan through the visible guide ROI and assign a found raw signature to the selected card.
* The user can still use mock-scan fallback.
* The selected card shows its aliases.
* The user can click an alias and remove it after confirmation.
* Auto-advance can move selection to the next unmapped card after successful assignment.
* Save, Back, and Share Debug Log exist.
* Core/profile editing logic is testable and not hidden inside Compose UI.
