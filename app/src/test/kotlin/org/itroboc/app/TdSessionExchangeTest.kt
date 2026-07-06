package org.itroboc.app

import org.itroboc.core.BoardEditState
import org.itroboc.core.BoardState
import org.itroboc.core.BoardVulnerability
import org.itroboc.core.CardId
import org.itroboc.core.PbnDoubleDummyData
import org.itroboc.core.PbnExportOptions
import org.itroboc.core.PbnExporter
import org.itroboc.core.Seat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
        val expectedWithHeader = """
% PBN 2.1
% EXPORT
%Content-type: text/x-pbn; charset=ISO-8859-1
%Creator: ITROBOC

$expected
        """.trimIndent()
        assertEquals(expectedWithHeader, exported)
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

    @Test
    fun `import expands grid size based on largest valid board number`() {
        val initialSessionState = TdSessionState(totalBoardsInGrid = 30)
        
        // Board 31 should expand grid to 33
        val board31 = PbnExporter.export(boardOne, PbnExportOptions(boardNumber = 31))
        val result1 = TdSessionExchange.importCumulative(initialSessionState, board31)
        assertEquals(33, result1.sessionState.totalBoardsInGrid)

        // Board 34 should expand grid to 36
        val board34 = PbnExporter.export(boardOne, PbnExportOptions(boardNumber = 34))
        val result2 = TdSessionExchange.importCumulative(initialSessionState, board34)
        assertEquals(36, result2.sessionState.totalBoardsInGrid)

        // Board 39 should expand grid to 39
        val board39 = PbnExporter.export(boardOne, PbnExportOptions(boardNumber = 39))
        val result3 = TdSessionExchange.importCumulative(initialSessionState, board39)
        assertEquals(39, result3.sessionState.totalBoardsInGrid)
    }

    @Test
    fun `import skips invalid board numbers and does not expand grid`() {
        val initialSessionState = TdSessionState(totalBoardsInGrid = 30)
        
        // Board 40 is invalid and should be skipped
        val board40 = PbnExporter.export(boardOne, PbnExportOptions(boardNumber = 40))
        val result1 = TdSessionExchange.importCumulative(initialSessionState, board40)
        assertTrue(result1.importedBoardNumbers.isEmpty())
        assertEquals(30, result1.sessionState.totalBoardsInGrid)
        assertEquals(1, result1.ignoredBlockCount)

        // Board 0 is invalid and should be skipped
        val board0 = PbnExporter.export(boardOne, PbnExportOptions(boardNumber = 0))
        val result2 = TdSessionExchange.importCumulative(initialSessionState, board0)
        assertTrue(result2.importedBoardNumbers.isEmpty())
        assertEquals(30, result2.sessionState.totalBoardsInGrid)
        assertEquals(1, result2.ignoredBlockCount)
    }

    @Test
    fun `mixed import with valid and invalid boards expands only for valid ones`() {
        val initialSessionState = TdSessionState(totalBoardsInGrid = 30)
        
        val board32 = PbnExporter.export(boardOne, PbnExportOptions(boardNumber = 32))
        val board99 = PbnExporter.export(boardOne, PbnExportOptions(boardNumber = 99))
        val importedText = listOf(board32, board99).joinToString(separator = "\n\n")

        val result = TdSessionExchange.importCumulative(initialSessionState, importedText)
        
        assertEquals(listOf(32), result.importedBoardNumbers)
        assertEquals(1, result.ignoredBlockCount)
        assertEquals(33, result.sessionState.totalBoardsInGrid)
    }

    @Test
    fun `import ignores file header and preserves dds fields through export`() {
        val importedText = """
            % PBN 2.1
            % EXPORT
            %Content-type: text/x-pbn; charset=ISO-8859-1
            %Creator: ITROBOC

            [Event "ITROBOC Export"]
            [Board "4"]
            [Dealer "W"]
            [Vulnerable "All"]
            [Deal "W:..T9872.KQJ98765 AKQJT.987.65.432 98765.AKQJT.43.A 432.65432.AKQJ.T"]
            [DoubleDummyTricks "43367433678a9668a966"]
            [OptimumResultTable "Declarer;Denomination\2R;Result\2R"]
            N NT  4
            N  S  3
            W  C  6
            [OptimumScore "EW 4S; -620"]
        """.trimIndent()

        val result = TdSessionExchange.importCumulative(TdSessionState(), importedText)

        assertEquals(listOf(4), result.importedBoardNumbers)
        val importedBoard = result.sessionState.boards.getValue(4)
        assertEquals(
            PbnDoubleDummyData(
                doubleDummyTricks = "43367433678a9668a966",
                optimumResultTableHeader = """Declarer;Denomination\2R;Result\2R""",
                optimumResultTableRows = listOf("N NT  4", "N  S  3", "W  C  6"),
                optimumScore = "EW 4S; -620",
            ),
            importedBoard.pbnDoubleDummyData,
        )

        val exported = TdSessionExchange.exportCompleteBoards(result.sessionState)
        val expectedExport = """
            % PBN 2.1
            % EXPORT
            %Content-type: text/x-pbn; charset=ISO-8859-1
            %Creator: ITROBOC

            [Event "ITROBOC Export"]
            [Board "4"]
            [Dealer "W"]
            [Vulnerable "All"]
            [Deal "W:..T9872.KQJ98765 AKQJT.987.65.432 98765.AKQJT.43.A 432.65432.AKQJ.T"]
            [DoubleDummyTricks "43367433678a9668a966"]
            [OptimumResultTable "Declarer;Denomination\2R;Result\2R"]
            N NT  4
            N  S  3
            W  C  6
            [OptimumScore "EW 4S; -620"]
        """.trimIndent()
        assertEquals(expectedExport, exported)
    }

    @Test
    fun `placeholder dds values survive import and export`() {
        val importedText = """
            [Event "ITROBOC Export"]
            [Board "5"]
            [Dealer "N"]
            [Deal "N:AKQJT.987.65.432 98765.AKQJT.43.A 432.65432.AKQJ.T ..T9872.KQJ98765"]
            [DoubleDummyTricks "********************"]
            [OptimumScore ""]
        """.trimIndent()

        val result = TdSessionExchange.importCumulative(TdSessionState(), importedText)

        assertEquals("********************", result.sessionState.boards.getValue(5).pbnDoubleDummyData?.doubleDummyTricks)
        assertEquals("", result.sessionState.boards.getValue(5).pbnDoubleDummyData?.optimumScore)
        val exported = TdSessionExchange.exportCompleteBoards(result.sessionState)
        assertTrue(exported?.contains("""[DoubleDummyTricks "********************"]""") == true)
        assertTrue(exported?.contains("""[OptimumScore ""]""") == true)
    }

    @Test
    fun `partial block with dds fields is ignored`() {
        val importedText = """
            [Event "ITROBOC Export"]
            [Board "8"]
            [Dealer "N"]
            [Deal "N:AKQJT.987.65.432 98765.AKQJT.43.A 432.65432.AKQJ.T ..T9872.KQJ9876"]
            [DoubleDummyTricks "--------------------"]
            [OptimumScore "NS 1N; 90"]
        """.trimIndent()

        val result = TdSessionExchange.importCumulative(TdSessionState(), importedText)

        assertTrue(result.importedBoardNumbers.isEmpty())
        assertEquals(1, result.ignoredBlockCount)
    }

    @Test
    fun `reimported complete board replaces existing dds metadata`() {
        val existingState = TdSessionState(
            boards = mapOf(
                4 to BoardEditState(
                    boardNumber = 4,
                    boardState = boardOne,
                    pbnDoubleDummyData = PbnDoubleDummyData(
                        doubleDummyTricks = "old",
                        optimumScore = "old score",
                    ),
                ),
            ),
        )
        val importedText = """
            [Event "ITROBOC Export"]
            [Board "4"]
            [Dealer "W"]
            [Vulnerable "All"]
            [Deal "W:..T9872.KQJ98765 AKQJT.987.65.432 98765.AKQJT.43.A 432.65432.AKQJ.T"]
            [DoubleDummyTricks "new"]
            [OptimumScore "new score"]
        """.trimIndent()

        val result = TdSessionExchange.importCumulative(existingState, importedText)

        assertEquals("new", result.sessionState.boards.getValue(4).pbnDoubleDummyData?.doubleDummyTricks)
        assertEquals("new score", result.sessionState.boards.getValue(4).pbnDoubleDummyData?.optimumScore)
    }

    private fun boardOf(vararg seatCards: Pair<Seat, List<String>>): BoardState =
        seatCards.fold(BoardState()) { board, (seat, cards) ->
            cards.fold(board) { currentBoard, rawCard ->
                currentBoard.addCard(seat, CardId.parse(rawCard))
            }
        }
}
