package com.team.frameai.lut

import android.content.res.AssetManager
import android.graphics.Bitmap
import com.team.frameai.model.LutIds

/**
 * Still-photo grade for the review screen. Call it off the main thread.
 * The source bitmap is not modified and is not recycled.
 * Images larger than [PREVIEW_MAX_EDGE] are graded at that long edge.
 * Compiled by the Android app, not by the brain unit-test module.
 */
class BitmapLutRepository(
    private val repository: LutRepository,
) {
    fun apply(
        source: Bitmap,
        lutId: String,
        intensity: Float = LutRepository.DEFAULT_INTENSITY,
    ): Bitmap {
        val readable = ensureArgb(source)
        val working = downscale(readable)
        try {
            val argb = IntArray(working.width * working.height)
            working.getPixels(argb, 0, working.width, 0, 0, working.width, working.height)
            val graded = repository.apply(
                RgbImage(working.width, working.height, argb),
                lutId,
                intensity,
            )
            val output = Bitmap.createBitmap(working.width, working.height, Bitmap.Config.ARGB_8888)
            output.setPixels(graded.pixels, 0, working.width, 0, 0, working.width, working.height)
            return output
        } finally {
            if (working !== readable && working !== source) working.recycle()
            if (readable !== source) readable.recycle()
        }
    }

    private fun ensureArgb(source: Bitmap): Bitmap {
        if (source.config == Bitmap.Config.ARGB_8888) return source
        return source.copy(Bitmap.Config.ARGB_8888, false)
            ?: throw IllegalArgumentException("Could not read the captured bitmap")
    }

    private fun downscale(source: Bitmap): Bitmap {
        val edge = maxOf(source.width, source.height)
        if (edge <= PREVIEW_MAX_EDGE) return source
        val scale = PREVIEW_MAX_EDGE.toFloat() / edge.toFloat()
        val width = maxOf(1, (source.width * scale).toInt())
        val height = maxOf(1, (source.height * scale).toInt())
        return Bitmap.createScaledBitmap(source, width, height, true)
    }

    companion object {
        const val PREVIEW_MAX_EDGE = 1024

        fun fromAssets(assets: AssetManager): BitmapLutRepository {
            val ids = assets.list("luts").orEmpty()
                .filter { it.endsWith(".cube") }
                .map { it.removeSuffix(".cube") }
                .filter { it != "identity_4" }
            require(LutIds.WARM_FILM in ids && LutIds.COOL_CINEMA in ids) {
                "Bundled warm_film and cool_cinema LUTs are missing"
            }
            val loaders = ids.associateWith<String, () -> CubeLut> { id ->
                {
                    assets.open("luts/$id.cube").bufferedReader().use { reader ->
                        parseCube(reader.readText())
                    }
                }
            }
            return BitmapLutRepository(LutRepository(loaders = loaders))
        }
    }
}
