package com.team.frameai

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.team.frameai.camera.CompositionGuide
import com.team.frameai.guide.DirectorSession
import com.team.frameai.guide.GuideSmoother
import com.team.frameai.lut.BitmapLutRepository
import com.team.frameai.lut.CubePhotoGrader
import com.team.frameai.ui.FrameAiApp
import com.team.frameai.vision.MlKitPrimaryFaceDetector

class MainActivity : ComponentActivity() {
    private val faceDetector = MlKitPrimaryFaceDetector()
    private val director = DirectorSession()
    private val guide = GuideSmoother(director = director)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val grader = CubePhotoGrader(BitmapLutRepository.fromAssets(assets))
        setContent {
            FrameAiApp(
                photoGrader = grader,
                faceDetector = faceDetector,
                director = director,
                compositionGuide = CompositionGuide { face, steady, scene ->
                    guide.update(face, SystemClock.elapsedRealtime(), steady, scene)
                },
            )
        }
    }

    override fun onDestroy() {
        faceDetector.close()
        super.onDestroy()
    }
}
