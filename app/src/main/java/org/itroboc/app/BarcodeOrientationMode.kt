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

internal fun DetectedSignature.viewedAs(mode: BarcodeOrientationMode): String? = when (mode) {
    BarcodeOrientationMode.BFM -> rawSignature
    BarcodeOrientationMode.BRM -> {
        // Keeps the same digits but swaps "bfm" for "brm"
        rawSignature.replaceFirst("bfm", "brm")
    }
    BarcodeOrientationMode.AUTO -> null
}
