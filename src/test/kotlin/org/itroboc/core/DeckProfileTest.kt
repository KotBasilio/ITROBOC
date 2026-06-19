package org.itroboc.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeckProfileTest {
    @Test
    fun `looks up card ids by raw signature`() {
        val profile = DeckProfile(
            mapOf(
                "sig-spade-ace" to CardId.parse("SA"),
                "sig-club-queen" to CardId.parse("CQ"),
            ),
        )

        assertEquals(CardId.parse("SA"), profile.lookup("sig-spade-ace"))
        assertEquals(CardId.parse("CQ"), profile.lookup("sig-club-queen"))
        assertNull(profile.lookup("missing-signature"))
    }
}
