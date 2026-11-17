package com.team.frameai.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.exifinterface.media.ExifInterface
import com.team.frameai.guide.ActionExposure
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
import com.team.frameai.guide.LightingRead
import com.team.frameai.guide.PersonFrame
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
    @Volatile private var lastFrameSentAt = 0L
    private val frameSendBusy = AtomicBoolean(false)

    @Volatile var detector: PrimaryFaceDetector? = null
    @Volatile var compositionGuide: CompositionGuide? = null
    @Volatile var previewView: androidx.camera.view.PreviewView? = null
    @Volatile var onGuide: (LiveGuide, FaceBoxNormalized?, SceneGeometry?, LightingMap?, String?, Boolean) -> Unit =
        { _, _, _, _, _, _ -> }
    @Volatile private var followFace = false
    private var faceSeenAt = 0L
    private val previewTargets = java.util.concurrent.atomic.AtomicReference<List<TrackCandidate>>(emptyList())
    @Volatile var onFrameJpeg: ((ByteArray) -> Unit)? = null
    @Volatile var captureFrames: Boolean = false
    @Volatile var sceneLutAllowed: Boolean = true
    @Volatile var suggestedLutId: String = LutIds.WARM_FILM
    /** Latest measured light. The journey reads it when a guide update arrives. */
    @Volatile var latestLighting: LightingRead? = null

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

    /** Meter once, then capture the burst. A fast shutter is used when the phone can set one. */
    @androidx.annotation.OptIn(markerClass = [ExperimentalCamera2Interop::class])
    fun takeBurst(
        files: List<File>,
        onSaved: (List<File>) -> Unit,
        onError: (String) -> Unit,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ) {
        require(files.isNotEmpty()) { "Burst needs at least one output file" }
        val info = lifecycleController.cameraInfo ?: return onError("Camera is not ready.")
        val control = lifecycleController.cameraControl ?: return onError("Camera is not ready.")
        val camera2 = Camera2CameraInfo.from(info)
        val manual = camera2.getCameraCharacteristic(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
            ?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR) == true
        val exposureRange = camera2.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
        val isoRange = camera2.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
        val interop = Camera2CameraControl.from(control)
        val previous = interop.captureRequestOptions
        val token = generation
        val meter = File(files.first().parentFile, files.first().name + ".meter.jpg")
        val saved = mutableListOf<File>()
        fun restore() { interop.setCaptureRequestOptions(previous) }
        fun finishError(message: String) {
            restore()
            meter.delete()
            if (saved.isEmpty()) onError(message) else onSaved(saved.toList())
        }
        fun capture(index: Int) {
            if (!running || token != generation) {
                finishError("Burst interrupted. Captured frames are kept on the phone.")
                return
            }
            if (index == files.size) {
                restore()
                meter.delete()
                onSaved(saved.toList())
                return
            }
            onProgress(index + 1, files.size)
            val file = files[index]
            var finished = false
            val giveUp = Runnable {
                if (finished) return@Runnable
                finished = true
                files.drop(index).forEach(File::delete)
                finishError("The camera stopped responding during the burst. Please retry.")
            }
            mainHandler.postDelayed(giveUp, 4_000)
            takePicture(
                file = file,
                onSaved = {
                    if (finished) return@takePicture
                    finished = true
                    mainHandler.removeCallbacks(giveUp)
                    saved += file
                    // CameraX drops a takePicture issued inside its own save callback.
                    mainHandler.post { capture(index + 1) }
                },
                onError = { message ->
                    if (finished) return@takePicture
                    finished = true
                    mainHandler.removeCallbacks(giveUp)
                    files.drop(index).forEach(File::delete)
                    finishError(message)
                },
            )
        }
        fun captureAutomatically() {
            restore()
            mainHandler.post { capture(0) }
        }
        if (!manual || exposureRange == null || isoRange == null) {
            captureAutomatically()
            return
        }
        var meterDone = false
        fun afterMeter(block: () -> Unit) {
            if (meterDone) return
            meterDone = true
            block()
        }
        val meterTimeout = Runnable {
            afterMeter {
                meter.delete()
                captureAutomatically()
            }
        }
        mainHandler.postDelayed(meterTimeout, 2_500)
        takePicture(meter, onSaved = {
            afterMeter {
                mainHandler.removeCallbacks(meterTimeout)
                try {
                    if (!running || token != generation) {
                        finishError("Burst interrupted. Please retry.")
                        return@afterMeter
                    }
                    val exif = ExifInterface(meter)
                    val seconds = exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0)
                    val iso = exif.getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, 0)
                    meter.delete()
                    val plan = ActionExposure.plan(
                        seconds, iso, exposureRange.lower, exposureRange.upper, isoRange.lower, isoRange.upper,
                    )
                    if (plan == null) {
                        captureAutomatically()
                        return@afterMeter
                    }
                    val options = CaptureRequestOptions.Builder()
                        .setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                        .setCaptureRequestOption(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                        .setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, plan.nanos)
                        .setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, plan.iso)
                        .build()
                    val applied = interop.addCaptureRequestOptions(options)
                    var started = false
                    fun start(manualShutter: Boolean) {
                        if (started) return
                        started = true
                        if (!manualShutter) restore()
                        mainHandler.post { capture(0) }
                    }
                    applied.addListener({
                        val appliedOk = try {
                            applied.get()
                            true
                        } catch (_: Exception) {
                            false
                        }
                        start(appliedOk)
                    }, ContextCompat.getMainExecutor(appContext))
                    mainHandler.postDelayed({ start(false) }, 1_000)
                } catch (_: Exception) {
                    meter.delete()
                    captureAutomatically()
                }
            }
        }, onError = {
            afterMeter {
                mainHandler.removeCallbacks(meterTimeout)
                meter.delete()
                captureAutomatically()
            }
        })
    }

    @androidx.annotation.OptIn(markerClass = [TransformExperimental::class])
    private fun publishGuide(image: androidx.camera.core.ImageProxy) {
        if (!running) return
        val token = generation
        val frameAt = SystemClock.elapsedRealtime()
        val tracked = subjectTracker.inspect(image)
        val face = detector?.detectPrimaryFace(image)
        val nowMs = SystemClock.elapsedRealtime()
        if (followFace && face != null) faceSeenAt = nowMs
        if (followFace && nowMs - faceSeenAt > 1_500L) followFace = false
        val rotation = image.imageInfo.rotationDegrees
        val raw = when {
            followFace -> face
            subjectTracker.hasSelection -> tracked.primary
            else -> PersonFrame.cover(tracked.primary, face, rotation)
        }
        val userChoseSubject = subjectTracker.hasSelection || followFace
        val overlap = ShapeOverlap.strongest(tracked.boxes)
        val samples = LumaGridSampler.sample(image)
        val measured = samples?.let { GeometryMeasure.measure(it).forViewer(frontCamera()) }
        // ML Kit's adapter already returns unrotated buffer coordinates.
        val source = ImageProxyTransformFactory().getOutputTransform(image)
        val width = image.width
        val height = image.height
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
                    SystemClock.elapsedRealtime() - frameAt < 4_000L
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
                val faceChoice = if (face != null && fresh && preview != null && target != null) {
                    val mappedFace = AnalysisCoordinateMapper.map(
                        source,
                        target,
                        width,
                        height,
                        preview.width,
                        preview.height,
                        face,
                        mirroredPreview = frontCamera(),
                    )
                    val rect = mappedFace?.let { FrameRect(it.left, it.top, it.right, it.bottom) }
                    if (rect != null && tappableBox(rect) && choices.none { containsPoint(it.box, rect.centerX, rect.centerY) }) {
                        TrackCandidate(FACE_SUBJECT, rect)
                    } else {
                        null
                    }
                } else {
                    null
                }
                val visible = if (faceChoice == null) choices else choices + faceChoice
                previewTargets.set(visible)
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
                latestLighting = scene?.lighting
                onGuide(guide, mapped, lines, lightingMap, null, userChoseSubject)
            } catch (error: Exception) {
                Log.w("FrameAI", "Preview mapping unavailable", error)
                latestLighting = null
                onGuide(
                    staticTargetGuide(),
                    null,
                    null,
                    null,
                    "The framing guide could not align with the preview. Reopen the camera to retry.",
                    userChoseSubject,
                )
            }
        }
        maybeSendFrame(image)
    }

    fun finishFrameSend() {
        frameSendBusy.set(false)
    }

    /** Pulls the next preview upload forward without stacking a second request. */
    fun requestAnalysisSoon() {
        if (frameSendBusy.get()) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastFrameSentAt >= FRAME_INTERVAL_MS) return
        lastFrameSentAt = now - FRAME_INTERVAL_MS
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
            clearSubjectSelection()
            return false
        }
        if (id == FACE_SUBJECT) {
            subjectTracker.clearSelection()
            followFace = true
            faceSeenAt = SystemClock.elapsedRealtime()
            return true
        }
        followFace = false
        subjectTracker.select(id)
        return true
    }

    fun clearSubjectSelection() {
        followFace = false
        subjectTracker.clearSelection()
    }

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

    private fun tappableBox(box: FrameRect): Boolean {
        if (box.width < 0.03f || box.height < 0.03f) return false
        if (box.width > 0.96f && box.height > 0.96f) return false
        return true
    }

    private fun containsPoint(box: FrameRect, x: Float, y: Float): Boolean =
        x in box.left..box.right && y in box.top..box.bottom

    private companion object {
        const val TARGET_X = 0.67
        const val TARGET_Y = 0.33
        const val FRAME_INTERVAL_MS = 4_000L
        const val FACE_SUBJECT = -1
    }
}
