package org.itroboc.core

data class DuplicateBoardMetadata(
    val dealer: Seat,
    val vulnerability: BoardVulnerability,
) {
    companion object {
        fun forBoardNumber(boardNumber: Int): DuplicateBoardMetadata {
            require(boardNumber > 0) { "Board number must be positive: $boardNumber" }

            val index = (boardNumber - 1) % 16
            return DuplicateBoardMetadata(
                dealer = Seat.entries[index % Seat.entries.size],
                vulnerability = VULNERABILITY_CYCLE[index],
            )
        }

        private val VULNERABILITY_CYCLE = listOf(
            BoardVulnerability.NONE,
            BoardVulnerability.NS,
            BoardVulnerability.EW,
            BoardVulnerability.ALL,
            BoardVulnerability.NS,
            BoardVulnerability.EW,
            BoardVulnerability.ALL,
            BoardVulnerability.NONE,
            BoardVulnerability.EW,
            BoardVulnerability.ALL,
            BoardVulnerability.NONE,
            BoardVulnerability.NS,
            BoardVulnerability.ALL,
            BoardVulnerability.NONE,
            BoardVulnerability.NS,
            BoardVulnerability.EW,
        )
    }
}

enum class BoardVulnerability(val pbnValue: String) {
    NONE("None"),
    NS("NS"),
    EW("EW"),
    ALL("All"),
}
