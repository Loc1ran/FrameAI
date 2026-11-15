package com.team.frameai.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameJpegTest {
    @Test
    fun longEdgeStaysAt768AndBothSidesStayEven() {
        val (width, height) = FrameJpeg.outputSize(4000, 3000, rotationDegrees = 90)

        assertEquals(768, maxOf(width, height))
        assertTrue(width % 2 == 0)
        assertTrue(height % 2 == 0)
        assertTrue(width < height)
    }

    @Test
    fun smallFrameIsNotEnlarged() {
        val (width, height) = FrameJpeg.outputSize(320, 240, rotationDegrees = 0)

        assertEquals(320, width)
        assertEquals(240, height)
    }
}
