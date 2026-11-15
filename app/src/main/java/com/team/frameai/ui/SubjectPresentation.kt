package com.team.frameai.ui

import com.team.frameai.model.FaceBoxNormalized
import com.team.frameai.model.LiveGuide

/** The measured guide owns both the card and overlay, including validated director changes. */
object SubjectPresentation {
    fun guide(live: LiveGuide?, area: FaceBoxNormalized?): LiveGuide? {
        if (area == null) return null
        return live
    }
}
