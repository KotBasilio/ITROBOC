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
            guideSpec = AdminScanGuideSpec(widthFraction = 0.20f, heightFraction = 0.08f),
        )

        assertEquals(40, roi.x)
        assertEquals(27, roi.y)
        assertEquals(20, roi.width)
        assertEquals(5, roi.height)
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
            roiAngleDeg = 0.0,
            rawSignature = "bars:1-1-1",
            confidence = 0.875,
            thresholdMode = "adaptive-ink-midrange",
            thresholdValue = 127,
            normalizedPattern = "1-1-1",
            blackRuns = listOf("1..3", "5..7"),
            signatureModel = "grid13-v1",
            reverseSignature = "brm1255",
            grid13FwdBitsPreSentinel = "1110101001011",
            grid13FwdBits = "1010101001001",
            grid13RevBits = "1001001010101",
            grid13FwdHex = "1549",
            grid13RevHex = "1255",
            rl2 = "B1-W1-B1",
            blackRunsPx = listOf(3, 3),
            whiteGapsPx = listOf(2),
            blackRunCentersPx = listOf(2.0, 6.0),
            activeStartX = 1,
            activeEndX = 7,
            activeSpanPx = 7,
            sentinelValid = false,
            sentinelIssues = listOf("bit11 must be white"),
            sentinelRepairApplied = true,
            sentinelRepairReason = "Normalized Grid13 control bits: bit11 must be white",
            ambiguous = false,
            warnings = listOf("debug warning"),
            deckProfileMatchCount = 1,
        )

        val jsonLine = record.toJsonLine()

        assertContains(jsonLine, "\"timestampMillis\":123")
        assertContains(jsonLine, "\"selectedCard\":\"♦A\"")
        assertContains(jsonLine, "\"decodeResultType\":\"Found\"")
        assertContains(jsonLine, "\"roiAngleDeg\":0.0000")
        assertContains(jsonLine, "\"cropWidthPx\":30")
        assertContains(jsonLine, "\"cropHeightPx\":40")
        assertContains(jsonLine, "\"roi\":{\"x\":10,\"y\":20,\"width\":30,\"height\":40}")
        assertContains(jsonLine, "\"rawSignature\":\"bars:1-1-1\"")
        assertContains(jsonLine, "\"confidence\":0.8750")
        assertContains(jsonLine, "\"thresholdMode\":\"adaptive-ink-midrange\"")
        assertContains(jsonLine, "\"thresholdValue\":127")
        assertContains(jsonLine, "\"blackRuns\":[\"1..3\",\"5..7\"]")
        assertContains(jsonLine, "\"signatureModel\":\"grid13-v1\"")
        assertContains(jsonLine, "\"reverseSignature\":\"brm1255\"")
        assertContains(jsonLine, "\"grid13FwdBitsPreSentinel\":\"1110101001011\"")
        assertContains(jsonLine, "\"grid13FwdBits\":\"1010101001001\"")
        assertContains(jsonLine, "\"grid13RevBits\":\"1001001010101\"")
        assertContains(jsonLine, "\"grid13FwdHex\":\"1549\"")
        assertContains(jsonLine, "\"grid13RevHex\":\"1255\"")
        assertContains(jsonLine, "\"rl2\":\"B1-W1-B1\"")
        assertContains(jsonLine, "\"blackRunsPx\":[3,3]")
        assertContains(jsonLine, "\"whiteGapsPx\":[2]")
        assertContains(jsonLine, "\"blackRunCentersPx\":[2.0000,6.0000]")
        assertContains(jsonLine, "\"activeStartX\":1")
        assertContains(jsonLine, "\"activeEndX\":7")
        assertContains(jsonLine, "\"activeSpanPx\":7")
        assertContains(jsonLine, "\"sentinelValid\":false")
        assertContains(jsonLine, "\"sentinelIssues\":[\"bit11 must be white\"]")
        assertContains(jsonLine, "\"sentinelRepairApplied\":true")
        assertContains(jsonLine, "\"sentinelRepairReason\":\"Normalized Grid13 control bits: bit11 must be white\"")
        assertContains(jsonLine, "\"scanlineAgreement\":null")
        assertContains(jsonLine, "\"ambiguous\":false")
        assertContains(jsonLine, "\"warnings\":[\"debug warning\"]")
        assertContains(jsonLine, "\"deckProfileMatchCount\":1")
    }
}
