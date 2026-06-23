package org.itroboc.vision

private const val GRID13_BIT_COUNT = 13

fun grid13BitsToHex(bits13: String): String {
    validateGrid13Bits(bits13)
    val paddedBits = bits13.padStart(length = 16, padChar = '0')
    return paddedBits
        .toInt(radix = 2)
        .toString(radix = 16)
        .uppercase()
        .padStart(length = 4, padChar = '0')
}

fun forwardMealSignature(bits13: String): String = "bfm${grid13BitsToHex(bits13)}"

fun reverseMealSignature(bits13: String): String = "brm${grid13BitsToHex(bits13)}"

fun reverseBits(bits13: String): String {
    validateGrid13Bits(bits13)
    return bits13.reversed()
}

private fun validateGrid13Bits(bits13: String) {
    require(bits13.length == GRID13_BIT_COUNT) {
        "Grid13 bits must contain exactly $GRID13_BIT_COUNT bits"
    }
    require(bits13.all { it == '0' || it == '1' }) {
        "Grid13 bits may contain only '0' and '1'"
    }
}
