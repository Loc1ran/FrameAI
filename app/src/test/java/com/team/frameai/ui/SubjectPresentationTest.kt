package com.team.frameai.ui

import com.team.frameai.guide.GuideEngine
import com.team.frameai.model.FaceBoxNormalized
import org.junit.Assert.*
import org.junit.Test

class SubjectPresentationTest {
    private val face = FaceBoxNormalized.fromEdges(0.1f, 0.2f, 0.3f, 0.5f)

    @Test fun localDetectionProvidesBoxAndMatchingInstructionWithoutCloudReply() {
        val local = GuideEngine().advise(face).guide
        val shown = SubjectPresentation.guide(local, face)
        assertSame(local, shown)
        assertEquals(local.message, framingHint(shown, face).title)
        assertFalse(shown!!.hideGoal)
    }

    @Test fun aLostDetectionDoesNotKeepAnOldReadyBox() {
        val old = GuideEngine().advise(face).guide.copy(isReady = true)
        assertNull(SubjectPresentation.guide(old, null))
        assertFalse(framingHint(SubjectPresentation.guide(old, null)).aligned)
    }

    @Test fun validatedViewpointCuesAreStillPreserved() {
        val directed = GuideEngine().advise(face).guide.copy(hideGoal = true, source = "gemini")
        assertSame(directed, SubjectPresentation.guide(directed, face))
    }

    @Test fun changingSubjectsResetsTheOldBoxAndRateLimit() {
        val smoother = com.team.frameai.guide.GuideSmoother(minIntervalMs = 10_000, smoothing = 0.1f)
        smoother.update(face, 1_000)
        smoother.reset()
        assertNull(smoother.lastKind())
        val next = FaceBoxNormalized.fromEdges(0.6f, 0.2f, 0.8f, 0.5f)
        val guide = smoother.update(next, 1_001)
        assertEquals(next.anchorX.toDouble(), guide.subjectX!!, 0.001)
    }
}
