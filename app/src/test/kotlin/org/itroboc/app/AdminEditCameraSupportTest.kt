package org.itroboc.app

import org.itroboc.vision.BarcodeRoi
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertContains
import kotlin.test.assertEquals

class AdminEditCameraSupportTest {
    @Test
    fun `centered barcode roi uses guide fractions`() {
        val roi = centeredBarcodeRoi(
            imageWidth = 100,
            imageHeight = 60,
            guideSpec = AdminScanGuideSpec(widthFraction = 0.25f, heightFraction = 0.10f),
        )

        assertEquals(37, roi.x)
        assertEquals(27, roi.y)
        assertEquals(25, roi.width)
        assertEquals(6, roi.height)
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
    fun `debug record json line includes roi and found signature fields`() {
        val record = AdminEditScanDebugRecord(
            timestampMillis = 123L,
            selectedCard = "♦A",
            frameWidth = 640,
            frameHeight = 480,
            frameRotation = 90,
            frameTimestampNanos = 456L,
            roi = BarcodeRoi(x = 10, y = 20, width = 30, height = 40),
            decodeResultType = "Found",
            rawSignature = "bars:1-1-1",
            confidence = 0.875,
            normalizedPattern = "1-1-1",
            blackRuns = listOf("1..3", "5..7"),
        )

        val jsonLine = record.toJsonLine()

        assertContains(jsonLine, "\"timestampMillis\":123")
        assertContains(jsonLine, "\"selectedCard\":\"♦A\"")
        assertContains(jsonLine, "\"decodeResultType\":\"Found\"")
        assertContains(jsonLine, "\"roi\":{\"x\":10,\"y\":20,\"width\":30,\"height\":40}")
        assertContains(jsonLine, "\"rawSignature\":\"bars:1-1-1\"")
        assertContains(jsonLine, "\"confidence\":0.8750")
        assertContains(jsonLine, "\"blackRuns\":[\"1..3\",\"5..7\"]")
    }
}
