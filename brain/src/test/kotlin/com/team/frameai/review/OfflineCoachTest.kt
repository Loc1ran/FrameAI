package com.team.frameai.review

import com.team.frameai.guide.GuideEngine
import com.team.frameai.model.AdviceSource
import com.team.frameai.model.FaceBoxNormalized
import com.team.frameai.model.LutIds
import com.team.frameai.model.ShotReview
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OfflineCoachTest {
    private val engine = GuideEngine()

    @Test
    fun everyStateIsLabeledOfflineAndUsesAKnownLut() {
        val guides = listOf(
            engine.suggest(null),
            engine.suggest(FaceBoxNormalized(0.4f, 0.4f, 0.5f, 0.5f)),
            engine.suggest(FaceBoxNormalized(0.66f, 0.32f, 0.68f, 0.34f)),
        )
        for (guide in guides) {
            val review = OfflineCoach.review(guide)
            assertEquals(AdviceSource.OFFLINE_RULES, review.source)
            assertTrue(review.suggestedLutId in LutIds.SUGGESTABLE)
            assertTrue(review.sceneType in ShotReview.SCENE_TYPES)
            assertTrue(review.summary.contains("Offline tip"))
            assertTrue(review.summary.length <= 200)
            assertTrue(review.improvement.length <= 200)
        }
    }
}
