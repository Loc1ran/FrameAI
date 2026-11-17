package com.team.frameai.guide

import com.team.frameai.model.LiveGuide
import kotlin.math.abs

/**
 * Keeps one Athena direction until the subject reaches it.
 * The target does not chase the subject, so the arrow can be followed and the frame can turn green.
 */
class AimLock {
    private var key = ""
    private var targetX = 0.5f
    private var targetY = 0.5f
    private var originHeight: Float? = null
    private var locked = false

    fun clear() {
        key = ""
        locked = false
        originHeight = null
    }

    fun draw(
        action: String,
        preset: String,
        subjectX: Float?,
        subjectY: Float?,
        subjectHeight: Float?,
        geminiReady: Boolean,
        issue: String,
        rollDegrees: Float?,
        instruction: String,
    ): LiveGuide? {
        if (action == "straighten" || action == "change_lighting" || action == "expose_for_subject") {
            return AiDrawing.guide(action, preset, subjectX, subjectY, geminiReady, rollDegrees, instruction)
        }
        val nextKey = "$action|$preset"
        if (nextKey != key) {
            key = nextKey
            locked = false
            originHeight = subjectHeight
        }
        if (action == "hold" && preset == "none") {
            val x = subjectX ?: 0.5f
            val y = subjectY ?: 0.5f
            return AiDrawing.pinned(instruction, x, y, x, y, ready = true)
        }
        val x = subjectX
        val y = subjectY
        if (!locked) {
            if (x == null || y == null) {
                val provisional = place(action, preset, 0.5f, 0.5f)
                return AiDrawing.pinned(instruction, 0.5f, 0.5f, provisional.first, provisional.second, ready = false)
            }
            val placed = place(action, preset, x, y)
            targetX = placed.first
            targetY = placed.second
            originHeight = subjectHeight
            locked = true
        }
        val currentX = x ?: targetX
        val currentY = y ?: targetY
        val ready = geminiReady || issue == "already_strong" || arrived(action, currentX, currentY, subjectHeight)
        return AiDrawing.pinned(instruction, currentX, currentY, targetX, targetY, ready)
    }

    private fun arrived(action: String, x: Float, y: Float, height: Float?): Boolean {
        if (action == "move_closer") {
            val start = originHeight ?: return false
            val now = height ?: return false
            return now >= start + GROWTH
        }
        if (action == "move_farther") {
            val start = originHeight ?: return false
            val now = height ?: return false
            return start - now >= GROWTH
        }
        return abs(x - targetX) <= ARRIVE_X && abs(y - targetY) <= ARRIVE_Y
    }

    private fun place(action: String, preset: String, x: Float, y: Float): Pair<Float, Float> {
        val stepped = when (action) {
            "pan_left", "step_left" -> x + STEP to y
            "pan_right", "step_right" -> x - STEP to y
            "tilt_up", "raise_camera", "move_closer" -> x to y + STEP
            "tilt_down", "lower_camera", "move_farther" -> x to y - STEP
            "hold" -> presetPoint(preset, x, y) ?: (x to y)
            else -> presetPoint(preset, x, y) ?: (x to y)
        }
        return stepped.first.coerceIn(0.08f, 0.92f) to stepped.second.coerceIn(0.08f, 0.92f)
    }

    private fun presetPoint(preset: String, x: Float, y: Float): Pair<Float, Float>? = when (preset) {
        "centered" -> 0.5f to y
        "negative_space_left", "leading_space" -> GuideEngine.THIRD_HI to y
        "negative_space_right" -> GuideEngine.THIRD_LO to y
        "low_horizon" -> x to GuideEngine.THIRD_HI
        "high_horizon" -> x to GuideEngine.THIRD_LO
        else -> null
    }

    private companion object {
        const val STEP = 0.22f
        const val ARRIVE_X = 0.12f
        const val ARRIVE_Y = 0.12f
        const val GROWTH = 0.08f
    }
}
