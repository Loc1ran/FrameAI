package com.team.frameai.guide

import com.team.frameai.model.LiveCoach

enum class CompositionMode { SUBJECT, SCENE }
data class SceneAdvice(val title: String, val detail: String, val ready: Boolean = false)

/** Scene interpretation comes from the full image, never from a face or single object box. */
object SceneComposition {
    fun suggest(reply: LiveCoach?, enabled: Boolean, configured: Boolean, failed: Boolean = false): SceneAdvice = when {
        !configured -> SceneAdvice("Scene AI unavailable", "Connect the camera to the AI server to analyze the activity and its surroundings. You can still take a photo.")
        !enabled -> SceneAdvice("Analyze the whole scene", "Turn AI on to share camera previews and get framing advice for the action and its surroundings.")
        failed -> SceneAdvice("Scene AI unavailable", "The server did not return an analysis. Retrying while AI is on; you can still take a photo.")
        reply == null -> SceneAdvice("Waiting for scene analysis", "Hold briefly. Keep the action and important details visible; you can take a photo at any time.")
        reply.source == "demo_mock" -> SceneAdvice("Demo scene suggestion", reply.message)
        reply.source != "gemini" -> SceneAdvice("Scene analysis unavailable", "No fresh AI scene analysis. Frame it your way and take a photo whenever you like.")
        else -> SceneAdvice(reply.subject, reply.message, reply.isReady)
    }
}
