package com.team.frameai.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.team.frameai.guide.GuideEngine
import com.team.frameai.guide.PortraitFrame
import com.team.frameai.model.FaceBoxNormalized
import com.team.frameai.model.LiveGuide
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Shows the picture the frame should become.
 * A dashed shape is the goal. A solid shape is what the camera sees now.
 * An arrow connects them. No sentence is required.
 */
@Composable
fun GuideOverlay(guide: LiveGuide, area: FaceBoxNormalized?, coachEnabled: Boolean) {
    val accent = if (guide.isReady) FrameReady else FrameGold
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            listOf(w / 3f, w * 2f / 3f).forEach { x ->
                drawLine(FrameLine, Offset(x, 0f), Offset(x, h), 1.dp.toPx())
            }
            listOf(h / 3f, h * 2f / 3f).forEach { y ->
                drawLine(FrameLine, Offset(0f, y), Offset(w, y), 1.dp.toPx())
            }
            if (!coachEnabled) return@Canvas
            if (guide.kind == GuideEngine.KIND_NO_FACE && guide.message == GuideEngine.NO_FACE_MESSAGE) {
                drawGhost(slotAround(guide.targetX.toFloat(), guide.targetY.toFloat(), 0.34f, 0.46f), FrameGold)
                return@Canvas
            }

            val horizon = guide.subjectX == null && guide.subjectY != null
            if (horizon) {
                drawHorizon(guide, accent)
                return@Canvas
            }

            val goal = goalSlot(guide, area)
            drawGhost(goal, accent)
            if (area != null && area.width > 0.02f && area.height > 0.02f) {
                drawSolid(area.toNorm(), if (guide.isReady) FrameReady else FramePaper)
            }
            if (!guide.isReady) {
                val from = when {
                    area != null && area.width > 0.02f -> Offset(area.centerX * w, area.centerY * h)
                    guide.subjectX != null && guide.subjectY != null ->
                        Offset(guide.subjectX.toFloat() * w, guide.subjectY.toFloat() * h)
                    else -> Offset(w / 2f, h / 2f)
                }
                val to = Offset((goal.left + goal.right) / 2f * w, (goal.top + goal.bottom) / 2f * h)
                drawArrow(from, to, accent, 3.dp.toPx())
            } else {
                drawCheck(Offset((goal.left + goal.right) / 2f * w, (goal.top + goal.bottom) / 2f * h), FrameReady)
            }
        }
    }
}

private fun DrawScope.drawHorizon(guide: LiveGuide, accent: Color) {
    val w = size.width
    val h = size.height
    val current = guide.subjectY?.toFloat()?.times(h) ?: return
    val target = guide.targetY.toFloat() * h
    val top = minOf(current, target)
    val bottom = maxOf(current, target)
    drawRect(accent.copy(alpha = 0.22f), Offset(0f, top), Size(w, (bottom - top).coerceAtLeast(1f)))
    drawLine(FramePaper, Offset(0f, current), Offset(w, current), 2.dp.toPx())
    drawLine(
        accent,
        Offset(0f, target),
        Offset(w, target),
        3.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(18.dp.toPx(), 12.dp.toPx())),
    )
    if (guide.isReady) {
        drawCheck(Offset(w / 2f, target), FrameReady)
    } else {
        drawArrow(Offset(w / 2f, current), Offset(w / 2f, target), accent, 3.dp.toPx())
    }
}

private fun goalSlot(guide: LiveGuide, area: FaceBoxNormalized?): NormRect {
    val seen = area?.takeIf { it.width > 0.02f && it.height > 0.02f }
    if (guide.zoom != null) {
        val slot = PortraitFrame.goal(
            guide.targetX.toFloat(),
            guide.targetY.toFloat(),
            guide.zoom,
            seen,
        )
        return slotAround(slot.centerX, slot.centerY, slot.width, slot.height)
    }
    return when (guide.message) {
        GuideEngine.SMALL_MESSAGE -> slotAround(
            seen?.centerX ?: guide.targetX.toFloat(),
            seen?.centerY ?: guide.targetY.toFloat(),
            ((seen?.width ?: 0.2f) * 1.8f).coerceIn(0.34f, 0.62f),
            ((seen?.height ?: 0.24f) * 1.8f).coerceIn(0.42f, 0.72f),
        )
        GuideEngine.LARGE_MESSAGE -> slotAround(
            seen?.centerX ?: guide.targetX.toFloat(),
            seen?.centerY ?: guide.targetY.toFloat(),
            0.34f,
            0.42f,
        )
        GuideEngine.EDGE_MESSAGE -> seen?.let { inside(it.toNorm()) }
            ?: slotAround(guide.targetX.toFloat(), guide.targetY.toFloat(), 0.34f, 0.46f)
        else -> {
            val width = seen?.width ?: 0.34f
            val height = seen?.height ?: 0.46f
            slotAround(guide.targetX.toFloat(), guide.targetY.toFloat(), width, height)
        }
    }
}

private fun DrawScope.drawGhost(rect: NormRect, color: Color) {
    val px = toPx(rect)
    drawRoundRect(color.copy(alpha = 0.16f), px.topLeft, px.size, CornerRadius(18.dp.toPx()))
    drawRoundRect(
        color,
        px.topLeft,
        px.size,
        CornerRadius(18.dp.toPx()),
        style = Stroke(
            width = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(16.dp.toPx(), 10.dp.toPx())),
        ),
    )
    drawBrackets(px, color)
}

private fun DrawScope.drawSolid(rect: NormRect, color: Color) {
    val px = toPx(rect)
    drawRoundRect(color.copy(alpha = 0.14f), px.topLeft, px.size, CornerRadius(18.dp.toPx()))
    drawRoundRect(color, px.topLeft, px.size, CornerRadius(18.dp.toPx()), style = Stroke(2.5.dp.toPx()))
}

private fun DrawScope.drawBrackets(px: PxRect, color: Color) {
    val arm = 18.dp.toPx()
    val stroke = 3.dp.toPx()
    val left = px.topLeft.x
    val top = px.topLeft.y
    val right = left + px.size.width
    val bottom = top + px.size.height
    listOf(
        Offset(left, top) to Offset(1f, 1f),
        Offset(right, top) to Offset(-1f, 1f),
        Offset(left, bottom) to Offset(1f, -1f),
        Offset(right, bottom) to Offset(-1f, -1f),
    ).forEach { (corner, sign) ->
        drawLine(color, corner, Offset(corner.x + sign.x * arm, corner.y), stroke)
        drawLine(color, corner, Offset(corner.x, corner.y + sign.y * arm), stroke)
    }
}

private fun DrawScope.drawCheck(center: Offset, color: Color) {
    val stroke = 3.dp.toPx()
    drawLine(color, Offset(center.x - 12.dp.toPx(), center.y), Offset(center.x - 4.dp.toPx(), center.y + 8.dp.toPx()), stroke)
    drawLine(color, Offset(center.x - 4.dp.toPx(), center.y + 8.dp.toPx()), Offset(center.x + 14.dp.toPx(), center.y - 10.dp.toPx()), stroke)
}

private fun DrawScope.drawArrow(from: Offset, to: Offset, color: Color, stroke: Float) {
    val dx = to.x - from.x
    val dy = to.y - from.y
    val length = hypot(dx.toDouble(), dy.toDouble()).toFloat()
    if (length < 36.dp.toPx()) return
    val ux = dx / length
    val uy = dy / length
    val head = 16.dp.toPx()
    val wing = 9.dp.toPx()
    val tip = Offset(to.x - ux * 10.dp.toPx(), to.y - uy * 10.dp.toPx())
    drawLine(color, from, tip, stroke)
    val base = Offset(tip.x - ux * head, tip.y - uy * head)
    drawLine(color, tip, Offset(base.x - uy * wing, base.y + ux * wing), stroke)
    drawLine(color, tip, Offset(base.x + uy * wing, base.y - ux * wing), stroke)
}

private data class NormRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

private data class PxRect(val topLeft: Offset, val size: Size)

private fun FaceBoxNormalized.toNorm() = NormRect(left, top, right, bottom)

private fun slotAround(cx: Float, cy: Float, width: Float, height: Float): NormRect {
    return inside(NormRect(cx - width / 2f, cy - height / 2f, cx + width / 2f, cy + height / 2f))
}

private fun inside(rect: NormRect): NormRect {
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
    return NormRect(rect.left + dx, rect.top + dy, rect.right + dx, rect.bottom + dy)
}

private fun DrawScope.toPx(rect: NormRect): PxRect {
    val w = size.width
    val h = size.height
    return PxRect(
        Offset(rect.left * w, rect.top * h),
        Size(abs(rect.right - rect.left) * w, abs(rect.bottom - rect.top) * h),
    )
}
