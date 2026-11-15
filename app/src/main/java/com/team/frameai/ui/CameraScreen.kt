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
import com.team.frameai.guide.AiDrawing
import com.team.frameai.guide.CompositionMode
import com.team.frameai.guide.DirectorSession
import com.team.frameai.guide.FrameRatio
import com.team.frameai.guide.GeometryCoach
import com.team.frameai.guide.GuideEngine
import com.team.frameai.guide.LightingAdviceEngine
import com.team.frameai.guide.LightingRead
import com.team.frameai.guide.FrameEvidence
import com.team.frameai.guide.Odyssey
import com.team.frameai.guide.OdysseyJourney
import com.team.frameai.guide.OdysseyTrial
import com.team.frameai.guide.PhotographyStyle
import com.team.frameai.guide.SceneComposition
import com.team.frameai.guide.LightingMap
import com.team.frameai.guide.LutAdvice
import com.team.frameai.guide.SceneGeometry
import com.team.frameai.guide.TrialSession
import com.team.frameai.BuildConfig
import com.team.frameai.camera.*
import com.team.frameai.model.AdviceSource
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
    director: DirectorSession = DirectorSession(),
    voyage: Boolean = false,
    onVoyage: (Boolean) -> Unit = {},
    episodeIndex: Int = 0,
    onEpisode: (Int) -> Unit = {},
    onOpenJournal: () -> Unit = {},
    onLesson: (String, String, String, String) -> Unit = { _, _, _, _ -> },
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
    var lightingMap by remember { mutableStateOf<LightingMap?>(null) }
    var lightingRead by remember { mutableStateOf<LightingRead?>(null) }
    var showLightingMap by rememberSaveable { mutableStateOf(false) }
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
    var aiStatus by remember { mutableStateOf("Waiting for a scene suggestion…") }
    var ratioName by rememberSaveable { mutableStateOf(FrameRatio.FOUR_THREE.name) }
    val frameRatio = FrameRatio.entries.firstOrNull { it.name == ratioName } ?: FrameRatio.FULL
    var styleName by rememberSaveable { mutableStateOf(PhotographyStyle.AUTO.wire) }
    val photoStyle = PhotographyStyle.fromWire(styleName)
    var lookName by remember { mutableStateOf<String?>(null) }
    val coachGate = remember { CoachGate() }
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
                lightingMap = null
                trackingError = null
                granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                resumed = false
                aiReply = null
                director.bump()
                coachGate.lastKey = ""
                modeGeneration++
                area = null
                live = null
                geometry = null
                lightingMap = null
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(Unit) { onDispose { controller.shutdown() } }
    DisposableEffect(lifecycleOwner, granted, faceDetector, compositionGuide) {
        controller.detector = faceDetector
        controller.compositionGuide = compositionGuide
        controller.onGuide = { next, box, lines, light, failure, manual ->
            if (resumed) {
                live = next
                area = box
                geometry = lines
                lightingMap = light
                lightingRead = controller.latestLighting
                picked = manual
                areaAt = SystemClock.elapsedRealtime()
                trackingError = failure
            }
        }
        controller.onFrameJpeg = { jpeg ->
            val requestMode = mode
            val requestGeneration = modeGeneration
            val requestStyle = PhotographyStyle.fromWire(styleName).wire
            val requestKey = "$requestMode|$requestStyle|${coachGate.bucket}|${coachGate.aligned}"
            val fresh = aiReply != null && SystemClock.elapsedRealtime() - aiReplyAt < 8_000
            if (requestKey == coachGate.lastKey && fresh) {
                controller.finishFrameSend()
            } else scope.launch {
                try {
                    val reply = client.coach(
                        jpeg,
                        scene = requestMode == CompositionMode.SCENE,
                        instruction = "",
                        style = requestStyle,
                    )
                    if (cloudOn && resumed && mode == requestMode && modeGeneration == requestGeneration) {
                        aiReply = reply
                        aiFailed = reply == null
                        aiReplyAt = SystemClock.elapsedRealtime()
                        aiStatus = if (reply == null) {
                            "AI unavailable. The on-device guide stays in place."
                        } else {
                            ""
                        }
                        if (reply != null) {
                            coachGate.lastKey = requestKey
                            if (requestMode == CompositionMode.SUBJECT) {
                                director.offer(reply, SystemClock.elapsedRealtime())
                            }
                            val look = LutAdvice.accept(reply.suggestedLutId, reply.sceneType)
                            if (look != null) {
                                controller.suggestedLutId = look
                                controller.sceneLutAllowed = false
                                lookName = if (voyage) Odyssey.nameOf(look) ?: LutAdvice.displayName(look) else LutAdvice.displayName(look)
                            }
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
    LaunchedEffect(mode) { controller.setActionMode(mode == CompositionMode.ACTION) }
    val now = SystemClock.elapsedRealtime()
    val goal = director.current(now)
    val sceneAdvice = SceneComposition.suggest(aiReply, cloudOn, client.enabled, aiFailed)
    val episode = Odyssey.episodeAt(episodeIndex)
    val reply = aiReply?.takeIf { it.source == AdviceSource.GEMINI.wire }
    val aiAction = goal?.action ?: reply?.actionType
    val aiPreset = goal?.preset ?: reply?.compositionPreset ?: "none"
    val aiReady = aiAction == "hold" && reply?.isReady == true
    val drawn = if (mode != CompositionMode.ACTION && aiAction != null) {
        AiDrawing.guide(
            aiAction,
            aiPreset,
            area?.anchorX,
            area?.anchorY,
            aiReady,
            geometry?.rollDegrees,
            goal?.instruction ?: reply?.message.orEmpty(),
        )
    } else {
        null
    }
    val plainHint = when (mode) {
        CompositionMode.SCENE ->
            if (aiReply?.source == AdviceSource.GEMINI.wire) {
                FramingHint(sceneAdvice.title, sceneAdvice.detail, if (sceneAdvice.ready) "✓" else "◎", sceneAdvice.ready)
            } else {
                FramingHint("Athena is looking", "The scene guide waits for her. You can take the photo at any time.", "◎")
            }
        CompositionMode.ACTION -> FramingHint(
            "Capture the movement",
            if (cloudOn && actionClient.enabled) "Use bright light. A fast shutter captures eight frames; AI checks subject sharpness."
            else "Use bright light. Eight fast-shutter shots, ranked by overall detail.",
            "⚡",
        )
        CompositionMode.SUBJECT -> when {
            trackingError != null -> FramingHint("Tracking unavailable", trackingError.orEmpty(), "!")
            goal != null -> FramingHint(
                goal.instruction,
                goal.reason,
                AiDrawing.symbol(goal.action, aiReady),
                aiReady,
            )
            reply != null -> FramingHint(
                reply.message,
                reply.actionReason.ifBlank { "The arrow matches this direction." },
                AiDrawing.symbol(reply.actionType, aiReady),
                aiReady,
            )
            !cloudOn || !client.enabled -> FramingHint(
                "Athena is off",
                "Turn AI on for a direction. Nothing is drawn until she answers, and you can still take the photo.",
                "◎",
            )
            aiFailed -> FramingHint(
                "Athena is unavailable",
                "No guide is drawn until she answers. You can still take the photo.",
                "◎",
            )
            else -> FramingHint(
                "Athena is looking",
                "The grid stays up. The arrow appears when she chooses a direction.",
                "◎",
            )
        }
    }
    val hint = plainHint
    val trials = remember { TrialSession() }
    var athenaOffer by remember { mutableStateOf<OdysseyTrial?>(null) }
    var athenaActive by remember { mutableStateOf<OdysseyTrial?>(null) }
    var athenaLesson by remember { mutableStateOf<String?>(null) }
    val evidence = FrameEvidence(
        subjectX = area?.anchorX,
        subjectY = area?.anchorY,
        subjectHeight = area?.height,
        ready = hint.aligned,
        edgeProblem = live?.message == GuideEngine.EDGE_MESSAGE ||
            live?.message == GuideEngine.BREATHING_CUE ||
            live?.message == GeometryCoach.SEPARATE_SHAPES,
        lightingCue = live?.message?.let(LightingAdviceEngine::isCue) == true,
        issue = director.current(SystemClock.elapsedRealtime())?.issue ?: "none",
        action = director.current(SystemClock.elapsedRealtime())?.action ?: "hold",
        subjectLuma = lightingRead?.subjectMean,
        backgroundLuma = lightingRead?.backgroundMean,
        clippedShare = lightingRead?.clippedShare,
        coachStamp = aiReplyAt,
    )
    LaunchedEffect(evidence, guided, mode) {
        if (!guided || mode == CompositionMode.ACTION) {
            athenaOffer = null
            return@LaunchedEffect
        }
        trials.observe(evidence)
        athenaActive = trials.active
        athenaLesson = trials.lesson
        athenaOffer = trials.suggestion(evidence)
    }
    val ink = if (voyage) VoyageTheme.of(episode) else null
    coachGate.aligned = hint.aligned
    coachGate.bucket = area?.let { "${(it.anchorX * 8f).toInt()}:${(it.anchorY * 8f).toInt()}" } ?: "none"
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(hint.aligned, cloudOn) {
        if (hint.aligned && cloudOn) {
            coachGate.lastKey = ""
            controller.requestAnalysisSoon()
        }
    }
    LaunchedEffect(hint.aligned, guided, mode) {
        settled = false
        if (hint.aligned && guided) { delay(600); settled = true }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(ink?.wash ?: FrameInk).safeDrawingPadding()) {
        val deckHeight = maxHeight * 0.36f
        LaunchedEffect(frameRatio) {
            area = null
            live = null
            geometry = null
            lightingMap = null
            tapMark = null
        }
        val preview: @Composable (Modifier) -> Unit = { modifier ->
            FrameViewport(frameRatio, false, modifier, rim = ink?.line) {
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
                if (mode == CompositionMode.SUBJECT && showLightingMap) LightingOverlay(lightingMap, guided)
                when (mode) {
                    CompositionMode.ACTION -> Unit
                    else -> GuideOverlay(drawn, area, true, geometry, course = ink)
                }
                if (mode == CompositionMode.SUBJECT) {
                    Box(
                        Modifier.fillMaxSize().pointerInput(mode) {
                            detectTapGestures { offset ->
                                if (size.width <= 0f || size.height <= 0f) return@detectTapGestures
                                tapMark = offset
                                controller.selectSubjectAt(offset.x / size.width, offset.y / size.height)
                                director.bump()
                                coachGate.lastKey = ""
                                modeGeneration++
                                aiReply = null
                                aiFailed = false
                                controller.requestAnalysisSoon()
                            }
                        },
                    )
                }
                tapMark?.let { mark ->
                    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                        drawCircle(ink?.line ?: FrameGold, radius = 18.dp.toPx(), center = mark, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                    }
                }
                if (ink != null) {
                    CameraLabel(orientationDegrees, Modifier.align(Alignment.TopCenter).padding(top = 12.dp)) {
                        VoyageCourse(episode, hint.aligned)
                    }
                } else CameraLabel(orientationDegrees, Modifier.align(Alignment.TopStart).padding(12.dp)) {
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
                cloudOn = cloudOn, voyage = voyage, ink = ink, lookName = lookName,
                aiFailed = aiFailed, aiStatus = aiStatus, error = error,
                capturing = capturing, settled = settled,
                athena = {
                    if (mode != CompositionMode.ACTION) {
                        AthenaCard(
                            stage = OdysseyJourney.stage(athenaActive != null, athenaLesson != null),
                            title = hint.title,
                            detail = hint.detail,
                            symbol = hint.symbol,
                            offer = athenaOffer,
                            active = athenaActive,
                            lesson = athenaLesson,
                            onAccept = { trial ->
                                trials.accept(trial, evidence)
                                athenaActive = trial
                                athenaOffer = null
                                athenaLesson = null
                            },
                            onDecline = { trial ->
                                trials.decline(trial)
                                athenaOffer = null
                            },
                        )
                    }
                },
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
                                        val finished = trials.consume()
                                        onLesson(finished.first, finished.second, finished.third, photoStyle.shortLabel)
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
                            onSaved = {
                                capturing = false
                                if (voyage && settled && mode != CompositionMode.ACTION) onEpisode(Odyssey.nextIndex(episodeIndex))
                                val finished = trials.consume()
                                onLesson(finished.first, finished.second, finished.third, photoStyle.shortLabel)
                                athenaLesson = null
                                athenaActive = trials.active
                                athenaOffer = null
                                onCaptured(file, stamp, controller.suggestedLutId, captureGuide)
                            },
                            onError = { capturing = false; file.delete(); error = it },
                        )
                    }
                },
                modifier = modifier.testTag("cameraControls"),
            )
        }
        Column(Modifier.fillMaxSize()) {
            CameraToolbar(
                mode, frameRatio, guided, cloudOn, client.enabled, orientationDegrees, voyage,
                onMode = { choice ->
                    if (capturing) return@CameraToolbar
                    if (choice != CompositionMode.SUBJECT) controller.clearSubjectSelection()
                    mode = choice
                    area = null
                    picked = choice == CompositionMode.SUBJECT && picked
                    modeGeneration++
                    aiFailed = false
                    aiReply = null
                    director.bump()
                    coachGate.lastKey = ""
                },
                onRatio = { if (!capturing) ratioName = it.name },
                onVoyage = {
                    val enable = !voyage
                    onVoyage(enable)
                    if (enable) {
                        guided = true
                        onEpisode(0)
                    }
                },
                onCloud = {
                    if (capturing) return@CameraToolbar
                    val enable = !cloudOn
                    cloudOn = enable
                    aiReply = null
                    aiFailed = false
                    modeGeneration++
                    director.bump()
                    coachGate.lastKey = ""
                    lookName = null
                    controller.sceneLutAllowed = true
                    if (enable) controller.requestAnalysisSoon()
                },
                showLightingMap = showLightingMap,
                onLightingMap = { showLightingMap = !showLightingMap },
                onOpenJournal = onOpenJournal,
            )
            if (granted) {
                StyleRow(photoStyle, orientationDegrees) { choice ->
                    if (capturing || choice.wire == styleName) return@StyleRow
                    styleName = choice.wire
                    director.bump()
                    coachGate.lastKey = ""
                    aiReply = null
                    aiFailed = false
                    modeGeneration++
                    controller.requestAnalysisSoon()
                }
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
    voyage: Boolean,
    onMode: (CompositionMode) -> Unit,
    onRatio: (FrameRatio) -> Unit,
    onVoyage: () -> Unit,
    onCloud: () -> Unit,
    showLightingMap: Boolean,
    onLightingMap: () -> Unit,
    onOpenJournal: () -> Unit,
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
                DropdownMenuItem(text = { Text("Odyssey Journal") }, onClick = { onOpenJournal(); menu = null })
                DropdownMenuItem(text = { Column {
                    Text(if (voyage) "Leave the voyage" else "The Voyage")
                    Text("Three frames: threshold, crossing, return", fontSize = 12.sp)
                } }, onClick = { onVoyage(); menu = null })
                DropdownMenuItem(text = { Text(if (showLightingMap) "Hide lighting map" else "Show lighting map") },
                    onClick = { onLightingMap(); menu = null })
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
    rim: androidx.compose.ui.graphics.Color? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val aspect = ratio.previewAspect(landscape)
        val width = if (aspect == null) maxWidth else minOf(maxWidth, maxHeight * aspect)
        val height = if (aspect == null) maxHeight else width / aspect
        Box(
            Modifier.size(width, height)
                .then(if (rim != null) Modifier.border(2.dp, rim, RoundedCornerShape(16.dp)) else Modifier)
                .clip(RoundedCornerShape(16.dp))
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
    voyage: Boolean,
    ink: VoyageInk?,
    lookName: String?,
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
    athena: @Composable () -> Unit = {},
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
            athena()
            if (mode == CompositionMode.ACTION) GuidanceCard(hint, null)
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
                else "ATHENA",
                color = ink?.light ?: FrameGold,
                fontSize = 9.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (mode == CompositionMode.SUBJECT) {
                coachingLookLine(lookName)?.let { line ->
                    Text(line, color = FrameMuted, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 1)
                }
            }
            if (mode == CompositionMode.ACTION) {
                Text("After capture, AI checks timing, sharpness, clipping, and composition.",
                    color = FrameMuted, fontSize = 11.sp, maxLines = 2)
            } else if (aiFailed) Text(aiStatus, color = FrameMuted, fontSize = 11.sp, maxLines = 2)
        }
        error?.let { Text(it, color = FrameGold, fontSize = 12.sp) }
        }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(modifier = Modifier.weight(1f), enabled = zoomOutEnabled, onClick = onZoomOut) {
                CameraLabel(orientationDegrees, Modifier.size(56.dp)) { Text("Zoom −") }
            }
            Box(
                Modifier.size(70.dp).testTag("shutter")
                    .border(
                        if (ink != null && settled) 3.dp else 2.dp,
                        when {
                            ink != null && settled -> ink.light
                            ink != null -> ink.line
                            settled -> FrameReady
                            else -> FramePaper
                        },
                        CircleShape,
                    )
                    .padding(6.dp).clip(CircleShape)
                    .background(when {
                        capturing -> FrameMuted
                        ink != null && settled -> ink.light
                        else -> FramePaper
                    })
                    .semantics { contentDescription = "Take photo" }
                    .clickable(enabled = !capturing, role = Role.Button) { onCapture() },
            )
            TextButton(modifier = Modifier.weight(1f), enabled = zoomInEnabled, onClick = onZoomIn) {
                CameraLabel(orientationDegrees, Modifier.size(56.dp)) { Text("Zoom +") }
            }
        }

    }
}

@Composable
private fun StyleRow(
    style: PhotographyStyle,
    orientationDegrees: Int,
    onStyle: (PhotographyStyle) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 8.dp).testTag("styleSelector"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PhotographyStyle.entries.forEach { choice ->
            val selected = choice == style
            TextButton(
                onClick = { onStyle(choice) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            ) {
                CameraLabel(orientationDegrees, Modifier.fillMaxSize()) {
                    Text(
                        choice.shortLabel,
                        fontSize = 11.sp,
                        maxLines = 1,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) FrameGold else FrameMuted,
                    )
                }
            }
        }
    }
}

private class CoachGate {
    var lastKey: String = ""
    var aligned: Boolean = false
    var bucket: String = "none"
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
private fun GuidanceCard(hint: FramingHint, ink: VoyageInk? = null) {
    val readyColor = ink?.light ?: FrameReady
    val aim = ink?.line ?: FrameGold
    Row(
        Modifier.fillMaxWidth().background(ink?.wash ?: FrameSurface, RoundedCornerShape(12.dp)).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (ink != null) {
            Box(
                Modifier.padding(end = 8.dp).width(3.dp).height(36.dp)
                    .background(if (hint.aligned) readyColor else aim, RoundedCornerShape(2.dp)),
            )
        }
        Text(hint.symbol,
            color = if (hint.aligned) readyColor else aim, fontSize = 24.sp,
            modifier = Modifier.width(32.dp))
        Column(Modifier.weight(1f)) {
            Text(
                hint.title,
                color = if (ink != null) ink.light else FramePaper,
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 18.sp,
            )
            Text(
                hint.detail,
                color = if (ink != null) FramePaper.copy(alpha = 0.82f) else FrameMuted,
                fontSize = 12.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
