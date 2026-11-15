package com.team.frameai.guide

import com.team.frameai.model.LiveCoach
import com.team.frameai.model.LiveGuide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveCoachSessionTest {
    private val offline = LiveGuide(
        kind = "ALIGN_SUBJECT",
        message = "on device",
        targetX = 0.33,
        targetY = 0.67,
        subjectX = 0.4,
        subjectY = 0.5,
        isReady = false,
        source = "offline_rules",
    )

    @Test
    fun geminiReplyBecomesThePictureAndKeepsZoom() {
        val memory = LiveCoachSession.remember(CoachMemory(), cloud(zoom = "in", ratio = 1.6), now = 1_000)
        val shown = LiveCoachSession.show(offline, memory, now = 1_000)

        assertEquals("gemini", shown.guide.source)
        assertEquals(GuideEngine.SMALL_MESSAGE, shown.guide.message)
        assertEquals("in", shown.zoom)
        assertEquals(1.6, shown.targetZoomRatio, 0.001)
        assertEquals("warm_film", shown.lutId)
        assertEquals(0.67, shown.guide.targetX, 0.001)
    }

    @Test
    fun failedReplyHoldsGeminiForEightSecondsThenUsesTheDevice() {
        val memory = LiveCoachSession.remember(CoachMemory(), cloud(zoom = "out"), now = 5_000)
        val held = LiveCoachSession.remember(memory, latest = null, now = 9_000)
        val during = LiveCoachSession.show(offline, held, now = 13_000)
        val after = LiveCoachSession.show(offline, held, now = 13_001)

        assertEquals("gemini", during.guide.source)
        assertEquals("out", during.zoom)
        assertEquals(GuideEngine.LARGE_MESSAGE, during.guide.message)
        assertEquals("offline_rules", after.guide.source)
        assertEquals("hold", after.zoom)
        assertNull(after.lutId)
    }

    @Test
    fun deviceZoomStaysWhenTheServerHasNotAnswered() {
        val device = offline.copy(zoom = "in", targetZoomRatio = 2.4)
        val shown = LiveCoachSession.show(device, CoachMemory(), now = 0)

        assertEquals("in", shown.zoom)
        assertEquals(2.4, shown.targetZoomRatio, 0.001)
    }

    @Test
    fun holdZoomDoesNotAskTheLensToMove() {
        val memory = LiveCoachSession.remember(CoachMemory(), cloud(zoom = "hold", ready = true), now = 0)
        val shown = LiveCoachSession.show(offline, memory, now = 0)

        assertEquals("hold", shown.zoom)
        assertEquals(GuideEngine.KIND_READY, shown.guide.kind)
    }

    @Test
    fun horizonReplyDropsTheSubjectColumnSoTheLineCanDraw() {
        val reply = cloud(zoom = "hold").copy(
            referenceId = "landscape_low_horizon",
            sceneType = "landscape",
            subjectX = 0.2,
            subjectY = 0.72,
        )
        val memory = LiveCoachSession.remember(CoachMemory(), reply, now = 0)
        val shown = LiveCoachSession.show(offline, memory, now = 0)

        assertNull(shown.guide.subjectX)
        assertEquals(0.72, shown.guide.subjectY!!, 0.001)
    }

    @Test
    fun noneLutLeavesTheOnDeviceLookAlone() {
        val reply = cloud(zoom = "hold").copy(suggestedLutId = "none")
        val memory = LiveCoachSession.remember(CoachMemory(), reply, now = 0)
        val shown = LiveCoachSession.show(offline, memory, now = 0)

        assertNull(shown.lutId)
    }

    private fun cloud(zoom: String, ratio: Double = 2.0, ready: Boolean = false) = LiveCoach(
        sceneType = "portrait",
        subject = "person",
        referenceId = "portrait_space_left",
        message = "Zoom until the person fills the dashed frame.",
        zoom = zoom,
        targetZoomRatio = ratio,
        targetX = 0.67,
        targetY = 0.33,
        subjectX = 0.48,
        subjectY = 0.46,
        isReady = ready,
        suggestedLutId = "warm_film",
        source = "gemini",
    )
}
