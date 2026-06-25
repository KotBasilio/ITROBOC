package org.itroboc.core

class DeckProfileEditor(
    initialMappings: Map<String, CardId>,
    val metadata: DeckProfileMetadata
) {
    private val mutableMappings = initialMappings.toMutableMap()

    fun assign(signature: String, card: CardId?): DeckProfileEditResult {
        if (card == null) return DeckProfileEditResult.NoCardSelected
        
        val existing = mutableMappings[signature]
        return when {
            existing == card -> DeckProfileEditResult.AlreadyAssignedToSelected(card, signature)
            existing != null -> DeckProfileEditResult.SignatureConflict(signature, existing, card)
            else -> {
                mutableMappings[signature] = card
                DeckProfileEditResult.Assigned(card, signature)
            }
        }
    }

    fun remove(signature: String) {
        mutableMappings.remove(signature)
    }

    fun getAliases(card: CardId): List<String> {
        return mutableMappings.filterValues { it == card }.keys.toList()
    }

    fun isMapped(card: CardId): Boolean {
        return mutableMappings.containsValue(card)
    }

    fun mappedCardCount(): Int {
        return mutableMappings.values.distinct().size
    }

    fun isComplete(): Boolean {
        return mappedCardCount() == 52
    }

    fun hasAliasesForEveryCard(): Boolean {
        return Suit.entries.all { suit ->
            Rank.entries.all { rank ->
                getAliases(CardId(suit, rank)).isNotEmpty()
            }
        }
    }

    fun toDeckProfile(): DeckProfile {
        return DeckProfile(
            signatureToCard = mutableMappings.toMap(),
            metadata = metadata
        )
    }
}
