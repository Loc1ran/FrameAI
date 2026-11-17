package com.team.frameai.guide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubjectLockTest {
    @Test
    fun theSameSubjectStaysLocked() {
        val bottle = candidate(7, 0.62f, 0.55f, 0.18f, 0.28f)
        val monitor = candidate(3, 0.2f, 0.4f, 0.3f, 0.4f)
        val lock = SubjectLock()
        lock.choose(listOf(bottle), 0)
        val chosen = lock.choose(listOf(monitor, bottle), 100)
        assertEquals(7, chosen?.id)
    }

    @Test
    fun aNewTrackingNumberOnTheSameBoxDoesNotStartOver() {
        val lock = SubjectLock()
        lock.choose(listOf(candidate(7, 0.60f, 0.50f, 0.20f, 0.30f)), 0)
        val moved = candidate(11, 0.62f, 0.52f, 0.20f, 0.30f)
        assertEquals(11, lock.choose(listOf(moved), 100)?.id)
    }

    @Test
    fun aHeadInsideABodyBecomesTheBody() {
        val head = candidate(1, 0.50f, 0.22f, 0.16f, 0.12f)
        val body = candidate(2, 0.50f, 0.48f, 0.34f, 0.70f)
        val lock = SubjectLock()
        lock.choose(listOf(head), 0)
        assertEquals(2, lock.choose(listOf(head, body), 100)?.id)
    }

    @Test
    fun aNewSubjectIsTheOneNearestTheCenter() {
        val bottle = candidate(7, 0.55f, 0.5f, 0.16f, 0.3f)
        val monitor = candidate(3, 0.15f, 0.4f, 0.24f, 0.4f)
        val chosen = SubjectLock().choose(listOf(monitor, bottle), 0)
        assertEquals(7, chosen?.id)
    }

    @Test
    fun aBoxThatFillsTheFrameIsNotASubject() {
        val background = candidate(1, 0.5f, 0.5f, 0.98f, 0.98f)
        assertNull(SubjectLock().choose(listOf(background), 0))
    }

    @Test fun missingFrameDoesNotInventABoxOrSwitchToAnotherObject() {
        val lock = SubjectLock()
        val bottle = candidate(7, 0.6f, 0.5f, 0.16f, 0.3f)
        val monitor = candidate(3, 0.5f, 0.5f, 0.3f, 0.4f)
        lock.choose(listOf(bottle), 0)
        assertEquals(7, lock.choose(emptyList(), 100)?.id)
        assertEquals(7, lock.choose(listOf(monitor), 400)?.id)
        assertTrue(lock.isLocked(400))
        assertEquals(7, lock.choose(listOf(monitor, bottle), 800)?.id)
        assertFalse(lock.isLocked(1_801))
        assertEquals(7, lock.choose(listOf(monitor), 1_801)?.id)
        assertEquals(3, lock.choose(listOf(monitor), 3_400)?.id)
    }

    @Test fun aTapSelectsThatObjectInsteadOfTheNearestCenter() {
        val bottle = candidate(7, 0.8f, 0.7f, 0.16f, 0.24f)
        val monitor = candidate(3, 0.2f, 0.3f, 0.2f, 0.3f)
        val lock = SubjectLock()
        lock.select(3, 0)
        assertEquals(3, lock.choose(listOf(bottle, monitor), 100)?.id)
        assertEquals(3, lock.choose(listOf(bottle), 200)?.id)
        assertTrue(lock.hasSelection())
        assertEquals(7, lock.choose(listOf(bottle), 2_800)?.id)
        assertFalse(lock.hasSelection())
    }

    @Test fun rejectsInvalidAndDegenerateBounds() {
        listOf(
            FrameRect(Float.NaN, 0f, 0.5f, 0.5f),
            FrameRect(-0.1f, 0f, 0.5f, 0.5f),
            FrameRect(0.8f, 0.2f, 0.1f, 0.5f),
        ).forEach { assertNull(SubjectLock().choose(listOf(TrackCandidate(1, it)), 0)) }
    }

    private fun candidate(id: Int, cx: Float, cy: Float, width: Float, height: Float) = TrackCandidate(
        id = id,
        box = FrameRect(cx - width / 2f, cy - height / 2f, cx + width / 2f, cy + height / 2f),
    )
}
