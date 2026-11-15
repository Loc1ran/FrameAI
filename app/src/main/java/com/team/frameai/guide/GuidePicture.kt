package com.team.frameai.guide

import com.team.frameai.model.FaceBoxNormalized
import com.team.frameai.model.LiveGuide

data class FrameRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
}

enum class FrameCue {
    MOVE,
    GROW,
    SHRINK,
    READY,
}

data class CoachPicture(
    val current: FrameRect,
    val goal: FrameRect,
    val cue: FrameCue,
)

/**
 * The picture on the preview. [current] is what the camera sees.
 * [goal] is the frame to make. No sentence is required.
 */
object GuidePicture {
    fun of(guide: LiveGuide, face: FaceBoxNormalized?): CoachPicture {
        val current = currentOf(guide, face)
        if (guide.isReady) return CoachPicture(current, current, FrameCue.READY)
        val wantedGrow = guide.zoom == GuideEngine.ZOOM_IN || guide.message == GuideEngine.SMALL_MESSAGE
        val wantedShrink = guide.zoom == GuideEngine.ZOOM_OUT ||
            guide.message == GuideEngine.LARGE_MESSAGE ||
            guide.message == GuideEngine.EDGE_MESSAGE
        val growBy = enlarge(current)
        val shrinkBy = reduce(current)
        val grow = wantedGrow && growBy >= 1.15f
        val shrink = !grow && wantedShrink && shrinkBy <= 0.87f
        val goal = when {
            grow -> slot(current.centerX, current.centerY, current.width * growBy, current.height * growBy)
            shrink -> slot(current.centerX, current.centerY, current.width * shrinkBy, current.height * shrinkBy)
            else -> slot(guide.targetX.toFloat(), guide.targetY.toFloat(), current.width, current.height)
        }
        val cue = when {
            grow -> FrameCue.GROW
            shrink -> FrameCue.SHRINK
            else -> FrameCue.MOVE
        }
        return CoachPicture(current, goal, cue)
    }

    private fun enlarge(current: FrameRect): Float {
        val width = current.width.coerceAtLeast(0.05f)
        val height = current.height.coerceAtLeast(0.05f)
        val byWidth = GuideEngine.IDEAL_FACE_WIDTH / width
        val byHeight = GuideEngine.IDEAL_FACE_HEIGHT / height
        return maxOf(byWidth, byHeight).coerceIn(1f, 4f)
    }

    private fun reduce(current: FrameRect): Float {
        val width = current.width.coerceAtLeast(0.05f)
        val height = current.height.coerceAtLeast(0.05f)
        val byWidth = GuideEngine.IDEAL_FACE_WIDTH / width
        val byHeight = GuideEngine.IDEAL_FACE_HEIGHT / height
        return minOf(byWidth, byHeight).coerceIn(0.35f, 1f)
    }

    private fun currentOf(guide: LiveGuide, face: FaceBoxNormalized?): FrameRect {
        if (face != null && face.width > 0.02f && face.height > 0.02f) {
            return inside(FrameRect(face.left, face.top, face.right, face.bottom))
        }
        val x = guide.subjectX?.toFloat()
        val y = guide.subjectY?.toFloat()
        if (x != null && y != null) return slot(x, y, 0.30f, 0.38f)
        return slot(0.5f, 0.5f, 0.30f, 0.38f)
    }

    private fun slot(centerX: Float, centerY: Float, width: Float, height: Float): FrameRect {
        val w = width.coerceIn(0.16f, 0.78f)
        val h = height.coerceIn(0.18f, 0.84f)
        return inside(FrameRect(centerX - w / 2f, centerY - h / 2f, centerX + w / 2f, centerY + h / 2f))
    }

    private fun inside(rect: FrameRect): FrameRect {
        val dx = when {
            rect.left < 0.03f -> 0.03f - rect.left
            rect.right > 0.97f -> 0.97f - rect.right
            else -> 0f
        }
        val dy = when {
            rect.top < 0.03f -> 0.03f - rect.top
            rect.bottom > 0.97f -> 0.97f - rect.bottom
            else -> 0f
        }
        return FrameRect(rect.left + dx, rect.top + dy, rect.right + dx, rect.bottom + dy)
    }
}
