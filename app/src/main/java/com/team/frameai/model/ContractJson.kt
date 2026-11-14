package com.team.frameai.model

import kotlinx.serialization.json.Json

object ContractJson {
    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
    }

    fun parseLiveGuide(raw: String): LiveGuide {
        val guide = json.decodeFromString(LiveGuide.serializer(), raw)
        require(guide.kind in GUIDE_KINDS) { "kind" }
        require(guide.source in SOURCES) { "source" }
        require(guide.message.length <= 120) { "message" }
        require(guide.targetX in 0.0..1.0) { "targetX" }
        require(guide.targetY in 0.0..1.0) { "targetY" }
        guide.subjectX?.let { require(it in 0.0..1.0) { "subjectX" } }
        guide.subjectY?.let { require(it in 0.0..1.0) { "subjectY" } }
        return guide
    }

    fun parseShotReview(raw: String): ShotReview {
        val review = json.decodeFromString(ShotReview.serializer(), raw)
        require(review.sceneType in SCENE_TYPES) { "sceneType" }
        require(review.suggestedLutId in LUT_IDS) { "suggestedLutId" }
        require(review.source in SOURCES) { "source" }
        require(review.summary.length <= 200) { "summary" }
        require(review.improvement.length <= 200) { "improvement" }
        require(review.principle.length <= 80) { "principle" }
        require(review.lutReason.length <= 160) { "lutReason" }
        return review
    }

    private val GUIDE_KINDS = setOf("ALIGN_SUBJECT", "READY", "NO_FACE")
    private val SCENE_TYPES = setOf("portrait", "landscape", "street", "other")
    private val LUT_IDS = setOf("warm_film", "cool_cinema", "none")
    private val SOURCES = setOf("offline_rules", "demo_mock", "gemini")
}
