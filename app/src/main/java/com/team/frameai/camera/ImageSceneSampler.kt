package com.team.frameai.camera

import androidx.camera.core.ImageProxy
import com.team.frameai.model.FaceBoxNormalized
import com.team.frameai.model.SceneRead
import com.team.frameai.vision.FaceBoxMath
import java.nio.ByteBuffer

/** Reads background brightness and color from the camera frame. Does not close the image. */
object ImageSceneSampler {
    fun read(image: ImageProxy, face: FaceBoxNormalized?): SceneRead? {
        val planes = image.planes
        if (planes.isEmpty()) return null
        val yPlane = planes[0]
        val uPlane = planes.getOrNull(1)
        val vPlane = planes.getOrNull(2)
        val width = image.width
        val height = image.height
        if (width <= 1 || height <= 1) return null
        val rotation = image.imageInfo.rotationDegrees
        val y = yPlane.buffer.duplicate()
        val u = uPlane?.buffer?.duplicate()
        val v = vPlane?.buffer?.duplicate()

        var leftSum = 0f
        var leftCount = 0
        var rightSum = 0f
        var rightCount = 0
        var topSum = 0f
        var topCount = 0
        var bottomSum = 0f
        var bottomCount = 0
        val rowSum = FloatArray(GRID)
        val rowCount = IntArray(GRID)
        var warmthSum = 0f
        var warmthCount = 0
        var faceSum = 0f
        var faceCount = 0

        for (row in 0 until GRID) {
            for (col in 0 until GRID) {
                val nx = (col + 0.5f) / GRID
                val ny = (row + 0.5f) / GRID
                val (sx, sy) = FaceBoxMath.uprightPointToSensor(nx, ny, rotation)
                val luma = sample(
                    y,
                    yPlane.rowStride,
                    yPlane.pixelStride,
                    (sx * width).toInt(),
                    (sy * height).toInt(),
                    width,
                    height,
                ) ?: continue
                if (face != null && inside(nx, ny, face)) {
                    faceSum += luma
                    faceCount += 1
                    continue
                }
                if (nx < 0.5f) {
                    leftSum += luma
                    leftCount += 1
                } else {
                    rightSum += luma
                    rightCount += 1
                }
                if (ny < 0.5f) {
                    topSum += luma
                    topCount += 1
                } else {
                    bottomSum += luma
                    bottomCount += 1
                }
                rowSum[row] += luma
                rowCount[row] += 1
                if (u != null && v != null && uPlane != null && vPlane != null) {
                    val chromaX = (sx * (width / 2)).toInt()
                    val chromaY = (sy * (height / 2)).toInt()
                    val chromaWidth = (width / 2).coerceAtLeast(1)
                    val chromaHeight = (height / 2).coerceAtLeast(1)
                    val uValue = sample(u, uPlane.rowStride, uPlane.pixelStride, chromaX, chromaY, chromaWidth, chromaHeight)
                    val vValue = sample(v, vPlane.rowStride, vPlane.pixelStride, chromaX, chromaY, chromaWidth, chromaHeight)
                    if (uValue != null && vValue != null) {
                        warmthSum += vValue - uValue
                        warmthCount += 1
                    }
                }
            }
        }
        if (leftCount < 4 || rightCount < 4) return null
        val warmth = if (warmthCount == 0) 0f else (warmthSum / warmthCount)
        return SceneRead(
            leftLuma = leftSum / leftCount,
            rightLuma = rightSum / rightCount,
            warmth = warmth,
            topLuma = if (topCount == 0) 0.5f else topSum / topCount,
            bottomLuma = if (bottomCount == 0) 0.5f else bottomSum / bottomCount,
            horizonY = horizonY(rowSum, rowCount),
            subjectLuma = if (faceCount < 2) null else faceSum / faceCount,
        )
    }

    private fun horizonY(rowSum: FloatArray, rowCount: IntArray): Float? {
        var best = 0f
        var at = -1
        for (row in 0 until GRID - 1) {
            if (rowCount[row] < 2 || rowCount[row + 1] < 2) continue
            val above = rowSum[row] / rowCount[row]
            val below = rowSum[row + 1] / rowCount[row + 1]
            val strength = kotlin.math.abs(above - below)
            if (strength > best) {
                best = strength
                at = row
            }
        }
        if (at < 0 || best < HORIZON_DROP) return null
        return (at + 1f) / GRID
    }

    private fun inside(nx: Float, ny: Float, face: FaceBoxNormalized): Boolean {
        return nx in face.left..face.right && ny in face.top..face.bottom
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

    private const val GRID = 8
    private const val HORIZON_DROP = 0.18f
}
