# Deferred Tickets: Admin Read-only and Grid13 Preview Hygiene

These are follow-up tickets from the `fe03f24` review. They are not urgent blockers, but they should be handled before the Admin/Profile workflow is considered polished.

---

## Progress

- Ticket 1: Done 
- Ticket 2: Done 
- Ticket 3: Done 
- Ticket 4: Done 

## Ticket 1 — Make Admin::Edit read-only mode truly non-mutating

### Context

The new Admin::Edit read-only / preview mode is useful: it lets us inspect a profile/card barcode without editing aliases.

However, the current UI still has mutation paths.

Known risky paths:

- Mock button may still call `applySignature(...)`.
- Alias details dialog may still expose `Remove`.
- Save may still be visible/enabled even when there is no intended mutation.
- Any accidental `editor.assign(...)` or `editor.remove(...)` path should be impossible in read-only mode.

### Goal

When `isReadOnly == true`, the Admin::Edit screen must be inspection-only.

### Required behavior

In read-only mode:

- Disable Scan button.
- Disable Mock button.
- Disable Save button.
- Disable alias Remove.
- Do not allow alias assignment.
- Do not allow alias deletion.
- Back should simply return without unsaved-change confirmation unless something truly changed.
- Alias details may still open, but only as inspection.

### Implementation hint

Centralize mutations behind helpers such as:

```kotlin
if (!isReadOnly) {
    applySignature(...)
}
```

But prefer disabling UI controls as well, so the user sees clearly that the screen is read-only.

### Tests / manual checks

- Open built-in observed profile in read-only mode.
- Try Scan: unavailable.
- Try Mock: unavailable.
- Tap alias chip: details visible, Remove unavailable.
- Press Back: no save/discard prompt if no mutation happened.
- Confirm profile alias counts do not change.

---

## Ticket 2 — Render sentinel-invalid Grid13 preview tokens in warning colors

### Context

`grid13BitsFromToken()` previews whatever bits are encoded in the token. This is good: the preview should show the truth, even when the token is suspicious.

However, if a token violates the Grid13 sentinel/control-bit rule, the preview should make that visible.

Valid shape:

```text
10.........01
```

Mask:

```kotlin
(value and 0x1803) == 0x1001
```

### Goal

When a preview token is sentinel-invalid, render the barcode preview in a warning palette instead of normal black/white.

Suggested palette:

- Ink cells: black
- Empty cells / background: soft pink
- Optional label: `sentinel warning`

This means the user can still inspect the real bits, but the UI says: “this meal looks suspicious.”

### Required behavior

For valid `bfm/brm` tokens:

- Render normal black/white Grid13 preview.

For invalid `bfm/brm` tokens:

- Render the encoded bits faithfully.
- Use soft pink / black warning palette.
- Optionally show a short warning label or icon.
- Do not silently normalize for preview display.

For malformed tokens:

- Keep current malformed-token handling, but consider a separate “invalid token” message.

### Implementation hint

Use a small helper:

```kotlin
fun isGrid13SentinelValid(value: Int): Boolean =
    (value and 0x1803) == 0x1001
```

Or reuse the existing Grid13 sentinel helper if accessible from the UI layer without bad dependencies.

### Tests / manual checks

- Preview `bfm12A5` -> normal black/white.
- Preview a synthetic invalid token such as `bfm02A4` -> soft pink/black warning preview.
- Confirm the bit pattern shown is the actual invalid token, not repaired bits.
- Confirm read-only preview remains non-mutating.

---

## Ticket 3 — Reconcile Grid13 docs with control-bit normalization

### Context

The final intended decoder policy is control-bit normalization:

> If the barcode evidence is otherwise coherent, normalize the four control cells into `10.........01`, but retain debug evidence and warnings.

Some older docs still imply that any sentinel failure must always become `Ambiguous` or `NotFound`.

### Goal

Update docs so they consistently describe the current policy.

### Required doc changes

Update stale text saying:

- “sentinel failure must return Ambiguous/NotFound”
- “do not emit after sentinel repair”
- “reverse aliases should not be present in observed profile”
- “C8 mismatch remains”

Replace with:

- control-bit normalization is intentional
- pre/post sentinel bits must be logged
- repair must be visible in debug
- built-in observed profile may contain both `bfm` and `brm`
- live calibration should not auto-invent reverse aliases
- C8 mismatch is historical, not current

### Acceptance

Docs no longer contradict current code behavior.

---

## Ticket 4 — Add focused tests for `bfm/brm` identity and sentinel preview

### Goal

Add focused tests to prevent future orientation regressions.

### Suggested tests

Core/profile tests:

- every observed token passes regex `^(bfm|brm)[0-9A-F]{4}$`
- every observed token passes sentinel mask
- no duplicate full token across observed profile
- known criss-cross pairs remain distinct
- `bfmX` and `brmX` are not canonicalized into one identity

App/UI tests if practical, or manual test cases if Compose tests are not ready:

- read-only mode cannot mutate aliases
- invalid sentinel preview uses warning palette
- valid sentinel preview uses normal palette
