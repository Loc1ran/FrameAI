package com.team.frameai.guide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameRatioTest {
    @Test fun turningThePhoneSwapsTheFourByThreeFrame() {
        val portrait = FrameRatio.FOUR_THREE.previewAspect(landscape = false)!!
        val landscape = FrameRatio.FOUR_THREE.previewAspect(landscape = true)!!
        assertTrue(portrait < 1f)
        assertTrue(landscape > 1f)
        assertEquals(3f / 4f, portrait, 0.001f)
        assertEquals(4f / 3f, landscape, 0.001f)
    }

    @Test fun sixteenByNineAndSquareFollowThePhone() {
        assertEquals(9f / 16f, FrameRatio.SIXTEEN_NINE.previewAspect(landscape = false)!!, 0.001f)
        assertEquals(16f / 9f, FrameRatio.SIXTEEN_NINE.previewAspect(landscape = true)!!, 0.001f)
        assertEquals(1f, FrameRatio.SQUARE.previewAspect(landscape = false)!!, 0.001f)
        assertEquals(1f, FrameRatio.SQUARE.previewAspect(landscape = true)!!, 0.001f)
        assertNull(FrameRatio.FULL.previewAspect(landscape = true))
    }
}
