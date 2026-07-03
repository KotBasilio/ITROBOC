package org.itroboc.app

import org.itroboc.core.BoardEditState
import org.itroboc.core.BoardState
import org.itroboc.core.BoardVulnerability
import org.itroboc.core.CardId
import org.itroboc.core.PbnExportOptions
import org.itroboc.core.PbnExporter
import org.itroboc.core.Seat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TdSessionExchangeTest {
    private val boardOne = boardOf(
        Seat.NORTH to listOf("SA", "SK", "SQ", "SJ", "ST", "H9", "H8", "H7", "D6", "D5", "C4", "C3", "C2"),
        Seat.EAST to listOf("S9", "S8", "S7", "S6", "S5", "HA", "HK", "HQ", "HJ", "HT", "D4", "D3", "CA"),
        Seat.SOUTH to listOf("S4", "S3", "S2", "H6", "H5", "H4", "H3", "H2", "DA", "DK", "DQ", "DJ", "CT"),
        Seat.WEST to listOf("DT", "D9", "D8", "D7", "D2", "CK", "CQ", "CJ", "C9", "C8", "C7", "C6", "C5"),
    )

    private val boardTwo = boardOf(
        Seat.NORTH to listOf("HA", "HK", "HQ", "HJ", "HT", "S9", "S8", "S7", "D6", "D5", "C4", "C3", "C2"),
        Seat.EAST to listOf("H9", "H8", "H7", "H6", "H5", "SA", "SK", "SQ", "SJ", "ST", "D4", "D3", "CA"),
        Seat.SOUTH to listOf("H4", "H3", "H2", "S6", "S5", "S4", "S3", "S2", "DA", "DK", "DQ", "DJ", "CT"),
        Seat.WEST to listOf("DT", "D9", "D8", "D7", "D2", "CK", "CQ", "CJ", "C9", "C8", "C7", "C6", "C5"),
    )

    @Test
    fun `export includes only complete boards in board order`() {
        val sessionState = TdSessionState(
            boards = mapOf(
                3 to BoardEditState(boardNumber = 3, boardState = BoardState()),
                7 to BoardEditState(boardNumber = 7, boardState = boardTwo),
                2 to BoardEditState(boardNumber = 2, boardState = boardOne),
            )
        )

        val exported = TdSessionExchange.exportCompleteBoards(sessionState)

        val expected = listOf(
            PbnExporter.export(
                boardOne,
                PbnExportOptions(
                    boardNumber = 2,
                    dealer = Seat.EAST,
                    vulnerability = BoardVulnerability.NS,
                ),
            ),
            PbnExporter.export(
                boardTwo,
                PbnExportOptions(
                    boardNumber = 7,
                    dealer = Seat.SOUTH,
                    vulnerability = BoardVulnerability.ALL,
                ),
            ),
        ).joinToString(separator = "\n\n")
        assertEquals(expected, exported)
    }

    @Test
    fun `export returns null when no complete boards exist`() {
        val sessionState = TdSessionState(
            boards = mapOf(
                4 to BoardEditState(boardNumber = 4, boardState = BoardState()),
            )
        )

        assertNull(TdSessionExchange.exportCompleteBoards(sessionState))
    }

    @Test
    fun `import ignores empty or partial boards and overwrites complete boards cumulatively`() {
        val existingState = TdSessionState(
            boards = mapOf(
                7 to BoardEditState(
                    boardNumber = 7,
                    boardState = boardOne,
                    selectedSeat = Seat.WEST,
                ),
                9 to BoardEditState(
                    boardNumber = 9,
                    boardState = boardOne,
                    selectedSeat = Seat.SOUTH,
                ),
            )
        )

        val completeBoardSeven = PbnExporter.export(boardTwo, PbnExportOptions(boardNumber = 7))
        val partialBoardEight = """
            [Event "ITROBOC Export"]
            [Site "Local"]
            [Board "8"]
            [Dealer "N"]
            [Deal "N:AKQJT.987.65.432 98765.AKQJT.43.A 432.65432.AKQJ.T ..T9872.KQJ9876"]
        """.trimIndent()
        val completeBoardTwelve = PbnExporter.export(boardTwo, PbnExportOptions(boardNumber = 12))
        val importedText = listOf(
            completeBoardSeven,
            partialBoardEight,
            completeBoardTwelve,
        ).joinToString(separator = "\n\n")

        val result = TdSessionExchange.importCumulative(existingState, importedText)

        assertEquals(listOf(7, 12), result.importedBoardNumbers)
        assertEquals(1, result.ignoredBlockCount)
        assertEquals(boardTwo, result.sessionState.boards.getValue(7).boardState)
        assertEquals(Seat.WEST, result.sessionState.boards.getValue(7).selectedSeat)
        assertEquals(boardTwo, result.sessionState.boards.getValue(12).boardState)
        assertEquals(boardOne, result.sessionState.boards.getValue(9).boardState)
        assertEquals(Seat.SOUTH, result.sessionState.boards.getValue(9).selectedSeat)
    }

    @Test
    fun `export excludes boards outside the grid range`() {
        val sessionState = TdSessionState(
            boards = mapOf(
                31 to BoardEditState(boardNumber = 31, boardState = boardOne),
            ),
            totalBoardsInGrid = 30
        )

        assertNull(TdSessionExchange.exportCompleteBoards(sessionState))
    }

    private fun boardOf(vararg seatCards: Pair<Seat, List<String>>): BoardState =
        seatCards.fold(BoardState()) { board, (seat, cards) ->
            cards.fold(board) { currentBoard, rawCard ->
                currentBoard.addCard(seat, CardId.parse(rawCard))
            }
        }
}
