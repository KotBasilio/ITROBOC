# Plan: Further `Grid13VerdictDecoder` Optimizations

Date: 2026-07-01
Audience: Selyn / future maintainers
Area: `Grid13VerdictDecoder`

## Purpose

This note describes my current view of the next optimization steps for `Grid13VerdictDecoder`.

The goal is not "make code shorter."
The goal is:

- keep TD runtime decoding fast
- preserve safe `rawSignature` parity with the slow path
- avoid reintroducing forensic/debug work into the verdict path
- make performance changes measurable and reversible

## Current State

We already have:

- a named split between `Grid13SlowDecoder` and `Grid13VerdictDecoder`
- `TD::EditBoard` wired to the verdict path
- tests that protect the high-level verdict contract
- `Grid13SlowDecoder` retained as the alignment oracle

This is a strong base.

The fast path is now real, but still early.
It duplicates parts of the slow path logic in a leaner form, which is fine for now, but it means we should be deliberate about where parity and divergence are allowed.

## Status Snapshot (2026-07-02)

To avoid stale assumptions, here is the current optimization picture after the
latest TD / verdict work.

### Already Done

- `Grid13VerdictDecoder` is no longer just a debug-stripping wrapper.
  It is a real independent fast path.
- verdict path now uses compact bitwise Grid13 handling rather than string-only
  bit derivation
- strict invalid-run rejection is active in the verdict path
- slow-path naming split is done:
  - `Grid13SlowDecoder`
  - `Grid13SlowBarcodeMeasurement`
- TD live scanning uses the verdict path
- analyzer preprocessing no longer copies the whole luma plane before ROI work;
  it now extracts only the ROI bytes needed for decode
- TD has practical `scansPerSecond` field telemetry
- slow path remains available as the alignment oracle

### Partially Done / Still Crude

- projection still allocates a fresh `DoubleArray` per decode
- ROI extraction still copies ROI bytes into a fresh `ByteArray` per frame
- SPS telemetry still prunes and rebuilds a small recent-delta list on a timer
- verdict confidence is intentionally simple, not heavily tuned
- section-level benchmarking is still light / ad hoc rather than formal

### Not Done Yet

- reusable scratch buffers across frames
- fixed-size primitive run storage tuned specifically for verdict path
- deeper projection-loop optimization beyond the current straightforward scan
- more formal latency / percentile instrumentation
- native / C++ experiments

Interpret the rest of this note through that status lens.

## Optimization Priorities

My recommended priority order is:

1. Preserve correctness contract first.
2. Remove unnecessary allocations in the hot path.
3. Reuse already-proven primitives where they are cheap.
4. Instrument before making large structural bets.
5. Only then consider lower-level / native experiments.

## What Must Not Regress

Before chasing speed, these must stay true:

- a real accepted barcode should yield the same `rawSignature` as `Grid13SlowDecoder`
- verdict path should remain `debug = null`
- reject-obvious-garbage behavior should stay strict
- TD flow should prefer "not sure" over wrong-card mutation

That means optimization should be framed as:

- "same answer, cheaper path"

not:

- "new algorithm and hopefully similar behavior"

## Near-Term Opportunities

### 1. Centralize shared non-debug primitives, but allow verdict-only bitwise helpers

Right now the verdict path manually reimplements projection, thresholding, run extraction, active span, and confidence pieces.

That may be okay temporarily, but the healthy direction is:

- keep debug-building exclusive to the slow path
- share cheap pure primitives where parity matters
- avoid forcing the verdict path through string-shaped helpers when bitwise helpers are the better fit

Good candidates for sharing:

- `projectInkColumns(...)`
- `adaptiveInkThreshold(...)`
- `thresholdInkProjection(...)`
- `extractRuns(...)`
- `activeSpanFromBlackRuns(...)`

This reduces the risk that slow and verdict silently drift in bit derivation.

But this should not be read as "verdict must reuse every slow-path representation."

For example:

- `grid13SlowBitsFromProjection(...)`
- `hasInvalidGrid13RunCandidateSlow(...)`
- `checkGrid13SentinelsSlow(...)`
- `normalizeGrid13SentinelsSlow(...)`

I think it is healthy to allow verdict-dedicated helpers such as:

- `grid13FastBitsFromProjection(...)` returning a compact bit representation
- bitwise sentinel check / normalization helpers
- bitwise reverse / meal-signature conversion helpers
- bitwise invalid-run checks for `111` / `0000`-style gates

The key principle is:

- share primitives when the representation is still natural for both paths
- fork deliberately when the slow-path string representation becomes hot-path baggage

So the desired architecture is not:

- "everything shared"

It is:

- "shared where cheap and parity-safe, verdict-specific where performance meaningfully improves"

### 2. Prefer arrays / counters over transient collections

Current verdict code still builds some intermediate collections:

- `DoubleArray` projection
- `MutableList<IntRange>` for runs
- mapped width lists during confidence calculation

Possible improvements:

- keep projection, but avoid secondary mapped lists
- compute run-count / widest / endpoint widths in one pass
- avoid `map { ... }` in hot confidence logic
- consider storing only the minimal run facts needed by verdict:
  - first run start/end
  - last run start/end
  - widest run width
  - run count

This keeps the algorithm readable while reducing churn.

### 3. Keep the hot path branch-light

The verdict decoder should favor:

- linear scans
- local mutable counters
- no optional debug branching
- no string formatting except when a final signature is actually produced
- compact bitwise operations when they remove text-shaped overhead

The important design habit is:

- defer all non-essential decoration until after a candidate has survived the gates

### 4. Tighten parity tests on known images

The current verdict contract tests are good, but we should add more parity coverage using known-valid synthetic or fixture-driven images.

The most useful assertions are:

- same image -> same `Found` vs `Ambiguous` vs `NotFound` category where intended
- same accepted image -> same `rawSignature`
- verdict result -> `debug == null`

Avoid over-constraining:

- exact confidence equality
- exact internal run counts unless they are part of the public contract

### 5. Add simple benchmark-oriented instrumentation

Before making the decoder much more clever, I would add measurements that can be sampled while developing:

- decode attempts per second
- average decode time
- p95 decode time
- percentage of time spent in:
  - projection
  - run extraction
  - bit derivation
  - confidence / verdict assembly

This can start as simple timing around sections in a dev-only path.
We do not need a full benchmarking framework immediately.

## Medium-Term Opportunities

### 6. Revisit projection cost

Projection is likely the first unavoidable cost in the pipeline.

Questions worth testing:

- can projection work directly on existing gray bytes without extra conceptual conversion overhead?
- can row traversal be made friendlier for cache / tight loops?
- can we reuse buffers between frames in the analyzer path?

This is where real speed may live.

### 7. Reuse scratch buffers between frames

If profiling shows allocation pressure, a valuable next step is a scratch-work object for verdict decoding.

For example:

- reusable projection buffer
- reusable run buffer / small arrays
- reusable tiny counters object

This should only be done if measurements show allocation churn is relevant.
Do not pre-complicate the decoder if the real bottleneck is elsewhere.

### 8. Explore fixed-size run storage

Grid13 is structurally small.
Verdict likely does not need a general-purpose dynamic run list.

If needed later:

- use fixed-size arrays for a bounded number of candidate runs
- track count separately

That could reduce allocation and branch cost without making the code unreadable.

## What I Would Delay

### 9. Native / C++ rewrite

Possible eventually, but not the next move.

I would delay C++ until:

- Kotlin-side hot path is already lean and measured
- we have evidence that the remaining bottleneck is arithmetic / memory bandwidth
- we know the JNI / NDK complexity is worth it

Native code is a good final lever, not a first lever.

### 10. Threading changes inside verdict decoding

The analyzer already runs on a dedicated background executor.
I would not complicate verdict decoding with internal parallelism.

Grid13 decode per frame is small enough that:

- more threads likely add overhead and complexity
- determinism and debuggability are more valuable

## Suggested Work Sequence

If I were guiding the next steps, I would do them in this order:

1. Add/extend parity tests on known synthetic and fixture images.
2. Refactor verdict to share cheap primitives with slow path where it improves parity confidence.
3. Remove obvious allocation churn in verdict confidence/run handling.
4. Add simple section timing / counters for dev measurement.
5. Use those measurements to identify the true hottest part.
6. Only then choose between:
   - buffer reuse
   - tighter loops
   - fixed-size storage
   - native experiments

## Design Principle

My preferred principle for this decoder is:

- slow path explains
- verdict path decides

The verdict path should feel like a disciplined instrument:

- small
- predictable
- strict
- measurable

## Practical North Star

If future optimization work goes well, the ideal verdict path looks like:

- gray image
- projection
- threshold
- active span / runs
- 13-bit compact value
- strict run gate
- sentinel normalization
- `rawSignature`
- usable confidence
- done

No debug jewelry.
No reverse forensic story.
No allocations that are only needed for narration.
