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
    /** How the director should weigh this frame. The phone does not turn this into a fixed target. */
    val photographyStyle: String = "auto",
    /** A supported composition. The phone turns this into a target. It does not copy model coordinates. */
    val compositionPreset: String = "none",
    val primaryIssue: String = "none",
    /** Pan, tilt, step, height, or light. Physical moves do not draw a dashed subject box. */
    val actionType: String = "hold",
    val actionReason: String = "",
    val confidence: Double = 0.0,
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
        require(photographyStyle in STYLES) { "photographyStyle" }
        require(compositionPreset in PRESETS) { "compositionPreset" }
        require(primaryIssue in ISSUES) { "primaryIssue" }
        require(actionType in ACTIONS) { "actionType" }
        require(actionReason.length <= 160) { "actionReason" }
        require(confidence in 0.0..1.0) { "confidence" }
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
        val STYLES = setOf("auto", "cinematic", "portrait", "street", "landscape")
        val PRESETS = setOf(
            "none",
            "centered",
            "negative_space_left",
            "negative_space_right",
            "low_horizon",
            "high_horizon",
            "leading_space",
            "environmental",
            "street_layer",
        )
        val ISSUES = setOf(
            "none",
            "subject_underexposed",
            "backlit",
            "harsh_light",
            "distracting_background",
            "clipped_highlights",
            "tilted_horizon",
            "weak_separation",
            "empty_space",
            "already_strong",
        )
        val ACTIONS = setOf(
            "hold",
            "pan_left",
            "pan_right",
            "tilt_up",
            "tilt_down",
            "straighten",
            "step_left",
            "step_right",
            "orbit_left",
            "orbit_right",
            "move_closer",
            "move_farther",
            "lower_camera",
            "raise_camera",
            "change_lighting",
            "expose_for_subject",
        )
        val VIEWPOINT = setOf(
            "step_left",
            "step_right",
            "orbit_left",
            "orbit_right",
            "move_closer",
            "move_farther",
            "lower_camera",
            "raise_camera",
        )
        val PHYSICAL = VIEWPOINT + setOf(
            "change_lighting",
            "expose_for_subject",
        )
    }
}
