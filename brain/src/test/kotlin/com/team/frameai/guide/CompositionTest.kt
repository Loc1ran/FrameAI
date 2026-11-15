package com.team.frameai.guide

import com.team.frameai.model.AnchorKind
import com.team.frameai.model.FaceBoxNormalized
import com.team.frameai.model.SceneRead
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CompositionTest {
    private val engine = GuideEngine()

    @Test
    fun missingYawAtTheCenterStaysOnAThird() {
        val guide = engine.advise(portrait(0.50f, 0.36f))
        assertEquals(CompositionStrategy.THIRDS, guide.strategy)
        assertEquals(GuideEngine.THIRD_LO.toDouble(), guide.guide.targetX, 0.0001)
        assertNotEquals(GuideEngine.CENTER_CUE, guide.guide.message)
    }

    @Test
    fun symmetryAndAFrontalFaceNearTheMiddleCanCenter() {
        val scene = SceneRead(leftLuma = 0.40f, rightLuma = 0.42f, warmth = 0f)
        val framing = engine.advise(portrait(0.40f, 0.36f, yaw = 2f), scene = scene)
        assertEquals(CompositionStrategy.CENTERED, framing.strategy)
        assertEquals(0.5, framing.guide.targetX, 0.0001)
        assertEquals(GuideEngine.CENTER_CUE, framing.guide.message)
        val already = engine.advise(portrait(0.48f, 0.36f, yaw = 2f), scene = scene)
        assertEquals(CompositionStrategy.CENTERED, already.strategy)
        assertTrue(already.guide.isReady)
        assertEquals(GuideEngine.GREAT_CUE, already.guide.message)
    }

    @Test
    fun aFrontalFaceAlreadyOnAThirdIsNotPulledToCenter() {
        val scene = SceneRead(leftLuma = 0.40f, rightLuma = 0.41f, warmth = 0f)
        val framing = engine.advise(portrait(0.33f, 0.33f, yaw = 0f), scene = scene)
        assertEquals(CompositionStrategy.THIRDS, framing.strategy)
        assertEquals(0.33, framing.guide.targetX, 0.0001)
        assertTrue(framing.guide.isReady)
    }

    @Test
    fun anExplicitPreferenceCentersASuitablePortrait() {
        val framing = engine.advise(portrait(0.33f, 0.36f, yaw = 0f), preferCentered = true)
        assertEquals(CompositionStrategy.CENTERED, framing.strategy)
        assertEquals(0.5, framing.guide.targetX, 0.0001)
        assertEquals(GuideEngine.CENTER_CUE, framing.guide.message)
    }

    @Test
    fun leftAndRightThirdsFollowTheAnchor() {
        val left = engine.suggest(area(0.20f, 0.36f, 0.24f, 0.40f))
        val right = engine.suggest(area(0.80f, 0.36f, 0.24f, 0.40f))
        assertEquals(0.33, left.targetX, 0.0001)
        assertEquals(0.67, right.targetX, 0.0001)
        assertEquals(CoachAction.MOVE_LEFT, left.message)
        assertEquals(CoachAction.MOVE_RIGHT, right.message)
    }

    @Test
    fun headTurnLeavesSpaceInFront() {
        val turnedRight = engine.advise(portrait(0.50f, 0.36f, yaw = 22f))
        val turnedLeft = engine.advise(portrait(0.50f, 0.36f, yaw = -22f))
        assertEquals(CompositionStrategy.LOOK_ROOM, turnedRight.strategy)
        assertEquals(0.33, turnedRight.guide.targetX, 0.0001)
        assertEquals(GuideEngine.LOOK_CUE, turnedRight.guide.message)
        assertEquals(CompositionStrategy.LOOK_ROOM, turnedLeft.strategy)
        assertEquals(0.67, turnedLeft.guide.targetX, 0.0001)
        assertEquals(GuideEngine.LOOK_CUE, turnedLeft.guide.message)
        assertEquals(CoachAction.MOVE_RIGHT, CoachAction.slightMove(0.80f, 0.36f, 0.33f, 0.33f))
    }

    @Test
    fun aSmallYawDoesNotCountAsLookRoom() {
        val framing = engine.advise(portrait(0.70f, 0.36f, yaw = 8f))
        assertEquals(CompositionStrategy.THIRDS, framing.strategy)
        assertEquals(0.67, framing.guide.targetX, 0.0001)
    }

    @Test
    fun brightnessDoesNotOverrideHeadTurn() {
        val brightLeft = SceneRead(leftLuma = 0.84f, rightLuma = 0.20f, warmth = 0f)
        val framing = engine.advise(portrait(0.50f, 0.36f, yaw = 22f), scene = brightLeft)
        assertEquals(CompositionStrategy.LOOK_ROOM, framing.strategy)
        assertEquals(0.33, framing.guide.targetX, 0.0001)
    }

    @Test
    fun eyeAnchorAimsAtTheUpperThirdAndFallsBackInOrder() {
        val eyes = portrait(0.62f, 0.58f).copy(anchorX = 0.62f, anchorY = 0.40f, anchorKind = AnchorKind.EYES)
        val eyeGuide = engine.suggest(eyes)
        assertEquals(0.62, eyeGuide.subjectX!!, 0.0001)
        assertEquals(0.40, eyeGuide.subjectY!!, 0.0001)
        assertEquals(0.33, eyeGuide.targetY, 0.0001)

        val nose = eyes.copy(anchorKind = AnchorKind.NOSE, anchorY = 0.46f)
        assertEquals(0.33, engine.suggest(nose).targetY, 0.0001)
        assertEquals(0.46, engine.suggest(nose).subjectY!!, 0.0001)

        val box = portrait(0.62f, 0.70f)
        val boxGuide = engine.suggest(box)
        assertEquals(AnchorKind.BOX, box.anchorKind)
        assertEquals(0.70, boxGuide.subjectY!!, 0.0001)
        assertEquals(0.67, boxGuide.targetY, 0.0001)
    }

    @Test
    fun breathingRoomMovesInwardWithoutCrossingTheFrame() {
        val clippedLeft = area(0.08f, 0.40f, 0.18f, 0.28f)
        val left = engine.suggest(clippedLeft)
        assertEquals(GuideEngine.BREATHING_CUE, left.message)
        assertTrue(left.targetX > clippedLeft.anchorX.toDouble())
        assertTrue(left.targetX <= 0.42)
        assertTrue(left.targetX < 0.5)

        val clippedTop = area(0.50f, 0.08f, 0.24f, 0.16f)
        val top = engine.suggest(clippedTop)
        assertEquals(GuideEngine.BREATHING_CUE, top.message)
        assertEquals(0.50, top.targetX, 0.05)
        assertTrue(top.targetY > clippedTop.anchorY.toDouble())
        assertTrue(top.targetY <= 0.42)
        assertFalse(top.isReady)
    }

    @Test
    fun aSmallFaceAloneIsNotAnEnvironmentalPortrait() {
        val small = area(0.40f, 0.46f, 0.16f, 0.20f)
        val alone = engine.advise(small)
        assertEquals(CompositionStrategy.THIRDS, alone.strategy)
        assertEquals(GuideEngine.ZOOM_HOLD, alone.guide.zoom)
        assertNotEquals(GuideEngine.SMALL_MESSAGE, alone.guide.message)

        val place = SceneRead(leftLuma = 0.55f, rightLuma = 0.40f, warmth = 0f, horizonY = 0.67f)
        val environmental = engine.advise(small, scene = place)
        assertEquals(CompositionStrategy.ENVIRONMENTAL, environmental.strategy)
        assertTrue(environmental.confidence >= 0.55f)
        assertEquals(GuideEngine.ZOOM_HOLD, environmental.guide.zoom)
        assertEquals(GuideEngine.ENVIRONMENT_CUE, environmental.guide.message)
    }

    @Test
    fun movementMatchesThePhoneDirection() {
        assertEquals(CoachAction.MOVE_RIGHT, engine.suggest(area(0.80f, 0.36f, 0.24f, 0.40f)).message)
        assertEquals(CoachAction.MOVE_LEFT, engine.suggest(area(0.20f, 0.36f, 0.24f, 0.40f)).message)
        assertEquals(CoachAction.MOVE_DOWN, engine.suggest(area(0.67f, 0.80f, 0.24f, 0.18f)).message)
        assertEquals(CoachAction.MOVE_UP, engine.suggest(area(0.67f, 0.20f, 0.24f, 0.16f)).message)
        assertEquals(
            "Move right",
            CoachAction.move(subjectX = 0.80f, subjectY = 0.36f, goalX = 0.67f, goalY = 0.33f),
        )
    }

    @Test
    fun alignmentUsesTheAnchorAndTheSameTolerance() {
        val ready = engine.suggest(portrait(0.67f, 0.33f))
        assertTrue(ready.isReady)
        assertEquals(GuideEngine.KIND_READY, ready.kind)
        assertEquals(GuideEngine.GREAT_CUE, ready.message)

        val past = engine.suggest(portrait(GuideEngine.THIRD_HI + 0.09f, 0.33f))
        assertFalse(past.isReady)
        assertEquals(GuideEngine.KIND_ALIGN, past.kind)
    }

    @Test
    fun shadowWarnsOnlyAfterThePlacementIsAligned() {
        val shadow = SceneRead(leftLuma = 0.70f, rightLuma = 0.66f, warmth = 0f, subjectLuma = 0.12f)
        val aligned = engine.suggest(portrait(0.67f, 0.33f), scene = shadow)
        assertTrue(aligned.isReady)
        assertEquals(GuideEngine.SHADOW_CUE, aligned.message)
        assertTrue(shadow.faceInShadow())

        val moving = engine.suggest(portrait(0.20f, 0.36f), scene = shadow)
        assertFalse(moving.isReady)
        assertEquals(CoachAction.MOVE_LEFT, moving.message)
    }

    @Test
    fun lowConfidenceDoesNotReplaceTheHeldComposition() {
        val stable = CompositionStabilizer()
        val first = stable.choose(CompositionStrategy.THIRDS, 0.72f, 0.33f, urgent = false)
        val weak = stable.choose(CompositionStrategy.CENTERED, 0.50f, 0.50f, urgent = false)
        assertEquals(CompositionStrategy.THIRDS, first.strategy)
        assertEquals(CompositionStrategy.THIRDS, weak.strategy)
        assertEquals(0.33f, weak.targetX)
    }

    @Test
    fun yawFlipsDoNotFlickerTheTargetOnTheNextFrame() {
        val smoother = GuideSmoother(minIntervalMs = 0, smoothing = 1f)
        val facingLeft = portrait(0.50f, 0.36f, yaw = -20f)
        val facingRight = portrait(0.50f, 0.36f, yaw = 20f)
        val first = smoother.update(facingLeft, nowMs = 0)
        assertEquals(0.67, first.targetX, 0.0001)
        val second = smoother.update(facingRight, nowMs = 40)
        val third = smoother.update(facingRight, nowMs = 80)
        assertEquals(0.67, second.targetX, 0.0001)
        assertEquals(0.67, third.targetX, 0.0001)
    }

    @Test
    fun aLostFaceClearsTheHeldSide() {
        val smoother = GuideSmoother(minIntervalMs = 0, smoothing = 1f)
        smoother.update(portrait(0.50f, 0.36f, yaw = 22f), nowMs = 0)
        val gone = smoother.update(null, nowMs = 30)
        assertEquals(GuideEngine.KIND_NO_FACE, gone.kind)
        assertNull(gone.subjectX)
        val returned = smoother.update(portrait(0.50f, 0.36f, yaw = -22f), nowMs = 60)
        assertEquals(0.67, returned.targetX, 0.0001)
    }

    @Test
    fun horizonAndOpenScenesStayAvailable() {
        val open = engine.advise(null, scene = SceneRead(0.4f, 0.42f, 0.02f))
        assertEquals(CompositionStrategy.OPEN_SCENE, open.strategy)
        assertEquals(GuideEngine.OPEN_READY, open.guide.message)
        assertTrue(open.guide.isReady)

        val middle = engine.advise(null, scene = SceneRead(0.5f, 0.5f, 0f, horizonY = 0.40f))
        assertEquals(CompositionStrategy.HORIZON, middle.strategy)
        assertEquals(0.33, middle.guide.targetY, 0.0001)

        val sky = engine.advise(
            null,
            scene = SceneRead(0.5f, 0.5f, 0f, topLuma = 0.86f, bottomLuma = 0.24f, horizonY = 0.50f),
        )
        assertEquals(0.67, sky.guide.targetY, 0.0001)
        assertEquals(GuideEngine.HORIZON_LOWER_CUE, sky.guide.message)

        val missing = engine.advise(null)
        assertEquals(CompositionStrategy.NO_SUBJECT, missing.strategy)
        assertEquals(GuideEngine.NO_FACE_MESSAGE, missing.guide.message)
        assertFalse(missing.guide.isReady)
    }

    @Test
    fun mirroredPreviewFlipsYawAndTheCropMapsTheAnchor() {
        assertEquals(18f, HeadOrientation.previewYaw(18f, mirroredPreview = false))
        assertEquals(-18f, HeadOrientation.previewYaw(18f, mirroredPreview = true))
        assertTrue(HeadOrientation.turnedTowardViewersRight(18f))
        assertTrue(HeadOrientation.turnedTowardViewersLeft(-18f))
        assertFalse(HeadOrientation.turnedTowardViewersRight(null))
    }

    private fun portrait(x: Float, y: Float, yaw: Float? = null): FaceBoxNormalized {
        return area(x, y, 0.30f, 0.40f).copy(yawDegrees = yaw)
    }

    private fun area(x: Float, y: Float, width: Float, height: Float): FaceBoxNormalized {
        return FaceBoxNormalized(
            centerX = x,
            centerY = y,
            left = x - width / 2f,
            top = y - height / 2f,
            right = x + width / 2f,
            bottom = y + height / 2f,
        )
    }
}
