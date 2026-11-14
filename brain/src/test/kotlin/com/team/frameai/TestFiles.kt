package com.team.frameai

import java.io.File

fun frameFile(relative: String): File {
    val candidates = listOf(
        File(relative),
        File("..").resolve(relative),
        File("../..").resolve(relative),
    )
    return candidates.firstOrNull { it.exists() }
        ?: error("Could not find $relative from ${File(".").absolutePath}")
}
