package com.team.frameai.ui

import com.team.frameai.model.LiveGuide
import com.team.frameai.model.ShotReview
import org.junit.Assert.*
import org.junit.Test

class CaptureReviewTest {
    private val fallback = ShotReview("other", "fallback", "Use the grid.", "thirds", "none", "Keep the original.", "offline_rules")
    private val ready = LiveGuide("READY", "", 0.5, 0.5, 0.5, 0.5, true, "offline_rules")

    @Test fun usesOnlyReliableCaptureContext() {
        val result = reviewAtCapture(ready, fallback)
        assertTrue(result.summary.contains("matches the goal"))
        assertEquals("offline_rules", result.source)
        val align = reviewAtCapture(ready.copy(kind = "ALIGN_SUBJECT", isReady = false, subjectX = 0.2), fallback)
        assertTrue(align.summary.contains("does not match"))
        val absent = reviewAtCapture(ready.copy(kind = "NO_FACE", isReady = false, subjectX = null, subjectY = null), fallback)
        assertTrue(absent.summary.contains("no person"))
        assertNotEquals(result.summary, absent.summary)
    }

    @Test fun missingMockOrUnmappedContextStaysGeneric() {
        listOf(null, ready.copy(source = "demo_mock"), ready.copy(subjectX = null),
            ready.copy(subjectY = Double.NaN), ready.copy(kind = "UNKNOWN")).forEach {
            val result = reviewAtCapture(it, fallback)
            assertTrue(result.summary.contains("no reliable framing data"))
            assertEquals(fallback.improvement, result.improvement)
        }
    }
}
