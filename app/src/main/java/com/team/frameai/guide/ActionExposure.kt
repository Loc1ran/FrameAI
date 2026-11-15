package com.team.frameai.guide

import kotlin.math.roundToInt

data class ActionExposurePlan(val nanos: Long, val iso: Int)

object ActionExposure {
    /** Keep metered brightness by increasing ISO as exposure shortens. Reject >1 stop loss. */
    fun plan(seconds: Double, iso: Int, minNanos: Long, maxNanos: Long,
             minIso: Int, maxIso: Int): ActionExposurePlan? {
        if (!seconds.isFinite() || seconds <= 0 || iso <= 0 || minNanos <= 0 ||
            maxNanos < minNanos || minIso <= 0 || maxIso < minIso) return null
        val target = minOf(1_000_000L, (seconds * 1e9).toLong()).coerceIn(minNanos, maxNanos)
        if (target > 2_000_000L) return null
        val needed = iso * seconds * 1e9 / target
        if (needed > maxIso * 2.0) return null
        return ActionExposurePlan(target, needed.roundToInt().coerceIn(minIso, maxIso))
    }
}
