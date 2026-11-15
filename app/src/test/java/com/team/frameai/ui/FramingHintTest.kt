package com.team.frameai.ui

import com.team.frameai.guide.CoachAction
import com.team.frameai.guide.GuideEngine
import com.team.frameai.model.FaceBoxNormalized
import com.team.frameai.model.LiveGuide
import org.junit.Assert.*
import org.junit.Test

class FramingHintTest {
    @Test fun noDetectionNeverInventsAReadySubject() {
        assertEquals("Find your subject", framingHint(null).title)
        assertFalse(framingHint(null).aligned)
    }

    @Test fun directionsFollowTheRecommendedTarget() {
        assertEquals("→", framingHint(guide(0.33, 0.33, 0.80f, 0.50f, CoachAction.MOVE_RIGHT)).symbol)
        assertEquals("←", framingHint(guide(0.67, 0.33, 0.20f, 0.50f, CoachAction.MOVE_LEFT)).symbol)
        assertEquals("↓", framingHint(guide(0.67, 0.33, 0.67f, 0.80f, CoachAction.MOVE_DOWN)).symbol)
        assertEquals("↑", framingHint(guide(0.67, 0.67, 0.67f, 0.20f, CoachAction.MOVE_UP)).symbol)
    }

    @Test fun readinessUsesTheGuideInsteadOfTheCenter() {
        val onThird = guide(0.67, 0.33, 0.67f, 0.33f, GuideEngine.GREAT_CUE, ready = true)
        assertTrue(framingHint(onThird).aligned)
        assertEquals(GuideEngine.GREAT_CUE, framingHint(onThird).title)
        val center = guide(0.33, 0.33, 0.50f, 0.50f, CoachAction.MOVE_RIGHT)
        assertFalse(framingHint(center).aligned)
        assertEquals(CoachAction.MOVE_RIGHT, framingHint(center).title)
    }

    @Test fun aViewpointCueUsesItsOwnDetailAndSymbol() {
        val hint = framingHint(
            guide(0.5, 0.33, 0.5f, 0.33f, "Take one step to your right.").copy(
                detail = "That moves the pole out from behind their head.",
                hideGoal = true,
                cueSymbol = "⇄",
            ),
        )
        assertEquals("Take one step to your right.", hint.title)
        assertEquals("That moves the pole out from behind their head.", hint.detail)
        assertEquals("⇄", hint.symbol)
        assertFalse(hint.aligned)
    }

    @Test fun clippedSubjectUsesTheGuideMessage() {
        val edge = FaceBoxNormalized(0.12f, 0.5f, 0f, 0.3f, 0.24f, 0.7f)
        val hint = framingHint(guide(0.33, 0.33, 0.12f, 0.5f, GuideEngine.BREATHING_CUE), edge)
        assertEquals(GuideEngine.BREATHING_CUE, hint.title)
        assertFalse(hint.aligned)
    }

    private fun guide(
        targetX: Double,
        targetY: Double,
        subjectX: Float,
        subjectY: Float,
        message: String,
        ready: Boolean = false,
    ) = LiveGuide(
        kind = if (ready) GuideEngine.KIND_READY else GuideEngine.KIND_ALIGN,
        message = message,
        targetX = targetX,
        targetY = targetY,
        subjectX = subjectX.toDouble(),
        subjectY = subjectY.toDouble(),
        isReady = ready,
        source = "offline_rules",
    )
}
