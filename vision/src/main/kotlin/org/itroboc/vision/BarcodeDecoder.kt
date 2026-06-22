package org.itroboc.vision

interface BarcodeDecoder {
    fun decode(image: GrayImage): BarcodeDecodeResult
}

sealed interface BarcodeDecodeResult {
    data class Found(val signature: DetectedSignature) : BarcodeDecodeResult

    data class NotFound(
        val reason: String,
        val debug: BarcodeDebugInfo? = null,
    ) : BarcodeDecodeResult

    data class Ambiguous(
        val candidates: List<DetectedSignature>,
        val reason: String,
        val debug: BarcodeDebugInfo? = null,
    ) : BarcodeDecodeResult
}
