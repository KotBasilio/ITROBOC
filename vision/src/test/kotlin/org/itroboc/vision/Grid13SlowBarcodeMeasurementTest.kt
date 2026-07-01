package org.itroboc.vision

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Grid13SlowBarcodeMeasurementTest {
    @Test
    fun `measures projection into runs span grid13 and signatures`() {
        val projection = doubleArrayOf(
            0.0,
            100.0, 100.0,
            0.0, 0.0,
            100.0, 100.0,
            0.0, 0.0,
            100.0, 100.0,
            0.0, 0.0,
            100.0, 100.0,
            0.0, 0.0, 0.0, 0.0,
            100.0, 100.0,
            0.0, 0.0, 0.0, 0.0,
            100.0, 100.0,
            0.0,
        )

        val measurement = assertNotNull(measureGrid13SlowBarcodeProjection(projection, threshold = 50))

        assertEquals(listOf(2, 2, 2, 2, 2, 2), measurement.blackRunsPx)
        assertEquals(listOf(2, 2, 2, 4, 4), measurement.whiteGapsPx)
        assertEquals(listOf(1..2, 5..6, 9..10, 13..14, 19..20, 25..26), measurement.blackRunEdges)
        assertEquals(1, measurement.activeStartX)
        assertEquals(26, measurement.activeEndX)
        assertEquals(26, measurement.activeSpanPx)
        assertEquals(13, measurement.grid13FwdBits.length)
        assertEquals("1010101001001", measurement.grid13FwdBits)
        assertEquals(grid13RunLengthSignature(measurement.grid13FwdBits), measurement.rl2)
        assertEquals(reverseBits(measurement.grid13FwdBits), measurement.grid13RevBits)
        assertTrue(measurement.rawSignature.matches(Regex("^bfm[0-9A-F]{4}$")))
        assertTrue(measurement.reverseSignature.matches(Regex("^brm[0-9A-F]{4}$")))
    }

    @Test
    fun `returns null for flat projection`() {
        assertNull(measureGrid13SlowBarcodeProjection(doubleArrayOf(0.0, 0.0, 0.0), threshold = 0))
    }

    @Test
    fun `ignores tiny edge artifacts before active barcode span`() {
        val projection = doubleArrayOf(
            100.0,
            0.0, 0.0, 0.0,
            100.0, 100.0,
            0.0, 0.0,
            100.0, 100.0,
            0.0, 0.0,
            100.0, 100.0,
            0.0, 0.0,
            100.0, 100.0,
            0.0, 0.0, 0.0, 0.0,
            100.0, 100.0,
            0.0, 0.0, 0.0, 0.0,
            100.0, 100.0,
        )

        val measurement = assertNotNull(measureGrid13SlowBarcodeProjection(projection, threshold = 50))

        assertEquals(listOf(4..5, 8..9, 12..13, 16..17, 22..23, 28..29), measurement.blackRunEdges)
        assertEquals(4, measurement.activeStartX)
    }

    @Test
    fun `computes grid13 bits over active span cells`() {
        val projection = doubleArrayOf(
            100.0, 0.0, 100.0, 0.0, 100.0, 0.0, 100.0,
            0.0, 100.0, 0.0, 100.0, 0.0, 100.0,
        )

        assertEquals(
            "1010101010101",
            grid13SlowBitsFromProjection(
                projection = projection,
                activeSpan = 0..12,
                threshold = 50,
            ),
        )
    }

    @Test
    fun `rejects raw grid13 candidates containing black triple or white quadruple runs`() {
        assertNull(
            measureGrid13SlowBarcodeProjection(
                projection = doubleArrayOf(
                    100.0, 100.0,
                    0.0, 0.0,
                    100.0, 100.0,
                    100.0, 100.0,
                    100.0, 100.0,
                    0.0, 0.0,
                    100.0, 100.0,
                    0.0, 0.0,
                    100.0, 100.0,
                    0.0, 0.0,
                    0.0, 0.0,
                    0.0, 0.0,
                    0.0, 0.0,
                    100.0, 100.0,
                ),
                threshold = 50,
            ),
        )
    }
}
