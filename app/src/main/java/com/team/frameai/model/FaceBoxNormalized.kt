package com.team.frameai.model

/** Face center and bounds in a normalized 0..1 space. Origin is the top-left. */
data class FaceBoxNormalized(
    val centerX: Float,
    val centerY: Float,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    companion object {
        fun fromEdges(left: Float, top: Float, right: Float, bottom: Float): FaceBoxNormalized {
            return FaceBoxNormalized(
                centerX = (left + right) / 2f,
                centerY = (top + bottom) / 2f,
                left = left,
                top = top,
                right = right,
                bottom = bottom,
            )
        }

        fun clamped(left: Float, top: Float, right: Float, bottom: Float): FaceBoxNormalized? {
            if (!left.isFinite() || !top.isFinite() || !right.isFinite() || !bottom.isFinite()) {
                return null
            }
            val clampedLeft = left.coerceIn(0f, 1f)
            val clampedTop = top.coerceIn(0f, 1f)
            val clampedRight = right.coerceIn(0f, 1f)
            val clampedBottom = bottom.coerceIn(0f, 1f)
            if (clampedRight - clampedLeft < 0.0001f || clampedBottom - clampedTop < 0.0001f) {
                return null
            }
            return fromEdges(clampedLeft, clampedTop, clampedRight, clampedBottom)
        }
    }
}
