package com.team.frameai.model

import com.team.frameai.lut.LutPack

object LutIds {
    const val WARM_FILM = "warm_film"
    const val COOL_CINEMA = "cool_cinema"
    const val NONE = "none"

    val CREATIVE = setOf(WARM_FILM, COOL_CINEMA)

    /** Every bundled look, plus none. */
    val SUGGESTABLE: Set<String>
        get() = LutPack.ids + NONE
}
