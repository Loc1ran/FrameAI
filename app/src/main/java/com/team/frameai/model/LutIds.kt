package com.team.frameai.model

object LutIds {
    const val WARM_FILM = "warm_film"
    const val COOL_CINEMA = "cool_cinema"
    const val NONE = "none"

    val CREATIVE = setOf(WARM_FILM, COOL_CINEMA)
    val SUGGESTABLE = setOf(WARM_FILM, COOL_CINEMA, NONE)
}
