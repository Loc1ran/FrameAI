package com.team.frameai.camera

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetector
import com.team.frameai.model.RawFaceBox
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class SubjectTrackerTest {
    @Test fun waitsForDetectionBeforeReleasingTheFrameAndMapsRotation() {
        val completion = TaskCompletionSource<List<DetectedObject>>()
        val started = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        val tracker = MlKitSubjectTracker(fakeDetector { started.countDown(); completion.task })
        val bitmap = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        try {
            val result = executor.submit<RawFaceBox?> { tracker.track(InputImage.fromBitmap(bitmap, 90)) }
            assertTrue(started.await(2, TimeUnit.SECONDS))
            try {
                result.get(350, TimeUnit.MILLISECONDS)
                fail("The camera buffer must stay owned while ML Kit is still using it")
            } catch (_: TimeoutException) {
                // The old 250 ms timeout returned early and let the controller close the buffer.
            }
            completion.setResult(listOf(DetectedObject(Rect(10, 40, 50, 160), 7, emptyList())))
            val box = result.get(2, TimeUnit.SECONDS)!!
            assertEquals(0.5f, box.centerX, 0.001f)
            assertEquals(0.7f, box.centerY, 0.001f)
            assertTrue(tracker.isLocked)
        } finally {
            completion.trySetResult(emptyList())
            executor.shutdown()
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS))
            tracker.close()
            bitmap.recycle()
        }
    }

    @Test fun detectionFailureIsNotReportedAsAnEmptyScene() {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val input = InputImage.fromBitmap(bitmap, 0)
        try {
            MlKitSubjectTracker(fakeDetector { Tasks.forResult(emptyList()) }).use {
                assertNull(it.track(input))
            }
            MlKitSubjectTracker(fakeDetector { Tasks.forException(IllegalStateException("model unavailable")) }).use {
                try {
                    it.track(input)
                    fail("A failed detector must reach the camera's error state")
                } catch (error: ExecutionException) {
                    assertEquals("model unavailable", error.cause?.message)
                }
            }
        } finally { bitmap.recycle() }
    }

    @Test fun installedObjectModelProcessesSuccessiveFrames() {
        val bitmap = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888)
        try {
            MlKitSubjectTracker().use { tracker ->
                repeat(35) { tracker.track(InputImage.fromBitmap(bitmap, 0)) }
            }
        } finally { bitmap.recycle() }
    }

    private fun fakeDetector(process: () -> Task<List<DetectedObject>>): ObjectDetector =
        Proxy.newProxyInstance(ObjectDetector::class.java.classLoader, arrayOf(ObjectDetector::class.java)) { _, method, _ ->
            when (method.name) {
                "process" -> process()
                "close" -> null
                else -> error("Unexpected detector call: ${method.name}")
            }
        } as ObjectDetector
}
