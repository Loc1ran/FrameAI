package com.team.frameai.guide

import com.team.frameai.model.FaceBoxNormalized

/**
 * Dashed goal for a person. The center is a third.
 * Zooming in draws that goal larger than the face. Zooming out draws it smaller.
 * Holding zoom keeps the person's current size and only moves the goal onto the third.
 */
object PortraitFrame {
    data class Slot(val centerX: Float, val centerY: Float, val width: Float, val height: Float)

    fun goal(targetX: Float, targetY: Float, zoom: String?, face: FaceBoxNormalized?): Slot {
        val (width, height) = when (zoom) {
            GuideEngine.ZOOM_IN, GuideEngine.ZOOM_OUT ->
                GuideEngine.IDEAL_FACE_WIDTH to GuideEngine.IDEAL_FACE_HEIGHT
            else -> {
                if (face != null && face.width > 0.02f && face.height > 0.02f) {
                    face.width to face.height
                } else {
                    GuideEngine.IDEAL_FACE_WIDTH to GuideEngine.IDEAL_FACE_HEIGHT
                }
            }
        }
        return Slot(targetX, targetY, width, height)
    }
}
