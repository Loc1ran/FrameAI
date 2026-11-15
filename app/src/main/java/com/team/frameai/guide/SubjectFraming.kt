package com.team.frameai.guide

import com.team.frameai.model.RawFaceBox

object SubjectFraming {
    fun choose(face: RawFaceBox?, tracked: RawFaceBox?, manual: Boolean): RawFaceBox? {
        if (!manual) return face ?: tracked
        if (tracked == null) return null
        // Keep the selected person, but use their eye/nose anchor for portrait framing.
        return face?.takeIf {
            it.left >= tracked.left && it.right <= tracked.right &&
                it.top >= tracked.top && it.bottom <= tracked.bottom
        } ?: tracked
    }
}
