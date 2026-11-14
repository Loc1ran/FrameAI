package com.team.frameai.guide

import com.team.frameai.model.FaceBoxNormalized
import com.team.frameai.model.GuideKind
import com.team.frameai.model.LiveGuide

/**
 * Eases the face box and emits at about 4 Hz.
 * A face appearing or disappearing is delivered immediately so a stale READY cannot linger.
 */
class GuideSmoother(
    private val engine: GuideEngine = GuideEngine(),
    private val minIntervalMs: Long = 250L,
    private val smoothing: Float = 0.45f,
) {
    private var smoothed: FaceBoxNormalized? = null
    private var lastEmitMs: Long = Long.MIN_VALUE
    private var lastGuide: LiveGuide? = null

    fun update(face: FaceBoxNormalized?, nowMs: Long, steady: Boolean? = null): LiveGuide {
        val blended = blend(face)
        val suggestion = engine.suggest(blended, steady)
        val previous = lastGuide
        val sameKind = previous != null && previous.kind == suggestion.kind
        if (sameKind && nowMs - lastEmitMs < minIntervalMs) {
            return previous
        }
        lastGuide = suggestion
        lastEmitMs = nowMs
        return suggestion
    }

    fun lastKind(): GuideKind? = lastGuide?.kind

    private fun blend(face: FaceBoxNormalized?): FaceBoxNormalized? {
        val prior = smoothed
        val weight = smoothing.coerceIn(0f, 1f)
        val next = when {
            face == null || prior == null -> face
            else -> lerpBox(prior, face, weight)
        }
        smoothed = next
        return next
    }

    private fun lerpBox(from: FaceBoxNormalized, to: FaceBoxNormalized, weight: Float): FaceBoxNormalized {
        return FaceBoxNormalized(
            left = from.left + (to.left - from.left) * weight,
            top = from.top + (to.top - from.top) * weight,
            right = from.right + (to.right - from.right) * weight,
            bottom = from.bottom + (to.bottom - from.bottom) * weight,
        )
    }
}
