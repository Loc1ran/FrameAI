package com.team.frameai.guide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AimLockTest {
    @Test fun theArrowStaysAimedAtTheFirstTarget() {
        val aim = AimLock()
        val first = aim.draw("pan_left", "none", 0.40f, 0.50f, 0.30f, false, "none", null, "Move the phone left.")
        assertNotNull(first)
        assertTrue(AiDrawing.phoneX(0.40f, first!!.targetX.toFloat()) < 0f)
        val moved = aim.draw("pan_left", "none", 0.48f, 0.50f, 0.30f, false, "none", null, "Move the phone left.")
        assertEquals(first.targetX, moved!!.targetX, 0.001)
        assertFalse(moved.isReady)
    }

    @Test fun arrivingTurnsTheFrameGreen() {
        val aim = AimLock()
        val first = aim.draw("pan_left", "none", 0.40f, 0.50f, 0.30f, false, "none", null, "Move the phone left.")
        val arrived = aim.draw(
            "pan_left", "none", first!!.targetX.toFloat(), 0.50f, 0.30f, false, "none", null, "Move the phone left.",
        )
        assertTrue(arrived!!.isReady)
    }

    @Test fun aHoldWithNoTargetIsTheGreenSignal() {
        val held = AimLock().draw("hold", "none", 0.42f, 0.48f, 0.30f, false, "none", null, "This frame is working.")
        assertTrue(held!!.isReady)
    }

    @Test fun aNamedDirectionDrawsAnArrowBeforeTheSubjectIsFound() {
        val guide = AimLock().draw("step_right", "none", null, null, null, false, "none", null, "Take a step right.")
        assertFalse(guide!!.isReady)
        assertTrue(AiDrawing.phoneX(0.50f, guide.targetX.toFloat()) > 0f)
    }

    @Test fun closerDrawsAnArrowUntilTheSubjectGrows() {
        val aim = AimLock()
        val first = aim.draw("move_closer", "none", 0.50f, 0.50f, 0.20f, false, "none", null, "Step closer.")
        assertFalse(first!!.isReady)
        assertTrue(AiDrawing.phoneY(0.50f, first.targetY.toFloat()) < 0f)
        val grown = aim.draw("move_closer", "none", 0.50f, 0.50f, 0.30f, false, "none", null, "Step closer.")
        assertTrue(grown!!.isReady)
    }
}
