package com.team.frameai.guide

import kotlin.math.abs

/**
 * Turns the on-screen frames into a lens ratio.
 * The dashed frame compared with the solid frame is the scale, applied to the zoom the camera is using now.
 */
object ZoomLens {
    fun target(current: Float, scale: Float, min: Float, max: Float): Float? {
        if (!current.isFinite() || !scale.isFinite() || scale <= 0f) return null
        if (min > max) return null
        val target = (current * scale).coerceIn(min, max)
        if (abs(target - current) < 0.05f) return null
        return target
    }
}
