package org.itroboc.vision

class Grid13SlowDecoder(
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
            reason = "No grid13-v2 barcode measurement produced",
            debug = BarcodeDebugInfo(
                threshold = threshold,
                signatureModel = GRID13_SIGNATURE_MODEL,
            ),
        )

        val sentinelCheck = checkGrid13Sentinels(measurement.grid13FwdBits)
        val signature = measurement.toDetectedSignature(
            imageHeight = image.height,
            threshold = threshold,
            sentinelCheck = sentinelCheck,
        )

        if (measurement.confidence < minimumFoundConfidence) {
            return BarcodeDecodeResult.Ambiguous(
                candidates = listOf(signature),
                reason = "Low-confidence grid13-v2 measurement",
                debug = signature.debug,
            )
        }

        return BarcodeDecodeResult.Found(signature)
    }
}

const val GRID13_SIGNATURE_MODEL: String = "grid13-v2"

private fun Grid13BarcodeMeasurement.toDetectedSignature(
    imageHeight: Int,
    threshold: Int,
    sentinelCheck: Grid13SentinelCheck,
): DetectedSignature {
    val correctedFwdBits = if (sentinelCheck.isValid) {
        grid13FwdBits
    } else {
        normalizeGrid13Sentinels(grid13FwdBits)
    }
    val correctedRevBits = reverseBits(correctedFwdBits)
    val correctedRl2 = grid13RunLengthSignature(correctedFwdBits)
    val repairReason = sentinelCheck.issues
        .takeIf { it.isNotEmpty() }
        ?.joinToString(prefix = "Normalized Grid13 control bits: ")

    return DetectedSignature(
        rawSignature = forwardMealSignature(correctedFwdBits),
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
            normalizedPattern = correctedRl2,
            signatureModel = GRID13_SIGNATURE_MODEL,
            reverseSignature = reverseMealSignature(correctedRevBits),
            grid13FwdBitsPreSentinel = grid13FwdBits,
            grid13FwdBits = correctedFwdBits,
            grid13RevBits = correctedRevBits,
            grid13FwdHex = grid13BitsToHex(correctedFwdBits),
            grid13RevHex = grid13BitsToHex(correctedRevBits),
            rl2 = correctedRl2,
            blackRunsPx = blackRunsPx,
            whiteGapsPx = whiteGapsPx,
            blackRunCentersPx = blackRunCentersPx,
            activeStartX = activeStartX,
            activeEndX = activeEndX,
            activeSpanPx = activeSpanPx,
            sentinelValid = sentinelCheck.isValid,
            sentinelIssues = sentinelCheck.issues,
            sentinelRepairApplied = !sentinelCheck.isValid,
            sentinelRepairReason = repairReason,
            warnings = warnings + listOfNotNull(repairReason),
        ),
    )
}
