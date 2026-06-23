package org.itroboc.vision

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class Grid13MeasurementPrimitivesTest {
    @Test
    fun `ink from rgb uses inverse minimum channel`() {
        assertEquals(255, inkFromRgb(red = 0, green = 0, blue = 0))
        assertEquals(0, inkFromRgb(red = 255, green = 255, blue = 255))
        assertEquals(215, inkFromRgb(red = 180, green = 40, blue = 60))
    }

    @Test
    fun `gray image converts to compatible ink darkness`() {
        val image = GrayImage(
            width = 3,
            height = 1,
            pixels = byteArrayOf(0, 127, 255.toByte()),
        )

        val inkImage = image.toInkImage()

        assertEquals(255, inkImage.inkAt(0, 0))
        assertEquals(128, inkImage.inkAt(1, 0))
        assertEquals(0, inkImage.inkAt(2, 0))
    }

    @Test
    fun `projection averages ink values by column`() {
        val image = InkImage(
            width = 3,
            height = 2,
            inkValues = intArrayOf(
                0, 100, 200,
                50, 150, 250,
            ),
        )

        assertContentEquals(
            doubleArrayOf(25.0, 125.0, 225.0),
            projectInkColumns(image),
        )
    }

    @Test
    fun `adaptive threshold uses projection midpoint`() {
        val projection = doubleArrayOf(10.0, 40.0, 90.0)

        assertEquals(50, adaptiveInkThreshold(projection))
    }

    @Test
    fun `threshold creates dark column mask`() {
        val projection = doubleArrayOf(10.0, 40.0, 90.0)

        assertEquals(listOf(false, false, true), thresholdInkProjection(projection, threshold = 50))
    }

    @Test
    fun `extracts runs from active mask`() {
        val mask = listOf(false, true, true, false, true, false, true, true)

        assertEquals(listOf(1..2, 4..4, 6..7), extractRuns(mask))
    }

    @Test
    fun `active span covers first to last black run`() {
        assertEquals(2..10, activeSpanFromBlackRuns(listOf(2..3, 6..7, 9..10)))
        assertNull(activeSpanFromBlackRuns(emptyList()))
    }

    @Test
    fun `internal white gaps are spaces between black runs`() {
        assertEquals(
            listOf(4..5, 8..8),
            internalWhiteGapsBetween(listOf(2..3, 6..7, 9..10)),
        )
    }

    @Test
    fun `ink image rejects out of range ink values`() {
        assertFailsWith<IllegalArgumentException> {
            InkImage(width = 1, height = 1, inkValues = intArrayOf(300))
        }
    }
}
