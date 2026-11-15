package com.team.frameai.model

/**
 * A coarse read of the current frame, outside the face.
 * Luma is 0..1. Warmth is negative for cool light and positive for warm light.
 */
data class SceneRead(
    val leftLuma: Float,
    val rightLuma: Float,
    val warmth: Float,
    val topLuma: Float = 0.5f,
    val bottomLuma: Float = 0.5f,
    val horizonY: Float? = null,
) {
    fun hasSideBias(): Boolean = kotlin.math.abs(leftLuma - rightLuma) > SIDE_BIAS

    fun hasHorizon(): Boolean = horizonY != null

    val lookId: String
        get() = if (warmth < -0.08f) LutIds.COOL_CINEMA else LutIds.WARM_FILM

    companion object {
        const val SIDE_BIAS = 0.12f
    }
}
