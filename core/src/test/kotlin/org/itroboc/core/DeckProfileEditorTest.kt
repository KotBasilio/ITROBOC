package org.itroboc.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeckProfileEditorTest {

    private val metadata = DeckProfileMetadata("test", "Test Profile", false, false)
    private val sa = CardId.parse("SA")
    private val sk = CardId.parse("SK")

    @Test
    fun `assigning first signature`() {
        val editor = DeckProfileEditor(emptyMap(), metadata)
        val result = editor.assign("0x1", sa)
        
        assertTrue(result is DeckProfileEditResult.Assigned)
        assertEquals(sa, (result as DeckProfileEditResult.Assigned).card)
        assertEquals("0x1", result.signature)
        assertEquals(listOf("0x1"), editor.getAliases(sa))
        assertTrue(editor.isMapped(sa))
        assertEquals(1, editor.mappedCardCount())
    }

    @Test
    fun `adding alias to same card`() {
        val editor = DeckProfileEditor(mapOf("0x1" to sa), metadata)
        val result = editor.assign("0x2", sa)
        
        assertTrue(result is DeckProfileEditResult.Assigned)
        assertEquals(listOf("0x1", "0x2").sorted(), editor.getAliases(sa).sorted())
        assertEquals(1, editor.mappedCardCount())
    }

    @Test
    fun `re-assigning same signature to same card is harmless`() {
        val editor = DeckProfileEditor(mapOf("0x1" to sa), metadata)
        val result = editor.assign("0x1", sa)
        
        assertTrue(result is DeckProfileEditResult.AlreadyAssignedToSelected)
        assertEquals(listOf("0x1"), editor.getAliases(sa))
    }

    @Test
    fun `assigning used signature to different card is a conflict`() {
        val editor = DeckProfileEditor(mapOf("0x1" to sa), metadata)
        val result = editor.assign("0x1", sk)
        
        assertTrue(result is DeckProfileEditResult.SignatureConflict)
        val conflict = result as DeckProfileEditResult.SignatureConflict
        assertEquals("0x1", conflict.signature)
        assertEquals(sa, conflict.existingCard)
        assertEquals(sk, conflict.selectedCard)
        
        // Mapping should not have changed
        assertEquals(sa, editor.toDeckProfile().lookup("0x1"))
        assertFalse(editor.isMapped(sk))
    }

    @Test
    fun `removing alias`() {
        val editor = DeckProfileEditor(mapOf("0x1" to sa, "0x2" to sa), metadata)
        editor.remove("0x1")
        
        assertEquals(listOf("0x2"), editor.getAliases(sa))
        assertTrue(editor.isMapped(sa))
        
        editor.remove("0x2")
        assertFalse(editor.isMapped(sa))
        assertEquals(0, editor.mappedCardCount())
    }

    @Test
    fun `completeness check`() {
        val editor = DeckProfileEditor(emptyMap(), metadata)
        assertFalse(editor.isComplete())
        
        // Fill all 52 cards
        Suit.entries.forEach { suit ->
            Rank.entries.forEach { rank ->
                val card = CardId(suit, rank)
                editor.assign("sig-${suit.symbol}-${rank.symbol}", card)
            }
        }
        
        assertEquals(52, editor.mappedCardCount())
        assertTrue(editor.isComplete())
    }

    @Test
    fun `assigning with no card selected`() {
        val editor = DeckProfileEditor(emptyMap(), metadata)
        val result = editor.assign("0x1", null)
        assertTrue(result is DeckProfileEditResult.NoCardSelected)
    }
}
