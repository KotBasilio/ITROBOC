package org.itroboc.vision

/**
 * An optimized decoder for the "Verdict" path.
 *
 * Phase 1: Seam splitting. Initially delegates to a fuller decoder
 * but strips out debug information to separate call sites.
 */
class Grid13VerdictDecoder(
    private val fullDecoder: BarcodeDecoder = Grid13SlowDecoder()
) : BarcodeDecoder {

    override fun decode(image: GrayImage): BarcodeDecodeResult {
        val result = fullDecoder.decode(image)
        return result.stripDebug()
    }

    private fun BarcodeDecodeResult.stripDebug(): BarcodeDecodeResult = when (this) {
        is BarcodeDecodeResult.Found -> {
            BarcodeDecodeResult.Found(
                signature = signature.copy(debug = null)
            )
        }
        is BarcodeDecodeResult.NotFound -> {
            copy(debug = null)
        }
        is BarcodeDecodeResult.Ambiguous -> {
            copy(
                candidates = candidates.map { it.copy(debug = null) },
                debug = null
            )
        }
    }
}
