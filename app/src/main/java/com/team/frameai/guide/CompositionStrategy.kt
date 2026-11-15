package com.team.frameai.guide

import com.team.frameai.model.LiveGuide

/**
 * Strategies the offline coach can justify from a face, optional landmarks, and a coarse scene read.
 *
 * Geometric roll, symmetry, and perspective use measured edges. They are not object names.
 * A subject mask is not available, so overlap is limited to detector boxes.
 */
enum class CompositionStrategy {
    BREATHING_ROOM,
    ENVIRONMENTAL,
    CENTERED,
    LOOK_ROOM,
    THIRDS,
    HORIZON,
    OPEN_SCENE,
    NO_SUBJECT,
    GEOMETRY,
}

data class Framing(
    val guide: LiveGuide,
    val strategy: CompositionStrategy,
    val confidence: Float,
    val urgent: Boolean,
)

data class StableChoice(
    val strategy: CompositionStrategy,
    val targetX: Float?,
)

/**
 * Holds a composition until a new one is clearly more confident.
 * Breathing room and a lost subject bypass the hold.
 * A target that would jump to the opposite third is held with the previous side.
 */
class CompositionStabilizer {
    private var strategy: CompositionStrategy? = null
    private var confidence = 0f
    private var targetX: Float? = null

    fun choose(candidate: CompositionStrategy, confidence: Float, targetX: Float, urgent: Boolean): StableChoice {
        val previous = strategy
        val previousX = this.targetX
        if (urgent || previous == null) {
            return commit(candidate, confidence, targetX)
        }
        val jumped = previousX != null && kotlin.math.abs(targetX - previousX) > SIDE_JUMP
        if (candidate == previous && !jumped) {
            return commit(candidate, confidence, targetX)
        }
        if (confidence >= this.confidence + SWITCH_MARGIN && confidence >= MIN_SWITCH) {
            return commit(candidate, confidence, targetX)
        }
        this.confidence = (this.confidence - DECAY).coerceAtLeast(0.3f)
        return StableChoice(previous, previousX)
    }

    fun clear() {
        strategy = null
        confidence = 0f
        targetX = null
    }

    private fun commit(candidate: CompositionStrategy, confidence: Float, targetX: Float): StableChoice {
        strategy = candidate
        this.confidence = confidence
        this.targetX = targetX
        return StableChoice(candidate, targetX)
    }

    private companion object {
        const val SWITCH_MARGIN = 0.18f
        const val MIN_SWITCH = 0.62f
        const val DECAY = 0.06f
        const val SIDE_JUMP = 0.2f
    }
}
