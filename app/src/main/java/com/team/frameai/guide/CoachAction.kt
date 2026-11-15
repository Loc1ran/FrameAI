package com.team.frameai.guide

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The one thing the preview asks for.
 * A move label is the way to shift the phone. Panning right slides the subject left,
 * so the phone direction is opposite the gap from the subject to the dashed frame.
 */
object CoachAction {
    const val MOVE_LEFT = "Move slightly left."
    const val MOVE_RIGHT = "Move slightly right."
    const val MOVE_UP = "Move camera upward."
    const val MOVE_DOWN = "Move camera downward."

    fun label(
        cue: FrameCue,
        zoomPossible: Boolean,
        horizon: Boolean,
        ready: Boolean,
        subjectX: Float = 0.5f,
        subjectY: Float = 0.5f,
        goalX: Float = 0.5f,
        goalY: Float = 0.5f,
        zoomScale: Float = 1f,
    ): String? {
        if (ready) return null
        if (horizon) return tilt(subjectY, goalY)
        return when (cue) {
            FrameCue.GROW -> zoom(grow = true, scale = zoomScale, zoomPossible = zoomPossible)
            FrameCue.SHRINK -> zoom(grow = false, scale = zoomScale, zoomPossible = zoomPossible)
            FrameCue.MOVE -> move(subjectX, subjectY, goalX, goalY)
            FrameCue.READY -> null
        }
    }

    fun zoom(grow: Boolean, scale: Float, zoomPossible: Boolean): String {
        val times = if (grow) scale else 1f / scale.coerceAtLeast(0.125f)
        val verb = when {
            grow && zoomPossible -> "Zoom in"
            grow -> "Step closer"
            zoomPossible -> "Zoom out"
            else -> "Step back"
        }
        return "$verb ${factorText(times)}"
    }

    private fun factorText(times: Float): String {
        val tenths = (times.coerceIn(1.1f, 8f) * 10f).roundToInt()
        val whole = tenths / 10
        val frac = tenths % 10
        return if (frac == 0) "$whole×" else "$whole.$frac×"
    }

    fun move(subjectX: Float, subjectY: Float, goalX: Float, goalY: Float): String {
        return phrase("Move", subjectX - goalX, subjectY - goalY)
    }

    /**
     * One camera-move sentence. The phone direction is opposite the on-screen gap
     * from the subject anchor to the dashed frame, same sign as [move].
     */
    fun slightMove(subjectX: Float, subjectY: Float, goalX: Float, goalY: Float): String {
        val horizontal = horizontalWord(subjectX - goalX)
        val vertical = verticalWord(subjectY - goalY)
        val choice = when {
            horizontal == null -> vertical
            vertical == null -> horizontal
            abs(subjectY - goalY) > abs(subjectX - goalX) -> vertical
            else -> horizontal
        }
        return when (choice) {
            "left" -> MOVE_LEFT
            "right" -> MOVE_RIGHT
            "up" -> MOVE_UP
            "down" -> MOVE_DOWN
            else -> "Move toward the dashed frame."
        }
    }

    fun tilt(horizonY: Float, goalY: Float): String {
        val vertical = verticalWord(horizonY - goalY) ?: return "Tilt"
        return "Tilt $vertical"
    }

    private fun phrase(verb: String, phoneX: Float, phoneY: Float): String {
        val words = directions(phoneX, phoneY)
        return when (words.size) {
            0 -> verb
            1 -> "$verb ${words[0]}"
            else -> "$verb ${words[0]} and ${words[1]}"
        }
    }

    private fun directions(phoneX: Float, phoneY: Float): List<String> {
        val horizontal = horizontalWord(phoneX)
        val vertical = verticalWord(phoneY)
        return when {
            horizontal == null -> listOfNotNull(vertical)
            vertical == null -> listOf(horizontal)
            abs(phoneY) >= abs(phoneX) -> listOf(vertical, horizontal)
            else -> listOf(horizontal, vertical)
        }
    }

    private fun horizontalWord(phoneX: Float): String? = when {
        phoneX > DEAD_ZONE -> "right"
        phoneX < -DEAD_ZONE -> "left"
        else -> null
    }

    private fun verticalWord(phoneY: Float): String? = when {
        phoneY > DEAD_ZONE -> "down"
        phoneY < -DEAD_ZONE -> "up"
        else -> null
    }

    private const val DEAD_ZONE = 0.06f
}
