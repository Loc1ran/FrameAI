package com.team.frameai.ui

import com.team.frameai.guide.CoachAction
import com.team.frameai.guide.GeometryCoach
import com.team.frameai.guide.GuideEngine
import com.team.frameai.guide.LightingAdviceEngine
import com.team.frameai.model.FaceBoxNormalized
import com.team.frameai.model.LiveGuide

data class FramingHint(val title: String, val detail: String, val symbol: String, val aligned: Boolean = false)

/**
 * The suggestion stays on the move in [instruction]. A reply from an older move is not shown.
 * A model sentence is shown only when it starts with that same instruction.
 */
internal fun matchingSuggestion(instruction: String, message: String?, sentFor: String?): String {
    val move = instruction.trim()
    if (move.isEmpty()) return message?.trim().orEmpty()
    val reply = message?.trim().orEmpty()
    if (sentFor?.trim() != move || reply.isEmpty()) return move
    return if (reply.startsWith(move, ignoreCase = true)) reply else move
}

/**
 * The card repeats [LiveGuide.message]. Direction comes from that recommendation, not from the frame center.
 */
fun framingHint(guide: LiveGuide?, area: FaceBoxNormalized? = null): FramingHint {
    if (guide == null) {
        return FramingHint(
            "Find your subject",
            "Point at one person or a clear subject.",
            "◎",
        )
    }
    return FramingHint(
        title = guide.message,
        detail = detailFor(guide),
        symbol = symbolFor(guide, area),
        aligned = guide.isReady,
    )
}

private fun detailFor(guide: LiveGuide): String {
    LightingAdviceEngine.detail(guide.message)?.let { return it }
    return when {
        guide.isReady -> "The frame matches the goal."
        guide.message == GeometryCoach.ROTATE_CLOCKWISE ||
            guide.message == GeometryCoach.ROTATE_COUNTERCLOCKWISE ->
            "The solid line is the edge. The dashed line is level."
        guide.message == GeometryCoach.CENTER_ANGLE ||
            guide.message == GeometryCoach.STRAIGHT_ON ->
            "The dashed line shows a more centered angle."
        guide.message == GeometryCoach.SEPARATE_SHAPES ->
            "A small step to either side can separate the shapes."
        else -> "The dashed frame is the goal. The arrow shows how to move the phone."
    }
}

private fun symbolFor(guide: LiveGuide, area: FaceBoxNormalized?): String {
    LightingAdviceEngine.symbol(guide.message)?.let { return it }
    if (guide.isReady) return "✓"
    if (guide.message == GeometryCoach.ROTATE_CLOCKWISE) return "↻"
    if (guide.message == GeometryCoach.ROTATE_COUNTERCLOCKWISE) return "↺"
    if (guide.message == GeometryCoach.SEPARATE_SHAPES) return "↔"
    if (guide.message == GuideEngine.BREATHING_CUE) return "↔"
    val subjectX = area?.anchorX ?: guide.subjectX?.toFloat()
    val subjectY = area?.anchorY ?: guide.subjectY?.toFloat()
    if (subjectX == null || subjectY == null) return "◎"
    return when (CoachAction.slightMove(subjectX, subjectY, guide.targetX.toFloat(), guide.targetY.toFloat())) {
        CoachAction.MOVE_LEFT -> "←"
        CoachAction.MOVE_RIGHT -> "→"
        CoachAction.MOVE_UP -> "↑"
        CoachAction.MOVE_DOWN -> "↓"
        else -> "↗"
    }
}
