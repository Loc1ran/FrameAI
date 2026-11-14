package com.team.frameai.guide

import com.team.frameai.model.AdviceSource
import com.team.frameai.model.FaceBoxNormalized
import com.team.frameai.model.LiveGuide
import kotlin.math.abs

/**
 * Deterministic portrait exercise. The target is a chosen thirds placement, not a universal rule.
 * Messages stay direction-free until the preview transform has been checked on a phone.
 *
 * [steady] is accepted so the camera screen can pass a later steadiness reading.
 * P0 does not add a second hint from it.
 */
class GuideEngine(
    val targetX: Float = DEFAULT_TARGET_X,
    val targetY: Float = DEFAULT_TARGET_Y,
    val toleranceX: Float = DEFAULT_TOLERANCE_X,
    val toleranceY: Float = DEFAULT_TOLERANCE_Y,
) {
    fun suggest(
        faceBox: FaceBoxNormalized?,
        @Suppress("UNUSED_PARAMETER") steady: Boolean? = null,
    ): LiveGuide {
        if (faceBox == null) {
            return noFace()
        }
        val aligned = abs(faceBox.centerX - targetX) <= toleranceX &&
            abs(faceBox.centerY - targetY) <= toleranceY
        return if (aligned) ready(faceBox) else align(faceBox)
    }

    private fun noFace(): LiveGuide {
        return LiveGuide(
            kind = KIND_NO_FACE,
            message = NO_FACE_MESSAGE,
            targetX = targetX.toDouble(),
            targetY = targetY.toDouble(),
            subjectX = null,
            subjectY = null,
            isReady = false,
            source = AdviceSource.OFFLINE_RULES.wire,
        )
    }

    private fun ready(faceBox: FaceBoxNormalized): LiveGuide {
        return LiveGuide(
            kind = KIND_READY,
            message = READY_MESSAGE,
            targetX = targetX.toDouble(),
            targetY = targetY.toDouble(),
            subjectX = faceBox.centerX.toDouble(),
            subjectY = faceBox.centerY.toDouble(),
            isReady = true,
            source = AdviceSource.OFFLINE_RULES.wire,
        )
    }

    private fun align(faceBox: FaceBoxNormalized): LiveGuide {
        return LiveGuide(
            kind = KIND_ALIGN,
            message = ALIGN_MESSAGE,
            targetX = targetX.toDouble(),
            targetY = targetY.toDouble(),
            subjectX = faceBox.centerX.toDouble(),
            subjectY = faceBox.centerY.toDouble(),
            isReady = false,
            source = AdviceSource.OFFLINE_RULES.wire,
        )
    }

    companion object {
        const val DEFAULT_TARGET_X = 0.67f
        const val DEFAULT_TARGET_Y = 0.33f
        const val DEFAULT_TOLERANCE_X = 0.08f
        const val DEFAULT_TOLERANCE_Y = 0.10f

        const val KIND_ALIGN = "ALIGN_SUBJECT"
        const val KIND_READY = "READY"
        const val KIND_NO_FACE = "NO_FACE"

        const val ALIGN_MESSAGE = "Align the face with the target circle"
        const val READY_MESSAGE = "Framing looks good \u2014 hold steady and shoot"
        const val NO_FACE_MESSAGE = "Use the grid to place your subject"
    }
}
