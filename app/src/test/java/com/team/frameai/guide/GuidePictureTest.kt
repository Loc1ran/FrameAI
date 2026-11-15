package com.team.frameai.guide

import com.team.frameai.model.FaceBoxNormalized
import com.team.frameai.model.LiveGuide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidePictureTest {
    @Test
    fun aSmallSubjectGrowsInPlace() {
        val picture = GuidePicture.of(
            guide(GuideEngine.SMALL_MESSAGE, ready = false, targetX = 0.67, targetY = 0.33),
            faceAt(0.40f, 0.55f, 0.18f, 0.22f),
        )

        assertEquals(FrameCue.GROW, picture.cue)
        assertTrue(picture.goal.width > picture.current.width * 1.4f)
        assertEquals(picture.current.centerX, picture.goal.centerX, 0.04f)
        assertEquals(picture.current.centerY, picture.goal.centerY, 0.04f)
    }

    @Test
    fun aSubjectThatFillsTheFrameShrinksInsideItself() {
        val picture = GuidePicture.of(
            guide(GuideEngine.LARGE_MESSAGE, ready = false, targetX = 0.67, targetY = 0.33),
            faceAt(0.5f, 0.5f, 0.7f, 0.8f),
        )

        assertEquals(FrameCue.SHRINK, picture.cue)
        assertTrue(picture.goal.width < picture.current.width)
        assertEquals(picture.current.centerX, picture.goal.centerX, 0.08f)
    }

    @Test
    fun anOffSubjectMovesOntoTheThirdAtTheSameSize() {
        val picture = GuidePicture.of(
            guide(GuideEngine.ALIGN_MESSAGE, ready = false, targetX = 0.33, targetY = 0.33),
            faceAt(0.7f, 0.6f, 0.24f, 0.3f),
        )

        assertEquals(FrameCue.MOVE, picture.cue)
        assertEquals(0.33f, picture.goal.centerX, 0.04f)
        assertEquals(picture.current.width, picture.goal.width, 0.05f)
    }

    @Test
    fun aReadyFrameMarksTheSubjectInsteadOfAFloatingBox() {
        val picture = GuidePicture.of(
            guide(GuideEngine.READY_MESSAGE, ready = true, targetX = 0.67, targetY = 0.33),
            faceAt(0.66f, 0.34f, 0.22f, 0.28f),
        )

        assertEquals(FrameCue.READY, picture.cue)
        assertEquals(picture.current.centerX, picture.goal.centerX, 0.001f)
        assertEquals(picture.current.centerY, picture.goal.centerY, 0.001f)
    }

    private fun guide(message: String, ready: Boolean, targetX: Double, targetY: Double) = LiveGuide(
        kind = if (ready) GuideEngine.KIND_READY else GuideEngine.KIND_ALIGN,
        message = message,
        targetX = targetX,
        targetY = targetY,
        subjectX = 0.4,
        subjectY = 0.55,
        isReady = ready,
        source = "offline_rules",
    )

    private fun faceAt(cx: Float, cy: Float, width: Float, height: Float) = FaceBoxNormalized.fromEdges(
        cx - width / 2f,
        cy - height / 2f,
        cx + width / 2f,
        cy + height / 2f,
    )
}
