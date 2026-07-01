package org.itroboc.vision

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class Grid13VerdictDecoderTest {

    private val slowDecoder = Grid13SlowDecoder()
    private val verdictDecoder = Grid13VerdictDecoder()

    @Test
    fun `verdict decoder produces same signature as slow decoder on synthetic image`() {
        val bits = "1010101001001"
        val image = grid13Image(bits)

        val slowResult = assertIs<BarcodeDecodeResult.Found>(slowDecoder.decode(image))
        val verdictResult = assertIs<BarcodeDecodeResult.Found>(verdictDecoder.decode(image))

        assertEquals(slowResult.signature.rawSignature, verdictResult.signature.rawSignature)
        assertEquals(slowResult.signature.confidence, verdictResult.signature.confidence, 0.001)
        assertEquals(slowResult.signature.bounds, verdictResult.signature.bounds)
        
        assertNotNull(slowResult.signature.debug)
        assertNull(verdictResult.signature.debug)
    }

    @Test
    fun `verdict decoder handles low confidence as ambiguous`() {
        // High minimum confidence to force ambiguous
        val strictVerdictDecoder = Grid13VerdictDecoder(minimumFoundConfidence = 0.99)
        val image = grid13Image("1010101001001")

        val result = assertIs<BarcodeDecodeResult.Ambiguous>(strictVerdictDecoder.decode(image))
        assertEquals(1, result.candidates.size)
        assertNull(result.debug)
    }

    @Test
    fun `verdict decoder handles not found correctly`() {
        val emptyImage = GrayImage(width = 10, height = 10, pixels = ByteArray(100) { 255.toByte() })
        
        val result = assertIs<BarcodeDecodeResult.NotFound>(verdictDecoder.decode(emptyImage))
        assertNull(result.debug)
    }

    private fun grid13Image(bits: String): GrayImage {
        require(bits.length == 13)
        // Ensure some width for black runs
        val cellWidth = 4
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
