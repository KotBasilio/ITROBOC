package org.itroboc.app

import org.itroboc.vision.BarcodeRoi
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class AdminEditCameraSupportTest {
    @Test
    fun `centered barcode roi uses guide fractions`() {
        val roi = centeredBarcodeRoi(
            imageWidth = 100,
            imageHeight = 60,
            guideRectSpec = GuideRectSpec(widthFraction = 0.8f, heightFraction = 0.15f),
        )

        assertEquals(10, roi.x)
        assertEquals(25, roi.y)
        assertEquals(80, roi.width)
        assertEquals(9, roi.height)
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
}
