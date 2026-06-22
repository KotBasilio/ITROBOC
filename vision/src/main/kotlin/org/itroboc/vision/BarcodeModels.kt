package org.itroboc.vision

data class BarcodeRoi(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

data class BarcodeBounds(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

data class BarcodeDebugInfo(
    val threshold: Int? = null,
    val blackRuns: List<IntRange> = emptyList(),
    val normalizedPattern: String? = null,
)

data class DetectedSignature(
    val rawSignature: String,
    val confidence: Double,
    val bounds: BarcodeBounds? = null,
    val debug: BarcodeDebugInfo? = null,
)
