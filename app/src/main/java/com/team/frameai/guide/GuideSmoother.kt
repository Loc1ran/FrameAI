package com.team.frameai.guide

import com.team.frameai.model.FaceBoxNormalized
import com.team.frameai.model.LiveGuide
import com.team.frameai.model.SceneRead
import kotlin.math.abs

/**
 * Eases the face box and emits at about 4 Hz.
 * A face appearing or disappearing is delivered immediately so a stale READY cannot linger.
 * The held composition stays put until a new one is clearly more confident.
 */
class GuideSmoother(
    private val engine: GuideEngine = GuideEngine(),
    private val minIntervalMs: Long = 250L,
    private val smoothing: Float = 0.45f,
    private val stabilizer: CompositionStabilizer = CompositionStabilizer(),
    private val director: DirectorSession? = null,
    private val planner: DirectorPlanner = DirectorPlanner(),
) {
    private var smoothed: FaceBoxNormalized? = null
    private var lastEmitMs: Long = Long.MIN_VALUE
    private var lastGuide: LiveGuide? = null
    private val lightingHold = LightingHold()

    fun update(
        face: FaceBoxNormalized?,
        nowMs: Long,
        steady: Boolean? = null,
        scene: SceneRead? = null,
    ): LiveGuide {
        val blended = blend(face)
        if (blended == null) {
            stabilizer.clear()
            val base = applyLighting(engine.advise(null, steady, scene), scene)
            return publish(direct(base, null, nowMs, scene), nowMs)
        }
        val natural = engine.advise(blended, steady, scene)
        val stable = stabilizer.choose(
            natural.strategy,
            natural.confidence,
            natural.guide.targetX.toFloat(),
            natural.urgent,
        )
        val framing = if (sameChoice(natural, stable)) {
            natural
        } else {
            engine.advise(
                blended,
                steady,
                scene,
                lockedStrategy = stable.strategy,
                lockedTargetX = stable.targetX,
            )
        }
        if (stable.strategy == CompositionStrategy.GEOMETRY && framing.strategy != CompositionStrategy.GEOMETRY) {
            val kept = lastGuide
            if (kept != null && GeometryCoach.isCue(kept.message)) return kept
        }
        return publish(direct(applyLighting(framing, scene), blended, nowMs, scene), nowMs)
    }

    private fun direct(framing: Framing, face: FaceBoxNormalized?, nowMs: Long, scene: SceneRead?): Framing {
        val session = director ?: return framing
        return planner.merge(framing, session.current(nowMs), nowMs, engine, face, scene)
    }

    private fun applyLighting(framing: Framing, scene: SceneRead?): Framing {
        val blocked = framing.strategy == CompositionStrategy.BREATHING_ROOM ||
            GeometryCoach.isCue(framing.guide.message)
        if (blocked) return framing
        val persisted = lightingHold.update(LightingAdviceEngine.diagnose(scene?.lighting))
        val advice = persisted?.let { LightingAdviceEngine.present(it, framing) } ?: return framing
        return framing.copy(
            guide = framing.guide.copy(
                kind = GuideEngine.KIND_ALIGN,
                message = advice.message,
                isReady = false,
                zoom = GuideEngine.ZOOM_HOLD,
                targetZoomRatio = 1.0,
            ),
        )
    }

    fun lastKind(): String? = lastGuide?.kind

    private fun sameChoice(natural: Framing, stable: StableChoice): Boolean {
        val heldX = stable.targetX ?: return natural.strategy == stable.strategy
        return natural.strategy == stable.strategy && abs(natural.guide.targetX.toFloat() - heldX) < 0.02f
    }

    private fun publish(framing: Framing, nowMs: Long): LiveGuide {
        val suggestion = framing.guide
        val previous = lastGuide
        val sameKind = previous != null && previous.kind == suggestion.kind
        if (sameKind && nowMs - lastEmitMs < minIntervalMs) {
            return previous
        }
        lastGuide = suggestion
        lastEmitMs = nowMs
        return suggestion
    }

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
            centerX = from.centerX + (to.centerX - from.centerX) * weight,
            centerY = from.centerY + (to.centerY - from.centerY) * weight,
            left = from.left + (to.left - from.left) * weight,
            top = from.top + (to.top - from.top) * weight,
            right = from.right + (to.right - from.right) * weight,
            bottom = from.bottom + (to.bottom - from.bottom) * weight,
            anchorX = from.anchorX + (to.anchorX - from.anchorX) * weight,
            anchorY = from.anchorY + (to.anchorY - from.anchorY) * weight,
            anchorKind = to.anchorKind,
            yawDegrees = to.yawDegrees,
        )
    }
}
