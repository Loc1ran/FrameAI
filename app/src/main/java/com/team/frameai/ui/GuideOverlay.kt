package com.team.frameai.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.team.frameai.model.FaceBoxNormalized

@Composable
fun GuideOverlay(area: FaceBoxNormalized?, enabled: Boolean, ready: Boolean) {
    if (!enabled) return
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            listOf(1f / 3f, 2f / 3f).forEach {
                drawLine(FrameLine.copy(alpha = 0.25f), Offset(w * it, 0f), Offset(w * it, h), 1.dp.toPx())
                drawLine(FrameLine.copy(alpha = 0.25f), Offset(0f, h * it), Offset(w, h * it), 1.dp.toPx())
            }
            val target = Offset(w / 2, h / 2)
            val color = if (ready) FrameReady else FrameGold
            drawRect(color.copy(alpha = 0.10f),
                Offset(target.x - TARGET_RADIUS * w, target.y - TARGET_RADIUS * h),
                Size(TARGET_RADIUS * 2 * w, TARGET_RADIUS * 2 * h))
            drawRect(color,
                Offset(target.x - TARGET_RADIUS * w, target.y - TARGET_RADIUS * h),
                Size(TARGET_RADIUS * 2 * w, TARGET_RADIUS * 2 * h), style = Stroke(2.dp.toPx()))
            drawLine(color, Offset(target.x - 6.dp.toPx(), target.y), Offset(target.x + 6.dp.toPx(), target.y), 1.dp.toPx())
            drawLine(color, Offset(target.x, target.y - 6.dp.toPx()), Offset(target.x, target.y + 6.dp.toPx()), 1.dp.toPx())
            area?.let {
                drawRect(FramePaper.copy(alpha = 0.65f),
                    Offset(it.left * w, it.top * h), Size(it.width * w, it.height * h), style = Stroke(1.dp.toPx()))
                val center = Offset(it.centerX * w, it.centerY * h)
                drawCircle(FrameInk, 7.dp.toPx(), center)
                drawCircle(FramePaper, 4.dp.toPx(), center)
            }
        }
        Row(Modifier.align(Alignment.BottomCenter).padding(10.dp)
            .background(FrameInk.copy(alpha = 0.85f), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("● Subject", color = FramePaper, fontSize = 10.sp)
            Text("□ Center guide", color = if (ready) FrameReady else FrameGold, fontSize = 10.sp)
        }
    }
}
