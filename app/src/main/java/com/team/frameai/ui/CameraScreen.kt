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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
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
    var resumed by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val controller = remember { FrameCameraController(context.applicationContext) }
    val client = remember { LiveCoachClient(BuildConfig.SHOT_REVIEW_BASE_URL, BuildConfig.DEMO_TOKEN) }
    controller.captureFrames = granted && cloudOn && resumed && client.enabled
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
        controller.onGuide = { next, box, lines, failure ->
            if (resumed) {
                live = next
                area = box
                geometry = lines
                areaAt = SystemClock.elapsedRealtime()
                trackingError = failure
            }
        }
        controller.onFrameJpeg = { jpeg ->
            val requestMode = mode
            val requestGeneration = modeGeneration
            val requestInstruction = if (requestMode == CompositionMode.SCENE) "" else framingHint(live, area).title
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
    val sceneAdvice = SceneComposition.suggest(aiReply, cloudOn, client.enabled, aiFailed)
    val hint = if (mode == CompositionMode.SCENE) {
        FramingHint(sceneAdvice.title, sceneAdvice.detail, if (sceneAdvice.ready) "✓" else "◎", sceneAdvice.ready)
    } else {
        trackingError?.let { FramingHint("Tracking unavailable", it, "!") } ?: framingHint(live, area)
    }
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(hint.aligned, guided, mode) {
        settled = false
        if (hint.aligned && guided) { delay(600); settled = true }
    }

    Column(Modifier.fillMaxSize().background(FrameInk).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("FRAMEAI", color = FramePaper, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text("FRAME  /  CAPTURE  /  COLOR", color = FrameMuted, fontSize = 9.sp, letterSpacing = 1.sp)
            }
            TextButton(onClick = { guided = !guided }) { Text(if (guided) "Guide on" else "Guide off") }
            if (client.enabled) TextButton(onClick = { cloudOn = !cloudOn; aiReply = null; aiFailed = false; modeGeneration++ }) {
                Text(if (cloudOn) "AI on" else "AI off")
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CompositionMode.entries.forEach { choice ->
                FilterChip(selected = mode == choice, onClick = {
                    mode = choice
                    area = null
                    modeGeneration++
                    aiFailed = false
                    aiReply = null
                }, label = { Text(if (choice == CompositionMode.SUBJECT) "Subject" else "Scene") })
            }
        }
        if (granted) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(20.dp)).background(FrameInk)) {
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
                if (mode == CompositionMode.SCENE) SceneOverlay(null, guided, settled)
                else GuideOverlay(live, area, guided, geometry)
                Text(
                    when {
                        !guided -> "FREE FRAME"
                        mode == CompositionMode.SCENE -> "SCENE · ACTION + SURROUNDINGS"
                        trackingError != null -> "TRACKING UNAVAILABLE"
                        area != null -> "SUBJECT TRACKED"
                        else -> "LOOKING FOR A SUBJECT"
                    },
                    color = if (mode == CompositionMode.SCENE) FramePaper else if (area != null) FrameReady else FramePaper, fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
                        .background(FrameInk.copy(alpha = 0.85f), RoundedCornerShape(8.dp)).padding(8.dp),
                )
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
                if (guided) {
                    Text(if (mode == CompositionMode.SCENE) "01   SUBJECT + SURROUNDINGS" else "01   FRAME YOUR SUBJECT", color = FrameMuted, fontSize = 10.sp, letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 8.dp))
                    GuidanceCard(hint)
                } else {
                    Text("Frame it your way", color = FramePaper, fontSize = 20.sp)
                    Text("The guide is off. You can take a photo at any time.", color = FrameMuted, fontSize = 12.sp)
                }
                if (cloudOn) {
                    Text("AI SUGGESTION · same move as the guide", color = FrameGold, fontSize = 10.sp, modifier = Modifier.padding(top = 6.dp))
                    if (mode == CompositionMode.SUBJECT) {
                        Text(
                            matchingSuggestion(hint.title, aiReply?.message, aiForInstruction),
                            color = FrameMuted,
                            fontSize = 12.sp,
                            maxLines = 3,
                        )
                    }
                    if (aiFailed) Text(aiStatus, color = FrameMuted, fontSize = 12.sp)
                }
                error?.let { Text(it, color = FrameGold, fontSize = 12.sp) }
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        modifier = Modifier.weight(1f),
                        enabled = !capturing && controller.canApplyZoom(0.8f),
                        onClick = { controller.applyZoom(0.8f) },
                    ) { Text("Zoom −") }
                    Box(
                        Modifier.size(70.dp).border(2.dp, if (settled) FrameReady else FramePaper, CircleShape)
                            .padding(6.dp).clip(CircleShape).background(if (capturing) FrameMuted else FramePaper)
                            .semantics { contentDescription = "Take photo" }
                            .clickable(enabled = !capturing, role = Role.Button) {
                                capturing = true
                                error = null
                                val stamp = PhotoNames.timestamp()
                                val file = ImageSaver.createOriginal(context, stamp)
                                val fresh = mode == CompositionMode.SUBJECT && guided &&
                                    area != null && SystemClock.elapsedRealtime() - areaAt < 1_500
                                val captureGuide = if (fresh) live else null
                                controller.takePicture(file,
                                    onSaved = { capturing = false; onCaptured(file, stamp, controller.suggestedLutId, captureGuide) },
                                    onError = { capturing = false; file.delete(); error = it },
                                )
                            },
                    )
                    TextButton(
                        modifier = Modifier.weight(1f),
                        enabled = !capturing && controller.canApplyZoom(1.25f),
                        onClick = { controller.applyZoom(1.25f) },
                    ) { Text("Zoom +") }
                }
                Text("Take a photo whenever you like", color = FrameMuted, fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp))
            }
        } else {
            Column(Modifier.weight(1f).padding(28.dp), verticalArrangement = Arrangement.Center) {
                Text("Start with your camera", color = FramePaper, fontSize = 28.sp)
                Text("See your subject, follow one framing cue, then choose a color look.", color = FrameMuted, modifier = Modifier.padding(vertical = 16.dp))
                Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
                TextButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                }) { Text("Open settings") }
            }
        }
    }
}

@Composable
private fun GuidanceCard(hint: FramingHint) {
    Row(Modifier.fillMaxWidth().background(FrameSurface, RoundedCornerShape(16.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(hint.symbol,
            color = if (hint.aligned) FrameReady else FrameGold, fontSize = 32.sp,
            modifier = Modifier.width(44.dp))
        Column(Modifier.weight(1f)) {
            Text(
                hint.title,
                color = FramePaper, fontSize = 19.sp, fontWeight = FontWeight.SemiBold,
            )
            Text(hint.detail, color = FrameMuted, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
