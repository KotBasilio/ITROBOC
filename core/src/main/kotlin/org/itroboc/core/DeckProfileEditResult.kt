package org.itroboc.core

sealed class DeckProfileEditResult {
    data class Assigned(val card: CardId, val signature: String) : DeckProfileEditResult()
    data class AlreadyAssignedToSelected(val card: CardId, val signature: String) : DeckProfileEditResult()
    data class SignatureConflict(
        val signature: String,
        val existingCard: CardId,
        val selectedCard: CardId
    ) : DeckProfileEditResult()
    object NoCardSelected : DeckProfileEditResult()
}
