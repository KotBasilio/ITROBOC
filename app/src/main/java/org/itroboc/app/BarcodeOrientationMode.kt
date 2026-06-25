package org.itroboc.app

import org.itroboc.vision.DetectedSignature

enum class BarcodeOrientationMode(
    val label: String,
) {
    BFM("bfm"),
    BRM("brm"),
    AUTO("auto"),
}

internal fun DetectedSignature.signatureFor(mode: BarcodeOrientationMode): String? = when (mode) {
    BarcodeOrientationMode.BFM -> rawSignature
    BarcodeOrientationMode.BRM -> debug?.reverseSignature
    BarcodeOrientationMode.AUTO -> null
}
