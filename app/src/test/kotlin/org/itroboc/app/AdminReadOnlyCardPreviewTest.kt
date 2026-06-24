package org.itroboc.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdminReadOnlyCardPreviewTest {
    @Test
    fun `converts compact Grid13 token to thirteen visual cells`() {
        assertEquals("1010101001001", grid13BitsFromToken("bfm1549"))
        assertEquals("1001001010101", grid13BitsFromToken("brm1255"))
    }

    @Test
    fun `rejects malformed and out of range aliases`() {
        assertNull(grid13BitsFromToken("bfmXYZ1"))
        assertNull(grid13BitsFromToken("0x1549"))
        assertNull(grid13BitsFromToken("bfmFFFF"))
    }

    @Test
    fun `selects orientation aliases independently`() {
        val aliases = grid13CardAliases(
            listOf("0x1001", "brm1255", "bfm1549", "bfmFFFF"),
        )

        assertEquals("bfm1549", aliases.bfm)
        assertEquals("brm1255", aliases.brm)
        assertTrue(aliases.hasData)
    }

    @Test
    fun `reports no visual data without a valid Grid13 alias`() {
        assertFalse(grid13CardAliases(listOf("0x1001")).hasData)
    }
}
