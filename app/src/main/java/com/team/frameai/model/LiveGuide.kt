package com.team.frameai.model

import kotlinx.serialization.Serializable

/** Frozen LiveGuide contract. Coordinates are PreviewView-normalized, 0..1. */
@Serializable
data class LiveGuide(
    val kind: String,
    val message: String,
    val targetX: Double,
    val targetY: Double,
    val subjectX: Double? = null,
    val subjectY: Double? = null,
    val isReady: Boolean,
    val source: String,
)
