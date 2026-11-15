package com.team.frameai.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.team.frameai.model.FaceBoxNormalized

/** Scene mode protects the whole outline without forcing its center onto a target. */
@Composable
fun SceneOverlay(area: FaceBoxNormalized?, enabled: Boolean, ready: Boolean) {
    if (!enabled) return
    Canvas(Modifier.fillMaxSize()) {
        val color = if (ready) FrameReady else FrameGold
        val dash = PathEffect.dashPathEffect(floatArrayOf(12.dp.toPx(), 8.dp.toPx()))
        for (y in listOf(0.06f, 0.92f)) {
            drawLine(color.copy(alpha = 0.7f), Offset(0f, size.height * y),
                Offset(size.width, size.height * y), 2.dp.toPx(), pathEffect = dash)
        }
        area?.let {
            drawRect(color, Offset(it.left * size.width, it.top * size.height),
                Size(it.width * size.width, it.height * size.height), style = Stroke(2.dp.toPx()))
        }
    }
}
