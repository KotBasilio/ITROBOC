package org.itroboc.app

import org.itroboc.core.BoardProgressSummary
import org.itroboc.core.BoardEditState
import org.itroboc.core.BoardState
import org.itroboc.core.CardId
import org.itroboc.core.DuplicateBoardMetadata
import org.itroboc.core.HandState
import org.itroboc.core.PbnExportOptions
import org.itroboc.core.PbnDoubleDummyData
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
        return exportBoards(sessionState, allowPartial = false)
    }

    fun exportBoards(
        sessionState: TdSessionState,
        allowPartial: Boolean,
    ): String? {
        val exportedBoards = sessionState.boards.values
            .asSequence()
            .filter { it.boardNumber <= sessionState.totalBoardsInGrid }
            .sortedBy { it.boardNumber }
            .mapNotNull { boardEditState ->
                if (!allowPartial && !BoardProgressSummary.from(boardEditState.boardState).boardComplete) {
                    return@mapNotNull null
                }
                val metadata = DuplicateBoardMetadata.forBoardNumber(boardEditState.boardNumber)
                PbnExporter.export(
                    boardEditState.boardState,
                    PbnExportOptions(
                        boardNumber = boardEditState.boardNumber,
                        dealer = metadata.dealer,
                        vulnerability = metadata.vulnerability,
                        pbnDoubleDummyData = boardEditState.pbnDoubleDummyData,
                        allowPartial = allowPartial,
                    ),
                )
            }
            .toList()

        return exportedBoards.takeIf { it.isNotEmpty() }?.let { boards ->
            buildString {
                appendLine("% PBN 2.1")
                appendLine("% EXPORT")
                appendLine("%Content-type: text/x-pbn; charset=ISO-8859-1")
                appendLine("%Creator: ITROBOC")
                appendLine()
                append(boards.joinToString(separator = "\n\n"))
            }
        }
    }

    fun importCumulative(
        sessionState: TdSessionState,
        rawText: String,
        allowPartial: Boolean = false,
    ): TdSessionImportResult {
        val parsedBlocks = parseTaggedBlocks(rawText)
        var mergedState = sessionState
        val importedBoardNumbers = mutableListOf<Int>()
        var ignoredBlockCount = 0
        var maxValidBoardNumber = sessionState.totalBoardsInGrid

        parsedBlocks.forEach { block ->
            val importedBoard = parseBoard(block, allowPartial)
            if (importedBoard == null || !isSupportedTdBoardNumber(importedBoard.boardNumber)) {
                ignoredBlockCount += 1
                return@forEach
            }

            val selectedSeat = sessionState.boards[importedBoard.boardNumber]?.selectedSeat ?: Seat.NORTH
            mergedState = mergedState.updateBoard(
                BoardEditState(
                    boardNumber = importedBoard.boardNumber,
                    boardState = importedBoard.boardState,
                    selectedSeat = selectedSeat,
                    pbnDoubleDummyData = importedBoard.pbnDoubleDummyData,
                )
            )
            importedBoardNumbers += importedBoard.boardNumber
            if (importedBoard.boardNumber > maxValidBoardNumber) {
                maxValidBoardNumber = importedBoard.boardNumber
            }
        }

        val finalGridSize = TdSessionState.ALLOWED_GRID_SIZES.firstOrNull { it >= maxValidBoardNumber }
            ?: TdSessionState.ALLOWED_GRID_SIZES.last()
        
        if (finalGridSize > mergedState.totalBoardsInGrid) {
            mergedState = mergedState.updateGridSize(finalGridSize)
        }

        return TdSessionImportResult(
            sessionState = mergedState,
            importedBoardNumbers = importedBoardNumbers.distinct(),
            ignoredBlockCount = ignoredBlockCount,
        )
    }

    private fun isSupportedTdBoardNumber(boardNumber: Int): Boolean =
        boardNumber in 1..39

    private fun parseTaggedBlocks(rawText: String): List<List<String>> {
        val blocks = mutableListOf<List<String>>()
        val currentBlock = mutableListOf<String>()

        rawText.lineSequence()
            .map { it.trimEnd() }
            .forEach { line ->
                if (line.isBlank()) {
                    if (currentBlock.isNotEmpty()) {
                        blocks += currentBlock.toList()
                        currentBlock.clear()
                    }
                } else {
                    currentBlock += line
                }
            }

        if (currentBlock.isNotEmpty()) {
            blocks += currentBlock.toList()
        }

        return blocks.filter { block -> block.any { it.trimStart().startsWith("[") } }
    }

    private fun parseBoard(lines: List<String>, allowPartial: Boolean): ImportedBoard? {
        val tags = lines.mapNotNull { parseTag(it.trim()) }.toMap()
        val boardNumber = tags["Board"]?.toIntOrNull() ?: return null
        val dealer = tags["Dealer"]?.singleOrNull()?.let(::seatFromSymbol) ?: return null
        val deal = tags["Deal"] ?: return null
        val pbnDoubleDummyData = parsePbnDoubleDummyData(lines)

        return runCatching {
            ImportedBoard(
                boardNumber = boardNumber,
                boardState = parseBoardState(dealer = dealer, deal = deal, allowPartial = allowPartial),
                pbnDoubleDummyData = pbnDoubleDummyData,
            )
        }.getOrNull()
    }

    private fun parseTag(line: String): Pair<String, String>? {
        val match = TAG_REGEX.matchEntire(line) ?: return null
        return match.groupValues[1] to match.groupValues[2]
    }

    private fun parsePbnDoubleDummyData(lines: List<String>): PbnDoubleDummyData? {
        var doubleDummyTricks: String? = null
        var optimumResultTableHeader: String? = null
        var optimumResultTableRows: List<String> = emptyList()
        var optimumScore: String? = null

        var index = 0
        while (index < lines.size) {
            val line = lines[index].trim()
            val tag = parseTag(line)
            when (tag?.first) {
                "DoubleDummyTricks" -> doubleDummyTricks = tag.second
                "OptimumResultTable" -> {
                    optimumResultTableHeader = tag.second
                    val rows = mutableListOf<String>()
                    var rowIndex = index + 1
                    while (rowIndex < lines.size) {
                        val rowLine = lines[rowIndex].trimEnd()
                        if (rowLine.isBlank() || rowLine.trimStart().startsWith("[") || rowLine.trimStart().startsWith("%")) {
                            break
                        }
                        rows += rowLine.trim()
                        rowIndex += 1
                    }
                    optimumResultTableRows = rows
                    index = rowIndex - 1
                }
                "OptimumScore" -> optimumScore = tag.second
            }
            index += 1
        }

        if (doubleDummyTricks == null && optimumResultTableHeader == null && optimumScore == null) {
            return null
        }

        return PbnDoubleDummyData(
            doubleDummyTricks = doubleDummyTricks,
            optimumResultTableHeader = optimumResultTableHeader,
            optimumResultTableRows = optimumResultTableRows,
            optimumScore = optimumScore,
        )
    }

    private fun parseBoardState(
        dealer: Seat,
        deal: String,
        allowPartial: Boolean,
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
            parseHand(handString, allowPartial).cards().forEach { card ->
                boardState = boardState.addCard(seat, card)
            }
        }
        if (!allowPartial) {
            boardState.validateComplete()
        }
        return boardState
    }

    private fun parseHand(handString: String, allowPartial: Boolean): HandState {
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
        if (!allowPartial) {
            handState.validateComplete()
        }
        return handState
    }

    private fun seatFromSymbol(symbol: Char): Seat =
        Seat.entries.firstOrNull { it.symbol == symbol }
            ?: throw IllegalArgumentException("Unknown seat symbol: $symbol")

    private data class ImportedBoard(
        val boardNumber: Int,
        val boardState: BoardState,
        val pbnDoubleDummyData: PbnDoubleDummyData?,
    )

    private val TAG_REGEX = Regex("""\[(\w+)\s+"([^"]*)"]""")
    private val WHITESPACE_REGEX = Regex("""\s+""")
}

internal data class TdSessionImportResult(
    val sessionState: TdSessionState,
    val importedBoardNumbers: List<Int>,
    val ignoredBlockCount: Int,
)
