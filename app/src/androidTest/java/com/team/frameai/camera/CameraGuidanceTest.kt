package com.team.frameai.camera

import android.Manifest
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.rule.GrantPermissionRule
import com.team.frameai.guide.GuideEngine
import com.team.frameai.model.RawFaceBox
import com.team.frameai.ui.CameraScreen
import com.team.frameai.ui.FrameAiTheme
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

class CameraGuidanceTest {
    @get:Rule val permission = GrantPermissionRule.grant(Manifest.permission.CAMERA)
    @get:Rule val compose = createComposeRule()

    @Test fun analyzerDeliversCenteredGuideWithoutAPlacementChoice() {
        val hasFace = AtomicBoolean(true)
        val engine = GuideEngine()
        compose.setContent {
            FrameAiTheme {
                CameraScreen(
                    faceDetector = PrimaryFaceDetector {
                        if (hasFace.get()) RawFaceBox(0.5f, 0.5f, 0.4f, 0.4f, 0.6f, 0.6f) else null
                    },
                    compositionGuide = CompositionGuide { face, steady, scene -> engine.suggest(face, steady, scene) },
                    onCaptured = { _, _, _, _ -> },
                )
            }
        }
        compose.onNodeWithText("CHOOSE SUBJECT PLACEMENT", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Center guide", substring = true).assertDoesNotExist()
        compose.waitUntil(20_000) {
            compose.onAllNodesWithText("SUBJECT TRACKED", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
