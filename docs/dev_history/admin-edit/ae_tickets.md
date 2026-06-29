## Progress

- Ticket 1: Done 
- Ticket 2: Done 
- Ticket 3: Done 


Ticket 1 — Add editable deck-profile core model

Goal:
Add pure core/domain support for editing Deck Profile mappings with aliases.

Requirements:

* Add an editable profile model or editing helper in `:core`.
* Keep canonical mapping as `rawSignature -> CardId`.
* Provide derived view `CardId -> List<rawSignature>`.
* Support operations:

  * assign signature to card
  * remove signature/alias
  * get aliases for card
  * check if card is mapped
  * count mapped cards
  * check profile completeness
* Enforce invariants:

  * one raw signature maps to at most one card
  * one card may have multiple raw signatures
  * removing last alias makes the card unmapped
  * conflicts are reported, not silently overwritten
* Add typed edit results, for example:

  * Assigned
  * AlreadyAssignedToSelected
  * SignatureConflict
  * NoCardSelected if relevant
* Add tests for:

  * assigning first signature
  * adding alias to same card
  * same signature scanned again for same card
  * same signature attempted for different card = conflict
  * removing alias
  * completeness count
  * all 52 mapped = complete
* No Android code in this ticket.

---

Ticket 2 — Add Admin::Edit UI shell with mock scanner

Goal:
Add the first Admin::Edit screen in `:app`, using the editable profile logic from core and a mock signature input/source. No real camera yet.

Requirements:

* Add navigation from `Admin actions` → `Edit`.
* The screen should be split into left/right parts.
* Left part:

  * 52 card buttons
  * 4 columns: ♠ ♥ ♦ ♣
  * 13 rows: A K Q J T 9 8 7 6 5 4 3 2
  * green = mapped
  * yellow = unmapped
  * selected card has visible extra marker
* Right part:

  * mock camera preview placeholder occupying roughly upper 66%
  * selected-card status
  * list of aliases for selected card
  * each alias clickable for removal confirmation
  * controls: Scan, Save, Back, Auto-advance toggle
* `Scan` should:

  * use a mock raw signature source/input for now
  * assign/add alias to the selected card through core editing logic
  * show result/conflict/status message
  * auto-advance to next unmapped card if enabled and assignment succeeded
* `Save` should update app-level profile state in memory.
* `Back` should return to Admin actions.
* Do not add CameraX/camera permissions.
* Do not add persistence/database.
* Keep Compose UI code app-only.
* Build app successfully.

---

Ticket 3 — Polish Admin::Edit behavior and docs

Goal:
Stabilize the Admin::Edit prototype after the first UI shell exists.

Requirements:

* Make the selected-card status clear:

  * selected card name
  * mapped/unmapped status
  * aliases/signatures list
* Add removal confirmation dialog for aliases:

  * `Remove alias 0x1234 from ♦A?`
  * OK/Cancel
* Add helpful messages for:

  * no card selected
  * no signature detected
  * signature assigned
  * alias already exists
  * signature conflict
  * alias removed
  * profile complete
* Ensure built-in demo profile is not permanently mutated in core.
* Update README/docs:

  * Admin::Edit is a mock calibration workflow.
  * It supports aliases/multiple signatures per card.
  * Real camera and persistent profile storage are future work.
* Run:

  * `./gradlew :core:test`
  * `./gradlew :app:assembleDebug`

---

