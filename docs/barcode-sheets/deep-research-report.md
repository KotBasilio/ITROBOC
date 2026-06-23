# ITROBOC Barcode Strip Analysis Report

## Executive summary

I was able to access all of the files you expected me to check, plus the earlier report you uploaded for context. The five PNGs are readable and visually consistent with your descriptions: `Master.png` is a 52-code composite sheet arranged by suit and rank, while `spades.png`, `hearts.png`, `diamonds.png`, and `clubs.png` are suit-specific vertical sheets in rank order `A K Q J T 9 8 7 6 5 4 3 2`, each followed by the suit symbol at the bottom. `barcode_sheets_discussion.txt` is readable and contains the prior run-length / bitstring brainstorming. `deep-research-report.md` is also readable and is the earlier report produced before the images were available.

I analyzed the uploaded images directly in-session with quantitative measurements, not just visual reading. The strongest practical conclusion is that the visible marks behave like short 1D black/white run patterns, but the most stable raw signature for ITROBOC is **not** a raw “bar widths only” code. It is better to keep two layers: a human-debuggable alternating run-length signature and a machine-stable normalized fixed-cell signature.

Measured from the uploaded sheets, the visible structure is highly regular. Across all 52 cards, the barcode span from first black bar to last black bar is nearly constant at about **53.6 px on average**, ranging from **51 px to 57 px**. The number of black runs is strongly constrained: **29 cards have 5 black runs, 21 have 6, one has 7, and one has 4**. The first and last black bars are always visually narrow, with measured widths only **3–6 px** and **no wide endpoints** anywhere in the set. Black run widths show a clean bimodal pattern, mainly around **4–6 px** and **9–12 px**. White gaps also show a narrow/wide structure, mainly around **2–4 px** and **6–7 px**, with a few larger gaps around **10–11 px** that are best interpreted as multi-unit white spans rather than a separate primitive symbol class.

Your hypotheses therefore score as follows. The alternating black/white run model is strongly supported. The idea that the first and last black bars are special boundary or calibration bars is also strongly supported by the sheet measurements. The “thin black = `1`, wide black = `11`, thin white = `0`, wide white = `00`” idea is a good **operational decoding model**, but the data suggest it is not quite sufficient as the only raw signature representation, because a strict two-class run-length expansion produces **one collision** in this deck. A normalized fixed-cell signature resolves that.

The most useful outcome for ITROBOC is this:

```text
Primary raw signature:  grid13Fwd / grid13Rev
Secondary debug signature: RL2 alternating run lengths
Semantic meaning: Deck Profile only
```

A custom playing-card machine-readable code is also consistent with the older Jannersten patent family, which describes a six-position binary card code with one or two reference bars and explicitly permits adjacent bars to merge into a broader visible bar. That matters because it supports treating the visible marks as a rendered appearance of a deeper discrete code, rather than as a retail barcode symbology. citeturn3view0

## File access and measurement method

The files I could access are listed below.

| File | Status | What is visible |
|---|---|---|
| `Master.png` | Accessible | Composite sheet with 52 barcode crops arranged in 4 suit columns and 13 rank rows; ranks appear on the right, suit pips at the bottom. |
| `spades.png` | Accessible | 13 spade barcode crops in `A K Q J T 9 8 7 6 5 4 3 2` order, with a spade symbol at the bottom. |
| `hearts.png` | Accessible | 13 heart barcode crops in `A K Q J T 9 8 7 6 5 4 3 2` order, with a heart symbol at the bottom. |
| `diamonds.png` | Accessible | 13 diamond barcode crops in `A K Q J T 9 8 7 6 5 4 3 2` order, with a diamond symbol at the bottom. |
| `clubs.png` | Accessible | 13 club barcode crops in `A K Q J T 9 8 7 6 5 4 3 2` order, with a club symbol at the bottom. |
| `barcode_sheets_discussion.txt` | Accessible | Prior discussion about alternating run lengths, thin/wide quantization, forward/reverse handling, and 52-card distinctness testing. |
| `deep-research-report.md` | Accessible | Earlier internet-grounded report that explicitly lacked access to the image evidence. |

For the image analysis itself, I used a simple but appropriate pipeline for clean sheet images. I segmented card rows from each suit sheet by vertical darkness projection, padded each row slightly so the bars were fully inside the crop, located the barcode region on the left side of each row, and extracted black runs from connected dark vertical components. From those components I measured:

- the number of black runs,
- each black run width in pixels,
- each internal white gap width in pixels,
- the total active span from first bar to last bar,
- a per-card two-class run-length signature,
- and a normalized equal-span fixed-cell signature.

I generated two candidate raw signatures for each card.

The first is **RL2**, a practical alternating run-length signature where black widths and white gaps are each quantized into narrow/wide classes on a per-card basis, with the first and last black runs constrained to the narrow class.

The second is **Grid13**, a normalized 13-cell occupancy signature produced by resampling the active barcode span from the first black edge to the last black edge into 13 equal cells and thresholding cell darkness. This is not a claim that the printer’s original internal code is “really 13 bits.” It is simply the smallest practical normalized signature I tested on your sheets that gave a clean **52/52 unique forward signatures** result.

Public patent context is consistent with using a custom fixed-position card code rather than assuming a standard barcode. The 2003 Jannersten patent describes a playing-card code with “at least” a six-position binary code, one or more reference bars, and explicitly notes that adjacent bars may merge into one broader visible bar. That makes a two-layer interpretation very plausible: the camera sees runs, while the decoder should prefer a normalized fixed-cell identity. citeturn3view0

## Inferred model

### What the uploaded sheets directly show

The visible code family is short, regular, and low-entropy in shape but still rich enough to identify the deck.

The black-run count distribution is tightly bounded:

| Black runs | Cards |
|---|---:|
| 4 | 1 |
| 5 | 29 |
| 6 | 21 |
| 7 | 1 |

That matches the visual impression from the sheets: most cards really do have 5 or 6 visible black runs.

The endpoint behavior is especially strong evidence for fixed references or sentinels. The first bar widths across the 52 cards are distributed as `3 px: 1`, `4 px: 20`, `5 px: 28`, `6 px: 3`. The last bar widths are `3 px: 1`, `4 px: 11`, `5 px: 34`, `6 px: 6`. There are **no** endpoint bars in the wide 9–12 px class. In plain terms: the leftmost and rightmost visible black bars are always narrow.

The interior black bars are bimodal. Globally, black widths occurred at `{3,4,5,6,7,9,10,11,12}` pixels, but the mass is concentrated at `4–6` and `9–12`. There is a conspicuous gap around `8`, which is exactly what you would expect if the visible mark is dominated by “thin” and “wide” bars.

The white gaps behave similarly, though a bit less cleanly. They occurred at `{1,2,3,4,5,6,7,8,10,11}` pixels, with most counts at `2–3` and `6–7`, plus a small number of larger `10–11` gaps. That suggests two practical unit classes are real, but a few gaps behave like **multi-unit white spans**, not just “wide gap = exactly 2 units.”

### What that implies for your hypotheses

Hypotheses about alternating black and white measurement are strongly supported. A decoder that looks only at black bars would be throwing away meaningful structure.

Hypotheses about thin/wide width quantization are also supported, but with an important caveat. A strict `1 / 2` model for both black and white runs works very well as a **first-pass decoder**, but it is not perfectly collision-free on this 52-card dataset. That means ITROBOC should not store only that representation.

Your “starts and ends with thin black bars” hypothesis is strongly supported by the measured endpoint widths. Your “`10...01`” hypothesis is visually plausible for many cards, but once the barcode is normalized into fixed cells, one should not hard-code that textual form too early. The sentinels are real enough to use operationally, but the stable raw signature should still be stored separately from that interpretation.

### Distinctness, collisions, and orientation

I tested the deck with two signature families.

The first test was the practical two-class run-length model, RL2. Under this model, each card is converted into an alternating signature such as:

```text
B1-W1-B1-W1-B2-W1-B2-W2-B2-W2-B1
```

and then expanded to bits if desired.

That model performed very well, but not perfectly. It produced **51 distinct signatures out of 52**, with one collision:

```text
D2 == C8  under RL2
RL2 = B1-W2-B1-W1-B2-W2-B2-W1-B1
```

So the short answer to your “does the binary run-length model distinguish all 52 cards?” question is:

**A strict thin/wide run-length model almost does, but not quite.**

The second test was the normalized **Grid13** signature. Under this model, each active barcode span is normalized to 13 equal cells and binarized. That produced **52 distinct forward signatures out of 52** on these sheet images.

Orientation is definitely not something you should discard casually. If I canonicalize by taking `min(forward, reverse)`, uniqueness drops from **52** to **36**. Example reverse-canonical collisions include pairs such as `SA` and `DT`, `SQ` and `HT`, and `S7` and `C3`. That means the raw signature layer should preserve orientation, or at minimum preserve both forward and reverse forms and let the Deck Profile decide which one is semantically valid.

### Suit and rank look jointly encoded, not cleanly separated

I do not see a clean “suit bits on one side, rank bits on the other side” decomposition in the uploaded sheets.

There **are** suit-family regularities. In the 13-cell candidate, cards of the same suit are somewhat more similar to each other than random pairs are. But “same rank across suits” is **not** especially similar. Using simple Hamming distance on the 13-cell signatures, same-suit pairs average about **3.86** differing cells, all pairs average about **4.62**, and same-rank cross-suit pairs average about **4.94**. In plain terms, suit family structure is more visible than rank-family structure, but the total code does not decompose into an obvious independent rank template plus a little suit suffix.

For ITROBOC, the safe conclusion is:

**Treat the raw code as a jointly assigned per-card identifier.**
If later analysis reveals a suit/rank factorization for a specific deck family, that can live in the Deck Profile layer.

### Recommended raw signature format

The best format for the decoder is a two-layer raw record.

The primary signature should be the normalized fixed-cell form:

```text
grid13Fwd:1010101001001
grid13Rev:1001001010101
```

The secondary debug form should preserve the local alternating run structure:

```text
rl2:B1-W1-B1-W1-B2-W1-B2-W2-B2-W2-B1
```

For debugging and future model migration, I would also store the measured pixel evidence:

```text
blackRunsPx:[3,4,5,5,5,3]
whiteGapsPx:[4,4,3,7,8]
activeSpanPx:53
```

That gives you a stable machine key, a human-readable explanation of why the key was produced, and enough evidence to reprocess the sample later if you change the quantizer.

## Table of the 52 cards and proposed signatures

The table below uses these columns:

- **B#** = number of visible black runs
- **B(px)** = measured black run widths in pixels
- **W(px)** = measured white gap widths in pixels
- **RL2** = inferred alternating run-length signature using thin/wide quantization
- **Grid13** = proposed normalized fixed-cell raw signature
- **Conf** = practical confidence in the measurement on these sheet images

### Spades

| Card | B# | B(px) | W(px) | RL2 | Grid13 | Conf |
|---|---:|---|---|---|---|---|
| SA | 6 | 3,4,5,5,5,3 | 4,4,3,7,8 | B1-W1-B1-W1-B2-W1-B2-W2-B2-W2-B1 | 1010101001001 | High |
| SK | 6 | 4,5,6,5,6,4 | 3,7,3,3,7 | B1-W1-B2-W2-B2-W1-B2-W1-B2-W2-B1 | 1010010101001 | High |
| SQ | 6 | 4,5,9,5,5,4 | 3,4,3,4,7 | B1-W1-B1-W1-B2-W1-B1-W1-B1-W2-B1 | 1010110101001 | High |
| SJ | 5 | 4,10,5,10,5 | 7,3,3,7 | B1-W2-B2-W1-B1-W1-B2-W2-B1 | 1001101011001 | High |
| ST | 5 | 5,10,6,10,5 | 3,6,3,7 | B1-W1-B2-W2-B1-W1-B2-W2-B1 | 0011001011001 | Medium |
| S9 | 6 | 5,6,6,6,6,5 | 6,3,2,3,7 | B1-W2-B2-W1-B2-W1-B2-W1-B2-W2-B1 | 1001010101001 | High |
| S8 | 6 | 5,6,6,6,6,5 | 3,2,7,3,6 | B1-W1-B2-W1-B2-W2-B2-W1-B2-W2-B1 | 1010100101001 | High |
| S7 | 5 | 5,10,6,6,6 | 7,7,3,6 | B1-W2-B2-W2-B1-W1-B1-W2-B1 | 1001100101001 | High |
| S6 | 5 | 5,10,11,7,5 | 2,7,2,6 | B1-W1-B2-W2-B2-W1-B1-W2-B1 | 1011001101001 | High |
| S5 | 5 | 5,6,10,6,5 | 7,7,3,7 | B1-W2-B1-W2-B2-W1-B1-W2-B1 | 1001001101001 | High |
| S4 | 5 | 5,6,6,11,5 | 7,7,2,7 | B1-W2-B1-W2-B1-W1-B2-W2-B1 | 1001001011001 | High |
| S3 | 5 | 5,5,11,6,5 | 3,7,7,7 | B1-W1-B1-W2-B2-W2-B1-W2-B1 | 1010011001001 | High |
| S2 | 5 | 5,6,12,7,6 | 7,2,6,6 | B1-W2-B1-W1-B2-W2-B1-W2-B1 | 1001011001001 | High |

### Hearts

| Card | B# | B(px) | W(px) | RL2 | Grid13 | Conf |
|---|---:|---|---|---|---|---|
| HA | 5 | 4,5,6,5,4 | 7,7,7,7 | B1-W1-B2-W1-B2-W1-B2-W1-B1 | 1001001001001 | High |
| HK | 6 | 5,6,10,10,6,5 | 2,3,2,2,3 | B1-W1-B1-W2-B2-W1-B2-W1-B1-W2-B1 | 1010110110101 | High |
| HQ | 6 | 4,6,6,10,6,5 | 3,2,7,2,2 | B1-W1-B1-W1-B1-W2-B2-W1-B1-W1-B1 | 1010100110101 | High |
| HJ | 6 | 4,5,6,9,6,5 | 3,7,3,3,2 | B1-W1-B1-W2-B1-W1-B2-W1-B1-W1-B1 | 1010010110101 | High |
| HT | 6 | 5,6,6,10,6,5 | 6,3,2,3,2 | B1-W2-B1-W1-B1-W1-B2-W1-B1-W1-B1 | 1001010110101 | High |
| H9 | 5 | 4,10,10,6,5 | 7,7,2,3 | B1-W2-B2-W2-B2-W1-B1-W1-B1 | 1001100110101 | High |
| H8 | 5 | 5,11,11,6,5 | 2,10,2,2 | B1-W1-B2-W2-B2-W1-B1-W1-B1 | 1011000110101 | High |
| H7 | 6 | 5,6,11,6,7,5 | 3,2,6,2,2 | B1-W1-B1-W1-B2-W2-B1-W1-B1-W1-B1 | 1010110010101 | High |
| H6 | 5 | 5,10,6,6,6 | 11,7,2,2 | B1-W2-B2-W2-B1-W1-B1-W1-B1 | 1000110010101 | High |
| H5 | 6 | 5,6,7,7,6,5 | 2,6,6,2,2 | B1-W1-B2-W2-B2-W2-B2-W1-B2-W1-B1 | 1010010010101 | High |
| H4 | 6 | 6,6,6,7,6,6 | 6,3,6,2,2 | B1-W2-B1-W1-B1-W2-B2-W1-B1-W1-B1 | 1001010010101 | High |
| H3 | 5 | 6,6,11,7,6 | 6,11,1,2 | B1-W2-B1-W2-B2-W1-B1-W1-B1 | 1001000110101 | High |
| H2 | 5 | 5,12,11,7,5 | 10,1,2,2 | B1-W2-B2-W1-B2-W1-B1-W1-B1 | 1000110110101 | High |

### Diamonds

| Card | B# | B(px) | W(px) | RL2 | Grid13 | Conf |
|---|---:|---|---|---|---|---|
| DA | 5 | 4,9,5,5,5 | 3,7,7,7 | B1-W1-B2-W2-B1-W2-B1-W2-B1 | 1011001001001 | High |
| DK | 7 | 5,6,6,6,5,5,4 | 2,2,2,3,3,3 | B1-W1-B2-W1-B2-W1-B2-W2-B1-W2-B1-W2-B1 | 1010101010101 | High |
| DQ | 6 | 4,9,6,6,5,4 | 7,3,2,3,3 | B1-W2-B2-W1-B1-W1-B1-W1-B1-W1-B1 | 1001101010101 | High |
| DJ | 6 | 4,10,5,5,5,5 | 3,7,3,3,3 | B1-W1-B2-W2-B1-W1-B1-W1-B1-W1-B1 | 1011001010101 | High |
| DT | 6 | 5,6,6,6,5,5 | 6,7,2,3,3 | B1-W2-B2-W2-B2-W1-B2-W1-B1-W1-B1 | 1001001010101 | High |
| D9 | 6 | 5,5,9,6,5,5 | 3,3,3,7,3 | B1-W1-B1-W1-B2-W1-B1-W2-B1-W1-B1 | 1010110100101 | High |
| D8 | 5 | 4,10,5,6,5 | 11,3,7,2 | B1-W2-B2-W1-B1-W2-B1-W1-B1 | 1000110100101 | High |
| D7 | 6 | 4,6,5,6,6,4 | 3,7,3,6,3 | B1-W1-B2-W2-B1-W1-B2-W2-B2-W1-B1 | 1010010100101 | High |
| D6 | 6 | 4,6,6,5,6,4 | 7,2,3,7,3 | B1-W2-B2-W1-B2-W1-B1-W2-B2-W1-B1 | 1001010100101 | High |
| D5 | 6 | 4,5,6,9,6,4 | 3,3,3,7,3 | B1-W1-B1-W1-B1-W1-B2-W2-B1-W1-B1 | 1010101100101 | High |
| D4 | 5 | 5,6,10,6,5 | 7,6,7,2 | B1-W2-B1-W2-B2-W2-B1-W1-B1 | 1001001100101 | High |
| D3 | 6 | 5,6,6,6,11,5 | 2,2,3,6,2 | B1-W1-B1-W1-B1-W1-B1-W2-B2-W1-B1 | 1010101001101 | High |
| D2 | 5 | 5,6,10,10,5 | 6,3,7,2 | B1-W2-B1-W1-B2-W2-B2-W1-B1 | 1001001001101 | Medium |

### Clubs

| Card | B# | B(px) | W(px) | RL2 | Grid13 | Conf |
|---|---:|---|---|---|---|---|
| CA | 5 | 4,10,6,6,5 | 6,2,6,6 | B1-W2-B2-W1-B1-W2-B1-W2-B1 | 1001101001001 | High |
| CK | 5 | 4,9,9,6,4 | 7,3,7,2 | B1-W2-B2-W1-B2-W2-B1-W1-B1 | 1001101100101 | High |
| CQ | 5 | 4,5,9,10,5 | 3,3,7,6 | B1-W1-B1-W1-B2-W2-B2-W2-B1 | 1010110011001 | High |
| CJ | 5 | 5,6,10,10,5 | 2,2,10,2 | B1-W1-B1-W1-B2-W2-B2-W1-B1 | 1010110001101 | High |
| CT | 5 | 4,10,10,6,4 | 3,6,6,3 | B1-W1-B2-W2-B2-W2-B1-W1-B1 | 1011001100101 | High |
| C9 | 5 | 4,6,10,10,5 | 3,6,6,2 | B1-W1-B1-W2-B2-W2-B2-W1-B1 | 1010011001101 | High |
| C8 | 5 | 6,6,10,9,5 | 6,2,7,3 | B1-W2-B1-W1-B2-W2-B2-W1-B1 | 0001011001101 | Medium |
| C7 | 5 | 4,10,6,10,4 | 7,2,6,3 | B1-W2-B2-W1-B1-W2-B2-W1-B1 | 1001101001101 | High |
| C6 | 5 | 5,10,6,10,5 | 2,6,7,2 | B1-W1-B2-W2-B1-W2-B2-W1-B1 | 1011001001101 | High |
| C5 | 4 | 5,12,10,5 | 10,5,6 | B1-W2-B2-W1-B2-W1-B1 | 1000110011001 | High |
| C4 | 6 | 5,6,6,6,10,6 | 2,2,2,2,6 | B1-W1-B1-W1-B1-W1-B1-W1-B2-W2-B1 | 1010101011001 | High |
| C3 | 5 | 5,6,6,10,5 | 7,2,6,6 | B1-W2-B1-W1-B1-W2-B2-W2-B1 | 1001010011001 | High |
| C2 | 5 | 5,6,7,10,5 | 2,6,6,6 | B1-W1-B1-W2-B1-W2-B2-W2-B1 | 1010010011001 | High |

## Algorithm recommendations

### Recommended decoder strategy

The decoder should output **two raw representations** from the same crop.

The first output should be the production raw signature:

```text
grid13Fwd
grid13Rev
```

The second should be the explanation layer:

```text
blackRunsPx
whiteGapsPx
rl2
```

That gives you the best of both worlds. `Grid13` is collision-free on this sheet set and stable for deck profiling. `RL2` is easier to reason about when something goes wrong in the camera pipeline.

### Crop preprocessing

Do not start with OCR logic. Start with an ink-density pipeline.

For color handling, I recommend a darkness channel based on:

```text
ink = 255 - min(R, G, B)
```

rather than plain luminance only. That makes black and dark-red printing behave more similarly, which matters because the hearts and diamonds strips are visibly reddish while the spades and clubs are darker gray/black.

Normalize the crop lightly for exposure, then find the narrow barcode band. If the app already has a card crop, search along the expected edge region for a high-vertical-structure strip. Once found, rotate so the bars are vertical and collapse the ROI to a 1D signal by taking the median darkness across rows.

### Black/white extraction

From the 1D signal, threshold to find black runs and white gaps between the first and last black runs. In production I would decode several neighboring scanlines or mini-bands and require them to agree.

Two endpoint checks should be explicit:

- The first black run should be narrow.
- The last black run should be narrow.

If either endpoint becomes wide, confidence should fall sharply.

### Unit-width estimation

For RL2, estimate the black narrow unit from the endpoints plus the smallest internal black runs. Estimate the white narrow unit from the smaller gap cluster. Do not force exact pixel widths.

A practical quantizer is:

- Black run class = narrow or wide
- White gap class = narrow, wide, or multi-wide when needed

Even if you expose only `B1/B2` and `W1/W2` in the exported debug signature, keep an internal ambiguity flag when a measured width sits near the threshold.

### Fixed-cell normalization

After black-run extraction, also compute the active span from first black edge to last black edge and resample it to 13 equal cells. Threshold cell means to produce:

```text
grid13Fwd
grid13Rev
```

For this sheet set, that forward representation was the first simple normalized form I tested that gave 52 out of 52 distinct signatures.

### Ambiguity handling

A crop should not silently produce a confident but fragile answer. Each decode should include:

- the best `grid13Fwd`,
- the reverse form,
- the `RL2` debug form,
- the measured span,
- the number of agreeing scanlines,
- and a confidence score.

If multiple adjacent scanlines disagree, or if the RL2 model collides with another profile entry, return an ambiguity state rather than inventing certainty.

### Confidence calculation

I recommend a confidence model with these components:

```text
contrastScore
endpointScore
runQuantizationMargin
scanlineAgreement
grid13Stability
deckProfileUniqueness
```

The confidence should be capped if either of these is true:

- endpoint bars are not clearly narrow,
- or the result only exists after aggressive threshold tuning.

### Recommended debug fields

The Android app should log enough data that a bad decode becomes inspectable.

I would log at least:

```text
cropWidthPx
cropHeightPx
roiBox
roiAngleDeg
inkChannel
thresholdMode
activeStartX
activeEndX
activeSpanPx
blackRunsPx
whiteGapsPx
rl2
grid13Fwd
grid13Rev
scanlineAgreement
confidence
ambiguous
deckProfileMatchCount
```

If possible in debug builds, also save a tiny 1D signal snapshot and a visualization of inferred cell boundaries.

## Risks and uncertainties

The cleanest risk statement is this: **RL2 is excellent, but not sufficient by itself**. On your sheet set it has one observed collision, `D2` versus `C8`. If ITROBOC stored only the thin/wide alternating run-length form, those two cards would be indistinguishable under this quantizer.

The second risk is over-canonicalizing orientation. On these sheets, forward `Grid13` is collision-free, but reverse-canonicalization drops uniqueness from 52 to 36. So if the app may see both orientations, the raw signature layer must keep both, or else the Deck Profile must store both aliases explicitly.

The third risk is mistaking the visible rendering for the deepest internal code. The older Jannersten patent family explicitly describes a fixed-position binary card code with reference bars and merged visible bars, so the run-length appearance can easily be a rendering layer rather than the true design primitive. That is one more reason to separate the production signature from the human-readable run explanation. citeturn3view0

The fourth risk is assuming suit and rank are independently encoded. The sheet data do not show a clean decomposition. Suit-family regularities exist, but the overall pattern looks jointly assigned enough that the decoder should treat the raw code as a per-card ID until a specific Deck Profile proves otherwise.

Finally, the 13-cell normalization should be understood correctly. I am **not** claiming that the deck manufacturer’s official symbology is “13-bit.” I am saying that, on your uploaded sheet images, a 13-cell normalized occupancy signature is the best simple practical raw signature I found for distinctness.

## Concrete implementation recommendations for a Kotlin vision module

The `:vision` module should be pure Kotlin/JVM and organized around immutable intermediate objects. A good structure would be:

```kotlin
data class BarcodeMeasurement(
    val blackRunsPx: IntArray,
    val whiteGapsPx: IntArray,
    val activeSpanPx: Int,
    val rl2: String,
    val grid13Fwd: String,
    val grid13Rev: String,
    val confidence: Double,
    val ambiguous: Boolean
)
```

I would then split the pipeline into small pure functions:

```kotlin
fun toInk(grayOrRgb: ImageBuffer): GrayBuffer
fun findBarcodeBand(cardCrop: GrayBuffer): RotatedRect
fun rectifyBand(ink: GrayBuffer, band: RotatedRect): GrayBuffer
fun projectTo1D(band: GrayBuffer): DoubleArray
fun extractRuns(signal: DoubleArray): RunMeasurement
fun quantizeRl2(runs: RunMeasurement): String
fun normalizeGrid13(signal: DoubleArray, activeRange: IntRange): Pair<String, String>
fun scoreDecode(measurement: BarcodeMeasurement): Double
```

The most valuable test fixtures are the files you already uploaded. I would place them under something like:

```text
vision/src/test/resources/barcode-sheets/
  Master.png
  spades.png
  hearts.png
  diamonds.png
  clubs.png
```

and then add a generated golden manifest, for example:

```json
{
  "SA": { "grid13Fwd": "1010101001001", "rl2": "B1-W1-B1-W1-B2-W1-B2-W2-B2-W2-B1" },
  "SK": { "grid13Fwd": "1010010101001", "rl2": "B1-W1-B2-W2-B2-W1-B2-W1-B2-W2-B1" }
}
```

The unit-test suite should include these categories.

A row-segmentation test should confirm that each suit sheet yields exactly 13 barcode rows and that the bottom suit symbol is not mistaken for a barcode.

A measurement test should confirm the black-run counts, active spans, and endpoint thinness for all 52 cards.

A uniqueness test should assert that `grid13Fwd` has 52 unique values on the golden set.

A collision test should assert that RL2 has the known `D2` / `C8` collision unless you intentionally revise the quantizer.

An orientation test should check `grid13Fwd` and `grid13Rev` explicitly, and verify that canonicalizing by reversal is **not** allowed in the generic raw-signature path.

A degradation suite should introduce blur, exposure shifts, JPEG artifacts, light perspective skew, and partial crop shifts, and then measure whether `grid13Fwd` remains stable.

A channel test should compare luminance, min-RGB darkness, and possibly value-channel darkness on black and red suits, since the red suits are visually weaker in plain grayscale.

For Codex-oriented implementation work, I would recommend this sequence.

First, implement the sheet analyzer and golden manifest generator.

Second, lock down the `Grid13` and `RL2` extraction on the clean static resources.

Third, add degraded synthetic test cases.

Fourth, wire in the camera-facing ROI finder.

That order keeps the hard inferential work in a deterministic JVM test harness, which is exactly where it belongs.

The most practical final recommendation for ITROBOC is therefore:

- use **`grid13Fwd/grid13Rev`** as the production raw signature,
- keep **`RL2`** as the explanatory and debugging signature,
- keep semantics in the **Deck Profile** only,
- and preserve orientation explicitly rather than canonicalizing it away.