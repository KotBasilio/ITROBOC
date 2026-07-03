package org.itroboc.vision

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * An optimized decoder for the "Verdict" path.
 *
 * Phase 2: Fast Path implementation. This decoder calculates the minimum necessary
 * information to produce a barcode signature and confidence score, skipping all
 * forensic/debug allocations.
 */
class Grid13VerdictDecoder(
    private val minimumFoundConfidence: Double = 0.60,
) : BarcodeDecoder {
    private var projectionScratch = DoubleArray(0)

    init {
        require(minimumFoundConfidence in 0.0..1.0) {
            "minimumFoundConfidence must be within [0, 1]"
        }
    }

    override fun decode(image: GrayImage): BarcodeDecodeResult {
        projectionScratch = ensureProjectionCapacity(projectionScratch, image.width)
        val projection = projectionScratch

        for (x in 0 until image.width) {
            var total = 0
            for (y in 0 until image.height) {
                total += 255 - (image.pixels[(y * image.width) + x].toInt() and 0xFF)
            }
            projection[x] = total.toDouble() / image.height
        }

        if (image.width == 0) {
            return BarcodeDecodeResult.NotFound("Empty image projection")
        }

        if (projection.inkRange(image.width) < 1.0) {
            return BarcodeDecodeResult.NotFound("Insufficient ink contrast")
        }
        val threshold = adaptiveInkThreshold(projection, image.width)

        var firstBlack = -1
        var lastBlackExclusive = -1
        var blackRunCount = 0
        var widestRunWidth = 0
        var firstRunWidth = 0
        var lastRunWidth = 0
        var currentRunStart = -1

        fun commitRun(runEndExclusive: Int) {
            if (currentRunStart == -1) return
            val runWidth = runEndExclusive - currentRunStart
            if (runWidth >= 2) {
                if (firstBlack == -1) {
                    firstBlack = currentRunStart
                    firstRunWidth = runWidth
                }
                lastBlackExclusive = runEndExclusive
                lastRunWidth = runWidth
                widestRunWidth = max(widestRunWidth, runWidth)
                blackRunCount++
            }
            currentRunStart = -1
        }

        for (x in 0 until image.width) {
            when {
                projection[x] >= threshold && currentRunStart == -1 -> currentRunStart = x
                projection[x] < threshold && currentRunStart != -1 -> commitRun(x)
            }
        }
        commitRun(image.width)

        if (blackRunCount == 0) {
            return BarcodeDecodeResult.NotFound("No black bars detected")
        }

        val activeWidth = lastBlackExclusive - firstBlack
        val bits13 = grid13VerdictBitsFromProjection(
            projection = projection,
            activeStart = firstBlack,
            activeWidth = activeWidth,
            threshold = threshold,
        )
        if (hasInvalidGrid13RunCandidate(bits13)) {
            return BarcodeDecodeResult.NotFound("Invalid bit run pattern")
        }

        val correctedBits13 = normalizeGrid13Sentinels(bits13)
        val confidence = calculateConfidence(
            blackRunCount = blackRunCount,
            activeWidth = activeWidth,
            firstRunWidth = firstRunWidth,
            lastRunWidth = lastRunWidth,
            widestRunWidth = widestRunWidth,
        )

        val signature = DetectedSignature(
            rawSignature = bits13ToForwardMealSignature(correctedBits13),
            confidence = confidence,
            bounds = BarcodeBounds(
                x = firstBlack,
                y = 0,
                width = activeWidth,
                height = image.height,
            ),
            debug = null
        )

        if (confidence < minimumFoundConfidence) {
            return BarcodeDecodeResult.Ambiguous(
                candidates = listOf(signature),
                reason = "Low-confidence verdict",
                debug = null,
            )
        }

        return BarcodeDecodeResult.Found(signature)
    }

    private fun calculateConfidence(
        blackRunCount: Int,
        activeWidth: Int,
        firstRunWidth: Int,
        lastRunWidth: Int,
        widestRunWidth: Int,
    ): Double {
        val runCountScore = when (blackRunCount) {
            in 5..6 -> 1.0
            4, 7 -> 0.75
            else -> 0.45
        }
        val spanScore = (1.0 - (abs(activeWidth - 54) / 54.0)).coerceIn(0.0, 1.0)
        val endpointScore = if (blackRunCount >= 2 && widestRunWidth > 0) {
            if (firstRunWidth <= widestRunWidth * 0.75 && lastRunWidth <= widestRunWidth * 0.75) 1.0 else 0.5
        } else 0.0

        return ((0.4 * runCountScore) + (0.3 * spanScore) + (0.3 * endpointScore)).coerceIn(0.0, 0.99)
    }
}

private const val GRID13_VERDICT_BIT_COUNT = 13
private const val GRID13_VERDICT_MAX_VALUE = (1 shl GRID13_VERDICT_BIT_COUNT) - 1
private const val GRID13_VERDICT_SENTINEL_MASK = 0x1803
private const val GRID13_VERDICT_SENTINEL_VALUE = 0x1001

internal fun grid13VerdictBitsFromProjection(
    projection: DoubleArray,
    activeStart: Int,
    activeWidth: Int,
    threshold: Int,
): Int {
    require(projection.isNotEmpty()) { "projection must not be empty" }
    require(activeStart >= 0) { "activeStart must be non-negative" }
    require(activeWidth > 0) { "activeWidth must be positive" }
    require(activeStart + activeWidth <= projection.size) { "active span must fit projection" }
    require(threshold in 0..255) { "threshold must be within 0..255" }

    var bits13 = 0
    repeat(GRID13_VERDICT_BIT_COUNT) { cellIndex ->
        val cellStart = activeStart + ((cellIndex * activeWidth) / GRID13_VERDICT_BIT_COUNT)
        val cellEndExclusive = activeStart + (((cellIndex + 1) * activeWidth) / GRID13_VERDICT_BIT_COUNT)
        val safeEndExclusive = cellEndExclusive.coerceAtLeast(cellStart + 1)

        var totalInk = 0.0
        for (x in cellStart until safeEndExclusive) {
            totalInk += projection[x]
        }
        val meanInk = totalInk / (safeEndExclusive - cellStart)
        bits13 = bits13 shl 1
        if (meanInk >= threshold) {
            bits13 = bits13 or 1
        }
    }
    return bits13
}

internal fun hasInvalidGrid13RunCandidate(bits13: Int): Boolean {
    require(bits13 in 0..GRID13_VERDICT_MAX_VALUE) {
        "Grid13 value must fit within $GRID13_VERDICT_BIT_COUNT bits"
    }

    var currentBit = (bits13 shr (GRID13_VERDICT_BIT_COUNT - 1)) and 1
    var currentRunLength = 1
    for (shift in (GRID13_VERDICT_BIT_COUNT - 2) downTo 0) {
        val bit = (bits13 shr shift) and 1
        if (bit == currentBit) {
            currentRunLength++
        } else {
            currentBit = bit
            currentRunLength = 1
        }

        if ((currentBit == 1 && currentRunLength >= 3) || (currentBit == 0 && currentRunLength >= 4)) {
            return true
        }
    }
    return false
}

internal fun normalizeGrid13Sentinels(bits13: Int): Int {
    require(bits13 in 0..GRID13_VERDICT_MAX_VALUE) {
        "Grid13 value must fit within $GRID13_VERDICT_BIT_COUNT bits"
    }
    return (bits13 and GRID13_VERDICT_SENTINEL_MASK.inv()) or GRID13_VERDICT_SENTINEL_VALUE
}

internal fun bits13ToString(bits13: Int): String {
    require(bits13 in 0..GRID13_VERDICT_MAX_VALUE) {
        "Grid13 value must fit within $GRID13_VERDICT_BIT_COUNT bits"
    }
    return buildString(capacity = GRID13_VERDICT_BIT_COUNT) {
        for (shift in (GRID13_VERDICT_BIT_COUNT - 1) downTo 0) {
            append(if ((bits13 shr shift) and 1 == 1) '1' else '0')
        }
    }
}

internal fun bits13ToForwardMealSignature(bits13: Int): String {
    require(bits13 in 0..GRID13_VERDICT_MAX_VALUE) {
        "Grid13 value must fit within $GRID13_VERDICT_BIT_COUNT bits"
    }
    return "bfm${bits13.toString(radix = 16).uppercase().padStart(4, '0')}"
}

private fun ensureProjectionCapacity(
    current: DoubleArray,
    requiredSize: Int,
): DoubleArray = if (current.size >= requiredSize) current else DoubleArray(requiredSize)

private fun adaptiveInkThreshold(
    projection: DoubleArray,
    width: Int,
): Int {
    require(width > 0) { "width must be positive" }
    var minValue = projection[0]
    var maxValue = projection[0]
    for (index in 1 until width) {
        val value = projection[index]
        if (value < minValue) minValue = value
        if (value > maxValue) maxValue = value
    }
    return (((minValue + maxValue) / 2.0).roundToInt()).coerceIn(0, 255)
}

private fun DoubleArray.inkRange(width: Int): Double {
    require(width > 0) { "width must be positive" }
    var minValue = this[0]
    var maxValue = this[0]
    for (index in 1 until width) {
        val value = this[index]
        if (value < minValue) minValue = value
        if (value > maxValue) maxValue = value
    }
    return maxValue - minValue
}
