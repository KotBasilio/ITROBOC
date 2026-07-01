package org.itroboc.vision

import kotlin.math.abs

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

    override fun decode(image: GrayImage): BarcodeDecodeResult {
        // 1. Column projection (Ink-based: 255 is black)
        val projection = DoubleArray(image.width) { x ->
            var total = 0
            for (y in 0 until image.height) {
                total += 255 - (image.pixels[(y * image.width) + x].toInt() and 0xFF)
            }
            total.toDouble() / image.height
        }

        if (projection.isEmpty()) {
            return BarcodeDecodeResult.NotFound("Empty image projection")
        }

        // 2. Adaptive threshold
        val minValue = projection.minOrNull() ?: 0.0
        val maxValue = projection.maxOrNull() ?: 0.0
        if ((maxValue - minValue) < 1.0) {
            return BarcodeDecodeResult.NotFound("Insufficient ink contrast")
        }
        val threshold = (minValue + maxValue) / 2.0

        // 3. Extract black runs for active span and confidence
        val blackRuns = mutableListOf<IntRange>()
        var runStart = -1
        for (x in projection.indices) {
            val isBlack = projection[x] >= threshold
            if (isBlack && runStart == -1) {
                runStart = x
            } else if (!isBlack && runStart != -1) {
                val width = x - runStart
                if (width >= 2) {
                    blackRuns += runStart until x
                }
                runStart = -1
            }
        }
        if (runStart != -1) {
            val width = projection.size - runStart
            if (width >= 2) {
                blackRuns += runStart until projection.size
            }
        }

        if (blackRuns.isEmpty()) {
            return BarcodeDecodeResult.NotFound("No black bars detected")
        }

        val activeSpan = blackRuns.first().first..blackRuns.last().last
        val activeWidth = activeSpan.last - activeSpan.first + 1

        // 4. Derive 13 bits
        val fwdBits = buildString(capacity = 13) {
            repeat(13) { cellIndex ->
                val cellStart = activeSpan.first + ((cellIndex * activeWidth) / 13)
                val cellEndExclusive = activeSpan.first + (((cellIndex + 1) * activeWidth) / 13)
                val safeEndExclusive = cellEndExclusive.coerceAtLeast(cellStart + 1)
                
                var totalInk = 0.0
                for (x in cellStart until safeEndExclusive) {
                    totalInk += projection[x]
                }
                val meanInk = totalInk / (safeEndExclusive - cellStart)
                append(if (meanInk >= threshold) '1' else '0')
            }
        }

        if (hasInvalidGrid13RunCandidateSlow(fwdBits)) {
            return BarcodeDecodeResult.NotFound("Invalid bit run pattern")
        }

        // 5. Sentinel normalization
        val sentinelCheck = checkGrid13SentinelsSlow(fwdBits)
        val correctedFwdBits = if (sentinelCheck.isValid) {
            fwdBits
        } else {
            normalizeGrid13SentinelsSlow(fwdBits)
        }

        // 6. Confidence calculation
        val confidence = calculateConfidence(blackRuns, activeWidth)

        val signature = DetectedSignature(
            rawSignature = forwardMealSignatureSlow(correctedFwdBits),
            confidence = confidence,
            bounds = BarcodeBounds(
                x = activeSpan.first,
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
        blackRuns: List<IntRange>,
        activeWidth: Int,
    ): Double {
        val runCountScore = when (blackRuns.size) {
            in 5..6 -> 1.0
            4, 7 -> 0.75
            else -> 0.45
        }
        val spanScore = (1.0 - (abs(activeWidth - 54) / 54.0)).coerceIn(0.0, 1.0)
        
        // Endpoint narrowness check
        val endpointScore = if (blackRuns.size >= 2) {
            val widths = blackRuns.map { it.last - it.first + 1 }
            val widest = widths.maxOrNull() ?: 1
            if (widths.first() <= widest * 0.75 && widths.last() <= widest * 0.75) 1.0 else 0.5
        } else 0.0

        return ((0.4 * runCountScore) + (0.3 * spanScore) + (0.3 * endpointScore)).coerceIn(0.0, 0.99)
    }
}
