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
        assertEquals(DeckProfileSignatureModels.SYNTHETIC_DEMO_BRIDGE_52_V1, profile.metadata.signatureModel)
        assertTrue(profile.metadata.notes?.contains("Synthetic demo/reference mapping") == true)
        assertTrue(profile.metadata.notes?.contains("not a real WinDup/Jannersten profile") == true)
    }

    @Test
    fun `deck profile metadata can carry grid13 signature model`() {
        val profile = DeckProfile(
            signatureToCard = mapOf("bfm1549" to CardId.parse("SA")),
            metadata = DeckProfileMetadata(
                profileId = "club-calibration",
                displayName = "Club calibration",
                signatureModel = DeckProfileSignatureModels.GRID13_V1,
            ),
        )

        assertEquals(DeckProfileSignatureModels.GRID13_V1, profile.metadata.signatureModel)
        assertEquals(CardId.parse("SA"), profile.lookup("bfm1549"))
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

    @Test
    fun `observed profile exposes expected metadata`() {
        val profile = BuiltInDeckProfiles.observedV1()

        assertEquals("builtin-observed-v1", profile.metadata.profileId)
        assertEquals("Built-in Observed v1", profile.metadata.displayName)
        assertEquals(true, profile.metadata.isBuiltIn)
        assertEquals(false, profile.metadata.isDemo)
        assertEquals(DeckProfileSignatureModels.GRID13_V1, profile.metadata.signatureModel)
        assertTrue(profile.metadata.notes?.contains("S6 is omitted") == true)
    }

    @Test
    fun `observed profile contains scanned aliases and expected incomplete coverage`() {
        val profile = BuiltInDeckProfiles.observedV1()

        assertEquals(102, profile.mappingCount())
        assertEquals(51, profile.cardIds().size)
        assertEquals(profile.mappingCount(), profile.rawSignatures().size)

        assertEquals(CardId.parse("SA"), profile.lookup("bfm1255"))
        assertEquals(CardId.parse("SA"), profile.lookup("brm1549"))
        assertEquals(CardId.parse("C6"), profile.lookup("bfm002A"))
        assertEquals(CardId.parse("C6"), profile.lookup("brm0A80"))
        assertTrue(CardId.parse("S6") !in profile.cardIds())
    }

    @Test
    fun `observed profile keeps cross orientation payloads as distinct aliases`() {
        val profile = BuiltInDeckProfiles.observedV1()

        assertEquals(CardId.parse("SA"), profile.lookup("bfm1255"))
        assertEquals(CardId.parse("DT"), profile.lookup("brm1255"))
        assertEquals(CardId.parse("DT"), profile.lookup("bfm1549"))
        assertEquals(CardId.parse("SA"), profile.lookup("brm1549"))
    }

    @Test
    fun `default built in profile is observed profile`() {
        assertEquals("builtin-observed-v1", BuiltInDeckProfiles.defaultProfile().metadata.profileId)
    }
}
