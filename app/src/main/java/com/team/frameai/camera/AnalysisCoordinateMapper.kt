package com.team.frameai.camera

import androidx.camera.view.TransformExperimental
import androidx.camera.view.transform.CoordinateTransform
import androidx.camera.view.transform.OutputTransform
import com.team.frameai.guide.HeadOrientation
import com.team.frameai.guide.LightingMap
import com.team.frameai.guide.LightingRead
import com.team.frameai.guide.NormLine
import com.team.frameai.guide.SceneGeometry
import com.team.frameai.model.FaceBoxNormalized
import com.team.frameai.model.RawFaceBox
import com.team.frameai.vision.FaceBoxMath
import kotlin.math.max
import kotlin.math.min

object AnalysisCoordinateMapper {
    @androidx.annotation.OptIn(markerClass = [TransformExperimental::class])
    fun map(
        source: OutputTransform,
        target: OutputTransform,
        imageWidth: Int,
        imageHeight: Int,
        viewWidth: Int,
        viewHeight: Int,
        raw: RawFaceBox,
        mirroredPreview: Boolean = false,
    ): FaceBoxNormalized? {
        if (viewWidth <= 0 || viewHeight <= 0) return null
        val transform = CoordinateTransform(source, target)
        val width = imageWidth.toFloat()
        val height = imageHeight.toFloat()
        val anchorX = raw.anchorX ?: raw.centerX
        val anchorY = raw.anchorY ?: raw.centerY
        val points = floatArrayOf(
            raw.left * width, raw.top * height,
            raw.right * width, raw.bottom * height,
            raw.centerX * width, raw.centerY * height,
            anchorX * width, anchorY * height,
        )
        transform.mapPoints(points)
        fun nx(x: Float) = (x / viewWidth).coerceIn(0f, 1f)
        fun ny(y: Float) = (y / viewHeight).coerceIn(0f, 1f)
        val left = nx(points[0])
        val top = ny(points[1])
        val right = nx(points[2])
        val bottom = ny(points[3])
        val yaw = raw.yawDegrees?.let { HeadOrientation.previewYaw(it, mirroredPreview) }
        return FaceBoxNormalized(
            centerX = nx(points[4]),
            centerY = ny(points[5]),
            left = min(left, right),
            top = min(top, bottom),
            right = max(left, right),
            bottom = max(top, bottom),
            anchorX = nx(points[6]),
            anchorY = ny(points[7]),
            anchorKind = raw.anchorKind,
            yawDegrees = yaw?.takeIf { it.isFinite() },
        )
    }

    /**
     * Moves upright-image lines into the preview.
     * Returns the measurements with lines removed when the preview size is not usable.
     */
    @androidx.annotation.OptIn(markerClass = [TransformExperimental::class])
    fun mapGeometry(
        source: OutputTransform,
        target: OutputTransform,
        imageWidth: Int,
        imageHeight: Int,
        viewWidth: Int,
        viewHeight: Int,
        geometry: SceneGeometry,
        rotationDegrees: Int,
    ): SceneGeometry {
        if (viewWidth <= 0 || viewHeight <= 0 || imageWidth <= 0 || imageHeight <= 0) {
            return geometry.copy(detected = null, leveled = null)
        }
        val transform = CoordinateTransform(source, target)
        return geometry.copy(
            detected = geometry.detected?.let {
                mapLine(it, transform, imageWidth, imageHeight, viewWidth, viewHeight, rotationDegrees)
            },
            leveled = geometry.leveled?.let {
                mapLine(it, transform, imageWidth, imageHeight, viewWidth, viewHeight, rotationDegrees)
            },
        )
    }

    @androidx.annotation.OptIn(markerClass = [TransformExperimental::class])
    private fun mapLine(
        line: NormLine,
        transform: CoordinateTransform,
        imageWidth: Int,
        imageHeight: Int,
        viewWidth: Int,
        viewHeight: Int,
        rotationDegrees: Int,
    ): NormLine {
        val (sx1, sy1) = FaceBoxMath.uprightPointToSensor(line.x1, line.y1, rotationDegrees)
        val (sx2, sy2) = FaceBoxMath.uprightPointToSensor(line.x2, line.y2, rotationDegrees)
        val points = floatArrayOf(
            sx1 * imageWidth,
            sy1 * imageHeight,
            sx2 * imageWidth,
            sy2 * imageHeight,
        )
        transform.mapPoints(points)
        fun nx(x: Float) = (x / viewWidth).coerceIn(0f, 1f)
        fun ny(y: Float) = (y / viewHeight).coerceIn(0f, 1f)
        return NormLine(nx(points[0]), ny(points[1]), nx(points[2]), ny(points[3]))
    }

    /** Places each luma-grid cell into the preview. Cells outside the crop stay outside 0..1 and are not drawn. */
    @androidx.annotation.OptIn(markerClass = [TransformExperimental::class])
    fun mapLighting(
        source: OutputTransform,
        target: OutputTransform,
        imageWidth: Int,
        imageHeight: Int,
        viewWidth: Int,
        viewHeight: Int,
        lighting: LightingRead,
        rotationDegrees: Int,
    ): LightingMap? {
        if (viewWidth <= 0 || viewHeight <= 0 || imageWidth <= 0 || imageHeight <= 0) return null
        val columns = lighting.columns
        val rows = lighting.rows
        val count = columns * rows
        if (lighting.bands.size != count) return null
        val transform = CoordinateTransform(source, target)
        val points = FloatArray(count * 2)
        for (row in 0 until rows) {
            for (col in 0 until columns) {
                val nx = (col + 0.5f) / columns
                val ny = (row + 0.5f) / rows
                val (sx, sy) = FaceBoxMath.uprightPointToSensor(nx, ny, rotationDegrees)
                val index = row * columns + col
                points[index * 2] = sx * imageWidth
                points[index * 2 + 1] = sy * imageHeight
            }
        }
        transform.mapPoints(points)
        val xs = FloatArray(count)
        val ys = FloatArray(count)
        for (index in 0 until count) {
            xs[index] = points[index * 2] / viewWidth
            ys[index] = points[index * 2 + 1] / viewHeight
        }
        return LightingMap(columns, rows, lighting.bands, xs, ys)
    }
}
