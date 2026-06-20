package org.itroboc.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CardIdTest {
    @Test
    fun `parses valid canonical card ids`() {
        assertEquals(CardId(Suit.SPADES, Rank.ACE), CardId.parse("SA"))
        assertEquals(CardId(Suit.CLUBS, Rank.QUEEN), CardId.parse("cq"))
        assertEquals("D8", CardId.parse("D8").toString())
    }

    @Test
    fun `rejects invalid canonical card ids`() {
        assertFailsWith<IllegalArgumentException> { CardId.parse("") }
        assertFailsWith<IllegalArgumentException> { CardId.parse("S10") }
        assertFailsWith<IllegalArgumentException> { CardId.parse("XQ") }
        assertFailsWith<IllegalArgumentException> { CardId.parse("C1") }
    }
}
