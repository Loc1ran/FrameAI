package com.team.frameai.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.team.frameai.guide.OdysseyStage
import com.team.frameai.guide.OdysseyTrial

@Composable
internal fun OwlMark(modifier: Modifier = Modifier) {
    Canvas(modifier.size(22.dp)) {
        val gold = FrameGold
        drawCircle(gold, radius = size.minDimension * 0.46f, style = Stroke(1.5.dp.toPx()))
        drawCircle(gold, radius = 1.6.dp.toPx(), center = Offset(size.width * 0.38f, size.height * 0.42f))
        drawCircle(gold, radius = 1.6.dp.toPx(), center = Offset(size.width * 0.62f, size.height * 0.42f))
        drawLine(
            gold,
            Offset(size.width * 0.50f, size.height * 0.50f),
            Offset(size.width * 0.50f, size.height * 0.64f),
            strokeWidth = 1.4.dp.toPx(),
        )
    }
}

@Composable
internal fun AthenaCard(
    stage: OdysseyStage,
    title: String,
    detail: String,
    symbol: String,
    offer: OdysseyTrial?,
    active: OdysseyTrial?,
    lesson: String?,
    onAccept: (OdysseyTrial) -> Unit,
    onDecline: (OdysseyTrial) -> Unit,
    modifier: Modifier = Modifier,
) {
    val stageName = when (stage) {
        OdysseyStage.DEPARTURE -> "Discover"
        OdysseyStage.TRIALS -> "Improve"
        OdysseyStage.TRANSFORMATION -> "Learn"
    }
    Column(
        modifier.fillMaxWidth().background(FrameSurface, RoundedCornerShape(12.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OwlMark()
            Text("ATHENA", color = FrameGold, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Text(stageName, color = FrameMuted, fontSize = 11.sp, modifier = Modifier.weight(1f))
            Text(symbol, color = if (stage == OdysseyStage.TRANSFORMATION) FrameGold else FramePaper, fontSize = 18.sp)
        }
        Text(title, color = FramePaper, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 18.sp)
        Text(detail, color = FrameMuted, fontSize = 12.sp, lineHeight = 15.sp)
        lesson?.let {
            Text(it, color = FramePaper, fontSize = 12.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 2.dp))
        }
        active?.let {
            Text(
                "${it.title} · watching for a real change",
                color = FrameGold,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (active == null && lesson == null && offer != null) {
            Text(offer.pitch, color = FramePaper, fontSize = 12.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 2.dp))
            Row {
                TextButton(onClick = { onAccept(offer) }) { Text(offer.title) }
                TextButton(onClick = { onDecline(offer) }) { Text("Not now") }
            }
        }
    }
}
