package org.itroboc.app

import android.util.Log
import androidx.camera.core.ImageProxy
import org.itroboc.vision.BarcodeDecodeResult
import org.itroboc.vision.BarcodeDecoder
import org.itroboc.vision.BarcodeRoi
import org.itroboc.vision.Grid13SlowDecoder
import org.itroboc.vision.GrayImage

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

/** Shared CameraX frame-to-verdict adapter used by Admin and TD. */
internal class CameraFrameDecoder(
    private val decoder: BarcodeDecoder = Grid13SlowDecoder(),
    private val guideSpec: CameraScanGuideSpec = barcodeScanGuideSpec,
) {
    fun decode(imageProxy: ImageProxy, reusablePixels: ByteArray): CameraScanOutcome {
        val startedAtNanos = System.nanoTime()
        val frameDebugInfo = imageProxy.toFrameDebugInfo()
        val roi = centeredBarcodeRoi(
            imageWidth = imageProxy.width,
            imageHeight = imageProxy.height,
            guideSpec = guideSpec,
        )

        return try {
            val roiCopyStartedAtNanos = System.nanoTime()
            val grayImage = imageProxy.toGrayImage(
                roi = roi,
                reusablePixels = reusablePixels,
            )
            val roiCopyEndedAtNanos = System.nanoTime()
            val decodeResult = decoder.decode(grayImage)
            val decodeEndedAtNanos = System.nanoTime()
            maybeLogAnalyzerTimings(
                roiCopyNanos = roiCopyEndedAtNanos - roiCopyStartedAtNanos,
                decodeNanos = decodeEndedAtNanos - roiCopyEndedAtNanos,
                totalNanos = decodeEndedAtNanos - startedAtNanos,
            )
            CameraScanOutcome.Decoded(
                frameDebugInfo = frameDebugInfo,
                roi = roi,
                decodeResult = decodeResult,
            )
        } catch (error: Exception) {
            CameraScanOutcome.ConversionFailed(
                frameDebugInfo = frameDebugInfo,
                roi = roi,
                reason = error.message ?: "Failed to extract guide ROI",
            )
        }
    }

    private fun maybeLogAnalyzerTimings(
        roiCopyNanos: Long,
        decodeNanos: Long,
        totalNanos: Long,
    ) {
        if (!Log.isLoggable(ANALYZER_TIMING_TAG, Log.DEBUG)) {
            return
        }
        Log.d(
            ANALYZER_TIMING_TAG,
            "Analyzer timings roiCopyMs=${roiCopyNanos.toMillisString()} " +
                "decodeMs=${decodeNanos.toMillisString()} " +
                "totalAnalyzeMs=${totalNanos.toMillisString()}",
        )
    }
}

private fun ImageProxy.toFrameDebugInfo(): FrameDebugInfo =
    FrameDebugInfo(
        width = width,
        height = height,
        rotationDegrees = imageInfo.rotationDegrees,
        timestampNanos = imageInfo.timestamp,
        requestedByScan = true,
    )

private fun ImageProxy.toGrayImage(
    roi: BarcodeRoi,
    reusablePixels: ByteArray,
): GrayImage {
    val lumaPlane = planes.firstOrNull()
        ?: error("Camera frame does not expose a luma plane")
    return extractGrayImageFromLumaPlaneBuffer(
        imageWidth = width,
        imageHeight = height,
        lumaBuffer = lumaPlane.buffer,
        rowStride = lumaPlane.rowStride,
        pixelStride = lumaPlane.pixelStride,
        roi = roi,
        reusablePixels = reusablePixels,
    )
}

private fun Long.toMillisString(): String = "%.3f".format(this / 1_000_000.0)

private const val ANALYZER_TIMING_TAG = "AnalyzerTiming"
