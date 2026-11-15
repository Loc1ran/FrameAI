package com.team.frameai.ui

import android.app.Activity
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.team.frameai.model.LiveGuide
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.team.frameai.camera.CompositionGuide
import com.team.frameai.camera.PhotoGrader
import com.team.frameai.camera.PrimaryFaceDetector
import com.team.frameai.guide.DirectorSession

private const val CameraRoute = "camera"

@Composable
fun FrameAiApp(
    photoGrader: PhotoGrader,
    faceDetector: PrimaryFaceDetector? = null,
    compositionGuide: CompositionGuide? = null,
    director: DirectorSession = DirectorSession(),
) {
    var route by rememberSaveable { mutableStateOf(CameraRoute) }
    var photoPath by rememberSaveable { mutableStateOf("") }
    var photoStamp by rememberSaveable { mutableStateOf("") }
    var suggestedLut by rememberSaveable { mutableStateOf("warm_film") }
    var captureGuide by remember { mutableStateOf<LiveGuide?>(null) }
    val showingCamera = route == CameraRoute || photoPath.isEmpty()
    val context = LocalContext.current
    val activity = remember(context) {
        generateSequence(context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
    }
    DisposableEffect(activity, showingCamera) {
        activity?.requestedOrientation = if (showingCamera) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else ActivityInfo.SCREEN_ORIENTATION_SENSOR
        onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
    }

    FrameAiTheme {
        if (showingCamera) {
            CameraScreen(
                faceDetector = faceDetector,
                compositionGuide = compositionGuide,
                director = director,
                orientationDegrees = rememberCameraOrientation(),
                onCaptured = { file, stamp, lutId, guide ->
                    photoPath = file.absolutePath
                    photoStamp = stamp
                    suggestedLut = lutId
                    captureGuide = guide
                    route = "review"
                },
            )
        } else {
            ReviewScreen(
                originalPath = photoPath,
                capturedAt = photoStamp,
                photoGrader = photoGrader,
                suggestedLut = suggestedLut,
                captureGuide = captureGuide,
                onRetake = {
                    route = CameraRoute
                    photoPath = ""
                    captureGuide = null
                },
            )
        }
    }
}
