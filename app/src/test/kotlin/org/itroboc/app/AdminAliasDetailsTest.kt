package org.itroboc.app

import org.itroboc.vision.BarcodeDebugInfo
import org.itroboc.vision.DetectedSignature
import kotlin.test.Test
import kotlin.test.assertContains

class AdminAliasDetailsTest {
    @Test
    fun `grid13 evidence displays detailed scan fields`() {
        val signature = DetectedSignature(
            rawSignature = "bfm1549",
            confidence = 0.93,
            debug = BarcodeDebugInfo(
                signatureModel = "grid13-v2",
                reverseSignature = "brm1255",
                grid13FwdBits = "1010101001001",
                grid13RevBits = "1001001010101",
                rl2 = "B1-W1-B1",
                blackRunsPx = listOf(3, 4, 5),
                whiteGapsPx = listOf(4, 4),
                warnings = listOf("test warning"),
            ),
        )

        val details = AdminAliasDetails(
            rawSignature = signature.rawSignature,
            evidence = signature.toAdminAliasScanEvidence(),
        )

        val lines = details.displayLines()

        assertContains(lines, "Alias: bfm1549")
        assertContains(lines, "Model: grid13-v2")
        assertContains(lines, "Forward bits: 1010101001001")
        assertContains(lines, "Reverse bits: 1001001010101")
        assertContains(lines, "Reverse token: brm1255")
        assertContains(lines, "RL2: B1-W1-B1")
        assertContains(lines, "Black runs: [3, 4, 5]")
        assertContains(lines, "White gaps: [4, 4]")
        assertContains(lines, "Confidence: 0.93")
        assertContains(lines, "Warnings: test warning")
    }

    @Test
    fun `missing evidence is stated explicitly`() {
        val lines = AdminAliasDetails(
            rawSignature = "bfm1549",
            evidence = null,
        ).displayLines()

        assertContains(lines, "Alias: bfm1549")
        assertContains(lines, "No scan evidence is retained for this alias in the current edit session.")
    }
}
