package com.team.frameai.ui

import com.team.frameai.guide.CoachAction
import com.team.frameai.guide.GeometryCoach
import com.team.frameai.guide.GuideEngine
import com.team.frameai.guide.LightingAdviceEngine
import com.team.frameai.model.FaceBoxNormalized
import com.team.frameai.model.LiveGuide

data class FramingHint(val title: String, val detail: String, val symbol: String, val aligned: Boolean = false)

/** One optional look name. The line never repeats a camera move. */
internal fun coachingLookLine(name: String?): String? {
    val look = name?.trim().orEmpty()
    if (look.isEmpty()) return null
    return "Suggested look · $look"
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
        detail = guide.detail?.takeIf { it.isNotBlank() } ?: detailFor(guide),
        symbol = when {
            guide.isReady -> "✓"
            !guide.cueSymbol.isNullOrBlank() -> guide.cueSymbol
            else -> symbolFor(guide, area)
        },
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
