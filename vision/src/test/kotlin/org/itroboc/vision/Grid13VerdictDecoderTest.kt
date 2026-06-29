package org.itroboc.vision

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class Grid13VerdictDecoderTest {

    private val fullDecoder = Grid13BarcodeDecoder()
    private val verdictDecoder = Grid13VerdictDecoder(fullDecoder)

    @Test
    fun `consistency between full and verdict decoders`() {
        // Create a mock GrayImage that should result in a Found barcode
        // For simplicity, we can use an image with at least some content
        val image = GrayImage(100, 20, ByteArray(2000) { 128.toByte() })

        val fullResult = fullDecoder.decode(image)
        val verdictResult = verdictDecoder.decode(image)

        // Result type consistency
        assertEquals(fullResult::class, verdictResult::class)

        when (fullResult) {
            is BarcodeDecodeResult.Found -> {
                val verdictFound = verdictResult as BarcodeDecodeResult.Found
                assertEquals(fullResult.signature.rawSignature, verdictFound.signature.rawSignature)
                assertEquals(fullResult.signature.confidence, verdictFound.signature.confidence)
                assertNotNull(fullResult.signature.debug)
                assertNull(verdictFound.signature.debug)
            }
            is BarcodeDecodeResult.NotFound -> {
                val verdictNotFound = verdictResult as BarcodeDecodeResult.NotFound
                assertEquals(fullResult.reason, verdictNotFound.reason)
                // In Phase 1, fullDecoder might return debug info even for NotFound
                // But verdictDecoder MUST NOT.
                assertNull(verdictNotFound.debug)
            }
            is BarcodeDecodeResult.Ambiguous -> {
                val verdictAmbiguous = verdictResult as BarcodeDecodeResult.Ambiguous
                assertEquals(fullResult.reason, verdictAmbiguous.reason)
                assertEquals(fullResult.candidates.size, verdictAmbiguous.candidates.size)
                assertNull(verdictAmbiguous.debug)
                verdictAmbiguous.candidates.forEach { assertNull(it.debug) }
            }
        }
    }
}
