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
    val candidateLutIds: List<String> = emptyList(),
) {
    init {
        require(sceneType in SCENE_TYPES) { "Unsupported sceneType: $sceneType" }
        require(suggestedLutId in LutIds.SUGGESTABLE) { "Unsupported LUT id: $suggestedLutId" }
        require(summary.length in 1..200)
        require(improvement.length in 1..200)
        require(principle.length in 1..80)
        require(lutReason.length in 1..160)
        require(source in SOURCES) { "Unsupported source: $source" }
        require(candidateLutIds.size <= 10) { "candidateLutIds" }
        require(candidateLutIds.distinct().size == candidateLutIds.size) { "candidateLutIds" }
        require(candidateLutIds.all { it in LutIds.SUGGESTABLE && it != LutIds.NONE }) { "candidateLutIds" }
    }

    companion object {
        val SCENE_TYPES = setOf("portrait", "landscape", "street", "other")
        val SOURCES = setOf("gemini", "offline_rules", "demo_mock")
    }
}
