package org.itroboc.app

import org.itroboc.vision.DetectedSignature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BoardScanScreenTest {
    private val signature = DetectedSignature(
        rawSignature = "bfm1549",
        confidence = 0.95,
        debug = null,
    )

    @Test
    fun `viewedAs chooses explicit lookup token using family prefix`() {
        // BFM uses the raw signature as-is
        assertEquals("bfm1549", signature.viewedAs(BarcodeOrientationMode.BFM))
        
        // BRM swaps the prefix, preserving the payload
        assertEquals("brm1549", signature.viewedAs(BarcodeOrientationMode.BRM))
    }

    @Test
    fun `auto orientation remains deliberately unresolved`() {
        assertNull(signature.viewedAs(BarcodeOrientationMode.AUTO))
    }
}
