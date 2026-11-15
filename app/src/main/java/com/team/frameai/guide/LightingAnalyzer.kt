package com.team.frameai.guide

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Luminance is normalized from the camera's 8-bit Y plane: 0 is black and 1 is 255.
 * A night frame can be dark on purpose, and snow can be bright on purpose.
 * These cutoffs only mark extremes. They do not by themselves become advice.
 */
object LightingThresholds {
    const val VERY_DARK = 0.08f
    const val VERY_BRIGHT = 0.92f
    const val CLIPPED = 0.97f
    const val WIDESPREAD_CLIP = 0.18f
    const val SUBJECT_CLIP = 0.22f
    const val SUBJECT_DARK_MEAN = 0.16f
    const val SUBJECT_DARK_SHARE = 0.55f
    const val LIT_BACKGROUND = 0.28f
    const val BACKLIGHT_GAP = 0.28f
    const val BACKLIGHT_SEVERE_GAP = 0.40f
    const val SUBJECT_NOT_BRIGHT = 0.42f
    const val BACKGROUND_BRIGHT = 0.55f
    const val HARSH_STEP = 0.45f
    const val HARSH_EDGE_SHARE = 0.10f
    const val HARSH_DARK_SHARE = 0.18f
    const val HARSH_BRIGHT_SHARE = 0.12f
    const val UNEVEN_SPREAD = 0.28f
    const val LOW_CONTRAST_GAP = 0.06f
    const val MIN_SUBJECT_CELLS = 8
    const val MIN_QUADRANT_CELLS = 2
    const val MIN_CONFIDENCE = 0.55f
    const val SEVERE = 0.80f
    const val READY_FLOOR = 0.55f
    const val SHADE_DARK = 0.18f
    const val SHADE_BRIGHT = 0.82f
}

enum class LightingIssue {
    OVEREXPOSURE,
    UNDEREXPOSURE,
    BACKLIT_SUBJECT,
    HARSH_LIGHTING,
    UNEVEN_ILLUMINATION,
    LOW_SUBJECT_BACKGROUND_CONTRAST,
    NONE,
}

data class LightingRead(
    val mean: Float,
    val median: Float,
    val darkShare: Float,
    val midShare: Float,
    val brightShare: Float,
    val clippedShare: Float,
    val clippedQuadrants: Int,
    val subjectMean: Float?,
    val backgroundMean: Float?,
    val subjectClippedShare: Float?,
    val subjectDarkShare: Float?,
    val subjectSpread: Float?,
    val subjectCells: Int,
    val strongEdgeShare: Float,
    val edgeQuadrants: Int,
    val columns: Int,
    val rows: Int,
    val bands: ByteArray,
)

/** Preview positions for the optional lighting tint. Bands stay in upright-image order. */
data class LightingMap(
    val columns: Int,
    val rows: Int,
    val bands: ByteArray,
    val xs: FloatArray,
    val ys: FloatArray,
)

/**
 * Measures a luma grid that is already in upright normalized coordinates.
 * A missing or unusable subject box leaves the subject fields empty.
 */
object LightingAnalyzer {
    fun measure(
        samples: FloatArray,
        columns: Int,
        rows: Int,
        subject: FrameRect? = null,
    ): LightingRead {
        require(samples.size == columns * rows)
        val sorted = samples.copyOf()
        sorted.sort()
        val median = if (sorted.size % 2 == 1) {
            sorted[sorted.size / 2]
        } else {
            (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2f
        }
        var sum = 0f
        var dark = 0
        var bright = 0
        var clipped = 0
        val clippedQuad = IntArray(4)
        val bands = ByteArray(samples.size)
        for (index in samples.indices) {
            val luma = samples[index]
            sum += luma
            if (luma <= LightingThresholds.VERY_DARK) dark += 1
            if (luma >= LightingThresholds.VERY_BRIGHT) bright += 1
            if (luma >= LightingThresholds.CLIPPED) {
                clipped += 1
                clippedQuad[quadrant(index, columns, rows)] += 1
            }
            bands[index] = when {
                luma <= LightingThresholds.SHADE_DARK -> 1
                luma >= LightingThresholds.SHADE_BRIGHT -> 2
                else -> 0
            }
        }
        val count = samples.size.coerceAtLeast(1)
        val box = usable(subject)
        val inside = ArrayList<Int>()
        if (box != null) {
            for (row in 0 until rows) {
                for (col in 0 until columns) {
                    val nx = (col + 0.5f) / columns
                    val ny = (row + 0.5f) / rows
                    if (nx in box.left..box.right && ny in box.top..box.bottom) {
                        inside.add(row * columns + col)
                    }
                }
            }
        }
        var subjectSum = 0f
        var subjectClipped = 0
        var subjectDark = 0
        for (index in inside) {
            val luma = samples[index]
            subjectSum += luma
            if (luma >= LightingThresholds.CLIPPED) subjectClipped += 1
            if (luma <= LightingThresholds.VERY_DARK) subjectDark += 1
        }
        val outsideCount = count - inside.size
        val backgroundSum = sum - subjectSum
        val edges = strongEdges(samples, columns, rows)
        return LightingRead(
            mean = sum / count,
            median = median,
            darkShare = dark / count.toFloat(),
            midShare = (count - dark - bright).coerceAtLeast(0) / count.toFloat(),
            brightShare = bright / count.toFloat(),
            clippedShare = clipped / count.toFloat(),
            clippedQuadrants = clippedQuad.count { it >= LightingThresholds.MIN_QUADRANT_CELLS },
            subjectMean = if (inside.size < LightingThresholds.MIN_SUBJECT_CELLS) null else subjectSum / inside.size,
            backgroundMean = if (outsideCount < LightingThresholds.MIN_SUBJECT_CELLS) null else backgroundSum / outsideCount,
            subjectClippedShare = if (inside.size < LightingThresholds.MIN_SUBJECT_CELLS) null else subjectClipped / inside.size.toFloat(),
            subjectDarkShare = if (inside.size < LightingThresholds.MIN_SUBJECT_CELLS) null else subjectDark / inside.size.toFloat(),
            subjectSpread = spread(samples, columns, rows, box),
            subjectCells = inside.size,
            strongEdgeShare = edges.first,
            edgeQuadrants = edges.second,
            columns = columns,
            rows = rows,
            bands = bands,
        )
    }

    private fun usable(box: FrameRect?): FrameRect? {
        if (box == null) return null
        if (!box.left.isFinite() || !box.top.isFinite() || !box.right.isFinite() || !box.bottom.isFinite()) return null
        val left = min(box.left, box.right).coerceIn(0f, 1f)
        val right = max(box.left, box.right).coerceIn(0f, 1f)
        val top = min(box.top, box.bottom).coerceIn(0f, 1f)
        val bottom = max(box.top, box.bottom).coerceIn(0f, 1f)
        if (right - left < 0.04f || bottom - top < 0.04f) return null
        return FrameRect(left, top, right, bottom)
    }

    private fun spread(samples: FloatArray, columns: Int, rows: Int, box: FrameRect?): Float? {
        if (box == null) return null
        val sums = FloatArray(4)
        val counts = IntArray(4)
        val midX = (box.left + box.right) / 2f
        val midY = (box.top + box.bottom) / 2f
        for (row in 0 until rows) {
            for (col in 0 until columns) {
                val nx = (col + 0.5f) / columns
                val ny = (row + 0.5f) / rows
                if (nx !in box.left..box.right || ny !in box.top..box.bottom) continue
                val quadrant = (if (nx >= midX) 1 else 0) + (if (ny >= midY) 2 else 0)
                sums[quadrant] += samples[row * columns + col]
                counts[quadrant] += 1
            }
        }
        if (counts.any { it < LightingThresholds.MIN_QUADRANT_CELLS }) return null
        val means = counts.indices.map { sums[it] / counts[it] }
        return means.max() - means.min()
    }

    private fun strongEdges(samples: FloatArray, columns: Int, rows: Int): Pair<Float, Int> {
        var strong = 0
        var pairs = 0
        val quad = IntArray(4)
        for (row in 0 until rows) {
            for (col in 0 until columns) {
                val index = row * columns + col
                val luma = samples[index]
                if (col + 1 < columns) {
                    pairs += 1
                    if (abs(luma - samples[index + 1]) >= LightingThresholds.HARSH_STEP) {
                        strong += 1
                        quad[quadrant(index, columns, rows)] += 1
                    }
                }
                if (row + 1 < rows) {
                    pairs += 1
                    if (abs(luma - samples[index + columns]) >= LightingThresholds.HARSH_STEP) {
                        strong += 1
                        quad[quadrant(index, columns, rows)] += 1
                    }
                }
            }
        }
        val share = if (pairs == 0) 0f else strong / pairs.toFloat()
        return share to quad.count { it >= LightingThresholds.MIN_QUADRANT_CELLS }
    }

    private fun quadrant(index: Int, columns: Int, rows: Int): Int {
        val col = index % columns
        val row = index / columns
        return (if (col >= columns / 2) 1 else 0) + (if (row >= rows / 2) 2 else 0)
    }
}
