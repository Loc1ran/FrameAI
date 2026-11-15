package com.team.frameai.vision

import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import com.team.frameai.camera.PrimaryFaceDetector
import com.team.frameai.model.AnchorKind
import com.team.frameai.model.RawFaceBox

/**
 * Synchronous ML Kit face box in the raw ImageProxy buffer.
 * The camera controller closes the frame. This detector does not.
 */
class MlKitPrimaryFaceDetector(
    private val detector: FaceDetector = defaultDetector(),
) : PrimaryFaceDetector, AutoCloseable {
    @Volatile
    private var closed = false

    @androidx.annotation.OptIn(markerClass = [androidx.camera.core.ExperimentalGetImage::class])
    override fun detectPrimaryFace(image: ImageProxy): RawFaceBox? {
        if (closed) return null
        val media = image.image ?: return null
        return try {
            val rotation = image.imageInfo.rotationDegrees
            val input = InputImage.fromMediaImage(media, rotation)
            // The controller owns this buffer until detection has finished.
            val faces = Tasks.await(detector.process(input))
            val primary = faces.maxByOrNull { face ->
                val rect = face.boundingBox
                rect.width() * rect.height()
            } ?: return null
            val (uprightWidth, uprightHeight) = FaceBoxMath.uprightSize(media.width, media.height, rotation)
            val upright = FaceBoxMath.normalizeUpright(
                left = primary.boundingBox.left.toFloat(),
                top = primary.boundingBox.top.toFloat(),
                right = primary.boundingBox.right.toFloat(),
                bottom = primary.boundingBox.bottom.toFloat(),
                uprightWidth = uprightWidth,
                uprightHeight = uprightHeight,
            ) ?: return null
            val anchor = readAnchor(primary, uprightWidth, uprightHeight)
            val sensor = FaceBoxMath.uprightBoxToSensor(upright, rotation)
            val (anchorX, anchorY) = FaceBoxMath.uprightPointToSensor(anchor.x, anchor.y, rotation)
            val yaw = primary.headEulerAngleY
            RawFaceBox(
                centerX = sensor.centerX,
                centerY = sensor.centerY,
                left = sensor.left,
                top = sensor.top,
                right = sensor.right,
                bottom = sensor.bottom,
                anchorX = anchorX,
                anchorY = anchorY,
                anchorKind = anchor.kind,
                yawDegrees = yaw.takeIf { it.isFinite() },
            )
        } catch (error: Exception) {
            Log.w("FrameAI", "Face detection unavailable; trying object tracking", error)
            null
        }
    }

    override fun close() {
        closed = true
        detector.close()
    }

    private fun readAnchor(face: Face, uprightWidth: Int, uprightHeight: Int): UprightAnchor {
        val width = uprightWidth.toFloat().coerceAtLeast(1f)
        val height = uprightHeight.toFloat().coerceAtLeast(1f)
        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position
        if (leftEye != null && rightEye != null) {
            return UprightAnchor((leftEye.x + rightEye.x) / 2f / width, (leftEye.y + rightEye.y) / 2f / height, AnchorKind.EYES)
        }
        val oneEye = leftEye ?: rightEye
        if (oneEye != null) {
            return UprightAnchor(oneEye.x / width, oneEye.y / height, AnchorKind.EYES)
        }
        val nose = face.getLandmark(FaceLandmark.NOSE_BASE)?.position
        if (nose != null) {
            return UprightAnchor(nose.x / width, nose.y / height, AnchorKind.NOSE)
        }
        val rect = face.boundingBox
        return UprightAnchor(
            (rect.left + rect.right) / 2f / width,
            (rect.top + rect.bottom) / 2f / height,
            AnchorKind.BOX,
        )
    }

    private data class UprightAnchor(val x: Float, val y: Float, val kind: AnchorKind)

    companion object {
        fun defaultDetector(): FaceDetector {
            val options = FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .setMinFaceSize(0.12f)
                .build()
            return FaceDetection.getClient(options)
        }
    }
}
