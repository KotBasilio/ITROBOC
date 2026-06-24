package org.itroboc.vision

private const val GRID13_BIT_COUNT = 13
private const val GRID13_MAX_VALUE = (1 shl GRID13_BIT_COUNT) - 1
private const val GRID13_SENTINEL_MASK = 0x1803
private const val GRID13_SENTINEL_VALUE = 0x1001

data class Grid13SentinelCheck(
    val isValid: Boolean,
    val bit12: Boolean,
    val bit11: Boolean,
    val bit1: Boolean,
    val bit0: Boolean,
    val issues: List<String>,
)

fun checkGrid13Sentinels(value: Int): Grid13SentinelCheck {
    require(value in 0..GRID13_MAX_VALUE) {
        "Grid13 value must fit within $GRID13_BIT_COUNT bits"
    }

    val bit12 = value and 0x1000 != 0
    val bit11 = value and 0x0800 != 0
    val bit1 = value and 0x0002 != 0
    val bit0 = value and 0x0001 != 0
    val issues = buildList {
        if (!bit12) add("bit12 must be black")
        if (bit11) add("bit11 must be white")
        if (bit1) add("bit1 must be white")
        if (!bit0) add("bit0 must be black")
    }

    return Grid13SentinelCheck(
        isValid = value and GRID13_SENTINEL_MASK == GRID13_SENTINEL_VALUE,
        bit12 = bit12,
        bit11 = bit11,
        bit1 = bit1,
        bit0 = bit0,
        issues = issues,
    )
}

fun checkGrid13Sentinels(bits13: String): Grid13SentinelCheck {
    validateGrid13Bits(bits13)
    return checkGrid13Sentinels(bits13.toInt(radix = 2))
}

fun normalizeGrid13Sentinels(bits13: String): String {
    validateGrid13Bits(bits13)
    return buildString(capacity = GRID13_BIT_COUNT) {
        append('1')
        append('0')
        append(bits13.substring(2, 11))
        append('0')
        append('1')
    }
}

fun grid13RunLengthSignature(bits13: String): String {
    validateGrid13Bits(bits13)
    val runs = mutableListOf<String>()
    var currentBit = bits13.first()
    var currentLength = 1

    for (bit in bits13.drop(1)) {
        if (bit == currentBit) {
            currentLength += 1
        } else {
            runs += "${if (currentBit == '1') 'B' else 'W'}$currentLength"
            currentBit = bit
            currentLength = 1
        }
    }
    runs += "${if (currentBit == '1') 'B' else 'W'}$currentLength"
    return runs.joinToString("-")
}

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
