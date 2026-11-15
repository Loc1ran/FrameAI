package com.team.frameai.camera

import com.team.frameai.model.FaceBoxNormalized
import com.team.frameai.model.LiveGuide
import com.team.frameai.model.SceneRead

fun interface CompositionGuide {
    /**
     * [faceBox] is already in PreviewView space. Null means no face to coach.
     * [scene] is the background read for this frame. Null when the frame could not be sampled.
     */
    fun suggest(faceBox: FaceBoxNormalized?, steady: Boolean?, scene: SceneRead?): LiveGuide
    fun reset() {}
}
