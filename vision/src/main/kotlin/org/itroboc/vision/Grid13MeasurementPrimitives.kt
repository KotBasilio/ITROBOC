package org.itroboc.vision

import kotlin.math.roundToInt

data class InkImage(
    val width: Int,
    val height: Int,
    val inkValues: IntArray,
) {
    init {
        require(width > 0) { "width must be positive" }
        require(height > 0) { "height must be positive" }
        require(inkValues.size == width * height) {
            "inkValues size must match width * height"
        }
        require(inkValues.all { it in 0..255 }) {
            "ink values must be within 0..255"
        }
    }

    fun inkAt(x: Int, y: Int): Int {
        require(x in 0 until width) { "x out of bounds: $x" }
        require(y in 0 until height) { "y out of bounds: $y" }
        return inkValues[(y * width) + x]
    }
}

fun inkFromRgb(
    red: Int,
    green: Int,
    blue: Int,
): Int {
    require(red in 0..255) { "red must be within 0..255" }
    require(green in 0..255) { "green must be within 0..255" }
    require(blue in 0..255) { "blue must be within 0..255" }
    return 255 - minOf(red, green, blue)
}

fun GrayImage.toInkImage(): InkImage =
    InkImage(
        width = width,
        height = height,
        inkValues = IntArray(width * height) { index ->
            255 - (pixels[index].toInt() and 0xFF)
        },
    )

fun projectInkColumns(image: InkImage): DoubleArray =
    DoubleArray(image.width) { x ->
        var total = 0
        for (y in 0 until image.height) {
            total += image.inkAt(x, y)
        }
        total.toDouble() / image.height
    }

fun adaptiveInkThreshold(projection: DoubleArray): Int {
    require(projection.isNotEmpty()) { "projection must not be empty" }
    val minValue = projection.minOrNull() ?: 0.0
    val maxValue = projection.maxOrNull() ?: 0.0
    return ((minValue + maxValue) / 2.0).roundToInt().coerceIn(0, 255)
}

fun thresholdInkProjection(
    projection: DoubleArray,
    threshold: Int = adaptiveInkThreshold(projection),
): List<Boolean> {
    require(threshold in 0..255) { "threshold must be within 0..255" }
    return projection.map { inkValue -> inkValue >= threshold }
}

fun extractRuns(mask: List<Boolean>): List<IntRange> {
    val runs = mutableListOf<IntRange>()
    var start = -1

    mask.forEachIndexed { index, isActive ->
        when {
            isActive && start == -1 -> start = index
            !isActive && start != -1 -> {
                runs += start..(index - 1)
                start = -1
            }
        }
    }

    if (start != -1) {
        runs += start..mask.lastIndex
    }

    return runs
}

fun activeSpanFromBlackRuns(blackRuns: List<IntRange>): IntRange? {
    if (blackRuns.isEmpty()) {
        return null
    }
    return blackRuns.first().first..blackRuns.last().last
}

fun internalWhiteGapsBetween(blackRuns: List<IntRange>): List<IntRange> =
    blackRuns.zipWithNext().mapNotNull { (leftRun, rightRun) ->
        val start = leftRun.last + 1
        val end = rightRun.first - 1
        if (start <= end) {
            start..end
        } else {
            null
        }
    }
