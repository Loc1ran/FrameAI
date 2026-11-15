package com.team.frameai.ui

import com.team.frameai.model.FaceBoxNormalized
import org.junit.Assert.*
import org.junit.Test

class FramingHintTest {
    private fun box(x: Float, y: Float) = FaceBoxNormalized(x, y, x - 0.08f, y - 0.08f, x + 0.08f, y + 0.08f)

    @Test fun noDetectionNeverInventsAReadySubject() {
        assertEquals("Find your subject", framingHint(null).title)
        assertFalse(framingHint(null).aligned)
    }

    @Test fun directionsNamePhoneMotionOneAxisAtATime() {
        assertEquals("Pan phone right", framingHint(box(0.8f, 0.5f)).title)
        assertEquals("Pan phone left", framingHint(box(0.2f, 0.5f)).title)
        assertEquals("Tilt phone down", framingHint(box(0.5f, 0.8f)).title)
        assertEquals("Tilt phone up", framingHint(box(0.5f, 0.2f)).title)
    }

    @Test fun readinessUsesTheVisibleCenterTarget() {
        assertTrue(framingHint(box(0.5f, 0.5f)).aligned)
        assertFalse(framingHint(box(0.67f, 0.33f)).aligned)
        assertFalse(framingHint(box(0.5f + TARGET_RADIUS + 0.001f, 0.5f)).aligned)
    }

    @Test fun clippedSubjectGetsRoomAdviceNotAnInventedDistance() {
        val edge = FaceBoxNormalized(0.12f, 0.5f, 0f, 0.3f, 0.24f, 0.7f)
        assertEquals("Give your subject room", framingHint(edge).title)
        assertFalse(framingHint(edge).aligned)
    }
}
