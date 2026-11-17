package com.team.frameai.guide

import com.team.frameai.model.FaceBoxNormalized
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonFrameTest {
    @Test
    fun aHeadSizedDetectionGrowsIntoTheBody() {
        val head = box(0.50f, 0.18f, 0.16f, 0.12f)
        val body = PersonFrame.choose(head, head)!!
        assertTrue(body.height > head.height * 3f)
        assertTrue(body.top <= head.top + 0.02f)
        assertTrue(body.bottom > head.bottom)
    }

    @Test
    fun aBodyThatAlreadyContainsTheFaceIsKept() {
        val face = box(0.50f, 0.20f, 0.14f, 0.12f)
        val body = box(0.50f, 0.48f, 0.36f, 0.72f)
        assertEquals(body, PersonFrame.choose(body, face))
    }

    @Test
    fun anObjectAwayFromTheFaceStaysThatObject() {
        val bottle = box(0.20f, 0.70f, 0.12f, 0.28f)
        val face = box(0.72f, 0.22f, 0.14f, 0.12f)
        assertEquals(bottle, PersonFrame.choose(bottle, face))
    }

    @Test
    fun aCloseUpFaceStaysTheHead() {
        val face = box(0.50f, 0.48f, 0.50f, 0.55f)
        assertEquals(face, PersonFrame.choose(null, face))
    }

    private fun box(cx: Float, cy: Float, width: Float, height: Float) = FaceBoxNormalized.fromEdges(
        cx - width / 2f, cy - height / 2f, cx + width / 2f, cy + height / 2f,
    )
}
