package org.itroboc.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HandStateTest {
    @Test
    fun `adds cards to a hand`() {
        val hand = HandState()
            .addCard(CardId.parse("SA"))
            .addCard(CardId.parse("HK"))

        assertEquals(2, hand.count())
        assertEquals(true, hand.contains(CardId.parse("SA")))
        assertEquals(true, hand.contains(CardId.parse("HK")))
    }

    @Test
    fun `rejects duplicate cards inside a hand`() {
        assertFailsWith<IllegalArgumentException> {
            HandState()
                .addCard(CardId.parse("SA"))
                .addCard(CardId.parse("SA"))
        }
    }

    @Test
    fun `validates a complete thirteen card hand`() {
        val hand = listOf(
            "SA", "SK", "SQ", "SJ", "ST",
            "H9", "H8", "H7",
            "D6", "D5",
            "C4", "C3", "C2",
        ).fold(HandState()) { acc, rawCard -> acc.addCard(CardId.parse(rawCard)) }

        hand.validateComplete()
        assertEquals(13, hand.count())
    }
}
