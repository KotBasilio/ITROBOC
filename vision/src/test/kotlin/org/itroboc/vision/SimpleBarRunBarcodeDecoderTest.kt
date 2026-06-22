package org.itroboc.vision

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SimpleBarRunBarcodeDecoderTest {
    private val decoder = SimpleBarRunBarcodeDecoder()

    @Test
    fun `clear repeated bars decode into a stable synthetic signature`() {
        val image = stripedImage(
            height = 8,
            white(2),
            black(2),
            white(2),
            black(2),
            white(2),
            black(2),
            white(2),
        )

        val result = decoder.decode(image)
        val found = assertIs<BarcodeDecodeResult.Found>(result)

        assertEquals("bars:1-1-1", found.signature.rawSignature)
        assertTrue(found.signature.confidence > 0.5)
        assertEquals(2, found.signature.bounds?.x)
        assertEquals(10, found.signature.bounds?.width)
    }

    @Test
    fun `blank image returns not found`() {
        val image = solidImage(width = 12, height = 6, intensity = 255)

        val result = decoder.decode(image)
        val notFound = assertIs<BarcodeDecodeResult.NotFound>(result)

        assertEquals("Image contrast too low for barcode decoding", notFound.reason)
        assertEquals(255, notFound.debug?.threshold)
    }

    @Test
    fun `uneven run widths return ambiguous candidates`() {
        val image = stripedImage(
            height = 8,
            white(2),
            black(2),
            white(1),
            black(4),
            white(1),
            black(2),
            white(2),
        )

        val result = decoder.decode(image)
        val ambiguous = assertIs<BarcodeDecodeResult.Ambiguous>(result)

        assertTrue(ambiguous.candidates.isNotEmpty())
        assertEquals(2, ambiguous.candidates.map { it.rawSignature }.distinct().size)
        assertEquals("1-2-1", ambiguous.debug?.normalizedPattern)
    }

    @Test
    fun `found result exposes debug threshold and black runs`() {
        val image = stripedImage(
            height = 6,
            white(1),
            black(3),
            white(2),
            black(3),
            white(1),
        )

        val result = decoder.decode(image)
        val found = assertIs<BarcodeDecodeResult.Found>(result)
        val debug = assertNotNull(found.signature.debug)

        assertEquals(127, debug.threshold)
        assertEquals(listOf(1..3, 6..8), debug.blackRuns)
        assertEquals("1-1", debug.normalizedPattern)
    }

    private fun stripedImage(
        height: Int,
        vararg segments: Segment,
    ): GrayImage {
        val width = segments.sumOf { it.width }
        val pixels = ByteArray(width * height)
        var xOffset = 0

        for (segment in segments) {
            val intensity = if (segment.isBlack) 0 else 255
            repeat(segment.width) { xInSegment ->
                val x = xOffset + xInSegment
                repeat(height) { y ->
                    pixels[(y * width) + x] = intensity.toByte()
                }
            }
            xOffset += segment.width
        }

        return GrayImage(width = width, height = height, pixels = pixels)
    }

    private fun solidImage(
        width: Int,
        height: Int,
        intensity: Int,
    ): GrayImage = GrayImage(
        width = width,
        height = height,
        pixels = ByteArray(width * height) { intensity.toByte() },
    )

    private fun black(width: Int): Segment = Segment(width = width, isBlack = true)

    private fun white(width: Int): Segment = Segment(width = width, isBlack = false)

    private data class Segment(
        val width: Int,
        val isBlack: Boolean,
    )
}
