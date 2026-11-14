package com.team.frameai.model

/**
 * Primary face in the unrotated ImageProxy buffer.
 * Values are normalized to ImageProxy width and height, not to the preview.
 */
data class RawFaceBox(
    val centerX: Float,
    val centerY: Float,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)
