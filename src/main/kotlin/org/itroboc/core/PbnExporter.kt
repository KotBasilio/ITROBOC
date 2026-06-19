package org.itroboc.core

object PbnExporter {
    fun export(
        boardState: BoardState,
        options: PbnExportOptions = PbnExportOptions(),
    ): String {
        boardState.validateComplete()

        val dealValue = boardState.handsInPbnOrder(options.dealer)
            .joinToString(separator = " ") { it.toPbnHand() }

        return buildString {
            options.event?.let { appendLine("[Event \"$it\"]") }
            options.site?.let { appendLine("[Site \"$it\"]") }
            options.boardNumber?.let { appendLine("[Board \"$it\"]") }
            appendLine("[Dealer \"${options.dealer.symbol}\"]")
            append("[Deal \"${options.dealer.symbol}:$dealValue\"]")
        }
    }
}

data class PbnExportOptions(
    val boardNumber: Int? = null,
    val event: String? = "ITROBOC Export",
    val site: String? = "Local",
    val dealer: Seat = Seat.NORTH,
)
