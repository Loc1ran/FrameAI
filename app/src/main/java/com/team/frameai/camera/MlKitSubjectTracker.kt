package com.team.frameai.camera

import android.graphics.Rect
import android.os.SystemClock
import androidx.camera.core.ImageProxy
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.team.frameai.guide.FrameRect
import com.team.frameai.guide.SubjectLock
import com.team.frameai.guide.TrackCandidate
import com.team.frameai.model.FaceBoxNormalized
import com.team.frameai.model.RawFaceBox
import com.team.frameai.vision.FaceBoxMath

data class TrackedFrame(val primary: RawFaceBox?, val boxes: List<FrameRect>)

/**
 * Follows one prominent object without switching to newly appearing faces.
 * The camera controller closes the image. This tracker does not.
 */
class MlKitSubjectTracker(
    private val detector: ObjectDetector = defaultDetector(),
) : AutoCloseable {
    @Volatile
    private var closed = false
    private val subjectLock = SubjectLock()

    val isLocked: Boolean get() = subjectLock.isLocked(SystemClock.elapsedRealtime())

    @androidx.annotation.OptIn(markerClass = [androidx.camera.core.ExperimentalGetImage::class])
    fun track(image: ImageProxy): RawFaceBox? = inspect(image).primary

    @androidx.annotation.OptIn(markerClass = [androidx.camera.core.ExperimentalGetImage::class])
    fun inspect(image: ImageProxy): TrackedFrame {
        if (closed) return TrackedFrame(null, emptyList())
        val media = image.image ?: return TrackedFrame(null, emptyList())
        return inspect(InputImage.fromMediaImage(media, image.imageInfo.rotationDegrees))
    }

    internal fun track(input: InputImage): RawFaceBox? = inspect(input).primary

    internal fun inspect(input: InputImage): TrackedFrame {
        if (closed) return TrackedFrame(null, emptyList())
        val rotation = input.rotationDegrees
        // Keep the camera buffer alive until ML Kit finishes; CameraX drops queued frames.
        val found = Tasks.await(detector.process(input))
        val (width, height) = FaceBoxMath.uprightSize(input.width, input.height, rotation)
        val candidates = found.mapNotNull { item ->
            val upright = normalize(item.boundingBox, width, height) ?: return@mapNotNull null
            TrackCandidate(
                id = item.trackingId,
                box = FrameRect(upright.left, upright.top, upright.right, upright.bottom),
            )
        }
        val boxes = candidates.map { it.box }
        val chosen = subjectLock.choose(candidates, SystemClock.elapsedRealtime())
            ?: return TrackedFrame(null, boxes)
        val upright = FaceBoxNormalized.fromEdges(chosen.box.left, chosen.box.top, chosen.box.right, chosen.box.bottom)
        val sensor = FaceBoxMath.uprightBoxToSensor(upright, rotation)
        return TrackedFrame(
            RawFaceBox(
                centerX = sensor.centerX,
                centerY = sensor.centerY,
                left = sensor.left,
                top = sensor.top,
                right = sensor.right,
                bottom = sensor.bottom,
            ),
            boxes,
        )
    }

    override fun close() {
        closed = true
        detector.close()
    }

    private fun normalize(rect: Rect, uprightWidth: Int, uprightHeight: Int): FaceBoxNormalized? {
        return FaceBoxMath.normalizeUpright(
            left = rect.left.toFloat(),
            top = rect.top.toFloat(),
            right = rect.right.toFloat(),
            bottom = rect.bottom.toFloat(),
            uprightWidth = uprightWidth,
            uprightHeight = uprightHeight,
        )
    }

    companion object {
        fun defaultDetector(): ObjectDetector {
            val options = ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
                .build()
            return ObjectDetection.getClient(options)
        }
    }
}
