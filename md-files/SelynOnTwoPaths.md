Yes — I like the direction. The split into **Verdict** and **Explanation** is architecturally right.

But I’d mark one important seam very clearly:

**This first step simulates the Verdict path, but does not yet optimize it.**

If `Grid13VerdictDecoder` initially delegates to `Grid13BarcodeDecoder` and only returns `debug = null`, then the expensive debug work may already have happened. So FPS may not improve much yet. It is still useful as an API separation step, but not as the real performance win.

So I’d read the plan as two phases:

```text
Phase 1: separate call sites / prove screens can request different decoder modes
Phase 2: actually implement fast measurement that avoids debug allocations/work
```

That is good. Just don’t expect Phase 1 to deliver the speed gold.

On naming: `Grid13VerdictDecoder` is acceptable and clear. I slightly prefer `Grid13FastDecoder` or `Grid13RuntimeDecoder`, but `Verdict` has a nice meaning: it returns the operational answer, not the courtroom transcript. So I’m fine with it.

On switch placement: putting the switch at `AdminEditCameraFrameDecoder` works technically, but the name now smells a bit stale because EditBoard also uses it. Not urgent, but later I’d rename it to something neutral like:

```text
CameraFrameBarcodeDecoder
Grid13CameraFrameDecoder
BarcodeFrameDecoder
```

Current plan is okay; future cleanup.

The biggest thing I’d ask team to preserve is this contract:

```text
Admin::Edit = Explanation path
TD::EditBoard = Verdict path
```

Admin calibration needs logs, barcode inspection cards, full debug, soft-pink warning previews, all the little forensic beetle footprints.

TD board scanning needs speed and a safe raw signature. It doesn’t need centers, gaps, RL2, reverse debug, or rich explanation per frame.

For tests, I’d slightly adjust team’s plan. Instead of requiring confidence to match closely forever, I’d test:

```text
same image -> same result type
same image -> same rawSignature when Found
same image -> Verdict debug == null
same image -> Full debug != null
same image -> confidence is usable / same band, not necessarily identical forever
```

Because once the fast path becomes truly separate, its confidence may be simpler. We should not over-constrain it if raw signature correctness is stable.

One more important check: EditBoard’s `viewedAs(mode)` must still work with `debug = null`. Since your orientation model uses the visible payload and switches only the prefix, that should be fine:

```text
raw bfm15A9 + BRM mode -> brm15A9
```

So Verdict does **not** need `debug.reverseSignature`. Good.

My verdict on team’s plan:

**Green as a seam-splitting step. Yellow if anyone expects immediate FPS improvement.**

The real optimization starts when `Grid13VerdictDecoder` stops delegating to Full and uses a lean primitive path:

```text
projection -> active span -> 13 bits -> strict run gate -> sentinel sandwich -> rawSignature
```

No debug jewelry. No lists unless needed. No narrator in the hot path.

So yes: good plan, with one brass label on it:

> Phase 1 creates the two doors. Phase 2 makes the fast door actually fast. 🪲⚡
