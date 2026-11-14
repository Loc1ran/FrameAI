package com.team.frameai.camera

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.team.frameai.model.LiveGuide
import com.team.frameai.model.LutIds
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class FrameCameraController(context: Context) {
    private val appContext = context.applicationContext
    val lifecycleController = LifecycleCameraController(appContext)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var lastGuideAt = 0L

    @Volatile var detector: PrimaryFaceDetector? = null
    @Volatile var compositionGuide: CompositionGuide? = null
    @Volatile var previewView: androidx.camera.view.PreviewView? = null
    @Volatile var onGuide: (LiveGuide, com.team.frameai.model.FaceBoxNormalized?) -> Unit = { _, _ -> }
    @Volatile var suggestedLutId: String = LutIds.WARM_FILM

    init {
        lifecycleController.cameraSelector = androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA
        lifecycleController.imageCaptureMode = ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
    }

    fun start(owner: LifecycleOwner) {
        lifecycleController.bindToLifecycle(owner)
    }

    fun stop() {
        lifecycleController.unbind()
    }

    fun shutdown() {
        stop()
        analysisExecutor.shutdown()
    }

    fun enableAnalysis() {
        lifecycleController.imageAnalysisBackpressureStrategy =
            androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
        lifecycleController.setEnabledUseCases(
            CameraController.IMAGE_CAPTURE or CameraController.IMAGE_ANALYSIS,
        )
        lifecycleController.setImageAnalysisAnalyzer(analysisExecutor) { image ->
            try {
                publishGuide(image)
            } catch (_: Throwable) {
                // A bad frame must not freeze the preview.
            } finally {
                image.close()
            }
        }
    }

    fun disableAnalysis() {
        lifecycleController.clearImageAnalysisAnalyzer()
        lifecycleController.setEnabledUseCases(CameraController.IMAGE_CAPTURE)
    }

    fun takePicture(file: File, onSaved: () -> Unit, onError: (String) -> Unit) {
        try {
            val options = ImageCapture.OutputFileOptions.Builder(file).build()
            lifecycleController.takePicture(
                options,
                ContextCompat.getMainExecutor(appContext),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) = onSaved()
                    override fun onError(exception: ImageCaptureException) {
                        onError(exception.message ?: "Could not save the photo")
                    }
                },
            )
        } catch (error: Exception) {
            onError(error.message ?: "Could not save the photo")
        }
    }

    private fun publishGuide(image: androidx.camera.core.ImageProxy) {
        val faceDetector = detector ?: return
        val now = SystemClock.elapsedRealtime()
        if (now - lastGuideAt < 250L) return
        lastGuideAt = now
        val raw = faceDetector.detectPrimaryFace(image)
        val preview = previewView
        val mapped = if (raw != null && preview != null) {
            AnalysisCoordinateMapper.map(image, preview, raw)
        } else {
            null
        }
        val scene = ImageSceneSampler.read(image, mapped)
        if (scene != null) suggestedLutId = scene.lookId
        val guide = when {
            raw != null && mapped == null -> staticTargetGuide()
            else -> compositionGuide?.suggest(mapped, null, scene) ?: detectedGuide(mapped)
        }
        val deliver = onGuide
        val area = mapped
        mainHandler.post { deliver(guide, area) }
    }

    private fun detectedGuide(mapped: com.team.frameai.model.FaceBoxNormalized?): LiveGuide {
        if (mapped == null) return noFaceGuide()
        return LiveGuide(
            kind = "ALIGN_SUBJECT",
            message = "Subject detected",
            targetX = TARGET_X,
            targetY = TARGET_Y,
            subjectX = mapped.centerX.toDouble(),
            subjectY = mapped.centerY.toDouble(),
            isReady = false,
            source = "offline_rules",
        )
    }

    private fun staticTargetGuide(): LiveGuide = LiveGuide(
        kind = "ALIGN_SUBJECT",
        message = "Use the grid to place your subject",
        targetX = TARGET_X,
        targetY = TARGET_Y,
        subjectX = null,
        subjectY = null,
        isReady = false,
        source = "offline_rules",
    )

    private fun noFaceGuide(): LiveGuide = LiveGuide(
        kind = "NO_FACE",
        message = "Use the grid to place your subject",
        targetX = TARGET_X,
        targetY = TARGET_Y,
        subjectX = null,
        subjectY = null,
        isReady = false,
        source = "offline_rules",
    )

    private companion object {
        const val TARGET_X = 0.67
        const val TARGET_Y = 0.33
    }
}
