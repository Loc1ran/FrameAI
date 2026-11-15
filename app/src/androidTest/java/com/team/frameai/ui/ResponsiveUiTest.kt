package com.team.frameai.ui

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.team.frameai.MainActivity
import com.team.frameai.camera.PhotoGrader
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ResponsiveUiTest {
    @get:Rule val permission = GrantPermissionRule.grant(Manifest.permission.CAMERA)
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun rotate(landscape: Boolean) {
        val originalActivity = compose.activity
        compose.runOnUiThread {
            compose.activity.requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        compose.waitUntil(10_000) {
            val orientation = compose.activity.resources.configuration.orientation
            orientation == if (landscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
        }
        compose.waitForIdle()
        assertSame("Rotating must keep an in-progress capture or edit alive", originalActivity, compose.activity)
    }

    private fun checkPreview(aspect: Float) {
        compose.waitUntil(10_000) {
            val box = compose.onNodeWithTag("cameraPreview").fetchSemanticsNode().boundsInRoot
            kotlin.math.abs(box.width / box.height - aspect) < 0.02f
        }
        compose.onNodeWithContentDescription("Take photo").assertIsDisplayed().assertIsEnabled()
        val shutter = compose.onNodeWithTag("shutter").fetchSemanticsNode().boundsInRoot
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue(shutter.left >= root.left && shutter.right <= root.right)
        assertTrue(shutter.top >= root.top && shutter.bottom <= root.bottom)
    }

    @Test fun onlyLabelsRotateWhilePreviewAndShutterStayFixed() {
        val degrees = mutableIntStateOf(0)
        rotate(false)
        compose.runOnUiThread {
            compose.activity.setContent {
                FrameAiTheme {
                    CameraScreen(null, null, onCaptured = { _, _, _, _ -> }, orientationDegrees = degrees.intValue)
                }
            }
        }
        checkPreview(3f / 4f)
        val preview = compose.onNodeWithTag("cameraPreview").fetchSemanticsNode().boundsInRoot
        val shutter = compose.onNodeWithTag("shutter").fetchSemanticsNode().boundsInRoot
        for (angle in listOf(0, 90, 180, 270)) {
            compose.runOnIdle { degrees.intValue = angle }
            compose.waitForIdle()
            checkPreview(3f / 4f)
            assertEquals(preview, compose.onNodeWithTag("cameraPreview").fetchSemanticsNode().boundsInRoot)
            assertEquals(shutter, compose.onNodeWithTag("shutter").fetchSemanticsNode().boundsInRoot)
            val label = compose.onNodeWithText("Subject", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertEquals("Label bounds at $angle degrees: $label", angle == 90 || angle == 270, label.height > label.width)
            compose.onNodeWithContentDescription("Camera options").performClick()
            compose.onNodeWithText("Odyssey Journal").assertIsDisplayed().performClick()
            compose.onNodeWithText("Odyssey Journal").assertDoesNotExist()
            screenshot("ui-camera-labels-$angle.png")
        }
        compose.onNodeWithContentDescription("Photo shape").performClick()
        compose.onNodeWithText("16:9", substring = false).performClick()
        checkPreview(9f / 16f)
        compose.onNodeWithContentDescription("Photo shape").performClick()
        compose.onNodeWithText("1:1", substring = false).performClick()
        checkPreview(1f)
    }

    @Test fun rotatingDuringALookKeepsTheResultAndEditorControls() {
        val file = File(compose.activity.cacheDir, "rotation-fixture.jpg")
        val source = Bitmap.createBitmap(240, 320, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        file.outputStream().use { source.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        source.recycle()
        val release = CountDownLatch(1)
        val calls = AtomicInteger()
        try {
            rotate(false)
            compose.runOnUiThread {
                compose.activity.setContent {
                    FrameAiTheme {
                        ReviewScreen(file.absolutePath, "rotation-fixture", PhotoGrader { bitmap, _, _ ->
                            calls.incrementAndGet()
                            check(release.await(15, TimeUnit.SECONDS))
                            bitmap.copy(Bitmap.Config.ARGB_8888, true).apply { eraseColor(Color.GREEN) }
                        }, onRetake = {})
                    }
                }
            }
            compose.waitUntil(10_000) {
                compose.onAllNodesWithContentDescription("Original photo").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Warm Film").performScrollTo().performClick()
            compose.waitUntil(5_000) { calls.get() == 1 }
            rotate(true)
            release.countDown()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithContentDescription("Edited photo, Warm Film").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Save edit").performScrollTo().assertIsDisplayed().assertIsEnabled()
            screenshot("ui-review-landscape.png")
            rotate(false)
            compose.onNodeWithContentDescription("Edited photo, Warm Film").assertIsDisplayed()
            compose.onNodeWithText("Save edit").performScrollTo().assertIsDisplayed()
            screenshot("ui-review-portrait.png")
            assertEquals(1, calls.get())
        } finally {
            release.countDown()
            file.delete()
            compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR }
        }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        // System rotation animations run after Compose has finished measuring the new layout.
        android.os.SystemClock.sleep(1_000)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(compose.activity.cacheDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
