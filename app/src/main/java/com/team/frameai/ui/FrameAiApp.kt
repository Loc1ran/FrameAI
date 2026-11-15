package com.team.frameai.ui

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

private const val CameraRoute = "camera"

@Composable
fun FrameAiApp(
    photoGrader: PhotoGrader,
    faceDetector: PrimaryFaceDetector? = null,
    compositionGuide: CompositionGuide? = null,
) {
    var route by rememberSaveable { mutableStateOf(CameraRoute) }
    var photoPath by rememberSaveable { mutableStateOf("") }
    var photoStamp by rememberSaveable { mutableStateOf("") }
    var suggestedLut by rememberSaveable { mutableStateOf("warm_film") }
    var captureGuide by remember { mutableStateOf<LiveGuide?>(null) }

    FrameAiTheme {
        if (route == CameraRoute || photoPath.isEmpty()) {
            CameraScreen(
                faceDetector = faceDetector,
                compositionGuide = compositionGuide,
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
