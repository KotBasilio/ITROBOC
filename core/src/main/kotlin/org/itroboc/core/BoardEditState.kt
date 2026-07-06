package org.itroboc.core

data class AddedCardRecord(
    val seat: Seat,
    val card: CardId,
)

data class DuplicateOverrideCandidate(
    val card: CardId,
    val signature: String,
    val existingSeat: Seat,
    val targetSeat: Seat,
)

data class PbnDoubleDummyData(
    val doubleDummyTricks: String? = null,
    val optimumResultTableHeader: String? = null,
    val optimumResultTableRows: List<String> = emptyList(),
    val optimumScore: String? = null,
)

data class BoardEditState(
    val boardNumber: Int,
    val boardState: BoardState = BoardState(),
    val selectedSeat: Seat = Seat.NORTH,
    val addHistory: List<AddedCardRecord> = emptyList(),
    val duplicateOverrideCandidate: DuplicateOverrideCandidate? = null,
    val pbnDoubleDummyData: PbnDoubleDummyData? = null,
)
