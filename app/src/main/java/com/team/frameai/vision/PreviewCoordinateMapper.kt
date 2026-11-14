package com.team.frameai.vision

import com.team.frameai.model.FaceBoxNormalized
import kotlin.math.max

/**
 * PreviewView center-crop, which is ScaleType.FILL_CENTER.
 * Analysis width and height are the upright frame, not the raw sensor buffer when rotation is 90 or 270.
 * [PreviewMapping.mirrorX] is for a front camera. The rear-camera demo leaves it false.
 */
data class PreviewMapping(
    val analysisWidth: Int,
    val analysisHeight: Int,
    val viewWidth: Int,
    val viewHeight: Int,
    val mirrorX: Boolean = false,
)

object PreviewCoordinateMapper {
    fun mapPoint(nx: Float, ny: Float, mapping: PreviewMapping): Pair<Float, Float> {
        require(mapping.analysisWidth > 0 && mapping.analysisHeight > 0) {
            "Analysis size must be positive"
        }
        require(mapping.viewWidth > 0 && mapping.viewHeight > 0) {
            "PreviewView size must be positive"
        }
        val analysisWidth = mapping.analysisWidth.toFloat()
        val analysisHeight = mapping.analysisHeight.toFloat()
        val viewWidth = mapping.viewWidth.toFloat()
        val viewHeight = mapping.viewHeight.toFloat()
        val scale = max(viewWidth / analysisWidth, viewHeight / analysisHeight)
        val offsetX = (analysisWidth * scale - viewWidth) / 2f
        val offsetY = (analysisHeight * scale - viewHeight) / 2f
        var previewX = (nx * analysisWidth * scale - offsetX) / viewWidth
        val previewY = (ny * analysisHeight * scale - offsetY) / viewHeight
        if (mapping.mirrorX) {
            previewX = 1f - previewX
        }
        return previewX to previewY
    }

    fun mapBox(box: FaceBoxNormalized, mapping: PreviewMapping): FaceBoxNormalized? {
        val corners = arrayOf(
            box.left to box.top,
            box.right to box.top,
            box.right to box.bottom,
            box.left to box.bottom,
        )
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        for ((x, y) in corners) {
            val (previewX, previewY) = mapPoint(x, y, mapping)
            minX = minOf(minX, previewX)
            minY = minOf(minY, previewY)
            maxX = maxOf(maxX, previewX)
            maxY = maxOf(maxY, previewY)
        }
        return FaceBoxNormalized.clamped(minX, minY, maxX, maxY)
    }
}
