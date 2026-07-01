package org.itroboc.vision

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Grid13VerdictDecoderTest {

    private val slowDecoder = Grid13SlowDecoder()
    private val verdictDecoder = Grid13VerdictDecoder()
    private val manifest = Grid13VerdictGoldenManifest.load()

    @Test
    fun `golden grid13 images keep the same bfm signatures in verdict path`() {
        manifest.cards.forEach { card ->
            val image = grid13Image(card.grid13FwdBits)
            val verdictResult = assertIs<BarcodeDecodeResult.Found>(verdictDecoder.decode(image), card.cardId)

            assertEquals(card.rawSignature, verdictResult.signature.rawSignature, card.cardId)
            assertNull(verdictResult.signature.debug, card.cardId)
        }
    }

    @Test
    fun `verdict decoder matches slow decoder signatures on golden manifest`() {
        manifest.cards.forEach { card ->
            val image = grid13Image(card.grid13FwdBits)
            val slowResult = assertIs<BarcodeDecodeResult.Found>(slowDecoder.decode(image), card.cardId)
            val verdictResult = assertIs<BarcodeDecodeResult.Found>(verdictDecoder.decode(image), card.cardId)

            assertEquals(slowResult.signature.rawSignature, verdictResult.signature.rawSignature, card.cardId)
            assertEquals(slowResult.signature.bounds, verdictResult.signature.bounds, card.cardId)
            assertTrue(verdictResult.signature.confidence in 0.0..1.0, card.cardId)
            assertNull(verdictResult.signature.debug, card.cardId)
        }
    }

    @Test
    fun `verdict decoder rejects black triple candidates`() {
        val result = assertIs<BarcodeDecodeResult.NotFound>(verdictDecoder.decode(grid13Image("1011101001001")))
        assertEquals("Invalid bit run pattern", result.reason)
        assertNull(result.debug)
    }

    @Test
    fun `verdict decoder rejects white quadruple candidates`() {
        val result = assertIs<BarcodeDecodeResult.NotFound>(verdictDecoder.decode(grid13Image("1000010101001")))
        assertEquals("Invalid bit run pattern", result.reason)
        assertNull(result.debug)
    }

    @Test
    fun `verdict decoder preserves sentinel normalization behavior`() {
        val result = assertIs<BarcodeDecodeResult.Found>(
            Grid13VerdictDecoder(minimumFoundConfidence = 0.0).decode(grid13Image("1101010101001")),
        )

        assertEquals("bfm12A9", result.signature.rawSignature)
        assertNull(result.signature.debug)
    }

    @Test
    fun `verdict decoder uses confidence threshold rejection`() {
        val strictDecoder = Grid13VerdictDecoder(minimumFoundConfidence = 0.99)
        val result = assertIs<BarcodeDecodeResult.Ambiguous>(strictDecoder.decode(grid13Image("1010101001001")))

        assertEquals(1, result.candidates.size)
        assertEquals("bfm1549", result.candidates.single().rawSignature)
        assertNull(result.debug)
        assertNull(result.candidates.single().debug)
    }

    @Test
    fun `bitwise helpers keep the same strict run gate rules`() {
        assertTrue(hasInvalidGrid13RunCandidate(0b1011101001001))
        assertTrue(hasInvalidGrid13RunCandidate(0b1000010101001))
        assertFalse(hasInvalidGrid13RunCandidate(0b1010101001001))
    }

    @Test
    fun `bitwise sentinel normalization keeps corset frame`() {
        assertEquals("1001010101001", bits13ToString(normalizeGrid13Sentinels(0b1101010101001)))
    }

    private fun grid13Image(bits: String): GrayImage {
        require(bits.length == 13)
        val cellWidth = 4
        val height = 4
        val width = bits.length * cellWidth
        val pixels = ByteArray(width * height)

        bits.forEachIndexed { cellIndex, bit ->
            val intensity = if (bit == '1') 0 else 255
            repeat(cellWidth) { xInCell ->
                val x = (cellIndex * cellWidth) + xInCell
                repeat(height) { y ->
                    pixels[(y * width) + x] = intensity.toByte()
                }
            }
        }

        return GrayImage(width = width, height = height, pixels = pixels)
    }
}

private data class Grid13VerdictGoldenManifest(
    val cards: List<Grid13VerdictGoldenCard>,
) {
    companion object {
        fun load(): Grid13VerdictGoldenManifest {
            val text = requireNotNull(
                Grid13VerdictGoldenManifest::class.java.getResource("/barcode-sheets/grid13-v2-golden.json"),
            ) {
                "Missing grid13-v2 golden manifest"
            }.readText()

            val cards = Regex("\\{\\s*\"cardId\".*?\\}", RegexOption.DOT_MATCHES_ALL)
                .findAll(text)
                .map { match -> Grid13VerdictGoldenCard.fromJsonObject(match.value) }
                .toList()

            return Grid13VerdictGoldenManifest(cards = cards)
        }
    }
}

private data class Grid13VerdictGoldenCard(
    val cardId: String,
    val rawSignature: String,
    val grid13FwdBits: String,
) {
    companion object {
        fun fromJsonObject(json: String): Grid13VerdictGoldenCard =
            Grid13VerdictGoldenCard(
                cardId = json.stringField("cardId"),
                rawSignature = json.stringField("rawSignature"),
                grid13FwdBits = json.stringField("grid13FwdBits"),
            )
    }
}

private fun String.stringField(name: String): String =
    requireNotNull(
        Regex("\"$name\"\\s*:\\s*\"([^\"]*)\"").find(this)?.groupValues?.get(1),
    ) {
        "Missing string field $name"
    }
