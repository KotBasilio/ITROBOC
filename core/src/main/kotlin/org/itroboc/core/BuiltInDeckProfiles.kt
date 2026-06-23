package org.itroboc.core

object BuiltInDeckProfiles {
    fun demoBridge52(): DeckProfile =
        DeckProfile(
            signatureToCard = buildMap {
                var signatureValue = 0x1001
                Suit.entries.forEach { suit ->
                    Rank.entries.forEach { rank ->
                        put("0x${signatureValue.toString(16).uppercase()}", CardId(suit, rank))
                        signatureValue += 1
                    }
                }
            },
            metadata = DeckProfileMetadata(
                profileId = "builtin-demo-bridge52-v1",
                displayName = "Built-in Demo Bridge 52",
                isBuiltIn = true,
                isDemo = true,
                notes = "Synthetic demo/reference mapping only; not a real WinDup/Jannersten profile.",
                signatureModel = DeckProfileSignatureModels.SYNTHETIC_DEMO_BRIDGE_52_V1,
            ),
        )
}
