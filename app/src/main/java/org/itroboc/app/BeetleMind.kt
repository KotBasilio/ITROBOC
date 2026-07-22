package org.itroboc.app

import org.itroboc.core.CardId

internal data class BeetleMindSettings(
    val requiredConsensusFrames: Int,
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

internal data class BeetleSignatureCandidate(
    val rawSignature: String,
    val confidence: Double,
)

internal sealed interface BeetleEvidence {
    data class Known(
        val rawSignature: String,
        val cardId: CardId,
        val confidence: Double,
    ) : BeetleEvidence

    data class Unknown(
        val rawSignature: String,
        val confidence: Double,
    ) : BeetleEvidence

    data class Ambiguous(
        val candidates: List<BeetleSignatureCandidate>,
    ) : BeetleEvidence

    data class NotFound(
        val reason: String,
    ) : BeetleEvidence

    data class ConversionFailure(
        val reason: String,
    ) : BeetleEvidence
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

internal data class AcceptedCardScan(
    val rawSignature: String,
    val cardId: CardId,
    val confidence: Double,
)

internal data class BeetleMindOutput(
    val thought: BeetleThought,
    val acceptedCardScan: AcceptedCardScan? = null,
)

/** Pure Kotlin evidence-trust state machine. It does not own camera or board behavior. */
internal class BeetleMind(
    private val nowMillis: () -> Long,
    private val settings: BeetleMindSettings,
) {
    var thought: BeetleThought = BeetleThought.Blank
        private set

    private var ponderingSignature: String? = null
    private var ponderingCount: Int = 0
    private var lastAcceptedRawSignature: String? = null
    private var lastAcceptedAtMillis: Long = 0L

    fun observe(
        evidence: BeetleEvidence,
        requiredConsensusFrames: Int = settings.requiredConsensusFrames,
    ): BeetleMindOutput {
        require(requiredConsensusFrames in BeetleMindSettings.ALLOWED_CONSENSUS_FRAMES) {
            "requiredConsensusFrames must be within ${BeetleMindSettings.ALLOWED_CONSENSUS_FRAMES}"
        }

        return when (evidence) {
            is BeetleEvidence.Known -> observeKnown(
                evidence = evidence,
                requiredConsensusFrames = requiredConsensusFrames,
            )
            is BeetleEvidence.Unknown -> {
                clearPondering()
                publish(BeetleThought.Pondering(evidence.rawSignature, evidenceCount = 1))
            }
            is BeetleEvidence.Ambiguous -> {
                clearPondering()
                val nextThought = evidence.candidates.firstOrNull()
                    ?.rawSignature
                    ?.let(BeetleThought::Uncertain)
                    ?: BeetleThought.Blank
                publish(nextThought)
            }
            is BeetleEvidence.NotFound,
            is BeetleEvidence.ConversionFailure,
            -> reset()
        }
    }

    fun dream(topic: BeetleDream): BeetleMindOutput {
        clearPondering()
        return publish(BeetleThought.Dreaming(topic))
    }

    fun reset(): BeetleMindOutput {
        clearPondering()
        return publish(BeetleThought.Blank)
    }

    private fun observeKnown(
        evidence: BeetleEvidence.Known,
        requiredConsensusFrames: Int,
    ): BeetleMindOutput {
        if (evidence.rawSignature == ponderingSignature) {
            ponderingCount++
        } else {
            ponderingSignature = evidence.rawSignature
            ponderingCount = 1
        }

        val cardName = "${evidence.cardId.suit.prettySymbol}${evidence.cardId.rank.symbol}"
        if (ponderingCount < requiredConsensusFrames) {
            return publish(BeetleThought.Pondering(cardName, ponderingCount))
        }

        val currentTimeMillis = nowMillis()
        val acceptedCardScan = if (
            evidence.rawSignature == lastAcceptedRawSignature &&
            currentTimeMillis - lastAcceptedAtMillis < settings.debounceWindowMillis
        ) {
            null
        } else {
            lastAcceptedRawSignature = evidence.rawSignature
            lastAcceptedAtMillis = currentTimeMillis
            AcceptedCardScan(
                rawSignature = evidence.rawSignature,
                cardId = evidence.cardId,
                confidence = evidence.confidence,
            )
        }
        return publish(
            nextThought = BeetleThought.Certain(cardName),
            acceptedCardScan = acceptedCardScan,
        )
    }

    private fun clearPondering() {
        ponderingSignature = null
        ponderingCount = 0
    }

    private fun publish(
        nextThought: BeetleThought,
        acceptedCardScan: AcceptedCardScan? = null,
    ): BeetleMindOutput {
        thought = nextThought
        return BeetleMindOutput(
            thought = nextThought,
            acceptedCardScan = acceptedCardScan,
        )
    }
}
