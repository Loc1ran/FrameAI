package com.team.frameai.vision

import com.team.frameai.model.FaceBoxNormalized

/**
 * Coordinate helpers for a single ML Kit face.
 *
 * ML Kit's box is already in the upright image when InputImage is built with the frame rotation.
 * Use [normalizeUpright] for that box. Use [sensorBoxToUpright] only when the rectangle is still
 * in the unrotated sensor buffer. Doing both will place the marker on the wrong side.
 */
object FaceBoxMath {
    fun uprightSize(sensorWidth: Int, sensorHeight: Int, rotationDegrees: Int): Pair<Int, Int> {
        val rotation = quarterTurns(rotationDegrees)
        return if (rotation == 90 || rotation == 270) {
            sensorHeight to sensorWidth
        } else {
            sensorWidth to sensorHeight
        }
    }

    fun normalizeUpright(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        uprightWidth: Int,
        uprightHeight: Int,
    ): FaceBoxNormalized? {
        if (uprightWidth <= 0 || uprightHeight <= 0) return null
        val width = uprightWidth.toFloat()
        val height = uprightHeight.toFloat()
        return FaceBoxNormalized.clamped(left / width, top / height, right / width, bottom / height)
    }

    /**
     * Clockwise rotation of a normalized sensor point into upright normalized space.
     * Image origin is top-left, x to the right, y down.
     */
    fun sensorPointToUpright(nx: Float, ny: Float, rotationDegrees: Int): Pair<Float, Float> {
        return when (quarterTurns(rotationDegrees)) {
            0 -> nx to ny
            90 -> (1f - ny) to nx
            180 -> (1f - nx) to (1f - ny)
            270 -> ny to (1f - nx)
            else -> error("rotation was not a quarter turn")
        }
    }

    fun sensorBoxToUpright(box: FaceBoxNormalized, rotationDegrees: Int): FaceBoxNormalized {
        val corners = arrayOf(
            box.left to box.top,
            box.right to box.top,
            box.right to box.bottom,
            box.left to box.bottom,
        )
        var minX = 1f
        var minY = 1f
        var maxX = 0f
        var maxY = 0f
        for ((x, y) in corners) {
            val (uprightX, uprightY) = sensorPointToUpright(x, y, rotationDegrees)
            minX = minOf(minX, uprightX)
            minY = minOf(minY, uprightY)
            maxX = maxOf(maxX, uprightX)
            maxY = maxOf(maxY, uprightY)
        }
        return FaceBoxNormalized(
            left = minX.coerceIn(0f, 1f),
            top = minY.coerceIn(0f, 1f),
            right = maxX.coerceIn(0f, 1f),
            bottom = maxY.coerceIn(0f, 1f),
        )
    }

    fun selectPrimary(faces: List<FaceBoxNormalized>): FaceBoxNormalized? {
        return faces.maxWithOrNull(compareBy { it.width * it.height })
    }

    private fun quarterTurns(rotationDegrees: Int): Int {
        val rotation = ((rotationDegrees % 360) + 360) % 360
        if (rotation % 90 != 0) {
            throw IllegalArgumentException("rotation must be a multiple of 90, was $rotationDegrees")
        }
        return rotation
    }
}
