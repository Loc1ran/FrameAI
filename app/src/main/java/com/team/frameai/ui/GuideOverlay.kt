package com.team.frameai.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.team.frameai.guide.GuideEngine
import com.team.frameai.model.FaceBoxNormalized
import com.team.frameai.model.LiveGuide
import kotlin.math.abs

@Composable
fun GuideOverlay(guide: LiveGuide, area: FaceBoxNormalized?, coachEnabled: Boolean) {
    val accent = if (guide.isReady) FrameReady else FrameGold
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val stroke = 1.dp.toPx()
            val grid = FrameLine
            listOf(w / 3f, w * 2f / 3f).forEach { x ->
                drawLine(grid, Offset(x, 0f), Offset(x, h), stroke)
            }
            listOf(h / 3f, h * 2f / 3f).forEach { y ->
                drawLine(grid, Offset(0f, y), Offset(w, y), stroke)
            }
            if (!coachEnabled) return@Canvas
            if (guide.kind == GuideEngine.KIND_NO_FACE && guide.message == GuideEngine.NO_FACE_MESSAGE) {
                return@Canvas
            }
            if (guide.subjectX == null && guide.subjectY != null) {
                val horizon = guide.subjectY.toFloat() * h
                drawLine(accent, Offset(0f, horizon), Offset(w, horizon), 2.dp.toPx())
                if (!guide.isReady) {
                    val target = guide.targetY.toFloat() * h
                    drawLine(
                        accent.copy(alpha = 0.45f),
                        Offset(0f, target),
                        Offset(w, target),
                        2.dp.toPx(),
                    )
                }
            }
            if (area != null && area.width > 0.02f && area.height > 0.02f) {
                drawRoundRect(
                    color = accent.copy(alpha = 0.18f),
                    topLeft = Offset(area.left * w, area.top * h),
                    size = Size(area.width * w, area.height * h),
                    cornerRadius = CornerRadius(18.dp.toPx()),
                )
                drawRoundRect(
                    color = accent,
                    topLeft = Offset(area.left * w, area.top * h),
                    size = Size(area.width * w, area.height * h),
                    cornerRadius = CornerRadius(18.dp.toPx()),
                    style = Stroke(2.dp.toPx()),
                )
            }
            val sx = guide.subjectX
            val sy = guide.subjectY
            val showMark = (guide.message == GuideEngine.ALIGN_MESSAGE ||
                guide.message == GuideEngine.PLACE_ANY_MESSAGE) &&
                sx != null &&
                sy != null &&
                (abs(sx - guide.targetX) > 0.08 || abs(sy - guide.targetY) > 0.10)
            if (showMark) {
                drawCircle(
                    accent,
                    radius = 28.dp.toPx(),
                    center = Offset(guide.targetX.toFloat() * w, guide.targetY.toFloat() * h),
                    style = Stroke(2.dp.toPx()),
                )
            }
            if (sx != null && sy != null) {
                drawCircle(FramePaper, radius = 7.dp.toPx(), center = Offset(sx.toFloat() * w, sy.toFloat() * h))
            }
        }
        if (coachEnabled) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 96.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = sourceLabel(guide.source),
                    color = FrameInk,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .background(accent, RoundedCornerShape(99.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
                Text(
                    text = guide.message,
                    color = FramePaper,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(top = 8.dp, start = 24.dp, end = 24.dp),
                )
            }
        }
    }
}
