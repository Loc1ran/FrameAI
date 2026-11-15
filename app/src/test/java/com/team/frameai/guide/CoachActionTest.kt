package com.team.frameai.guide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoachActionTest {
    @Test
    fun zoomNamesHowFar() {
        assertEquals("Zoom in 2×", CoachAction.zoom(grow = true, scale = 2f, zoomPossible = true))
        assertEquals("Zoom in 1.7×", CoachAction.zoom(grow = true, scale = 1.7f, zoomPossible = true))
        assertEquals("Zoom out 2×", CoachAction.zoom(grow = false, scale = 0.5f, zoomPossible = true))
        assertEquals("Step closer 2×", CoachAction.zoom(grow = true, scale = 2f, zoomPossible = false))
        assertEquals("Step back 2.5×", CoachAction.zoom(grow = false, scale = 0.4f, zoomPossible = false))
    }

    @Test
    fun panningRightSlidesASubjectThatSitsTooFarRight() {
        assertEquals("Move right", CoachAction.move(subjectX = 0.75f, subjectY = 0.5f, goalX = 0.33f, goalY = 0.5f))
    }

    @Test
    fun panningLeftSlidesASubjectThatSitsTooFarLeft() {
        assertEquals("Move left", CoachAction.move(subjectX = 0.25f, subjectY = 0.5f, goalX = 0.67f, goalY = 0.5f))
    }

    @Test
    fun tiltingDownSlidesASubjectThatSitsTooLow() {
        assertEquals("Move down", CoachAction.move(subjectX = 0.5f, subjectY = 0.75f, goalX = 0.5f, goalY = 0.33f))
    }

    @Test
    fun tiltingUpSlidesASubjectThatSitsTooHigh() {
        assertEquals("Move up", CoachAction.move(subjectX = 0.5f, subjectY = 0.25f, goalX = 0.5f, goalY = 0.67f))
    }

    @Test
    fun aDiagonalNamesTheLargerShiftFirst() {
        assertEquals(
            "Move down and right",
            CoachAction.move(subjectX = 0.8f, subjectY = 0.78f, goalX = 0.67f, goalY = 0.33f),
        )
    }

    @Test
    fun aHorizonNamesTheTilt() {
        assertEquals("Tilt down", CoachAction.tilt(horizonY = 0.7f, goalY = 0.33f))
        assertEquals("Tilt up", CoachAction.tilt(horizonY = 0.2f, goalY = 0.67f))
    }

    @Test
    fun aMatchSaysNothing() {
        assertNull(
            CoachAction.label(
                FrameCue.READY,
                zoomPossible = false,
                horizon = false,
                ready = true,
                subjectX = 0.2f,
                subjectY = 0.2f,
                goalX = 0.8f,
                goalY = 0.8f,
            ),
        )
    }
}
