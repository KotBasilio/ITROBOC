package org.itroboc.app

import org.itroboc.vision.BarcodeRoi
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CameraFrameSupportTest {
    @Test
    fun `centered barcode roi uses guide fractions`() {
        val roi = centeredBarcodeRoi(
            imageWidth = 100,
            imageHeight = 60,
            guideSpec = barcodeScanGuideSpec,
        )

        assertEquals(40, roi.x)
        assertEquals(29, roi.y)
        assertEquals(20, roi.width)
        assertEquals(2, roi.height)
    }

    @Test
    fun `barcode guide height is capped in roi and overlay coordinates`() {
        val roi = centeredBarcodeRoi(
            imageWidth = 1920,
            imageHeight = 1080,
            guideSpec = barcodeScanGuideSpec,
        )
        val overlay = centeredGuideRectBounds(
            containerWidth = 1080f,
            containerHeight = 1920f,
            guideSpec = barcodeScanGuideSpec,
        )

        assertEquals(384, roi.width)
        assertEquals(10, roi.height)
        assertEquals(10f, overlay.height)
    }

    @Test
    fun `extract gray image respects row stride while cropping centered roi`() {
        val roi = BarcodeRoi(x = 1, y = 1, width = 3, height = 2)
        val lumaBytes = byteArrayOf(
            1, 2, 3, 4, 5, 99,
            6, 7, 8, 9, 10, 99,
            11, 12, 13, 14, 15, 99,
        )

        val image = extractGrayImageFromLumaBytes(
            imageWidth = 5,
            imageHeight = 3,
            lumaBytes = lumaBytes,
            rowStride = 6,
            pixelStride = 1,
            roi = roi,
        )

        assertEquals(3, image.width)
        assertEquals(2, image.height)
        assertContentEquals(byteArrayOf(7, 8, 9, 12, 13, 14), image.pixels)
    }

    @Test
    fun `roi only buffer copy matches full copy then crop with non trivial stride`() {
        val roi = BarcodeRoi(x = 2, y = 1, width = 4, height = 3)
        val lumaBytes = byteArrayOf(
            1, 2, 3, 4, 5, 6, 7, 8, 90, 91, 92, 93,
            9, 10, 11, 12, 13, 14, 15, 16, 94, 95, 96, 97,
            17, 18, 19, 20, 21, 22, 23, 24, 98, 99, 100, 101,
            25, 26, 27, 28, 29, 30, 31, 32, 102, 103, 104, 105,
            33, 34, 35, 36, 37, 38, 39, 40, 106, 107, 108, 109,
            41, 42, 43, 44, 45, 46, 47, 48, 110, 111, 112, 113,
        )

        val fromBytes = extractGrayImageFromLumaBytes(
            imageWidth = 8,
            imageHeight = 6,
            lumaBytes = lumaBytes,
            rowStride = 12,
            pixelStride = 1,
            roi = roi,
        )
        val fromBuffer = extractGrayImageFromLumaPlaneBuffer(
            imageWidth = 8,
            imageHeight = 6,
            lumaBuffer = ByteBuffer.wrap(lumaBytes),
            rowStride = 12,
            pixelStride = 1,
            roi = roi,
        )

        assertEquals(4, fromBuffer.width)
        assertEquals(3, fromBuffer.height)
        assertContentEquals(byteArrayOf(11, 12, 13, 14, 19, 20, 21, 22, 27, 28, 29, 30), fromBuffer.pixels)
        assertContentEquals(fromBytes.pixels, fromBuffer.pixels)
    }

    @Test
    fun `roi only buffer copy handles edge aligned and single pixel crops`() {
        val lumaBytes = byteArrayOf(
            1, 2, 3, 4, 50,
            5, 6, 7, 8, 51,
            9, 10, 11, 12, 52,
            13, 14, 15, 16, 53,
        )

        val topLeft = extractGrayImageFromLumaPlaneBuffer(
            imageWidth = 4,
            imageHeight = 4,
            lumaBuffer = ByteBuffer.wrap(lumaBytes),
            rowStride = 5,
            pixelStride = 1,
            roi = BarcodeRoi(x = 0, y = 0, width = 2, height = 2),
        )
        val bottomRight = extractGrayImageFromLumaPlaneBuffer(
            imageWidth = 4,
            imageHeight = 4,
            lumaBuffer = ByteBuffer.wrap(lumaBytes),
            rowStride = 5,
            pixelStride = 1,
            roi = BarcodeRoi(x = 2, y = 2, width = 2, height = 2),
        )
        val singlePixel = extractGrayImageFromLumaPlaneBuffer(
            imageWidth = 4,
            imageHeight = 4,
            lumaBuffer = ByteBuffer.wrap(lumaBytes),
            rowStride = 5,
            pixelStride = 1,
            roi = BarcodeRoi(x = 3, y = 1, width = 1, height = 1),
        )

        assertContentEquals(byteArrayOf(1, 2, 5, 6), topLeft.pixels)
        assertContentEquals(byteArrayOf(11, 12, 15, 16), bottomRight.pixels)
        assertContentEquals(byteArrayOf(8), singlePixel.pixels)
    }

    @Test
    fun `roi only buffer copy can reuse exact size destination pixels`() {
        val roi = BarcodeRoi(x = 1, y = 1, width = 3, height = 2)
        val lumaBytes = byteArrayOf(
            1, 2, 3, 4, 5, 99,
            6, 7, 8, 9, 10, 99,
            11, 12, 13, 14, 15, 99,
        )
        val reusablePixels = ByteArray(roi.width * roi.height) { 42 }

        val image = extractGrayImageFromLumaPlaneBuffer(
            imageWidth = 5,
            imageHeight = 3,
            lumaBuffer = ByteBuffer.wrap(lumaBytes),
            rowStride = 6,
            pixelStride = 1,
            roi = roi,
            reusablePixels = reusablePixels,
        )

        assertTrue(image.pixels === reusablePixels)
        assertContentEquals(byteArrayOf(7, 8, 9, 12, 13, 14), reusablePixels)
    }

    @Test
    fun `roi only buffer copy still supports non unit pixel stride`() {
        val roi = BarcodeRoi(x = 1, y = 0, width = 3, height = 2)
        val lumaBytes = byteArrayOf(
            1, 90, 2, 91, 3, 92, 4, 93,
            5, 94, 6, 95, 7, 96, 8, 97,
        )

        val image = extractGrayImageFromLumaPlaneBuffer(
            imageWidth = 4,
            imageHeight = 2,
            lumaBuffer = ByteBuffer.wrap(lumaBytes),
            rowStride = 8,
            pixelStride = 2,
            roi = roi,
        )

        assertContentEquals(byteArrayOf(2, 3, 4, 6, 7, 8), image.pixels)
    }

    @Test
    fun `roi only buffer copy rejects invalid roi clearly`() {
        val buffer = ByteBuffer.wrap(ByteArray(24))

        assertFailsWith<IllegalArgumentException> {
            extractGrayImageFromLumaPlaneBuffer(
                imageWidth = 4,
                imageHeight = 4,
                lumaBuffer = buffer,
                rowStride = 6,
                pixelStride = 1,
                roi = BarcodeRoi(x = 3, y = 3, width = 2, height = 2),
            )
        }
    }
}
