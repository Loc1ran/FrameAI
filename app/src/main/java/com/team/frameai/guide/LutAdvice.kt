package com.team.frameai.guide

import com.team.frameai.lut.LutPack
import com.team.frameai.model.LutIds

/**
 * A suggested grade has to be a phone look that is already on the device.
 * Strong casts are skipped for portraits so skin is not pushed pink or blue.
 */
object LutAdvice {
    private val SKIN_RISK = setOf(
        "eastman_kodak_7355_pink_violet",
        "filmtec_101_blue",
        "filmtec_102_by",
        "kodak_filmsat_t101",
    )

    fun accept(id: String, sceneType: String): String? {
        if (id == LutIds.NONE) return null
        val look = LutPack.looks.firstOrNull { it.id == id } ?: return null
        if (!look.phoneJpeg) return null
        if (sceneType == "portrait" && id in SKIN_RISK) return null
        return id
    }

    fun displayName(id: String): String? = LutPack.looks.firstOrNull { it.id == id }?.name
}
