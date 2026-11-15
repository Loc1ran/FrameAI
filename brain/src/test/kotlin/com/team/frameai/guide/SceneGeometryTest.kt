package com.team.frameai.guide

import com.team.frameai.model.FaceBoxNormalized
import com.team.frameai.model.SceneRead
import com.team.frameai.vision.PreviewMapping
import kotlin.math.abs
import kotlin.math.tan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SceneGeometryTest {
    private val engine = GuideEngine()

    @Test
    fun aSlightlyTiltedHorizontalEdgeAsksForTheMatchingRotation() {
        val slope = tan(Math.toRadians(6.0)).toFloat()
        val measured = GeometryMeasure.measure(split(horizontal = true, slope = slope))
        assertTrue(measured.rollDegrees > 4f, "roll was ${measured.rollDegrees}")
        assertTrue(measured.rollDegrees < 10f, "roll was ${measured.rollDegrees}")
        assertTrue(measured.rollConfidence >= 0.62f, "confidence was ${measured.rollConfidence}")
        val line = measured.detected
        assertTrue(line != null)
        if (line!!.x2 >= line.x1) assertTrue(line.y2 > line.y1) else assertTrue(line.y1 > line.y2)
        val guide = engine.suggest(portrait(0.67f, 0.36f), scene = scene(measured))
        assertEquals(GeometryCoach.ROTATE_CLOCKWISE, guide.message)
        assertFalse(guide.isReady)
        assertEquals("hold", guide.zoom)
        assertNoObjectNames(guide.message)
    }

    @Test
    fun aSlightlyTiltedVerticalEdgeUsesTheSameRotationDirection() {
        val slope = -tan(Math.toRadians(6.0)).toFloat()
        val measured = GeometryMeasure.measure(split(horizontal = false, slope = slope))
        assertTrue(measured.verticalEdges)
        assertTrue(measured.rollDegrees > 4f, "roll was ${measured.rollDegrees}")
        val guide = engine.suggest(null, scene = scene(measured))
        assertEquals(GeometryCoach.ROTATE_CLOCKWISE, guide.message)
    }

    @Test
    fun theOppositeTiltAsksForTheOppositeRotation() {
        val slope = -tan(Math.toRadians(6.0)).toFloat()
        val measured = GeometryMeasure.measure(split(horizontal = true, slope = slope))
        assertTrue(measured.rollDegrees < -4f, "roll was ${measured.rollDegrees}")
        assertEquals(
            GeometryCoach.ROTATE_COUNTERCLOCKWISE,
            engine.suggest(null, scene = scene(measured)).message,
        )
    }

    @Test
    fun aLevelEdgeAndABlankFrameStayQuiet() {
        val level = GeometryMeasure.measure(split(horizontal = true, slope = 0f))
        assertTrue(abs(level.rollDegrees) < 2f, "roll was ${level.rollDegrees}")
        assertNull(GeometryCoach.consider(level, 0f, openFrame()))
        val blank = GeometryMeasure.measure(FloatArray(GeometryMeasure.COLUMNS * GeometryMeasure.ROWS) { 0.45f })
        assertTrue(blank.structure < 0.35f)
        assertNull(GeometryCoach.consider(blank, 0f, openFrame()))
        assertEquals(GuideEngine.OPEN_READY, engine.suggest(null, scene = scene(blank)).message)
    }

    @Test
    fun anIntentionalDiagonalIsNotStraightened() {
        val slope = tan(Math.toRadians(40.0)).toFloat()
        val measured = GeometryMeasure.measure(split(horizontal = true, slope = slope))
        assertTrue(measured.rollConfidence < 0.62f || abs(measured.rollDegrees) !in 3f..14f)
        assertNull(GeometryCoach.consider(measured, 0f, openFrame()))
    }

    @Test
    fun strongSymmetryCanCenterAndAsymmetryCannot() {
        val centered = GeometryMeasure.measure(bar(0.50f))
        assertTrue(centered.symmetry >= 0.86f, "symmetry was ${centered.symmetry}")
        assertTrue(centered.structure >= 0.35f, "structure was ${centered.structure}")
        val guide = engine.suggest(portrait(0.33f, 0.36f), scene = scene(centered))
        assertEquals(GeometryCoach.CENTER_ANGLE, guide.message)
        assertEquals(0.5, guide.targetX, 0.0001)
        assertEquals(CoachAction.MOVE_LEFT, CoachAction.slightMove(0.33f, 0.36f, guide.targetX.toFloat(), guide.targetY.toFloat()))

        val already = engine.suggest(portrait(0.50f, 0.36f), scene = scene(centered))
        assertNotEquals(GeometryCoach.CENTER_ANGLE, already.message)

        val uneven = GeometryMeasure.measure(bar(0.25f))
        assertTrue(uneven.symmetry < 0.86f, "symmetry was ${uneven.symmetry}")
        val thirds = engine.suggest(portrait(0.67f, 0.36f), scene = scene(uneven))
        assertFalse(GeometryCoach.isCue(thirds.message))
    }

    @Test
    fun strongConvergenceOnASymmetricSceneIsNotTreatedAsAMistake() {
        val hallway = GeometryMeasure.measure(hallway(shift = 0f))
        assertTrue(hallway.convergence >= 0.7f, "convergence was ${hallway.convergence}")
        assertTrue(hallway.symmetry >= 0.86f, "symmetry was ${hallway.symmetry}")
        assertNull(GeometryCoach.consider(hallway, 0f, openFrame()))
        assertEquals(GuideEngine.OPEN_READY, engine.suggest(null, scene = scene(hallway)).message)
    }

    @Test
    fun moderateConvergenceCanAskForAStraighterViewpoint() {
        val left = verticalHits(x0 = 0.22f, slope = -0.28f)
        val right = verticalHits(x0 = 0.62f, slope = 0.28f)
        val measured = GeometryMeasure.summarize(left + right, structure = 0.8f, symmetry = 0.62f)
        assertTrue(measured.convergenceConfidence >= 0.65f, "confidence was ${measured.convergenceConfidence}")
        assertEquals(GeometryCoach.STRAIGHT_ON, GeometryCoach.consider(measured, 0f, openFrame())?.message)
        assertEquals(0.5, GeometryCoach.consider(measured, 0f, openFrame())?.targetX)
    }

    @Test
    fun overlapIsMentionedOnlyWhenTwoBoxesActuallyIntersect() {
        val separate = ShapeOverlap.iou(FrameRect(0.1f, 0.1f, 0.3f, 0.4f), FrameRect(0.6f, 0.2f, 0.9f, 0.7f))
        assertEquals(0f, separate)
        val overlapping = ShapeOverlap.strongest(
            listOf(FrameRect(0.2f, 0.2f, 0.7f, 0.8f), FrameRect(0.3f, 0.3f, 0.8f, 0.9f)),
        )
        assertTrue(overlapping >= 0.35f, "iou was $overlapping")
        val open = engine.suggest(null, scene = scene(null).copy(shapeOverlap = overlapping))
        assertEquals(GeometryCoach.SEPARATE_SHAPES, open.message)
        assertFalse(open.message.contains("left", ignoreCase = true))
        val weak = engine.suggest(null, scene = scene(null).copy(shapeOverlap = 0.1f))
        assertEquals(GuideEngine.OPEN_READY, weak.message)
        val busyPortrait = engine.suggest(portrait(0.20f, 0.36f), scene = scene(null).copy(shapeOverlap = overlapping))
        assertNotEquals(GeometryCoach.SEPARATE_SHAPES, busyPortrait.message)
    }

    @Test
    fun rollOutranksSymmetryAndBreathingRoomOutranksRoll() {
        val tilted = GeometryMeasure.measure(split(horizontal = true, slope = tan(Math.toRadians(6.0)).toFloat()))
        val both = tilted.copy(symmetry = 0.95f, structure = 0.9f)
        assertEquals(GeometryCoach.ROTATE_CLOCKWISE, GeometryCoach.consider(both, 0.6f, openFrame())?.message)
        val clipped = FaceBoxNormalized(0.10f, 0.33f, 0f, 0.18f, 0.22f, 0.48f)
        val guide = engine.suggest(clipped, scene = scene(both))
        assertEquals(GuideEngine.BREATHING_CUE, guide.message)
    }

    @Test
    fun lookRoomKeepsPriorityOverSymmetryAndThirdsRemainWithoutEvidence() {
        val centered = GeometryMeasure.measure(bar(0.50f))
        val looking = engine.suggest(portrait(0.50f, 0.36f, yaw = 25f), scene = scene(centered))
        assertEquals(GuideEngine.LOOK_CUE, looking.message)
        val plain = engine.suggest(portrait(0.33f, 0.36f), scene = scene(null))
        assertFalse(GeometryCoach.isCue(plain.message))
        assertEquals(0.33, plain.targetX, 0.05)
    }

    @Test
    fun aLevelHorizonStaysAPlacementCueAndATiltedOneBecomesRotation() {
        val level = GeometryMeasure.measure(split(horizontal = true, slope = 0f))
        val placed = engine.suggest(
            null,
            scene = SceneRead(0.4f, 0.4f, 0f, topLuma = 0.8f, bottomLuma = 0.3f, horizonY = 0.5f, geometry = level),
        )
        assertTrue(
            placed.message == GuideEngine.HORIZON_LOWER_CUE || placed.message == GuideEngine.HORIZON_HIGHER_CUE,
            placed.message,
        )
        val tilted = GeometryMeasure.measure(split(horizontal = true, slope = tan(Math.toRadians(6.0)).toFloat()))
        val rolled = engine.suggest(
            null,
            scene = SceneRead(0.4f, 0.4f, 0f, horizonY = 0.5f, geometry = tilted),
        )
        assertEquals(GeometryCoach.ROTATE_CLOCKWISE, rolled.message)
    }

    @Test
    fun lowConfidenceMeasurementsProduceNoRecommendation() {
        val shy = SceneGeometry(
            rollDegrees = 8f,
            rollConfidence = 0.4f,
            verticalEdges = false,
            symmetry = 0.5f,
            structure = 0.2f,
            convergence = 0.2f,
            convergenceConfidence = 0.2f,
        )
        assertNull(GeometryCoach.consider(shy, 0.1f, openFrame()))
        val sparse = GeometryMeasure.summarize(
            verticalHits(0.5f, 0f).take(4),
            structure = 0.1f,
            symmetry = 0.95f,
        )
        assertNull(GeometryCoach.consider(sparse, 0f, openFrame()))
    }

    @Test
    fun aGeometryCueHoldsAcrossAQuietFrameThenYields() {
        val tilted = scene(GeometryMeasure.measure(split(horizontal = true, slope = tan(Math.toRadians(6.0)).toFloat())))
        val level = scene(null)
        val smoother = GuideSmoother(engine, minIntervalMs = 0)
        val face = portrait(0.67f, 0.36f)
        val first = smoother.update(face, 0L, scene = tilted)
        assertEquals(GeometryCoach.ROTATE_CLOCKWISE, first.message)
        val held = smoother.update(face, 100L, scene = level)
        assertEquals(GeometryCoach.ROTATE_CLOCKWISE, held.message)
        var latest = held
        repeat(24) { step ->
            latest = smoother.update(face, 1_000L + step * 100L, scene = level)
        }
        assertFalse(GeometryCoach.isCue(latest.message))
    }

    @Test
    fun aMirroredPreviewReversesRotationAndPreviewMappingKeepsThePoint() {
        val measured = GeometryMeasure.measure(split(horizontal = true, slope = tan(Math.toRadians(6.0)).toFloat()))
        val mirrored = measured.forViewer(mirroredPreview = true)
        assertEquals(measured.rollDegrees, -mirrored.rollDegrees, 0.001f)
        assertEquals(measured.detected, mirrored.detected)
        assertEquals(
            GeometryCoach.ROTATE_COUNTERCLOCKWISE,
            GeometryCoach.consider(mirrored, 0f, openFrame())?.message,
        )
        val crop = PreviewMapping(1000, 2000, 1000, 1000)
        val (_, y) = GeometryMapping.previewPoint(0.5f, 0.1f, rotation = 90, crop)
        assertEquals(-0.3f, y, 0.02f)
        val (straightX, straightY) = GeometryMapping.previewPoint(0.2f, 0.4f, rotation = 270, crop)
        val expected = com.team.frameai.vision.PreviewCoordinateMapper.mapPoint(0.2f, 0.4f, crop)
        assertEquals(expected.first, straightX, 0.001f)
        assertEquals(expected.second, straightY, 0.001f)
        val mirroredView = crop.copy(mirrorX = true)
        val (mx, _) = GeometryMapping.previewPoint(0.2f, 0.5f, rotation = 0, mirroredView)
        val rear = GeometryMapping.previewPoint(0.2f, 0.5f, rotation = 0, crop)
        assertEquals(1f - rear.first, mx, 0.001f)
    }

    private fun openFrame() = engine.advise(null, scene = SceneRead(0.4f, 0.4f, 0f))

    private fun scene(geometry: SceneGeometry?) = SceneRead(0.4f, 0.42f, 0.02f, geometry = geometry)

    private fun portrait(x: Float, y: Float, yaw: Float? = null): FaceBoxNormalized {
        return FaceBoxNormalized(
            centerX = x,
            centerY = y,
            left = x - 0.15f,
            top = y - 0.20f,
            right = x + 0.15f,
            bottom = y + 0.20f,
            yawDegrees = yaw,
        )
    }

    private fun split(horizontal: Boolean, slope: Float): FloatArray {
        val width = GeometryMeasure.COLUMNS
        val height = GeometryMeasure.ROWS
        val samples = FloatArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val nx = x / (width - 1f)
                val ny = y / (height - 1f)
                val dark = if (horizontal) ny < 0.55f + slope * (nx - 0.5f) else nx < 0.48f + slope * (ny - 0.5f)
                samples[y * width + x] = if (dark) 0.15f else 0.85f
            }
        }
        return samples
    }

    private fun bar(center: Float): FloatArray {
        val width = GeometryMeasure.COLUMNS
        val height = GeometryMeasure.ROWS
        val samples = FloatArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val nx = x / (width - 1f)
                samples[y * width + x] = if (abs(nx - center) < 0.08f) 0.9f else 0.12f
            }
        }
        return samples
    }

    private fun hallway(shift: Float): FloatArray {
        val width = GeometryMeasure.COLUMNS
        val height = GeometryMeasure.ROWS
        val samples = FloatArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val nx = x / (width - 1f)
                val ny = y / (height - 1f)
                val left = 0.40f + shift - 0.22f * ny
                val right = 0.60f + shift + 0.22f * ny
                samples[y * width + x] = if (nx in left..right) 0.82f else 0.14f
            }
        }
        return samples
    }

    private fun verticalHits(x0: Float, slope: Float): List<Hit> {
        return (0 until 12).map { index ->
            val y = 0.12f + index * (0.76f / 11f)
            Hit(x = x0 + slope * (y - 0.5f), y = y, mag = 0.55f, horizontal = false)
        }
    }

    private fun assertNoObjectNames(message: String) {
        listOf("table", "bottle", "wall", "building", "road", "door", "window").forEach { word ->
            assertFalse(message.contains(word, ignoreCase = true), message)
        }
    }
}
