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
    /** Portrait reference from server/references.json. Null when this frame is not a person. */
    val referenceId: String? = null,
    /** `in`, `out`, or `hold`. The lens does not move until the user applies zoom. */
    val zoom: String? = null,
    val targetZoomRatio: Double? = null,
    /** Why the current instruction matters. Null keeps the local detail line. */
    val detail: String? = null,
    /** A viewpoint change has no reliable dashed frame, so the goal box stays hidden. */
    val hideGoal: Boolean = false,
    /** Card glyph for a move that is not a pan or tilt. Null keeps the arrow glyph. */
    val cueSymbol: String? = null,
)
