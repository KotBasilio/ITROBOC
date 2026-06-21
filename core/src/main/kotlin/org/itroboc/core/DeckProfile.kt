package org.itroboc.core

data class DeckProfile(
    private val signatureToCard: Map<String, CardId>,
    val metadata: DeckProfileMetadata = DeckProfileMetadata(),
) {
    fun lookup(signature: String): CardId? = signatureToCard[signature]

    fun mappingCount(): Int = signatureToCard.size

    fun rawSignatures(): Set<String> = signatureToCard.keys

    fun cardIds(): Set<CardId> = signatureToCard.values.toSet()
}

data class DeckProfileMetadata(
    val profileId: String = "ad-hoc-profile",
    val displayName: String = "Ad-hoc Deck Profile",
    val isBuiltIn: Boolean = false,
    val isDemo: Boolean = false,
    val notes: String? = null,
)
