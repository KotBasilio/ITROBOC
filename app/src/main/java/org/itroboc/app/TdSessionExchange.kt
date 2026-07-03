package org.itroboc.app

import org.itroboc.core.BoardProgressSummary
import org.itroboc.core.BoardEditState
import org.itroboc.core.BoardState
import org.itroboc.core.CardId
import org.itroboc.core.DuplicateBoardMetadata
import org.itroboc.core.HandState
import org.itroboc.core.PbnExportOptions
import org.itroboc.core.PbnExporter
import org.itroboc.core.Rank
import org.itroboc.core.Seat
import org.itroboc.core.Suit

internal object TdSessionExchange {
    fun exportCompleteBoard(
        boardState: BoardState,
        boardNumber: Int,
    ): String {
        val metadata = DuplicateBoardMetadata.forBoardNumber(boardNumber)
        return PbnExporter.export(
            boardState,
            PbnExportOptions(
                boardNumber = boardNumber,
                dealer = metadata.dealer,
                vulnerability = metadata.vulnerability,
            ),
        )
    }

    fun exportCompleteBoards(sessionState: TdSessionState): String? {
        val exportedBoards = sessionState.boards.values
            .asSequence()
            .filter { it.boardNumber <= sessionState.totalBoardsInGrid }
            .sortedBy { it.boardNumber }
            .mapNotNull { boardEditState ->
                if (!BoardProgressSummary.from(boardEditState.boardState).boardComplete) {
                    return@mapNotNull null
                }
                exportCompleteBoard(
                    boardState = boardEditState.boardState,
                    boardNumber = boardEditState.boardNumber,
                )
            }
            .toList()

        return exportedBoards.takeIf { it.isNotEmpty() }?.joinToString(separator = "\n\n")
    }

    fun importCumulative(
        sessionState: TdSessionState,
        rawText: String,
    ): TdSessionImportResult {
        val parsedBoards = parseTaggedBlocks(rawText)
        var mergedState = sessionState
        val importedBoardNumbers = mutableListOf<Int>()
        var ignoredBlockCount = 0

        parsedBoards.forEach { block ->
            val importedBoard = parseCompleteBoard(block)
            if (importedBoard == null) {
                ignoredBlockCount += 1
                return@forEach
            }

            val selectedSeat = sessionState.boards[importedBoard.boardNumber]?.selectedSeat ?: Seat.NORTH
            mergedState = mergedState.updateBoard(
                BoardEditState(
                    boardNumber = importedBoard.boardNumber,
                    boardState = importedBoard.boardState,
                    selectedSeat = selectedSeat,
                )
            )
            importedBoardNumbers += importedBoard.boardNumber
        }

        return TdSessionImportResult(
            sessionState = mergedState,
            importedBoardNumbers = importedBoardNumbers.distinct(),
            ignoredBlockCount = ignoredBlockCount,
        )
    }

    private fun parseTaggedBlocks(rawText: String): List<List<String>> {
        val blocks = mutableListOf<MutableList<String>>()
        var currentBlock = mutableListOf<String>()

        rawText.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (!line.startsWith("[") || !line.endsWith("]")) {
                if (currentBlock.isNotEmpty()) {
                    blocks += currentBlock
                    currentBlock = mutableListOf()
                }
                return@forEach
            }

            if (line.startsWith("[Board ") && currentBlock.any { it.startsWith("[Deal ") }) {
                blocks += currentBlock
                currentBlock = mutableListOf()
            }

            currentBlock += line
        }

        if (currentBlock.isNotEmpty()) {
            blocks += currentBlock
        }

        return blocks
    }

    private fun parseCompleteBoard(lines: List<String>): ImportedBoard? {
        val tags = lines.mapNotNull(::parseTag).toMap()
        val boardNumber = tags["Board"]?.toIntOrNull() ?: return null
        val dealer = tags["Dealer"]?.singleOrNull()?.let(::seatFromSymbol) ?: return null
        val deal = tags["Deal"] ?: return null

        return runCatching {
            ImportedBoard(
                boardNumber = boardNumber,
                boardState = parseCompleteBoardState(dealer = dealer, deal = deal),
            )
        }.getOrNull()
    }

    private fun parseTag(line: String): Pair<String, String>? {
        val match = TAG_REGEX.matchEntire(line) ?: return null
        return match.groupValues[1] to match.groupValues[2]
    }

    private fun parseCompleteBoardState(
        dealer: Seat,
        deal: String,
    ): BoardState {
        val separatorIndex = deal.indexOf(':')
        require(separatorIndex >= 0) { "PBN deal must contain seat prefix" }
        val dealDealer = seatFromSymbol(deal.first())
        require(dealDealer == dealer) { "Dealer tag and deal prefix must match" }

        val handStrings = deal.substring(separatorIndex + 1).trim().split(WHITESPACE_REGEX)
        require(handStrings.size == Seat.entries.size) {
            "PBN deal must contain four hands"
        }

        val orderedSeats = generateSequence(dealer) { it.next() }
            .take(Seat.entries.size)
            .toList()

        var boardState = BoardState()
        orderedSeats.zip(handStrings).forEach { (seat, handString) ->
            parseHand(handString).cards().forEach { card ->
                boardState = boardState.addCard(seat, card)
            }
        }
        boardState.validateComplete()
        return boardState
    }

    private fun parseHand(handString: String): HandState {
        val suitStrings = handString.split(".")
        require(suitStrings.size == Suit.entries.size) {
            "PBN hand must contain four suits"
        }

        var handState = HandState()
        Suit.entries.zip(suitStrings).forEach { (suit, ranks) ->
            ranks.forEach { rankSymbol ->
                handState = handState.addCard(CardId(suit, Rank.fromSymbol(rankSymbol)))
            }
        }
        handState.validateComplete()
        return handState
    }

    private fun seatFromSymbol(symbol: Char): Seat =
        Seat.entries.firstOrNull { it.symbol == symbol }
            ?: throw IllegalArgumentException("Unknown seat symbol: $symbol")

    private data class ImportedBoard(
        val boardNumber: Int,
        val boardState: BoardState,
    )

    private val TAG_REGEX = Regex("""\[(\w+)\s+"([^"]*)"]""")
    private val WHITESPACE_REGEX = Regex("""\s+""")
}

internal data class TdSessionImportResult(
    val sessionState: TdSessionState,
    val importedBoardNumbers: List<Int>,
    val ignoredBlockCount: Int,
)
