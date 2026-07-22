package org.itroboc.app

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import org.itroboc.vision.BarcodeDecodeResult
import org.itroboc.vision.BarcodeDebugInfo
import org.itroboc.vision.BarcodeRoi
import org.itroboc.vision.DetectedSignature
import java.io.File

internal fun FrameDebugInfo.describe(): String =
    "Frame: ${width}x${height} rot=$rotationDegrees ts=$timestampNanos scan=$requestedByScan"

internal fun CameraScanOutcome.describe(): String = when (this) {
    is CameraScanOutcome.Decoded -> when (val result = decodeResult) {
        is BarcodeDecodeResult.Found -> buildString {
            append("Detected ")
            append(result.signature.rawSignature)
            append(" (")
            append(result.signature.confidence.formatConfidence())
            append(")")
            result.signature.debug?.normalizedPattern?.let { pattern ->
                append(" pattern=")
                append(pattern)
            }
            result.signature.debug?.reverseSignature?.let { reverseSignature ->
                append(" reverse=")
                append(reverseSignature)
            }
        }
        is BarcodeDecodeResult.NotFound -> result.reason
        is BarcodeDecodeResult.Ambiguous -> buildString {
            append("Ambiguous pattern: ")
            append(result.candidates.joinToString(" / ") { it.rawSignature })
        }
    }
    is CameraScanOutcome.ConversionFailed -> "Guide ROI extraction failed: $reason"
}

private fun Double.formatConfidence(): String = "%.2f".format(this)

internal data class AdminEditScanDebugRecord(
    val timestampMillis: Long,
    val selectedCard: String,
    val frameWidth: Int,
    val frameHeight: Int,
    val frameRotation: Int,
    val frameTimestampNanos: Long,
    val roi: BarcodeRoi?,
    val decodeResultType: String,
    val roiAngleDeg: Double = 0.0,
    val rawSignature: String? = null,
    val confidence: Double? = null,
    val failureReason: String? = null,
    val thresholdMode: String? = null,
    val thresholdValue: Int? = null,
    val normalizedPattern: String? = null,
    val blackRuns: List<String> = emptyList(),
    val ambiguousCandidates: List<String> = emptyList(),
    val signatureModel: String? = null,
    val reverseSignature: String? = null,
    val grid13FwdBitsPreSentinel: String? = null,
    val grid13FwdBits: String? = null,
    val grid13RevBits: String? = null,
    val grid13FwdHex: String? = null,
    val grid13RevHex: String? = null,
    val rl2: String? = null,
    val blackRunsPx: List<Int> = emptyList(),
    val whiteGapsPx: List<Int> = emptyList(),
    val blackRunCentersPx: List<Double> = emptyList(),
    val activeStartX: Int? = null,
    val activeEndX: Int? = null,
    val activeSpanPx: Int? = null,
    val sentinelValid: Boolean? = null,
    val sentinelIssues: List<String> = emptyList(),
    val sentinelRepairApplied: Boolean = false,
    val sentinelRepairReason: String? = null,
    val scanlineAgreement: Double? = null,
    val ambiguous: Boolean = false,
    val warnings: List<String> = emptyList(),
    val deckProfileMatchCount: Int? = null,
) {
    fun toJsonLine(): String {
        val fields = mutableListOf<String>()
        fields += jsonField("timestampMillis", timestampMillis.toString(), raw = true)
        fields += jsonField("selectedCard", selectedCard)
        fields += jsonField("frameWidth", frameWidth.toString(), raw = true)
        fields += jsonField("frameHeight", frameHeight.toString(), raw = true)
        fields += jsonField("frameRotation", frameRotation.toString(), raw = true)
        fields += jsonField("frameTimestampNanos", frameTimestampNanos.toString(), raw = true)
        fields += jsonField("decodeResultType", decodeResultType)
        fields += jsonField("roiAngleDeg", formatConfidenceForJson(roiAngleDeg), raw = true)
        roi?.let {
            fields += jsonField("cropWidthPx", it.width.toString(), raw = true)
            fields += jsonField("cropHeightPx", it.height.toString(), raw = true)
            fields += "\"roi\":{" +
                listOf(
                    jsonField("x", it.x.toString(), raw = true),
                    jsonField("y", it.y.toString(), raw = true),
                    jsonField("width", it.width.toString(), raw = true),
                    jsonField("height", it.height.toString(), raw = true),
                ).joinToString(",") +
                "}"
        }
        rawSignature?.let { fields += jsonField("rawSignature", it) }
        confidence?.let { fields += jsonField("confidence", formatConfidenceForJson(it), raw = true) }
        failureReason?.let { fields += jsonField("failureReason", it) }
        thresholdMode?.let { fields += jsonField("thresholdMode", it) }
        thresholdValue?.let { fields += jsonField("thresholdValue", it.toString(), raw = true) }
        normalizedPattern?.let { fields += jsonField("normalizedPattern", it) }
        signatureModel?.let { fields += jsonField("signatureModel", it) }
        reverseSignature?.let { fields += jsonField("reverseSignature", it) }
        grid13FwdBitsPreSentinel?.let { fields += jsonField("grid13FwdBitsPreSentinel", it) }
        grid13FwdBits?.let { fields += jsonField("grid13FwdBits", it) }
        grid13RevBits?.let { fields += jsonField("grid13RevBits", it) }
        grid13FwdHex?.let { fields += jsonField("grid13FwdHex", it) }
        grid13RevHex?.let { fields += jsonField("grid13RevHex", it) }
        rl2?.let { fields += jsonField("rl2", it) }
        activeStartX?.let { fields += jsonField("activeStartX", it.toString(), raw = true) }
        activeEndX?.let { fields += jsonField("activeEndX", it.toString(), raw = true) }
        activeSpanPx?.let { fields += jsonField("activeSpanPx", it.toString(), raw = true) }
        sentinelValid?.let { fields += jsonField("sentinelValid", it.toString(), raw = true) }
        fields += jsonField("sentinelRepairApplied", sentinelRepairApplied.toString(), raw = true)
        sentinelRepairReason?.let { fields += jsonField("sentinelRepairReason", it) }
        fields += scanlineAgreement?.let {
            jsonField("scanlineAgreement", formatConfidenceForJson(it), raw = true)
        } ?: jsonNullField("scanlineAgreement")
        fields += jsonField("ambiguous", ambiguous.toString(), raw = true)
        deckProfileMatchCount?.let {
            fields += jsonField("deckProfileMatchCount", it.toString(), raw = true)
        }
        if (blackRuns.isNotEmpty()) {
            fields += "\"blackRuns\":" + blackRuns.toJsonArray()
        }
        if (blackRunsPx.isNotEmpty()) {
            fields += "\"blackRunsPx\":" + blackRunsPx.toJsonIntArray()
        }
        if (whiteGapsPx.isNotEmpty()) {
            fields += "\"whiteGapsPx\":" + whiteGapsPx.toJsonIntArray()
        }
        if (blackRunCentersPx.isNotEmpty()) {
            fields += "\"blackRunCentersPx\":" + blackRunCentersPx.toJsonDoubleArray()
        }
        if (ambiguousCandidates.isNotEmpty()) {
            fields += "\"ambiguousCandidates\":" + ambiguousCandidates.toJsonArray()
        }
        if (sentinelIssues.isNotEmpty()) {
            fields += "\"sentinelIssues\":" + sentinelIssues.toJsonArray()
        }
        if (warnings.isNotEmpty()) {
            fields += "\"warnings\":" + warnings.toJsonArray()
        }
        return "{${fields.joinToString(",")}}"
    }
}

internal class AdminEditScanDebugLogManager(private val context: Context) {
    val logFile: File by lazy {
        val baseDir = context.getExternalFilesDir("debug") ?: File(context.filesDir, "debug")
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }
        File(baseDir, "admin-edit-scan-log.jsonl")
    }

    fun clearLog() {
        logFile.writeText("")
    }

    fun appendScanRecord(
        selectedCard: String,
        outcome: CameraScanOutcome,
        deckProfileMatchCount: Int? = null,
    ) {
        val record = outcome.toDebugRecord(
            selectedCard = selectedCard,
            timestampMillis = System.currentTimeMillis(),
            deckProfileMatchCount = deckProfileMatchCount,
        )
        logFile.appendText(record.toJsonLine() + "\n")
    }

    fun createShareIntent(): Intent? {
        if (!logFile.exists() || logFile.length() == 0L) {
            return null
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            logFile,
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/x-ndjson"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "ITROBOC Admin::Edit scan debug log")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}

internal object AdminEditScanDebugLogStartup {
    private var clearedForProcess = false

    @Synchronized
    fun clearOnce(context: Context) {
        if (clearedForProcess) {
            return
        }
        AdminEditScanDebugLogManager(context.applicationContext).clearLog()
        clearedForProcess = true
    }
}

internal fun CameraScanOutcome.toDebugRecord(
    selectedCard: String,
    timestampMillis: Long,
    deckProfileMatchCount: Int? = null,
): AdminEditScanDebugRecord = when (this) {
    is CameraScanOutcome.Decoded -> when (val result = decodeResult) {
        is BarcodeDecodeResult.Found -> result.signature.toDebugRecord(
            timestampMillis = timestampMillis,
            selectedCard = selectedCard,
            frameDebugInfo = frameDebugInfo,
            roi = roi,
            deckProfileMatchCount = deckProfileMatchCount,
        )
        is BarcodeDecodeResult.NotFound -> AdminEditScanDebugRecord(
            timestampMillis = timestampMillis,
            selectedCard = selectedCard,
            frameWidth = frameDebugInfo.width,
            frameHeight = frameDebugInfo.height,
            frameRotation = frameDebugInfo.rotationDegrees,
            frameTimestampNanos = frameDebugInfo.timestampNanos,
            roi = roi,
            decodeResultType = "NotFound",
            failureReason = result.reason,
            thresholdMode = result.debug.toThresholdMode(),
            thresholdValue = result.debug?.threshold,
            normalizedPattern = result.debug?.normalizedPattern,
            blackRuns = result.debug.toBlackRunStrings(),
            signatureModel = result.debug?.signatureModel,
            reverseSignature = result.debug?.reverseSignature,
            grid13FwdBitsPreSentinel = result.debug?.grid13FwdBitsPreSentinel,
            grid13FwdBits = result.debug?.grid13FwdBits,
            grid13RevBits = result.debug?.grid13RevBits,
            grid13FwdHex = result.debug?.grid13FwdHex,
            grid13RevHex = result.debug?.grid13RevHex,
            rl2 = result.debug?.rl2,
            blackRunsPx = result.debug?.blackRunsPx.orEmpty(),
            whiteGapsPx = result.debug?.whiteGapsPx.orEmpty(),
            blackRunCentersPx = result.debug?.blackRunCentersPx.orEmpty(),
            activeStartX = result.debug?.activeStartX,
            activeEndX = result.debug?.activeEndX,
            activeSpanPx = result.debug?.activeSpanPx,
            sentinelValid = result.debug?.sentinelValid,
            sentinelIssues = result.debug?.sentinelIssues.orEmpty(),
            sentinelRepairApplied = result.debug?.sentinelRepairApplied == true,
            sentinelRepairReason = result.debug?.sentinelRepairReason,
            ambiguous = false,
            warnings = result.debug?.warnings.orEmpty(),
            deckProfileMatchCount = deckProfileMatchCount,
        )
        is BarcodeDecodeResult.Ambiguous -> AdminEditScanDebugRecord(
            timestampMillis = timestampMillis,
            selectedCard = selectedCard,
            frameWidth = frameDebugInfo.width,
            frameHeight = frameDebugInfo.height,
            frameRotation = frameDebugInfo.rotationDegrees,
            frameTimestampNanos = frameDebugInfo.timestampNanos,
            roi = roi,
            decodeResultType = "Ambiguous",
            failureReason = result.reason,
            thresholdMode = result.debug.toThresholdMode(),
            thresholdValue = result.debug?.threshold,
            normalizedPattern = result.debug?.normalizedPattern,
            blackRuns = result.debug.toBlackRunStrings(),
            ambiguousCandidates = result.candidates.map { it.rawSignature },
            signatureModel = result.debug?.signatureModel,
            reverseSignature = result.debug?.reverseSignature,
            grid13FwdBitsPreSentinel = result.debug?.grid13FwdBitsPreSentinel,
            grid13FwdBits = result.debug?.grid13FwdBits,
            grid13RevBits = result.debug?.grid13RevBits,
            grid13FwdHex = result.debug?.grid13FwdHex,
            grid13RevHex = result.debug?.grid13RevHex,
            rl2 = result.debug?.rl2,
            blackRunsPx = result.debug?.blackRunsPx.orEmpty(),
            whiteGapsPx = result.debug?.whiteGapsPx.orEmpty(),
            blackRunCentersPx = result.debug?.blackRunCentersPx.orEmpty(),
            activeStartX = result.debug?.activeStartX,
            activeEndX = result.debug?.activeEndX,
            activeSpanPx = result.debug?.activeSpanPx,
            sentinelValid = result.debug?.sentinelValid,
            sentinelIssues = result.debug?.sentinelIssues.orEmpty(),
            sentinelRepairApplied = result.debug?.sentinelRepairApplied == true,
            sentinelRepairReason = result.debug?.sentinelRepairReason,
            ambiguous = true,
            warnings = result.debug?.warnings.orEmpty(),
            deckProfileMatchCount = deckProfileMatchCount,
        )
    }
    is CameraScanOutcome.ConversionFailed -> AdminEditScanDebugRecord(
        timestampMillis = timestampMillis,
        selectedCard = selectedCard,
        frameWidth = frameDebugInfo.width,
        frameHeight = frameDebugInfo.height,
        frameRotation = frameDebugInfo.rotationDegrees,
        frameTimestampNanos = frameDebugInfo.timestampNanos,
        roi = roi,
        decodeResultType = "ConversionFailed",
        failureReason = reason,
        ambiguous = false,
        deckProfileMatchCount = deckProfileMatchCount,
    )
}

private fun DetectedSignature.toDebugRecord(
    timestampMillis: Long,
    selectedCard: String,
    frameDebugInfo: FrameDebugInfo,
    roi: BarcodeRoi,
    deckProfileMatchCount: Int?,
): AdminEditScanDebugRecord = AdminEditScanDebugRecord(
    timestampMillis = timestampMillis,
    selectedCard = selectedCard,
    frameWidth = frameDebugInfo.width,
    frameHeight = frameDebugInfo.height,
    frameRotation = frameDebugInfo.rotationDegrees,
    frameTimestampNanos = frameDebugInfo.timestampNanos,
    roi = roi,
    decodeResultType = "Found",
    rawSignature = rawSignature,
    confidence = confidence,
    thresholdMode = debug.toThresholdMode(),
    thresholdValue = debug?.threshold,
    normalizedPattern = debug?.normalizedPattern,
    blackRuns = debug.toBlackRunStrings(),
    signatureModel = debug?.signatureModel,
    reverseSignature = debug?.reverseSignature,
    grid13FwdBitsPreSentinel = debug?.grid13FwdBitsPreSentinel,
    grid13FwdBits = debug?.grid13FwdBits,
    grid13RevBits = debug?.grid13RevBits,
    grid13FwdHex = debug?.grid13FwdHex,
    grid13RevHex = debug?.grid13RevHex,
    rl2 = debug?.rl2,
    blackRunsPx = debug?.blackRunsPx.orEmpty(),
    whiteGapsPx = debug?.whiteGapsPx.orEmpty(),
    blackRunCentersPx = debug?.blackRunCentersPx.orEmpty(),
    activeStartX = debug?.activeStartX,
    activeEndX = debug?.activeEndX,
    activeSpanPx = debug?.activeSpanPx,
    sentinelValid = debug?.sentinelValid,
    sentinelIssues = debug?.sentinelIssues.orEmpty(),
    sentinelRepairApplied = debug?.sentinelRepairApplied == true,
    sentinelRepairReason = debug?.sentinelRepairReason,
    ambiguous = false,
    warnings = debug?.warnings.orEmpty(),
    deckProfileMatchCount = deckProfileMatchCount,
)

private fun BarcodeDebugInfo?.toBlackRunStrings(): List<String> =
    this?.blackRuns?.map { "${it.first}..${it.last}" }.orEmpty()

private fun BarcodeDebugInfo?.toThresholdMode(): String? =
    this?.let { debug ->
        debug.threshold ?: return@let null
        if (debug.signatureModel == "grid13-v2") {
            "adaptive-ink-midrange"
        } else {
            "intensity-midrange"
        }
    }

private fun List<String>.toJsonArray(): String =
    joinToString(prefix = "[", postfix = "]", separator = ",") { value ->
        "\"${value.escapeJson()}\""
    }

private fun List<Int>.toJsonIntArray(): String =
    joinToString(prefix = "[", postfix = "]", separator = ",")

private fun List<Double>.toJsonDoubleArray(): String =
    joinToString(prefix = "[", postfix = "]", separator = ",") { value ->
        formatConfidenceForJson(value)
    }

private fun jsonField(
    name: String,
    value: String,
    raw: Boolean = false,
): String = if (raw) {
    "\"${name.escapeJson()}\":$value"
} else {
    "\"${name.escapeJson()}\":\"${value.escapeJson()}\""
}

private fun jsonNullField(name: String): String = "\"${name.escapeJson()}\":null"

private fun formatConfidenceForJson(value: Double): String = "%.4f".format(value)

private fun String.escapeJson(): String = buildString(length) {
    for (char in this@escapeJson) {
        when (char) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(char)
        }
    }
}
