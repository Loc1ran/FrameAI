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
                improvement = "Match the solid frame to the dashed goal, then take the photo.",
                principle = "rule of thirds",
                suggestedLutId = LutIds.WARM_FILM,
                lutReason = "Warm Film is a safe default until a subject is in frame.",
                source = AdviceSource.OFFLINE_RULES.wire,
            )
            GuideEngine.KIND_READY -> ShotReview(
                sceneType = "portrait",
                summary = "Offline tip — the current frame matches the goal.",
                improvement = "Hold this frame and take the photo.",
                principle = "rule of thirds",
                suggestedLutId = LutIds.WARM_FILM,
                lutReason = "Warm Film fits a portrait when the framing is ready.",
                source = AdviceSource.OFFLINE_RULES.wire,
            )
            else -> ShotReview(
                sceneType = "portrait",
                summary = "Offline tip — the current frame does not match the goal yet.",
                improvement = "Move until the solid frame sits on the dashed goal, then take the photo.",
                principle = "rule of thirds",
                suggestedLutId = LutIds.WARM_FILM,
                lutReason = "Warm Film keeps portrait skin tones natural.",
                source = AdviceSource.OFFLINE_RULES.wire,
            )
        }
    }
}
