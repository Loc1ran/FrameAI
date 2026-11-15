package com.team.frameai.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.team.frameai.BuildConfig
import com.team.frameai.camera.ImageSaver
import com.team.frameai.camera.PhotoGrader
import com.team.frameai.lut.LutPack
import com.team.frameai.model.LiveGuide
import com.team.frameai.model.ShotReview
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private data class GradedPhoto(val lutId: String, val bitmap: Bitmap)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReviewScreen(
    originalPath: String,
    capturedAt: String,
    photoGrader: PhotoGrader,
    captureGuide: LiveGuide? = null,
    suggestedLut: String = "warm_film",
    onRetake: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val offlineReview = remember(originalPath, captureGuide) {
        reviewAtCapture(captureGuide, loadOfflineReview(context))
    }
    val client = remember(offlineReview) {
        ShotReviewClient(
            baseUrl = BuildConfig.SHOT_REVIEW_BASE_URL,
            demoToken = BuildConfig.DEMO_TOKEN,
            offlineReview = offlineReview,
        )
    }
    var original by remember(originalPath) { mutableStateOf<Bitmap?>(null) }
    var edited by remember(originalPath) { mutableStateOf<GradedPhoto?>(null) }
    var showEdited by remember(originalPath) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var cloudEnabled by remember { mutableStateOf(false) }
    var coaching by remember(originalPath) { mutableStateOf<ShotReview?>(null) }
    var savedEdit by remember(originalPath) { mutableStateOf<File?>(null) }
    var openFailed by remember(originalPath) { mutableStateOf(false) }
    var bestMatch by remember(originalPath) { mutableStateOf(suggestedLut.takeIf { it in LutPack.ids }) }
    var matchReason by remember(originalPath) { mutableStateOf<String?>(null) }

    BackHandler { if (!busy) onRetake() }
    LaunchedEffect(originalPath) {
        val decoded = try {
            withContext(Dispatchers.Default) {
                ImageSaver.decodeOriented(originalPath, maxEdge = 2048)
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            null
        }
        original = decoded
        openFailed = decoded == null
        if (openFailed) {
            status = "Could not open this photo. Return to the camera to try again."
            return@LaunchedEffect
        }
        if (!client.cloudConfigured || decoded == null) return@LaunchedEffect
        try {
            status = "Choosing a look…"
            val jpeg = withContext(Dispatchers.Default) { ImageSaver.jpegForReview(decoded) }
            val review = client.review(jpeg, cloudEnabled = true)
            if (review.source == "gemini" && review.suggestedLutId in LutPack.ids) {
                bestMatch = review.suggestedLutId
                matchReason = review.lutReason
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            // The full list stays available when the choice does not arrive.
        } finally {
            if (status == "Choosing a look…") status = null
        }
    }

    suspend fun materializeEdit(photo: GradedPhoto): File {
        savedEdit?.takeIf { it.isFile }?.let { return it }
        val file = withContext(Dispatchers.IO) {
            ImageSaver.createEdit(context, capturedAt, photo.lutId).also {
                ImageSaver.writeJpeg(photo.bitmap, it)
            }
        }
        savedEdit = file
        return file
    }

    val visibleEdit = edited.takeIf { showEdited }
    val shown = visibleEdit?.bitmap ?: original
    val outputLabel = if (visibleEdit == null) "original" else "edit"

    @Composable
    fun LookChoices(title: String, looks: List<LutPack.Look>) {
        if (looks.isEmpty()) return
        Text(title, color = FramePaper, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
        val ordered = looks.sortedWith(compareBy<LutPack.Look> { if (it.id == bestMatch) 0 else 1 }.thenBy { it.name })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ordered.forEach { look ->
                FilterChip(
                    selected = visibleEdit?.lutId == look.id,
                    enabled = original != null && !busy,
                    onClick = {
                        val id = look.id
                        val label = look.name
                        if (edited?.lutId == id) {
                            showEdited = true
                            status = null
                        } else {
                            val source = original ?: return@FilterChip
                            busy = true
                            edited = null
                            showEdited = false
                            savedEdit = null
                            status = "Applying $label…"
                            scope.launch {
                                try {
                                    val bitmap = withContext(Dispatchers.Default) {
                                        photoGrader.apply(source, id, LutPack.REVIEW_INTENSITY)
                                    }
                                    edited = GradedPhoto(id, bitmap)
                                    showEdited = true
                                    status = null
                                } catch (cancel: CancellationException) {
                                    throw cancel
                                } catch (_: Exception) {
                                    status = "Could not apply $label. Your original is unchanged. Try again."
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    },
                    label = { Text(look.name) },
                )
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(FrameInk)
            .safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Your photo", color = FramePaper, fontSize = 24.sp, modifier = Modifier.weight(1f))
            TextButton(enabled = !busy, onClick = onRetake) { Text("Retake") }
        }
        Text(
            visibleEdit?.let { "Edited · ${lutLabel(it.lutId)}" } ?: "Original",
            color = FramePaper,
        )
        if (shown != null) {
            Image(
                bitmap = shown.asImageBitmap(),
                contentDescription = visibleEdit?.let { "Edited photo, ${lutLabel(it.lutId)}" } ?: "Original photo",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).height(300.dp)
                    .clip(RoundedCornerShape(16.dp)).background(FrameSurface),
            )
        } else if (!openFailed) {
            Text("Opening photo…", color = FrameMuted, modifier = Modifier.padding(vertical = 32.dp))
        }

        Text("02   CHOOSE YOUR COLOR", color = FrameGold, fontSize = 11.sp)
        Text(
            "Apply one look at a time. Save keeps the photo on screen, and you can try another after that.",
            color = FrameMuted,
            fontSize = 12.sp,
        )
        bestMatch?.let { id ->
            val lead = if (matchReason != null) "Best match" else "From this frame"
            Text(
                "$lead: ${lutLabel(id)}" + (matchReason?.let { reason -> ". $reason" } ?: ""),
                color = FrameGold,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !showEdited,
                enabled = original != null && !busy,
                onClick = { showEdited = false; status = null },
                label = { Text("Original") },
            )
        }
        LookChoices("Film and cinema", LutPack.looks.filter { it.phoneJpeg })
        LookChoices("Camera conversions", LutPack.looks.filter { !it.phoneJpeg })
        if (busy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
        }
        status?.let { Text(it, color = FrameGold, modifier = Modifier.padding(vertical = 8.dp)) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                enabled = edited != null && !busy,
                onClick = { showEdited = !showEdited; status = null },
            ) { Text(if (showEdited) "Show original" else "Show edit") }
            Button(
                enabled = shown != null && !busy,
                onClick = {
                    val photo = visibleEdit
                    busy = true
                    scope.launch {
                        try {
                            val file = if (photo != null) materializeEdit(photo) else File(originalPath)
                            val uri = withContext(Dispatchers.IO) { ImageSaver.exportToGallery(context, file) }
                            status = if (uri != null) "Saved $outputLabel to Pictures/FrameAI."
                            else "Kept $outputLabel in FrameAI. Use Share to save it to another app."
                        } catch (cancel: CancellationException) {
                            throw cancel
                        } catch (_: Exception) {
                            status = "Could not save to Gallery. Your original is safe; try Share."
                        } finally {
                            busy = false
                        }
                    }
                },
            ) { Text("Save $outputLabel") }
            OutlinedButton(
                enabled = shown != null && !busy,
                onClick = {
                    val photo = visibleEdit
                    busy = true
                    scope.launch {
                        try {
                            val file = if (photo != null) materializeEdit(photo) else File(originalPath)
                            ImageSaver.share(context, file)
                            status = null
                        } catch (cancel: CancellationException) {
                            throw cancel
                        } catch (_: Exception) {
                            status = "Could not share this photo. Try again."
                        } finally {
                            busy = false
                        }
                    }
                },
            ) { Text("Share $outputLabel") }
        }
        Text(
            visibleEdit?.let { "Edit: ${it.bitmap.width} × ${it.bitmap.height} px. Full-size original kept." }
                ?: "Your full-size original is kept. Edited copies are up to 1024 px on the long edge.",
            color = FrameMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
        )

        Text("One tip for your next photo", color = FramePaper, fontSize = 18.sp)
        if (client.cloudConfigured) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Ask cloud coach", color = FramePaper)
                    Text(
                        "When enabled, Get photo tip sends this photo only when you tap it.",
                        color = FrameMuted, fontSize = 12.sp,
                    )
                }
                Switch(checked = cloudEnabled, enabled = !busy, onCheckedChange = { cloudEnabled = it })
            }
        } else {
            Text("On-device tips are available without internet.", color = FrameMuted, fontSize = 12.sp)
        }
        Button(
            enabled = original != null && !busy,
            onClick = {
                val source = original ?: return@Button
                val useCloud = cloudEnabled && client.cloudConfigured
                busy = true
                coaching = null
                status = if (useCloud) "Getting photo advice…" else null
                scope.launch {
                    try {
                        coaching = if (useCloud) {
                            val jpeg = withContext(Dispatchers.Default) { ImageSaver.jpegForReview(source) }
                            client.review(jpeg, cloudEnabled = true)
                        } else offlineReview
                        status = null
                    } catch (cancel: CancellationException) {
                        throw cancel
                    } catch (_: Exception) {
                        coaching = offlineReview
                        status = null
                    } finally {
                        busy = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) { Text("Get photo tip") }
        coaching?.let { CoachingCard(it) }
    }
}

@Composable
private fun CoachingCard(review: ShotReview) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            .background(androidx.compose.ui.graphics.Color(0xFF161616), RoundedCornerShape(12.dp))
            .padding(14.dp),
    ) {
        Text(sourceLabel(review.source), color = FrameGold, fontSize = 12.sp)
        Text(review.summary, color = FramePaper, modifier = Modifier.padding(top = 6.dp))
        Text(review.improvement, color = FramePaper, modifier = Modifier.padding(top = 8.dp))
        Text(
            "${review.principle} · ${lutLabel(review.suggestedLutId)}",
            color = FrameMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp),
        )
        Text(review.lutReason, color = FrameMuted, fontSize = 13.sp)
    }
}
