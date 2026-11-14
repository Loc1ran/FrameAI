package com.team.frameai.model

import kotlinx.serialization.Serializable

/** Frozen ShotReview contract. [source] distinguishes Gemini from offline and sample text. */
@Serializable
data class ShotReview(
    val sceneType: String,
    val summary: String,
    val improvement: String,
    val principle: String,
    val suggestedLutId: String,
    val lutReason: String,
    val source: String,
)
