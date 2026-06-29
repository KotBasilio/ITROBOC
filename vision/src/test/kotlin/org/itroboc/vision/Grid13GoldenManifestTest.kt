package org.itroboc.vision

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Grid13GoldenManifestTest {
    private val manifest = Grid13GoldenManifest.load()

    @Test
    fun `golden manifest contains all fifty two cards`() {
        val expectedCards = listOf('S', 'H', 'D', 'C').flatMap { suit ->
            listOf("A", "K", "Q", "J", "T", "9", "8", "7", "6", "5", "4", "3", "2").map { rank ->
                "$suit$rank"
            }
        }.toSet()

        assertEquals("grid13-v2", manifest.signatureModel)
        assertEquals(52, manifest.cards.size)
        assertEquals(expectedCards, manifest.cards.map { it.cardId }.toSet())
    }

    @Test
    fun `golden manifest signatures are formatted and unique`() {
        assertEquals(52, manifest.cards.map { it.rawSignature }.toSet().size)
        assertEquals(52, manifest.cards.map { it.grid13FwdBits }.toSet().size)

        manifest.cards.forEach { card ->
            assertTrue(card.rawSignature.matches(Regex("^bfm[0-9A-F]{4}$")), card.cardId)
            assertTrue(card.reverseSignature.matches(Regex("^brm[0-9A-F]{4}$")), card.cardId)
            assertEquals(13, card.grid13FwdBits.length, card.cardId)
            assertEquals(reverseBits(card.grid13FwdBits), card.grid13RevBits, card.cardId)
            assertEquals(forwardMealSignature(card.grid13FwdBits), card.rawSignature, card.cardId)
            assertEquals(reverseMealSignature(card.grid13RevBits), card.reverseSignature, card.cardId)
            assertEquals(grid13RunLengthSignature(card.grid13FwdBits), card.rl2, card.cardId)
        }
    }

    @Test
    fun `golden manifest run signatures are exact and collision free`() {
        assertEquals(52, manifest.cards.map { it.rl2 }.toSet().size)
        assertTrue(manifest.cards.any { "-W3-" in "-${it.rl2}-" })
        assertTrue(manifest.cards.none { Regex("(^|-)B3($|-)").containsMatchIn(it.rl2) })
    }

    @Test
    fun `golden manifest does not canonicalize orientation`() {
        val canonicalized = manifest.cards.map { card ->
            minOf(card.grid13FwdBits, card.grid13RevBits)
        }

        assertTrue(canonicalized.toSet().size < manifest.cards.size)
    }
}

private data class Grid13GoldenManifest(
    val signatureModel: String,
    val cards: List<Grid13GoldenCard>,
) {
    companion object {
        fun load(): Grid13GoldenManifest {
            val text = requireNotNull(
                Grid13GoldenManifest::class.java.getResource("docs/dev_history/vision/barcodes/grid13-v2-golden.json"),
            ) {
                "Missing grid13-v2 golden manifest"
            }.readText()

            val signatureModel = requireNotNull(
                Regex("\"signatureModel\"\\s*:\\s*\"([^\"]+)\"").find(text)?.groupValues?.get(1),
            ) {
                "Missing signatureModel"
            }
            val cards = Regex("\\{\\s*\"cardId\".*?\\}", RegexOption.DOT_MATCHES_ALL)
                .findAll(text)
                .map { match -> Grid13GoldenCard.fromJsonObject(match.value) }
                .toList()

            return Grid13GoldenManifest(
                signatureModel = signatureModel,
                cards = cards,
            )
        }
    }
}

private data class Grid13GoldenCard(
    val cardId: String,
    val rawSignature: String,
    val reverseSignature: String,
    val grid13FwdBits: String,
    val grid13RevBits: String,
    val rl2: String,
) {
    companion object {
        fun fromJsonObject(json: String): Grid13GoldenCard =
            Grid13GoldenCard(
                cardId = json.stringField("cardId"),
                rawSignature = json.stringField("rawSignature"),
                reverseSignature = json.stringField("reverseSignature"),
                grid13FwdBits = json.stringField("grid13FwdBits"),
                grid13RevBits = json.stringField("grid13RevBits"),
                rl2 = json.stringField("rl2"),
            )
    }
}

private fun String.stringField(name: String): String =
    requireNotNull(
        Regex("\"$name\"\\s*:\\s*\"([^\"]*)\"").find(this)?.groupValues?.get(1),
    ) {
        "Missing string field $name"
    }
