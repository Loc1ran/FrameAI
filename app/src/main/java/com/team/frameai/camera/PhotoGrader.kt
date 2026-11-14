package com.team.frameai.camera

import android.graphics.Bitmap

fun interface PhotoGrader {
    /**
     * Grade a copy of [source]. Implementations must leave [source] unchanged.
     * [lutId] is `warm_film` or `cool_cinema`. [intensity] is 0..1.
     */
    fun apply(source: Bitmap, lutId: String, intensity: Float): Bitmap
}
