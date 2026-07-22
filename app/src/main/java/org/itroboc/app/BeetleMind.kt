package org.itroboc.app

import org.itroboc.core.CardId

internal data class BeetleMindSettings(
    val requiredConsensusFrames: Int = DEFAULT_REQUIRED_CONSENSUS_FRAMES,
    val debounceWindowMillis: Long = DEFAULT_DEBOUNCE_WINDOW_MILLIS,
) {
    init {
        require(requiredConsensusFrames in ALLOWED_CONSENSUS_FRAMES) {
            "requiredConsensusFrames must be within $ALLOWED_CONSENSUS_FRAMES"
        }
        require(debounceWindowMillis >= 0L) {
            "debounceWindowMillis must be non-negative"
        }
    }

    companion object {
        val ALLOWED_CONSENSUS_FRAMES = 2..6
        const val DEFAULT_REQUIRED_CONSENSUS_FRAMES = 2
        const val DEFAULT_DEBOUNCE_WINDOW_MILLIS = 1_000L
    }
}

internal enum class BeetleDream(
    val presentation: String,
) {
    EYES("Eyes"),
    HANDS("Hands"),
    WIND("Wind"),
    JOKER("Joker"),
}

internal sealed interface BeetleMindInput {
    data class KnownSignature(
        val rawSignature: String,
        val cardId: CardId,
    ) : BeetleMindInput

    data class UnknownSignature(
        val rawSignature: String,
    ) : BeetleMindInput

    data class Ambiguous(
        val candidateSignature: String?,
    ) : BeetleMindInput

    data object NotFound : BeetleMindInput

    data object ConversionFailed : BeetleMindInput

    data class Dream(
        val topic: BeetleDream,
    ) : BeetleMindInput

    data object Reset : BeetleMindInput
}

internal sealed interface BeetleThought {
    val presentation: String

    data object Blank : BeetleThought {
        override val presentation: String = "0"
    }

    data class Pondering(
        val subject: String,
        val evidenceCount: Int,
    ) : BeetleThought {
        init {
            require(evidenceCount > 0) { "evidenceCount must be positive" }
        }

        override val presentation: String = subject + when (evidenceCount) {
            1 -> "?"
            2 -> "??"
            else -> "???"
        }
    }

    data class Certain(
        val subject: String,
    ) : BeetleThought {
        override val presentation: String = "$subject."
    }

    data class Uncertain(
        val candidateSignature: String,
    ) : BeetleThought {
        override val presentation: String = "$candidateSignature~"
    }

    data class Dreaming(
        val topic: BeetleDream,
    ) : BeetleThought {
        override val presentation: String = topic.presentation
    }
}

internal data class AcceptedSignature(
    val rawSignature: String,
    val cardId: CardId,
)

internal data class BeetleMindOutput(
    val thought: BeetleThought,
    val acceptedSignature: AcceptedSignature? = null,
)

/** Pure Kotlin evidence-trust state machine. It does not own camera or board behavior. */
internal class BeetleMind(
    private val settings: BeetleMindSettings = BeetleMindSettings(),
) {
    var thought: BeetleThought = BeetleThought.Blank
        private set

    private var ponderingSignature: String? = null
    private var ponderingCount: Int = 0
    private var lastAcceptedRawSignature: String? = null
    private var lastAcceptedAtMillis: Long = 0L

    fun process(
        input: BeetleMindInput,
        nowMillis: Long,
        requiredConsensusFrames: Int = settings.requiredConsensusFrames,
    ): BeetleMindOutput {
        require(requiredConsensusFrames in BeetleMindSettings.ALLOWED_CONSENSUS_FRAMES) {
            "requiredConsensusFrames must be within ${BeetleMindSettings.ALLOWED_CONSENSUS_FRAMES}"
        }

        return when (input) {
            is BeetleMindInput.KnownSignature -> processKnownSignature(
                input = input,
                nowMillis = nowMillis,
                requiredConsensusFrames = requiredConsensusFrames,
            )
            is BeetleMindInput.UnknownSignature -> {
                resetPondering()
                publish(BeetleThought.Pondering(input.rawSignature, evidenceCount = 1))
            }
            is BeetleMindInput.Ambiguous -> {
                resetPondering()
                val nextThought = input.candidateSignature
                    ?.let(BeetleThought::Uncertain)
                    ?: BeetleThought.Blank
                publish(nextThought)
            }
            BeetleMindInput.NotFound,
            BeetleMindInput.ConversionFailed,
            BeetleMindInput.Reset,
            -> {
                resetPondering()
                publish(BeetleThought.Blank)
            }
            is BeetleMindInput.Dream -> {
                resetPondering()
                publish(BeetleThought.Dreaming(input.topic))
            }
        }
    }

    private fun processKnownSignature(
        input: BeetleMindInput.KnownSignature,
        nowMillis: Long,
        requiredConsensusFrames: Int,
    ): BeetleMindOutput {
        if (input.rawSignature == ponderingSignature) {
            ponderingCount++
        } else {
            ponderingSignature = input.rawSignature
            ponderingCount = 1
        }

        val cardName = "${input.cardId.suit.prettySymbol}${input.cardId.rank.symbol}"
        if (ponderingCount < requiredConsensusFrames) {
            return publish(BeetleThought.Pondering(cardName, ponderingCount))
        }

        val acceptedSignature = if (
            input.rawSignature == lastAcceptedRawSignature &&
            nowMillis - lastAcceptedAtMillis < settings.debounceWindowMillis
        ) {
            null
        } else {
            lastAcceptedRawSignature = input.rawSignature
            lastAcceptedAtMillis = nowMillis
            AcceptedSignature(
                rawSignature = input.rawSignature,
                cardId = input.cardId,
            )
        }
        return publish(
            nextThought = BeetleThought.Certain(cardName),
            acceptedSignature = acceptedSignature,
        )
    }

    private fun resetPondering() {
        ponderingSignature = null
        ponderingCount = 0
    }

    private fun publish(
        nextThought: BeetleThought,
        acceptedSignature: AcceptedSignature? = null,
    ): BeetleMindOutput {
        thought = nextThought
        return BeetleMindOutput(
            thought = nextThought,
            acceptedSignature = acceptedSignature,
        )
    }
}
