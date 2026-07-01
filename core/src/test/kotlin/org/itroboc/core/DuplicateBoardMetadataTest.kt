package org.itroboc.core

import kotlin.test.Test
import kotlin.test.assertEquals

class DuplicateBoardMetadataTest {
    @Test
    fun `duplicate board metadata follows standard 16 board cycle`() {
        val expected = listOf(
            1 to (Seat.NORTH to BoardVulnerability.NONE),
            2 to (Seat.EAST to BoardVulnerability.NS),
            3 to (Seat.SOUTH to BoardVulnerability.EW),
            4 to (Seat.WEST to BoardVulnerability.ALL),
            5 to (Seat.NORTH to BoardVulnerability.NS),
            6 to (Seat.EAST to BoardVulnerability.EW),
            7 to (Seat.SOUTH to BoardVulnerability.ALL),
            8 to (Seat.WEST to BoardVulnerability.NONE),
            9 to (Seat.NORTH to BoardVulnerability.EW),
            10 to (Seat.EAST to BoardVulnerability.ALL),
            11 to (Seat.SOUTH to BoardVulnerability.NONE),
            12 to (Seat.WEST to BoardVulnerability.NS),
            13 to (Seat.NORTH to BoardVulnerability.ALL),
            14 to (Seat.EAST to BoardVulnerability.NONE),
            15 to (Seat.SOUTH to BoardVulnerability.NS),
            16 to (Seat.WEST to BoardVulnerability.EW),
            17 to (Seat.NORTH to BoardVulnerability.NONE),
        )

        expected.forEach { (boardNumber, expectedPair) ->
            val actual = DuplicateBoardMetadata.forBoardNumber(boardNumber)
            assertEquals(expectedPair.first, actual.dealer, "dealer for board $boardNumber")
            assertEquals(expectedPair.second, actual.vulnerability, "vulnerability for board $boardNumber")
        }
    }
}
