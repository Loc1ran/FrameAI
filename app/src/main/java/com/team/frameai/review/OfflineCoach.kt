package com.team.frameai.review

import com.team.frameai.guide.GuideEngine
import com.team.frameai.model.AdviceSource
import com.team.frameai.model.LiveGuide
import com.team.frameai.model.LutIds
import com.team.frameai.model.ShotReview

/**
 * Labeled local lesson used when the phone cannot reach the review server.
 * [ShotReview.source] is always `offline_rules`.
 */
object OfflineCoach {
    fun review(guide: LiveGuide): ShotReview {
        return when (guide.kind) {
            GuideEngine.KIND_NO_FACE -> ShotReview(
                sceneType = "other",
                summary = "Offline tip — the frame has no person, and it can still be a picture.",
                improvement = "There is no clear subject to place yet.",
                principle = "Point at one subject, then use the grid.",
                suggestedLutId = LutIds.WARM_FILM,
                lutReason = "Warm Film is a safe default until a subject is in frame.",
                source = AdviceSource.OFFLINE_RULES.wire,
            )
            GuideEngine.KIND_READY -> ShotReview(
                sceneType = "portrait",
                summary = "Offline tip — the current frame matches the goal.",
                improvement = "The placement already works. Check that the light on the subject is even.",
                principle = "Hold this angle, then look at the light.",
                suggestedLutId = LutIds.WARM_FILM,
                lutReason = "Warm Film fits a portrait when the framing is ready.",
                source = AdviceSource.OFFLINE_RULES.wire,
            )
            else -> ShotReview(
                sceneType = "portrait",
                summary = "Offline tip — the current frame does not match the goal yet.",
                improvement = "The subject is not on the marked goal yet.",
                principle = "Move until the subject sits on the marked third.",
                suggestedLutId = LutIds.WARM_FILM,
                lutReason = "Warm Film keeps portrait skin tones natural.",
                source = AdviceSource.OFFLINE_RULES.wire,
            )
        }
    }
}
