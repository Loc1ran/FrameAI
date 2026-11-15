package com.team.frameai.camera

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.camera.view.TransformExperimental
import androidx.camera.view.transform.ImageProxyTransformFactory
import com.team.frameai.model.FaceBoxNormalized
import com.team.frameai.vision.FaceBoxMath
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.team.frameai.guide.FrameRect
import com.team.frameai.guide.GeometryMeasure
import com.team.frameai.guide.LightingAnalyzer
import com.team.frameai.guide.LightingMap
import com.team.frameai.guide.SceneGeometry
import com.team.frameai.guide.ShapeOverlap
import com.team.frameai.guide.SubjectTap
import com.team.frameai.guide.TrackCandidate
import com.team.frameai.guide.ZoomLens
import com.team.frameai.model.LiveGuide
import com.team.frameai.model.LutIds
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class FrameCameraController(context: Context) {
    private val appContext = context.applicationContext
    val lifecycleController = LifecycleCameraController(appContext)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val subjectTracker = MlKitSubjectTracker()
    @Volatile private var generation = 0
    @Volatile private var running = false
    /** CameraX only allows reading the selector on the main thread. Analysis keeps this copy. */
    @Volatile private var facingFront = false
    private var lastFrameSentAt = 0L
    private val frameSendBusy = AtomicBoolean(false)

    @Volatile var detector: PrimaryFaceDetector? = null
    @Volatile var compositionGuide: CompositionGuide? = null
    @Volatile var previewView: androidx.camera.view.PreviewView? = null
    @Volatile var onGuide: (LiveGuide, FaceBoxNormalized?, SceneGeometry?, LightingMap?, String?, Boolean) -> Unit =
        { _, _, _, _, _, _ -> }
    private val previewTargets = java.util.concurrent.atomic.AtomicReference<List<TrackCandidate>>(emptyList())
    @Volatile var onFrameJpeg: ((ByteArray) -> Unit)? = null
    @Volatile var captureFrames: Boolean = false
    @Volatile var sceneLutAllowed: Boolean = true
    @Volatile var suggestedLutId: String = LutIds.WARM_FILM

    init {
        lifecycleController.cameraSelector = androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA
        facingFront = false
        lifecycleController.imageCaptureMode = ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
    }

    fun start(owner: LifecycleOwner) {
        running = true
        generation++
        lifecycleController.bindToLifecycle(owner)
    }

    fun stop() {
        running = false
        generation++
        lifecycleController.unbind()
    }

    fun shutdown() {
        stop()
        subjectTracker.close()
        analysisExecutor.shutdown()
    }

    fun enableAnalysis() {
        lifecycleController.imageAnalysisBackpressureStrategy =
            androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
        lifecycleController.setEnabledUseCases(
            CameraController.IMAGE_CAPTURE or CameraController.IMAGE_ANALYSIS,
        )
        lifecycleController.setImageAnalysisAnalyzer(analysisExecutor) { image ->
            val token = generation
            try {
                publishGuide(image)
            } catch (error: Exception) {
                Log.w("FrameAI", "Frame analysis unavailable", error)
                mainHandler.post {
                    if (running && token == generation) onGuide(
                        staticTargetGuide(),
                        null,
                        null,
                        null,
                        "Object detection is unavailable. Reopen the camera to retry; you can still take a photo.",
                        subjectTracker.hasSelection,
                    )
                }
            } finally {
                image.close()
            }
        }
    }

    fun disableAnalysis() {
        lifecycleController.clearImageAnalysisAnalyzer()
        lifecycleController.setEnabledUseCases(CameraController.IMAGE_CAPTURE)
    }

    fun setActionMode(enabled: Boolean) {
        lifecycleController.imageCaptureMode = if (enabled) {
            ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
        } else {
            ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
        }
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

    /** Captures a short burst sequentially so CameraX never receives overlapping requests. */
    fun takeBurst(
        files: List<File>,
        onSaved: (List<File>) -> Unit,
        onError: (String) -> Unit,
    ) {
        require(files.isNotEmpty()) { "Burst needs at least one output file" }
        val saved = mutableListOf<File>()
        fun capture(index: Int) {
            if (index == files.size) {
                onSaved(saved.toList())
                return
            }
            val file = files[index]
            takePicture(
                file = file,
                onSaved = {
                    saved += file
                    capture(index + 1)
                },
                onError = { message ->
                    files.drop(index).forEach(File::delete)
                    if (saved.isEmpty()) onError(message) else onSaved(saved.toList())
                },
            )
        }
        capture(0)
    }

    @androidx.annotation.OptIn(markerClass = [TransformExperimental::class])
    private fun publishGuide(image: androidx.camera.core.ImageProxy) {
        if (!running) return
        val token = generation
        val frameAt = SystemClock.elapsedRealtime()
        val manual = subjectTracker.hasSelection
        val tracked = subjectTracker.inspect(image)
        val face = if (manual) null else detector?.detectPrimaryFace(image)
        val raw = if (manual) tracked.primary else face ?: tracked.primary
        val overlap = ShapeOverlap.strongest(tracked.boxes)
        val samples = LumaGridSampler.sample(image)
        val measured = samples?.let { GeometryMeasure.measure(it).forViewer(frontCamera()) }
        // ML Kit's adapter already returns unrotated buffer coordinates.
        val source = ImageProxyTransformFactory().getOutputTransform(image)
        val width = image.width
        val height = image.height
        val rotation = image.imageInfo.rotationDegrees
        val upright = raw?.let {
            FaceBoxMath.sensorBoxToUpright(
                FaceBoxNormalized(it.centerX, it.centerY, it.left, it.top, it.right, it.bottom),
                image.imageInfo.rotationDegrees,
            )
        }
        val subjectRect = upright?.let { FrameRect(it.left, it.top, it.right, it.bottom) }
        val lighting = samples?.let {
            LightingAnalyzer.measure(it, GeometryMeasure.COLUMNS, GeometryMeasure.ROWS, subjectRect)
        }
        val scene = ImageSceneSampler.read(image, upright)?.copy(
            geometry = measured,
            shapeOverlap = overlap,
            lighting = lighting,
        )
        if (scene != null && sceneLutAllowed) suggestedLutId = scene.lookId
        mainHandler.post {
            if (!running || token != generation) return@post
            try {
                val preview = previewView
                val target = preview?.outputTransform
                val fresh = preview != null && target != null &&
                    SystemClock.elapsedRealtime() - frameAt < 1_500L
                val mapped = if (raw != null && fresh) {
                    AnalysisCoordinateMapper.map(
                        source,
                        target!!,
                        width,
                        height,
                        preview!!.width,
                        preview.height,
                        raw,
                        mirroredPreview = frontCamera(),
                    )
                } else null
                val choices = if (fresh) {
                    tracked.subjects.mapNotNull { subject ->
                        val box = AnalysisCoordinateMapper.map(
                            source,
                            target!!,
                            width,
                            height,
                            preview!!.width,
                            preview.height,
                            subject.sensor,
                            mirroredPreview = frontCamera(),
                        ) ?: return@mapNotNull null
                        TrackCandidate(subject.id, FrameRect(box.left, box.top, box.right, box.bottom))
                    }
                } else emptyList()
                previewTargets.set(choices)
                val guide = when {
                    raw != null && mapped == null -> staticTargetGuide()
                    else -> compositionGuide?.suggest(mapped, null, scene) ?: detectedGuide(mapped)
                }
                val lines = scene?.geometry?.let { geometry ->
                    if (preview != null && target != null) {
                        AnalysisCoordinateMapper.mapGeometry(
                            source,
                            target,
                            width,
                            height,
                            preview.width,
                            preview.height,
                            geometry,
                            rotation,
                        )
                    } else {
                        geometry.copy(detected = null, leveled = null)
                    }
                }
                val lightingMap = scene?.lighting?.let { read ->
                    if (preview != null && target != null) {
                        AnalysisCoordinateMapper.mapLighting(
                            source,
                            target,
                            width,
                            height,
                            preview.width,
                            preview.height,
                            read,
                            rotation,
                        )
                    } else {
                        null
                    }
                }
                onGuide(guide, mapped, lines, lightingMap, null, subjectTracker.hasSelection)
            } catch (error: Exception) {
                Log.w("FrameAI", "Preview mapping unavailable", error)
                onGuide(
                    staticTargetGuide(),
                    null,
                    null,
                    null,
                    "The framing guide could not align with the preview. Reopen the camera to retry.",
                    subjectTracker.hasSelection,
                )
            }
        }
        maybeSendFrame(image)
    }

    fun finishFrameSend() {
        frameSendBusy.set(false)
    }

    fun canApplyZoom(scale: Float): Boolean {
        val zoom = lifecycleController.zoomState.value ?: return false
        return ZoomLens.target(zoom.zoomRatio, scale, zoom.minZoomRatio, zoom.maxZoomRatio) != null
    }

    fun applyZoom(scale: Float) {
        try {
            val control = lifecycleController.cameraControl ?: return
            val zoom = lifecycleController.zoomState.value ?: return
            val target = ZoomLens.target(zoom.zoomRatio, scale, zoom.minZoomRatio, zoom.maxZoomRatio) ?: return
            control.setZoomRatio(target)
        } catch (_: Exception) {
            // The lens is not ready yet. Another tap can try again.
        }
    }

    private fun frontCamera(): Boolean = facingFront

    /** Selects the object under the tap, in preview coordinates from 0 to 1. An empty tap clears it. */
    fun selectSubjectAt(x: Float, y: Float): Boolean {
        val chosen = SubjectTap.choose(previewTargets.get(), x, y)
        val id = chosen?.id
        if (id == null) {
            subjectTracker.clearSelection()
            return false
        }
        subjectTracker.select(id)
        return true
    }

    fun clearSubjectSelection() = subjectTracker.clearSelection()

    private fun maybeSendFrame(image: androidx.camera.core.ImageProxy) {
        if (!captureFrames) return
        val listener = onFrameJpeg ?: return
        val now = SystemClock.elapsedRealtime()
        if (now - lastFrameSentAt < FRAME_INTERVAL_MS) return
        if (!frameSendBusy.compareAndSet(false, true)) return
        lastFrameSentAt = now
        try {
            val jpeg = FrameJpeg.encode(image)
            if (jpeg == null) {
                frameSendBusy.set(false)
                return
            }
            val token = generation
            mainHandler.post {
                if (running && captureFrames && token == generation) listener(jpeg)
                else finishFrameSend()
            }
        } catch (_: Throwable) {
            frameSendBusy.set(false)
        }
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
        message = "Use the grid to place any subject",
        targetX = TARGET_X,
        targetY = TARGET_Y,
        subjectX = null,
        subjectY = null,
        isReady = false,
        source = "offline_rules",
    )

    private fun noFaceGuide(): LiveGuide = LiveGuide(
        kind = "NO_FACE",
        message = "Point at any subject. Use the grid to choose a placement.",
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
        const val FRAME_INTERVAL_MS = 4_000L
    }
}
