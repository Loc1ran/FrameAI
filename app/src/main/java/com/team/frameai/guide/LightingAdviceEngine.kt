package com.team.frameai.guide

data class LightingAdvice(
    val issue: LightingIssue,
    val message: String,
    val detail: String,
    val confidence: Float,
    val severity: Float,
)

/**
 * Turns a lighting measurement into one sentence.
 * Darkness, brightness, and contrast are not treated as mistakes by themselves.
 */
object LightingAdviceEngine {
    const val TOO_DARK = "Your subject appears too dark."
    const val BACKLIT = "Your subject appears darker than the background."
    const val CLIPPED = "Bright areas may be losing detail."
    const val SUBJECT_CLIPPED = "Your subject has some overexposed areas."
    const val HARSH = "The lighting looks quite harsh."
    const val UNEVEN = "Light is uneven across your subject."
    const val BLEND = "Your subject blends into the background."

    const val TOO_DARK_DETAIL = "Try moving toward a brighter area."
    const val BACKLIT_DETAIL = "Try turning so the light falls more evenly on your subject."
    const val CLIPPED_DETAIL = "Reduce exposure slightly, or step out of the strongest light."
    const val SUBJECT_CLIPPED_DETAIL = "Bright parts of the subject may be losing detail."
    const val HARSH_DETAIL = "Try a position with softer, more even light."
    const val UNEVEN_DETAIL = "Try a position with more balanced lighting."
    const val BLEND_DETAIL = "Try changing your angle to improve visual separation."

    fun diagnose(read: LightingRead?): LightingAdvice? {
        if (read == null) return null
        subjectClipped(read)?.let { return it }
        subjectDark(read)?.let { return it }
        widespreadClip(read)?.let { return it }
        backlight(read)?.let { return it }
        harsh(read)?.let { return it }
        uneven(read)?.let { return it }
        return blend(read)
    }

    fun present(advice: LightingAdvice, framing: Framing): LightingAdvice? {
        if (advice.confidence < LightingThresholds.MIN_CONFIDENCE) return null
        if (!overrides(framing, advice)) return null
        return advice
    }

    fun overrides(framing: Framing, advice: LightingAdvice): Boolean {
        if (advice.confidence < LightingThresholds.MIN_CONFIDENCE) return false
        if (framing.strategy == CompositionStrategy.BREATHING_ROOM) return false
        if (framing.strategy == CompositionStrategy.LOOK_ROOM || framing.strategy == CompositionStrategy.ENVIRONMENTAL) return false
        if (GeometryCoach.isCue(framing.guide.message)) return false
        if (framing.guide.isReady) return advice.severity >= LightingThresholds.READY_FLOOR
        return advice.severity >= LightingThresholds.SEVERE
    }

    fun isCue(message: String): Boolean {
        return message == TOO_DARK || message == BACKLIT || message == CLIPPED ||
            message == SUBJECT_CLIPPED || message == HARSH || message == UNEVEN || message == BLEND
    }

    fun symbol(message: String): String? {
        return when (message) {
            TOO_DARK, BACKLIT -> "◐"
            CLIPPED, SUBJECT_CLIPPED -> "☀"
            HARSH -> "✷"
            UNEVEN -> "▤"
            BLEND -> "◎"
            else -> null
        }
    }

    fun detail(message: String): String? {
        return when (message) {
            TOO_DARK -> TOO_DARK_DETAIL
            BACKLIT -> BACKLIT_DETAIL
            CLIPPED -> CLIPPED_DETAIL
            SUBJECT_CLIPPED -> SUBJECT_CLIPPED_DETAIL
            HARSH -> HARSH_DETAIL
            UNEVEN -> UNEVEN_DETAIL
            BLEND -> BLEND_DETAIL
            else -> null
        }
    }

    private fun subjectClipped(read: LightingRead): LightingAdvice? {
        val share = read.subjectClippedShare ?: return null
        if (read.subjectCells < LightingThresholds.MIN_SUBJECT_CELLS) return null
        if (share < LightingThresholds.SUBJECT_CLIP) return null
        return advice(LightingIssue.OVEREXPOSURE, SUBJECT_CLIPPED, SUBJECT_CLIPPED_DETAIL, 0.84f, 0.88f)
    }

    private fun subjectDark(read: LightingRead): LightingAdvice? {
        val mean = read.subjectMean ?: return null
        val dark = read.subjectDarkShare ?: return null
        val background = read.backgroundMean ?: return null
        if (mean > LightingThresholds.SUBJECT_DARK_MEAN) return null
        if (dark < LightingThresholds.SUBJECT_DARK_SHARE) return null
        if (background < LightingThresholds.LIT_BACKGROUND) return null
        return advice(LightingIssue.UNDEREXPOSURE, TOO_DARK, TOO_DARK_DETAIL, 0.82f, 0.86f)
    }

    private fun widespreadClip(read: LightingRead): LightingAdvice? {
        if (read.clippedShare < LightingThresholds.WIDESPREAD_CLIP) return null
        if (read.clippedQuadrants < 3) return null
        return advice(LightingIssue.OVEREXPOSURE, CLIPPED, CLIPPED_DETAIL, 0.80f, 0.74f)
    }

    private fun backlight(read: LightingRead): LightingAdvice? {
        val subject = read.subjectMean ?: return null
        val background = read.backgroundMean ?: return null
        val gap = background - subject
        if (subject >= LightingThresholds.SUBJECT_NOT_BRIGHT) return null
        if (background < LightingThresholds.BACKGROUND_BRIGHT) return null
        if (gap < LightingThresholds.BACKLIGHT_GAP) return null
        val severe = gap >= LightingThresholds.BACKLIGHT_SEVERE_GAP && subject <= 0.30f
        return advice(
            LightingIssue.BACKLIT_SUBJECT,
            BACKLIT,
            BACKLIT_DETAIL,
            if (severe) 0.80f else 0.68f,
            if (severe) 0.84f else 0.66f,
        )
    }

    private fun harsh(read: LightingRead): LightingAdvice? {
        if (read.strongEdgeShare < LightingThresholds.HARSH_EDGE_SHARE) return null
        if (read.darkShare < LightingThresholds.HARSH_DARK_SHARE) return null
        if (read.brightShare < LightingThresholds.HARSH_BRIGHT_SHARE) return null
        if (read.edgeQuadrants < 3) return null
        return advice(LightingIssue.HARSH_LIGHTING, HARSH, HARSH_DETAIL, 0.66f, 0.60f)
    }

    private fun uneven(read: LightingRead): LightingAdvice? {
        val spread = read.subjectSpread ?: return null
        if (read.subjectCells < 12) return null
        if (spread < LightingThresholds.UNEVEN_SPREAD) return null
        return advice(LightingIssue.UNEVEN_ILLUMINATION, UNEVEN, UNEVEN_DETAIL, 0.64f, 0.58f)
    }

    private fun blend(read: LightingRead): LightingAdvice? {
        val subject = read.subjectMean ?: return null
        val background = read.backgroundMean ?: return null
        if (read.subjectCells < LightingThresholds.MIN_SUBJECT_CELLS) return null
        if (kotlin.math.abs(subject - background) > LightingThresholds.LOW_CONTRAST_GAP) return null
        if (subject !in 0.25f..0.75f || background !in 0.25f..0.75f) return null
        return advice(LightingIssue.LOW_SUBJECT_BACKGROUND_CONTRAST, BLEND, BLEND_DETAIL, 0.60f, 0.52f)
    }

    private fun advice(
        issue: LightingIssue,
        message: String,
        detail: String,
        confidence: Float,
        severity: Float,
    ): LightingAdvice {
        return LightingAdvice(issue, message, detail, confidence, severity)
    }
}

/**
 * Waits for the same lighting issue on several frames, then keeps it briefly after it disappears.
 */
class LightingHold(
    private val showAfter: Int = 3,
    private val clearAfter: Int = 4,
) {
    private var pending: LightingIssue = LightingIssue.NONE
    private var pendingCount = 0
    private var shown: LightingAdvice? = null
    private var clearCount = 0

    fun update(advice: LightingAdvice?): LightingAdvice? {
        if (advice == null || advice.issue == LightingIssue.NONE) {
            pending = LightingIssue.NONE
            pendingCount = 0
            val current = shown ?: return null
            clearCount += 1
            if (clearCount >= clearAfter) {
                shown = null
                clearCount = 0
                return null
            }
            return current
        }
        clearCount = 0
        if (shown?.issue == advice.issue) {
            shown = advice
            return advice
        }
        if (pending == advice.issue) {
            pendingCount += 1
        } else {
            pending = advice.issue
            pendingCount = 1
        }
        if (pendingCount >= showAfter) {
            shown = advice
            return advice
        }
        return shown
    }
}

