package com.team.frameai.vision

import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.team.frameai.model.FaceBoxNormalized
import java.util.concurrent.atomic.AtomicBoolean

/**
 * On-device face box for the live guide.
 *
 * This adapter consumes [imageProxy] and closes it on every path. The camera owner must not close it again.
 * Coordinates returned to [onResult] are PreviewView-normalized after FILL_CENTER crop.
 * Pass the current PreviewView size. A zero size returns null so the UI keeps the static target
 * instead of painting a box in the wrong space.
 *
 * Detection is ML Kit bundled face detection in FAST mode. One primary face, no tracking, no identity.
 * Compiled by the Android app, not by the brain unit-test module.
 */
class FaceDetectionAdapter(
    private val detector: FaceDetector = defaultDetector(),
) : AutoCloseable {
    @Volatile
    private var closed = false

    fun analyze(
        imageProxy: ImageProxy,
        viewWidth: Int,
        viewHeight: Int,
        mirrorX: Boolean = false,
        onResult: (FaceBoxNormalized?) -> Unit,
    ) {
        val frameClosed = AtomicBoolean(false)
        fun closeFrame() {
            if (frameClosed.compareAndSet(false, true)) {
                imageProxy.close()
            }
        }
        fun deliver(result: FaceBoxNormalized?) {
            try {
                onResult(result)
            } finally {
                closeFrame()
            }
        }
        if (closed || viewWidth <= 0 || viewHeight <= 0) {
            deliver(null)
            return
        }
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            deliver(null)
            return
        }
        try {
            val rotation = imageProxy.imageInfo.rotationDegrees
            val input = InputImage.fromMediaImage(mediaImage, rotation)
            val (uprightWidth, uprightHeight) = FaceBoxMath.uprightSize(
                mediaImage.width,
                mediaImage.height,
                rotation,
            )
            detector.process(input)
                .addOnSuccessListener { faces ->
                    val boxes = faces.mapNotNull { face ->
                        val rect = face.boundingBox
                        FaceBoxMath.normalizeUpright(
                            left = rect.left.toFloat(),
                            top = rect.top.toFloat(),
                            right = rect.right.toFloat(),
                            bottom = rect.bottom.toFloat(),
                            uprightWidth = uprightWidth,
                            uprightHeight = uprightHeight,
                        )
                    }
                    val primary = FaceBoxMath.selectPrimary(boxes)
                    val mapped = primary?.let { box ->
                        PreviewCoordinateMapper.mapBox(
                            box,
                            PreviewMapping(
                                analysisWidth = uprightWidth,
                                analysisHeight = uprightHeight,
                                viewWidth = viewWidth,
                                viewHeight = viewHeight,
                                mirrorX = mirrorX,
                            ),
                        )
                    }
                    deliver(mapped)
                }
                .addOnFailureListener {
                    deliver(null)
                }
                .addOnCompleteListener {
                    closeFrame()
                }
        } catch (error: RuntimeException) {
            deliver(null)
        }
    }

    override fun close() {
        closed = true
        detector.close()
    }

    companion object {
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
