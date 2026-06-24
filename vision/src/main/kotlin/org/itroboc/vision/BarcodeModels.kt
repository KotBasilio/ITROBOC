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
    val signatureModel: String? = null,
    val reverseSignature: String? = null,
    val grid13FwdBitsPreSentinel: String? = null,
    val grid13FwdBits: String? = null,
    val grid13RevBits: String? = null,
    val grid13FwdHex: String? = null,
    val grid13RevHex: String? = null,
    val rl2: String? = null,
    val blackRunsPx: List<Int> = emptyList(),
    val whiteGapsPx: List<Int> = emptyList(),
    val blackRunCentersPx: List<Double> = emptyList(),
    val activeStartX: Int? = null,
    val activeEndX: Int? = null,
    val activeSpanPx: Int? = null,
    val sentinelValid: Boolean? = null,
    val sentinelIssues: List<String> = emptyList(),
    val sentinelRepairApplied: Boolean = false,
    val sentinelRepairReason: String? = null,
    val warnings: List<String> = emptyList(),
)

data class DetectedSignature(
    val rawSignature: String,
    val confidence: Double,
    val bounds: BarcodeBounds? = null,
    val debug: BarcodeDebugInfo? = null,
)
