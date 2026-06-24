package org.itroboc.vision

data class Grid13BarcodeMeasurement(
    val blackRunsPx: List<Int>,
    val whiteGapsPx: List<Int>,
    val blackRunEdges: List<IntRange>,
    val blackRunCentersPx: List<Double>,
    val activeStartX: Int,
    val activeEndX: Int,
    val activeSpanPx: Int,
    val rl2: String,
    val grid13FwdBits: String,
    val grid13RevBits: String,
    val grid13FwdHex: String,
    val grid13RevHex: String,
    val rawSignature: String,
    val reverseSignature: String,
    val confidence: Double,
    val warnings: List<String> = emptyList(),
)

fun measureGrid13Barcode(
    image: InkImage,
    threshold: Int? = null,
): Grid13BarcodeMeasurement? {
    val projection = projectInkColumns(image)
    return measureGrid13BarcodeProjection(
        projection = projection,
        threshold = threshold ?: adaptiveInkThreshold(projection),
    )
}

fun measureGrid13BarcodeProjection(
    projection: DoubleArray,
    threshold: Int = adaptiveInkThreshold(projection),
    minBlackRunWidth: Int = 2,
): Grid13BarcodeMeasurement? {
    require(minBlackRunWidth > 0) { "minBlackRunWidth must be positive" }
    if (projection.inkRange < 1.0) {
        return null
    }
    val blackMask = thresholdInkProjection(projection, threshold)
    val blackRuns = extractRuns(blackMask).filter { it.width >= minBlackRunWidth }
    val activeSpan = activeSpanFromBlackRuns(blackRuns) ?: return null
    val whiteGaps = internalWhiteGapsBetween(blackRuns)
    val fwdBits = grid13BitsFromProjection(
        projection = projection,
        activeSpan = activeSpan,
        threshold = threshold,
    )
    val revBits = reverseBits(fwdBits)

    return Grid13BarcodeMeasurement(
        blackRunsPx = blackRuns.map { it.width },
        whiteGapsPx = whiteGaps.map { it.width },
        blackRunEdges = blackRuns,
        blackRunCentersPx = blackRuns.map { (it.first + it.last) / 2.0 },
        activeStartX = activeSpan.first,
        activeEndX = activeSpan.last,
        activeSpanPx = activeSpan.width,
        rl2 = grid13RunLengthSignature(fwdBits),
        grid13FwdBits = fwdBits,
        grid13RevBits = revBits,
        grid13FwdHex = grid13BitsToHex(fwdBits),
        grid13RevHex = grid13BitsToHex(revBits),
        rawSignature = forwardMealSignature(fwdBits),
        reverseSignature = reverseMealSignature(revBits),
        confidence = confidenceForMeasurement(blackRuns = blackRuns, activeSpan = activeSpan),
        warnings = warningsForMeasurement(blackRuns),
    )
}

fun grid13BitsFromProjection(
    projection: DoubleArray,
    activeSpan: IntRange,
    threshold: Int,
): String {
    require(projection.isNotEmpty()) { "projection must not be empty" }
    require(threshold in 0..255) { "threshold must be within 0..255" }
    require(activeSpan.first >= 0) { "active span start must be non-negative" }
    require(activeSpan.last < projection.size) { "active span must fit projection" }

    val activeWidth = activeSpan.width
    return buildString(capacity = 13) {
        repeat(13) { cellIndex ->
            val cellStart = activeSpan.first + ((cellIndex * activeWidth) / 13)
            val cellEndExclusive = activeSpan.first + (((cellIndex + 1) * activeWidth) / 13)
            val safeEndExclusive = cellEndExclusive.coerceAtLeast(cellStart + 1)
            val meanInk = projection.sliceMean(cellStart until safeEndExclusive)
            append(if (meanInk >= threshold) '1' else '0')
        }
    }
}

private fun confidenceForMeasurement(
    blackRuns: List<IntRange>,
    activeSpan: IntRange,
): Double {
    val runCountScore = when (blackRuns.size) {
        in 5..6 -> 1.0
        4, 7 -> 0.75
        else -> 0.45
    }
    val spanScore = (1.0 - (kotlin.math.abs(activeSpan.width - 54) / 54.0)).coerceIn(0.0, 1.0)
    val endpointScore = if (blackRuns.endpointBarsLookNarrow()) 1.0 else 0.5
    return ((0.4 * runCountScore) + (0.3 * spanScore) + (0.3 * endpointScore)).coerceIn(0.0, 0.99)
}

private fun warningsForMeasurement(blackRuns: List<IntRange>): List<String> = buildList {
    if (!blackRuns.endpointBarsLookNarrow()) {
        add("Endpoint black bars are not clearly narrow")
    }
}

private fun List<IntRange>.endpointBarsLookNarrow(): Boolean {
    if (size < 2) {
        return false
    }
    val widths = map { it.width }
    val widest = widths.maxOrNull() ?: return false
    val firstWidth = widths.first()
    val lastWidth = widths.last()
    return firstWidth <= widest * 0.75 && lastWidth <= widest * 0.75
}

private val IntRange.width: Int
    get() = last - first + 1

private fun DoubleArray.sliceMean(indices: IntRange): Double {
    require(indices.first >= 0) { "indices start must be non-negative" }
    require(indices.last < size) { "indices must fit array" }
    var total = 0.0
    for (index in indices) {
        total += this[index]
    }
    return total / indices.count()
}

private val DoubleArray.inkRange: Double
    get() = (maxOrNull() ?: 0.0) - (minOrNull() ?: 0.0)
