package com.team.frameai.lut

import com.team.frameai.model.LutIds

/**
 * The two phone-photo looks the review screen can apply.
 * Other files under assets/luts are not selectable. Several are camera-log conversions
 * and must not be applied to a normal phone JPEG.
 */
object LutPack {
    data class Look(val id: String, val name: String, val sourceAsset: String)

    val looks: List<Look> = listOf(
        Look(LutIds.WARM_FILM, "Warm Film", "cinematic_04.cube"),
        Look(LutIds.COOL_CINEMA, "Cool Cinema", "cinematic_08.cube"),
    )

    val ids: Set<String> = looks.map { it.id }.toSet()

    /** Strength the review screen passes to the grader. */
    const val REVIEW_INTENSITY = 0.85f
}
