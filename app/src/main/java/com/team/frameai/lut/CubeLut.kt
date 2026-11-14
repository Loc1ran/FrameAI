package com.team.frameai.lut

import kotlin.math.floor

class CubeParseException(message: String) : IllegalArgumentException(message)

/**
 * 3D .cube LUT. Table order is red-fastest, then green, then blue.
 * [mapInto] expects RGB in the same units as DOMAIN_MIN / DOMAIN_MAX (0..1 for the bundled files).
 */
class CubeLut(
    val size: Int,
    val domainMin: FloatArray,
    val domainMax: FloatArray,
    private val table: FloatArray,
) {
    fun mapInto(r: Float, g: Float, b: Float, out: FloatArray) {
        val x = domainIndex(r, domainMin[0], domainMax[0])
        val y = domainIndex(g, domainMin[1], domainMax[1])
        val z = domainIndex(b, domainMin[2], domainMax[2])
        val x0 = floor(x).toInt().coerceIn(0, size - 1)
        val y0 = floor(y).toInt().coerceIn(0, size - 1)
        val z0 = floor(z).toInt().coerceIn(0, size - 1)
        val x1 = (x0 + 1).coerceAtMost(size - 1)
        val y1 = (y0 + 1).coerceAtMost(size - 1)
        val z1 = (z0 + 1).coerceAtMost(size - 1)
        val tx = if (x1 == x0) 0f else (x - x0).coerceIn(0f, 1f)
        val ty = if (y1 == y0) 0f else (y - y0).coerceIn(0f, 1f)
        val tz = if (z1 == z0) 0f else (z - z0).coerceIn(0f, 1f)
        for (channel in 0 until 3) {
            val c000 = sample(x0, y0, z0, channel)
            val c100 = sample(x1, y0, z0, channel)
            val c010 = sample(x0, y1, z0, channel)
            val c110 = sample(x1, y1, z0, channel)
            val c001 = sample(x0, y0, z1, channel)
            val c101 = sample(x1, y0, z1, channel)
            val c011 = sample(x0, y1, z1, channel)
            val c111 = sample(x1, y1, z1, channel)
            val c00 = lerp(c000, c100, tx)
            val c10 = lerp(c010, c110, tx)
            val c01 = lerp(c001, c101, tx)
            val c11 = lerp(c011, c111, tx)
            val c0 = lerp(c00, c10, ty)
            val c1 = lerp(c01, c11, ty)
            out[channel] = lerp(c0, c1, tz)
        }
    }

    private fun domainIndex(value: Float, min: Float, max: Float): Float {
        val normalized = ((value - min) / (max - min)).coerceIn(0f, 1f)
        return normalized * (size - 1)
    }

    private fun sample(redIndex: Int, greenIndex: Int, blueIndex: Int, channel: Int): Float {
        val index = (redIndex + size * (greenIndex + size * blueIndex)) * 3 + channel
        return table[index]
    }

    private fun lerp(from: Float, to: Float, amount: Float): Float {
        return from + (to - from) * amount
    }
}

fun parseCube(text: String): CubeLut {
    val data = ArrayList<Float>(256)
    var size: Int? = null
    var domainMin = floatArrayOf(0f, 0f, 0f)
    var domainMax = floatArrayOf(1f, 1f, 1f)
    var saw1d = false
    val maxFloats = MAX_SIZE * MAX_SIZE * MAX_SIZE * 3
    for (raw in text.split('\n')) {
        val line = raw.trim().removePrefix("\uFEFF").removeSuffix("\r")
        if (line.isEmpty() || line.startsWith("#")) continue
        val parts = line.split(Regex("\\s+"))
        val key = parts[0]
        when {
            key.equals("TITLE", ignoreCase = true) -> Unit
            key.equals("LUT_1D_SIZE", ignoreCase = true) -> saw1d = true
            key.equals("LUT_3D_SIZE", ignoreCase = true) -> {
                val parsed = parts.getOrNull(1)?.toIntOrNull()
                    ?: throw CubeParseException("LUT_3D_SIZE is missing an integer")
                if (parsed !in 2..MAX_SIZE) {
                    throw CubeParseException("LUT_3D_SIZE $parsed is not supported")
                }
                size = parsed
            }
            key.equals("DOMAIN_MIN", ignoreCase = true) -> domainMin = readDomain(parts, "DOMAIN_MIN")
            key.equals("DOMAIN_MAX", ignoreCase = true) -> domainMax = readDomain(parts, "DOMAIN_MAX")
            key.equals("LUT_3D_INPUT_RANGE", ignoreCase = true) -> Unit
            parts.size == 3 && parts.all { it.toFloatOrNull() != null } -> {
                if (data.size + 3 > maxFloats) {
                    throw CubeParseException("LUT has too many RGB rows")
                }
                val red = parts[0].toFloat()
                val green = parts[1].toFloat()
                val blue = parts[2].toFloat()
                if (!red.isFinite() || !green.isFinite() || !blue.isFinite()) {
                    throw CubeParseException("LUT row is not finite")
                }
                data.add(red)
                data.add(green)
                data.add(blue)
            }
            else -> throw CubeParseException("Unrecognized LUT line")
        }
    }
    if (saw1d) throw CubeParseException("1D LUTs are not supported")
    val resolvedSize = size ?: throw CubeParseException("LUT_3D_SIZE is required")
    val expectedRows = resolvedSize * resolvedSize * resolvedSize
    val actualRows = data.size / 3
    if (data.size != expectedRows * 3) {
        throw CubeParseException("Expected $expectedRows RGB rows, found $actualRows")
    }
    for (channel in 0 until 3) {
        if (!(domainMax[channel] > domainMin[channel])) {
            throw CubeParseException("DOMAIN_MAX must be greater than DOMAIN_MIN")
        }
    }
    return CubeLut(resolvedSize, domainMin, domainMax, data.toFloatArray())
}

private const val MAX_SIZE = 64

private fun readDomain(parts: List<String>, label: String): FloatArray {
    if (parts.size != 4) throw CubeParseException("$label must contain 3 values")
    val values = FloatArray(3)
    for (index in 0 until 3) {
        val value = parts[index + 1].toFloatOrNull()
            ?: throw CubeParseException("$label is not numeric")
        if (!value.isFinite()) throw CubeParseException("$label is not finite")
        values[index] = value
    }
    return values
}
