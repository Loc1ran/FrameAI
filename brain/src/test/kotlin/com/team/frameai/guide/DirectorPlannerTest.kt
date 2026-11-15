package com.team.frameai.guide

import com.team.frameai.model.AnchorKind
import com.team.frameai.model.FaceBoxNormalized
import com.team.frameai.model.LiveCoach
import com.team.frameai.model.SceneRead
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DirectorPlannerTest {
    private val engine = GuideEngine()
    private val planner = DirectorPlanner()

    @Test fun walkingAroundKeepsTheSubjectButDoesNotInventATarget() {
        val face = faceAt(0.5f, 0.33f)
        val offline = engine.advise(face)
        for (action in listOf("orbit_left", "orbit_right")) {
            val goal = goal(
                action = action,
                preset = "none",
                instruction = "Try walking a little around the car to show its side.",
                reason = "The head-on view makes the car look flat.",
            )
            val result = planner.merge(offline, goal, NOW, engine, face, null).guide
            assertTrue(result.hideGoal)
            assertFalse(result.isReady)
            assertEquals("hold", result.zoom)
            assertEquals(goal.instruction, result.message)
            assertEquals(goal.reason, result.detail)
            assertEquals(offline.guide.targetX, result.targetX)
        }
    }

    @Test fun centeredPortraitCanStayCentered() {
        val face = faceAt(0.5f, 0.33f)
        val offline = engine.advise(face)
        val goal = goal(
            action = "hold",
            preset = "centered",
            instruction = "Your composition is already strong. Hold this angle.",
            reason = "The face is balanced and nothing is cut off.",
        )
        val guided = planner.merge(offline, goal, NOW, engine, face, null)
        assertTrue(guided.guide.isReady)
        assertEquals(goal.instruction, guided.guide.message)
        assertEquals(0.5, guided.guide.targetX, 0.02)
        assertFalse(guided.guide.hideGoal)
    }

    @Test fun negativeSpaceRightMovesThePhoneRight() {
        val face = faceAt(0.62f, 0.33f)
        val offline = engine.advise(face)
        val goal = goal(
            action = "pan_right",
            preset = "negative_space_right",
            instruction = "Keep them on the left third.",
            reason = "Open space on the right keeps the street in the frame.",
        )
        val guided = planner.merge(offline, goal, NOW, engine, face, null)
        assertEquals(GuideEngine.THIRD_LO.toDouble(), guided.guide.targetX, 0.02)
        assertEquals(goal.instruction, guided.guide.message)
        assertFalse(guided.guide.hideGoal)
        val move = CoachAction.slightMove(
            face.anchorX,
            face.anchorY,
            guided.guide.targetX.toFloat(),
            guided.guide.targetY.toFloat(),
        )
        assertEquals(CoachAction.MOVE_RIGHT, move)
    }

    @Test fun aPanThatFightsThePresetIsIgnored() {
        val face = faceAt(0.62f, 0.33f)
        val offline = engine.advise(face)
        val goal = goal(
            action = "pan_left",
            preset = "negative_space_right",
            instruction = "Slide the phone to the left.",
            reason = "This direction does not match the chosen third.",
        )
        val guided = planner.merge(offline, goal, NOW, engine, face, null)
        assertEquals(offline.guide.message, guided.guide.message)
        assertEquals(offline.guide.targetX, guided.guide.targetX, 0.0001)
        assertNotEquals(goal.instruction, guided.guide.message)
    }

    @Test fun backlitSubjectGetsALightingLineAndNoNewBox() {
        val face = faceAt(0.5f, 0.33f)
        val offline = engine.advise(face)
        val goal = goal(
            action = "change_lighting",
            preset = "none",
            instruction = "Turn toward softer light.",
            reason = "The face is darker than the bright background.",
            issue = "backlit",
        )
        val guided = planner.merge(offline, goal, NOW, engine, face, null)
        assertEquals(goal.instruction, guided.guide.message)
        assertEquals(goal.reason, guided.guide.detail)
        assertTrue(guided.guide.hideGoal)
        assertEquals(offline.guide.targetX, guided.guide.targetX, 0.0001)
        assertEquals(offline.guide.targetY, guided.guide.targetY, 0.0001)
        assertFalse(guided.guide.isReady)
    }

    @Test fun anOverlappingBackgroundAsksForAStepWithoutADashedTarget() {
        val face = faceAt(0.48f, 0.36f)
        val offline = engine.advise(face)
        val goal = goal(
            action = "step_right",
            preset = "none",
            instruction = "Take one step to your right.",
            reason = "That moves the pole out from behind their head.",
            issue = "distracting_background",
        )
        val guided = planner.merge(offline, goal, NOW, engine, face, null)
        assertEquals(goal.instruction, guided.guide.message)
        assertTrue(guided.guide.hideGoal)
        assertEquals("⇄", guided.guide.cueSymbol)
        assertEquals(offline.guide.targetX, guided.guide.targetX, 0.0001)
    }

    @Test fun movingCloserDoesNotInventAFrameTarget() {
        val face = faceAt(0.5f, 0.33f)
        val offline = engine.advise(face)
        val goal = goal(
            action = "move_closer",
            preset = "none",
            instruction = "Move closer so the doorway frames them.",
            reason = "Empty space around the subject is not doing any work.",
        )
        val guided = planner.merge(offline, goal, NOW, engine, face, null)
        assertTrue(guided.guide.hideGoal)
        assertEquals(goal.instruction, guided.guide.message)
        assertEquals(offline.guide.targetX, guided.guide.targetX, 0.0001)
        assertEquals(offline.guide.targetY, guided.guide.targetY, 0.0001)
    }

    @Test fun loweringTheCameraIsNotATilt() {
        val face = faceAt(0.5f, 0.33f)
        val offline = engine.advise(face)
        val goal = goal(
            action = "lower_camera",
            preset = "none",
            instruction = "Lower your camera to give the building height.",
            reason = "A lower viewpoint makes the building look more dramatic.",
        )
        val guided = planner.merge(offline, goal, NOW, engine, face, null)
        assertEquals("⌄", guided.guide.cueSymbol)
        assertTrue(guided.guide.hideGoal)
        assertEquals(offline.guide.targetY, guided.guide.targetY, 0.0001)
    }

    @Test fun aHighHorizonCanBeAskedDown() {
        val scene = SceneRead(0.4f, 0.4f, 0f, topLuma = 0.8f, bottomLuma = 0.3f, horizonY = 0.22f)
        val offline = engine.advise(null, scene = scene)
        val goal = goal(
            action = "tilt_up",
            preset = "low_horizon",
            instruction = "Tilt up so the horizon sits lower.",
            reason = "The sky is the larger part of this view.",
            sceneType = "landscape",
        )
        val guided = planner.merge(offline, goal, NOW, engine, null, scene)
        assertEquals(GuideEngine.THIRD_HI.toDouble(), guided.guide.targetY, 0.02)
        assertEquals(goal.instruction, guided.guide.message)
        assertFalse(guided.guide.hideGoal)
        assertNull(guided.guide.subjectX)
    }

    @Test fun theWrongTiltDoesNotMoveTheHorizon() {
        val scene = SceneRead(0.4f, 0.4f, 0f, topLuma = 0.8f, bottomLuma = 0.3f, horizonY = 0.22f)
        val offline = engine.advise(null, scene = scene)
        val goal = goal(
            action = "tilt_down",
            preset = "low_horizon",
            instruction = "Tilt the phone downward.",
            reason = "This tilt does not put the horizon on the lower line.",
            sceneType = "landscape",
        )
        val guided = planner.merge(offline, goal, NOW, engine, null, scene)
        assertEquals(offline.guide.targetY, guided.guide.targetY, 0.0001)
        assertEquals(offline.guide.message, guided.guide.message)
    }

    @Test fun aLostSubjectKeepsTheOnDeviceGuide() {
        val offline = engine.advise(null)
        val goal = goal(
            action = "step_left",
            preset = "none",
            instruction = "Take one step to your left.",
            reason = "The background would open up from the other side.",
        )
        val guided = planner.merge(offline, goal, NOW, engine, null, null)
        assertEquals(GuideEngine.NO_FACE_MESSAGE, guided.guide.message)
        assertFalse(guided.guide.hideGoal)
    }

    @Test fun aClippedSubjectIsNotGivenANewComposition() {
        val face = FaceBoxNormalized(
            centerX = 0.15f,
            centerY = 0.45f,
            left = 0f,
            top = 0.25f,
            right = 0.30f,
            bottom = 0.65f,
            anchorX = 0.15f,
            anchorY = 0.45f,
        )
        val offline = engine.advise(face)
        val goal = goal(
            action = "pan_right",
            preset = "centered",
            instruction = "Put the face in the center.",
            reason = "The face is more important than the edge of the frame.",
        )
        val guided = planner.merge(offline, goal, NOW, engine, face, null)
        assertEquals(GuideEngine.BREATHING_CUE, guided.guide.message)
        assertEquals(GuideEngine.BREATHING_CUE, offline.guide.message)
    }

    @Test fun aMeasuredTiltIsNotReplacedByAPan() {
        val face = faceAt(0.5f, 0.33f)
        val offline = engine.advise(face).let {
            it.copy(guide = it.guide.copy(message = GeometryCoach.ROTATE_CLOCKWISE))
        }
        val goal = goal(
            action = "pan_left",
            preset = "negative_space_left",
            instruction = "Move the phone left.",
            reason = "The subject could sit on the other third.",
        )
        val guided = planner.merge(offline, goal, NOW, engine, face, null)
        assertEquals(GeometryCoach.ROTATE_CLOCKWISE, guided.guide.message)
        assertFalse(guided.guide.hideGoal)
    }

    @Test fun straightenAddsAReasonOnlyWhenThePhoneIsActuallyTilted() {
        val face = faceAt(0.5f, 0.33f)
        val level = engine.advise(face)
        val invented = goal(
            action = "straighten",
            preset = "none",
            instruction = "Straighten the horizon.",
            reason = "The picture looks slightly tilted.",
            issue = "tilted_horizon",
        )
        val unchanged = planner.merge(level, invented, NOW, engine, face, null)
        assertEquals(level.guide.message, unchanged.guide.message)

        val tilted = level.copy(guide = level.guide.copy(message = GeometryCoach.ROTATE_COUNTERCLOCKWISE))
        val guided = planner.merge(tilted, invented, NOW, engine, face, null)
        assertEquals(GeometryCoach.ROTATE_COUNTERCLOCKWISE, guided.guide.message)
        assertEquals(invented.reason, guided.guide.detail)
    }

    @Test fun aStaleReplyFallsBackToTheOnDeviceGuide() {
        val face = faceAt(0.62f, 0.33f)
        val offline = engine.advise(face)
        val goal = goal(
            action = "pan_right",
            preset = "negative_space_right",
            instruction = "Keep them on the left third.",
            reason = "Open space on the right keeps the street in the frame.",
            at = 0L,
        )
        val guided = planner.merge(offline, goal, DirectorGoal.STALE_MS + 1, engine, face, null)
        assertEquals(offline.guide.message, guided.guide.message)
        assertEquals(offline.guide.targetX, guided.guide.targetX, 0.0001)
    }

    @Test fun aMissingReplyUsesTheOnDeviceGuide() {
        val face = faceAt(0.5f, 0.33f)
        val offline = engine.advise(face)
        val guided = planner.merge(offline, null, NOW, engine, face, null)
        assertEquals(offline.guide.message, guided.guide.message)
        assertFalse(guided.guide.hideGoal)
    }

    @Test fun anUnusableReplyDoesNotEraseAGoodOne() {
        val stable = DirectorPlanner()
        val face = faceAt(0.62f, 0.33f)
        val offline = engine.advise(face)
        val good = goal(
            action = "pan_right",
            preset = "negative_space_right",
            instruction = "Keep them on the left third.",
            reason = "Open space on the right keeps the street in the frame.",
        )
        val first = stable.merge(offline, good, NOW, engine, face, null)
        val bad = goal(
            action = "pan_left",
            preset = "negative_space_right",
            instruction = "Slide the phone to the left.",
            reason = "This direction does not match the chosen third.",
            confidence = 0.95,
        )
        val second = stable.merge(offline, bad, NOW + 1_000, engine, face, null)
        assertEquals(first.guide.targetX, second.guide.targetX, 0.0001)
        assertEquals(first.guide.message, second.guide.message)
    }

    @Test fun aWeakOppositeGoalDoesNotReplaceTheCurrentOne() {
        val session = DirectorSession()
        val right = coach(action = "pan_right", preset = "negative_space_right", confidence = 0.86)
        session.offer(right, 1_000)
        val left = coach(action = "pan_left", preset = "negative_space_left", confidence = 0.90)
        session.offer(left, 2_000)
        assertEquals("negative_space_right", session.current(2_000)?.preset)
        val stronger = coach(action = "pan_left", preset = "negative_space_left", confidence = 0.99)
        session.offer(stronger, 3_000)
        assertEquals("negative_space_left", session.current(3_000)?.preset)
    }

    @Test fun bumpDropsTheGoalAndALateLowConfidenceReplyCannotRestoreIt() {
        val session = DirectorSession()
        session.offer(coach(action = "pan_right", preset = "negative_space_right", confidence = 0.9), 1_000)
        session.bump()
        assertNull(session.current(1_000))
        session.offer(coach(action = "pan_left", preset = "negative_space_left", confidence = 0.4), 1_100)
        assertNull(session.current(1_100))
    }

    @Test fun genericOrDemoRepliesAreRejected() {
        assertNull(DirectorGoal.from(coach(instruction = "Improve composition."), NOW, 0))
        assertNull(DirectorGoal.from(coach(instruction = "Move slightly left."), NOW, 0))
        assertNull(DirectorGoal.from(coach(confidence = 0.4), NOW, 0))
        assertNull(DirectorGoal.from(coach().copy(source = "demo_mock"), NOW, 0))
    }

    @Test fun theSmootherFollowsAPresetUntilTheSessionIsCleared() {
        val session = DirectorSession()
        val smoother = GuideSmoother(minIntervalMs = 0, smoothing = 1f, director = session)
        val face = faceAt(0.62f, 0.33f)
        session.offer(
            coach(
                action = "pan_right",
                preset = "negative_space_right",
                instruction = "Keep them on the left third.",
                reason = "Open space on the right keeps the street in the frame.",
            ),
            1_000,
        )
        val guided = smoother.update(face, 1_000)
        assertEquals("Keep them on the left third.", guided.message)
        assertEquals(GuideEngine.THIRD_LO.toDouble(), guided.targetX, 0.05)
        session.bump()
        val offline = smoother.update(face, 1_200)
        assertNotEquals("Keep them on the left third.", offline.message)
        assertTrue(offline.targetX > 0.5)
    }

    @Test fun portraitGradesThatPushSkinAreRejected() {
        assertEquals("warm_film", LutAdvice.accept("warm_film", "portrait"))
        assertNull(LutAdvice.accept("eastman_kodak_7355_pink_violet", "portrait"))
        assertEquals("eastman_kodak_7355_pink_violet", LutAdvice.accept("eastman_kodak_7355_pink_violet", "landscape"))
        assertNull(LutAdvice.accept("alexa_logc_to_rec709", "landscape"))
        assertNull(LutAdvice.accept("none", "portrait"))
        assertEquals("Warm Film", LutAdvice.displayName("warm_film"))
    }

    @Test fun tiltUpAimsBelowTheSubject() {
        val face = faceAt(0.5f, 0.20f)
        val offline = engine.advise(face)
        val goal = goal(
            action = "tilt_up",
            preset = "none",
            instruction = "Tilt up a little.",
            reason = "The eyes are sitting too high in the frame.",
        )
        val guided = planner.merge(offline, goal, NOW, engine, face, null)
        assertTrue(guided.guide.targetY > face.anchorY.toDouble())
        assertEquals(goal.instruction, guided.guide.message)
        val move = CoachAction.slightMove(
            face.anchorX,
            face.anchorY,
            guided.guide.targetX.toFloat(),
            guided.guide.targetY.toFloat(),
        )
        assertEquals(CoachAction.MOVE_UP, move)
        assertTrue(abs(guided.guide.targetX - face.anchorX.toDouble()) < 0.2)
    }

    private fun goal(
        action: String,
        preset: String,
        instruction: String,
        reason: String,
        issue: String = "none",
        confidence: Double = 0.9,
        sceneType: String = "portrait",
        at: Long = NOW,
    ): DirectorGoal {
        return DirectorGoal.from(
            coach(action, preset, instruction, reason, issue, confidence, sceneType),
            at,
            generation = 0,
        )!!
    }

    private fun coach(
        action: String = "hold",
        preset: String = "none",
        instruction: String = "Hold this angle for a moment.",
        reason: String = "The frame already has a clear subject.",
        issue: String = "none",
        confidence: Double = 0.9,
        sceneType: String = "portrait",
    ) = LiveCoach(
        sceneType = sceneType,
        subject = "subject",
        referenceId = "none",
        message = instruction,
        zoom = "hold",
        targetZoomRatio = 1.0,
        targetX = 0.5,
        targetY = 0.5,
        isReady = action == "hold",
        suggestedLutId = "warm_film",
        source = "gemini",
        photographyStyle = "cinematic",
        compositionPreset = preset,
        primaryIssue = issue,
        actionType = action,
        actionReason = reason,
        confidence = confidence,
    )

    private fun faceAt(x: Float, y: Float): FaceBoxNormalized {
        return FaceBoxNormalized(
            centerX = x,
            centerY = y,
            left = x - 0.14f,
            top = y - 0.16f,
            right = x + 0.14f,
            bottom = y + 0.18f,
            anchorX = x,
            anchorY = y,
            anchorKind = AnchorKind.EYES,
        )
    }

    private companion object {
        const val NOW = 5_000L
    }
}
