package com.team.frameai.model

/** Face center and bounds in PreviewView space, each axis in 0..1. */
data class FaceBoxNormalized(
    val centerX: Float,
    val centerY: Float,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)
