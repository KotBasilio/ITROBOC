package org.itroboc.app

import org.itroboc.core.CardId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class BeetleMindTest {
    @Test
    fun `known signature reaches typed acceptance at configured consensus`() {
        val mind = BeetleMind()

        val first = mind.process(knownAceOfSpades(), nowMillis = 100L)
        val second = mind.process(knownAceOfSpades(), nowMillis = 150L)

        assertEquals("♠A?", first.thought.presentation)
        assertNull(first.acceptedSignature)
        assertEquals("♠A.", second.thought.presentation)
        assertEquals(
            AcceptedSignature(
                rawSignature = "bfm1549",
                cardId = CardId.parse("SA"),
            ),
            second.acceptedSignature,
        )
    }

    @Test
    fun `changed signature starts a fresh consensus`() {
        val mind = BeetleMind(BeetleMindSettings(requiredConsensusFrames = 3))

        mind.process(knownAceOfSpades(), nowMillis = 100L)
        mind.process(knownAceOfSpades(), nowMillis = 150L)
        val changed = mind.process(
            BeetleMindInput.KnownSignature(
                rawSignature = "bfm1550",
                cardId = CardId.parse("HK"),
            ),
            nowMillis = 200L,
        )

        assertEquals("♥K?", changed.thought.presentation)
        assertNull(changed.acceptedSignature)
    }

    @Test
    fun `current threshold is applied to accumulated evidence`() {
        val mind = BeetleMind(BeetleMindSettings(requiredConsensusFrames = 4))

        mind.process(knownAceOfSpades(), nowMillis = 100L)
        val accepted = mind.process(
            input = knownAceOfSpades(),
            nowMillis = 150L,
            requiredConsensusFrames = 2,
        )

        assertEquals("♠A.", accepted.thought.presentation)
        assertEquals("bfm1549", accepted.acceptedSignature?.rawSignature)
    }

    @Test
    fun `long consensus caps visible hesitation at three question marks`() {
        val mind = BeetleMind(BeetleMindSettings(requiredConsensusFrames = 6))

        val thoughts = (1..5).map { count ->
            mind.process(knownAceOfSpades(), nowMillis = count * 50L).thought.presentation
        }

        assertEquals(listOf("♠A?", "♠A??", "♠A???", "♠A???", "♠A???"), thoughts)
    }

    @Test
    fun `unknown signatures remain visible but never become accepted`() {
        val mind = BeetleMind()

        repeat(8) { index ->
            val output = mind.process(
                BeetleMindInput.UnknownSignature("bfm-unknown"),
                nowMillis = index * 50L,
            )

            assertEquals("bfm-unknown?", output.thought.presentation)
            assertNull(output.acceptedSignature)
        }
    }

    @Test
    fun `ambiguous evidence shows candidate and resets consensus`() {
        val mind = BeetleMind(BeetleMindSettings(requiredConsensusFrames = 3))
        mind.process(knownAceOfSpades(), nowMillis = 100L)
        mind.process(knownAceOfSpades(), nowMillis = 150L)

        val ambiguous = mind.process(
            BeetleMindInput.Ambiguous(candidateSignature = "bfm1550"),
            nowMillis = 200L,
        )
        val restarted = mind.process(knownAceOfSpades(), nowMillis = 250L)

        assertIs<BeetleThought.Uncertain>(ambiguous.thought)
        assertEquals("bfm1550~", ambiguous.thought.presentation)
        assertEquals("♠A?", restarted.thought.presentation)
        assertNull(restarted.acceptedSignature)
    }

    @Test
    fun `ambiguous evidence without candidate blanks thought`() {
        val output = BeetleMind().process(
            BeetleMindInput.Ambiguous(candidateSignature = null),
            nowMillis = 100L,
        )

        assertEquals(BeetleThought.Blank, output.thought)
    }

    @Test
    fun `not found conversion failure and reset blank thought and consensus`() {
        val resetInputs = listOf(
            BeetleMindInput.NotFound,
            BeetleMindInput.ConversionFailed,
            BeetleMindInput.Reset,
        )

        resetInputs.forEach { resetInput ->
            val mind = BeetleMind(BeetleMindSettings(requiredConsensusFrames = 3))
            mind.process(knownAceOfSpades(), nowMillis = 100L)
            mind.process(knownAceOfSpades(), nowMillis = 150L)

            val reset = mind.process(resetInput, nowMillis = 200L)
            val restarted = mind.process(knownAceOfSpades(), nowMillis = 250L)

            assertEquals(BeetleThought.Blank, reset.thought)
            assertEquals("♠A?", restarted.thought.presentation)
            assertNull(restarted.acceptedSignature)
        }
    }

    @Test
    fun `dream topics are typed and reset pondering`() {
        BeetleDream.entries.forEach { topic ->
            val mind = BeetleMind(BeetleMindSettings(requiredConsensusFrames = 3))
            mind.process(knownAceOfSpades(), nowMillis = 100L)
            mind.process(knownAceOfSpades(), nowMillis = 150L)

            val dream = mind.process(BeetleMindInput.Dream(topic), nowMillis = 200L)
            val restarted = mind.process(knownAceOfSpades(), nowMillis = 250L)

            assertEquals(BeetleThought.Dreaming(topic), dream.thought)
            assertEquals(topic.presentation, dream.thought.presentation)
            assertEquals("♠A?", restarted.thought.presentation)
        }
    }

    @Test
    fun `accepted signature is debounced until the window expires`() {
        val mind = BeetleMind()

        mind.process(knownAceOfSpades(), nowMillis = 0L)
        val accepted = mind.process(knownAceOfSpades(), nowMillis = 100L)
        val debounced = mind.process(knownAceOfSpades(), nowMillis = 1_099L)
        val acceptedAtBoundary = mind.process(knownAceOfSpades(), nowMillis = 1_100L)

        assertEquals("bfm1549", accepted.acceptedSignature?.rawSignature)
        assertNull(debounced.acceptedSignature)
        assertEquals("♠A.", debounced.thought.presentation)
        assertEquals("bfm1549", acceptedAtBoundary.acceptedSignature?.rawSignature)
    }

    @Test
    fun `dream does not erase accepted signature debounce`() {
        val mind = BeetleMind()
        mind.process(knownAceOfSpades(), nowMillis = 0L)
        mind.process(knownAceOfSpades(), nowMillis = 100L)
        mind.process(BeetleMindInput.Dream(BeetleDream.EYES), nowMillis = 150L)

        mind.process(knownAceOfSpades(), nowMillis = 200L)
        val repeated = mind.process(knownAceOfSpades(), nowMillis = 250L)

        assertNull(repeated.acceptedSignature)
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
            BeetleMind().process(
                input = knownAceOfSpades(),
                nowMillis = 100L,
                requiredConsensusFrames = 7,
            )
        }
    }

    private fun knownAceOfSpades(): BeetleMindInput.KnownSignature =
        BeetleMindInput.KnownSignature(
            rawSignature = "bfm1549",
            cardId = CardId.parse("SA"),
        )
}
