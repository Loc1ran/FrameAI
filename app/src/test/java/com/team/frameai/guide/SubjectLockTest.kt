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
        assertNull(lock.choose(emptyList(), 100))
        assertNull(lock.choose(listOf(monitor), 400))
        assertTrue(lock.isLocked(400))
        assertEquals(7, lock.choose(listOf(monitor, bottle), 800)?.id)
        assertFalse(lock.isLocked(1_801))
        assertEquals(3, lock.choose(listOf(monitor), 1_801)?.id)
    }

    @Test fun aTapSelectsThatObjectInsteadOfTheNearestCenter() {
        val bottle = candidate(7, 0.8f, 0.7f, 0.16f, 0.24f)
        val monitor = candidate(3, 0.2f, 0.3f, 0.2f, 0.3f)
        val lock = SubjectLock()
        lock.select(3, 0)
        assertEquals(3, lock.choose(listOf(bottle, monitor), 100)?.id)
        assertNull(lock.choose(listOf(bottle), 200))
        assertTrue(lock.hasSelection())
        assertEquals(7, lock.choose(listOf(bottle), 1_800)?.id)
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
