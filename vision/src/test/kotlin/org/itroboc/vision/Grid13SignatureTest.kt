package org.itroboc.vision

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class Grid13SignatureTest {
    @Test
    fun `converts valid thirteen bit input to four digit uppercase hex`() {
        assertEquals("1549", grid13BitsToHex("1010101001001"))
    }

    @Test
    fun `preserves leading zeroes in four digit hex`() {
        assertEquals("0001", grid13BitsToHex("0000000000001"))
        assertEquals("0000", grid13BitsToHex("0000000000000"))
    }

    @Test
    fun `rejects invalid bit length`() {
        assertFailsWith<IllegalArgumentException> {
            grid13BitsToHex("101010100100")
        }
        assertFailsWith<IllegalArgumentException> {
            grid13BitsToHex("10101010010010")
        }
    }

    @Test
    fun `rejects non binary characters`() {
        assertFailsWith<IllegalArgumentException> {
            grid13BitsToHex("101010100100X")
        }
    }

    @Test
    fun `builds forward and reverse meal signatures with fixed prefixes`() {
        assertEquals("bfm1549", forwardMealSignature("1010101001001"))
        assertEquals("brm1549", reverseMealSignature("1010101001001"))
    }

    @Test
    fun `reverses bits explicitly`() {
        assertEquals("1001001010101", reverseBits("1010101001001"))
    }

    @Test
    fun `does not canonicalize forward and reverse orientation`() {
        val forwardBits = "1010101001001"
        val reverseBitsValue = reverseBits(forwardBits)

        assertEquals("bfm1549", forwardMealSignature(forwardBits))
        assertEquals("brm1255", reverseMealSignature(reverseBitsValue))
        assertNotEquals(
            forwardMealSignature(forwardBits).removePrefix("bfm"),
            reverseMealSignature(reverseBitsValue).removePrefix("brm"),
        )
    }
}
