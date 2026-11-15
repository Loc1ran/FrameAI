package com.team.frameai.model

import kotlinx.serialization.Serializable

/** One live-coach reply. The camera draws this. It does not print [message]. */
@Serializable
data class LiveCoach(
    val sceneType: String,
    val subject: String,
    val referenceId: String,
    val message: String,
    val zoom: String,
    val targetZoomRatio: Double,
    val targetX: Double,
    val targetY: Double,
    val isReady: Boolean,
    val suggestedLutId: String,
    val source: String,
    val subjectX: Double? = null,
    val subjectY: Double? = null,
) {
    init {
        require(sceneType in SCENE_TYPES) { "sceneType" }
        require(subject.length in 1..40) { "subject" }
        require(referenceId in REFERENCE_IDS) { "referenceId" }
        require(message.length in 1..120) { "message" }
        require(zoom in ZOOM) { "zoom" }
        require(targetZoomRatio in 1.0..8.0) { "targetZoomRatio" }
        require(targetX in 0.0..1.0 && targetY in 0.0..1.0) { "target" }
        subjectX?.let { require(it in 0.0..1.0) { "subjectX" } }
        subjectY?.let { require(it in 0.0..1.0) { "subjectY" } }
        require(suggestedLutId in LutIds.SUGGESTABLE) { "suggestedLutId" }
        require(source in SOURCES) { "source" }
    }

    companion object {
        val SCENE_TYPES = setOf("portrait", "landscape", "other")
        val REFERENCE_IDS = setOf(
            "portrait_space_left",
            "portrait_space_right",
            "landscape_low_horizon",
            "landscape_high_horizon",
            "landscape_subject_small",
            "street_layer",
            "none",
        )
        val ZOOM = setOf("in", "out", "hold")
        val SOURCES = setOf("gemini", "offline_rules", "demo_mock")
        val HORIZON_IDS = setOf("landscape_low_horizon", "landscape_high_horizon")
    }
}
