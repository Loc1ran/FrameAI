package com.team.frameai.guide

import com.team.frameai.frameFile
import com.team.frameai.model.AdviceSource
import com.team.frameai.model.FaceBoxNormalized
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
        assertEquals(GuideEngine.KIND_NO_FACE, guide.kind)
        assertEquals(GuideEngine.NO_FACE_MESSAGE, guide.message)
        assertNull(guide.subjectX)
        assertNull(guide.subjectY)
        assertFalse(guide.isReady)
        assertEquals(AdviceSource.OFFLINE_RULES.wire, guide.source)
        assertEquals(GuideEngine.DEFAULT_TARGET_X.toDouble(), guide.targetX, 0.0001)
        assertEquals(GuideEngine.DEFAULT_TARGET_Y.toDouble(), guide.targetY, 0.0001)
    }

    @Test
    fun centeredFaceIsReady() {
        val guide = engine.suggest(boxAt(0.67f, 0.33f))
        assertEquals(GuideEngine.KIND_READY, guide.kind)
        assertEquals(GuideEngine.READY_MESSAGE, guide.message)
        assertTrue(guide.isReady)
        assertEquals(0.67, guide.subjectX!!, 0.0001)
        assertEquals(0.33, guide.subjectY!!, 0.0001)
        assertEquals(AdviceSource.OFFLINE_RULES.wire, guide.source)
    }

    @Test
    fun toleranceBoundaryStaysReady() {
        val onEdge = engine.suggest(
            boxAt(
                GuideEngine.DEFAULT_TARGET_X + GuideEngine.DEFAULT_TOLERANCE_X,
                GuideEngine.DEFAULT_TARGET_Y + GuideEngine.DEFAULT_TOLERANCE_Y,
            ),
        )
        assertEquals(GuideEngine.KIND_READY, onEdge.kind)
        val pastX = engine.suggest(
            boxAt(GuideEngine.DEFAULT_TARGET_X + 0.09f, GuideEngine.DEFAULT_TARGET_Y),
        )
        val pastY = engine.suggest(
            boxAt(GuideEngine.DEFAULT_TARGET_X, GuideEngine.DEFAULT_TARGET_Y + 0.11f),
        )
        assertEquals(GuideEngine.KIND_ALIGN, pastX.kind)
        assertEquals(GuideEngine.KIND_ALIGN, pastY.kind)
    }

    @Test
    fun offCenterFaceAlignsWithoutADirection() {
        val guide = engine.suggest(boxAt(0.48f, 0.39f), steady = false)
        assertEquals(GuideEngine.KIND_ALIGN, guide.kind)
        assertEquals(GuideEngine.ALIGN_MESSAGE, guide.message)
        assertFalse(guide.isReady)
        assertFalse(guide.message.contains("left", ignoreCase = true))
        assertFalse(guide.message.contains("right", ignoreCase = true))
        assertEquals(engine.suggest(boxAt(0.48f, 0.39f), steady = true).kind, guide.kind)
    }

    @Test
    fun frozenMockCentersFollowTheSameRules() {
        assertEquals(GuideEngine.KIND_READY, engine.suggest(boxAt(0.66f, 0.34f)).kind)
        assertEquals(GuideEngine.KIND_ALIGN, engine.suggest(boxAt(0.48f, 0.39f)).kind)
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
        assertEquals(GuideEngine.KIND_NO_FACE, lost.kind)
        val moved = GuideSmoother(minIntervalMs = 250, smoothing = 1f)
        moved.update(boxAt(0.20f, 0.20f), nowMs = 0)
        val later = moved.update(boxAt(0.40f, 0.40f), nowMs = 250)
        assertEquals(0.40, later.subjectX!!, 0.0001)
    }

    @Test
    fun lostFaceDoesNotStayReadyAndTheNextFaceShowsImmediately() {
        val smoother = GuideSmoother(minIntervalMs = 250, smoothing = 0.5f)
        val ready = smoother.update(boxAt(0.67f, 0.33f), nowMs = 0)
        assertEquals(GuideEngine.KIND_READY, ready.kind)
        val gone = smoother.update(null, nowMs = 40)
        assertEquals(GuideEngine.KIND_NO_FACE, gone.kind)
        assertNull(gone.subjectX)
        assertFalse(gone.isReady)
        val back = smoother.update(boxAt(0.20f, 0.80f), nowMs = 50)
        assertEquals(GuideEngine.KIND_ALIGN, back.kind)
        assertEquals(0.20, back.subjectX!!, 0.0001)
    }

    @Test
    fun smootherEasesTheBox() {
        val smoother = GuideSmoother(minIntervalMs = 0, smoothing = 0.5f)
        smoother.update(boxAt(0.20f, 0.50f), nowMs = 0)
        val next = smoother.update(boxAt(0.40f, 0.50f), nowMs = 0)
        assertEquals(0.30, next.subjectX!!, 0.0001)
    }

    private fun boxAt(centerX: Float, centerY: Float): FaceBoxNormalized {
        return FaceBoxNormalized(centerX, centerY, centerX, centerY, centerX, centerY)
    }
}
