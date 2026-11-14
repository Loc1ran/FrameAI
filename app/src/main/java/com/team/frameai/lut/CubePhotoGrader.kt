package com.team.frameai.lut

import android.graphics.Bitmap
import com.team.frameai.camera.PhotoGrader

/**
 * Real .cube grade for the review screen. The original bitmap is not modified.
 * Unknown ids and malformed cubes throw. A failure is not returned as a copy of the original.
 */
class CubePhotoGrader(
    private val repository: BitmapLutRepository,
) : PhotoGrader {
    override fun apply(source: Bitmap, lutId: String, intensity: Float): Bitmap {
        return repository.apply(source, lutId, intensity)
    }
}
