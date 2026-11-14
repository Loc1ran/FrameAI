package com.team.frameai.camera

import com.team.frameai.model.FaceBoxNormalized
import com.team.frameai.model.LiveGuide

fun interface CompositionGuide {
    /**
     * [faceBox] is already in PreviewView space. Null means no face to coach.
     * Developer B's GuideEngine is the production implementation.
     */
    fun suggest(faceBox: FaceBoxNormalized?, steady: Boolean?): LiveGuide
}
