package com.team.frameai.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

/**
 * On-device color preview used until Developer B's LutRepository is wired in MainActivity.
 * This is a color matrix, not a .cube LUT.
 */
class FallbackPhotoGrader : PhotoGrader {
    override fun apply(source: Bitmap, lutId: String, intensity: Float): Bitmap {
        val matrix = when (lutId) {
            "warm_film" -> WARM
            "cool_cinema" -> COOL
            else -> return source.copy(Bitmap.Config.ARGB_8888, false)
        }
        val amount = intensity.coerceIn(0f, 1f)
        val graded = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        Canvas(graded).drawBitmap(
            source,
            0f,
            0f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { colorFilter = ColorMatrixColorFilter(matrix) },
        )
        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        Canvas(result).drawBitmap(
            graded,
            0f,
            0f,
            Paint().apply { alpha = (amount * 255f).toInt().coerceIn(0, 255) },
        )
        graded.recycle()
        return result
    }

    private companion object {
        val WARM = ColorMatrix(
            floatArrayOf(
                1.14f, 0.04f, 0f, 0f, 12f,
                0.02f, 1.04f, 0f, 0f, 4f,
                0f, 0.01f, 0.86f, 0f, -4f,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
        val COOL = ColorMatrix(
            floatArrayOf(
                0.90f, 0f, 0.03f, 0f, 0f,
                0f, 1.03f, 0.02f, 0f, 2f,
                0.02f, 0.05f, 1.16f, 0f, 10f,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
    }
}
