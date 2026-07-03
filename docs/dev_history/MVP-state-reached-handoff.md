## MVP State Reached: Handoff for New Chat Instance

Date: 2026-07-02
Audience: new Codex / ChatGPT instance entering post-MVP work
Project: ITROBOC

### What Exists Now

ITROBOC has crossed into real MVP territory.

Core field proof:
- the scanner was taken to a live bridge tournament on Wednesday, 2026-07-01
- it successfully processed about two-thirds of the tournament hands
- the system produced usable PBN output from real club cards in real conditions

This matters more than internal milestone language.
The product has passed first contact with reality.

### Current Product Shape

Major product areas now present:
- `Admin::Edit`
  - profile calibration / mapping workflow
  - explanation-rich slow decoder path
  - debug log sharing
- `TD::EditBoard`
  - live scanning via `Grid13VerdictDecoder`
  - board editing and seat assignment flow
  - board completion behavior
  - PBN preview on complete boards
  - TD export / cumulative import
- Core bridge domain
  - board / hand / card models
  - reducer-style board editing logic
  - PBN export
  - duplicate-board metadata (`Dealer`, `Vulnerable`)
- Vision
  - `Grid13SlowDecoder` retained as explanation / oracle path
  - `Grid13VerdictDecoder` used as TD runtime path
  - golden-manifest-backed tests

### Important Proven Invariants

These were not guessed; they were grounded during the recent cycle:

- physical orientation -> token family mapping matters
- for TD runtime, "wrong card mutation" is worse than "not sure yet"
- strict run gate is intentional:
  - reject black run length >= 3
  - reject white run length >= 4
- `Grid13VerdictDecoder` should remain debug-free
- accepted fast-path signatures should preserve `rawSignature` parity with `Grid13SlowDecoder`
- duplicate-board export metadata should follow board number:
  - dealer cycle
  - vulnerability cycle

Durable formulation from the barcode/model cycle:

> Physical card -> orientation -> bits -> token -> run form.

### Recent Technical Wins

Recent high-value changes before this handoff:

- split runtime decoding into:
  - `Grid13SlowDecoder`
  - `Grid13VerdictDecoder`
- moved TD live scanning onto the verdict path
- replaced string-heavy bit work in the verdict path with compact bitwise handling
- added early invalid-run rejection in the runtime decoder
- switched analyzer preprocessing from full-luma copy toward ROI-only luma extraction
- improved `scansPerSecond` into useful field telemetry
- added TD board export / cumulative import
- made export DDS-friendly by including `Vulnerable`
- unified TD preview and TD export PBN generation

### Collaboration / Workflow Context

This repo uses a multi-lane workflow:
- Archy = product owner / continuity holder
- Bob / Codex = repo-local implement/review lane
- Selyn = design-table / architectural brief shaping
- Foxy = Android Studio / IDE-shaped help

Active collaboration preference:
- use MV-M / team mode unless Archy asks for MV-off
- preserve voice distinction when useful
- keep patches small and reviewable
- commit and push at end of each ticket

The relational / field layer is documented in:
- [md-files/field.md](/home/miron/proj/ITROBOC/md-files/field.md)

### What Post-MVP Likely Means

The project is no longer in pure "can this become real?" mode.
The likely next phase is:

- hardening field behavior
- improving scanner speed and reliability
- reducing wrong-card risk
- smoothing tournament-director workflow
- choosing which experiments become durable product features

So post-MVP work should likely prioritize:
- field-driven quality
- operational clarity
- performance where it changes user experience
- preserving proven invariants while cleaning internals

### Open Optimization Picture

Important: some optimizations are already done.
Do not restart from stale assumptions that the verdict decoder is still a wrapper or that the analyzer still copies the whole frame.

Current state:
- verdict fast path exists and is real
- ROI-only luma extraction exists and is real
- slow path remains available as oracle

Still-open likely optimization areas:
- reuse scratch buffers instead of allocating per frame
- tighten projection / threshold loops
- reduce tiny temporary collections in SPS logic if it becomes relevant
- add more benchmark-grade instrumentation if needed
- possibly explore native / C++ only after Kotlin-side measurements flatten out

### Suggested Re-entry Questions for New Instance

If starting post-MVP cleanly, good opening questions are:

1. What field failure modes matter most now:
   - wrong card
   - missed card
   - low SPS
   - UI friction
2. Which optimizations already shipped, and which are still only planned?
3. What should remain strict even if it costs some reads?
4. What are the next product bets after tournament-first viability?

### Useful Files

- [docs/product_context.md](/home/miron/proj/ITROBOC/docs/product_context.md)
- [md-files/field.md](/home/miron/proj/ITROBOC/md-files/field.md)
- [docs/dev_history/td-edit/PLAN_2026-07-01_verdict_decoder_optimization.md](/home/miron/proj/ITROBOC/docs/dev_history/td-edit/PLAN_2026-07-01_verdict_decoder_optimization.md)
- [docs/dev_history/td-edit/HANDOFF_2026-07-01_verdict_decoder_and_sps.md](/home/miron/proj/ITROBOC/docs/dev_history/td-edit/HANDOFF_2026-07-01_verdict_decoder_and_sps.md)

### Short Re-entry Summary

ITROBOC is a real MVP now.
It has already survived first field use.
The next chat instance should treat this as post-MVP shaping, not greenfield invention.
