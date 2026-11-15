package com.team.frameai.guide

/** The photo shape shown in the preview. Full uses whatever space the phone is giving the camera. */
enum class FrameRatio(val label: String) {
    FULL("Full"),
    FOUR_THREE("4:3"),
    SIXTEEN_NINE("16:9"),
    SQUARE("1:1");

    /** Width divided by height. Portrait 4:3 is taller than it is wide. */
    fun previewAspect(landscape: Boolean): Float? = when (this) {
        FULL -> null
        SQUARE -> 1f
        FOUR_THREE -> if (landscape) 4f / 3f else 3f / 4f
        SIXTEEN_NINE -> if (landscape) 16f / 9f else 9f / 16f
    }
}
