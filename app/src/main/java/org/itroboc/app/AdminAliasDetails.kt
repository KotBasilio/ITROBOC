package org.itroboc.app

import org.itroboc.vision.DetectedSignature

internal data class AdminAliasScanEvidence(
    val signatureModel: String?,
    val reverseSignature: String?,
    val grid13FwdBits: String?,
    val grid13RevBits: String?,
    val rl2: String?,
    val blackRunsPx: List<Int>,
    val whiteGapsPx: List<Int>,
    val confidence: Double,
    val warnings: List<String>,
)

internal data class AdminAliasDetails(
    val rawSignature: String,
    val evidence: AdminAliasScanEvidence?,
) {
    fun displayLines(): List<String> = buildList {
        add("Alias: $rawSignature")
        if (evidence == null) {
            add("No scan evidence is retained for this alias in the current edit session.")
            return@buildList
        }

        add("Model: ${evidence.signatureModel ?: "unknown"}")
        evidence.grid13FwdBits?.let { add("Forward bits: $it") }
        evidence.grid13RevBits?.let { add("Reverse bits: $it") }
        evidence.reverseSignature?.let { add("Reverse token: $it") }
        evidence.rl2?.let { add("RL2: $it") }
        if (evidence.blackRunsPx.isNotEmpty()) {
            add("Black runs: ${evidence.blackRunsPx.joinToString(prefix = "[", postfix = "]")}")
        }
        if (evidence.whiteGapsPx.isNotEmpty()) {
            add("White gaps: ${evidence.whiteGapsPx.joinToString(prefix = "[", postfix = "]")}")
        }
        add("Confidence: ${evidence.confidence.formatAliasConfidence()}")
        if (evidence.warnings.isNotEmpty()) {
            add("Warnings: ${evidence.warnings.joinToString()}")
        }
    }
}

internal fun DetectedSignature.toAdminAliasScanEvidence(): AdminAliasScanEvidence =
    AdminAliasScanEvidence(
        signatureModel = debug?.signatureModel,
        reverseSignature = debug?.reverseSignature,
        grid13FwdBits = debug?.grid13FwdBits,
        grid13RevBits = debug?.grid13RevBits,
        rl2 = debug?.rl2,
        blackRunsPx = debug?.blackRunsPx.orEmpty(),
        whiteGapsPx = debug?.whiteGapsPx.orEmpty(),
        confidence = confidence,
        warnings = debug?.warnings.orEmpty(),
    )

private fun Double.formatAliasConfidence(): String = "%.2f".format(this)
