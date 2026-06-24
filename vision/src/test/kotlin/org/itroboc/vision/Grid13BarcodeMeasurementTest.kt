package org.itroboc.vision

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Grid13BarcodeMeasurementTest {
    @Test
    fun `measures projection into runs span grid13 and signatures`() {
        val projection = doubleArrayOf(
            0.0,
            100.0, 100.0,
            0.0,
            100.0, 100.0,
            0.0,
            100.0, 100.0,
            0.0,
        )

        val measurement = assertNotNull(measureGrid13BarcodeProjection(projection, threshold = 50))

        assertEquals(listOf(2, 2, 2), measurement.blackRunsPx)
        assertEquals(listOf(1, 1), measurement.whiteGapsPx)
        assertEquals(listOf(1..2, 4..5, 7..8), measurement.blackRunEdges)
        assertEquals(1, measurement.activeStartX)
        assertEquals(8, measurement.activeEndX)
        assertEquals(8, measurement.activeSpanPx)
        assertEquals(13, measurement.grid13FwdBits.length)
        assertEquals(grid13RunLengthSignature(measurement.grid13FwdBits), measurement.rl2)
        assertEquals(reverseBits(measurement.grid13FwdBits), measurement.grid13RevBits)
        assertTrue(measurement.rawSignature.matches(Regex("^bfm[0-9A-F]{4}$")))
        assertTrue(measurement.reverseSignature.matches(Regex("^brm[0-9A-F]{4}$")))
    }

    @Test
    fun `returns null for flat projection`() {
        assertNull(measureGrid13BarcodeProjection(doubleArrayOf(0.0, 0.0, 0.0), threshold = 0))
    }

    @Test
    fun `ignores tiny edge artifacts before active barcode span`() {
        val projection = doubleArrayOf(
            100.0,
            0.0, 0.0,
            100.0, 100.0,
            0.0,
            100.0, 100.0,
        )

        val measurement = assertNotNull(measureGrid13BarcodeProjection(projection, threshold = 50))

        assertEquals(listOf(3..4, 6..7), measurement.blackRunEdges)
        assertEquals(3, measurement.activeStartX)
    }

    @Test
    fun `computes grid13 bits over active span cells`() {
        val projection = doubleArrayOf(
            100.0, 0.0, 100.0, 0.0, 100.0, 0.0, 100.0,
            0.0, 100.0, 0.0, 100.0, 0.0, 100.0,
        )

        assertEquals(
            "1010101010101",
            grid13BitsFromProjection(
                projection = projection,
                activeSpan = 0..12,
                threshold = 50,
            ),
        )
    }
}
