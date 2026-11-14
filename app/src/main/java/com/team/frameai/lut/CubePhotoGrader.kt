package com.team.frameai.lut

import android.graphics.Bitmap
import com.team.frameai.camera.PhotoGrader

/** Real .cube grade for the review screen. The original bitmap is not modified. */
class CubePhotoGrader(
    private val repository: BitmapLutRepository,
) : PhotoGrader {
    override fun apply(source: Bitmap, lutId: String, intensity: Float): Bitmap {
        return try {
            repository.apply(source, lutId, intensity)
        } catch (error: RuntimeException) {
            source.copy(Bitmap.Config.ARGB_8888, false) ?: source
        }
    }
}
