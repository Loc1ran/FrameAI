package com.team.frameai.camera

import androidx.camera.core.ImageProxy
import com.team.frameai.model.RawFaceBox

fun interface PrimaryFaceDetector {
    /**
     * Detect one face in raw buffer space. Null means no face.
     * Developer B implements this in vision/FaceDetectionAdapter.
     */
    fun detectPrimaryFace(image: ImageProxy): RawFaceBox?
}
