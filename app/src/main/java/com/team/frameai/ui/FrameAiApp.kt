package com.team.frameai.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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

    FrameAiTheme {
        if (route == CameraRoute || photoPath.isEmpty()) {
            CameraScreen(
                faceDetector = faceDetector,
                compositionGuide = compositionGuide,
                onCaptured = { file, stamp ->
                    photoPath = file.absolutePath
                    photoStamp = stamp
                    route = "review"
                },
            )
        } else {
            ReviewScreen(
                originalPath = photoPath,
                capturedAt = photoStamp,
                photoGrader = photoGrader,
                onRetake = {
                    route = CameraRoute
                    photoPath = ""
                },
            )
        }
    }
}
