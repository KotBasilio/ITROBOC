# Hand-off Note: Verdict Decoder and SPS Telemetry

Date: 2026-07-01
Audience: Selyn / future maintainers
Area: `TD::EditBoard`, `Grid13VerdictDecoder`, `scansPerSecond`

## Summary

Recent work around `TD::EditBoard` created two connected design shifts:

1. `Grid13VerdictDecoder` is now a real independent fast-path decoder for TD use.
2. `scansPerSecond` is now field telemetry based on recent inter-scan deltas, not a strict benchmark counter.

The intent is to keep `Admin::Edit` explanation-rich and `TD::EditBoard` speed-focused.

## Decoder Path Split

Current intended contract:

- `Admin::Edit` uses the explanation / slow path.
- `TD::EditBoard` uses the verdict / fast path.

This split happened in phases:

- Phase 1 introduced the seam:
  - `Grid13VerdictDecoder` existed as a wrapper that stripped debug.
  - This proved call-site separation but did not improve speed much.
- Phase 2 introduced an independent implementation inside `Grid13VerdictDecoder`.
  - It now computes projection, threshold, black runs, active span, 13 bits, run gate, sentinel normalization, confidence, and `rawSignature` directly.
  - It intentionally avoids the full debug / forensic payload.

Related naming cleanup:

- Old full decoder was renamed from `Grid13BarcodeDecoder` to `Grid13SlowDecoder`.
- This is deliberate: the codebase now has a clearer mental model:
  - `Grid13SlowDecoder` = explanation / forensic / alignment reference
  - `Grid13VerdictDecoder` = TD runtime path

## What Must Stay True

The important long-term contract for `Grid13VerdictDecoder` is not exact internal parity with slow debug behavior.

The important contract is:

- same accepted barcode -> same `rawSignature`
- verdict result is safe for TD mutation flow
- verdict path does not expose debug payload

Confidence should not be over-constrained to exactly match `Grid13SlowDecoder`.
That equality briefly reappeared in tests and was intentionally relaxed again.

Current test philosophy should remain:

- assert signature parity on known synthetic / fixture inputs
- assert `debug == null` on verdict results
- allow verdict confidence to differ, as long as it is usable and within sane range

## Orientation Model

Current TD orientation model is:

- visible payload is primary
- BRM means "same visible payload, different family prefix"

This is why `viewedAs(mode)` is the right TD lookup path.
`signatureFor()` was removed from the active path because it encouraged reading `debug.reverseSignature` as if it were the operational lookup token.

Current default orientation was intentionally changed to `BRM`.
This was not accidental.

## SPS Telemetry Semantics

`scansPerSecond` should currently be understood as meaningful field telemetry, not strict scientific benchmarking.

Current behavior:

- hot path (`onScanProcessed`) only appends deltas:
  - every callback contributes a delta
  - no filtering or rate math happens there
- background `LaunchedEffect`:
  - walks the delta list backwards
  - retains only the recent tail spanning about the last 1000 ms
  - derives the displayed cadence from that retained tail

Important detail:

- the retained tail now includes the delta that crosses the 1000 ms boundary
- the displayed rate is computed from the same retained deltas
- this fixed an earlier logic mismatch where numerator and denominator described different tails

Current idle convention:

- when no retained deltas exist, `scansPerSecond` becomes negative
- the negative magnitude is derived from `scansIdleCount`
- this is intentional and useful in field testing:
  - shows how many `LaunchedEffect` loops happened before camera activity
  - also shows continued idle loops when camera is off / quiet

So this metric is best read as:

- recent scan cadence over trailing ~1 s while active
- visible idle-loop signal while inactive

Not as:

- formal decoder benchmark throughput

## Camera Threading

`CameraPreview()` does not decode on the UI thread.

Current threading model:

- CameraX analyzer runs on `analysisExecutor`, a dedicated single-thread executor.
- `frameDecoder.decode(imageProxy)` runs there.
- only after decode finishes does the code hop to `mainExecutor`.
- `onScanProcessed(scanOutcome)` runs on the main thread.

So:

- decode work is background work
- Compose state updates and UI-facing telemetry updates happen on the main thread

This matters when interpreting `scansPerSecond`:

- it reflects callbacks arriving to the UI layer
- not an instrumented timer placed inside the decoder itself

## Suggested Next Care

- Keep `Grid13SlowDecoder` available as the alignment oracle during fast-path evolution.
- Prefer adding parity tests on known barcodes rather than re-tightening confidence equality.
- If benchmarking becomes more formal later, add separate instrumentation for:
  - decoded outcomes per second
  - average decode latency
  - p95 decode latency

## Current Mental Model

- Slow path: narrator, forensic footprints, debug jewelry
- Verdict path: operational answer for TD
- SPS: field cadence signal, not lab-grade truth meter
