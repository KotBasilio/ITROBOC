package org.itroboc.vision

import java.awt.Color
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.stream.MemoryCacheImageOutputStream
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Grid13DegradationTest {
    @Test
    fun `degradation reports cover expected classes and tolerate dropped measurements`() {
        val reports = buildDegradationReports()

        assertEquals(
            setOf(
                "horizontal crop shift +1",
                "vertical crop shift +1",
                "mild blur",
                "brightened exposure",
                "darkened exposure",
                "jpeg compression",
                "mild skew",
            ),
            reports.map { it.name }.toSet(),
        )
        reports.forEach { report ->
            assertTrue(report.total > 0, report.name)
            assertTrue(report.measured in 0..report.total, report.summary())
            assertTrue(report.missing in 0..report.total, report.summary())
            assertEquals(report.total, report.measured + report.missing, report.summary())
            assertTrue(report.stable in 0..report.measured, report.summary())
            assertTrue(report.summary().contains("missing="), report.summary())
            assertTrue(report.measured > 0, report.name)
        }
    }

    @Test
    fun `small crop shifts still measure most baseline cards`() {
        val reports = buildDegradationReports()
            .filter { it.name.startsWith("horizontal") || it.name.startsWith("vertical") }

        reports.forEach { report ->
            assertTrue(
                report.measuredRate >= 0.80,
                "${report.name} measured too few cards after raw-bit gating: ${report.summary()}",
            )
        }
    }

    @Test
    fun `min rgb ink channel is at least as stable as luminance on red suits`() {
        val minRgb = measureRedSuitChannelStability(SheetInkChannel.MIN_RGB)
        val luminance = measureRedSuitChannelStability(SheetInkChannel.LUMINANCE)

        assertTrue(
            minRgb.stabilityRate >= luminance.stabilityRate,
            "min-RGB ${minRgb.summary()} should be at least as stable as luminance ${luminance.summary()}",
        )
    }

    @Test
    fun `degradation summaries report missing cards when a transform drops measurements`() {
        val reports = buildDegradationReports()

        assertTrue(reports.any { it.missing > 0 }, reports.joinToString(separator = "\n") { it.summary() })
        reports.filter { it.missing > 0 }.forEach { report ->
            assertTrue(report.examples.any { it.startsWith("missing:") }, report.summary())
        }
    }

    private fun buildDegradationReports(): List<DegradationReport> {
        val baseline = measureBaseline()
        return listOf(
            degradationReport(
                name = "horizontal crop shift +1",
                baseline = baseline,
                cropTransform = { crop, image -> crop.shifted(dx = 1, dy = 0, image = image) },
            ),
            degradationReport(
                name = "vertical crop shift +1",
                baseline = baseline,
                cropTransform = { crop, image -> crop.shifted(dx = 0, dy = 1, image = image) },
            ),
            degradationReport(
                name = "mild blur",
                baseline = baseline,
                imageTransform = BufferedImage::boxBlurred,
            ),
            degradationReport(
                name = "brightened exposure",
                baseline = baseline,
                imageTransform = { it.withExposure(delta = 24) },
            ),
            degradationReport(
                name = "darkened exposure",
                baseline = baseline,
                imageTransform = { it.withExposure(delta = -24) },
            ),
            degradationReport(
                name = "jpeg compression",
                baseline = baseline,
                imageTransform = { it.jpegRoundTrip(qualityHint = 0.65f) },
            ),
            degradationReport(
                name = "mild skew",
                baseline = baseline,
                imageTransform = { it.shearedX(shear = 0.015) },
            ),
        )
    }

    private fun measureBaseline(): Map<String, Grid13BarcodeMeasurement> =
        measureAllSheets().associate { it.cardId to it.measurement }

    private fun degradationReport(
        name: String,
        baseline: Map<String, Grid13BarcodeMeasurement>,
        imageTransform: (BufferedImage) -> BufferedImage = { it },
        cropTransform: (LabeledBarcodeCrop, BufferedImage) -> LabeledBarcodeCrop = { crop, _ -> crop },
    ): DegradationReport {
        val changed = mutableListOf<String>()
        val degradedByCard = measureAllSheets(
            imageTransform = imageTransform,
            cropTransform = cropTransform,
        ).associateBy { it.cardId }
        var measured = 0
        var stable = 0
        var confidenceDeltaTotal = 0.0

        baseline.forEach { (cardId, baselineMeasurement) ->
            val degraded = degradedByCard[cardId]
            if (degraded == null) {
                if (changed.size < 8) {
                    changed += "missing:$cardId"
                }
                return@forEach
            }
            measured++
            if (baselineMeasurement.grid13FwdBits == degraded.measurement.grid13FwdBits) {
                stable++
            } else if (changed.size < 8) {
                changed += "$cardId:${baselineMeasurement.grid13FwdBits}->${degraded.measurement.grid13FwdBits}"
            }
            confidenceDeltaTotal += degraded.measurement.confidence - baselineMeasurement.confidence
        }

        return DegradationReport(
            name = name,
            total = baseline.size,
            measured = measured,
            missing = baseline.size - measured,
            stable = stable,
            averageConfidenceDelta = if (measured == 0) 0.0 else confidenceDeltaTotal / measured,
            examples = changed,
        )
    }

    private fun measureRedSuitChannelStability(channel: SheetInkChannel): DegradationReport {
        val baseline = measureAllSheets()
            .filter { it.crop.suit == 'H' || it.crop.suit == 'D' }
            .associate { it.cardId to it.measurement }
        var stable = 0
        var measured = 0
        val changed = mutableListOf<String>()

        measureAllSheets(channel = channel)
            .filter { it.crop.suit == 'H' || it.crop.suit == 'D' }
            .forEach { candidate ->
                measured++
                val baselineMeasurement = baseline.getValue(candidate.cardId)
                if (baselineMeasurement.grid13FwdBits == candidate.measurement.grid13FwdBits) {
                    stable++
                } else if (changed.size < 8) {
                    changed += "${candidate.cardId}:${baselineMeasurement.grid13FwdBits}->${candidate.measurement.grid13FwdBits}"
                }
            }

        return DegradationReport(
            name = "red suits ${channel.name}",
            total = baseline.size,
            measured = measured,
            missing = baseline.size - measured,
            stable = stable,
            averageConfidenceDelta = 0.0,
            examples = changed,
        )
    }

    private fun measureAllSheets(
        imageTransform: (BufferedImage) -> BufferedImage = { it },
        cropTransform: (LabeledBarcodeCrop, BufferedImage) -> LabeledBarcodeCrop = { crop, _ -> crop },
        channel: SheetInkChannel = SheetInkChannel.MIN_RGB,
    ): List<LabeledBarcodeMeasurement> =
        BarcodeSheetAnalyzer.suitSheets.flatMap { sheet ->
            val original = BarcodeSheetAnalyzer.loadResourceImage(sheet.resourceName)
            val transformed = imageTransform(original)
            BarcodeSheetAnalyzer.analyzeSuitSheet(sheet).crops.mapNotNull { originalCrop ->
                val crop = cropTransform(originalCrop, transformed)
                val measurement = measureGrid13Barcode(crop.toInkImage(transformed, channel))
                measurement?.let {
                    LabeledBarcodeMeasurement(
                        cardId = originalCrop.cardId,
                        crop = crop,
                        measurement = it,
                    )
                }
            }
        }
}

private data class DegradationReport(
    val name: String,
    val total: Int,
    val measured: Int,
    val missing: Int,
    val stable: Int,
    val averageConfidenceDelta: Double,
    val examples: List<String>,
) {
    val stabilityRate: Double = if (measured == 0) 0.0 else stable.toDouble() / measured
    val measuredRate: Double = if (total == 0) 0.0 else measured.toDouble() / total

    fun summary(): String =
        "$name stability=${"%.2f".format(stabilityRate)} measured=$measured/$total missing=$missing " +
            "avgConfidenceDelta=${"%.3f".format(averageConfidenceDelta)} examples=${examples.joinToString()}"
}

private fun LabeledBarcodeCrop.shifted(
    dx: Int,
    dy: Int,
    image: BufferedImage,
): LabeledBarcodeCrop {
    val shiftedBounds = bounds.copy(
        x = (bounds.x + dx).coerceIn(0, image.width - bounds.width),
        y = (bounds.y + dy).coerceIn(0, image.height - bounds.height),
    )
    return copy(bounds = shiftedBounds)
}

private fun BufferedImage.boxBlurred(): BufferedImage {
    val output = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    for (y in 0 until height) {
        for (x in 0 until width) {
            var red = 0
            var green = 0
            var blue = 0
            var alpha = 0
            var count = 0
            for (yy in (y - 1).coerceAtLeast(0)..(y + 1).coerceAtMost(height - 1)) {
                for (xx in (x - 1).coerceAtLeast(0)..(x + 1).coerceAtMost(width - 1)) {
                    val color = Color(getRGB(xx, yy), true)
                    alpha += color.alpha
                    red += color.red
                    green += color.green
                    blue += color.blue
                    count++
                }
            }
            output.setRGB(
                x,
                y,
                Color(red / count, green / count, blue / count, alpha / count).rgb,
            )
        }
    }
    return output
}

private fun BufferedImage.withExposure(delta: Int): BufferedImage {
    val output = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    for (y in 0 until height) {
        for (x in 0 until width) {
            val color = Color(getRGB(x, y), true)
            output.setRGB(
                x,
                y,
                Color(
                    (color.red + delta).coerceIn(0, 255),
                    (color.green + delta).coerceIn(0, 255),
                    (color.blue + delta).coerceIn(0, 255),
                    color.alpha,
                ).rgb,
            )
        }
    }
    return output
}

private fun BufferedImage.jpegRoundTrip(qualityHint: Float): BufferedImage {
    require(qualityHint in 0f..1f) { "qualityHint must be within 0..1" }
    val rgb = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val graphics = rgb.createGraphics()
    graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
    graphics.drawImage(this, 0, 0, null)
    graphics.dispose()

    val output = ByteArrayOutputStream()
    val writer = ImageIO.getImageWritersByFormatName("jpg").asSequence().firstOrNull()
        ?: error("No JPEG writer available")
    MemoryCacheImageOutputStream(output).use { imageOutput ->
        writer.output = imageOutput
        val params = writer.defaultWriteParam.apply {
            compressionMode = ImageWriteParam.MODE_EXPLICIT
            compressionQuality = qualityHint
        }
        writer.write(null, IIOImage(rgb, null, null), params)
        writer.dispose()
    }
    return ImageIO.read(ByteArrayInputStream(output.toByteArray()))
        ?: error("Could not read JPEG round-trip image at quality hint $qualityHint")
}

private fun BufferedImage.shearedX(shear: Double): BufferedImage {
    val output = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val graphics = output.createGraphics()
    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
    graphics.color = Color(255, 255, 255, 0)
    graphics.fillRect(0, 0, width, height)
    graphics.transform = AffineTransform.getShearInstance(shear, 0.0)
    graphics.drawImage(this, 0, 0, null)
    graphics.dispose()
    return output
}
