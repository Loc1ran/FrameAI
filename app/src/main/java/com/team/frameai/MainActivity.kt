package com.team.frameai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.team.frameai.camera.CompositionGuide
import com.team.frameai.guide.GuideEngine
import com.team.frameai.lut.BitmapLutRepository
import com.team.frameai.lut.CubePhotoGrader
import com.team.frameai.ui.FrameAiApp
import com.team.frameai.vision.MlKitPrimaryFaceDetector

class MainActivity : ComponentActivity() {
    private val faceDetector = MlKitPrimaryFaceDetector()
    private val guide = GuideEngine()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val grader = CubePhotoGrader(BitmapLutRepository.fromAssets(assets))
        setContent {
            FrameAiApp(
                photoGrader = grader,
                faceDetector = faceDetector,
                compositionGuide = CompositionGuide { face, steady ->
                    guide.suggest(face, steady)
                },
            )
        }
    }

    override fun onDestroy() {
        faceDetector.close()
        super.onDestroy()
    }
}
