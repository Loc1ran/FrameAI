package com.team.frameai.guide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import com.team.frameai.model.LiveCoach
import org.junit.Test

class AiDrawingTest {
    @Test fun theArrowPointsTheWayAthenaNames() {
        val left = AiDrawing.guide("pan_left", "none", 0.40f, 0.50f, false, null, "Move the phone left.")
        val right = AiDrawing.guide("pan_right", "none", 0.40f, 0.50f, false, null, "Aim the phone right.")
        val up = AiDrawing.guide("tilt_up", "none", 0.40f, 0.50f, false, null, "Tilt up.")
        val down = AiDrawing.guide("tilt_down", "none", 0.40f, 0.50f, false, null, "Tilt down.")
        assertNotNull(left)
        assertTrue(AiDrawing.phoneX(0.40f, left!!.targetX.toFloat()) < 0f)
        assertTrue(AiDrawing.phoneX(0.40f, right!!.targetX.toFloat()) > 0f)
        assertTrue(AiDrawing.phoneY(0.50f, up!!.targetY.toFloat()) < 0f)
        assertTrue(AiDrawing.phoneY(0.50f, down!!.targetY.toFloat()) > 0f)
        assertEquals("←", AiDrawing.symbol("pan_left", false))
        assertEquals("→", AiDrawing.symbol("step_right", false))
        assertEquals("↑", AiDrawing.symbol("raise_camera", false))
        assertEquals("↓", AiDrawing.symbol("tilt_down", false))
    }

    @Test fun bodyMovementNeverBecomesAPanTargetOrDigitalZoom() {
        for (action in LiveCoach.VIEWPOINT) {
            // A centered subject (even with an inconsistent ready flag) cannot prove a walk is done.
            val guide = AiDrawing.guide(action, "centered", 0.5f, 0.5f, true, null, "Try a different viewpoint.")!!
            assertTrue(action, guide.hideGoal)
            assertFalse(action, guide.isReady)
            assertEquals(action, GuideEngine.ZOOM_HOLD, guide.zoom)
            assertEquals(action, 1.0, guide.targetZoomRatio!!, 0.0)
            assertNotNull(action, ViewpointGuidance.cue(action))
        }
    }

    @Test fun viewpointsStillWorkForScenesWithoutAnObjectBox() {
        for (action in LiveCoach.VIEWPOINT) {
            val guide = AiDrawing.guide(action, "none", null, null, false, null, "Try a different viewpoint.")!!
            assertTrue(guide.hideGoal)
            assertNull(guide.subjectX)
            assertNull(guide.subjectY)
        }
        assertNull(ViewpointGuidance.cue("pan_left"))
        assertNull(ViewpointGuidance.cue("tilt_down"))
        assertNull(ViewpointGuidance.cue("hold"))
    }

    @Test fun aHoldUsesTheNamedThirdAndALightChangeDrawsNoArrow() {
        val held = AiDrawing.guide("hold", "negative_space_right", 0.70f, 0.45f, false, null, "Leave space on the right.")
        assertEquals(GuideEngine.THIRD_LO.toDouble(), held!!.targetX, 0.001)
        assertTrue(AiDrawing.phoneX(0.70f, held.targetX.toFloat()) > 0f)
        val light = AiDrawing.guide("change_lighting", "none", 0.50f, 0.50f, false, null, "Turn toward softer light.")
        assertTrue(light!!.hideGoal)
        assertNull(AiDrawing.guide("hold", "none", 0.50f, 0.50f, false, null, "Hold."))
    }
}
