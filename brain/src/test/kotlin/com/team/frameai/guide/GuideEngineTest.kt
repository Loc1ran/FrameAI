package com.team.frameai.guide

import com.team.frameai.frameFile
import com.team.frameai.model.AdviceSource
import com.team.frameai.model.FaceBoxNormalized
import com.team.frameai.model.LutIds
import com.team.frameai.model.SceneRead
import kotlin.math.abs
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
        assertNull(guide.referenceId)
        assertNull(guide.zoom)
        assertEquals(AdviceSource.OFFLINE_RULES.wire, guide.source)
        assertEquals(GuideEngine.DEFAULT_TARGET_X.toDouble(), guide.targetX, 0.0001)
        assertEquals(GuideEngine.DEFAULT_TARGET_Y.toDouble(), guide.targetY, 0.0001)
    }

    @Test
    fun eitherThirdCanBeReady() {
        val right = engine.suggest(boxAt(0.67f, 0.33f))
        val left = engine.suggest(boxAt(0.33f, 0.33f))
        assertEquals(GuideEngine.KIND_READY, right.kind)
        assertEquals(GuideEngine.KIND_READY, left.kind)
        assertEquals(0.33, left.targetX, 0.0001)
    }

    @Test
    fun aLargeFaceIsNotReadyJustBecauseItSitsOnAThird() {
        val guide = engine.suggest(areaAt(0.50f, 0.40f, 0.56f, 0.36f))
        assertEquals(GuideEngine.KIND_ALIGN, guide.kind)
        assertEquals(GuideEngine.LARGE_MESSAGE, guide.message)
    }

    @Test
    fun aCutOffFaceIsCalledOutBeforePlacement() {
        val guide = engine.suggest(areaAt(0.10f, 0.33f, 0.22f, 0.30f))
        assertEquals(GuideEngine.EDGE_MESSAGE, guide.message)
    }

    @Test
    fun aBrightSideMovesTheFaceToTheCalmerThird() {
        val brightLeft = SceneRead(leftLuma = 0.82f, rightLuma = 0.28f, warmth = 0.2f)
        val guide = engine.suggest(boxAt(0.33f, 0.33f), scene = brightLeft)
        assertEquals(0.67, guide.targetX, 0.0001)
        assertEquals(GuideEngine.PLACE_ANY_MESSAGE, guide.message)
        assertEquals(LutIds.WARM_FILM, brightLeft.lookId)
        val settled = engine.suggest(boxAt(0.67f, 0.33f), scene = brightLeft)
        assertEquals(GuideEngine.KIND_READY, settled.kind)
        assertEquals(GuideEngine.READY_WARM, settled.message)
    }

    @Test
    fun aBalancedFrameWithoutAPersonIsReady() {
        val even = SceneRead(leftLuma = 0.4f, rightLuma = 0.42f, warmth = 0.02f)
        val guide = engine.suggest(null, scene = even)
        assertEquals(GuideEngine.KIND_READY, guide.kind)
        assertEquals(GuideEngine.OPEN_READY, guide.message)
        assertTrue(guide.isReady)
        assertFalse(guide.message.contains("face", ignoreCase = true))
    }

    @Test
    fun aHorizonInTheMiddleMovesToTheNearerGridLine() {
        val sky = SceneRead(leftLuma = 0.5f, rightLuma = 0.48f, warmth = 0.1f, horizonY = 0.5f)
        val guide = engine.suggest(null, scene = sky)
        assertEquals(GuideEngine.KIND_ALIGN, guide.kind)
        assertEquals(0.33, guide.targetY, 0.0001)
        assertEquals(GuideEngine.HORIZON_UPPER, guide.message)
        assertEquals(0.5, guide.subjectY!!, 0.0001)
        assertNull(guide.referenceId)
        assertNull(guide.zoom)
    }

    @Test
    fun aHorizonOnAGridLineIsReady() {
        val sky = SceneRead(leftLuma = 0.5f, rightLuma = 0.48f, warmth = 0.0f, horizonY = 0.67f)
        val guide = engine.suggest(null, scene = sky)
        assertEquals(GuideEngine.KIND_READY, guide.kind)
        assertEquals(GuideEngine.HORIZON_READY, guide.message)
        assertTrue(guide.isReady)
    }

    @Test
    fun aBrightSideWithoutAPersonStillPlacesTheSubject() {
        val brightLeft = SceneRead(leftLuma = 0.8f, rightLuma = 0.3f, warmth = 0.0f)
        val guide = engine.suggest(null, scene = brightLeft)
        assertEquals(0.67, guide.targetX, 0.0001)
        assertEquals(GuideEngine.PLACE_ANY_MESSAGE, guide.message)
    }

    @Test
    fun coolBackgroundSelectsCoolCinema() {
        val cool = SceneRead(leftLuma = 0.4f, rightLuma = 0.42f, warmth = -0.3f)
        val guide = engine.suggest(boxAt(0.67f, 0.33f), scene = cool)
        assertEquals(GuideEngine.READY_COOL, guide.message)
        assertEquals(LutIds.COOL_CINEMA, cool.lookId)
    }

    @Test
    fun aPersonMapsToAPortraitReferenceAndZoom() {
        val clipped = areaAt(0.10f, 0.33f, 0.22f, 0.30f)
        val clippedGuide = engine.suggest(clipped)
        assertEquals(GuideEngine.EDGE_MESSAGE, clippedGuide.message)
        assertEquals(GuideEngine.ZOOM_OUT, clippedGuide.zoom)
        assertEquals(1.0, clippedGuide.targetZoomRatio!!, 0.0001)
        assertEquals(GuideEngine.PORTRAIT_SPACE_RIGHT, clippedGuide.referenceId)
        assertEquals(GuideEngine.THIRD_LO.toDouble(), clippedGuide.targetX, 0.0001)
        val clippedGoal = PortraitFrame.goal(
            clippedGuide.targetX.toFloat(),
            clippedGuide.targetY.toFloat(),
            clippedGuide.zoom,
            clipped,
        )
        assertTrue(abs(clippedGoal.centerX - clipped.centerX) > 0.15f)
        assertFalse(clippedGuide.isReady)

        val small = areaAt(0.48f, 0.46f, 0.10f, 0.12f)
        val smallGuide = engine.suggest(small)
        assertEquals(GuideEngine.SMALL_MESSAGE, smallGuide.message)
        assertEquals(GuideEngine.ZOOM_IN, smallGuide.zoom)
        assertTrue(smallGuide.targetZoomRatio!! > 1.0)
        assertTrue(smallGuide.targetZoomRatio!! <= 8.0)
        assertEquals(GuideEngine.PORTRAIT_SPACE_RIGHT, smallGuide.referenceId)
        val smallGoal = PortraitFrame.goal(
            smallGuide.targetX.toFloat(),
            smallGuide.targetY.toFloat(),
            smallGuide.zoom,
            small,
        )
        assertEquals(smallGuide.targetX.toFloat(), smallGoal.centerX, 0.0001f)
        assertTrue(smallGoal.width > small.width)
        assertTrue(smallGoal.height > small.height)
        assertFalse(smallGuide.isReady)

        val large = areaAt(0.50f, 0.40f, 0.70f, 0.70f)
        val largeGuide = engine.suggest(large)
        assertEquals(GuideEngine.LARGE_MESSAGE, largeGuide.message)
        assertEquals(GuideEngine.ZOOM_OUT, largeGuide.zoom)
        assertEquals(1.0, largeGuide.targetZoomRatio!!, 0.0001)
        assertTrue(
            largeGuide.referenceId == GuideEngine.PORTRAIT_SPACE_LEFT ||
                largeGuide.referenceId == GuideEngine.PORTRAIT_SPACE_RIGHT,
        )
        val largeGoal = PortraitFrame.goal(
            largeGuide.targetX.toFloat(),
            largeGuide.targetY.toFloat(),
            largeGuide.zoom,
            large,
        )
        assertEquals(largeGuide.targetX.toFloat(), largeGoal.centerX, 0.0001f)
        assertTrue(largeGoal.width < large.width)
        assertTrue(largeGoal.height < large.height)
        assertFalse(largeGuide.isReady)

        val readyFace = areaAt(0.67f, 0.33f, 0.28f, 0.36f)
        val ready = engine.suggest(readyFace)
        assertEquals(GuideEngine.KIND_READY, ready.kind)
        assertEquals(GuideEngine.ZOOM_HOLD, ready.zoom)
        assertEquals(1.0, ready.targetZoomRatio!!, 0.0001)
        assertEquals(GuideEngine.PORTRAIT_SPACE_LEFT, ready.referenceId)
        assertTrue(ready.isReady)
        val readyGoal = PortraitFrame.goal(
            ready.targetX.toFloat(),
            ready.targetY.toFloat(),
            ready.zoom,
            readyFace,
        )
        assertEquals(readyFace.centerX, readyGoal.centerX, 0.0001f)
        assertEquals(readyFace.width, readyGoal.width, 0.0001f)
        assertEquals(readyFace.height, readyGoal.height, 0.0001f)

        val leftThird = engine.suggest(areaAt(0.33f, 0.67f, 0.28f, 0.36f))
        assertEquals(GuideEngine.KIND_READY, leftThird.kind)
        assertEquals(GuideEngine.ZOOM_HOLD, leftThird.zoom)
        assertEquals(GuideEngine.PORTRAIT_SPACE_RIGHT, leftThird.referenceId)
        assertTrue(leftThird.isReady)
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
        val second = smoother.update(boxAt(0.22f, 0.22f), nowMs = 100)
        assertSame(first, second)
        val lost = smoother.update(null, nowMs = 120)
        assertEquals(GuideEngine.KIND_NO_FACE, lost.kind)
        val moved = GuideSmoother(minIntervalMs = 250, smoothing = 1f)
        moved.update(boxAt(0.20f, 0.20f), nowMs = 0)
        val later = moved.update(boxAt(0.15f, 0.20f), nowMs = 250)
        assertEquals(0.15, later.subjectX!!, 0.0001)
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

    private fun areaAt(centerX: Float, centerY: Float, width: Float, height: Float): FaceBoxNormalized {
        return FaceBoxNormalized(
            centerX,
            centerY,
            centerX - width / 2f,
            centerY - height / 2f,
            centerX + width / 2f,
            centerY + height / 2f,
        )
    }
}
