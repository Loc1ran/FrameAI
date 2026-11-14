package com.team.frameai.lut

import kotlin.math.roundToInt

class UnknownLutException(val lutId: String) : IllegalArgumentException("Unknown LUT id: $lutId")

/**
 * Grades a copy of an image. The input [RgbImage.pixels] array is never written.
 * This is a still-photo grade, not a live GPU preview.
 */
class LutRepository(
    private val luts: Map<String, CubeLut>,
) {
    fun apply(image: RgbImage, lutId: String, intensity: Float = DEFAULT_INTENSITY): RgbImage {
        val lut = luts[lutId] ?: throw UnknownLutException(lutId)
        val graded = IntArray(image.pixels.size)
        gradeArgb(image.pixels, graded, lut, intensity.coerceIn(0f, 1f))
        return RgbImage(image.width, image.height, graded)
    }

    fun ids(): Set<String> = luts.keys

    companion object {
        const val DEFAULT_INTENSITY = 0.7f
    }
}

private fun gradeArgb(source: IntArray, dest: IntArray, lut: CubeLut, intensity: Float) {
    val mapped = FloatArray(3)
    for (index in source.indices) {
        val color = source[index]
        if (intensity == 0f) {
            dest[index] = color
            continue
        }
        val alpha = (color ushr 24) and 0xFF
        val red = (color shr 16) and 0xFF
        val green = (color shr 8) and 0xFF
        val blue = color and 0xFF
        lut.mapInto(red / 255f, green / 255f, blue / 255f, mapped)
        val outRed = blendChannel(red, mapped[0], intensity)
        val outGreen = blendChannel(green, mapped[1], intensity)
        val outBlue = blendChannel(blue, mapped[2], intensity)
        dest[index] = (alpha shl 24) or (outRed shl 16) or (outGreen shl 8) or outBlue
    }
}

private fun blendChannel(original: Int, gradedUnit: Float, intensity: Float): Int {
    val graded = gradedUnit.coerceIn(0f, 1f) * 255f
    val mixed = original * (1f - intensity) + graded * intensity
    return mixed.roundToInt().coerceIn(0, 255)
}
