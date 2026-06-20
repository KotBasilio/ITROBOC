package org.itroboc.core

object BuiltInDeckProfiles {
    fun demoBridge52(): DeckProfile =
        DeckProfile(
            buildMap {
                var signatureValue = 0x1001
                Suit.entries.forEach { suit ->
                    Rank.entries.forEach { rank ->
                        put("0x${signatureValue.toString(16).uppercase()}", CardId(suit, rank))
                        signatureValue += 1
                    }
                }
            },
        )
}
