package com.team.frameai.camera

import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import androidx.camera.view.TransformExperimental
import androidx.camera.view.transform.CoordinateTransform
import androidx.camera.view.transform.ImageProxyTransformFactory
import com.team.frameai.model.FaceBoxNormalized
import com.team.frameai.model.RawFaceBox
import kotlin.math.max
import kotlin.math.min

object AnalysisCoordinateMapper {
    @OptIn(TransformExperimental::class)
    fun map(image: ImageProxy, preview: PreviewView, raw: RawFaceBox): FaceBoxNormalized? {
        if (preview.width == 0 || preview.height == 0) return null
        val target = preview.outputTransform ?: return null
        val source = ImageProxyTransformFactory().apply {
            isUsingRotationDegrees = true
        }.getOutputTransform(image)
        val transform = CoordinateTransform(source, target)
        val width = image.width.toFloat()
        val height = image.height.toFloat()
        val points = floatArrayOf(
            raw.left * width, raw.top * height,
            raw.right * width, raw.bottom * height,
            raw.centerX * width, raw.centerY * height,
        )
        transform.mapPoints(points)
        val viewWidth = preview.width.toFloat()
        val viewHeight = preview.height.toFloat()
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
