package org.itroboc.core

data class DeckProfile(private val signatureToCard: Map<String, CardId>) {
    fun lookup(signature: String): CardId? = signatureToCard[signature]
}
