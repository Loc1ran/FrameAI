package com.team.frameai.guide

/**
 * Head yaw as an approximate turn, never as gaze.
 *
 * ML Kit's Euler Y is positive when the face turns toward the right side of the upright image.
 * The rear-camera preview shows that upright image, so the same sign is the viewer's right.
 * A mirrored front preview flips the picture, so the controller flips the angle before coaching.
 */
object HeadOrientation {
    const val LOOK_DEAD_ZONE = 15f

    fun previewYaw(uprightYaw: Float, mirroredPreview: Boolean): Float {
        if (!uprightYaw.isFinite()) return uprightYaw
        return if (mirroredPreview) -uprightYaw else uprightYaw
    }

    fun turnedTowardViewersRight(yawDegrees: Float?): Boolean {
        return yawDegrees != null && yawDegrees.isFinite() && yawDegrees >= LOOK_DEAD_ZONE
    }

    fun turnedTowardViewersLeft(yawDegrees: Float?): Boolean {
        return yawDegrees != null && yawDegrees.isFinite() && yawDegrees <= -LOOK_DEAD_ZONE
    }
}
