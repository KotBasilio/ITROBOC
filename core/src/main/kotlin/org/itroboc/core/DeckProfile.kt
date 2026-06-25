package org.itroboc.core

data class DeckProfile(
    private val signatureToCard: Map<String, CardId>,
    val metadata: DeckProfileMetadata = DeckProfileMetadata(),
) {
    fun lookup(signature: String): CardId? = signatureToCard[signature]

    fun mappingCount(): Int = signatureToCard.size

    fun rawSignatures(): Set<String> = signatureToCard.keys

    fun cardIds(): Set<CardId> = signatureToCard.values.toSet()

    fun toEditor(): DeckProfileEditor = DeckProfileEditor(signatureToCard, metadata)

    fun withMetadata(metadata: DeckProfileMetadata): DeckProfile =
        DeckProfile(signatureToCard = signatureToCard, metadata = metadata)

    fun toJson(): String = buildString {
        append("{\n")
        append("  \"metadata\": ${metadata.toJson().prependIndent("  ").trimStart()},\n")
        append("  \"signatureToCard\": {\n")
        val sortedMappings = signatureToCard.toList().sortedBy { it.first }
        sortedMappings.forEachIndexed { index, (sig, card) ->
            append("    \"$sig\": \"$card\"")
            if (index < sortedMappings.size - 1) append(",")
            append("\n")
        }
        append("  }\n")
        append("}")
    }

    companion object {
        fun fromJson(json: String): DeckProfile {
            val metadataJson = Regex("\"metadata\"\\s*:\\s*(\\{.*?\\})", RegexOption.DOT_MATCHES_ALL)
                .find(json)?.groupValues?.get(1) ?: "{}"
            val metadata = DeckProfileMetadata.fromJson(metadataJson)

            val mappingsJson = Regex("\"signatureToCard\"\\s*:\\s*\\{(.*?)\\}", RegexOption.DOT_MATCHES_ALL)
                .find(json)?.groupValues?.get(1) ?: ""
            val mappings = Regex("\"([^\"]+)\"\\s*:\\s*\"([^\"]+)\"")
                .findAll(mappingsJson)
                .associate { it.groupValues[1] to CardId.parse(it.groupValues[2]) }

            return DeckProfile(mappings, metadata)
        }
    }
}

data class DeckProfileMetadata(
    val profileId: String = "ad-hoc-profile",
    val displayName: String = "Ad-hoc Deck Profile",
    val isBuiltIn: Boolean = false,
    val isDemo: Boolean = false,
    val notes: String? = null,
    val signatureModel: String? = null,
) {
    fun toJson(): String = buildString {
        append("{\n")
        append("  \"profileId\": \"$profileId\",\n")
        append("  \"displayName\": \"$displayName\",\n")
        append("  \"isBuiltIn\": $isBuiltIn,\n")
        append("  \"isDemo\": $isDemo")
        notes?.let { append(",\n  \"notes\": \"${it.escapeJson()}\"") }
        signatureModel?.let { append(",\n  \"signatureModel\": \"$it\"") }
        append("\n}")
    }

    companion object {
        fun fromJson(json: String): DeckProfileMetadata =
            DeckProfileMetadata(
                profileId = json.stringField("profileId") ?: "imported-profile",
                displayName = json.stringField("displayName") ?: "Imported Profile",
                isBuiltIn = json.booleanField("isBuiltIn") ?: false,
                isDemo = json.booleanField("isDemo") ?: false,
                notes = json.stringField("notes")?.unescapeJson(),
                signatureModel = json.stringField("signatureModel")
            )

        private fun String.stringField(name: String): String? =
            Regex("\"$name\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(this)?.groupValues?.get(1)

        private fun String.booleanField(name: String): Boolean? =
            Regex("\"$name\"\\s*:\\s*(true|false)").find(this)?.groupValues?.get(1)?.toBoolean()

        private fun String.escapeJson(): String =
            this.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")

        private fun String.unescapeJson(): String =
            this.replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
    }
}

object DeckProfileSignatureModels {
    const val GRID13_V2: String = "grid13-v2"
    const val SYNTHETIC_DEMO_BRIDGE_52_V1: String = "synthetic-demo-bridge52-v1"
}
