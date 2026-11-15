package com.team.frameai.ui

import com.team.frameai.model.LiveGuide
import com.team.frameai.model.ShotReview
import com.team.frameai.review.OfflineCoach

internal fun reviewAtCapture(guide: LiveGuide?, fallback: ShotReview): ShotReview {
    val realGuide = guide?.takeIf { it.source == "offline_rules" || it.source == "gemini" }
    val usable = when (realGuide?.kind) {
        "NO_FACE" -> realGuide.subjectX == null && realGuide.subjectY == null && !realGuide.isReady
        "ALIGN_SUBJECT", "READY" ->
            realGuide.subjectX?.let { it.isFinite() && it in 0.0..1.0 } == true &&
                realGuide.subjectY?.let { it.isFinite() && it in 0.0..1.0 } == true
        else -> false
    }
    if (!usable || realGuide == null) {
        return fallback.copy(summary = "General composition tip — no reliable framing data was saved.")
    }
    val review = OfflineCoach.review(realGuide)
    return review.copy(
        sceneType = "other",
        principle = "subject placement",
        summary = "Framing at capture: " + review.summary.removePrefix("Offline tip — "),
        improvement = if (realGuide.isReady) "The subject reached the center guide. Next time, also check the background and lighting."
            else "For a centered photo, follow the phone direction until the white subject dot reaches the gold guide. Turn Guide off to frame freely.",
        lutReason = "Compare both looks on this photo and choose the color you prefer.",
    )
}
