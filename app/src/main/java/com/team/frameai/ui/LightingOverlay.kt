package com.team.frameai.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.team.frameai.guide.LightingMap
import kotlin.math.abs

private val Shade = Color(0x553D6F8F)
private val Glare = Color(0x55C4492C)

/** A coarse tint from the measured luma grid. Balanced cells stay clear. */
@Composable
fun LightingOverlay(map: LightingMap?, enabled: Boolean) {
    if (!enabled || map == null) return
    Canvas(Modifier.fillMaxSize()) {
        val columns = map.columns
        val rows = map.rows
        for (row in 0 until rows) {
            for (col in 0 until columns) {
                val index = row * columns + col
                val band = map.bands[index].toInt()
                if (band == 0) continue
                val x = map.xs[index]
                val y = map.ys[index]
                if (x < -0.02f || x > 1.02f || y < -0.02f || y > 1.02f) continue
                val nextX = if (col + 1 < columns) index + 1 else index - 1
                val nextY = if (row + 1 < rows) index + columns else index - columns
                val dx = abs(map.xs[nextX] - x)
                val dy = abs(map.ys[nextY] - y)
                if (dx <= 0.002f || dy <= 0.002f || dx > 0.2f || dy > 0.2f) continue
                drawRect(
                    if (band == 1) Shade else Glare,
                    Offset((x - dx / 2f) * size.width, (y - dy / 2f) * size.height),
                    Size(dx * size.width, dy * size.height),
                )
            }
        }
    }
}
