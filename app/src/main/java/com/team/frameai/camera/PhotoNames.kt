package com.team.frameai.camera

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PhotoNames {
    fun timestamp(now: Date = Date()): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(now)

    fun original(stamp: String): String = "photo_${safe(stamp)}_original.jpg"

    fun edited(stamp: String, lutId: String): String = "photo_${safe(stamp)}_${safe(lutId)}.jpg"

    private fun safe(value: String): String =
        value.lowercase(Locale.US).replace(Regex("[^a-z0-9_]+"), "_").trim('_').ifEmpty { "shot" }
}
