package com.team.frameai.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.team.frameai.guide.CompositionMode
import com.team.frameai.guide.FrameRatio
import com.team.frameai.guide.SceneComposition
import com.team.frameai.guide.SceneGeometry
import com.team.frameai.BuildConfig
import com.team.frameai.camera.*
import com.team.frameai.model.FaceBoxNormalized
import com.team.frameai.model.LiveCoach
import com.team.frameai.model.LiveGuide
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun CameraScreen(
    faceDetector: PrimaryFaceDetector?,
    compositionGuide: CompositionGuide?,
    onCaptured: (File, String, String, LiveGuide?) -> Unit,
    orientationDegrees: Int = 0,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var asked by rememberSaveable { mutableStateOf(false) }
    var mode by rememberSaveable { mutableStateOf(CompositionMode.SUBJECT) }
    var modeGeneration by remember { mutableIntStateOf(0) }
    var guided by rememberSaveable { mutableStateOf(true) }
    var area by remember { mutableStateOf<FaceBoxNormalized?>(null) }
    var live by remember { mutableStateOf<LiveGuide?>(null) }
    var geometry by remember { mutableStateOf<SceneGeometry?>(null) }
    var picked by remember { mutableStateOf(false) }
    var tapMark by remember { mutableStateOf<Offset?>(null) }
    var trackingError by remember { mutableStateOf<String?>(null) }
    var areaAt by remember { mutableLongStateOf(0L) }
    var capturing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var cloudOn by rememberSaveable { mutableStateOf(false) }
    var aiFailed by remember { mutableStateOf(false) }
    var aiReplyAt by remember { mutableLongStateOf(0L) }
    var aiReply by remember { mutableStateOf<LiveCoach?>(null) }
    var aiForInstruction by remember { mutableStateOf<String?>(null) }
    var aiStatus by remember { mutableStateOf("Waiting for a scene suggestion…") }
    var ratioName by rememberSaveable { mutableStateOf(FrameRatio.FOUR_THREE.name) }
    val frameRatio = FrameRatio.entries.firstOrNull { it.name == ratioName } ?: FrameRatio.FULL
    var resumed by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val controller = remember { FrameCameraController(context.applicationContext) }
    val client = remember { LiveCoachClient(BuildConfig.SHOT_REVIEW_BASE_URL, BuildConfig.DEMO_TOKEN) }
    val actionClient = remember { ActionBestShotClient(BuildConfig.SHOT_REVIEW_BASE_URL, BuildConfig.DEMO_TOKEN) }
    controller.captureFrames = granted && cloudOn && resumed && client.enabled && mode != CompositionMode.ACTION
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it; asked = true }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                resumed = true
                area = null
                live = null
                geometry = null
                trackingError = null
                granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                resumed = false
                aiReply = null
                modeGeneration++
                area = null
                live = null
                geometry = null
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(Unit) { onDispose { controller.shutdown() } }
    DisposableEffect(lifecycleOwner, granted, faceDetector, compositionGuide) {
        controller.detector = faceDetector
        controller.compositionGuide = compositionGuide
        controller.onGuide = { next, box, lines, failure, manual ->
            if (resumed) {
                live = next
                area = box
                geometry = lines
                picked = manual
                areaAt = SystemClock.elapsedRealtime()
                trackingError = failure
            }
        }
        controller.onFrameJpeg = { jpeg ->
            val requestMode = mode
            val requestGeneration = modeGeneration
            val requestInstruction = if (requestMode == CompositionMode.SUBJECT) framingHint(live, area).title else ""
            scope.launch {
                try {
                    val reply = client.coach(
                        jpeg,
                        scene = requestMode == CompositionMode.SCENE,
                        instruction = requestInstruction,
                    )
                    if (cloudOn && resumed && mode == requestMode && modeGeneration == requestGeneration) {
                        aiReply = reply
                        aiForInstruction = requestInstruction
                        aiFailed = reply == null
                        aiReplyAt = SystemClock.elapsedRealtime()
                        aiStatus = if (reply == null) "AI unavailable. The guide above is the move to follow." else ""
                        if (reply?.suggestedLutId in com.team.frameai.lut.LutPack.ids) {
                            controller.suggestedLutId = reply!!.suggestedLutId
                        }
                    }
                } finally { controller.finishFrameSend() }
            }
        }
        if (granted) {
            controller.start(lifecycleOwner)
            controller.enableAnalysis()
        }
        onDispose { controller.stop() }
    }
    LaunchedEffect(Unit) {
        if (!granted && !asked) { asked = true; launcher.launch(Manifest.permission.CAMERA) }
    }
    LaunchedEffect(areaAt) {
        delay(1_500)
        area = null
    }
    LaunchedEffect(aiReplyAt) {
        if (aiReply != null) { delay(8_000); aiReply = null; aiStatus = "Waiting for a fresh suggestion…" }
    }
    LaunchedEffect(tapMark) {
        if (tapMark != null) {
            delay(700)
            tapMark = null
        }
    }
    LaunchedEffect(frameRatio) { controller.setFrameRatio(frameRatio) }
    LaunchedEffect(mode) { controller.setActionMode(mode == CompositionMode.ACTION) }
    val sceneAdvice = SceneComposition.suggest(aiReply, cloudOn, client.enabled, aiFailed)
    val hint = when (mode) {
        CompositionMode.SCENE ->
            FramingHint(sceneAdvice.title, sceneAdvice.detail, if (sceneAdvice.ready) "✓" else "◎", sceneAdvice.ready)
        CompositionMode.ACTION -> FramingHint(
            "Capture the movement",
            if (cloudOn && actionClient.enabled) "Use bright light. A fast shutter captures eight frames; AI checks subject sharpness."
            else "Use bright light. Eight fast-shutter shots, ranked by overall detail. AI can check the moving subject.",
            "⚡",
        )
        CompositionMode.SUBJECT ->
            trackingError?.let { FramingHint("Tracking unavailable", it, "!") } ?: framingHint(live, area)
    }
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(hint.aligned, guided, mode) {
        settled = false
        if (hint.aligned && guided) { delay(600); settled = true }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(FrameInk).safeDrawingPadding()) {
        val deckHeight = maxHeight * 0.42f
        LaunchedEffect(frameRatio) {
            area = null
            live = null
            geometry = null
            tapMark = null
        }
        val preview: @Composable (Modifier) -> Unit = { modifier ->
            FrameViewport(frameRatio, false, modifier) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { viewContext ->
                        PreviewView(viewContext).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            // SurfaceView is required for the tested phone's camera preview.
                            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                            this.controller = controller.lifecycleController
                        }
                    },
                    update = { controller.previewView = it },
                )
                when (mode) {
                    CompositionMode.SCENE -> SceneOverlay(null, guided, settled)
                    CompositionMode.SUBJECT -> GuideOverlay(live, area, guided, geometry)
                    CompositionMode.ACTION -> Unit
                }
                if (mode == CompositionMode.SUBJECT) {
                    Box(
                        Modifier.fillMaxSize().pointerInput(mode) {
                            detectTapGestures { offset ->
                                if (size.width <= 0f || size.height <= 0f) return@detectTapGestures
                                tapMark = offset
                                controller.selectSubjectAt(offset.x / size.width, offset.y / size.height)
                            }
                        },
                    )
                }
                tapMark?.let { mark ->
                    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                        drawCircle(FrameGold, radius = 18.dp.toPx(), center = mark, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                    }
                }
                CameraLabel(orientationDegrees, Modifier.align(Alignment.TopStart).padding(12.dp)) {
                Text(
                    when {
                        !guided -> "FREE FRAME"
                        mode == CompositionMode.SCENE -> "SCENE · ACTION + SURROUNDINGS"
                        mode == CompositionMode.ACTION -> "ACTION · EIGHT-FRAME BURST"
                        trackingError != null -> "TRACKING UNAVAILABLE"
                        picked -> "YOUR SUBJECT"
                        area != null -> "SUBJECT TRACKED"
                        else -> "LOOKING FOR A SUBJECT"
                    },
                    color = if (mode != CompositionMode.SUBJECT) FramePaper else if (area != null) FrameReady else FramePaper, fontSize = 10.sp,
                    modifier = Modifier
                        .background(FrameInk.copy(alpha = 0.85f), RoundedCornerShape(8.dp)).padding(8.dp),
                )
                }
            }
        }
        val deck: @Composable (Modifier) -> Unit = { modifier ->
            CaptureDeck(
                mode = mode, guided = guided, picked = picked, hint = hint,
                orientationDegrees = orientationDegrees,
                cloudOn = cloudOn, aiReply = aiReply, aiForInstruction = aiForInstruction,
                aiFailed = aiFailed, aiStatus = aiStatus, error = error,
                capturing = capturing, settled = settled,
                zoomOutEnabled = !capturing && controller.canApplyZoom(0.8f),
                zoomInEnabled = !capturing && controller.canApplyZoom(1.25f),
                onZoomOut = { controller.applyZoom(0.8f) },
                onZoomIn = { controller.applyZoom(1.25f) },
                onCapture = {
                    capturing = true
                    error = null
                    val stamp = PhotoNames.timestamp()
                    if (mode == CompositionMode.ACTION) {
                        val files = List(ACTION_BURST_SIZE) { index ->
                            ImageSaver.createOriginal(context, "${stamp}_burst_${index + 1}")
                        }
                        controller.takeBurst(
                            files = files,
                            onSaved = { saved ->
                                scope.launch {
                                    try {
                                        val selected = if (cloudOn) {
                                            val pick = actionClient.choose(saved)
                                            if (pick == null || pick.source != "gemini") {
                                                error = "AI could not check this burst. Retry, or turn AI off for local selection. Originals are retained in app storage."
                                                return@launch
                                            }
                                            if (!pick.usable) {
                                                error = "No clear action shot: ${pick.reason} Try brighter light and keep the subject in focus. Originals are retained in app storage."
                                                return@launch
                                            }
                                            pick.index
                                        } else actionClient.localBest(saved)
                                        val chosen = saved[selected]
                                        onCaptured(chosen, stamp, controller.suggestedLutId, null)
                                        saved.filterIndexed { index, _ -> index != selected }
                                            .forEach(File::delete)
                                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                        throw cancelled
                                    } catch (_: Exception) {
                                        error = "Could not select a frame. Originals are retained in app storage; please retry."
                                    } finally {
                                        capturing = false
                                    }
                                }
                            },
                            onError = { message ->
                                capturing = false
                                error = message
                            },
                        )
                    } else {
                        val file = ImageSaver.createOriginal(context, stamp)
                        val fresh = mode == CompositionMode.SUBJECT && guided &&
                            area != null && SystemClock.elapsedRealtime() - areaAt < 1_500
                        val captureGuide = if (fresh) live else null
                        controller.takePicture(file,
                            onSaved = { capturing = false; onCaptured(file, stamp, controller.suggestedLutId, captureGuide) },
                            onError = { capturing = false; file.delete(); error = it },
                        )
                    }
                },
                modifier = modifier.testTag("cameraControls"),
            )
        }
        Column(Modifier.fillMaxSize()) {
            CameraToolbar(
                mode, frameRatio, guided, cloudOn, client.enabled, orientationDegrees,
                onMode = { choice ->
                    if (capturing) return@CameraToolbar
                    if (choice != CompositionMode.SUBJECT) controller.clearSubjectSelection()
                    mode = choice
                    area = null
                    picked = choice == CompositionMode.SUBJECT && picked
                    modeGeneration++
                    aiFailed = false
                    aiReply = null
                },
                onRatio = { if (!capturing) ratioName = it.name },
                onGuide = { guided = !guided },
                onCloud = { if (!capturing) { cloudOn = !cloudOn; aiReply = null; aiFailed = false; modeGeneration++ } },
            )
            if (granted) {
                preview(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp))
                deck(Modifier.fillMaxWidth().height(deckHeight)
                    .padding(horizontal = 20.dp, vertical = 8.dp))
            } else {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(28.dp),
                    verticalArrangement = Arrangement.Center) {
                    Text("Start with your camera", color = FramePaper, fontSize = 28.sp)
                    Text("Frame your photo, follow a cue, then choose a color look.",
                        color = FrameMuted, modifier = Modifier.padding(vertical = 16.dp))
                    Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
                    TextButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                    }) { Text("Open settings") }
                }
            }
        }
    }
}

@Composable
private fun CameraToolbar(
    mode: CompositionMode,
    ratio: FrameRatio,
    guided: Boolean,
    cloudOn: Boolean,
    cloudAvailable: Boolean,
    orientationDegrees: Int,
    onMode: (CompositionMode) -> Unit,
    onRatio: (FrameRatio) -> Unit,
    onGuide: () -> Unit,
    onCloud: () -> Unit,
) {
    var menu by remember { mutableStateOf<String?>(null) }
    Row(Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 12.dp).testTag("cameraToolbar"),
        verticalAlignment = Alignment.CenterVertically) {
        CameraLabel(orientationDegrees, Modifier.weight(1f).fillMaxHeight()) {
            Text("FRAMEAI", color = FramePaper, fontSize = 15.sp,
                fontWeight = FontWeight.Bold, maxLines = 1)
        }
        Box {
            TextButton(onClick = { menu = "mode" }, modifier = Modifier.width(80.dp).fillMaxHeight()) {
                CameraLabel(orientationDegrees, Modifier.fillMaxSize()) {
                    Text(modeLabel(mode), fontSize = 13.sp)
                }
            }
            DropdownMenu(expanded = menu == "mode", onDismissRequest = { menu = null }) {
                CameraLabel(orientationDegrees, Modifier.width(if (orientationDegrees % 180 == 0) 240.dp else 208.dp)) { Column(Modifier.width(240.dp)) {
                CompositionMode.entries.forEach { choice ->
                    DropdownMenuItem(text = { Column {
                        Text(modeLabel(choice))
                        Text(modeDescription(choice), fontSize = 12.sp)
                    } }, onClick = { onMode(choice); menu = null })
                }
                } }
            }
        }
        Box {
            TextButton(onClick = { menu = "ratio" }, modifier = Modifier.width(64.dp).fillMaxHeight().semantics { contentDescription = "Photo shape" }) {
                CameraLabel(orientationDegrees, Modifier.fillMaxSize()) { Text(ratio.label) }
            }
            DropdownMenu(expanded = menu == "ratio", onDismissRequest = { menu = null }) {
                CameraLabel(orientationDegrees, Modifier.width(if (orientationDegrees % 180 == 0) 240.dp else 208.dp)) { Column(Modifier.width(240.dp)) {
                FrameRatio.entries.forEach { choice ->
                    DropdownMenuItem(
                        text = { Text(if (choice == FrameRatio.FULL) "Fill available space" else choice.label) },
                        onClick = { onRatio(choice); menu = null },
                    )
                }
                } }
            }
        }
        Box {
            TextButton(onClick = { menu = "options" }, modifier = Modifier.width(56.dp).fillMaxHeight().semantics { contentDescription = "Camera options" }) {
                CameraLabel(orientationDegrees, Modifier.fillMaxSize()) { Text("•••", fontSize = 18.sp) }
            }
            DropdownMenu(expanded = menu == "options", onDismissRequest = { menu = null }) {
                CameraLabel(orientationDegrees, Modifier.width(if (orientationDegrees % 180 == 0) 240.dp else 208.dp)) { Column(Modifier.width(240.dp)) {
                DropdownMenuItem(text = { Text(if (guided) "Turn guide off" else "Turn guide on") },
                    onClick = { onGuide(); menu = null })
                if (cloudAvailable) DropdownMenuItem(text = { Column {
                    Text(if (cloudOn) "Turn AI off" else "Turn AI on")
                    Text("Shares preview images while on", fontSize = 12.sp)
                } }, onClick = { onCloud(); menu = null })
                } }
            }
        }
    }
}

@Composable
internal fun FrameViewport(
    ratio: FrameRatio,
    landscape: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val aspect = ratio.previewAspect(landscape)
        val width = if (aspect == null) maxWidth else minOf(maxWidth, maxHeight * aspect)
        val height = if (aspect == null) maxHeight else width / aspect
        Box(
            Modifier.size(width, height).clip(RoundedCornerShape(16.dp))
                .background(FrameInk).testTag("cameraPreview"),
            content = content,
        )
    }
}

@Composable
private fun CaptureDeck(
    mode: CompositionMode,
    guided: Boolean,
    picked: Boolean,
    hint: FramingHint,
    orientationDegrees: Int,
    cloudOn: Boolean,
    aiReply: LiveCoach?,
    aiForInstruction: String?,
    aiFailed: Boolean,
    aiStatus: String,
    error: String?,
    capturing: Boolean,
    settled: Boolean,
    zoomOutEnabled: Boolean,
    zoomInEnabled: Boolean,
    onZoomOut: () -> Unit,
    onZoomIn: () -> Unit,
    onCapture: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CameraLabel(orientationDegrees, Modifier.weight(1f).fillMaxWidth()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        if (guided) {
            if (mode == CompositionMode.SUBJECT) {
                Text(
                    if (picked) "Following your tap. Tap empty space to choose automatically."
                    else "Tap a subject to choose what to follow.",
                    color = FrameMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            GuidanceCard(hint)
            if (mode == CompositionMode.ACTION && capturing) {
                Text("Metering light, capturing eight frames, then checking the burst…",
                    color = FrameGold, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
        } else {
            Text("Frame it your way", color = FramePaper, fontSize = 20.sp)
            Text("The guide is off. You can take a photo at any time.", color = FrameMuted, fontSize = 12.sp)
        }
        if (cloudOn) {
            Text(
                if (mode == CompositionMode.ACTION) "AI BEST SHOT · compares the burst"
                else "AI SUGGESTION · same move as the guide",
                color = FrameGold,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
            if (mode == CompositionMode.SUBJECT) {
                Text(
                    matchingSuggestion(hint.title, aiReply?.message, aiForInstruction),
                    color = FrameMuted,
                    fontSize = 12.sp,
                    maxLines = 3,
                )
            }
            if (mode == CompositionMode.ACTION) {
                Text("After capture, AI checks timing, sharpness, clipping, and composition.",
                    color = FrameMuted, fontSize = 12.sp)
            } else if (aiFailed) Text(aiStatus, color = FrameMuted, fontSize = 12.sp)
        }
        error?.let { Text(it, color = FrameGold, fontSize = 12.sp) }
        }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(modifier = Modifier.weight(1f), enabled = zoomOutEnabled, onClick = onZoomOut) {
                CameraLabel(orientationDegrees, Modifier.size(56.dp)) { Text("Zoom −") }
            }
            Box(
                Modifier.size(70.dp).testTag("shutter").border(2.dp, if (settled) FrameReady else FramePaper, CircleShape)
                    .padding(6.dp).clip(CircleShape).background(if (capturing) FrameMuted else FramePaper)
                    .semantics { contentDescription = "Take photo" }
                    .clickable(enabled = !capturing, role = Role.Button) { onCapture() },
            )
            TextButton(modifier = Modifier.weight(1f), enabled = zoomInEnabled, onClick = onZoomIn) {
                CameraLabel(orientationDegrees, Modifier.size(56.dp)) { Text("Zoom +") }
            }
        }

    }
}

private fun modeLabel(mode: CompositionMode): String = when (mode) {
    CompositionMode.SUBJECT -> "Subject"
    CompositionMode.SCENE -> "Scene"
    CompositionMode.ACTION -> "Action"
}

private fun modeDescription(mode: CompositionMode): String = when (mode) {
    CompositionMode.SUBJECT -> "Follow a person or object"
    CompositionMode.SCENE -> "Consider the surroundings"
    CompositionMode.ACTION -> "Burst and keep the best moment"
}

private const val ACTION_BURST_SIZE = 8

@Composable
private fun GuidanceCard(hint: FramingHint) {
    Row(Modifier.fillMaxWidth().background(FrameSurface, RoundedCornerShape(16.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(hint.symbol,
            color = if (hint.aligned) FrameReady else FrameGold, fontSize = 32.sp,
            modifier = Modifier.width(44.dp))
        Column(Modifier.weight(1f)) {
            Text(
                hint.title,
                color = FramePaper, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
            )
            Text(hint.detail, color = FrameMuted, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
