package org.itroboc.core

import kotlin.test.Test
import kotlin.test.assertEquals

class HandProgressSummaryTest {
    @Test
    fun `summarizes hand count completion and suit grouped cards`() {
        val hand = listOf("SK", "SA", "H8", "H9", "D5", "C2", "C4")
            .fold(HandState()) { current, rawCard -> current.addCard(CardId.parse(rawCard)) }

        val summary = HandProgressSummary.from(Seat.NORTH, hand)

        assertEquals(Seat.NORTH, summary.seat)
        assertEquals(7, summary.currentCount)
        assertEquals(6, summary.remainingCount)
        assertEquals(false, summary.isComplete)
        assertEquals(
            listOf("SA", "SK"),
            summary.cardsBySuit.first { it.suit == Suit.SPADES }.cards.map { it.toString() },
        )
        assertEquals(
            listOf("H9", "H8"),
            summary.cardsBySuit.first { it.suit == Suit.HEARTS }.cards.map { it.toString() },
        )
        assertEquals(
            listOf("D5"),
            summary.cardsBySuit.first { it.suit == Suit.DIAMONDS }.cards.map { it.toString() },
        )
        assertEquals(
            listOf("C4", "C2"),
            summary.cardsBySuit.first { it.suit == Suit.CLUBS }.cards.map { it.toString() },
        )
    }

    @Test
    fun `marks a full hand as complete`() {
        val hand = listOf(
            "SA", "SK", "SQ", "SJ", "ST",
            "H9", "H8", "H7",
            "D6", "D5",
            "C4", "C3", "C2",
        ).fold(HandState()) { current, rawCard -> current.addCard(CardId.parse(rawCard)) }

        val summary = HandProgressSummary.from(Seat.WEST, hand)

        assertEquals(13, summary.currentCount)
        assertEquals(0, summary.remainingCount)
        assertEquals(true, summary.isComplete)
    }
}
