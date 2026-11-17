package com.team.frameai.guide

import com.team.frameai.model.FaceBoxNormalized
import com.team.frameai.model.RawFaceBox
import com.team.frameai.vision.FaceBoxMath

/**
 * Automatic tracking should follow a person, not only the head.
 * A separate object that does not overlap the face stays that object.
 */
object PersonFrame {
    fun cover(subject: RawFaceBox?, face: RawFaceBox?, rotationDegrees: Int): RawFaceBox? {
        val subjectUpright = subject?.let { upright(it, rotationDegrees) }
        val faceUpright = face?.let { upright(it, rotationDegrees) }
        val chosen = choose(subjectUpright, faceUpright) ?: return null
        if (subjectUpright != null && sameBox(chosen, subjectUpright)) return subject
        if (faceUpright != null && sameBox(chosen, faceUpright)) return face
        val sensor = FaceBoxMath.uprightBoxToSensor(chosen, rotationDegrees)
        return RawFaceBox(
            centerX = sensor.centerX,
            centerY = sensor.centerY,
            left = sensor.left,
            top = sensor.top,
            right = sensor.right,
            bottom = sensor.bottom,
        )
    }

    fun choose(subject: FaceBoxNormalized?, face: FaceBoxNormalized?): FaceBoxNormalized? {
        if (face == null) return subject
        if (face.height >= 0.42f) return subject ?: face
        if (subject != null && containsMost(subject, face) && subject.height >= face.height * 1.8f) return subject
        if (subject != null && !overlaps(subject, face)) return subject
        return bodyAround(face)
    }

    fun bodyAround(head: FaceBoxNormalized): FaceBoxNormalized {
        val bodyHeight = (head.height * 6.5f).coerceAtMost(0.96f)
        val bodyWidth = (head.width * 2.4f).coerceIn(head.width, 0.92f)
        val left = (head.centerX - bodyWidth / 2f).coerceIn(0.01f, (0.99f - bodyWidth).coerceAtLeast(0.01f))
        val top = head.top.coerceIn(0.01f, 0.98f)
        val right = (left + bodyWidth).coerceAtMost(0.99f)
        val bottom = (top + bodyHeight).coerceAtMost(0.99f)
        return FaceBoxNormalized.fromEdges(left, top, right, bottom)
    }

    private fun upright(box: RawFaceBox, rotationDegrees: Int): FaceBoxNormalized {
        return FaceBoxMath.sensorBoxToUpright(
            FaceBoxNormalized.fromEdges(box.left, box.top, box.right, box.bottom),
            rotationDegrees,
        )
    }

    private fun sameBox(a: FaceBoxNormalized, b: FaceBoxNormalized): Boolean =
        a.left == b.left && a.top == b.top && a.right == b.right && a.bottom == b.bottom

    private fun overlaps(a: FaceBoxNormalized, b: FaceBoxNormalized): Boolean = shared(a, b) > 0f

    private fun containsMost(outer: FaceBoxNormalized, inner: FaceBoxNormalized): Boolean {
        val innerArea = inner.width * inner.height
        return innerArea > 0f && shared(outer, inner) / innerArea >= 0.6f
    }

    private fun shared(a: FaceBoxNormalized, b: FaceBoxNormalized): Float {
        val width = (minOf(a.right, b.right) - maxOf(a.left, b.left)).coerceAtLeast(0f)
        val height = (minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)).coerceAtLeast(0f)
        return width * height
    }
}
