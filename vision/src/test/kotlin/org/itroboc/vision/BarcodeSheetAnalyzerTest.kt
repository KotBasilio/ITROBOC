package org.itroboc.vision

import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BarcodeSheetAnalyzerTest {
    @Test
    fun `master sheet resource loads`() {
        val image = BarcodeSheetAnalyzer.loadResourceImage("Master.png")

        assertEquals(511, image.width)
        assertEquals(833, image.height)
    }

    @Test
    fun `each suit sheet yields thirteen barcode crops`() {
        BarcodeSheetAnalyzer.suitSheets.forEach { sheet ->
            val crops = BarcodeSheetAnalyzer.enumerateSuitSheetCrops(sheet)

            assertEquals(13, crops.size, sheet.resourceName)
            assertEquals("${sheet.suit}A", crops.first().cardId)
            assertEquals("${sheet.suit}2", crops.last().cardId)
        }
    }

    @Test
    fun `bottom suit symbol is not included as a barcode crop`() {
        BarcodeSheetAnalyzer.suitSheets.forEach { sheet ->
            val analysis = BarcodeSheetAnalyzer.analyzeSuitSheet(sheet)
            val lastCropBottom = analysis.crops.maxOf { it.bounds.bottomExclusive }
            val suitSymbolTop = assertNotNull(analysis.bottomSuitSymbolBand, sheet.resourceName).first

            assertTrue(
                lastCropBottom < suitSymbolTop,
                "${sheet.resourceName} last crop bottom $lastCropBottom should be above suit symbol top $suitSymbolTop",
            )
        }
    }

    @Test
    fun `all fifty two card ids are represented exactly once`() {
        val crops = BarcodeSheetAnalyzer.enumerateAllSuitSheetCrops()
        val expectedCards = listOf('S', 'H', 'D', 'C').flatMap { suit ->
            listOf("A", "K", "Q", "J", "T", "9", "8", "7", "6", "5", "4", "3", "2").map { rank ->
                "$suit$rank"
            }
        }.toSet()

        assertEquals(52, crops.size)
        assertEquals(expectedCards, crops.map { it.cardId }.toSet())
    }

    @Test
    fun `strict raw-bit gate still leaves substantial sheet measurement coverage`() {
        val batch = BarcodeSheetAnalyzer.tryMeasureAllSuitSheetCrops()

        assertEquals(52, batch.measurements.size + batch.failures.size)
        assertTrue(batch.measurements.size >= 40, "measured=${batch.measurements.size} failures=${batch.failures}")
        assertTrue(batch.failures.isNotEmpty(), "expected at least one strict-gate dropout")
        batch.measurements.forEach { record ->
            assertTrue(record.measurement.rawSignature.matches(Regex("^bfm[0-9A-F]{4}$")), record.cardId)
            assertTrue(record.measurement.reverseSignature.matches(Regex("^brm[0-9A-F]{4}$")), record.cardId)
            assertEquals(13, record.measurement.grid13FwdBits.length, record.cardId)
            assertEquals(reverseBits(record.measurement.grid13FwdBits), record.measurement.grid13RevBits, record.cardId)
            assertTrue(record.measurement.rl2.startsWith("B"), record.cardId)
        }
    }

    @Test
    fun `sheet measurement report includes readable rows for measured crops`() {
        val report = BarcodeSheetAnalyzer.tryMeasureAllSuitSheetCrops().measurements
            .take(3)
            .joinToString(separator = "\n") { it.toReportRow() }

        assertTrue(report.lines().size == 3, report)
        assertTrue(report.contains(" | "), report)
        assertTrue(report.contains("bfm"), report)
        assertTrue(report.contains("101"), report)
    }

    @Test
    fun `sheet measurement failures are surfaced as explicit card ids`() {
        val batch = BarcodeSheetAnalyzer.tryMeasureAllSuitSheetCrops()

        assertTrue(batch.failures.isNotEmpty(), "expected strict-gate dropouts")
        batch.failures.forEach { failure ->
            assertTrue(failure.matches(Regex("^[SHDC][AKQJT98765432]$")), failure)
        }
    }

    @Test
    fun `selected stable sheet measurements still match research table grid13 values`() {
        val byCard = BarcodeSheetAnalyzer.tryMeasureAllSuitSheetCrops().measurements.associateBy { it.cardId }

        assertEquals("1010101001001", byCard.getValue("SA").measurement.grid13FwdBits)
        assertEquals("1010010101001", byCard.getValue("SK").measurement.grid13FwdBits)
        assertEquals("1001001001101", byCard.getValue("D2").measurement.grid13FwdBits)
    }

    @Test
    fun `strict sheet measurement helper still reports first failing crop clearly`() {
        val error = kotlin.test.assertFailsWith<IllegalArgumentException> {
            BarcodeSheetAnalyzer.measureAllSuitSheetCrops()
        }

        assertTrue(error.message?.contains("Could not measure") == true, error.message ?: "")
        assertTrue(error.message?.contains(".png") == true, error.message ?: "")
    }
}

internal object BarcodeSheetAnalyzer {
    private val ranks = listOf("A", "K", "Q", "J", "T", "9", "8", "7", "6", "5", "4", "3", "2")

    val suitSheets = listOf(
        SuitSheet(suit = 'S', resourceName = "spades.png"),
        SuitSheet(suit = 'H', resourceName = "hearts.png"),
        SuitSheet(suit = 'D', resourceName = "diamonds.png"),
        SuitSheet(suit = 'C', resourceName = "clubs.png"),
    )

    fun enumerateAllSuitSheetCrops(): List<LabeledBarcodeCrop> =
        suitSheets.flatMap(::enumerateSuitSheetCrops)

    fun measureAllSuitSheetCrops(): List<LabeledBarcodeMeasurement> =
        suitSheets.flatMap { sheet ->
            val image = loadResourceImage(sheet.resourceName)
            analyzeSuitSheet(sheet).crops.map { crop ->
                val measurement = requireNotNull(measureGrid13SlowBarcode(crop.toInkImage(image))) {
                    "Could not measure ${crop.cardId} from ${crop.sheetResourceName}"
                }
                LabeledBarcodeMeasurement(
                    cardId = crop.cardId,
                    crop = crop,
                    measurement = measurement,
                )
            }
        }

    fun tryMeasureAllSuitSheetCrops(): SheetMeasurementBatch {
        val measurements = mutableListOf<LabeledBarcodeMeasurement>()
        val failures = mutableListOf<String>()

        suitSheets.forEach { sheet ->
            val image = loadResourceImage(sheet.resourceName)
            analyzeSuitSheet(sheet).crops.forEach { crop ->
                val measurement = measureGrid13SlowBarcode(crop.toInkImage(image))
                if (measurement == null) {
                    failures += crop.cardId
                } else {
                    measurements += LabeledBarcodeMeasurement(
                        cardId = crop.cardId,
                        crop = crop,
                        measurement = measurement,
                    )
                }
            }
        }

        return SheetMeasurementBatch(
            measurements = measurements,
            failures = failures,
        )
    }

    fun enumerateSuitSheetCrops(sheet: SuitSheet): List<LabeledBarcodeCrop> =
        analyzeSuitSheet(sheet).crops

    fun analyzeSuitSheet(sheet: SuitSheet): SuitSheetAnalysis {
        val image = loadResourceImage(sheet.resourceName)
        val barcodeRegionWidth = (image.width * 0.68).roundToInt().coerceIn(1, image.width)
        val contentBands = horizontalContentBands(
            image = image,
            scanWidth = barcodeRegionWidth,
        )

        require(contentBands.size >= ranks.size) {
            "${sheet.resourceName} yielded ${contentBands.size} content bands, expected at least ${ranks.size}"
        }

        val crops = ranks.zip(contentBands.take(ranks.size)).map { (rank, band) ->
            LabeledBarcodeCrop(
                cardId = "${sheet.suit}$rank",
                suit = sheet.suit,
                rank = rank,
                sheetResourceName = sheet.resourceName,
                bounds = CropBounds(
                    x = 0,
                    y = band.first,
                    width = barcodeRegionWidth,
                    height = band.last - band.first + 1,
                ),
            )
        }

        return SuitSheetAnalysis(
            sheet = sheet,
            imageWidth = image.width,
            imageHeight = image.height,
            crops = crops,
            bottomSuitSymbolBand = contentBands.drop(ranks.size).firstOrNull(),
        )
    }

    fun loadResourceImage(resourceName: String): BufferedImage {
        val resource = requireNotNull(
            BarcodeSheetAnalyzer::class.java.getResource("/barcode-sheets/$resourceName"),
        ) {
            "Missing barcode sheet resource: $resourceName"
        }
        return requireNotNull(ImageIO.read(resource)) {
            "Could not decode barcode sheet resource: $resourceName"
        }
    }

    private fun horizontalContentBands(
        image: BufferedImage,
        scanWidth: Int,
    ): List<IntRange> {
        val minimumContentPixels = (scanWidth * 0.05).roundToInt().coerceAtLeast(3)
        val rowHasContent = BooleanArray(image.height) { y ->
            var contentPixels = 0
            for (x in 0 until scanWidth) {
                if (image.isContentPixel(x, y)) {
                    contentPixels++
                }
            }
            contentPixels >= minimumContentPixels
        }

        val bands = mutableListOf<IntRange>()
        var bandStart: Int? = null
        for (y in rowHasContent.indices) {
            if (rowHasContent[y] && bandStart == null) {
                bandStart = y
            } else if (!rowHasContent[y] && bandStart != null) {
                bands += bandStart until y
                bandStart = null
            }
        }
        bandStart?.let { bands += it until image.height }

        return bands
            .filter { it.count() >= 8 }
            .map { band ->
                (band.first - 1).coerceAtLeast(0)..(band.last + 1).coerceAtMost(image.height - 1)
            }
    }

    private fun BufferedImage.isContentPixel(x: Int, y: Int): Boolean {
        val argb = getRGB(x, y)
        val alpha = (argb ushr 24) and 0xFF
        if (alpha == 0) {
            return false
        }
        val red = (argb ushr 16) and 0xFF
        val green = (argb ushr 8) and 0xFF
        val blue = argb and 0xFF
        return minOf(red, green, blue) < 245
    }

}

internal data class SuitSheet(
    val suit: Char,
    val resourceName: String,
)

internal data class SuitSheetAnalysis(
    val sheet: SuitSheet,
    val imageWidth: Int,
    val imageHeight: Int,
    val crops: List<LabeledBarcodeCrop>,
    val bottomSuitSymbolBand: IntRange?,
)

internal data class LabeledBarcodeCrop(
    val cardId: String,
    val suit: Char,
    val rank: String,
    val sheetResourceName: String,
    val bounds: CropBounds,
)

internal data class LabeledBarcodeMeasurement(
    val cardId: String,
    val crop: LabeledBarcodeCrop,
    val measurement: Grid13SlowBarcodeMeasurement,
) {
    fun toReportRow(): String =
        listOf(
            cardId,
            measurement.blackRunsPx.joinToString(separator = ","),
            measurement.whiteGapsPx.joinToString(separator = ","),
            measurement.rl2,
            measurement.grid13FwdBits,
            measurement.rawSignature,
        ).joinToString(separator = " | ")
}

internal data class SheetMeasurementBatch(
    val measurements: List<LabeledBarcodeMeasurement>,
    val failures: List<String>,
)

internal data class CropBounds(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
) {
    val bottomExclusive: Int = y + height
}

internal enum class SheetInkChannel {
    MIN_RGB,
    LUMINANCE,
}

internal fun LabeledBarcodeCrop.toInkImage(
    image: BufferedImage,
    channel: SheetInkChannel = SheetInkChannel.MIN_RGB,
): InkImage {
    val values = IntArray(bounds.width * bounds.height)
    var index = 0
    for (y in bounds.y until bounds.y + bounds.height) {
        for (x in bounds.x until bounds.x + bounds.width) {
            val argb = image.getRGB(x, y)
            val red = (argb ushr 16) and 0xFF
            val green = (argb ushr 8) and 0xFF
            val blue = argb and 0xFF
            values[index++] = when (channel) {
                SheetInkChannel.MIN_RGB -> inkFromRgb(red = red, green = green, blue = blue)
                SheetInkChannel.LUMINANCE -> {
                    val luminance = ((0.299 * red) + (0.587 * green) + (0.114 * blue)).roundToInt()
                    255 - luminance.coerceIn(0, 255)
                }
            }
        }
    }
    return InkImage(
        width = bounds.width,
        height = bounds.height,
        inkValues = values,
    )
}
