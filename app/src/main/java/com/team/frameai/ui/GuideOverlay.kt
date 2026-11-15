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
import com.team.frameai.guide.FrameRect
import com.team.frameai.guide.GeometryCoach
import com.team.frameai.guide.LightingAdviceEngine
import com.team.frameai.guide.GuidePicture
import com.team.frameai.guide.NormLine
import com.team.frameai.guide.SceneGeometry
import com.team.frameai.model.FaceBoxNormalized
import com.team.frameai.model.LiveGuide
import kotlin.math.hypot

/**
 * Dashed amber is the recommended frame. Solid white is the subject that is on screen now.
 * The arrow points the way to move the phone, which is opposite the way the subject should slide.
 */
@Composable
fun GuideOverlay(
    guide: LiveGuide?,
    area: FaceBoxNormalized?,
    enabled: Boolean,
    geometry: SceneGeometry? = null,
) {
    if (!enabled) return
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
            if (guide == null) return@Canvas
            if (LightingAdviceEngine.isCue(guide.message)) {
                val picture = GuidePicture.of(guide, area)
                if (area != null && area.width > 0.02f && area.height > 0.02f) {
                    drawSolid(picture.current, FramePaper)
                }
                return@Canvas
            }
            val geometryCue = guide.message == GeometryCoach.ROTATE_CLOCKWISE ||
                guide.message == GeometryCoach.ROTATE_COUNTERCLOCKWISE ||
                guide.message == GeometryCoach.CENTER_ANGLE ||
                guide.message == GeometryCoach.STRAIGHT_ON
            val accent = if (guide.isReady) FrameReady else FrameGold
            if (geometryCue) {
                drawGeometry(guide, geometry, accent)
                val picture = GuidePicture.of(guide, area)
                if (area != null && area.width > 0.02f && area.height > 0.02f) {
                    drawSolid(picture.current, if (guide.isReady) FrameReady else FramePaper)
                }
                val rotating = guide.message == GeometryCoach.ROTATE_CLOCKWISE ||
                    guide.message == GeometryCoach.ROTATE_COUNTERCLOCKWISE
                if (!rotating && guide.message != GeometryCoach.SEPARATE_SHAPES &&
                    (guide.subjectX != null || area != null)
                ) {
                    drawCameraArrow(guide, area, accent)
                }
                return@Canvas
            }
            if (guide.subjectX == null && guide.subjectY == null && area == null) {
                if (guide.isReady) drawCheck(Offset(w / 2f, h / 2f), FrameReady)
                return@Canvas
            }
            val horizon = guide.subjectX == null && guide.subjectY != null
            if (horizon) {
                drawHorizon(guide, accent)
                return@Canvas
            }
            val picture = GuidePicture.of(guide, area)
            drawDashed(picture.goal, accent)
            if (area != null && area.width > 0.02f && area.height > 0.02f) {
                drawSolid(picture.current, if (guide.isReady) FrameReady else FramePaper)
            }
            if (guide.isReady) {
                drawCheck(centerOf(picture.goal), FrameReady)
            } else if (guide.message != GeometryCoach.SEPARATE_SHAPES) {
                drawCameraArrow(guide, area, accent)
            }
        }
    }
}

private fun DrawScope.drawGeometry(guide: LiveGuide, geometry: SceneGeometry?, accent: Color) {
    val rollAgrees = when (guide.message) {
        GeometryCoach.ROTATE_CLOCKWISE -> geometry != null && geometry.rollDegrees >= 2f
        GeometryCoach.ROTATE_COUNTERCLOCKWISE -> geometry != null && geometry.rollDegrees <= -2f
        else -> false
    }
    val centering = guide.message == GeometryCoach.CENTER_ANGLE || guide.message == GeometryCoach.STRAIGHT_ON
    val detected = if (rollAgrees || (centering && geometry != null && geometry.symmetry >= 0.8f)) {
        geometry?.detected
    } else {
        null
    }
    val leveled = when {
        centering -> NormLine(0.5f, 0.08f, 0.5f, 0.92f)
        rollAgrees -> geometry?.leveled
        else -> null
    }
    detected?.let { drawNormLine(it, FramePaper, dashed = false) }
    leveled?.let { drawNormLine(it, accent, dashed = true) }
}

private fun DrawScope.drawNormLine(line: NormLine, color: Color, dashed: Boolean) {
    drawLine(
        color,
        Offset(line.x1 * size.width, line.y1 * size.height),
        Offset(line.x2 * size.width, line.y2 * size.height),
        2.dp.toPx(),
        pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(14.dp.toPx(), 10.dp.toPx())) else null,
    )
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
        val phoneY = (guide.subjectY ?: guide.targetY).toFloat() - guide.targetY.toFloat()
        drawCameraArrow(Offset(w / 2f, current), 0f, phoneY, accent)
    }
}

private fun DrawScope.drawCameraArrow(guide: LiveGuide, area: FaceBoxNormalized?, color: Color) {
    val subjectX = area?.anchorX ?: guide.subjectX?.toFloat() ?: return
    val subjectY = area?.anchorY ?: guide.subjectY?.toFloat() ?: return
    val phoneX = subjectX - guide.targetX.toFloat()
    val phoneY = subjectY - guide.targetY.toFloat()
    drawCameraArrow(Offset(subjectX * size.width, subjectY * size.height), phoneX, phoneY, color)
}

private fun DrawScope.drawCameraArrow(origin: Offset, phoneX: Float, phoneY: Float, color: Color) {
    val length = hypot(phoneX.toDouble(), phoneY.toDouble()).toFloat()
    if (length < 0.06f) return
    val reach = 88.dp.toPx()
    val end = Offset(origin.x + phoneX / length * reach, origin.y + phoneY / length * reach)
    drawArrow(origin, end, color, 3.dp.toPx())
}

private fun DrawScope.drawDashed(rect: FrameRect, color: Color) {
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
}

private fun DrawScope.drawSolid(rect: FrameRect, color: Color) {
    val px = toPx(rect)
    drawRoundRect(color, px.topLeft, px.size, CornerRadius(18.dp.toPx()), style = Stroke(2.5.dp.toPx()))
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
    if (length < 28.dp.toPx()) return
    val ux = dx / length
    val uy = dy / length
    val head = 16.dp.toPx()
    val wing = 9.dp.toPx()
    val tip = Offset(to.x - ux * 8.dp.toPx(), to.y - uy * 8.dp.toPx())
    drawLine(color, from, tip, stroke)
    val base = Offset(tip.x - ux * head, tip.y - uy * head)
    drawLine(color, tip, Offset(base.x - uy * wing, base.y + ux * wing), stroke)
    drawLine(color, tip, Offset(base.x + uy * wing, base.y - ux * wing), stroke)
}

private data class PxRect(val topLeft: Offset, val size: Size)

private fun DrawScope.centerOf(rect: FrameRect): Offset {
    return Offset(rect.centerX * size.width, rect.centerY * size.height)
}

private fun DrawScope.toPx(rect: FrameRect): PxRect {
    return PxRect(
        Offset(rect.left * size.width, rect.top * size.height),
        Size(kotlin.math.abs(rect.width) * size.width, kotlin.math.abs(rect.height) * size.height),
    )
}
