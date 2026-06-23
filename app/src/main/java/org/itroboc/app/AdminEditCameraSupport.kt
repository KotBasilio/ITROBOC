package org.itroboc.app

import android.content.Context
import android.content.Intent
import androidx.camera.core.ImageProxy
import androidx.core.content.FileProvider
import org.itroboc.vision.BarcodeDecodeResult
import org.itroboc.vision.BarcodeDebugInfo
import org.itroboc.vision.BarcodeDecoder
import org.itroboc.vision.BarcodeRoi
import org.itroboc.vision.DetectedSignature
import org.itroboc.vision.GrayImage
import org.itroboc.vision.SimpleBarRunBarcodeDecoder
import java.io.File
import kotlin.math.roundToInt

internal val adminScanGuideSpec = AdminScanGuideSpec(
    widthFraction = 0.25f,
    heightFraction = 0.10f,
)

internal data class AdminScanGuideSpec(
    val widthFraction: Float,
    val heightFraction: Float,
) {
    init {
        require(widthFraction in 0f..1f) { "widthFraction must be within [0, 1]" }
        require(heightFraction in 0f..1f) { "heightFraction must be within [0, 1]" }
    }
}

internal data class GuideRectBounds(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

internal data class FrameDebugInfo(
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    val timestampNanos: Long,
    val requestedByScan: Boolean,
)

internal sealed interface CameraScanOutcome {
    val frameDebugInfo: FrameDebugInfo
    val roi: BarcodeRoi?

    data class Decoded(
        override val frameDebugInfo: FrameDebugInfo,
        override val roi: BarcodeRoi,
        val decodeResult: BarcodeDecodeResult,
    ) : CameraScanOutcome

    data class ConversionFailed(
        override val frameDebugInfo: FrameDebugInfo,
        override val roi: BarcodeRoi?,
        val reason: String,
    ) : CameraScanOutcome
}

internal class AdminEditCameraFrameDecoder(
    private val decoder: BarcodeDecoder = SimpleBarRunBarcodeDecoder(),
    private val guideSpec: AdminScanGuideSpec = adminScanGuideSpec,
) {
    fun decode(imageProxy: ImageProxy): CameraScanOutcome {
        val frameDebugInfo = imageProxy.toFrameDebugInfo()
        val roi = centeredBarcodeRoi(
            imageWidth = imageProxy.width,
            imageHeight = imageProxy.height,
            guideSpec = guideSpec,
        )

        return try {
            val grayImage = imageProxy.toGrayImage(roi)
            CameraScanOutcome.Decoded(
                frameDebugInfo = frameDebugInfo,
                roi = roi,
                decodeResult = decoder.decode(grayImage),
            )
        } catch (error: Exception) {
            CameraScanOutcome.ConversionFailed(
                frameDebugInfo = frameDebugInfo,
                roi = roi,
                reason = error.message ?: "Failed to extract guide ROI",
            )
        }
    }
}

internal fun centeredBarcodeRoi(
    imageWidth: Int,
    imageHeight: Int,
    guideSpec: AdminScanGuideSpec,
): BarcodeRoi {
    require(imageWidth > 0) { "imageWidth must be positive" }
    require(imageHeight > 0) { "imageHeight must be positive" }

    val roiWidth = (imageWidth * guideSpec.widthFraction)
        .roundToInt()
        .coerceIn(1, imageWidth)
    val roiHeight = (imageHeight * guideSpec.heightFraction)
        .roundToInt()
        .coerceIn(1, imageHeight)
    val x = ((imageWidth - roiWidth) / 2).coerceAtLeast(0)
    val y = ((imageHeight - roiHeight) / 2).coerceAtLeast(0)

    return BarcodeRoi(
        x = x,
        y = y,
        width = roiWidth,
        height = roiHeight,
    )
}

internal fun centeredGuideRectBounds(
    containerWidth: Float,
    containerHeight: Float,
    guideSpec: AdminScanGuideSpec,
): GuideRectBounds {
    val width = containerWidth * guideSpec.widthFraction
    val height = containerHeight * guideSpec.heightFraction
    return GuideRectBounds(
        left = (containerWidth - width) / 2f,
        top = (containerHeight - height) / 2f,
        width = width,
        height = height,
    )
}

internal fun extractGrayImageFromLumaBytes(
    imageWidth: Int,
    imageHeight: Int,
    lumaBytes: ByteArray,
    rowStride: Int,
    pixelStride: Int,
    roi: BarcodeRoi,
): GrayImage {
    require(pixelStride > 0) { "pixelStride must be positive" }
    require(rowStride > 0) { "rowStride must be positive" }
    require(roi.x >= 0 && roi.y >= 0) { "ROI origin must be non-negative" }
    require(roi.x + roi.width <= imageWidth) { "ROI exceeds image width" }
    require(roi.y + roi.height <= imageHeight) { "ROI exceeds image height" }

    val expectedLastIndex = ((roi.y + roi.height - 1) * rowStride) +
        ((roi.x + roi.width - 1) * pixelStride)
    require(expectedLastIndex < lumaBytes.size) {
        "Luma plane bytes do not cover requested ROI"
    }

    val pixels = ByteArray(roi.width * roi.height)
    var destinationIndex = 0

    for (y in roi.y until roi.y + roi.height) {
        val rowOffset = y * rowStride
        for (x in roi.x until roi.x + roi.width) {
            val sourceIndex = rowOffset + (x * pixelStride)
            pixels[destinationIndex++] = lumaBytes[sourceIndex]
        }
    }

    return GrayImage(
        width = roi.width,
        height = roi.height,
        pixels = pixels,
    )
}

internal fun ImageProxy.toFrameDebugInfo(): FrameDebugInfo =
    FrameDebugInfo(
        width = width,
        height = height,
        rotationDegrees = imageInfo.rotationDegrees,
        timestampNanos = imageInfo.timestamp,
        requestedByScan = true,
    )

private fun ImageProxy.toGrayImage(roi: BarcodeRoi): GrayImage {
    val lumaPlane = planes.firstOrNull()
        ?: error("Camera frame does not expose a luma plane")
    val buffer = lumaPlane.buffer.duplicate().apply { rewind() }
    val lumaBytes = ByteArray(buffer.remaining())
    buffer.get(lumaBytes)

    return extractGrayImageFromLumaBytes(
        imageWidth = width,
        imageHeight = height,
        lumaBytes = lumaBytes,
        rowStride = lumaPlane.rowStride,
        pixelStride = lumaPlane.pixelStride,
        roi = roi,
    )
}

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
    val rawSignature: String? = null,
    val confidence: Double? = null,
    val failureReason: String? = null,
    val normalizedPattern: String? = null,
    val blackRuns: List<String> = emptyList(),
    val ambiguousCandidates: List<String> = emptyList(),
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
        roi?.let {
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
        normalizedPattern?.let { fields += jsonField("normalizedPattern", it) }
        if (blackRuns.isNotEmpty()) {
            fields += "\"blackRuns\":" + blackRuns.toJsonArray()
        }
        if (ambiguousCandidates.isNotEmpty()) {
            fields += "\"ambiguousCandidates\":" + ambiguousCandidates.toJsonArray()
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

    fun appendScanRecord(
        selectedCard: String,
        outcome: CameraScanOutcome,
    ) {
        val record = outcome.toDebugRecord(
            selectedCard = selectedCard,
            timestampMillis = System.currentTimeMillis(),
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

internal fun CameraScanOutcome.toDebugRecord(
    selectedCard: String,
    timestampMillis: Long,
): AdminEditScanDebugRecord = when (this) {
    is CameraScanOutcome.Decoded -> when (val result = decodeResult) {
        is BarcodeDecodeResult.Found -> result.signature.toDebugRecord(
            timestampMillis = timestampMillis,
            selectedCard = selectedCard,
            frameDebugInfo = frameDebugInfo,
            roi = roi,
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
            normalizedPattern = result.debug?.normalizedPattern,
            blackRuns = result.debug.toBlackRunStrings(),
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
            normalizedPattern = result.debug?.normalizedPattern,
            blackRuns = result.debug.toBlackRunStrings(),
            ambiguousCandidates = result.candidates.map { it.rawSignature },
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
    )
}

private fun DetectedSignature.toDebugRecord(
    timestampMillis: Long,
    selectedCard: String,
    frameDebugInfo: FrameDebugInfo,
    roi: BarcodeRoi,
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
    normalizedPattern = debug?.normalizedPattern,
    blackRuns = debug.toBlackRunStrings(),
)

private fun BarcodeDebugInfo?.toBlackRunStrings(): List<String> =
    this?.blackRuns?.map { "${it.first}..${it.last}" }.orEmpty()

private fun List<String>.toJsonArray(): String =
    joinToString(prefix = "[", postfix = "]", separator = ",") { value ->
        "\"${value.escapeJson()}\""
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
