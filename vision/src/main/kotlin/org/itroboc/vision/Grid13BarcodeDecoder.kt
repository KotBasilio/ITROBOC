package org.itroboc.vision

class Grid13BarcodeDecoder(
    private val minimumFoundConfidence: Double = 0.60,
) : BarcodeDecoder {
    init {
        require(minimumFoundConfidence in 0.0..1.0) {
            "minimumFoundConfidence must be within [0, 1]"
        }
    }

    override fun decode(image: GrayImage): BarcodeDecodeResult {
        val inkImage = image.toInkImage()
        val projection = projectInkColumns(inkImage)
        val threshold = adaptiveInkThreshold(projection)
        val measurement = measureGrid13BarcodeProjection(
            projection = projection,
            threshold = threshold,
        ) ?: return BarcodeDecodeResult.NotFound(
            reason = "No grid13-v1 barcode measurement produced",
            debug = BarcodeDebugInfo(
                threshold = threshold,
                signatureModel = GRID13_SIGNATURE_MODEL,
            ),
        )

        val signature = measurement.toDetectedSignature(
            imageHeight = image.height,
            threshold = threshold,
        )

        if (measurement.confidence < minimumFoundConfidence) {
            return BarcodeDecodeResult.Ambiguous(
                candidates = listOf(signature),
                reason = "Low-confidence grid13-v1 measurement",
                debug = signature.debug,
            )
        }

        return BarcodeDecodeResult.Found(signature)
    }
}

const val GRID13_SIGNATURE_MODEL: String = "grid13-v1"

private fun Grid13BarcodeMeasurement.toDetectedSignature(
    imageHeight: Int,
    threshold: Int,
): DetectedSignature =
    DetectedSignature(
        rawSignature = rawSignature,
        confidence = confidence,
        bounds = BarcodeBounds(
            x = activeStartX,
            y = 0,
            width = activeSpanPx,
            height = imageHeight,
        ),
        debug = BarcodeDebugInfo(
            threshold = threshold,
            blackRuns = blackRunEdges,
            normalizedPattern = rl2,
            signatureModel = GRID13_SIGNATURE_MODEL,
            reverseSignature = reverseSignature,
            grid13FwdBits = grid13FwdBits,
            grid13RevBits = grid13RevBits,
            grid13FwdHex = grid13FwdHex,
            grid13RevHex = grid13RevHex,
            rl2 = rl2,
            blackRunsPx = blackRunsPx,
            whiteGapsPx = whiteGapsPx,
            blackRunCentersPx = blackRunCentersPx,
            activeStartX = activeStartX,
            activeEndX = activeEndX,
            activeSpanPx = activeSpanPx,
            warnings = warnings,
        ),
    )
