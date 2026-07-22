# TD scan hot-path profiling: remaining work

Last audited against source snapshot: `ff06fbe`.

This file is only a remaining-work list. Remove an item when it lands. Do not
optimize from the old hot-path sketch alone: measure the real TD stream on a
target Android device first.

## 1. Capture a repeatable on-device baseline

There is no retained device baseline yet for the live TD scan path.

Capture a release-like stream session and record at least:

- device model and Android version;
- camera frame and ROI dimensions;
- configured consensus-frame count;
- delivered scans per second;
- analyzer ROI-copy, decoder, and total-analyze timings;
- enough samples to report median and a tail value such as p95;
- whether the session showed visible stutter, backlog, or delayed thoughts.

The existing opt-in `AnalyzerTiming` log in
[`AdminEditCameraSupport.kt`](../../app/src/main/java/org/itroboc/app/AdminEditCameraSupport.kt)
can supply the analyzer-stage timings. Keep profiling disabled during ordinary
TD use.

Done means a checked-in or attached result identifies the tested scenario and
gives evidence about which lane, if any, is the bottleneck.

## 2. Measure the analyzer-to-Main gap

Current timing ends when decoding ends. It does not measure:

- time queued in `mainExecutor`;
- time spent in `EditBoardController.handleCameraScan(...)`;
- delay from analyzer completion to the resulting visible UI state.

Add opt-in trace sections or monotonic timing around those boundaries, then
profile them on-device. Avoid permanent per-frame logging or additional
Compose state just to collect the measurements.

The relevant handoff remains in
[`EditBoardScreen.kt`](../../app/src/main/java/org/itroboc/app/EditBoardScreen.kt),
and controller processing remains in
[`EditBoardController.kt`](../../app/src/main/java/org/itroboc/app/EditBoardController.kt).

Done means the analyzer, queue, and controller costs can be compared using the
same capture instead of inferred from scans-per-second telemetry.

## 3. Remove controller hot-path churn if measurement justifies it

`EditBoardController` still performs avoidable collection work:

- every processed frame uses `scanDeltas = scanDeltas + delta`, allocating a
  new list;
- every 500 ms, `updateMetrics()` builds and reverses a pruned list;
- every analyzed outcome crosses to Main, where telemetry and thought state
  are considered for publication to Compose.

If profiling shows meaningful allocation, GC, queue, or recomposition pressure:

1. replace the delta-list copies with a bounded queue/ring or equivalent
   rolling metric;
2. keep telemetry storage out of Compose-observed state when the UI only needs
   the derived SPS value;
3. coalesce UI publication only where it preserves consensus, debounce,
   unknown-signature, and board-mutation behavior;
4. keep accepted board mutation on its current safe UI/controller boundary
   unless a separately designed state-ownership change proves necessary.

Preserve the existing controller tests for SPS window semantics, idle display,
consensus, debounce, and accepted-scan outcomes. Add focused tests for any new
rolling metric or publication policy.

Done means the measured hotspot is reduced without changing the field rule:
wrong card is worse than missed card.
