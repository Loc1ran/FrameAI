package com.team.frameai.camera

import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import com.team.frameai.vision.FaceBoxMath
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/** Turns the current analysis frame into an upright JPEG. Does not close the image. */
object FrameJpeg {
    const val LONG_EDGE = 768
    private const val MAX_BYTES = 2 * 1024 * 1024

    fun outputSize(sensorWidth: Int, sensorHeight: Int, rotationDegrees: Int, longEdge: Int = LONG_EDGE): Pair<Int, Int> {
        val (uprightW, uprightH) = FaceBoxMath.uprightSize(sensorWidth, sensorHeight, rotationDegrees)
        val longSide = maxOf(uprightW, uprightH).coerceAtLeast(1)
        val scale = minOf(1f, longEdge.toFloat() / longSide.toFloat())
        var outW = (uprightW * scale).toInt().coerceAtLeast(2)
        var outH = (uprightH * scale).toInt().coerceAtLeast(2)
        if (outW % 2 != 0) outW -= 1
        if (outH % 2 != 0) outH -= 1
        return outW to outH
    }

    fun encode(image: ImageProxy): ByteArray? {
        if (image.format != ImageFormat.YUV_420_888 || image.planes.size < 3) return null
        if (image.width <= 1 || image.height <= 1) return null
        val rotation = image.imageInfo.rotationDegrees
        val (outW, outH) = outputSize(image.width, image.height, rotation)
        val nv21 = ByteArray(outW * outH * 3 / 2)
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]
        val y = yPlane.buffer.duplicate()
        val u = uPlane.buffer.duplicate()
        val v = vPlane.buffer.duplicate()
        val width = image.width
        val height = image.height
        for (oy in 0 until outH) {
            for (ox in 0 until outW) {
                val (sx, sy) = FaceBoxMath.uprightPointToSensor(
                    (ox + 0.5f) / outW,
                    (oy + 0.5f) / outH,
                    rotation,
                )
                nv21[oy * outW + ox] = sample(
                    y,
                    yPlane.rowStride,
                    yPlane.pixelStride,
                    (sx * width).toInt(),
                    (sy * height).toInt(),
                    width,
                    height,
                )
            }
        }
        val chromaW = (width / 2).coerceAtLeast(1)
        val chromaH = (height / 2).coerceAtLeast(1)
        var chroma = outW * outH
        for (cy in 0 until outH / 2) {
            for (cx in 0 until outW / 2) {
                val (sx, sy) = FaceBoxMath.uprightPointToSensor(
                    (cx * 2 + 1f) / outW,
                    (cy * 2 + 1f) / outH,
                    rotation,
                )
                val x = (sx * chromaW).toInt()
                val yPos = (sy * chromaH).toInt()
                nv21[chroma] = sample(v, vPlane.rowStride, vPlane.pixelStride, x, yPos, chromaW, chromaH)
                nv21[chroma + 1] = sample(u, uPlane.rowStride, uPlane.pixelStride, x, yPos, chromaW, chromaH)
                chroma += 2
            }
        }
        val jpeg = ByteArrayOutputStream()
        val wrote = YuvImage(nv21, ImageFormat.NV21, outW, outH, null)
            .compressToJpeg(Rect(0, 0, outW, outH), 80, jpeg)
        if (!wrote) return null
        val bytes = jpeg.toByteArray()
        if (bytes.size < 3 || bytes[0] != 0xFF.toByte() || bytes[1] != 0xD8.toByte()) return null
        if (bytes.size > MAX_BYTES) return null
        return bytes
    }

    private fun sample(
        buffer: ByteBuffer,
        rowStride: Int,
        pixelStride: Int,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ): Byte {
        if (width <= 0 || height <= 0 || rowStride <= 0 || pixelStride <= 0) return 0
        val xx = x.coerceIn(0, width - 1)
        val yy = y.coerceIn(0, height - 1)
        val index = yy * rowStride + xx * pixelStride
        if (index < 0 || index >= buffer.capacity()) return 0
        return buffer.get(index)
    }
}
