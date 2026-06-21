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

For now, this may be a mock camera preview placeholder. No real camera is required in the first implementation.

Below the preview: mapping controls and selected-card status.

## Controls

Keep controls minimal.

Required buttons:

* `Scan`
* `Save`
* `Back`

Required option:

* `Auto-advance` toggle

### Scan behavior

`Scan` performs the full action:

`scan/read raw signature -> assign to selected card`

If the selected card already has mappings, the scanned signature is added as an alias.

There is no separate `Assign` button and no separate `Add alias` button.

In mock phase, the raw signature may come from a fake text field or generated mock scanner value.

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

Show all raw signatures currently assigned to that card.

Example:

`Aliases: 0x367A, 0x367B`

Each alias/signature should be clickable.

Clicking an alias opens a confirmation dialog:

`Remove alias 0x367A from ♦A?`

Buttons:

* `OK`
* `Cancel`

On `OK`, remove only that alias.

There is no separate `Clear selected mapping` button.

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

* real CameraX integration
* real barcode decoding
* persistence/database
* file import/export
* full admin calibration wizard
* cloud sync
* complex navigation framework

For now, use mock scanning to exercise the edit workflow.

## Acceptance behavior

At the end of the phase:

* The user can open Admin actions.
* The user can select a profile.
* The user can open Edit.
* The user sees a 52-card grid.
* Green/yellow status reflects whether each card has at least one mapping.
* The user can select a card.
* The user can mock-scan a raw signature and assign it to the selected card.
* The selected card shows its aliases.
* The user can click an alias and remove it after confirmation.
* Auto-advance can move selection to the next unmapped card after successful assignment.
* Save and Back exist.
* Core/profile editing logic is testable and not hidden inside Compose UI.
