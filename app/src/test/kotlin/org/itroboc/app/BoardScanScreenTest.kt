package org.itroboc.app

import org.itroboc.vision.BarcodeDebugInfo
import org.itroboc.vision.DetectedSignature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BoardScanScreenTest {
    private val signature = DetectedSignature(
        rawSignature = "bfm1549",
        confidence = 0.95,
        debug = BarcodeDebugInfo(reverseSignature = "brm1255"),
    )

    @Test
    fun `orientation mode chooses explicit lookup token`() {
        assertEquals("bfm1549", signature.signatureFor(BarcodeOrientationMode.BFM))
        assertEquals("brm1255", signature.signatureFor(BarcodeOrientationMode.BRM))
    }

    @Test
    fun `auto orientation remains deliberately unresolved`() {
        assertNull(signature.signatureFor(BarcodeOrientationMode.AUTO))
    }
}
