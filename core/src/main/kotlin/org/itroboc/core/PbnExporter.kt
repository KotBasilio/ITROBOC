package org.itroboc.core

object PbnExporter {
    fun export(
        boardState: BoardState,
        options: PbnExportOptions = PbnExportOptions(),
    ): String {
        if (!options.allowPartial) {
            boardState.validateComplete()
        }

        val dealValue = boardState.handsInPbnOrder(options.dealer)
            .joinToString(separator = " ") { it.toPbnHand() }

        return buildString {
            options.event?.let { appendLine("[Event \"$it\"]") }
            options.boardNumber?.let { appendLine("[Board \"$it\"]") }
            appendLine("[Dealer \"${options.dealer.symbol}\"]")
            options.vulnerability?.let { appendLine("[Vulnerable \"${it.pbnValue}\"]") }
            if (options.pbnDoubleDummyData == null) {
                append("[Deal \"${options.dealer.symbol}:$dealValue\"]")
            } else {
                appendLine("[Deal \"${options.dealer.symbol}:$dealValue\"]")
                appendPbnDoubleDummyData(options.pbnDoubleDummyData)
            }
        }
    }

    private fun StringBuilder.appendPbnDoubleDummyData(pbnDoubleDummyData: PbnDoubleDummyData) {
        pbnDoubleDummyData.doubleDummyTricks?.let {
            appendLine("[DoubleDummyTricks \"$it\"]")
        }
        pbnDoubleDummyData.optimumResultTableHeader?.let { header ->
            appendLine("[OptimumResultTable \"$header\"]")
            pbnDoubleDummyData.optimumResultTableRows.forEach(::appendLine)
        }
        pbnDoubleDummyData.optimumScore?.let {
            appendLine("[OptimumScore \"$it\"]")
        }
        if (lastOrNull() == '\n') {
            deleteCharAt(lastIndex)
        }
    }
}

data class PbnExportOptions(
    val boardNumber: Int? = null,
    val event: String? = "ITROBOC Export",
    val site: String? = "Local",
    val dealer: Seat = Seat.NORTH,
    val vulnerability: BoardVulnerability? = null,
    val pbnDoubleDummyData: PbnDoubleDummyData? = null,
    val allowPartial: Boolean = false,
)
