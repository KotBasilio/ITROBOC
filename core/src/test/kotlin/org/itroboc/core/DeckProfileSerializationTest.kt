package org.itroboc.core

import kotlin.test.Test
import kotlin.test.assertEquals

class DeckProfileSerializationTest {

    @Test
    fun `serializes and deserializes a deck profile`() {
        val original = DeckProfile(
            signatureToCard = mapOf(
                "bfm1549" to CardId.parse("SA"),
                "brm1255" to CardId.parse("SA"),
                "bfm1669" to CardId.parse("S6")
            ),
            metadata = DeckProfileMetadata(
                profileId = "test-id",
                displayName = "Test Profile",
                isBuiltIn = false,
                isDemo = false,
                notes = "Line 1\nLine 2 with \"quotes\"",
                signatureModel = "grid13-v2"
            )
        )

        val json = original.toJson()
        val restored = DeckProfile.fromJson(json)

        assertEquals(original.metadata.profileId, restored.metadata.profileId)
        assertEquals(original.metadata.displayName, restored.metadata.displayName)
        assertEquals(original.metadata.isBuiltIn, restored.metadata.isBuiltIn)
        assertEquals(original.metadata.isDemo, restored.metadata.isDemo)
        assertEquals(original.metadata.notes, restored.metadata.notes)
        assertEquals(original.metadata.signatureModel, restored.metadata.signatureModel)
        
        assertEquals(original.mappingCount(), restored.mappingCount())
        assertEquals(original.lookup("bfm1549"), restored.lookup("bfm1549"))
        assertEquals(original.lookup("brm1255"), restored.lookup("brm1255"))
        assertEquals(original.lookup("bfm1669"), restored.lookup("bfm1669"))
    }

    @Test
    fun `handles empty mappings`() {
        val original = DeckProfile(emptyMap(), DeckProfileMetadata(displayName = "Empty"))
        val json = original.toJson()
        val restored = DeckProfile.fromJson(json)
        
        assertEquals(0, restored.mappingCount())
        assertEquals("Empty", restored.metadata.displayName)
    }
}
