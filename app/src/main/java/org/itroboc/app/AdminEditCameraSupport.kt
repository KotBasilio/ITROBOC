package org.itroboc.app

import androidx.camera.core.ImageProxy
import org.itroboc.vision.BarcodeDecodeResult
import org.itroboc.vision.BarcodeDecoder
import org.itroboc.vision.BarcodeRoi
import org.itroboc.vision.GrayImage
import org.itroboc.vision.SimpleBarRunBarcodeDecoder
import kotlin.math.roundToInt

internal val adminEditGuideRectSpec = GuideRectSpec(
    widthFraction = 0.8f,
    heightFraction = 0.15f,
)

internal data class GuideRectSpec(
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

    data class Decoded(
        override val frameDebugInfo: FrameDebugInfo,
        val roi: BarcodeRoi,
        val decodeResult: BarcodeDecodeResult,
    ) : CameraScanOutcome

    data class ConversionFailed(
        override val frameDebugInfo: FrameDebugInfo,
        val reason: String,
    ) : CameraScanOutcome
}

internal class AdminEditCameraFrameDecoder(
    private val decoder: BarcodeDecoder = SimpleBarRunBarcodeDecoder(),
    private val guideRectSpec: GuideRectSpec = adminEditGuideRectSpec,
) {
    fun decode(imageProxy: ImageProxy): CameraScanOutcome {
        val frameDebugInfo = imageProxy.toFrameDebugInfo()

        return try {
            val roi = centeredBarcodeRoi(
                imageWidth = imageProxy.width,
                imageHeight = imageProxy.height,
                guideRectSpec = guideRectSpec,
            )
            val grayImage = imageProxy.toGrayImage(roi)
            CameraScanOutcome.Decoded(
                frameDebugInfo = frameDebugInfo,
                roi = roi,
                decodeResult = decoder.decode(grayImage),
            )
        } catch (error: Exception) {
            CameraScanOutcome.ConversionFailed(
                frameDebugInfo = frameDebugInfo,
                reason = error.message ?: "Failed to extract guide ROI",
            )
        }
    }
}

internal fun centeredBarcodeRoi(
    imageWidth: Int,
    imageHeight: Int,
    guideRectSpec: GuideRectSpec,
): BarcodeRoi {
    require(imageWidth > 0) { "imageWidth must be positive" }
    require(imageHeight > 0) { "imageHeight must be positive" }

    val roiWidth = (imageWidth * guideRectSpec.widthFraction)
        .roundToInt()
        .coerceIn(1, imageWidth)
    val roiHeight = (imageHeight * guideRectSpec.heightFraction)
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
    guideRectSpec: GuideRectSpec,
): GuideRectBounds {
    val width = containerWidth * guideRectSpec.widthFraction
    val height = containerHeight * guideRectSpec.heightFraction
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
