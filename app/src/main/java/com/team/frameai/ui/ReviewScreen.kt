package com.team.frameai.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.team.frameai.BuildConfig
import com.team.frameai.camera.ImageSaver
import com.team.frameai.camera.PhotoGrader
import com.team.frameai.model.ShotReview
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Looks = listOf("warm_film" to "Warm Film", "cool_cinema" to "Cool Cinema")

@Composable
fun ReviewScreen(
    originalPath: String,
    capturedAt: String,
    photoGrader: PhotoGrader,
    onRetake: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val client = remember {
        ShotReviewClient(
            baseUrl = BuildConfig.SHOT_REVIEW_BASE_URL,
            demoToken = BuildConfig.DEMO_TOKEN,
            offlineReview = loadOfflineReview(context),
        )
    }
    var original by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var graded by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var selectedLut by remember { mutableStateOf("warm_film") }
    var showEdited by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var cloudEnabled by remember { mutableStateOf(false) }
    var coaching by remember { mutableStateOf<ShotReview?>(null) }
    var savedFile by remember { mutableStateOf<java.io.File?>(null) }

    LaunchedEffect(originalPath) {
        original = withContext(Dispatchers.Default) {
            ImageSaver.decodeOriented(originalPath, maxEdge = 2048)
        }
        if (original == null) status = "Could not open the original photo"
    }

    val shown = if (showEdited && graded != null) graded else original

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FrameInk)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onRetake) { Text("Back", color = FrameGold) }
            Text("Review", color = FramePaper, fontSize = 20.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = onRetake) { Text("Retake", color = FramePaper) }
        }
        Text(
            if (showEdited && graded != null) "After · ${lutLabel(selectedLut)}" else "Before · original",
            color = FrameMuted,
            fontSize = 13.sp,
        )
        if (shown != null) {
            Image(
                bitmap = shown.asImageBitmap(),
                contentDescription = if (showEdited) "Graded photo" else "Original photo",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
            )
        } else {
            Text("Opening photo…", color = FrameMuted, modifier = Modifier.padding(vertical = 48.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Looks.forEach { (id, label) ->
                FilterChip(
                    selected = selectedLut == id,
                    onClick = {
                        selectedLut = id
                        graded = null
                        showEdited = false
                        savedFile = null
                    },
                    label = { Text(label) },
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                enabled = original != null && !busy,
                onClick = {
                    val source = original ?: return@Button
                    busy = true
                    status = "Grading…"
                    scope.launch {
                        try {
                            val result = withContext(Dispatchers.Default) {
                                photoGrader.apply(source, selectedLut, 0.85f)
                            }
                            graded = result
                            showEdited = true
                            savedFile = null
                            status = null
                        } catch (cancel: CancellationException) {
                            throw cancel
                        } catch (_: Exception) {
                            status = "Could not grade this photo"
                        } finally {
                            busy = false
                        }
                    }
                },
            ) { Text(if (busy) "Working" else "Apply") }
            OutlinedButton(
                enabled = graded != null,
                onClick = { showEdited = !showEdited },
            ) { Text(if (showEdited) "Before" else "After") }
            OutlinedButton(
                enabled = graded != null && !busy,
                onClick = {
                    val bitmap = graded ?: return@OutlinedButton
                    busy = true
                    scope.launch {
                        try {
                            val file = withContext(Dispatchers.IO) {
                                val edit = ImageSaver.createEdit(context, capturedAt, selectedLut)
                                ImageSaver.writeJpeg(bitmap, edit)
                                ImageSaver.exportToGallery(context, edit)
                                edit
                            }
                            savedFile = file
                            status = "Saved. Original kept."
                        } catch (cancel: CancellationException) {
                            throw cancel
                        } catch (_: Exception) {
                            status = "Could not save the edit"
                        } finally {
                            busy = false
                        }
                    }
                },
            ) { Text("Save") }
        }
        TextButton(
            enabled = (savedFile ?: java.io.File(originalPath)).exists(),
            onClick = {
                val file = savedFile ?: java.io.File(originalPath)
                ImageSaver.share(context, file)
            },
        ) { Text("Share", color = FramePaper) }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Cloud coaching", color = FramePaper)
                Text(
                    if (client.cloudConfigured) "Off by default. On sends one photo when you ask."
                    else "No server configured. Why this shot? stays an offline tip.",
                    color = FrameMuted,
                    fontSize = 12.sp,
                )
            }
            Switch(checked = cloudEnabled, onCheckedChange = { cloudEnabled = it })
        }
        Button(
            enabled = original != null && !busy,
            onClick = {
                val source = original ?: return@Button
                busy = true
                coaching = null
                status = if (cloudEnabled && client.cloudConfigured) "Asking…" else "Offline tip"
                scope.launch {
                    try {
                        val review = withContext(Dispatchers.Default) {
                            val jpeg = ImageSaver.jpegForReview(source)
                            client.review(jpeg, cloudEnabled)
                        }
                        coaching = review
                        status = null
                    } catch (cancel: CancellationException) {
                        throw cancel
                    } catch (_: Exception) {
                        coaching = client.review(ByteArray(0), cloudEnabled = false)
                        status = null
                    } finally {
                        busy = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) { Text("Why this shot?") }

        coaching?.let { review -> CoachingCard(review) }
        status?.let { Text(it, color = FrameGold, modifier = Modifier.padding(top = 8.dp)) }
        Text(
            "Warm Film and Cool Cinema are on-device cube grades. The original JPEG is not overwritten.",
            color = FrameMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
        )
    }
}

@Composable
private fun CoachingCard(review: ShotReview) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .background(androidx.compose.ui.graphics.Color(0xFF161616), RoundedCornerShape(12.dp))
            .padding(14.dp),
    ) {
        Text(sourceLabel(review.source), color = FrameGold, fontSize = 12.sp)
        Text(review.summary, color = FramePaper, modifier = Modifier.padding(top = 6.dp))
        Text(review.improvement, color = FramePaper, modifier = Modifier.padding(top = 8.dp))
        Text(
            "${review.principle} · ${lutLabel(review.suggestedLutId)}",
            color = FrameMuted,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(review.lutReason, color = FrameMuted, fontSize = 13.sp)
    }
}
