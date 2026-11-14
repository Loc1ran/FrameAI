package com.team.frameai.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.team.frameai.camera.CompositionGuide
import com.team.frameai.camera.FrameCameraController
import com.team.frameai.camera.ImageSaver
import com.team.frameai.camera.PhotoNames
import com.team.frameai.camera.PrimaryFaceDetector
import com.team.frameai.model.LiveGuide
import java.io.File

@Composable
fun CameraScreen(
    faceDetector: PrimaryFaceDetector?,
    compositionGuide: CompositionGuide?,
    onCaptured: (File, String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val appContext = context.applicationContext
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var asked by rememberSaveable { mutableStateOf(false) }
    var guide by remember { mutableStateOf(placeholderGuide()) }
    var coachEnabled by rememberSaveable { mutableStateOf(true) }
    var sampleIndex by rememberSaveable { mutableIntStateOf(0) }
    var capturing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        granted = allowed
        asked = true
    }

    val controller = remember { FrameCameraController(appContext) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(Unit) {
        onDispose { controller.shutdown() }
    }

    DisposableEffect(lifecycleOwner, granted, faceDetector, compositionGuide) {
        controller.detector = faceDetector
        controller.compositionGuide = compositionGuide
        controller.onGuide = { guide = it }
        if (granted) {
            controller.start(lifecycleOwner)
            if (faceDetector != null) controller.enableAnalysis() else controller.disableAnalysis()
        }
        onDispose { controller.stop() }
    }

    LaunchedEffect(Unit) {
        if (!granted && !asked) {
            asked = true
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(faceDetector) {
        if (faceDetector == null) {
            guide = loadGuide(context, SampleGuideAssets[sampleIndex])
        }
    }

    Box(Modifier.fillMaxSize().background(FrameInk)) {
        if (granted) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { viewContext ->
                    PreviewView(viewContext).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        this.controller = controller.lifecycleController
                    }
                },
                update = { view -> controller.previewView = view },
            )
            GuideOverlay(guide = guide, coachEnabled = coachEnabled)
        } else {
            PermissionGate(
                onAllow = { launcher.launch(Manifest.permission.CAMERA) },
                onSettings = {
                    val intent = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    )
                    context.startActivity(intent)
                },
            )
        }

        if (granted) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = { coachEnabled = !coachEnabled }) {
                    Text(if (coachEnabled) "Coach on" else "Coach off", color = FramePaper)
                }
                if (faceDetector == null) {
                    TextButton(onClick = {
                        sampleIndex = (sampleIndex + 1) % SampleGuideAssets.size
                        guide = loadGuide(context, SampleGuideAssets[sampleIndex])
                    }) {
                        Text("Sample", color = FrameGold)
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            error?.let {
                Text(
                    text = it,
                    color = FramePaper,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .padding(bottom = 12.dp)
                        .background(FrameInk.copy(alpha = 0.72f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            if (granted) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .border(3.dp, FramePaper, CircleShape)
                        .padding(6.dp)
                        .background(if (capturing) FrameGold else FramePaper, CircleShape)
                        .semantics { contentDescription = "Shutter" }
                        .clickable(enabled = !capturing) {
                            capturing = true
                            error = null
                            val stamp = PhotoNames.timestamp()
                            val file = ImageSaver.createOriginal(context, stamp)
                            controller.takePicture(
                                file = file,
                                onSaved = {
                                    capturing = false
                                    onCaptured(file, stamp)
                                },
                                onError = { message ->
                                    capturing = false
                                    file.delete()
                                    error = message
                                },
                            )
                        },
                )
            }
        }
    }
}

@Composable
private fun PermissionGate(onAllow: () -> Unit, onSettings: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        Text("Camera access", color = FramePaper, fontSize = 28.sp)
        Text(
            "FrameAI needs the camera to show a live preview and take a photo on this phone. Denying access does not close the app.",
            color = FrameMuted,
            modifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
        )
        Button(onClick = onAllow) { Text("Allow camera") }
        TextButton(onClick = onSettings) { Text("Open settings", color = FrameGold) }
    }
}

private fun placeholderGuide() = LiveGuide(
    kind = "ALIGN_SUBJECT",
    message = "Align the face with the target circle",
    targetX = 0.67,
    targetY = 0.33,
    subjectX = 0.48,
    subjectY = 0.39,
    isReady = false,
    source = "demo_mock",
)
