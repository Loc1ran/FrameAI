package com.team.frameai.vision

import com.team.frameai.model.FaceBoxNormalized
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FaceMappingTest {
    @Test
    fun uprightSizeSwapsOnSidewaysFrames() {
        assertEquals(1920 to 1080, FaceBoxMath.uprightSize(1920, 1080, 0))
        assertEquals(1080 to 1920, FaceBoxMath.uprightSize(1920, 1080, 90))
        assertEquals(1080 to 1920, FaceBoxMath.uprightSize(1920, 1080, 270))
        assertEquals(1080 to 1920, FaceBoxMath.uprightSize(1920, 1080, -90))
        assertFailsWith<IllegalArgumentException> { FaceBoxMath.uprightSize(1920, 1080, 45) }
    }

    @Test
    fun clockwiseRotationMovesCorners() {
        assertPoint(0f, 0f, 90, 1f, 0f)
        assertPoint(1f, 0f, 90, 1f, 1f)
        assertPoint(0f, 1f, 90, 0f, 0f)
        assertPoint(1f, 1f, 90, 0f, 1f)
        assertPoint(0f, 0f, 180, 1f, 1f)
        assertPoint(0f, 0f, 270, 0f, 1f)
        val sensor = FaceBoxNormalized(0f, 0f, 0.2f, 0.4f)
        val upright = FaceBoxMath.sensorBoxToUpright(sensor, 90)
        assertEquals(0.6f, upright.left, 0.0001f)
        assertEquals(0f, upright.top, 0.0001f)
        assertEquals(1f, upright.right, 0.0001f)
        assertEquals(0.2f, upright.bottom, 0.0001f)
    }

    @Test
    fun normalizeUsesUprightPixelsAndDropsEmptyBoxes() {
        val box = FaceBoxMath.normalizeUpright(0f, 0f, 100f, 50f, 200, 100)
        assertEquals(0.25f, box!!.centerX, 0.0001f)
        assertEquals(0.25f, box.centerY, 0.0001f)
        assertNull(FaceBoxMath.normalizeUpright(0f, 0f, 0f, 10f, 100, 100))
        assertNull(FaceBoxMath.normalizeUpright(0f, 0f, 10f, 10f, 0, 100))
    }

    @Test
    fun primaryFaceIsTheLargest() {
        val small = FaceBoxNormalized(0.1f, 0.1f, 0.2f, 0.2f)
        val large = FaceBoxNormalized(0.4f, 0.4f, 0.8f, 0.9f)
        assertEquals(large, FaceBoxMath.selectPrimary(listOf(small, large)))
        assertNull(FaceBoxMath.selectPrimary(emptyList()))
    }

    @Test
    fun centerCropAndMirrorMatchPreviewView() {
        val square = PreviewMapping(100, 100, 100, 100)
        val (sameX, sameY) = PreviewCoordinateMapper.mapPoint(0.25f, 0.40f, square)
        assertEquals(0.25f, sameX, 0.0001f)
        assertEquals(0.40f, sameY, 0.0001f)
        val (mirroredX, mirroredY) = PreviewCoordinateMapper.mapPoint(
            0.25f,
            0.40f,
            square.copy(mirrorX = true),
        )
        assertEquals(0.75f, mirroredX, 0.0001f)
        assertEquals(0.40f, mirroredY, 0.0001f)

        val wide = PreviewMapping(200, 100, 100, 100)
        val (centerX, centerY) = PreviewCoordinateMapper.mapPoint(0.5f, 0.5f, wide)
        assertEquals(0.5f, centerX, 0.0001f)
        assertEquals(0.5f, centerY, 0.0001f)
        val (leftEdge, _) = PreviewCoordinateMapper.mapPoint(0.25f, 0.5f, wide)
        assertEquals(0f, leftEdge, 0.0001f)
        val visible = FaceBoxNormalized(0.30f, 0.30f, 0.50f, 0.50f)
        assertTrue(PreviewCoordinateMapper.mapBox(visible, wide) != null)
        val croppedAway = FaceBoxNormalized(0.00f, 0.40f, 0.05f, 0.60f)
        assertNull(PreviewCoordinateMapper.mapBox(croppedAway, wide))
    }

    private fun assertPoint(nx: Float, ny: Float, rotation: Int, expectedX: Float, expectedY: Float) {
        val (x, y) = FaceBoxMath.sensorPointToUpright(nx, ny, rotation)
        assertEquals(expectedX, x, 0.0001f)
        assertEquals(expectedY, y, 0.0001f)
    }
}
