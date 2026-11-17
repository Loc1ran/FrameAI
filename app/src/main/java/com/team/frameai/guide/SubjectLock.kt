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
    private var selectedId: Int? = null
    private var held: TrackCandidate? = null
    private var lastSeenAt = 0L

    fun isLocked(nowMs: Long): Boolean = synchronized(this) {
        previousId != null && nowMs - lastSeenAt <= 1_000L
    }

    fun hasSelection(): Boolean = synchronized(this) { selectedId != null }

    fun select(id: Int, nowMs: Long) = synchronized(this) {
        selectedId = id
        previousId = id
        held = null
        lastSeenAt = nowMs
    }

    fun clearSelection() = synchronized(this) {
        selectedId = null
        previousId = null
        held = null
        lastSeenAt = 0L
    }

    fun choose(candidates: List<TrackCandidate>, nowMs: Long): TrackCandidate? = synchronized(this) {
        val usable = candidates.filter { usable(it.box) }
        val followed = follow(usable, nowMs)
        if (followed != null) return followed
        if (nowMs - lastSeenAt <= HOLD_MS && held != null) return held
        selectedId = null
        val nearest = usable.minByOrNull { distanceToCenter(it.box) } ?: return null
        remember(nearest, nowMs)
        nearest
    }

    /** Keep the same object when its tracking number changes or a frame is missed. */
    private fun follow(usable: List<TrackCandidate>, nowMs: Long): TrackCandidate? {
        val anchor = held
        val picked = selectedId ?: previousId
        if (picked == null && anchor == null) return null
        if (anchor == null && nowMs - lastSeenAt > 1_000L && selectedId == null) return null
        val exact = usable.firstOrNull { it.id != null && it.id == picked }
        if (exact != null) {
            val body = if (selectedId == null) containingBody(usable, exact) else null
            val kept = body ?: exact
            remember(kept, nowMs)
            return kept
        }
        val same = anchor?.let { previous ->
            usable.filter { overlap(it.box, previous.box) >= 0.35f }
                .maxByOrNull { overlap(it.box, previous.box) }
        }
        if (same != null) {
            if (selectedId != null) selectedId = same.id
            remember(same, nowMs)
            return same
        }
        return null
    }

    private fun containingBody(usable: List<TrackCandidate>, inner: TrackCandidate): TrackCandidate? {
        return usable.filter { outer ->
            outer.id != inner.id && isHeadInsideBody(inner.box, outer.box)
        }.maxByOrNull { area(it.box) }
    }

    /** A head sits in the top of a much taller body. A nearby object is not a body. */
    private fun isHeadInsideBody(head: FrameRect, body: FrameRect): Boolean {
        if (body.height < head.height * 2.2f) return false
        if (!containsMost(body, head)) return false
        val fromTop = (head.centerY - body.top) / body.height
        return fromTop in 0f..0.4f
    }

    private fun area(box: FrameRect): Float = box.width * box.height

    private fun containsMost(outer: FrameRect, inner: FrameRect): Boolean {
        val innerArea = area(inner)
        if (innerArea <= 0f) return false
        val width = (minOf(outer.right, inner.right) - maxOf(outer.left, inner.left)).coerceAtLeast(0f)
        val height = (minOf(outer.bottom, inner.bottom) - maxOf(outer.top, inner.top)).coerceAtLeast(0f)
        return width * height / innerArea >= 0.6f
    }

    private fun remember(candidate: TrackCandidate, nowMs: Long) {
        previousId = candidate.id
        held = candidate
        lastSeenAt = nowMs
    }

    private fun usable(box: FrameRect): Boolean {
        if (!listOf(box.left, box.top, box.right, box.bottom).all { it.isFinite() && it in 0f..1f }) return false
        if (box.width < 0.05f || box.height < 0.05f) return false
        if (box.width > 0.92f && box.height > 0.92f) return false
        return true
    }

    private fun overlap(a: FrameRect, b: FrameRect): Float {
        val left = maxOf(a.left, b.left)
        val top = maxOf(a.top, b.top)
        val right = minOf(a.right, b.right)
        val bottom = minOf(a.bottom, b.bottom)
        val width = (right - left).coerceAtLeast(0f)
        val height = (bottom - top).coerceAtLeast(0f)
        val shared = width * height
        val area = a.width * a.height + b.width * b.height - shared
        if (area <= 0f) return 0f
        return shared / area
    }

    private fun distanceToCenter(box: FrameRect): Float {
        val dx = box.centerX - 0.5f
        val dy = box.centerY - 0.5f
        return dx * dx + dy * dy
    }

    private companion object {
        const val HOLD_MS = 2_500L
    }
}
