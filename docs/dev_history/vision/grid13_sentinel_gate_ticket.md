# Bob Ticket: Add Grid13 sentinel gate and orientation-aware profile checks

## Status

Implemented on the `dev/vision` branch. The final policy treats the outer four
cells as control bits and normalizes them to `10.........01`. Debug evidence
retains the measured bits and records the correction.

## Goal

Strengthen `grid13` decoding and observed-profile validation by enforcing the outer sentinel structure:

```text
10.........01
```

This means every valid 13-bit Grid13 payload must satisfy:

- `bit12 = 1`
- `bit11 = 0`
- `bit1  = 0`
- `bit0  = 1`

Numeric mask:

```kotlin
(value and 0x1803) == 0x1001
```

## Motivation

The observed card barcodes appear to be bounded by thin black sentinel/calibration bars. The first and last cells should be black, and the immediately adjacent cells should be white gaps.

This provides a cheap and powerful error detector for:
- bad crop alignment
- bad thresholding
- accidental bit shifts
- stale golden-table values
- wrong run/cell interpretation

## Required changes

### 1. Add a Grid13 sentinel check helper

Add a pure Kotlin helper in `:vision` or wherever Grid13 signatures are currently modeled.

Suggested API shape:

```kotlin
data class Grid13SentinelCheck(
    val isValid: Boolean,
    val bit12: Boolean,
    val bit11: Boolean,
    val bit1: Boolean,
    val bit0: Boolean,
    val issues: List<String>,
)

fun checkGrid13Sentinels(value: Int): Grid13SentinelCheck
```

Or use an equivalent existing style.

The helper should validate only the 13-bit payload, not the prefix.

### 2. Apply sentinel check in decoder result production

When the decoder produces a candidate Grid13 payload:

- If sentinel check passes:
  - proceed normally.
- If sentinel check fails:
  - do not emit a confident `bfm...` alias.
  - return `Ambiguous` or `NotFound`, depending on existing result vocabulary.
  - include sentinel failure details in debug/warnings.

Do not silently emit invalid aliases.

### 3. Control-bit normalization policy

The four outer sentinel cells are treated as control bits and normalized to
`10.........01` before signature production. The measured bits remain evidence,
and normalization is logged explicitly:

```text
sentinelRepairApplied = true
sentinelRepairReason = "endpoint cell borderline but active-span boundary supports sentinel"
preRepairBits = ...
postRepairBits = ...
```

Confidence is checked after normalization. A corrected candidate may therefore
still return `Ambiguous` when confidence is insufficient.

### 4. Add debug fields

Add or preserve debug fields such as:

```text
sentinelValid
sentinelIssues
grid13FwdBitsPreSentinel
grid13FwdBits
grid13RevBits
rawSignature
reverseSignature
warnings
```

Use names consistent with existing debug structures.

### 5. Add observed-profile invariant tests in `:core`

For the built-in observed Grid13 profile:

- every alias token must match `^(bfm|brm)[0-9A-F]{4}$`
- every alias token must pass the sentinel mask
- every full alias token must map to exactly one CardId
- `bfm` and `brm` must remain distinct raw-signature families
- no canonical min/max forward/reverse lookup is used

### 6. Preserve orientation

Do not canonicalize forward and reverse payloads.

Do not silently transform:

```text
bfmX / brmY
```

into:

```text
bfmY / brmX
```

unless there is an explicit physical-orientation reason.

The prefix is part of the raw signature identity.

## Acceptance criteria

- `:vision:test` passes.
- `:core:test` passes.
- Decoder no longer returns confident aliases whose payload fails `(value and 0x1803) == 0x1001`.
- Built-in observed profile has no duplicate full aliases.
- Built-in observed profile aliases all satisfy the sentinel rule.
- Debug output clearly explains sentinel failures.
- Existing Admin::Edit flow still displays compact aliases like `bfm12A5`.

## Notes

Internally, `bfm` and `brm` are nicknamed:

- `bfm` = beetle forward meal
- `brm` = beetle reverse meal

This can be mentioned cheerfully in docs, but the key technical rule is that the prefixes preserve orientation.
