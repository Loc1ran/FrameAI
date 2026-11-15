package com.team.frameai.guide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ZoomLensTest {
    @Test
    fun zoomInMultipliesTheCurrentRatio() {
        assertEquals(4f, ZoomLens.target(current = 2f, scale = 2f, min = 1f, max = 8f)!!)
    }

    @Test
    fun zoomOutStepsTowardTheSmallerFrame() {
        assertEquals(1.5f, ZoomLens.target(current = 3f, scale = 0.5f, min = 1f, max = 8f)!!)
    }

    @Test
    fun zoomOutAtTheWidestLensDoesNotMove() {
        assertNull(ZoomLens.target(current = 1f, scale = 0.6f, min = 1f, max = 8f))
    }

    @Test
    fun aMatchingFrameDoesNotMoveTheLens() {
        assertNull(ZoomLens.target(current = 2f, scale = 1f, min = 1f, max = 8f))
    }
}
