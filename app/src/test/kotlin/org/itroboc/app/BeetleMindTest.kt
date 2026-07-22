package org.itroboc.app

import org.itroboc.core.CardId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class BeetleMindTest {
    @Test
    fun `known evidence reaches typed card scan at configured consensus`() {
        val time = FakeTime(100L)
        val mind = BeetleMind(nowMillis = time::now)

        val first = mind.observe(knownAceOfSpades())
        time.current = 150L
        val second = mind.observe(knownAceOfSpades())

        assertEquals("♠A?", first.thought.presentation)
        assertNull(first.acceptedCardScan)
        assertEquals("♠A.", second.thought.presentation)
        assertEquals(
            AcceptedCardScan(
                rawSignature = "bfm1549",
                cardId = CardId.parse("SA"),
                confidence = 0.95,
            ),
            second.acceptedCardScan,
        )
    }

    @Test
    fun `changed signature starts a fresh consensus`() {
        val mind = BeetleMind(
            nowMillis = { 200L },
            settings = BeetleMindSettings(requiredConsensusFrames = 3),
        )

        mind.observe(knownAceOfSpades())
        mind.observe(knownAceOfSpades())
        val changed = mind.observe(
            BeetleEvidence.Known(
                rawSignature = "bfm1550",
                cardId = CardId.parse("HK"),
                confidence = 0.90,
            ),
        )

        assertEquals("♥K?", changed.thought.presentation)
        assertNull(changed.acceptedCardScan)
    }

    @Test
    fun `current threshold is applied to accumulated evidence`() {
        val mind = BeetleMind(
            nowMillis = { 150L },
            settings = BeetleMindSettings(requiredConsensusFrames = 4),
        )

        mind.observe(knownAceOfSpades())
        val accepted = mind.observe(
            evidence = knownAceOfSpades(),
            requiredConsensusFrames = 2,
        )

        assertEquals("♠A.", accepted.thought.presentation)
        assertEquals("bfm1549", accepted.acceptedCardScan?.rawSignature)
    }

    @Test
    fun `long consensus caps visible hesitation at three question marks`() {
        val mind = BeetleMind(
            nowMillis = { 0L },
            settings = BeetleMindSettings(requiredConsensusFrames = 6),
        )

        val thoughts = (1..5).map {
            mind.observe(knownAceOfSpades()).thought.presentation
        }

        assertEquals(listOf("♠A?", "♠A??", "♠A???", "♠A???", "♠A???"), thoughts)
    }

    @Test
    fun `unknown evidence remains visible but never becomes accepted`() {
        val mind = BeetleMind(nowMillis = { 0L })

        repeat(8) {
            val output = mind.observe(
                BeetleEvidence.Unknown(
                    rawSignature = "bfm-unknown",
                    confidence = 0.40,
                ),
            )

            assertEquals("bfm-unknown?", output.thought.presentation)
            assertNull(output.acceptedCardScan)
        }
    }

    @Test
    fun `ambiguous evidence shows first candidate and resets consensus`() {
        val mind = BeetleMind(
            nowMillis = { 0L },
            settings = BeetleMindSettings(requiredConsensusFrames = 3),
        )
        mind.observe(knownAceOfSpades())
        mind.observe(knownAceOfSpades())
        val candidates = listOf(
            BeetleSignatureCandidate(rawSignature = "bfm1550", confidence = 0.60),
            BeetleSignatureCandidate(rawSignature = "bfm1551", confidence = 0.58),
        )

        val ambiguous = mind.observe(BeetleEvidence.Ambiguous(candidates))
        val restarted = mind.observe(knownAceOfSpades())

        assertIs<BeetleThought.Uncertain>(ambiguous.thought)
        assertEquals("bfm1550~", ambiguous.thought.presentation)
        assertEquals("♠A?", restarted.thought.presentation)
        assertNull(restarted.acceptedCardScan)
    }

    @Test
    fun `ambiguous evidence without candidates blanks thought`() {
        val output = BeetleMind(nowMillis = { 0L })
            .observe(BeetleEvidence.Ambiguous(candidates = emptyList()))

        assertEquals(BeetleThought.Blank, output.thought)
    }

    @Test
    fun `not found conversion failure and reset blank thought and consensus`() {
        val evidenceResets = listOf<BeetleEvidence>(
            BeetleEvidence.NotFound(reason = "No barcode pattern"),
            BeetleEvidence.ConversionFailure(reason = "Luma plane unavailable"),
        )

        evidenceResets.forEach { evidence ->
            val mind = mindAwaitingThirdFrame()

            val reset = mind.observe(evidence)
            val restarted = mind.observe(knownAceOfSpades())

            assertEquals(BeetleThought.Blank, reset.thought)
            assertEquals("♠A?", restarted.thought.presentation)
            assertNull(restarted.acceptedCardScan)
        }

        val mind = mindAwaitingThirdFrame()
        val reset = mind.reset()
        val restarted = mind.observe(knownAceOfSpades())

        assertEquals(BeetleThought.Blank, reset.thought)
        assertEquals("♠A?", restarted.thought.presentation)
    }

    @Test
    fun `dream topics are typed and reset pondering`() {
        BeetleDream.entries.forEach { topic ->
            val mind = mindAwaitingThirdFrame()

            val dream = mind.dream(topic)
            val restarted = mind.observe(knownAceOfSpades())

            assertEquals(BeetleThought.Dreaming(topic), dream.thought)
            assertEquals(topic.presentation, dream.thought.presentation)
            assertEquals("♠A?", restarted.thought.presentation)
        }
    }

    @Test
    fun `accepted card scan is debounced against injected time`() {
        val time = FakeTime(0L)
        val mind = BeetleMind(nowMillis = time::now)

        mind.observe(knownAceOfSpades())
        time.current = 100L
        val accepted = mind.observe(knownAceOfSpades())
        time.current = 1_099L
        val debounced = mind.observe(knownAceOfSpades())
        time.current = 1_100L
        val acceptedAtBoundary = mind.observe(knownAceOfSpades())

        assertEquals("bfm1549", accepted.acceptedCardScan?.rawSignature)
        assertNull(debounced.acceptedCardScan)
        assertEquals("♠A.", debounced.thought.presentation)
        assertEquals("bfm1549", acceptedAtBoundary.acceptedCardScan?.rawSignature)
    }

    @Test
    fun `dream does not erase accepted card scan debounce`() {
        val time = FakeTime(0L)
        val mind = BeetleMind(nowMillis = time::now)
        mind.observe(knownAceOfSpades())
        time.current = 100L
        mind.observe(knownAceOfSpades())
        time.current = 150L
        mind.dream(BeetleDream.EYES)

        time.current = 200L
        mind.observe(knownAceOfSpades())
        time.current = 250L
        val repeated = mind.observe(knownAceOfSpades())

        assertNull(repeated.acceptedCardScan)
        assertEquals("♠A.", repeated.thought.presentation)
    }

    @Test
    fun `settings and runtime threshold reject unsupported values`() {
        assertFailsWith<IllegalArgumentException> {
            BeetleMindSettings(requiredConsensusFrames = 1)
        }
        assertFailsWith<IllegalArgumentException> {
            BeetleMindSettings(requiredConsensusFrames = 7)
        }
        assertFailsWith<IllegalArgumentException> {
            BeetleMindSettings(debounceWindowMillis = -1L)
        }
        assertFailsWith<IllegalArgumentException> {
            BeetleMind(nowMillis = { 0L }).observe(
                evidence = knownAceOfSpades(),
                requiredConsensusFrames = 7,
            )
        }
    }

    private fun mindAwaitingThirdFrame(): BeetleMind = BeetleMind(
        nowMillis = { 0L },
        settings = BeetleMindSettings(requiredConsensusFrames = 3),
    ).also { mind ->
        mind.observe(knownAceOfSpades())
        mind.observe(knownAceOfSpades())
    }

    private fun knownAceOfSpades(): BeetleEvidence.Known =
        BeetleEvidence.Known(
            rawSignature = "bfm1549",
            cardId = CardId.parse("SA"),
            confidence = 0.95,
        )

    private class FakeTime(var current: Long) {
        fun now(): Long = current
    }
}
