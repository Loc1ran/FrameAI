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
    /** Average luma inside the subject box, when the sampler measured it. */
    val subjectLuma: Float? = null,
    /** Edge and symmetry measurements. Null when the frame could not be sampled. */
    val geometry: com.team.frameai.guide.SceneGeometry? = null,
    /** Highest intersection-over-union between detected object boxes. Zero when unknown. */
    val shapeOverlap: Float = 0f,
) {
    fun hasSideBias(): Boolean = kotlin.math.abs(leftLuma - rightLuma) > SIDE_BIAS

    fun hasHorizon(): Boolean = horizonY != null

    /** The face is much darker than the surrounding frame. Missing measurements are not treated as backlight. */
    fun faceInShadow(): Boolean {
        val face = subjectLuma ?: return false
        if (!face.isFinite()) return false
        val background = (leftLuma + rightLuma) / 2f
        return face < 0.35f && background - face >= 0.22f
    }

    val lookId: String
        get() = if (warmth < -0.08f) LutIds.COOL_CINEMA else LutIds.WARM_FILM

    companion object {
        const val SIDE_BIAS = 0.12f
    }
}
