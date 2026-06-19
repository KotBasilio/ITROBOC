package org.itroboc.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BoardStateTest {
    @Test
    fun `rejects duplicate cards across a board`() {
        assertFailsWith<IllegalArgumentException> {
            BoardState()
                .addCard(Seat.NORTH, CardId.parse("SA"))
                .addCard(Seat.EAST, CardId.parse("SA"))
        }
    }

    @Test
    fun `validates a complete board`() {
        val board = completeBoard()

        board.validateComplete()

        assertEquals(52, board.totalCardCount())
        assertEquals(13, board.handOf(Seat.WEST).count())
    }

    private fun completeBoard(): BoardState =
        boardOf(
            Seat.NORTH to listOf("SA", "SK", "SQ", "SJ", "ST", "H9", "H8", "H7", "D6", "D5", "C4", "C3", "C2"),
            Seat.EAST to listOf("S9", "S8", "S7", "S6", "S5", "HA", "HK", "HQ", "HJ", "HT", "D4", "D3", "CA"),
            Seat.SOUTH to listOf("S4", "S3", "S2", "H6", "H5", "H4", "H3", "H2", "DA", "DK", "DQ", "DJ", "CT"),
            Seat.WEST to listOf("DT", "D9", "D8", "D7", "D2", "CK", "CQ", "CJ", "C9", "C8", "C7", "C6", "C5"),
        )
}
