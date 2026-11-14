package com.team.frameai.lut

/**
 * Packed ARGB pixels. The LUT changes RGB only and keeps the alpha byte.
 */
class RgbImage(
    val width: Int,
    val height: Int,
    val pixels: IntArray,
) {
    init {
        require(width > 0 && height > 0)
        require(pixels.size == width * height)
    }

    fun checksum(): Long {
        var sum = 0L
        for (pixel in pixels) {
            sum += pixel.toLong() and 0xffffffffL
        }
        return sum
    }
}
