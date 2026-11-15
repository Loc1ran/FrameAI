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
    /** Upright-image anchor after it has been converted into the unrotated buffer. */
    val anchorX: Float? = null,
    val anchorY: Float? = null,
    val anchorKind: AnchorKind = AnchorKind.BOX,
    /** ML Kit Euler Y in the upright image. Positive turns toward that image's right. Null if unavailable. */
    val yawDegrees: Float? = null,
)
