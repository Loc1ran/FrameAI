package com.team.frameai.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.platform.testTag
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
    var rankedIds by remember(originalPath) { mutableStateOf<List<String>>(emptyList()) }

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
            status = "Choosing 10 looks…"
            val jpeg = withContext(Dispatchers.Default) { ImageSaver.jpegForReview(decoded) }
            val review = client.review(jpeg, cloudEnabled = true)
            if (review.source == "gemini") {
                bestMatch = review.suggestedLutId.takeIf { it in LutPack.ids }
                matchReason = review.lutReason.takeIf { bestMatch != null }
                if (review.candidateLutIds.isNotEmpty()) rankedIds = review.candidateLutIds
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            // The starting row stays available when the ranking does not arrive.
        } finally {
            if (status == "Choosing 10 looks…") status = null
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

    val shownLooks = reviewLookIds(rankedIds, bestMatch, edited?.lutId).mapNotNull { id ->
        LutPack.looks.firstOrNull { it.id == id }
    }

    val photo: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(visibleEdit?.let { "Edited · ${lutLabel(it.lutId)}" } ?: "Original", color = FramePaper)
            Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(FrameSurface), contentAlignment = Alignment.Center) {
                if (shown != null) {
                    Image(
                        bitmap = shown.asImageBitmap(),
                        contentDescription = visibleEdit?.let { "Edited photo, ${lutLabel(it.lutId)}" } ?: "Original photo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().testTag("reviewPhoto"),
                    )
                } else if (!openFailed) {
                    Text("Opening photo…", color = FrameMuted)
                }
            }
        }
    }
    val controls: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier.verticalScroll(rememberScrollState())) {
        Text("CHOOSE YOUR LOOK", color = FrameGold, fontSize = 11.sp)
        Text(
            if (rankedIds.isEmpty()) {
                "Swipe to explore 10 looks. Tap a look to preview it."
            } else {
                "10 looks chosen for this photo. Tap one to preview it."
            },
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
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = !showEdited,
                enabled = original != null && !busy,
                onClick = { showEdited = false; status = null },
                label = { Text("Original") },
            )
            shownLooks.forEach { look ->
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
    BoxWithConstraints(Modifier.fillMaxSize().background(FrameInk).safeDrawingPadding().padding(16.dp)) {
        val landscape = maxWidth > maxHeight
        val panelWidth = (maxWidth * 0.44f).coerceIn(280.dp, 380.dp)
        val photoHeight = maxHeight * 0.43f
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Your photo", color = FramePaper, fontSize = 24.sp, modifier = Modifier.weight(1f))
                TextButton(enabled = !busy, onClick = onRetake) { Text("Retake") }
            }
            if (landscape) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    photo(Modifier.weight(1f).fillMaxHeight())
                    controls(Modifier.width(panelWidth).fillMaxHeight())
                }
            } else {
                photo(Modifier.fillMaxWidth().height(photoHeight))
                controls(Modifier.weight(1f).fillMaxWidth())
            }
        }
    }

}

internal fun reviewLookIds(aiIds: List<String>, suggestedId: String?, keepId: String? = null): List<String> {
    val phone = LutPack.looks.filter { it.phoneJpeg }.map { it.id }
    val phoneSet = phone.toSet()
    val ranked = aiIds.filter { it in phoneSet }.distinct().take(10)
    val base = (if (ranked.isNotEmpty()) {
        val lead = suggestedId?.takeIf { it in phoneSet }
        if (lead != null) listOf(lead) + ranked.filter { it != lead } else ranked
    } else {
        listOfNotNull(suggestedId?.takeIf { it in phoneSet }) + phone
    }).distinct().take(10).toMutableList()
    if (keepId != null && keepId in phoneSet && keepId !in base) {
        if (base.size == 10) base.removeAt(base.lastIndex)
        base.add(0, keepId)
    }
    return base
}

@Composable
private fun CritiqueLine(label: String, body: String) {
    Text(label, color = FrameGold, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
    Text(body, color = FramePaper, fontSize = 14.sp)
}

@Composable
private fun CoachingCard(review: ShotReview) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            .background(androidx.compose.ui.graphics.Color(0xFF161616), RoundedCornerShape(12.dp))
            .padding(14.dp),
    ) {
        Text(sourceLabel(review.source), color = FrameGold, fontSize = 12.sp)
        CritiqueLine("What worked", review.summary)
        CritiqueLine("What could improve", review.improvement)
        CritiqueLine("Try next", review.principle)
        Text(
            "${lutLabel(review.suggestedLutId)} · ${review.lutReason}",
            color = FrameMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp),
        )
    }
}
