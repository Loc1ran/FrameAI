package com.team.frameai.guide

import com.team.frameai.model.AdviceSource
import com.team.frameai.model.AnchorKind
import com.team.frameai.model.FaceBoxNormalized
import com.team.frameai.model.LiveGuide
import com.team.frameai.model.SceneRead
import kotlin.math.abs

/**
 * Coaches whatever is in the frame: a person, a horizon, or any other subject.
 * A person defaults to a third. Center is used only with a real reason.
 * A scene with no person is still a picture.
 */
class GuideEngine(
    val toleranceX: Float = DEFAULT_TOLERANCE_X,
    val toleranceY: Float = DEFAULT_TOLERANCE_Y,
) {
    fun suggest(
        faceBox: FaceBoxNormalized?,
        @Suppress("UNUSED_PARAMETER") steady: Boolean? = null,
        scene: SceneRead? = null,
        preferCentered: Boolean = false,
        lockedStrategy: CompositionStrategy? = null,
        lockedTargetX: Float? = null,
        lockedTargetY: Float? = null,
        lockedHorizonY: Float? = null,
    ): LiveGuide {
        return advise(
            faceBox, steady, scene, preferCentered, lockedStrategy, lockedTargetX, lockedTargetY, lockedHorizonY,
        ).guide
    }

    fun advise(
        faceBox: FaceBoxNormalized?,
        @Suppress("UNUSED_PARAMETER") steady: Boolean? = null,
        scene: SceneRead? = null,
        preferCentered: Boolean = false,
        lockedStrategy: CompositionStrategy? = null,
        lockedTargetX: Float? = null,
        lockedTargetY: Float? = null,
        lockedHorizonY: Float? = null,
    ): Framing {
        val base = if (faceBox == null) {
            noFace(scene, lockedHorizonY)
        } else {
            val natural = choose(faceBox, scene, preferCentered)
            val strategy = when {
                natural.urgent -> natural.strategy
                lockedStrategy == null -> natural.strategy
                lockedStrategy.isPortrait() -> lockedStrategy
                else -> natural.strategy
            }
            val confidence = if (strategy == natural.strategy) natural.confidence else (natural.confidence - 0.05f).coerceAtLeast(0.4f)
            portrait(
                faceBox, scene, strategy, confidence,
                natural.urgent && strategy == natural.strategy, lockedTargetX, lockedTargetY,
            )
        }
        if (base.strategy == CompositionStrategy.BREATHING_ROOM) return base
        if (lockedStrategy != null && lockedStrategy != CompositionStrategy.GEOMETRY) return base
        return withGeometry(base, scene)
    }

    private fun withGeometry(base: Framing, scene: SceneRead?): Framing {
        val advice = GeometryCoach.consider(scene?.geometry, scene?.shapeOverlap ?: 0f, base) ?: return base
        val guide = base.guide.copy(
            kind = KIND_ALIGN,
            message = advice.message,
            isReady = false,
            zoom = ZOOM_HOLD,
            targetZoomRatio = WIDEST_ZOOM,
            targetX = advice.targetX ?: base.guide.targetX,
        )
        return Framing(guide, CompositionStrategy.GEOMETRY, advice.confidence, urgent = false)
    }

    private fun noFace(scene: SceneRead?, lockedHorizonY: Float?): Framing {
        if (scene == null) return framing(emptyFrame(), CompositionStrategy.NO_SUBJECT, 0.9f, urgent = true)
        val horizon = scene.horizonY
        if (scene.hasHorizon() && horizon != null) return horizonFrame(scene, horizon, lockedHorizonY)
        return framing(readyScene(scene, OPEN_READY), CompositionStrategy.OPEN_SCENE, 0.8f, urgent = true)
    }

    private fun emptyFrame(): LiveGuide {
        return LiveGuide(
            kind = KIND_NO_FACE,
            message = NO_FACE_MESSAGE,
            targetX = DEFAULT_TARGET_X.toDouble(),
            targetY = DEFAULT_TARGET_Y.toDouble(),
            subjectX = null,
            subjectY = null,
            isReady = false,
            source = AdviceSource.OFFLINE_RULES.wire,
        )
    }

    private fun horizonFrame(scene: SceneRead, horizon: Float, lockedHorizonY: Float?): Framing {
        val targetY = (lockedHorizonY ?: horizonTarget(scene, horizon)).coerceIn(0.2f, 0.8f)
        val ready = abs(horizon - targetY) <= toleranceY
        val message = when {
            ready && scene.warmth < -0.08f -> READY_COOL
            ready -> HORIZON_READY
            targetY > 0.5f -> HORIZON_LOWER_CUE
            else -> HORIZON_HIGHER_CUE
        }
        val guide = LiveGuide(
            kind = if (ready) KIND_READY else KIND_ALIGN,
            message = message,
            targetX = 0.5,
            targetY = targetY.toDouble(),
            subjectX = null,
            subjectY = horizon.toDouble(),
            isReady = ready,
            source = AdviceSource.OFFLINE_RULES.wire,
        )
        return framing(guide, CompositionStrategy.HORIZON, 0.84f, urgent = true)
    }

    /**
     * Keep the nearer grid line. The middle is a tie, and only then does sky-versus-foreground luma choose.
     * No luma difference keeps the previous nearer-line tie, which is the upper line.
     */
    private fun horizonTarget(scene: SceneRead, horizon: Float): Float {
        val nearer = if (abs(horizon - THIRD_LO) <= abs(horizon - THIRD_HI)) THIRD_LO else THIRD_HI
        val tie = abs(abs(horizon - THIRD_LO) - abs(horizon - THIRD_HI)) <= 0.02f
        if (!tie) return nearer
        return when {
            scene.topLuma > scene.bottomLuma + 0.12f -> THIRD_HI
            scene.bottomLuma > scene.topLuma + 0.12f -> THIRD_LO
            else -> nearer
        }
    }

    private fun readyScene(scene: SceneRead, neutral: String): LiveGuide {
        val message = when {
            scene.warmth < -0.08f -> READY_COOL
            scene.warmth > 0.08f -> READY_WARM
            else -> neutral
        }
        return LiveGuide(
            kind = KIND_READY,
            message = message,
            targetX = 0.5,
            targetY = 0.5,
            subjectX = null,
            subjectY = null,
            isReady = true,
            source = AdviceSource.OFFLINE_RULES.wire,
        )
    }

    private data class Choice(val strategy: CompositionStrategy, val confidence: Float, val urgent: Boolean)

    private fun choose(face: FaceBoxNormalized, scene: SceneRead?, preferCentered: Boolean): Choice {
        if (needsBreathing(face)) return Choice(CompositionStrategy.BREATHING_ROOM, 0.93f, urgent = true)
        val environmental = environmentalConfidence(face, scene)
        if (environmental >= ENVIRONMENTAL_FLOOR) {
            return Choice(CompositionStrategy.ENVIRONMENTAL, environmental, urgent = false)
        }
        val centered = centerConfidence(face, scene, preferCentered)
        if (centered > 0f) return Choice(CompositionStrategy.CENTERED, centered, urgent = false)
        if (HeadOrientation.turnedTowardViewersLeft(face.yawDegrees) ||
            HeadOrientation.turnedTowardViewersRight(face.yawDegrees)
        ) {
            val yaw = abs(face.yawDegrees ?: 0f)
            val confidence = (yaw / 40f).coerceIn(0.62f, 0.9f)
            return Choice(CompositionStrategy.LOOK_ROOM, confidence, urgent = false)
        }
        return Choice(CompositionStrategy.THIRDS, 0.72f, urgent = false)
    }

    private fun portrait(
        face: FaceBoxNormalized,
        scene: SceneRead?,
        strategy: CompositionStrategy,
        confidence: Float,
        urgent: Boolean,
        lockedTargetX: Float?,
        lockedTargetY: Float?,
    ): Framing {
        val computed = targetFor(strategy, face, scene)
        val targetX = when {
            lockedTargetX == null -> computed.first
            strategy == CompositionStrategy.BREATHING_ROOM -> computed.first
            else -> lockedTargetX.coerceIn(0.28f, 0.72f)
        }
        val targetY = when {
            lockedTargetY == null -> computed.second
            strategy == CompositionStrategy.BREATHING_ROOM -> computed.second
            else -> lockedTargetY.coerceIn(0.24f, 0.76f)
        }
        val placed = abs(face.anchorX - targetX) <= toleranceX && abs(face.anchorY - targetY) <= toleranceY
        val aligned = strategy != CompositionStrategy.BREATHING_ROOM && placed
        val zoom = zoomFor(strategy, face)
        val guide = LiveGuide(
            kind = if (aligned) KIND_READY else KIND_ALIGN,
            message = messageFor(strategy, aligned, face, scene, targetX, targetY),
            targetX = targetX.toDouble(),
            targetY = targetY.toDouble(),
            subjectX = face.anchorX.toDouble(),
            subjectY = face.anchorY.toDouble(),
            isReady = aligned,
            source = AdviceSource.OFFLINE_RULES.wire,
            referenceId = referenceFor(strategy, targetX),
            zoom = zoom.first,
            targetZoomRatio = zoom.second,
        )
        return framing(guide, strategy, confidence, urgent)
    }

    private fun targetFor(strategy: CompositionStrategy, face: FaceBoxNormalized, scene: SceneRead?): Pair<Float, Float> {
        val y = verticalTarget(face)
        return when (strategy) {
            CompositionStrategy.BREATHING_ROOM -> breathingTarget(face)
            CompositionStrategy.CENTERED -> 0.5f to y
            CompositionStrategy.LOOK_ROOM -> lookX(face) to y
            CompositionStrategy.ENVIRONMENTAL -> thirdX(face, scene, allowBrightness = false) to y
            else -> thirdX(face, scene, allowBrightness = true) to y
        }
    }

    /** Eyes and the nose sit on the upper third. A box center uses the nearer horizontal line. */
    private fun verticalTarget(face: FaceBoxNormalized): Float {
        if (face.anchorKind == AnchorKind.EYES || face.anchorKind == AnchorKind.NOSE) return THIRD_LO
        return if (face.anchorY < 0.5f) THIRD_LO else THIRD_HI
    }

    private fun lookX(face: FaceBoxNormalized): Float {
        return when {
            HeadOrientation.turnedTowardViewersRight(face.yawDegrees) -> THIRD_LO
            HeadOrientation.turnedTowardViewersLeft(face.yawDegrees) -> THIRD_HI
            else -> nearestThirdX(face.anchorX)
        }
    }

    /**
     * Brightness only breaks a tie when the anchor is not already committed to one third.
     * It never pulls a settled subject across the frame.
     */
    private fun thirdX(face: FaceBoxNormalized, scene: SceneRead?, allowBrightness: Boolean): Float {
        val nearest = nearestThirdX(face.anchorX)
        if (!allowBrightness || scene == null || !scene.hasSideBias()) return nearest
        val distLo = abs(face.anchorX - THIRD_LO)
        val distHi = abs(face.anchorX - THIRD_HI)
        val ambiguous = abs(distLo - distHi) <= 0.08f
        if (!ambiguous) return nearest
        return if (scene.leftLuma > scene.rightLuma) THIRD_HI else THIRD_LO
    }

    private fun nearestThirdX(x: Float): Float {
        return if (abs(x - THIRD_LO) <= abs(x - THIRD_HI)) THIRD_LO else THIRD_HI
    }

    private fun breathingTarget(face: FaceBoxNormalized): Pair<Float, Float> {
        val leftBad = face.left <= EDGE || face.anchorX < 0.12f
        val rightBad = face.right >= 1f - EDGE || face.anchorX > 0.88f
        val topBad = face.top <= EDGE || face.anchorY < 0.12f
        val bottomBad = face.bottom >= 1f - EDGE || face.anchorY > 0.90f
        val oversized = face.width > MAX_WIDTH || face.height > MAX_HEIGHT
        var x = when {
            leftBad && rightBad -> 0.5f
            leftBad -> maxOf(face.anchorX + 0.10f, THIRD_LO).coerceAtMost(0.42f)
            rightBad -> minOf(face.anchorX - 0.10f, THIRD_HI).coerceAtLeast(0.58f)
            else -> face.anchorX
        }
        var y = when {
            topBad && bottomBad -> 0.5f
            topBad -> maxOf(face.anchorY + 0.08f, THIRD_LO).coerceAtMost(0.42f)
            bottomBad -> minOf(face.anchorY - 0.08f, THIRD_HI).coerceAtLeast(0.58f)
            else -> verticalTarget(face)
        }
        if (oversized && !leftBad && !rightBad) x = face.anchorX.coerceIn(0.33f, 0.67f)
        if (oversized && !topBad && !bottomBad) y = verticalTarget(face)
        x = face.anchorX + (x - face.anchorX).coerceIn(-MAX_SHIFT, MAX_SHIFT)
        y = face.anchorY + (y - face.anchorY).coerceIn(-MAX_SHIFT, MAX_SHIFT)
        return x.coerceIn(0.28f, 0.72f) to y.coerceIn(0.24f, 0.76f)
    }

    private fun messageFor(
        strategy: CompositionStrategy,
        aligned: Boolean,
        face: FaceBoxNormalized,
        scene: SceneRead?,
        targetX: Float,
        targetY: Float,
    ): String {
        if (!aligned) {
            return when {
                strategy == CompositionStrategy.BREATHING_ROOM -> BREATHING_CUE
                strategy == CompositionStrategy.ENVIRONMENTAL -> ENVIRONMENT_CUE
                strategy == CompositionStrategy.CENTERED && abs(face.anchorX - targetX) > toleranceX -> CENTER_CUE
                strategy == CompositionStrategy.LOOK_ROOM &&
                    abs(face.anchorX - targetX) >= abs(face.anchorY - targetY) -> LOOK_CUE
                else -> CoachAction.slightMove(face.anchorX, face.anchorY, targetX, targetY)
            }
        }
        if (scene?.faceInShadow() == true) return SHADOW_CUE
        if (scene != null && scene.warmth < -0.08f) return READY_COOL
        if (scene != null && scene.warmth > 0.08f) return READY_WARM
        return GREAT_CUE
    }

    private fun zoomFor(strategy: CompositionStrategy, face: FaceBoxNormalized): Pair<String, Double> {
        val oversized = hasArea(face) && (face.width > MAX_WIDTH || face.height > MAX_HEIGHT)
        return if (strategy == CompositionStrategy.BREATHING_ROOM && oversized) {
            ZOOM_OUT to WIDEST_ZOOM
        } else {
            ZOOM_HOLD to WIDEST_ZOOM
        }
    }

    private fun referenceFor(strategy: CompositionStrategy, targetX: Float): String? {
        if (strategy == CompositionStrategy.CENTERED) return null
        return if (targetX >= 0.5f) PORTRAIT_SPACE_LEFT else PORTRAIT_SPACE_RIGHT
    }

    private fun needsBreathing(face: FaceBoxNormalized): Boolean {
        if (!hasArea(face)) return false
        if (face.left <= EDGE || face.top <= EDGE || face.right >= 1f - EDGE || face.bottom >= 1f - EDGE) return true
        if (face.anchorX < 0.12f || face.anchorX > 0.88f || face.anchorY < 0.12f || face.anchorY > 0.90f) return true
        if (face.width > MAX_WIDTH || face.height > MAX_HEIGHT) return true
        return false
    }

    /**
     * Size alone stays under [ENVIRONMENTAL_FLOOR]. A horizon or a clear background split has to agree.
     */
    private fun environmentalConfidence(face: FaceBoxNormalized, scene: SceneRead?): Float {
        if (!hasArea(face) || !isSmall(face) || touchesEdge(face)) return 0f
        var confidence = 0.34f
        if (scene?.hasHorizon() == true) confidence += 0.28f
        if (scene != null && abs(scene.topLuma - scene.bottomLuma) >= 0.18f) confidence += 0.16f
        if (scene?.hasSideBias() == true) confidence += 0.12f
        return confidence.coerceAtMost(0.82f)
    }

    /**
     * Missing yaw is not a reason to center. A frontal face on a third stays on that third.
     * Center needs symmetry plus a subject already near the middle, a suitable frontal portrait
     * in that same balanced middle, or an explicit preference.
     */
    private fun centerConfidence(face: FaceBoxNormalized, scene: SceneRead?, preferCentered: Boolean): Float {
        if (!portraitSized(face) || isSmall(face)) return 0f
        val yaw = face.yawDegrees
        if (HeadOrientation.turnedTowardViewersLeft(yaw) || HeadOrientation.turnedTowardViewersRight(yaw)) return 0f
        if (preferCentered) return 0.84f
        val frontal = yaw != null && abs(yaw) < 10f
        val symmetric = scene != null && abs(scene.leftLuma - scene.rightLuma) <= SYMMETRY
        val nearCenter = abs(face.anchorX - 0.5f) <= 0.12f
        if (!nearCenter) return 0f
        if (frontal && symmetric) return 0.78f
        if (symmetric && yaw == null) return 0.7f
        return 0f
    }

    private fun portraitSized(face: FaceBoxNormalized): Boolean {
        if (!hasArea(face)) return false
        return face.width in 0.18f..0.58f && face.height in 0.18f..0.72f
    }

    private fun isSmall(face: FaceBoxNormalized): Boolean {
        return face.width < 0.22f && face.height < 0.30f
    }

    private fun touchesEdge(face: FaceBoxNormalized): Boolean {
        return face.left <= EDGE || face.top <= EDGE || face.right >= 1f - EDGE || face.bottom >= 1f - EDGE
    }

    private fun hasArea(face: FaceBoxNormalized): Boolean {
        return face.width >= MIN_AREA_EDGE && face.height >= MIN_AREA_EDGE
    }

    private fun framing(guide: LiveGuide, strategy: CompositionStrategy, confidence: Float, urgent: Boolean): Framing {
        return Framing(guide, strategy, confidence, urgent)
    }

    private fun CompositionStrategy.isPortrait(): Boolean {
        return this == CompositionStrategy.BREATHING_ROOM ||
            this == CompositionStrategy.ENVIRONMENTAL ||
            this == CompositionStrategy.CENTERED ||
            this == CompositionStrategy.LOOK_ROOM ||
            this == CompositionStrategy.THIRDS
    }

    companion object {
        const val DEFAULT_TARGET_X = 0.67f
        const val DEFAULT_TARGET_Y = 0.33f
        const val DEFAULT_TOLERANCE_X = 0.08f
        const val DEFAULT_TOLERANCE_Y = 0.10f

        const val THIRD_LO = 0.33f
        const val THIRD_HI = 0.67f

        /** Face near the right third, open space on the left. */
        const val PORTRAIT_SPACE_LEFT = "portrait_space_left"

        /** Face near the left third, open space on the right. */
        const val PORTRAIT_SPACE_RIGHT = "portrait_space_right"

        const val ZOOM_IN = "in"
        const val ZOOM_OUT = "out"
        const val ZOOM_HOLD = "hold"

        const val IDEAL_FACE_WIDTH = 0.32f
        const val IDEAL_FACE_HEIGHT = 0.40f

        private const val WIDEST_ZOOM = 1.0
        private const val EDGE = 0.02f
        private const val MIN_AREA_EDGE = 0.04f
        private const val MAX_WIDTH = 0.55f
        private const val MAX_HEIGHT = 0.62f
        private const val MAX_SHIFT = 0.28f
        private const val SYMMETRY = 0.05f
        private const val ENVIRONMENTAL_FLOOR = 0.55f

        const val KIND_ALIGN = "ALIGN_SUBJECT"
        const val KIND_READY = "READY"
        const val KIND_NO_FACE = "NO_FACE"

        const val ALIGN_MESSAGE = "The subject is off a strong line. Settle it on the marked third."
        const val EDGE_MESSAGE = "The subject is cut off. Bring the whole subject inside the frame."
        const val SMALL_MESSAGE = "The subject is small in this frame. Step closer."
        const val LARGE_MESSAGE = "The subject fills the frame. Leave a little room around it."
        const val PLACE_ANY_MESSAGE = "One side is brighter. Put the subject on the calmer third."
        const val HORIZON_LOWER = "Rest the horizon on the lower grid line."
        const val HORIZON_UPPER = "Rest the horizon on the upper grid line."
        const val HORIZON_LOWER_CUE = "Keep the horizon lower."
        const val HORIZON_HIGHER_CUE = "Keep the horizon higher."
        const val HORIZON_READY = "The horizon sits on a grid line \u2014 hold steady and shoot"
        const val OPEN_READY = "This frame can hold any subject \u2014 hold steady and shoot"
        const val READY_MESSAGE = "This part of the frame looks balanced \u2014 hold steady and shoot"
        const val READY_WARM = "Warm light across the frame \u2014 hold steady and shoot"
        const val READY_COOL = "Cool light across the frame \u2014 hold steady and shoot"
        const val NO_FACE_MESSAGE = "Point at any subject. Use the grid to choose a placement."
        const val BREATHING_CUE = "Give your subject more breathing room."
        const val ENVIRONMENT_CUE = "Leave the surroundings in the frame."
        const val CENTER_CUE = "Try a centered composition."
        const val LOOK_CUE = "Leave more space in front."
        const val GREAT_CUE = "Great composition! Hold steady."
        const val SHADOW_CUE = "Face is in shadow."
    }
}
