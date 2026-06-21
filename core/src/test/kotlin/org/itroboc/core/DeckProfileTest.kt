package org.itroboc.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeckProfileTest {
    @Test
    fun `looks up card ids by raw signature`() {
        val profile = DeckProfile(
            signatureToCard = mapOf(
                "sig-spade-ace" to CardId.parse("SA"),
                "sig-club-queen" to CardId.parse("CQ"),
            ),
        )

        assertEquals(CardId.parse("SA"), profile.lookup("sig-spade-ace"))
        assertEquals(CardId.parse("CQ"), profile.lookup("sig-club-queen"))
        assertNull(profile.lookup("missing-signature"))
    }

    @Test
    fun `demo bridge profile contains all fifty two mappings exactly once`() {
        val profile = BuiltInDeckProfiles.demoBridge52()

        assertEquals(52, profile.mappingCount())
        assertEquals(52, profile.cardIds().size)
        assertEquals(52, profile.rawSignatures().size)
    }

    @Test
    fun `demo bridge profile exposes expected metadata`() {
        val profile = BuiltInDeckProfiles.demoBridge52()

        assertEquals("builtin-demo-bridge52-v1", profile.metadata.profileId)
        assertEquals("Built-in Demo Bridge 52", profile.metadata.displayName)
        assertEquals(true, profile.metadata.isBuiltIn)
        assertEquals(true, profile.metadata.isDemo)
        assertTrue(profile.metadata.notes?.contains("Synthetic demo/reference mapping") == true)
        assertTrue(profile.metadata.notes?.contains("not a real WinDup/Jannersten profile") == true)
    }

    @Test
    fun `demo bridge profile contains expected sample mappings`() {
        val profile = BuiltInDeckProfiles.demoBridge52()

        assertEquals(CardId.parse("SA"), profile.lookup("0x1001"))
        assertEquals(CardId.parse("HA"), profile.lookup("0x100E"))
        assertEquals(CardId.parse("DA"), profile.lookup("0x101B"))
        assertEquals(CardId.parse("CA"), profile.lookup("0x1028"))
        assertEquals(CardId.parse("C2"), profile.lookup("0x1034"))
    }

    @Test
    fun `demo bridge profile covers every canonical card id`() {
        val profile = BuiltInDeckProfiles.demoBridge52()

        val expectedCards = Suit.entries.flatMap { suit ->
            Rank.entries.map { rank -> CardId(suit, rank) }
        }.toSet()

        assertEquals(expectedCards, profile.cardIds())
    }

    @Test
    fun `demo bridge profile raw signatures are unique`() {
        val profile = BuiltInDeckProfiles.demoBridge52()

        assertTrue(profile.rawSignatures().all { it.startsWith("0x") })
        assertEquals(profile.mappingCount(), profile.rawSignatures().size)
    }
}
