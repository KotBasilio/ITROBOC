package org.itroboc.core

data class DeckProfile(private val signatureToCard: Map<String, CardId>) {
    fun lookup(signature: String): CardId? = signatureToCard[signature]

    fun mappingCount(): Int = signatureToCard.size

    fun rawSignatures(): Set<String> = signatureToCard.keys

    fun cardIds(): Set<CardId> = signatureToCard.values.toSet()
}
