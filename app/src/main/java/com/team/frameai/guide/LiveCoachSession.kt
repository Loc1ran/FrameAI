package com.team.frameai.guide

import com.team.frameai.lut.LutPack
import com.team.frameai.model.AdviceSource
import com.team.frameai.model.LiveCoach
import com.team.frameai.model.LiveGuide

data class CoachMemory(
    val reply: LiveCoach? = null,
    val atElapsedMs: Long = 0L,
)

data class ShownCoach(
    val guide: LiveGuide,
    val zoom: String,
    val targetZoomRatio: Double,
    val lutId: String?,
)

/**
 * Keeps a cloud reply on screen, then returns to the on-device coach.
 * A Gemini reply stays for [HOLD_MS] after the server stops answering.
 */
object LiveCoachSession {
    const val HOLD_MS = 8_000L

    fun remember(previous: CoachMemory, latest: LiveCoach?, now: Long): CoachMemory {
        if (latest == null) return previous
        if (latest.source != AdviceSource.GEMINI.wire && latest.source != AdviceSource.DEMO_MOCK.wire) {
            return previous
        }
        return CoachMemory(latest, now)
    }

    fun show(offline: LiveGuide, memory: CoachMemory, now: Long): ShownCoach {
        val reply = memory.reply
        val fresh = reply != null && now - memory.atElapsedMs <= HOLD_MS
        if (reply == null || !fresh) {
            return ShownCoach(
                offline,
                zoom = offline.zoom ?: "hold",
                targetZoomRatio = offline.targetZoomRatio ?: 1.0,
                lutId = null,
            )
        }
        val lut = reply.suggestedLutId.takeIf { it in LutPack.ids }
        return ShownCoach(
            guide = reply.toPicture(offline),
            zoom = reply.zoom,
            targetZoomRatio = reply.targetZoomRatio,
            lutId = lut,
        )
    }
}

private fun LiveCoach.toPicture(offline: LiveGuide): LiveGuide {
    val horizon = referenceId in LiveCoach.HORIZON_IDS
    val message = when {
        isReady -> GuideEngine.READY_MESSAGE
        zoom == "in" -> GuideEngine.SMALL_MESSAGE
        zoom == "out" -> GuideEngine.LARGE_MESSAGE
        else -> GuideEngine.ALIGN_MESSAGE
    }
    return LiveGuide(
        kind = if (isReady) GuideEngine.KIND_READY else GuideEngine.KIND_ALIGN,
        message = message,
        targetX = targetX,
        targetY = targetY,
        subjectX = if (horizon) null else subjectX ?: offline.subjectX,
        subjectY = if (horizon) subjectY ?: offline.subjectY else subjectY ?: offline.subjectY,
        isReady = isReady,
        source = source,
    )
}
