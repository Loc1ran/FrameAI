package com.team.frameai.guide

import com.team.frameai.model.AdviceSource
import com.team.frameai.model.FaceBoxNormalized
import com.team.frameai.model.LiveGuide
import com.team.frameai.model.SceneRead
import kotlin.math.abs

/**
 * Coaches whatever is in the frame: a person, a horizon, or any other subject.
 * A person can sit on either third. A scene with no person is still a picture.
 */
class GuideEngine(
    val toleranceX: Float = DEFAULT_TOLERANCE_X,
    val toleranceY: Float = DEFAULT_TOLERANCE_Y,
) {
    fun suggest(
        faceBox: FaceBoxNormalized?,
        @Suppress("UNUSED_PARAMETER") steady: Boolean? = null,
        scene: SceneRead? = null,
    ): LiveGuide {
        if (faceBox == null) return noFace(scene)
        val target = targetFor(faceBox, scene)
        val issue = areaIssue(faceBox, target)
        val message = messageFor(issue, scene)
        val ready = issue == Issue.READY
        return LiveGuide(
            kind = if (ready) KIND_READY else KIND_ALIGN,
            message = message,
            targetX = target.first.toDouble(),
            targetY = target.second.toDouble(),
            subjectX = faceBox.centerX.toDouble(),
            subjectY = faceBox.centerY.toDouble(),
            isReady = ready,
            source = AdviceSource.OFFLINE_RULES.wire,
        )
    }

    private fun noFace(scene: SceneRead?): LiveGuide {
        if (scene == null) return emptyFrame()
        val horizon = scene.horizonY
        if (scene.hasHorizon() && horizon != null) return horizonFrame(scene, horizon)
        if (scene.hasSideBias()) return openSubject(scene)
        return readyScene(scene, OPEN_READY)
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

    private fun horizonFrame(scene: SceneRead, horizon: Float): LiveGuide {
        val targetY = if (abs(horizon - THIRD_LO) <= abs(horizon - THIRD_HI)) THIRD_LO else THIRD_HI
        val ready = abs(horizon - targetY) <= toleranceY
        val message = when {
            ready && scene.warmth < -0.08f -> READY_COOL
            ready -> HORIZON_READY
            targetY > 0.5f -> HORIZON_LOWER
            else -> HORIZON_UPPER
        }
        return LiveGuide(
            kind = if (ready) KIND_READY else KIND_ALIGN,
            message = message,
            targetX = 0.5,
            targetY = targetY.toDouble(),
            subjectX = null,
            subjectY = horizon.toDouble(),
            isReady = ready,
            source = AdviceSource.OFFLINE_RULES.wire,
        )
    }

    private fun openSubject(scene: SceneRead): LiveGuide {
        val targetX = if (scene.leftLuma > scene.rightLuma) THIRD_HI else THIRD_LO
        return LiveGuide(
            kind = KIND_ALIGN,
            message = PLACE_ANY_MESSAGE,
            targetX = targetX.toDouble(),
            targetY = 0.5,
            subjectX = null,
            subjectY = null,
            isReady = false,
            source = AdviceSource.OFFLINE_RULES.wire,
        )
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

    private fun targetFor(face: FaceBoxNormalized, scene: SceneRead?): Pair<Float, Float> {
        val natural = nearestThird(face)
        if (scene == null) return natural
        val x = when {
            scene.leftLuma > scene.rightLuma + SceneRead.SIDE_BIAS -> THIRD_HI
            scene.rightLuma > scene.leftLuma + SceneRead.SIDE_BIAS -> THIRD_LO
            else -> natural.first
        }
        return x to natural.second
    }

    private fun messageFor(issue: Issue, scene: SceneRead?): String {
        return when (issue) {
            Issue.EDGE -> EDGE_MESSAGE
            Issue.SMALL -> SMALL_MESSAGE
            Issue.LARGE -> LARGE_MESSAGE
            Issue.PLACE -> if (scene != null && scene.hasSideBias()) PLACE_ANY_MESSAGE else ALIGN_MESSAGE
            Issue.READY -> when {
                scene == null -> READY_MESSAGE
                scene.warmth < -0.08f -> READY_COOL
                scene.warmth > 0.08f -> READY_WARM
                else -> READY_MESSAGE
            }
        }
    }

    private fun nearestThird(face: FaceBoxNormalized): Pair<Float, Float> {
        val x = if (abs(face.centerX - THIRD_LO) <= abs(face.centerX - THIRD_HI)) THIRD_LO else THIRD_HI
        val y = if (face.centerY < 0.5f) THIRD_LO else THIRD_HI
        return x to y
    }

    private fun areaIssue(face: FaceBoxNormalized, target: Pair<Float, Float>): Issue {
        if (hasArea(face)) {
            if (face.left <= EDGE || face.top <= EDGE || face.right >= 1f - EDGE || face.bottom >= 1f - EDGE) {
                return Issue.EDGE
            }
            if (face.width < MIN_WIDTH || face.height < MIN_HEIGHT) return Issue.SMALL
            if (face.width > MAX_WIDTH || face.height > MAX_HEIGHT) return Issue.LARGE
        }
        val placed = abs(face.centerX - target.first) <= toleranceX &&
            abs(face.centerY - target.second) <= toleranceY
        return if (placed) Issue.READY else Issue.PLACE
    }

    private fun hasArea(face: FaceBoxNormalized): Boolean {
        return face.width >= MIN_AREA_EDGE && face.height >= MIN_AREA_EDGE
    }

    private enum class Issue { EDGE, SMALL, LARGE, PLACE, READY }

    companion object {
        const val DEFAULT_TARGET_X = 0.67f
        const val DEFAULT_TARGET_Y = 0.33f
        const val DEFAULT_TOLERANCE_X = 0.08f
        const val DEFAULT_TOLERANCE_Y = 0.10f

        const val THIRD_LO = 0.33f
        const val THIRD_HI = 0.67f
        private const val EDGE = 0.02f
        private const val MIN_AREA_EDGE = 0.04f
        private const val MIN_WIDTH = 0.14f
        private const val MIN_HEIGHT = 0.16f
        private const val MAX_WIDTH = 0.55f
        private const val MAX_HEIGHT = 0.62f

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
        const val HORIZON_READY = "The horizon sits on a grid line \u2014 hold steady and shoot"
        const val OPEN_READY = "This frame can hold any subject \u2014 hold steady and shoot"
        const val READY_MESSAGE = "This part of the frame looks balanced \u2014 hold steady and shoot"
        const val READY_WARM = "Warm light across the frame \u2014 hold steady and shoot"
        const val READY_COOL = "Cool light across the frame \u2014 hold steady and shoot"
        const val NO_FACE_MESSAGE = "Point at any subject. Use the grid to choose a placement."
    }
}
