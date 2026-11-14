package com.team.frameai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.team.frameai.camera.FallbackPhotoGrader
import com.team.frameai.ui.FrameAiApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FrameAiApp(
                photoGrader = FallbackPhotoGrader(),
                // B supplies a PrimaryFaceDetector and CompositionGuide; A wires them here.
                faceDetector = null,
                compositionGuide = null,
            )
        }
    }
}
