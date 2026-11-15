package com.team.frameai.ui

import com.team.frameai.model.FaceBoxNormalized
import kotlin.math.abs

data class FramingHint(val title: String, val detail: String, val symbol: String, val aligned: Boolean = false)

/** Phone motion is opposite subject motion in the displayed rear-camera preview. */
fun framingHint(area: FaceBoxNormalized?): FramingHint {
    if (area == null || !area.centerX.isFinite() || !area.centerY.isFinite()) {
        return FramingHint("Find your subject", "Keep one person or distinct object near the center. Hold briefly while the white outline appears.", "◎")
    }
    if (area.left <= 0.02f || area.top <= 0.02f || area.right >= 0.98f || area.bottom >= 0.98f) {
        return FramingHint("Give your subject room", "The tracked subject touches an edge. Zoom out or move farther away until it fits.", "↔")
    }
    val dx = area.centerX - 0.5f
    val dy = area.centerY - 0.5f
    if (abs(dx) <= TARGET_RADIUS && abs(dy) <= TARGET_RADIUS) {
        return FramingHint("Position aligned", "The white dot is in the target. Hold the phone steady and take your photo.", "✓", true)
    }
    return if (abs(dx) >= abs(dy)) {
        if (dx > 0) FramingHint("Pan phone right", "Turn the phone slowly right. The subject will slide left toward the gold target.", "→")
        else FramingHint("Pan phone left", "Turn the phone slowly left. The subject will slide right toward the gold target.", "←")
    } else {
        if (dy > 0) FramingHint("Tilt phone down", "Angle the phone slowly down. The subject will move up toward the gold target.", "↓")
        else FramingHint("Tilt phone up", "Angle the phone slowly up. The subject will move down toward the gold target.", "↑")
    }
}

const val TARGET_RADIUS = 0.065f
