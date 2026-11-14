package com.team.frameai.model

/**
 * One face rectangle in a normalized 0..1 space.
 * Origin is the top-left of that space. [centerX] and [centerY] are what the guide compares to the target.
 * The Android adapter maps this into PreviewView space before the guide sees it.
 */
data class FaceBoxNormalized(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    init {
        require(left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite()) {
            "Face box contains a non-finite edge"
        }
        require(left in 0f..1f && top in 0f..1f && right in 0f..1f && bottom in 0f..1f) {
            "Face box must stay inside 0..1"
        }
        require(right >= left && bottom >= top) { "Face box is inverted" }
    }

    companion object {
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
            return FaceBoxNormalized(clampedLeft, clampedTop, clampedRight, clampedBottom)
        }
    }
}
