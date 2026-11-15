package com.team.frameai.guide

data class TrackCandidate(
    val id: Int?,
    val box: FrameRect,
)

/**
 * Picks the subject the preview should follow.
 * A short missed detection must not switch the guide to a different object.
 */
class SubjectLock {
    private var previousId: Int? = null
    private var lastSeenAt = 0L

    fun isLocked(nowMs: Long): Boolean = previousId != null && nowMs - lastSeenAt <= 1_000L

    fun choose(candidates: List<TrackCandidate>, nowMs: Long): TrackCandidate? {
        val usable = candidates.filter { usable(it.box) }
        val chosen = if (isLocked(nowMs)) {
            usable.firstOrNull { it.id == previousId }
        } else {
            usable.minByOrNull { distanceToCenter(it.box) }
        }
        if (chosen != null) {
            previousId = chosen.id
            lastSeenAt = nowMs
        }
        return chosen
    }

    private fun usable(box: FrameRect): Boolean {
        if (!listOf(box.left, box.top, box.right, box.bottom).all { it.isFinite() && it in 0f..1f }) return false
        if (box.width < 0.05f || box.height < 0.05f) return false
        if (box.width > 0.92f && box.height > 0.92f) return false
        return true
    }

    private fun distanceToCenter(box: FrameRect): Float {
        val dx = box.centerX - 0.5f
        val dy = box.centerY - 0.5f
        return dx * dx + dy * dy
    }
}
