package org.itroboc.core

import kotlin.test.Test
import kotlin.test.assertEquals

class PbnExporterTest {
    private val board = boardOf(
        Seat.NORTH to listOf("SA", "SK", "SQ", "SJ", "ST", "H9", "H8", "H7", "D6", "D5", "C4", "C3", "C2"),
        Seat.EAST to listOf("S9", "S8", "S7", "S6", "S5", "HA", "HK", "HQ", "HJ", "HT", "D4", "D3", "CA"),
        Seat.SOUTH to listOf("S4", "S3", "S2", "H6", "H5", "H4", "H3", "H2", "DA", "DK", "DQ", "DJ", "CT"),
        Seat.WEST to listOf("DT", "D9", "D8", "D7", "D2", "CK", "CQ", "CJ", "C9", "C8", "C7", "C6", "C5"),
    )

    @Test
    fun `exports a simple pbn deal for a complete board`() {
        val expected = """
            [Event "ITROBOC Export"]
            [Dealer "N"]
            [Deal "N:AKQJT.987.65.432 98765.AKQJT.43.A 432.65432.AKQJ.T ..T9872.KQJ98765"]
        """.trimIndent()

        assertEquals(expected, PbnExporter.export(board))
    }

    @Test
    fun `exports pbn with optional metadata dealer rotation and vulnerability`() {
        val expected = """
            [Event "Club Duplicate"]
            [Board "12"]
            [Dealer "E"]
            [Vulnerable "NS"]
            [Deal "E:98765.AKQJT.43.A 432.65432.AKQJ.T ..T9872.KQJ98765 AKQJT.987.65.432"]
        """.trimIndent()

        val actual = PbnExporter.export(
            board,
            PbnExportOptions(
                boardNumber = 12,
                event = "Club Duplicate",
                site = "Budapest",
                dealer = Seat.EAST,
                vulnerability = BoardVulnerability.NS,
            ),
        )

        assertEquals(expected, actual)
    }

    @Test
    fun `exports board level dds metadata without cumulative header`() {
        val expected = """
            [Event "ITROBOC Export"]
            [Board "4"]
            [Dealer "W"]
            [Vulnerable "All"]
            [Deal "W:..T9872.KQJ98765 AKQJT.987.65.432 98765.AKQJT.43.A 432.65432.AKQJ.T"]
            [DoubleDummyTricks "43367433678a9668a966"]
            [OptimumResultTable "Declarer;Denomination\2R;Result\2R"]
            N NT  4
            W  C  6
            [OptimumScore "EW 4S; -620"]
        """.trimIndent()

        val actual = PbnExporter.export(
            board,
            PbnExportOptions(
                boardNumber = 4,
                dealer = Seat.WEST,
                vulnerability = BoardVulnerability.ALL,
                pbnDoubleDummyData = PbnDoubleDummyData(
                    doubleDummyTricks = "43367433678a9668a966",
                    optimumResultTableHeader = """Declarer;Denomination\2R;Result\2R""",
                    optimumResultTableRows = listOf("N NT  4", "W  C  6"),
                    optimumScore = "EW 4S; -620",
                ),
            ),
        )

        assertEquals(expected, actual)
    }
}
