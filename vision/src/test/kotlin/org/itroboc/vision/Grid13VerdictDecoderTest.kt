package org.itroboc.vision

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class Grid13VerdictDecoderTest {

    @Test
    fun `found verdict preserves payload and strips debug`() {
        val fullResult = BarcodeDecodeResult.Found(
            signature = detectedSignature(
                rawSignature = "bfm1549",
                debug = barcodeDebugInfo(reverseSignature = "brm1255"),
            ),
        )

        val verdictResult = Grid13VerdictDecoder(FakeDecoder(fullResult)).decode(dummyImage())
        val found = assertIs<BarcodeDecodeResult.Found>(verdictResult)

        assertEquals("bfm1549", found.signature.rawSignature)
        assertEquals(0.95, found.signature.confidence)
        assertNull(found.signature.debug)
    }

    @Test
    fun `not found verdict preserves reason and strips debug`() {
        val fullResult = BarcodeDecodeResult.NotFound(
            reason = "No grid13-v2 barcode measurement produced",
            debug = barcodeDebugInfo(signatureModel = "grid13-v2"),
        )

        val verdictResult = Grid13VerdictDecoder(FakeDecoder(fullResult)).decode(dummyImage())
        val notFound = assertIs<BarcodeDecodeResult.NotFound>(verdictResult)

        assertEquals(fullResult.reason, notFound.reason)
        assertNull(notFound.debug)
    }

    @Test
    fun `ambiguous verdict preserves candidates and strips all debug`() {
        val fullResult = BarcodeDecodeResult.Ambiguous(
            candidates = listOf(
                detectedSignature(
                    rawSignature = "bfm1549",
                    debug = barcodeDebugInfo(reverseSignature = "brm1255"),
                ),
                detectedSignature(
                    rawSignature = "bfm12A9",
                    debug = barcodeDebugInfo(reverseSignature = "brm1955"),
                ),
            ),
            reason = "Low-confidence grid13-v2 measurement",
            debug = barcodeDebugInfo(signatureModel = "grid13-v2"),
        )

        val verdictResult = Grid13VerdictDecoder(FakeDecoder(fullResult)).decode(dummyImage())
        val ambiguous = assertIs<BarcodeDecodeResult.Ambiguous>(verdictResult)

        assertEquals(fullResult.reason, ambiguous.reason)
        assertEquals(fullResult.candidates.map { it.rawSignature }, ambiguous.candidates.map { it.rawSignature })
        assertEquals(fullResult.candidates.map { it.confidence }, ambiguous.candidates.map { it.confidence })
        assertNull(ambiguous.debug)
        ambiguous.candidates.forEach { candidate ->
            assertNull(candidate.debug)
        }
    }

    @Test
    fun `real decoder smoke test matches full verdict shape on found barcode`() {
        val fullDecoder = Grid13BarcodeDecoder()
        val verdictDecoder = Grid13VerdictDecoder(fullDecoder)
        val image = grid13Image("1010101001001")

        val fullResult = assertIs<BarcodeDecodeResult.Found>(fullDecoder.decode(image))
        val verdictResult = assertIs<BarcodeDecodeResult.Found>(verdictDecoder.decode(image))

        assertEquals(fullResult.signature.rawSignature, verdictResult.signature.rawSignature)
        assertNotNull(fullResult.signature.debug)
        assertNull(verdictResult.signature.debug)
    }

    private class FakeDecoder(
        private val result: BarcodeDecodeResult,
    ) : BarcodeDecoder {
        override fun decode(image: GrayImage): BarcodeDecodeResult = result
    }

    private fun detectedSignature(
        rawSignature: String,
        confidence: Double = 0.95,
        debug: BarcodeDebugInfo? = null,
    ) = DetectedSignature(
        rawSignature = rawSignature,
        confidence = confidence,
        bounds = BarcodeBounds(x = 1, y = 2, width = 3, height = 4),
        debug = debug,
    )

    private fun barcodeDebugInfo(
        signatureModel: String? = null,
        reverseSignature: String? = null,
    ) = BarcodeDebugInfo(
        signatureModel = signatureModel,
        reverseSignature = reverseSignature,
        rl2 = "B1-W1-B1",
    )

    private fun dummyImage(): GrayImage =
        GrayImage(width = 1, height = 1, pixels = byteArrayOf(0))

    private fun grid13Image(bits: String): GrayImage {
        require(bits.length == 13)
        val cellWidth = 2
        val height = 4
        val width = bits.length * cellWidth
        val pixels = ByteArray(width * height)

        bits.forEachIndexed { cellIndex, bit ->
            val intensity = if (bit == '1') 0 else 255
            repeat(cellWidth) { xInCell ->
                val x = (cellIndex * cellWidth) + xInCell
                repeat(height) { y ->
                    pixels[(y * width) + x] = intensity.toByte()
                }
            }
        }

        return GrayImage(width = width, height = height, pixels = pixels)
    }
}
