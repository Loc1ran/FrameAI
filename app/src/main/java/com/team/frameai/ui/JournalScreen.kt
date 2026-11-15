package com.team.frameai.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.team.frameai.camera.ImageSaver
import com.team.frameai.guide.JournalEntry
import com.team.frameai.guide.OdysseyJournal
import com.team.frameai.guide.OdysseyTrial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun JournalScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val journal = remember { OdysseyJournal(File(context.filesDir, "odyssey/journal.json")) }
    var entries by remember { mutableStateOf<List<JournalEntry>>(emptyList()) }
    LaunchedEffect(Unit) {
        entries = withContext(Dispatchers.IO) { journal.load() }
    }
    Column(
        Modifier.fillMaxSize().background(FrameInk).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Odyssey Journal", color = FramePaper, fontSize = 24.sp)
                Text("Photographs and the lesson each one taught.", color = FrameMuted, fontSize = 12.sp)
            }
            TextButton(onClick = onBack) { Text("Camera") }
        }
        if (entries.isEmpty()) {
            Text(
                "No entries yet. Take a photo, then add it from the review screen. A trial is optional.",
                color = FrameMuted,
            )
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            entries.forEach { entry ->
                JournalCard(entry)
            }
        }
    }
}

@Composable
private fun JournalCard(entry: JournalEntry) {
    var bitmap by remember(entry.photoPath) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(entry.photoPath) {
        bitmap = withContext(Dispatchers.IO) { ImageSaver.decodeOriented(entry.photoPath, 720) }
    }
    Column(
        Modifier.fillMaxWidth().background(FrameSurface, RoundedCornerShape(16.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val trial = OdysseyTrial.entries.firstOrNull { it.name == entry.trial }
        Text(trial?.let { "${it.title} — ${it.skill}" } ?: entry.style, color = FrameGold, fontSize = 13.sp)
        Text(entry.capturedAt, color = FrameMuted, fontSize = 11.sp)
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = entry.lesson,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(12.dp)),
            )
        }
        if (entry.lesson.isNotBlank()) {
            Text("Lesson", color = FrameGold, fontSize = 11.sp)
            Text(entry.lesson, color = FramePaper, fontSize = 14.sp)
        }
        if (entry.feedback.isNotBlank()) Text(entry.feedback, color = FrameMuted, fontSize = 13.sp)
    }
}
