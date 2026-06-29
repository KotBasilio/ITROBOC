package org.itroboc.app

import org.itroboc.vision.DetectedSignature

enum class BarcodeOrientationMode(
    val label: String,
) {
    BFM("bfm"),
    BRM("brm"),
    AUTO("auto"),
}

/**
 * Official TD lookup path for converting a detected signature into a deck-profile token.
 *
 * Current physical model:
 * - BFM: use the raw detected signature (e.g. bfm15A9)
 * - BRM: same visible payload but in the "brm" family (e.g. brm15A9)
 * - AUTO: deliberately unresolved until vision logic can determine orientation
 */
internal fun DetectedSignature.viewedAs(mode: BarcodeOrientationMode): String? = when (mode) {
    BarcodeOrientationMode.BFM -> rawSignature
    BarcodeOrientationMode.BRM -> {
        // Keeps the same digits but swaps "bfm" for "brm"
        rawSignature.replaceFirst("bfm", "brm")
    }
    BarcodeOrientationMode.AUTO -> null
}
