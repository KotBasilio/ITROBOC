package org.itroboc.core

internal fun boardOf(vararg seatCards: Pair<Seat, List<String>>): BoardState =
    seatCards.fold(BoardState()) { board, (seat, cards) ->
        cards.fold(board) { currentBoard, rawCard ->
            currentBoard.addCard(seat, CardId.parse(rawCard))
        }
    }
