package org.itroboc.core

object PbnExporter {
    fun export(boardState: BoardState): String {
        boardState.validateComplete()

        val dealValue = boardState.handsInPbnOrder()
            .joinToString(separator = " ") { it.toPbnHand() }

        return buildString {
            appendLine("[Event \"ITROBOC Export\"]")
            appendLine("[Site \"Local\"]")
            appendLine("[Dealer \"N\"]")
            append("[Deal \"N:$dealValue\"]")
        }
    }
}
