package com.team.frameai.guide

import com.team.frameai.frameFile
import com.team.frameai.model.AdviceSource
import com.team.frameai.model.FaceBoxNormalized
import com.team.frameai.model.GuideKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class GuideEngineTest {
    private val engine = GuideEngine()

    @Test
    fun noFaceReturnsTheFrozenMessage() {
        val guide = engine.suggest(null)
        assertEquals(GuideKind.NO_FACE, guide.kind)
        assertEquals(GuideEngine.NO_FACE_MESSAGE, guide.message)
        assertNull(guide.subjectX)
        assertNull(guide.subjectY)
        assertFalse(guide.isReady)
        assertEquals(AdviceSource.OFFLINE_RULES, guide.source)
        assertEquals(GuideEngine.DEFAULT_TARGET_X, guide.targetX)
        assertEquals(GuideEngine.DEFAULT_TARGET_Y, guide.targetY)
    }

    @Test
    fun centeredFaceIsReady() {
        val guide = engine.suggest(boxAt(0.67f, 0.33f))
        assertEquals(GuideKind.READY, guide.kind)
        assertEquals(GuideEngine.READY_MESSAGE, guide.message)
        assertTrue(guide.isReady)
        assertEquals(0.67f, guide.subjectX!!, 0.0001f)
        assertEquals(0.33f, guide.subjectY!!, 0.0001f)
        assertEquals(AdviceSource.OFFLINE_RULES, guide.source)
    }

    @Test
    fun toleranceBoundaryStaysReady() {
        val onEdge = engine.suggest(
            boxAt(
                GuideEngine.DEFAULT_TARGET_X + GuideEngine.DEFAULT_TOLERANCE_X,
                GuideEngine.DEFAULT_TARGET_Y + GuideEngine.DEFAULT_TOLERANCE_Y,
            ),
        )
        assertEquals(GuideKind.READY, onEdge.kind)
        val pastX = engine.suggest(
            boxAt(GuideEngine.DEFAULT_TARGET_X + 0.09f, GuideEngine.DEFAULT_TARGET_Y),
        )
        val pastY = engine.suggest(
            boxAt(GuideEngine.DEFAULT_TARGET_X, GuideEngine.DEFAULT_TARGET_Y + 0.11f),
        )
        assertEquals(GuideKind.ALIGN_SUBJECT, pastX.kind)
        assertEquals(GuideKind.ALIGN_SUBJECT, pastY.kind)
    }

    @Test
    fun offCenterFaceAlignsWithoutADirection() {
        val guide = engine.suggest(boxAt(0.48f, 0.39f), steady = false)
        assertEquals(GuideKind.ALIGN_SUBJECT, guide.kind)
        assertEquals(GuideEngine.ALIGN_MESSAGE, guide.message)
        assertFalse(guide.isReady)
        assertFalse(guide.message.contains("left", ignoreCase = true))
        assertFalse(guide.message.contains("right", ignoreCase = true))
        assertEquals(engine.suggest(boxAt(0.48f, 0.39f), steady = true).kind, guide.kind)
    }

    @Test
    fun frozenMockCentersFollowTheSameRules() {
        assertEquals(GuideKind.READY, engine.suggest(boxAt(0.66f, 0.34f)).kind)
        assertEquals(GuideKind.ALIGN_SUBJECT, engine.suggest(boxAt(0.48f, 0.39f)).kind)
        val align = frameFile("contracts/live-guide.mock.json").readText()
        val ready = frameFile("contracts/live-guide.ready.mock.json").readText()
        val noFace = frameFile("contracts/live-guide.noface.mock.json").readText()
        assertTrue(align.contains(GuideEngine.ALIGN_MESSAGE))
        assertTrue(ready.contains(GuideEngine.READY_MESSAGE))
        assertTrue(noFace.contains(GuideEngine.NO_FACE_MESSAGE))
        assertTrue(frameFile("contracts/live-guide.schema.json").readText().contains("offline_rules"))
    }

    @Test
    fun smootherThrottlesSameKindAndReleasesOnNoFace() {
        val smoother = GuideSmoother(minIntervalMs = 250, smoothing = 1f)
        val first = smoother.update(boxAt(0.20f, 0.20f), nowMs = 0)
        val second = smoother.update(boxAt(0.40f, 0.40f), nowMs = 100)
        assertSame(first, second)
        val lost = smoother.update(null, nowMs = 120)
        assertEquals(GuideKind.NO_FACE, lost.kind)
        val moved = GuideSmoother(minIntervalMs = 250, smoothing = 1f)
        moved.update(boxAt(0.20f, 0.20f), nowMs = 0)
        val later = moved.update(boxAt(0.40f, 0.40f), nowMs = 250)
        assertEquals(0.40f, later.subjectX!!, 0.0001f)
    }

    @Test
    fun smootherEasesTheBox() {
        val smoother = GuideSmoother(minIntervalMs = 0, smoothing = 0.5f)
        smoother.update(boxAt(0.20f, 0.50f), nowMs = 0)
        val next = smoother.update(boxAt(0.40f, 0.50f), nowMs = 0)
        assertEquals(0.30f, next.subjectX!!, 0.0001f)
    }

    private fun boxAt(centerX: Float, centerY: Float): FaceBoxNormalized {
        return FaceBoxNormalized(centerX, centerY, centerX, centerY)
    }
}
