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

    fun observedV1(): DeckProfile =
        DeckProfile(
            signatureToCard = buildMap {
                fun addObserved(
                    card: CardId,
                    rawSignature: String,
                    reverseSignature: String,
                ) {
                    putObserved(rawSignature, card)
                    putObserved(reverseSignature, card)
                }

                addObserved(CardId(Suit.SPADES, Rank.ACE), "bfm1255", "brm1549")
                addObserved(CardId(Suit.SPADES, Rank.KING), "bfm12A5", "brm14A9")
                addObserved(CardId(Suit.SPADES, Rank.QUEEN), "bfm12B4", "brm05A9")
                addObserved(CardId(Suit.SPADES, Rank.JACK), "bfm1359", "brm1359")
                addObserved(CardId(Suit.SPADES, Rank.TEN), "bfm10A2", "brm08A1")
                addObserved(CardId(Suit.SPADES, Rank.NINE), "bfm12A9", "brm12A9")
                addObserved(CardId(Suit.SPADES, Rank.EIGHT), "bfm1295", "brm1529")
                addObserved(CardId(Suit.SPADES, Rank.SEVEN), "bfm1299", "brm1329")
                addObserved(CardId(Suit.SPADES, Rank.SIX), "bfm1669", "brm12CD")
                addObserved(CardId(Suit.SPADES, Rank.FIVE), "bfm12C9", "brm1269")
                addObserved(CardId(Suit.SPADES, Rank.FOUR), "bfm1348", "brm0259")
                addObserved(CardId(Suit.SPADES, Rank.THREE), "bfm0015", "brm1500")
                addObserved(CardId(Suit.SPADES, Rank.TWO), "bfm0028", "brm0280")
                addObserved(CardId(Suit.HEARTS, Rank.ACE), "bfm1249", "brm1249")
                addObserved(CardId(Suit.HEARTS, Rank.KING), "bfm15B1", "brm11B5")
                addObserved(CardId(Suit.HEARTS, Rank.QUEEN), "bfm0016", "brm0D00")
                addObserved(CardId(Suit.HEARTS, Rank.JACK), "bfm15A5", "brm14B5")
                addObserved(CardId(Suit.HEARTS, Rank.TEN), "bfm15A9", "brm12B5")
                addObserved(CardId(Suit.HEARTS, Rank.NINE), "bfm1599", "brm1335")
                addObserved(CardId(Suit.HEARTS, Rank.EIGHT), "bfm158D", "brm1635")
                addObserved(CardId(Suit.HEARTS, Rank.SEVEN), "bfm1537", "brm1D95")
                addObserved(CardId(Suit.HEARTS, Rank.SIX), "bfm0014", "brm0500")
                addObserved(CardId(Suit.HEARTS, Rank.FIVE), "bfm1527", "brm1C95")
                addObserved(CardId(Suit.HEARTS, Rank.FOUR), "bfm1529", "brm1295")
                addObserved(CardId(Suit.HEARTS, Rank.THREE), "bfm1589", "brm1235")
                addObserved(CardId(Suit.HEARTS, Rank.TWO), "bfm083C", "brm0782")
                addObserved(CardId(Suit.DIAMONDS, Rank.ACE), "bfm1802", "brm0803")
                addObserved(CardId(Suit.DIAMONDS, Rank.KING), "bfm1555", "brm1555")
                addObserved(CardId(Suit.DIAMONDS, Rank.QUEEN), "bfm155C", "brm0755")
                addObserved(CardId(Suit.DIAMONDS, Rank.JACK), "bfm154D", "brm1655")
                addObserved(CardId(Suit.DIAMONDS, Rank.TEN), "bfm1549", "brm1255")
                addObserved(CardId(Suit.DIAMONDS, Rank.NINE), "bfm105E", "brm0F41")
                addObserved(CardId(Suit.DIAMONDS, Rank.EIGHT), "bfm1237", "brm1D89")
                addObserved(CardId(Suit.DIAMONDS, Rank.SEVEN), "bfm14A5", "brm14A5")
                addObserved(CardId(Suit.DIAMONDS, Rank.SIX), "bfm16F8", "brm03ED")
                addObserved(CardId(Suit.DIAMONDS, Rank.FIVE), "bfm14D5", "brm1565")
                addObserved(CardId(Suit.DIAMONDS, Rank.FOUR), "bfm14C8", "brm0265")
                addObserved(CardId(Suit.DIAMONDS, Rank.THREE), "bfm1655", "brm154D")
                addObserved(CardId(Suit.DIAMONDS, Rank.TWO), "bfm10D4", "brm0561")
                addObserved(CardId(Suit.CLUBS, Rank.ACE), "bfm1259", "brm1349")
                addObserved(CardId(Suit.CLUBS, Rank.KING), "bfm100C", "brm0601")
                addObserved(CardId(Suit.CLUBS, Rank.QUEEN), "bfm1024", "brm0481")
                addObserved(CardId(Suit.CLUBS, Rank.JACK), "bfm0900", "brm0012")
                addObserved(CardId(Suit.CLUBS, Rank.TEN), "bfm00A9", "brm12A0")
                addObserved(CardId(Suit.CLUBS, Rank.NINE), "bfm1665", "brm14CD")
                addObserved(CardId(Suit.CLUBS, Rank.EIGHT), "bfm131D", "brm1719")
                addObserved(CardId(Suit.CLUBS, Rank.SEVEN), "bfm1658", "brm034D")
                addObserved(CardId(Suit.CLUBS, Rank.SIX), "bfm164D", "brm164D")
                addObserved(CardId(Suit.CLUBS, Rank.FIVE), "bfm1C0D", "brm1607")
                addObserved(CardId(Suit.CLUBS, Rank.FOUR), "bfm1377", "brm1DD9")
                addObserved(CardId(Suit.CLUBS, Rank.THREE), "bfm1016", "brm0D01")
                addObserved(CardId(Suit.CLUBS, Rank.TWO), "bfm1325", "brm1499")
            },
            metadata = DeckProfileMetadata(
                profileId = "builtin-observed-v1",
                displayName = "Built-in Observed v1",
                isBuiltIn = true,
                isDemo = false,
                notes = "Observed grid13-v1 profile from scanned_attempt1.jsonl, with S6/C6 resolved by focused rescan 6S-and-6C.jsonl.",
                signatureModel = DeckProfileSignatureModels.GRID13_V1,
            ),
        )

    fun defaultProfile(): DeckProfile = observedV1()

    private fun MutableMap<String, CardId>.putObserved(
        signature: String,
        card: CardId,
    ) {
        val existing = putIfAbsent(signature, card)
        require(existing == null || existing == card) {
            "Observed profile signature $signature maps to both $existing and $card"
        }
    }
}
