package com.team.frameai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.team.frameai.guide.Odyssey

internal data class VoyageInk(val line: Color, val light: Color, val wash: Color)

internal object VoyageTheme {
    private val dawn = VoyageInk(Color(0xFFE7A08C), Color(0xFFF6D2C6), Color(0xFF3A221C))
    private val wine = VoyageInk(Color(0xFF8B97C4), Color(0xFFD5DCF2), Color(0xFF141824))
    private val olive = VoyageInk(Color(0xFF8FA56A), Color(0xFFD5E2B8), Color(0xFF1A2214))
    private val bronze = VoyageInk(Color(0xFFC4A15A), Color(0xFFF0D7A0), Color(0xFF2A2114))
    private val hall = VoyageInk(Color(0xFFA48BC4), Color(0xFFE4D8F2), Color(0xFF1A1422))
    private val ithaca = VoyageInk(FramePaper, FramePaper, Color(0xFF141414))

    fun of(episode: Odyssey.Episode): VoyageInk = when (episode) {
        Odyssey.Episode.THRESHOLD -> dawn
        Odyssey.Episode.CROSSING -> wine
        Odyssey.Episode.RETURN -> bronze
    }

    fun ofLook(lutId: String): VoyageInk = when (lutId) {
        "warm_film" -> dawn
        "cool_cinema" -> wine
        "g_outdoors1" -> olive
        "eastman_neg_5247_warm" -> bronze
        "cinematic_06" -> hall
        else -> ithaca
    }
}

@Composable
internal fun VoyageCourse(episode: Odyssey.Episode, ready: Boolean, modifier: Modifier = Modifier) {
    val ink = VoyageTheme.of(episode)
    val index = episode.ordinal
    Column(
        modifier
            .background(ink.wash.copy(alpha = 0.94f), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Odyssey.Episode.entries.forEachIndexed { stop, place ->
                val stopInk = VoyageTheme.of(place)
                val reached = stop <= index
                Box(
                    Modifier
                        .size(if (stop == index) 14.dp else 10.dp)
                        .border(1.5.dp, stopInk.line, CircleShape)
                        .background(if (reached) stopInk.light else Color.Transparent, CircleShape),
                )
                if (stop < Odyssey.Episode.entries.lastIndex) {
                    Box(
                        Modifier
                            .padding(horizontal = 4.dp)
                            .width(28.dp)
                            .height(2.dp)
                            .background(if (stop < index) stopInk.light else stopInk.line.copy(alpha = 0.35f)),
                    )
                }
            }
        }
        Text(
            episode.place.uppercase(),
            color = ink.light,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            if (ready) episode.readyLine else episode.aim,
            color = FramePaper,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 2.dp).width(220.dp),
        )
    }
}

@Composable
internal fun VoyageBeads(ids: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        ids.forEachIndexed { index, id ->
            val ink = VoyageTheme.ofLook(id)
            val on = index == selected
            val name = Odyssey.nameOf(id) ?: "Ithaca"
            Box(
                Modifier
                    .size(if (on) 16.dp else 10.dp)
                    .clip(CircleShape)
                    .background(if (on) ink.light else ink.line.copy(alpha = 0.4f))
                    .clickable { onSelect(index) }
                    .semantics { contentDescription = name },
            )
        }
    }
}
