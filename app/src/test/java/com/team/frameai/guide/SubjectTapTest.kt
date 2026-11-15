package com.team.frameai.guide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubjectTapTest {
    @Test fun aTapInsideABoxSelectsTheSmallerObject() {
        val room = candidate(1, 0.5f, 0.5f, 0.8f, 0.8f)
        val cup = candidate(2, 0.62f, 0.58f, 0.16f, 0.2f)
        val chosen = SubjectTap.choose(listOf(room, cup), 0.62f, 0.58f)
        assertEquals(2, chosen?.id)
    }

    @Test fun aNearbyTapSelectsTheClosestObject() {
        val bottle = candidate(7, 0.7f, 0.6f, 0.12f, 0.2f)
        val chosen = SubjectTap.choose(listOf(bottle), 0.78f, 0.62f)
        assertEquals(7, chosen?.id)
    }

    @Test fun aFarTapDoesNotSelectAnything() {
        val bottle = candidate(7, 0.2f, 0.2f, 0.12f, 0.2f)
        assertNull(SubjectTap.choose(listOf(bottle), 0.9f, 0.9f))
    }

    private fun candidate(id: Int, cx: Float, cy: Float, width: Float, height: Float) = TrackCandidate(
        id = id,
        box = FrameRect(cx - width / 2f, cy - height / 2f, cx + width / 2f, cy + height / 2f),
    )
}
