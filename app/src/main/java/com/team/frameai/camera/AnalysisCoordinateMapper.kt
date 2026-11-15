package com.team.frameai.camera

import androidx.camera.view.TransformExperimental
import androidx.camera.view.transform.CoordinateTransform
import androidx.camera.view.transform.OutputTransform
import com.team.frameai.model.FaceBoxNormalized
import com.team.frameai.model.RawFaceBox
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
    ): FaceBoxNormalized? {
        if (viewWidth <= 0 || viewHeight <= 0) return null
        val transform = CoordinateTransform(source, target)
        val width = imageWidth.toFloat()
        val height = imageHeight.toFloat()
        val points = floatArrayOf(
            raw.left * width, raw.top * height,
            raw.right * width, raw.bottom * height,
            raw.centerX * width, raw.centerY * height,
        )
        transform.mapPoints(points)
        fun nx(x: Float) = (x / viewWidth).coerceIn(0f, 1f)
        fun ny(y: Float) = (y / viewHeight).coerceIn(0f, 1f)
        val left = nx(points[0])
        val top = ny(points[1])
        val right = nx(points[2])
        val bottom = ny(points[3])
        return FaceBoxNormalized(
            centerX = nx(points[4]),
            centerY = ny(points[5]),
            left = min(left, right),
            top = min(top, bottom),
            right = max(left, right),
            bottom = max(top, bottom),
        )
    }
}
