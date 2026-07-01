package org.itroboc.vision

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Grid13SignatureTest {
    @Test
    fun `converts valid thirteen bit input to four digit uppercase hex`() {
        assertEquals("1549", grid13BitsToHexSlow("1010101001001"))
    }

    @Test
    fun `preserves leading zeroes in four digit hex`() {
        assertEquals("0001", grid13BitsToHexSlow("0000000000001"))
        assertEquals("0000", grid13BitsToHexSlow("0000000000000"))
    }

    @Test
    fun `rejects invalid bit length`() {
        assertFailsWith<IllegalArgumentException> {
            grid13BitsToHexSlow("101010100100")
        }
        assertFailsWith<IllegalArgumentException> {
            grid13BitsToHexSlow("10101010010010")
        }
    }

    @Test
    fun `rejects non binary characters`() {
        assertFailsWith<IllegalArgumentException> {
            grid13BitsToHexSlow("101010100100X")
        }
    }

    @Test
    fun `builds forward and reverse meal signatures with fixed prefixes`() {
        assertEquals("bfm1549", forwardMealSignatureSlow("1010101001001"))
        assertEquals("brm1549", reverseMealSignatureSlow("1010101001001"))
    }

    @Test
    fun `reverses bits explicitly`() {
        assertEquals("1001001010101", reverseBitsSlow("1010101001001"))
    }

    @Test
    fun `does not canonicalize forward and reverse orientation`() {
        val forwardBits = "1010101001001"
        val reverseBitsSlowValue = reverseBitsSlow(forwardBits)

        assertEquals("bfm1549", forwardMealSignatureSlow(forwardBits))
        assertEquals("brm1255", reverseMealSignatureSlow(reverseBitsSlowValue))
        assertNotEquals(
            forwardMealSignatureSlow(forwardBits).removePrefix("bfm"),
            reverseMealSignatureSlow(reverseBitsSlowValue).removePrefix("brm"),
        )
    }

    @Test
    fun `accepts valid outer sentinel cells`() {
        val check = checkGrid13SentinelsSlow("1010101001001")

        assertTrue(check.isValid)
        assertTrue(check.bit12)
        assertFalse(check.bit11)
        assertFalse(check.bit1)
        assertTrue(check.bit0)
        assertEquals(emptyList(), check.issues)
    }

    @Test
    fun `reports every invalid outer sentinel cell`() {
        val check = checkGrid13SentinelsSlow("0100000000010")

        assertFalse(check.isValid)
        assertEquals(
            listOf(
                "bit12 must be black",
                "bit11 must be white",
                "bit1 must be white",
                "bit0 must be black",
            ),
            check.issues,
        )
    }

    @Test
    fun `normalizes only the four control cells`() {
        assertEquals(
            "1010101010101",
            normalizeGrid13SentinelsSlow("0110101010110"),
        )
    }

    @Test
    fun `builds exact cell run signature including white run of three`() {
        assertEquals(
            "B1-W1-B2-W3-B2-W1-B1-W1-B1",
            grid13RunLengthSignatureSlow("1011000110101"),
        )
    }

    @Test
    fun `flags invalid raw grid13 candidates before run conversion`() {
        assertTrue(hasInvalidGrid13RunCandidateSlow("1011101001001"))
        assertTrue(hasInvalidGrid13RunCandidateSlow("1000010101001"))
        assertFalse(hasInvalidGrid13RunCandidateSlow("1010101001001"))
    }
}
