package com.team.frameai.camera

import androidx.camera.core.ImageProxy
import com.team.frameai.model.RawFaceBox

fun interface PrimaryFaceDetector {
    /**
     * Detect one face in raw buffer space. Null means no face.
     * The implementation is vision/MlKitPrimaryFaceDetector. It must not close the ImageProxy.
     */
    fun detectPrimaryFace(image: ImageProxy): RawFaceBox?
}
