package com.team.frameai.guide

import com.team.frameai.model.AdviceSource
import com.team.frameai.model.LiveCoach
import com.team.frameai.model.LiveGuide
import kotlin.math.abs

/**
 * Pan/tilt actions draw image-space targets. Physical viewpoint changes have no
 * image-space destination: keeping a subject centered does not mean a walk is complete.
 */
object AiDrawing {
    private const val STEP = 0.22f

    fun symbol(action: String, ready: Boolean): String = when {
        ready && action !in LiveCoach.PHYSICAL -> "✓"
        ViewpointGuidance.cue(action) != null -> ViewpointGuidance.cue(action)!!.symbol
        action == "pan_left" -> "←"
        action == "pan_right" -> "→"
        action == "tilt_up" -> "↑"
        action == "tilt_down" -> "↓"
        action == "straighten" -> "↻"
        action == "hold" -> "◎"
        else -> "◎"
    }

    fun guide(
        action: String,
        preset: String,
        subjectX: Float?,
        subjectY: Float?,
        ready: Boolean,
        rollDegrees: Float?,
        instruction: String,
    ): LiveGuide? {
        if (action == "straighten") return level(rollDegrees, instruction)
        if (action in LiveCoach.PHYSICAL) {
            return marker(instruction, subjectX ?: 0.5f, subjectY ?: 0.5f, ready = false, hideGoal = true)
                .copy(cueSymbol = symbol(action, false), targetZoomRatio = 1.0)
        }
        val x = subjectX
        val y = subjectY
        if (x == null || y == null) {
            return horizon(action, preset, instruction)
        }
        if (ready && action == "hold") {
            return marker(instruction, x, y, ready = true, hideGoal = false)
        }
        val target = when (action) {
            "pan_left" -> x + STEP to y
            "pan_right" -> x - STEP to y
            "tilt_up" -> x to y + STEP
            "tilt_down" -> x to y - STEP
            "hold" -> presetTarget(preset, x, y) ?: return null
            else -> presetTarget(preset, x, y) ?: return null
        }
        return marker(
            instruction,
            target.first.coerceIn(0.08f, 0.92f),
            target.second.coerceIn(0.08f, 0.92f),
            ready = false,
            hideGoal = false,
            subjectX = x,
            subjectY = y,
        )
    }

    /** Positive phone X means the arrow points right. */
    fun phoneX(subjectX: Float, targetX: Float): Float = subjectX - targetX

    fun phoneY(subjectY: Float, targetY: Float): Float = subjectY - targetY

    private fun presetTarget(preset: String, subjectX: Float, subjectY: Float): Pair<Float, Float>? = when (preset) {
        "centered" -> 0.5f to subjectY
        "negative_space_left", "leading_space" -> GuideEngine.THIRD_HI to subjectY
        "negative_space_right" -> GuideEngine.THIRD_LO to subjectY
        "low_horizon" -> subjectX to GuideEngine.THIRD_HI
        "high_horizon" -> subjectX to GuideEngine.THIRD_LO
        else -> null
    }

    private fun horizon(action: String, preset: String, instruction: String): LiveGuide? {
        val y = when {
            preset == "low_horizon" || action == "tilt_down" -> GuideEngine.THIRD_HI
            preset == "high_horizon" || action == "tilt_up" -> GuideEngine.THIRD_LO
            else -> return null
        }
        return marker(instruction, 0.5f, y, ready = false, hideGoal = false, subjectY = if (y == GuideEngine.THIRD_HI) GuideEngine.THIRD_LO else GuideEngine.THIRD_HI)
    }

    private fun level(rollDegrees: Float?, instruction: String): LiveGuide {
        val message = when {
            rollDegrees == null -> GeometryCoach.STRAIGHT_ON
            rollDegrees >= 2f -> GeometryCoach.ROTATE_CLOCKWISE
            rollDegrees <= -2f -> GeometryCoach.ROTATE_COUNTERCLOCKWISE
            else -> instruction
        }
        val level = rollDegrees != null && abs(rollDegrees) < 2f
        return marker(message, 0.5f, 0.5f, ready = level, hideGoal = false)
    }

    private fun marker(
        message: String,
        targetX: Float,
        targetY: Float,
        ready: Boolean,
        hideGoal: Boolean,
        zoom: String = GuideEngine.ZOOM_HOLD,
        subjectX: Float? = null,
        subjectY: Float? = null,
    ): LiveGuide {
        return LiveGuide(
            kind = if (ready) GuideEngine.KIND_READY else GuideEngine.KIND_ALIGN,
            message = message.ifBlank { "Hold this frame." },
            targetX = targetX.toDouble(),
            targetY = targetY.toDouble(),
            subjectX = subjectX?.toDouble(),
            subjectY = subjectY?.toDouble(),
            isReady = ready,
            source = AdviceSource.GEMINI.wire,
            zoom = zoom,
            hideGoal = hideGoal,
        )
    }
}
