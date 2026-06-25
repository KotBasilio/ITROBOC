package org.itroboc.vision

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class Grid13BarcodeDecoderTest {
    private val decoder = Grid13BarcodeDecoder()

    @Test
    fun `decodes grid13 image into forward meal signature`() {
        val result = decoder.decode(grid13Image("1010101001001"))
        val found = assertIs<BarcodeDecodeResult.Found>(result)
        val debug = assertNotNull(found.signature.debug)

        assertEquals("bfm1549", found.signature.rawSignature)
        assertEquals("brm1255", debug.reverseSignature)
        assertEquals("grid13-v2", debug.signatureModel)
        assertEquals("1010101001001", debug.grid13FwdBits)
        assertEquals("1001001010101", debug.grid13RevBits)
        assertEquals("1549", debug.grid13FwdHex)
        assertEquals("1255", debug.grid13RevHex)
        assertEquals("B1-W1-B1-W1-B1-W1-B1-W2-B1-W2-B1", debug.rl2)
        assertEquals(listOf(2, 2, 2, 2, 2, 2), debug.blackRunsPx)
        assertEquals(listOf(2, 2, 2, 4, 4), debug.whiteGapsPx)
        assertEquals(26, debug.activeSpanPx)
        assertTrue(found.signature.confidence >= 0.60)
    }

    @Test
    fun `flat image is not found`() {
        val result = decoder.decode(
            GrayImage(
                width = 26,
                height = 4,
                pixels = ByteArray(26 * 4) { 255.toByte() },
            ),
        )
        val notFound = assertIs<BarcodeDecodeResult.NotFound>(result)

        assertEquals("No grid13-v2 barcode measurement produced", notFound.reason)
        assertEquals("grid13-v2", notFound.debug?.signatureModel)
    }

    @Test
    fun `low confidence measurement is ambiguous and not silently found`() {
        val result = decoder.decode(grid13Image("1000000000001"))
        val ambiguous = assertIs<BarcodeDecodeResult.Ambiguous>(result)

        assertEquals("Low-confidence grid13-v2 measurement", ambiguous.reason)
        assertEquals(listOf("bfm1001"), ambiguous.candidates.map { it.rawSignature })
        assertEquals("grid13-v2", ambiguous.debug?.signatureModel)
        assertEquals(true, ambiguous.debug?.sentinelValid)
    }

    @Test
    fun `invalid sentinel pattern is normalized before producing a signature`() {
        val result = Grid13BarcodeDecoder(minimumFoundConfidence = 0.0)
            .decode(grid13Image("1100000000001"))
        val found = assertIs<BarcodeDecodeResult.Found>(result)
        val debug = assertNotNull(found.signature.debug)

        assertEquals("bfm1001", found.signature.rawSignature)
        assertEquals("1100000000001", debug.grid13FwdBitsPreSentinel)
        assertEquals("1000000000001", debug.grid13FwdBits)
        assertEquals("B1-W11-B1", debug.rl2)
        assertEquals(false, debug.sentinelValid)
        assertEquals(listOf("bit11 must be white"), debug.sentinelIssues)
        assertEquals(true, debug.sentinelRepairApplied)
        assertTrue(debug.sentinelRepairReason?.contains("bit11 must be white") == true)
    }

    @Test
    fun `normalized sentinel candidate can remain ambiguous because of confidence`() {
        val result = Grid13BarcodeDecoder(minimumFoundConfidence = 1.0)
            .decode(grid13Image("1100000000001"))
        val ambiguous = assertIs<BarcodeDecodeResult.Ambiguous>(result)

        assertEquals("Low-confidence grid13-v2 measurement", ambiguous.reason)
        assertEquals(listOf("bfm1001"), ambiguous.candidates.map { it.rawSignature })
        assertEquals(true, ambiguous.debug?.sentinelRepairApplied)
    }

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
