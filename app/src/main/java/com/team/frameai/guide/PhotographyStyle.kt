package com.team.frameai.guide

/** Changes how the director reads the frame. It does not force one composition on every picture. */
enum class PhotographyStyle(val wire: String, val shortLabel: String) {
    AUTO("auto", "Auto"),
    CINEMATIC("cinematic", "Cinematic"),
    PORTRAIT("portrait", "Portrait"),
    STREET("street", "Street"),
    LANDSCAPE("landscape", "Landscape"),
    ;

    companion object {
        fun fromWire(value: String): PhotographyStyle = entries.firstOrNull { it.wire == value } ?: AUTO
    }
}
