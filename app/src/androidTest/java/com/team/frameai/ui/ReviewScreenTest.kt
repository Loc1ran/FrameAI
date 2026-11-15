package com.team.frameai.ui

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.team.frameai.camera.ImageSaver
import com.team.frameai.camera.PhotoGrader
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class ReviewScreenTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var original: File
    private val stamp = "frameai_test_" + System.nanoTime()

    @Before fun createPhoto() {
        original = File(context.cacheDir, "$stamp.jpg")
        val bitmap = Bitmap.createBitmap(32, 48, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        ImageSaver.writeJpeg(bitmap, original)
        bitmap.recycle()
    }

    @After fun cleanFiles() {
        original.delete()
        File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES), "edits")
            .listFiles()?.filter { it.name.contains(stamp) }?.forEach { it.delete() }
    }

    private fun open(grader: PhotoGrader) {
        compose.setContent {
            FrameAiTheme { ReviewScreen(original.absolutePath, stamp, grader, onRetake = {}) }
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription("Original photo").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test fun gradesTheFirstLookWithoutATap() {
        val release = CountDownLatch(1)
        val requested = AtomicReference<String>()
        open(PhotoGrader { source, id, _ ->
            requested.set(id)
            check(release.await(10, TimeUnit.SECONDS))
            source.copy(Bitmap.Config.ARGB_8888, true).apply { eraseColor(Color.GREEN) }
        })
        try {
            compose.onNodeWithText("Share original").assertIsEnabled()
        } finally {
            release.countDown()
        }
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("Share edit").fetchSemanticsNodes().isNotEmpty()
        }
        assertFalse(requested.get().isNullOrBlank())
        compose.onNodeWithText("Share edit").assertExists()
    }

    @Test fun failedGradeKeepsOriginalAndReportsTheFailure() {
        open(PhotoGrader { _, _, _ -> throw IllegalArgumentException("bad cube") })
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("Could not apply the looks.", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Original photo").assertExists()
        compose.onNodeWithText("Share original").assertIsEnabled()
    }

    @Test fun sharingBeforeSavingUsesTheVisibleEditedPixels() {
        val before = original.readBytes()
        val shared = AtomicReference<Uri>()
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.action != Intent.ACTION_CHOOSER) return null
                @Suppress("DEPRECATION")
                val send = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
                @Suppress("DEPRECATION")
                shared.set(send.getParcelableExtra(Intent.EXTRA_STREAM))
                return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
            }
        }
        instrumentation.addMonitor(monitor)
        try {
            open(PhotoGrader { source, _, _ ->
                source.copy(Bitmap.Config.ARGB_8888, true).apply { eraseColor(Color.GREEN) }
            })
            compose.waitUntil(15_000) {
                compose.onAllNodesWithText("Share edit").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Share edit").performScrollTo().performClick()
            compose.waitUntil(10_000) { shared.get() != null }
            val bitmap = context.contentResolver.openInputStream(shared.get())!!.use(BitmapFactory::decodeStream)
            assertTrue(Color.green(bitmap.getPixel(0, 0)) > 240)
            assertTrue(Color.red(bitmap.getPixel(0, 0)) < 15)
            bitmap.recycle()
            assertArrayEquals(before, original.readBytes())
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }
}
