package com.team.frameai.guide

import com.team.frameai.model.FaceBoxNormalized
import com.team.frameai.model.SceneRead
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LightingAnalyzerTest {
    private val columns = 32
    private val rows = 24
    private val engine = GuideEngine()

    @Test
    fun aUniformlyDarkFrameIsMeasuredAndNotCalledAMistake() {
        val read = LightingAnalyzer.measure(fill(0.05f), columns, rows)
        assertEquals(0.05f, read.mean, 0.001f)
        assertEquals(0.05f, read.median, 0.001f)
        assertTrue(read.darkShare > 0.9f)
        assertNull(read.subjectMean)
        assertNull(LightingAdviceEngine.diagnose(read))
        val nightSubject = LightingAnalyzer.measure(fill(0.05f), columns, rows, subject())
        assertNotNull(nightSubject.subjectMean)
        assertNull(LightingAdviceEngine.diagnose(nightSubject))
    }

    @Test
    fun aBrightFrameIsNotClippedUntilPixelsAreNearWhite() {
        val snow = LightingAnalyzer.measure(fill(0.84f), columns, rows)
        assertTrue(snow.mean > 0.8f)
        assertEquals(0f, snow.clippedShare)
        assertNull(LightingAdviceEngine.diagnose(snow))
        val normal = LightingAnalyzer.measure(fill(0.45f), columns, rows)
        assertTrue(normal.midShare > 0.9f)
        assertNull(LightingAdviceEngine.diagnose(normal))
    }

    @Test
    fun aSmallBrightPatchIsNotWidespreadClipping() {
        val samples = fill(0.45f)
        paint(samples) { x, y -> if (x in 4..6 && y in 4..6) 0.99f else null }
        val read = LightingAnalyzer.measure(samples, columns, rows)
        assertTrue(read.clippedShare > 0f)
        assertTrue(read.clippedShare < LightingThresholds.WIDESPREAD_CLIP)
        assertTrue(read.clippedQuadrants <= 1)
        assertNull(LightingAdviceEngine.diagnose(read))
    }

    @Test
    fun widespreadNearWhitePixelsWarnThatDetailMayBeLost() {
        val samples = fill(0.40f)
        paint(samples) { x, _ -> if (x < 26) 0.99f else null }
        val read = LightingAnalyzer.measure(samples, columns, rows)
        assertTrue(read.clippedShare > LightingThresholds.WIDESPREAD_CLIP, "clipped ${read.clippedShare}")
        assertTrue(read.clippedQuadrants >= 3)
        val advice = LightingAdviceEngine.diagnose(read)
        assertEquals(LightingIssue.OVEREXPOSURE, advice?.issue)
        assertEquals(LightingAdviceEngine.CLIPPED, advice?.message)
    }

    @Test
    fun largeDarkRegionsWithoutASubjectStayQuiet() {
        val samples = fill(0.55f)
        paint(samples) { _, y -> if (y >= 8) 0.04f else null }
        val read = LightingAnalyzer.measure(samples, columns, rows)
        assertTrue(read.darkShare > 0.5f, "dark ${read.darkShare}")
        assertNull(LightingAdviceEngine.diagnose(read))
    }

    @Test
    fun aDarkSubjectAgainstABrightBackgroundIsDescribedAsDarkerNotAsAKnownLightDirection() {
        val read = LightingAnalyzer.measure(splitSubject(0.22f, 0.78f), columns, rows, subject())
        assertTrue((read.backgroundMean ?: 0f) - (read.subjectMean ?: 1f) > LightingThresholds.BACKLIGHT_GAP)
        val advice = LightingAdviceEngine.diagnose(read)
        assertEquals(LightingIssue.BACKLIT_SUBJECT, advice?.issue)
        assertEquals(LightingAdviceEngine.BACKLIT, advice?.message)
        assertFalse(advice!!.message.contains("behind", ignoreCase = true))
    }

    @Test
    fun similarSubjectAndBackgroundBrightnessIsNotBacklight() {
        val read = LightingAnalyzer.measure(splitSubject(0.48f, 0.56f), columns, rows, subject())
        val gap = abs((read.backgroundMean ?: 0f) - (read.subjectMean ?: 0f))
        assertTrue(gap > LightingThresholds.LOW_CONTRAST_GAP)
        assertTrue(gap < LightingThresholds.BACKLIGHT_GAP)
        assertNull(LightingAdviceEngine.diagnose(read))
    }

    @Test
    fun aVeryDarkSubjectInALitFrameIsUnderexposed() {
        val read = LightingAnalyzer.measure(splitSubject(0.06f, 0.62f), columns, rows, subject())
        assertTrue((read.subjectMean ?: 1f) < LightingThresholds.SUBJECT_DARK_MEAN)
        assertEquals(LightingAdviceEngine.TOO_DARK, LightingAdviceEngine.diagnose(read)?.message)
    }

    @Test
    fun aMissingOrUnusableSubjectBoxDoesNotInventASubject() {
        val patterned = splitSubject(0.12f, 0.80f)
        val missing = LightingAnalyzer.measure(patterned, columns, rows, subject = null)
        assertEquals(0, missing.subjectCells)
        assertNull(missing.subjectMean)
        assertNull(LightingAdviceEngine.diagnose(missing))
        val outside = LightingAnalyzer.measure(patterned, columns, rows, FrameRect(1.2f, 1.2f, 1.6f, 1.8f))
        assertEquals(0, outside.subjectCells)
        assertNull(LightingAdviceEngine.diagnose(outside))
        val partial = LightingAnalyzer.measure(fill(0.4f), columns, rows, FrameRect(-0.2f, 0.2f, 0.45f, 0.8f))
        assertTrue(partial.subjectCells >= LightingThresholds.MIN_SUBJECT_CELLS)
    }

    @Test
    fun repeatedBrightnessStepsCanLookHarshAndOneEdgeDoesNot() {
        val blocks = fill(0.08f)
        paint(blocks) { x, y -> if (((x / 4) + (y / 4)) % 2 == 0) 0.95f else 0.08f }
        val harsh = LightingAnalyzer.measure(blocks, columns, rows)
        assertTrue(harsh.strongEdgeShare >= LightingThresholds.HARSH_EDGE_SHARE, "edges ${harsh.strongEdgeShare}")
        assertTrue(harsh.edgeQuadrants >= 3)
        assertEquals(LightingIssue.HARSH_LIGHTING, LightingAdviceEngine.diagnose(harsh)?.issue)
        val split = fill(0.85f)
        paint(split) { x, _ -> if (x < columns / 2) 0.12f else 0.85f }
        val dramatic = LightingAnalyzer.measure(split, columns, rows)
        assertTrue(dramatic.strongEdgeShare < LightingThresholds.HARSH_EDGE_SHARE)
        assertNull(LightingAdviceEngine.diagnose(dramatic))
    }

    @Test
    fun unevenBrightnessAcrossASubjectIsAnEstimateNotALightSource() {
        val samples = fill(0.45f)
        paint(samples) { x, y ->
            if (x !in 8..23 || y !in 6..17) return@paint null
            if (x < 16) 0.12f else 0.78f
        }
        val read = LightingAnalyzer.measure(samples, columns, rows, FrameRect(0.25f, 0.25f, 0.75f, 0.75f))
        assertTrue((read.subjectSpread ?: 0f) >= LightingThresholds.UNEVEN_SPREAD, "spread ${read.subjectSpread}")
        val advice = LightingAdviceEngine.diagnose(read)
        assertEquals(LightingIssue.UNEVEN_ILLUMINATION, advice?.issue)
        assertFalse(advice!!.message.contains("sun", ignoreCase = true))
    }

    @Test
    fun lowSubjectBackgroundContrastIsSeparateFromDepthOfField() {
        val read = LightingAnalyzer.measure(splitSubject(0.50f, 0.53f), columns, rows, subject())
        val advice = LightingAdviceEngine.diagnose(read)
        assertEquals(LightingIssue.LOW_SUBJECT_BACKGROUND_CONTRAST, advice?.issue)
        assertEquals(LightingAdviceEngine.BLEND, advice?.message)
        assertFalse(advice!!.detail.contains("blur", ignoreCase = true))
    }

    @Test
    fun severeExposureCanReplaceAMinorMoveButNotBreathingRoomOrLookRoom() {
        val severe = scene(splitSubject(0.06f, 0.62f), subject())
        val moving = portrait(0.20f, 0.36f)
        val smoother = GuideSmoother(engine, minIntervalMs = 0, smoothing = 1f)
        val first = smoother.update(moving, 0L, scene = severe)
        assertFalse(LightingAdviceEngine.isCue(first.message))
        var latest = first
        repeat(2) { step -> latest = smoother.update(moving, 100L + step, scene = severe) }
        assertEquals(LightingAdviceEngine.TOO_DARK, latest.message)

        val mild = scene(splitSubject(0.36f, 0.70f), subject())
        val quiet = GuideSmoother(engine, minIntervalMs = 0, smoothing = 1f)
        var move = quiet.update(moving, 0L, scene = mild)
        repeat(4) { step -> move = quiet.update(moving, 10L + step, scene = mild) }
        assertFalse(LightingAdviceEngine.isCue(move.message))

        val clipped = FaceBoxNormalized(0.10f, 0.33f, 0f, 0.18f, 0.22f, 0.48f)
        val breathing = GuideSmoother(engine, minIntervalMs = 0, smoothing = 1f)
        var room = breathing.update(clipped, 0L, scene = severe)
        repeat(4) { step -> room = breathing.update(clipped, 20L + step, scene = severe) }
        assertEquals(GuideEngine.BREATHING_CUE, room.message)

        val looking = GuideSmoother(engine, minIntervalMs = 0, smoothing = 1f)
        var look = looking.update(portrait(0.50f, 0.36f, yaw = 25f), 0L, scene = severe)
        repeat(4) { step -> look = looking.update(portrait(0.50f, 0.36f, yaw = 25f), 30L + step, scene = severe) }
        assertEquals(GuideEngine.LOOK_CUE, look.message)
    }

    @Test
    fun lowConfidenceAndANormalFrameProduceNoWarning() {
        val ready = engine.advise(portrait(0.67f, 0.33f))
        val shy = LightingAdvice(
            LightingIssue.OVEREXPOSURE,
            LightingAdviceEngine.CLIPPED,
            LightingAdviceEngine.CLIPPED_DETAIL,
            confidence = 0.4f,
            severity = 0.9f,
        )
        assertNull(LightingAdviceEngine.present(shy, ready))
        assertNull(LightingAdviceEngine.diagnose(LightingAnalyzer.measure(fill(0.45f), columns, rows)))
    }

    @Test
    fun aLightingCueHoldsForAFewQuietFramesAndThenReturnsToComposition() {
        val severe = scene(splitSubject(0.06f, 0.62f), subject())
        val calm = scene(fill(0.45f), subject = null)
        val face = portrait(0.20f, 0.36f)
        val smoother = GuideSmoother(engine, minIntervalMs = 0, smoothing = 1f)
        repeat(2) { smoother.update(face, it.toLong(), scene = severe) }
        assertEquals(LightingAdviceEngine.TOO_DARK, smoother.update(face, 10L, scene = severe).message)
        assertEquals(LightingAdviceEngine.TOO_DARK, smoother.update(face, 20L, scene = calm).message)
        repeat(2) { smoother.update(face, 30L, scene = calm) }
        val cleared = smoother.update(face, 40L, scene = calm)
        assertFalse(LightingAdviceEngine.isCue(cleared.message))
    }

    private fun fill(value: Float): FloatArray = FloatArray(columns * rows) { value }

    private fun paint(samples: FloatArray, block: (Int, Int) -> Float?) {
        for (y in 0 until rows) {
            for (x in 0 until columns) {
                val value = block(x, y) ?: continue
                samples[y * columns + x] = value
            }
        }
    }

    private fun subject(): FrameRect = FrameRect(0.34f, 0.28f, 0.66f, 0.72f)

    private fun splitSubject(subjectLuma: Float, backgroundLuma: Float): FloatArray {
        val box = subject()
        return FloatArray(columns * rows) { index ->
            val x = index % columns
            val y = index / columns
            val nx = (x + 0.5f) / columns
            val ny = (y + 0.5f) / rows
            if (nx in box.left..box.right && ny in box.top..box.bottom) subjectLuma else backgroundLuma
        }
    }

    private fun scene(samples: FloatArray, subject: FrameRect?): SceneRead {
        return SceneRead(
            leftLuma = 0.4f,
            rightLuma = 0.4f,
            warmth = 0f,
            lighting = LightingAnalyzer.measure(samples, columns, rows, subject),
        )
    }

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
}
