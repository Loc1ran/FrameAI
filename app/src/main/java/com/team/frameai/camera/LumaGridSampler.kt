package com.team.frameai.camera

import androidx.camera.core.ImageProxy
import com.team.frameai.guide.GeometryMeasure
import com.team.frameai.vision.FaceBoxMath
import java.nio.ByteBuffer

/** Samples an upright luma grid for edge and symmetry measurements. Does not close the image. */
object LumaGridSampler {
    fun sample(image: ImageProxy): FloatArray? {
        val planes = image.planes
        if (planes.isEmpty()) return null
        val width = image.width
        val height = image.height
        if (width <= 1 || height <= 1) return null
        val yPlane = planes[0]
        val rotation = image.imageInfo.rotationDegrees
        val buffer = yPlane.buffer.duplicate()
        val grid = FloatArray(GeometryMeasure.COLUMNS * GeometryMeasure.ROWS)
        for (row in 0 until GeometryMeasure.ROWS) {
            for (col in 0 until GeometryMeasure.COLUMNS) {
                val nx = (col + 0.5f) / GeometryMeasure.COLUMNS
                val ny = (row + 0.5f) / GeometryMeasure.ROWS
                val (sx, sy) = FaceBoxMath.uprightPointToSensor(nx, ny, rotation)
                val luma = sample(
                    buffer,
                    yPlane.rowStride,
                    yPlane.pixelStride,
                    (sx * width).toInt(),
                    (sy * height).toInt(),
                    width,
                    height,
                ) ?: return null
                grid[row * GeometryMeasure.COLUMNS + col] = luma
            }
        }
        return grid
    }

    private fun sample(
        buffer: ByteBuffer,
        rowStride: Int,
        pixelStride: Int,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ): Float? {
        if (width <= 0 || height <= 0 || rowStride <= 0 || pixelStride <= 0) return null
        val xx = x.coerceIn(0, width - 1)
        val yy = y.coerceIn(0, height - 1)
        val index = yy * rowStride + xx * pixelStride
        if (index < 0 || index >= buffer.capacity()) return null
        return (buffer.get(index).toInt() and 0xFF) / 255f
    }
}
