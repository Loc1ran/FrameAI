package com.team.frameai.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import com.team.frameai.guide.JournalEntry
import com.team.frameai.guide.Odyssey
import com.team.frameai.guide.OdysseyJournal
import com.team.frameai.lut.LutPack
import com.team.frameai.model.LiveGuide
import com.team.frameai.model.ShotReview
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val ORIGINAL_PAGE = "original"

private data class GradedPhoto(val lutId: String, val bitmap: Bitmap)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReviewScreen(
    originalPath: String,
    capturedAt: String,
    photoGrader: PhotoGrader,
    captureGuide: LiveGuide? = null,
    suggestedLut: String = "warm_film",
    voyage: Boolean = false,
    lesson: String = "",
    trial: String = "",
    skill: String = "",
    styleLabel: String = "Auto",
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
    var variants by remember(originalPath) { mutableStateOf<List<GradedPhoto>>(emptyList()) }
    var applying by remember(originalPath) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var cloudEnabled by remember { mutableStateOf(false) }
    var coaching by remember(originalPath) { mutableStateOf<ShotReview?>(null) }
    var savedEdits by remember(originalPath) { mutableStateOf<Map<String, File>>(emptyMap()) }
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
        if (openFailed || decoded == null) {
            status = "Could not open this photo. Return to the camera to try again."
            return@LaunchedEffect
        }
        var chosen = if (voyage) Odyssey.lookIds else reviewLookIds(emptyList(), suggestedLut.takeIf { it in LutPack.ids })
        if (client.cloudConfigured) {
            try {
                status = if (voyage) "Reading the voyage…" else "Choosing looks…"
                val jpeg = withContext(Dispatchers.Default) { ImageSaver.jpegForReview(decoded) }
                val review = client.review(jpeg, cloudEnabled = true)
                if (review.source == "gemini") {
                    val match = review.suggestedLutId.takeIf { it in if (voyage) Odyssey.lookIds.toSet() else LutPack.ids }
                    bestMatch = match
                    matchReason = if (voyage) match?.let(Odyssey::sentence) else review.lutReason.takeIf { match != null }
                    if (!voyage && review.candidateLutIds.isNotEmpty()) {
                        rankedIds = review.candidateLutIds
                        chosen = reviewLookIds(review.candidateLutIds, match)
                    }
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                // The bundled looks still get applied.
            }
        }
        applying = true
        val graded = mutableListOf<GradedPhoto>()
        var failed = 0
        chosen.forEachIndexed { index, id ->
            status = "Applying looks ${index + 1}/${chosen.size}…"
            try {
                val bitmap = withContext(Dispatchers.Default) {
                    photoGrader.apply(decoded, id, LutPack.REVIEW_INTENSITY)
                }
                graded += GradedPhoto(id, bitmap)
                variants = graded.toList()
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                failed++
            }
        }
        applying = false
        status = if (failed == chosen.size && chosen.isNotEmpty()) {
            "Could not apply the looks. Your original is unchanged."
        } else {
            null
        }
    }

    suspend fun materializeEdit(photo: GradedPhoto): File {
        savedEdits[photo.lutId]?.takeIf { it.isFile }?.let { return it }
        val file = withContext(Dispatchers.IO) {
            ImageSaver.createEdit(context, capturedAt, photo.lutId).also {
                ImageSaver.writeJpeg(photo.bitmap, it)
            }
        }
        savedEdits = savedEdits + (photo.lutId to file)
        return file
    }

    val pages = buildList {
        if (variants.isEmpty()) {
            original?.let { add(GradedPhoto(ORIGINAL_PAGE, it)) }
        } else {
            addAll(variants)
            original?.let { add(GradedPhoto(ORIGINAL_PAGE, it)) }
        }
    }
    val pagerState = rememberPagerState(pageCount = { pages.size.coerceAtLeast(1) })
    var landed by remember(originalPath) { mutableStateOf(false) }
    LaunchedEffect(voyage, bestMatch, variants.size) {
        if (!voyage || landed || bestMatch == null || variants.isEmpty()) return@LaunchedEffect
        val index = variants.indexOfFirst { it.lutId == bestMatch }
        if (index >= 0) {
            landed = true
            pagerState.scrollToPage(index)
        }
    }
    val current = pages.getOrNull(pagerState.currentPage)
    val onOriginal = current == null || current.lutId == ORIGINAL_PAGE
    val outputLabel = if (onOriginal) "original" else "edit"
    val currentName = when {
        current == null -> "Original"
        current.lutId == ORIGINAL_PAGE -> if (voyage) "Ithaca" else "Original"
        else -> if (voyage) Odyssey.nameOf(current.lutId) ?: lutLabel(current.lutId) else lutLabel(current.lutId)
    }
    val shore = if (voyage && current != null) VoyageTheme.ofLook(current.lutId) else null
    val voyageIds = if (voyage) Odyssey.lookIds + ORIGINAL_PAGE else emptyList()

    val photo: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(currentName, color = shore?.light ?: FramePaper, modifier = Modifier.weight(1f))
                if (pages.size > 1) {
                    Text(
                        "${pagerState.currentPage + 1} / ${pages.size}",
                        color = FrameMuted,
                        fontSize = 12.sp,
                    )
                }
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                beyondViewportPageCount = 1,
            ) { page ->
                val item = pages.getOrNull(page)
                Box(
                    Modifier.fillMaxSize()
                        .then(
                            if (shore != null) Modifier.border(
                                3.dp,
                                VoyageTheme.ofLook(item?.lutId ?: ORIGINAL_PAGE).line,
                                RoundedCornerShape(16.dp),
                            ) else Modifier,
                        )
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (shore != null) VoyageTheme.ofLook(item?.lutId ?: ORIGINAL_PAGE).wash else FrameSurface),
                    contentAlignment = Alignment.Center,
                ) {
                    if (item != null) {
                        val description = if (item.lutId == ORIGINAL_PAGE) {
                            if (voyage) "Ithaca, unchanged" else "Original photo"
                        } else {
                            "Edited photo, ${if (voyage) Odyssey.nameOf(item.lutId) ?: lutLabel(item.lutId) else lutLabel(item.lutId)}"
                        }
                        Image(
                            bitmap = item.bitmap.asImageBitmap(),
                            contentDescription = description,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().testTag("reviewPhoto"),
                        )
                    } else if (!openFailed) {
                        Text("Opening photo…", color = FrameMuted)
                    }
                }
            }
        }
    }
    val controls: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier.verticalScroll(rememberScrollState())) {
        Text(
            if (shore != null) currentName else "YOUR LOOKS",
            color = shore?.light ?: FrameGold,
            fontSize = if (shore != null) 18.sp else 11.sp,
            fontWeight = if (shore != null) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal,
        )
        if (lesson.isNotBlank()) {
            Text("What changed", color = FrameGold, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
            Text(lesson, color = FramePaper, fontSize = 14.sp)
        }
        Text(
            if (voyage) "Each shore is the same picture. Save keeps the one on screen."
            else "Swipe left or right. Save keeps the picture on screen.",
            color = FrameMuted,
            fontSize = 12.sp,
        )
        if (shore != null && current != null) {
            Text(
                if (current.lutId == ORIGINAL_PAGE) Odyssey.ITHACA else Odyssey.sentence(current.lutId),
                color = shore.light,
                fontSize = 16.sp,
                lineHeight = 20.sp,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .background(shore.wash, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
        } else bestMatch?.let { id ->
            val lead = if (matchReason != null) "Best match" else "From this frame"
            Text(
                "$lead: ${lutLabel(id)}" + (matchReason?.let { reason -> ". $reason" } ?: ""),
                color = FrameGold,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (applying || busy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
        }
        status?.let { Text(it, color = FrameGold, modifier = Modifier.padding(vertical = 8.dp)) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = current != null && !busy,
                colors = if (shore != null) ButtonDefaults.buttonColors(containerColor = shore.light, contentColor = FrameInk) else ButtonDefaults.buttonColors(),
                onClick = {
                    val photo = current
                    busy = true
                    scope.launch {
                        try {
                            val file = if (photo == null || photo.lutId == ORIGINAL_PAGE) File(originalPath)
                            else materializeEdit(photo)
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
            ) { Text(if (shore != null) "Save this shore" else "Save $outputLabel") }
            OutlinedButton(
                enabled = current != null && !busy,
                onClick = {
                    val photo = current
                    busy = true
                    scope.launch {
                        try {
                            val file = if (photo == null || photo.lutId == ORIGINAL_PAGE) File(originalPath)
                            else materializeEdit(photo)
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
            OutlinedButton(
                enabled = current != null && !busy,
                onClick = {
                    val photo = current ?: return@OutlinedButton
                    busy = true
                    scope.launch {
                        try {
                            val file = if (photo.lutId == ORIGINAL_PAGE) File(originalPath) else materializeEdit(photo)
                            val note = lesson.ifBlank { coaching?.principle ?: "Captured without a trial." }
                            val feedback = listOf(coaching?.summary, coaching?.improvement)
                                .filter { !it.isNullOrBlank() }
                                .joinToString(" ")
                            withContext(Dispatchers.IO) {
                                OdysseyJournal(File(context.filesDir, "odyssey/journal.json")).add(
                                    JournalEntry(
                                        id = "$capturedAt-${photo.lutId}",
                                        photoPath = file.absolutePath,
                                        capturedAt = capturedAt,
                                        style = styleLabel,
                                        trial = trial,
                                        skill = skill,
                                        lesson = note,
                                        feedback = feedback,
                                    ),
                                )
                            }
                            status = "Added to the Odyssey Journal."
                        } catch (cancel: CancellationException) {
                            throw cancel
                        } catch (_: Exception) {
                            status = "Could not add this photo to the journal."
                        } finally {
                            busy = false
                        }
                    }
                },
            ) { Text("Add to journal") }
        }
        Text(
            if (current == null || current.lutId == ORIGINAL_PAGE) {
                "Your full-size original is kept. Edited copies are up to 1024 px on the long edge."
            } else {
                "Edit: ${current.bitmap.width} × ${current.bitmap.height} px. Full-size original kept."
            },
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
    BoxWithConstraints(Modifier.fillMaxSize().background(shore?.wash ?: FrameInk).safeDrawingPadding().padding(16.dp)) {
        val landscape = maxWidth > maxHeight
        val panelWidth = (maxWidth * 0.44f).coerceIn(280.dp, 380.dp)
        val photoHeight = maxHeight * 0.43f
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (shore != null) currentName else "Your photo",
                        color = shore?.light ?: FramePaper,
                        fontSize = 24.sp,
                    )
                    if (shore != null && voyageIds.isNotEmpty()) {
                        VoyageBeads(
                            ids = voyageIds,
                            selected = pagerState.currentPage.coerceAtMost(voyageIds.lastIndex),
                            onSelect = { index ->
                                if (index < pages.size) scope.launch { pagerState.animateScrollToPage(index) }
                            },
                        )
                    }
                }
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
