package org.itroboc.vision

import kotlin.math.roundToInt

class SimpleBarRunBarcodeDecoder(
    private val minContrastRange: Int = 24,
    private val minRunCount: Int = 2,
    private val minBlackCoverage: Double = 0.6,
    private val maxRelativeWidthSpread: Double = 0.45,
) : BarcodeDecoder {
    override fun decode(image: GrayImage): BarcodeDecodeResult {
        val minIntensity = image.pixels.minIntensity()
        val maxIntensity = image.pixels.maxIntensity()
        val threshold = (minIntensity + maxIntensity) / 2
        val contrastRange = maxIntensity - minIntensity
        val baseDebug = BarcodeDebugInfo(threshold = threshold)

        if (contrastRange < minContrastRange) {
            return BarcodeDecodeResult.NotFound(
                reason = "Image contrast too low for barcode decoding",
                debug = baseDebug,
            )
        }

        val blackColumns = (0 until image.width).map { x ->
            val blackPixels = (0 until image.height).count { y ->
                image.intensityAt(x, y) <= threshold
            }
            blackPixels.toDouble() / image.height >= minBlackCoverage
        }

        val blackRuns = blackColumns.toRuns()
        if (blackRuns.isEmpty()) {
            return BarcodeDecodeResult.NotFound(
                reason = "No barcode-like black runs detected",
                debug = baseDebug,
            )
        }

        if (blackRuns.size < minRunCount) {
            return BarcodeDecodeResult.NotFound(
                reason = "Too few black runs detected",
                debug = baseDebug.copy(blackRuns = blackRuns),
            )
        }

        val runWidths = blackRuns.map { it.last - it.first + 1 }
        val normalizedByMin = runWidths.toNormalizedPattern(runWidths.min().toDouble())
        val spread = runWidths.relativeSpread()
        val debug = BarcodeDebugInfo(
            threshold = threshold,
            blackRuns = blackRuns,
            normalizedPattern = normalizedByMin,
        )

        if (spread > maxRelativeWidthSpread) {
            val normalizedByAverage = runWidths.toNormalizedPattern(runWidths.average())
            val alternatePattern = if (normalizedByAverage != normalizedByMin) {
                normalizedByAverage
            } else {
                runWidths.toNormalizedPattern(runWidths.max().toDouble())
            }
            val candidatePatterns = linkedSetOf(normalizedByMin, alternatePattern)
            val candidates = candidatePatterns.map { pattern ->
                DetectedSignature(
                    rawSignature = "bars:$pattern",
                    confidence = 0.35,
                    bounds = blackRuns.toBounds(image.height),
                    debug = debug.copy(normalizedPattern = pattern),
                )
            }

            return BarcodeDecodeResult.Ambiguous(
                candidates = candidates,
                reason = "Run widths vary too much for a single stable signature",
                debug = debug,
            )
        }

        return BarcodeDecodeResult.Found(
            signature = DetectedSignature(
                rawSignature = "bars:$normalizedByMin",
                confidence = computeConfidence(contrastRange, blackRuns.size, spread),
                bounds = blackRuns.toBounds(image.height),
                debug = debug,
            )
        )
    }

    private fun computeConfidence(
        contrastRange: Int,
        runCount: Int,
        spread: Double,
    ): Double {
        val contrastScore = (contrastRange / 255.0).coerceIn(0.0, 1.0)
        val runScore = (runCount.coerceAtMost(6) / 6.0).coerceIn(0.0, 1.0)
        val spreadScore = (1.0 - spread).coerceIn(0.0, 1.0)
        return (0.35 + (0.35 * contrastScore) + (0.15 * runScore) + (0.15 * spreadScore))
            .coerceIn(0.0, 0.99)
    }
}

private fun ByteArray.minIntensity(): Int = minOf { it.toInt() and 0xFF }

private fun ByteArray.maxIntensity(): Int = maxOf { it.toInt() and 0xFF }

private fun List<Boolean>.toRuns(): List<IntRange> {
    val runs = mutableListOf<IntRange>()
    var start = -1

    forEachIndexed { index, isBlack ->
        when {
            isBlack && start == -1 -> start = index
            !isBlack && start != -1 -> {
                runs += start..(index - 1)
                start = -1
            }
        }
    }

    if (start != -1) {
        runs += start..lastIndex
    }

    return runs
}

private fun List<Int>.toNormalizedPattern(baseWidth: Double): String {
    require(baseWidth > 0.0) { "baseWidth must be positive" }
    return joinToString(separator = "-") { width ->
        (width / baseWidth).roundToInt().coerceAtLeast(1).toString()
    }
}

private fun List<Int>.relativeSpread(): Double {
    if (isEmpty()) return 0.0
    val averageWidth = average()
    if (averageWidth == 0.0) return 0.0
    return ((maxOrNull() ?: 0) - (minOrNull() ?: 0)) / averageWidth
}

private fun List<IntRange>.toBounds(imageHeight: Int): BarcodeBounds {
    val firstX = first().first
    val lastX = last().last
    return BarcodeBounds(
        x = firstX,
        y = 0,
        width = (lastX - firstX) + 1,
        height = imageHeight,
    )
}
