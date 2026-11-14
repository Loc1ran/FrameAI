package com.team.frameai.lut

import android.graphics.Bitmap
import com.team.frameai.camera.PhotoGrader

/**
 * Real .cube grade for the review screen. The original bitmap is not modified.
 * A missing or unreadable look throws. Callers must not present that failure as an edit.
 */
class CubePhotoGrader(
    private val grade: (Bitmap, String, Float) -> Bitmap,
) : PhotoGrader {
    constructor(repository: BitmapLutRepository) : this({ source, lutId, intensity ->
        repository.apply(source, lutId, intensity)
    })

    override fun apply(source: Bitmap, lutId: String, intensity: Float): Bitmap {
        return grade(source, lutId, intensity)
    }
}
