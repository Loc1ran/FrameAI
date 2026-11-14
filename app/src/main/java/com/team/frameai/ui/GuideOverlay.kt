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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.team.frameai.model.LiveGuide

@Composable
fun GuideOverlay(guide: LiveGuide, coachEnabled: Boolean) {
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
            val target = Offset(guide.targetX.toFloat() * w, guide.targetY.toFloat() * h)
            drawCircle(accent, radius = 42.dp.toPx(), center = target, style = Stroke(2.dp.toPx()))
            val sx = guide.subjectX
            val sy = guide.subjectY
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
