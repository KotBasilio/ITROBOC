package org.itroboc.app

import org.itroboc.vision.BarcodeRoi
import org.itroboc.vision.GrayImage
import java.nio.ByteBuffer
import kotlin.math.min
import kotlin.math.roundToInt

internal val barcodeScanGuideSpec = CameraScanGuideSpec(
    widthFraction = 0.20f,
    heightFraction = 0.03f,
    maxHeightPixels = 10,
)

internal data class CameraScanGuideSpec(
    val widthFraction: Float,
    val heightFraction: Float,
    val maxHeightPixels: Int? = null,
) {
    init {
        require(widthFraction in 0f..1f) { "widthFraction must be within [0, 1]" }
        require(heightFraction in 0f..1f) { "heightFraction must be within [0, 1]" }
        require(maxHeightPixels == null || maxHeightPixels > 0) {
            "maxHeightPixels must be positive when set"
        }
    }
}

internal data class GuideRectBounds(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

internal fun centeredBarcodeRoi(
    imageWidth: Int,
    imageHeight: Int,
    guideSpec: CameraScanGuideSpec,
): BarcodeRoi {
    require(imageWidth > 0) { "imageWidth must be positive" }
    require(imageHeight > 0) { "imageHeight must be positive" }

    val roiWidth = (imageWidth * guideSpec.widthFraction)
        .roundToInt()
        .coerceIn(1, imageWidth)
    val proportionalHeight = (imageHeight * guideSpec.heightFraction).roundToInt()
    val roiHeight = guideSpec.maxHeightPixels
        ?.let { min(proportionalHeight, it) }
        ?: proportionalHeight
    val clampedRoiHeight = roiHeight
        .coerceIn(1, imageHeight)
    val x = ((imageWidth - roiWidth) / 2).coerceAtLeast(0)
    val y = ((imageHeight - clampedRoiHeight) / 2).coerceAtLeast(0)

    return BarcodeRoi(
        x = x,
        y = y,
        width = roiWidth,
        height = clampedRoiHeight,
    )
}

internal fun centeredGuideRectBounds(
    containerWidth: Float,
    containerHeight: Float,
    guideSpec: CameraScanGuideSpec,
): GuideRectBounds {
    val width = containerWidth * guideSpec.widthFraction
    val proportionalHeight = containerHeight * guideSpec.heightFraction
    val height = guideSpec.maxHeightPixels
        ?.let { min(proportionalHeight, it.toFloat()) }
        ?: proportionalHeight
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
    val pixels = ByteArray(roi.width * roi.height)
    validateLumaRoiSource(
        imageWidth = imageWidth,
        imageHeight = imageHeight,
        rowStride = rowStride,
        pixelStride = pixelStride,
        roi = roi,
        sourceLimit = lumaBytes.size,
    )

    if (pixelStride == 1) {
        var destinationIndex = 0
        for (y in roi.y until roi.y + roi.height) {
            val sourceIndex = (y * rowStride) + roi.x
            System.arraycopy(lumaBytes, sourceIndex, pixels, destinationIndex, roi.width)
            destinationIndex += roi.width
        }
    } else {
        copyLumaRoiIntoBuffer(
            rowStride = rowStride,
            pixelStride = pixelStride,
            roi = roi,
        ) { sourceIndex, destinationIndex ->
            pixels[destinationIndex] = lumaBytes[sourceIndex]
        }
    }

    return GrayImage(
        width = roi.width,
        height = roi.height,
        pixels = pixels,
    )
}

internal fun extractGrayImageFromLumaPlaneBuffer(
    imageWidth: Int,
    imageHeight: Int,
    lumaBuffer: ByteBuffer,
    rowStride: Int,
    pixelStride: Int,
    roi: BarcodeRoi,
): GrayImage {
    validateLumaRoiSource(
        imageWidth = imageWidth,
        imageHeight = imageHeight,
        rowStride = rowStride,
        pixelStride = pixelStride,
        roi = roi,
        sourceLimit = lumaBuffer.limit(),
    )
    val source = lumaBuffer.duplicate()
    val pixels = ByteArray(roi.width * roi.height)

    if (pixelStride == 1) {
        var destinationIndex = 0
        for (y in roi.y until roi.y + roi.height) {
            source.position((y * rowStride) + roi.x)
            source.get(pixels, destinationIndex, roi.width)
            destinationIndex += roi.width
        }
    } else {
        copyLumaRoiIntoBuffer(
            rowStride = rowStride,
            pixelStride = pixelStride,
            roi = roi,
        ) { sourceIndex, destinationIndex ->
            pixels[destinationIndex] = source.get(sourceIndex)
        }
    }

    return GrayImage(
        width = roi.width,
        height = roi.height,
        pixels = pixels,
    )
}

internal fun extractGrayImageFromLumaPlaneBuffer(
    imageWidth: Int,
    imageHeight: Int,
    lumaBuffer: ByteBuffer,
    rowStride: Int,
    pixelStride: Int,
    roi: BarcodeRoi,
    reusablePixels: ByteArray,
): GrayImage {
    require(reusablePixels.size == roi.width * roi.height) {
        "reusablePixels must match ROI size exactly"
    }

    val source = lumaBuffer.duplicate()
    validateLumaRoiSource(
        imageWidth = imageWidth,
        imageHeight = imageHeight,
        rowStride = rowStride,
        pixelStride = pixelStride,
        roi = roi,
        sourceLimit = lumaBuffer.limit(),
    )

    if (pixelStride == 1) {
        var destinationIndex = 0
        for (y in roi.y until roi.y + roi.height) {
            source.position((y * rowStride) + roi.x)
            source.get(reusablePixels, destinationIndex, roi.width)
            destinationIndex += roi.width
        }
    } else {
        copyLumaRoiIntoBuffer(
            rowStride = rowStride,
            pixelStride = pixelStride,
            roi = roi,
        ) { sourceIndex, destinationIndex ->
            reusablePixels[destinationIndex] = source.get(sourceIndex)
        }
    }

    return GrayImage(
        width = roi.width,
        height = roi.height,
        pixels = reusablePixels,
    )
}

private fun validateLumaRoiSource(
    imageWidth: Int,
    imageHeight: Int,
    rowStride: Int,
    pixelStride: Int,
    roi: BarcodeRoi,
    sourceLimit: Int,
) {
    require(pixelStride > 0) { "pixelStride must be positive" }
    require(rowStride > 0) { "rowStride must be positive" }
    require(roi.x >= 0 && roi.y >= 0) { "ROI origin must be non-negative" }
    require(roi.x + roi.width <= imageWidth) { "ROI exceeds image width" }
    require(roi.y + roi.height <= imageHeight) { "ROI exceeds image height" }

    val expectedLastIndex = ((roi.y + roi.height - 1) * rowStride) +
        ((roi.x + roi.width - 1) * pixelStride)
    require(expectedLastIndex < sourceLimit) {
        "Luma plane source does not cover requested ROI"
    }
}

private inline fun copyLumaRoiIntoBuffer(
    rowStride: Int,
    pixelStride: Int,
    roi: BarcodeRoi,
    copyPixel: (sourceIndex: Int, destinationIndex: Int) -> Unit,
) {
    var destinationIndex = 0
    for (y in roi.y until roi.y + roi.height) {
        val rowOffset = y * rowStride
        for (x in roi.x until roi.x + roi.width) {
            val sourceIndex = rowOffset + (x * pixelStride)
            copyPixel(sourceIndex, destinationIndex++)
        }
    }
}
