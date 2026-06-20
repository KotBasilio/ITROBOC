package org.itroboc.core

import kotlin.test.Test
import kotlin.test.assertEquals

class BoardProgressSummaryTest {
    @Test
    fun `summarizes board counts and complete seats`() {
        val board = boardOf(
            Seat.NORTH to listOf("SA", "SK"),
            Seat.EAST to listOf("HA"),
            Seat.SOUTH to emptyList(),
            Seat.WEST to listOf(
                "SQ", "SJ", "ST", "S9", "S8",
                "H9", "H8", "H7",
                "D6", "D5",
                "C4", "C3", "C2",
            ),
        )

        val summary = BoardProgressSummary.from(board)

        assertEquals(2, summary.handCounts.getValue(Seat.NORTH))
        assertEquals(1, summary.handCounts.getValue(Seat.EAST))
        assertEquals(0, summary.handCounts.getValue(Seat.SOUTH))
        assertEquals(13, summary.handCounts.getValue(Seat.WEST))
        assertEquals(16, summary.totalCards)
        assertEquals(setOf(Seat.WEST), summary.completeSeats)
        assertEquals(false, summary.boardComplete)
        assertEquals(0, summary.duplicateConflictCount)
    }

    @Test
    fun `counts duplicate conflicts from recent scan reports when available`() {
        val board = boardOf(Seat.NORTH to listOf("SA"))
        val reports = listOf(
            TdScanReport(
                accumulator = TdScanAccumulator(DeckProfile(emptyMap())),
                result = TdScanResult.AlreadyOnBoard(CardId.parse("SA"), Seat.NORTH),
            ),
            TdScanReport(
                accumulator = TdScanAccumulator(DeckProfile(emptyMap())),
                result = TdScanResult.UnknownSignature("sig-missing"),
            ),
            TdScanReport(
                accumulator = TdScanAccumulator(DeckProfile(emptyMap())),
                result = TdScanResult.AlreadyOnBoard(CardId.parse("HK"), Seat.EAST),
            ),
        )

        val summary = BoardProgressSummary.from(board, reports)

        assertEquals(2, summary.duplicateConflictCount)
    }

    @Test
    fun `marks a complete board as complete`() {
        val summary = BoardProgressSummary.from(
            boardOf(
                Seat.NORTH to listOf("SA", "SK", "SQ", "SJ", "ST", "H9", "H8", "H7", "D6", "D5", "C4", "C3", "C2"),
                Seat.EAST to listOf("S9", "S8", "S7", "S6", "S5", "HA", "HK", "HQ", "HJ", "HT", "D4", "D3", "CA"),
                Seat.SOUTH to listOf("S4", "S3", "S2", "H6", "H5", "H4", "H3", "H2", "DA", "DK", "DQ", "DJ", "CT"),
                Seat.WEST to listOf("DT", "D9", "D8", "D7", "D2", "CK", "CQ", "CJ", "C9", "C8", "C7", "C6", "C5"),
            ),
        )

        assertEquals(true, summary.boardComplete)
        assertEquals(setOf(Seat.NORTH, Seat.EAST, Seat.SOUTH, Seat.WEST), summary.completeSeats)
        assertEquals(52, summary.totalCards)
    }

    @Test
    fun `board is not complete unless all seats are complete`() {
        val board = boardOf(
            Seat.NORTH to listOf("SA", "SK", "SQ", "SJ", "ST", "S9", "S8", "S7", "S6", "S5", "S4", "S3", "S2"),
            Seat.EAST to listOf("HA", "HK", "HQ", "HJ", "HT", "H9", "H8", "H7", "H6", "H5", "H4", "H3"),
            Seat.SOUTH to listOf("DA", "DK", "DQ", "DJ", "DT", "D9", "D8", "D7", "D6", "D5", "D4", "D3", "D2"),
            Seat.WEST to listOf("CA", "CK", "CQ", "CJ", "CT", "C9", "C8", "C7", "C6", "C5", "C4", "C3", "C2"),
        )

        val summary = BoardProgressSummary.from(board)

        assertEquals(51, summary.totalCards)
        assertEquals(setOf(Seat.NORTH, Seat.SOUTH, Seat.WEST), summary.completeSeats)
        assertEquals(false, summary.boardComplete)
    }
}
