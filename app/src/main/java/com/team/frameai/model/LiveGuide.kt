package com.team.frameai.model

enum class GuideKind {
    ALIGN_SUBJECT,
    READY,
    NO_FACE,
}

/**
 * One coaching hint. Field names match contracts/live-guide.schema.json.
 * The overlay shows this single message. It never stacks a second arrow or tip.
 */
data class LiveGuide(
    val kind: GuideKind,
    val message: String,
    val targetX: Float,
    val targetY: Float,
    val subjectX: Float?,
    val subjectY: Float?,
    val isReady: Boolean,
    val source: AdviceSource,
) {
    init {
        require(message.length in 1..120) { "Guide message length must be 1..120" }
        require(targetX in 0f..1f && targetY in 0f..1f) { "Guide target must be inside 0..1" }
        if (subjectX != null) require(subjectX in 0f..1f)
        if (subjectY != null) require(subjectY in 0f..1f)
        when (kind) {
            GuideKind.NO_FACE -> {
                require(subjectX == null && subjectY == null)
                require(!isReady)
            }
            GuideKind.READY -> require(isReady)
            GuideKind.ALIGN_SUBJECT -> require(!isReady)
        }
    }
}
