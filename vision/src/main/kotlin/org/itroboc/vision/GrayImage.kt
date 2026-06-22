package org.itroboc.vision

data class GrayImage(
    val width: Int,
    val height: Int,
    val pixels: ByteArray,
) {
    init {
        require(width > 0) { "width must be positive" }
        require(height > 0) { "height must be positive" }
        require(pixels.size == width * height) {
            "pixels size must match width * height"
        }
    }

    fun intensityAt(x: Int, y: Int): Int {
        require(x in 0 until width) { "x out of bounds: $x" }
        require(y in 0 until height) { "y out of bounds: $y" }
        return pixels[(y * width) + x].toInt() and 0xFF
    }
}
