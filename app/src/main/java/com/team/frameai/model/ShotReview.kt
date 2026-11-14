package com.team.frameai.model

/**
 * One post-capture coaching note. Field names match contracts/shot-review.schema.json.
 */
data class ShotReview(
    val sceneType: String,
    val summary: String,
    val improvement: String,
    val principle: String,
    val suggestedLutId: String,
    val lutReason: String,
    val source: AdviceSource,
) {
    init {
        require(sceneType in SCENE_TYPES) { "Unsupported sceneType: $sceneType" }
        require(suggestedLutId in LutIds.SUGGESTABLE) { "Unsupported LUT id: $suggestedLutId" }
        require(summary.length in 1..200)
        require(improvement.length in 1..200)
        require(principle.length in 1..80)
        require(lutReason.length in 1..160)
    }

    companion object {
        val SCENE_TYPES = setOf("portrait", "landscape", "street", "other")
    }
}
