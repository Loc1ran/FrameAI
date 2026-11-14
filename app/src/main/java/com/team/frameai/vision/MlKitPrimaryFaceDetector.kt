package com.team.frameai.vision

import android.graphics.Rect
import androidx.camera.core.ImageProxy
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.team.frameai.camera.PrimaryFaceDetector
import com.team.frameai.model.FaceBoxNormalized
import com.team.frameai.model.RawFaceBox
import java.util.concurrent.TimeUnit

/**
 * Synchronous ML Kit face box in the raw ImageProxy buffer.
 * The camera controller closes the frame. This detector does not.
 */
class MlKitPrimaryFaceDetector(
    private val detector: FaceDetector = defaultDetector(),
) : PrimaryFaceDetector, AutoCloseable {
    @Volatile
    private var closed = false

    override fun detectPrimaryFace(image: ImageProxy): RawFaceBox? {
        if (closed) return null
        val media = image.image ?: return null
        return try {
            val rotation = image.imageInfo.rotationDegrees
            val input = InputImage.fromMediaImage(media, rotation)
            val faces = Tasks.await(detector.process(input), TIMEOUT_MS, TimeUnit.MILLISECONDS)
            val primary = faces.maxByOrNull { face ->
                val rect = face.boundingBox
                rect.width() * rect.height()
            } ?: return null
            val upright = uprightBox(primary.boundingBox, rotation, media.width, media.height) ?: return null
            val sensor = FaceBoxMath.uprightBoxToSensor(upright, rotation)
            RawFaceBox(
                centerX = sensor.centerX,
                centerY = sensor.centerY,
                left = sensor.left,
                top = sensor.top,
                right = sensor.right,
                bottom = sensor.bottom,
            )
        } catch (error: Exception) {
            null
        }
    }

    override fun close() {
        closed = true
        detector.close()
    }

    private fun uprightBox(rect: Rect, rotation: Int, sensorWidth: Int, sensorHeight: Int): FaceBoxNormalized? {
        val (width, height) = FaceBoxMath.uprightSize(sensorWidth, sensorHeight, rotation)
        return FaceBoxMath.normalizeUpright(
            left = rect.left.toFloat(),
            top = rect.top.toFloat(),
            right = rect.right.toFloat(),
            bottom = rect.bottom.toFloat(),
            uprightWidth = width,
            uprightHeight = height,
        )
    }

    companion object {
        private const val TIMEOUT_MS = 800L

        fun defaultDetector(): FaceDetector {
            val options = FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .setMinFaceSize(0.12f)
                .build()
            return FaceDetection.getClient(options)
        }
    }
}
