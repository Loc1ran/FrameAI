package com.team.frameai.review

import com.team.frameai.model.AdviceSource
import com.team.frameai.model.GuideKind
import com.team.frameai.model.LiveGuide
import com.team.frameai.model.LutIds
import com.team.frameai.model.ShotReview

/**
 * Labeled local lesson used when the phone cannot reach the review server.
 * [ShotReview.source] is always [AdviceSource.OFFLINE_RULES].
 */
object OfflineCoach {
    fun review(guide: LiveGuide): ShotReview {
        return when (guide.kind) {
            GuideKind.NO_FACE -> ShotReview(
                sceneType = "other",
                summary = "Offline tip — no face is in the frame.",
                improvement = "Use the grid to place your subject near a thirds intersection.",
                principle = "rule of thirds",
                suggestedLutId = LutIds.WARM_FILM,
                lutReason = "Warm Film is a safe default until a subject is in frame.",
                source = AdviceSource.OFFLINE_RULES,
            )
            GuideKind.ALIGN_SUBJECT -> ShotReview(
                sceneType = "portrait",
                summary = "Offline tip — the face is away from the target.",
                improvement = "Align the face with the target circle, then take the photo.",
                principle = "rule of thirds",
                suggestedLutId = LutIds.WARM_FILM,
                lutReason = "Warm Film keeps portrait skin tones natural.",
                source = AdviceSource.OFFLINE_RULES,
            )
            GuideKind.READY -> ShotReview(
                sceneType = "portrait",
                summary = "Offline tip — the face is near the portrait target.",
                improvement = "Hold this frame and leave a little open space opposite the face.",
                principle = "rule of thirds",
                suggestedLutId = LutIds.WARM_FILM,
                lutReason = "Warm Film fits a portrait when the framing is ready.",
                source = AdviceSource.OFFLINE_RULES,
            )
        }
    }
}
