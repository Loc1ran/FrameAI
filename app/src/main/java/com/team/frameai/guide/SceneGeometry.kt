package com.team.frameai.guide

import com.team.frameai.vision.FaceBoxMath
import com.team.frameai.vision.PreviewCoordinateMapper
import com.team.frameai.vision.PreviewMapping
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.hypot
import kotlin.math.sqrt

data class NormLine(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
)

enum class CameraAdjustment {
    ROTATE_CLOCKWISE,
    ROTATE_COUNTERCLOCKWISE,
    MOVE_TOWARD_CENTER,
    STEP_SIDEWAYS,
}

/**
 * Measurements from a luma grid. Angles are in the upright image, y down.
 * [rollDegrees] is positive when a near-horizontal edge descends to the right.
 * That is not a claim about a table, wall, or road.
 */
data class SceneGeometry(
    val rollDegrees: Float,
    val rollConfidence: Float,
    val verticalEdges: Boolean,
    val symmetry: Float,
    val structure: Float,
    val convergence: Float,
    val convergenceConfidence: Float,
    val detected: NormLine? = null,
    val leveled: NormLine? = null,
) {
    /** A mirrored preview reverses clockwise and counterclockwise. Positions are mirrored by the preview transform. */
    fun forViewer(mirroredPreview: Boolean): SceneGeometry {
        if (!mirroredPreview) return this
        return copy(rollDegrees = -rollDegrees)
    }
}

data class GeometryAdvice(
    val message: String,
    val confidence: Float,
    val adjustment: CameraAdjustment,
    val targetX: Double? = null,
)

/**
 * Decides whether a measured grid is strong enough to say anything.
 * Diagonals are ignored. Blank frames are not called symmetric.
 * Strong perspective on an already symmetric scene is left alone.
 */
object GeometryCoach {
    const val ROTATE_CLOCKWISE = "Rotate the phone slightly clockwise."
    const val ROTATE_COUNTERCLOCKWISE = "Rotate the phone slightly counterclockwise."
    const val CENTER_ANGLE = "Try a more centered angle."
    const val STRAIGHT_ON = "Try a more straight-on perspective."
    const val SEPARATE_SHAPES = "Try a small step sideways so these shapes don't merge."

    fun isCue(message: String): Boolean {
        return message == ROTATE_CLOCKWISE ||
            message == ROTATE_COUNTERCLOCKWISE ||
            message == CENTER_ANGLE ||
            message == STRAIGHT_ON ||
            message == SEPARATE_SHAPES
    }

    fun consider(geometry: SceneGeometry?, overlap: Float, framing: Framing): GeometryAdvice? {
        if (framing.strategy == CompositionStrategy.BREATHING_ROOM) return null
        val measured = geometry
        val roll = rollAdvice(measured)
        if (roll != null) return roll
        if (framing.strategy == CompositionStrategy.LOOK_ROOM || framing.strategy == CompositionStrategy.ENVIRONMENTAL) {
            return null
        }
        val symmetry = symmetryAdvice(measured, framing)
        if (symmetry != null) return symmetry
        val perspective = perspectiveAdvice(measured)
        if (perspective != null) return perspective
        if (overlapAdviceAllowed(framing)) return overlapAdvice(overlap)
        return null
    }

    private fun rollAdvice(geometry: SceneGeometry?): GeometryAdvice? {
        if (geometry == null) return null
        val roll = geometry.rollDegrees
        if (geometry.rollConfidence < ROLL_CONFIDENCE) return null
        if (abs(roll) !in ROLL_MIN..ROLL_MAX) return null
        val clockwise = roll > 0f
        return GeometryAdvice(
            message = if (clockwise) ROTATE_CLOCKWISE else ROTATE_COUNTERCLOCKWISE,
            confidence = geometry.rollConfidence,
            adjustment = if (clockwise) CameraAdjustment.ROTATE_CLOCKWISE else CameraAdjustment.ROTATE_COUNTERCLOCKWISE,
        )
    }

    private fun symmetryAdvice(geometry: SceneGeometry?, framing: Framing): GeometryAdvice? {
        if (geometry == null) return null
        if (!geometry.verticalEdges) return null
        if (geometry.structure < STRUCTURE_MIN || geometry.symmetry < SYMMETRY_MIN) return null
        val subject = framing.guide.subjectX ?: return null
        if (abs(subject - 0.5) <= 0.08f) return null
        return GeometryAdvice(
            message = CENTER_ANGLE,
            confidence = geometry.symmetry.coerceIn(0f, 0.9f),
            adjustment = CameraAdjustment.MOVE_TOWARD_CENTER,
            targetX = 0.5,
        )
    }

    private fun perspectiveAdvice(geometry: SceneGeometry?): GeometryAdvice? {
        if (geometry == null) return null
        if (geometry.convergenceConfidence < CONVERGENCE_CONFIDENCE) return null
        if (abs(geometry.rollDegrees) >= ROLL_MIN && geometry.rollConfidence >= ROLL_CONFIDENCE) return null
        if (geometry.symmetry >= SYMMETRY_MIN && geometry.convergence >= 0.7f) return null
        if (geometry.symmetry < 0.45f || geometry.symmetry >= SYMMETRY_MIN) return null
        if (geometry.structure < STRUCTURE_MIN) return null
        return GeometryAdvice(
            message = STRAIGHT_ON,
            confidence = geometry.convergenceConfidence.coerceAtMost(0.7f),
            adjustment = CameraAdjustment.MOVE_TOWARD_CENTER,
            targetX = 0.5,
        )
    }

    private fun overlapAdviceAllowed(framing: Framing): Boolean {
        return framing.strategy == CompositionStrategy.OPEN_SCENE ||
            framing.strategy == CompositionStrategy.NO_SUBJECT ||
            framing.guide.isReady
    }

    private fun overlapAdvice(overlap: Float): GeometryAdvice? {
        if (overlap < OVERLAP_MIN) return null
        return GeometryAdvice(
            message = SEPARATE_SHAPES,
            confidence = (overlap * 1.1f).coerceIn(0f, 0.68f),
            adjustment = CameraAdjustment.STEP_SIDEWAYS,
        )
    }

    private const val ROLL_CONFIDENCE = 0.62f
    private const val ROLL_MIN = 3f
    private const val ROLL_MAX = 14f
    private const val STRUCTURE_MIN = 0.35f
    private const val SYMMETRY_MIN = 0.86f
    private const val CONVERGENCE_CONFIDENCE = 0.65f
    private const val OVERLAP_MIN = 0.35f
}

object GeometryMeasure {
    const val COLUMNS = 32
    const val ROWS = 24

    fun measure(samples: FloatArray, width: Int = COLUMNS, height: Int = ROWS): SceneGeometry {
        require(samples.size == width * height)
        val hits = ArrayList<Hit>()
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val left = samples[y * width + (x - 1)]
                val right = samples[y * width + (x + 1)]
                val up = samples[(y - 1) * width + x]
                val down = samples[(y + 1) * width + x]
                val gx = right - left
                val gy = down - up
                val mag = hypot(gx, gy)
                if (mag < 0.16f) continue
                val horizontal = abs(gy) >= abs(gx) * 2f
                val vertical = abs(gx) >= abs(gy) * 2f
                if (!horizontal && !vertical) continue
                hits.add(Hit(x / (width - 1f), y / (height - 1f), mag, horizontal))
            }
        }
        val structure = (hits.size / 24f).coerceAtMost(1f)
        return summarize(hits, structure, symmetry(samples, width, height))
    }

    internal fun summarize(hits: List<Hit>, structure: Float, symmetry: Float): SceneGeometry {
        val horizontal = hits.filter { it.horizontal }
        val vertical = hits.filter { !it.horizontal }
        val horizontalFit = fitHorizontal(horizontal)
        val verticalFit = fitVertical(vertical)
        val chosen = when {
            horizontalFit == null -> verticalFit
            verticalFit == null -> horizontalFit
            horizontalFit.confidence >= verticalFit.confidence -> horizontalFit
            else -> verticalFit
        }
        val other = if (chosen == horizontalFit) verticalFit else horizontalFit
        var confidence = chosen?.confidence ?: 0f
        if (chosen != null && other != null && abs(chosen.roll - other.roll) > 10f) {
            confidence *= 0.35f
        }
        val roll = chosen?.roll ?: 0f
        val detected = chosen?.let { segment(it, leveled = false) }
        val leveled = chosen?.let { segment(it, leveled = true) }
        val convergence = convergence(vertical)
        return SceneGeometry(
            rollDegrees = roll,
            rollConfidence = confidence,
            verticalEdges = chosen?.horizontal == false,
            symmetry = symmetry,
            structure = structure,
            convergence = convergence.first,
            convergenceConfidence = convergence.second,
            detected = detected,
            leveled = leveled,
        )
    }

    private fun convergence(vertical: List<Hit>): Pair<Float, Float> {
        val left = slopeDxDy(vertical.filter { it.x < 0.45f })
        val right = slopeDxDy(vertical.filter { it.x > 0.55f })
        if (left == null || right == null) return 0f to 0f
        if (left > -0.07 || right < 0.07) return 0f to 0f
        val magnitude = minOf(degrees(abs(left)), degrees(right))
        val count = vertical.count { it.x < 0.45f || it.x > 0.55f }
        val confidence = (count / 24f).coerceIn(0f, 1f) * (magnitude / 10f).coerceIn(0f, 1f)
        return (magnitude / 10f).coerceIn(0f, 1f) to confidence
    }

    private data class Fit(
        val roll: Float,
        val confidence: Float,
        val cx: Float,
        val cy: Float,
        val horizontal: Boolean,
    )

    private fun fitHorizontal(hits: List<Hit>): Fit? {
        if (hits.size < 10) return null
        val meanX = hits.map { it.x }.average()
        val meanY = hits.map { it.y }.average()
        var numerator = 0.0
        var denominator = 0.0
        for (hit in hits) {
            val dx = hit.x - meanX
            numerator += dx * (hit.y - meanY)
            denominator += dx * dx
        }
        val slope = if (denominator < 1e-4) 0.0 else numerator / denominator
        val residual = hits.map { (it.y - (meanY + slope * (it.x - meanX))).toFloat() }
        return Fit(degrees(slope), confidence(hits, residual), meanX.toFloat(), meanY.toFloat(), horizontal = true)
    }

    private fun fitVertical(hits: List<Hit>): Fit? {
        if (hits.size < 10) return null
        val meanX = hits.map { it.x }.average()
        val meanY = hits.map { it.y }.average()
        var numerator = 0.0
        var denominator = 0.0
        for (hit in hits) {
            val dy = hit.y - meanY
            numerator += dy * (hit.x - meanX)
            denominator += dy * dy
        }
        val slope = if (denominator < 1e-4) 0.0 else numerator / denominator
        val residual = hits.map { (it.x - (meanX + slope * (it.y - meanY))).toFloat() }
        return Fit(degrees(-slope), confidence(hits, residual), meanX.toFloat(), meanY.toFloat(), horizontal = false)
    }

    private fun slopeDxDy(hits: List<Hit>): Double? {
        if (hits.size < 6) return null
        val meanX = hits.map { it.x }.average()
        val meanY = hits.map { it.y }.average()
        var numerator = 0.0
        var denominator = 0.0
        for (hit in hits) {
            val dy = hit.y - meanY
            numerator += dy * (hit.x - meanX)
            denominator += dy * dy
        }
        if (denominator < 1e-4) return 0.0
        return numerator / denominator
    }

    private fun confidence(hits: List<Hit>, residual: List<Float>): Float {
        val variance = residual.map { it * it }.average()
        val meanMag = hits.map { it.mag }.average().toFloat()
        return (
            (hits.size / 18f).coerceAtMost(1f) *
                (1f - (sqrt(variance).toFloat() / 0.08f).coerceIn(0f, 1f)) *
                (meanMag / 0.30f).coerceAtMost(1f)
            ).coerceIn(0f, 1f)
    }

    private fun segment(fit: Fit, leveled: Boolean): NormLine {
        val roll = if (leveled) 0.0 else Math.toRadians(fit.roll.toDouble())
        val dx: Float
        val dy: Float
        if (fit.horizontal) {
            dx = 0.28f
            dy = (kotlin.math.tan(roll) * dx).toFloat()
        } else {
            dy = 0.28f
            dx = (-kotlin.math.tan(roll) * dy).toFloat()
        }
        return NormLine(
            (fit.cx - dx).coerceIn(0.04f, 0.96f),
            (fit.cy - dy).coerceIn(0.04f, 0.96f),
            (fit.cx + dx).coerceIn(0.04f, 0.96f),
            (fit.cy + dy).coerceIn(0.04f, 0.96f),
        )
    }

    private fun degrees(slope: Double): Float = Math.toDegrees(atan(slope)).toFloat()

    private fun symmetry(samples: FloatArray, width: Int, height: Int): Float {
        var diff = 0f
        var count = 0
        for (y in 0 until height) {
            for (x in 0 until width / 2) {
                val left = samples[y * width + x]
                val right = samples[y * width + (width - 1 - x)]
                diff += abs(left - right)
                count += 1
            }
        }
        if (count == 0) return 0f
        return (1f - (diff / count) / 0.28f).coerceIn(0f, 1f)
    }
}

internal data class Hit(
    val x: Float,
    val y: Float,
    val mag: Float,
    val horizontal: Boolean,
)

object ShapeOverlap {
    fun strongest(boxes: List<FrameRect>): Float {
        var best = 0f
        for (i in boxes.indices) {
            for (j in i + 1 until boxes.size) {
                best = maxOf(best, iou(boxes[i], boxes[j]))
            }
        }
        return best
    }

    fun iou(a: FrameRect, b: FrameRect): Float {
        val left = maxOf(a.left, b.left)
        val top = maxOf(a.top, b.top)
        val right = minOf(a.right, b.right)
        val bottom = minOf(a.bottom, b.bottom)
        val width = right - left
        val height = bottom - top
        if (width <= 0f || height <= 0f) return 0f
        val intersection = width * height
        val union = a.width * a.height + b.width * b.height - intersection
        if (union <= 0.0001f) return 0f
        return (intersection / union).coerceIn(0f, 1f)
    }
}

/**
 * Maps an upright-image point into a center-cropped preview.
 * The live camera uses the same upright-to-sensor step, then CameraX outputTransform.
 */
object GeometryMapping {
    fun previewPoint(uprightX: Float, uprightY: Float, rotation: Int, mapping: PreviewMapping): Pair<Float, Float> {
        val (sensorX, sensorY) = FaceBoxMath.uprightPointToSensor(uprightX, uprightY, rotation)
        val (backX, backY) = FaceBoxMath.sensorPointToUpright(sensorX, sensorY, rotation)
        return PreviewCoordinateMapper.mapPoint(backX, backY, mapping)
    }
}
