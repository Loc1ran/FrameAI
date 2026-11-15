package com.team.frameai.guide

import kotlin.math.abs

/**
 * The photography journey. Trials finish only when a later frame measures the change.
 * Following the words is not evidence.
 */
enum class OdysseyStage { DEPARTURE, TRIALS, TRANSFORMATION }

enum class OdysseyTrial(val title: String, val skill: String, val pitch: String) {
    CYCLOPS(
        "The Cyclops",
        "Perspective",
        "One viewpoint is a trap. Try a clearly different camera height.",
    ),
    SIRENS(
        "The Sirens",
        "Subject emphasis",
        "Something beside the subject is stealing attention. Step sideways.",
    ),
    SCYLLA(
        "Scylla and Charybdis",
        "Light balance",
        "The bright background and the dark subject are fighting. Rebalance the light.",
    ),
}

data class FrameEvidence(
    val subjectX: Float?,
    val subjectY: Float?,
    val subjectHeight: Float?,
    val ready: Boolean,
    val edgeProblem: Boolean,
    val lightingCue: Boolean,
    val issue: String,
    val action: String,
    val subjectLuma: Float?,
    val backgroundLuma: Float?,
    val clippedShare: Float?,
    val coachStamp: Long,
)

data class TrialVerdict(val completed: Boolean, val lesson: String? = null)

object OdysseyJourney {
    private val lightIssues = setOf(
        "subject_underexposed",
        "backlit",
        "harsh_light",
        "clipped_highlights",
    )
    private val distractionIssues = setOf("distracting_background", "weak_separation")

    fun suggest(evidence: FrameEvidence): OdysseyTrial? {
        if (evidence.issue in distractionIssues || evidence.edgeProblem) return OdysseyTrial.SIRENS
        if (evidence.action == "lower_camera" || evidence.action == "raise_camera") return OdysseyTrial.CYCLOPS
        if (evidence.issue in lightIssues || evidence.lightingCue) return OdysseyTrial.SCYLLA
        return null
    }

    fun stage(trialActive: Boolean, learned: Boolean): OdysseyStage = when {
        learned -> OdysseyStage.TRANSFORMATION
        trialActive -> OdysseyStage.TRIALS
        else -> OdysseyStage.DEPARTURE
    }

    fun evaluate(trial: OdysseyTrial, start: FrameEvidence, now: FrameEvidence): TrialVerdict {
        return when (trial) {
            OdysseyTrial.CYCLOPS -> cyclops(start, now)
            OdysseyTrial.SIRENS -> sirens(start, now)
            OdysseyTrial.SCYLLA -> scylla(start, now)
        }
    }

    private fun cyclops(start: FrameEvidence, now: FrameEvidence): TrialVerdict {
        val yShift = shift(start.subjectY, now.subjectY) >= 0.12f
        val grown = relativeChange(start.subjectHeight, now.subjectHeight) >= 0.22f
        if (!yShift && !grown) return TrialVerdict(false)
        val acceptable = now.ready || now.issue == "none" || now.issue == "already_strong"
        val worse = now.edgeProblem || now.lightingCue || now.issue in distractionIssues || now.issue in lightIssues
        if (!acceptable || worse) return TrialVerdict(false)
        return TrialVerdict(
            true,
            "The viewpoint changed, and this angle still holds the subject.",
        )
    }

    private fun sirens(start: FrameEvidence, now: FrameEvidence): TrialVerdict {
        val moved = shift(start.subjectX, now.subjectX) >= 0.10f
        if (!moved || now.lightingCue) return TrialVerdict(false)
        val fromEdge = start.edgeProblem && !now.edgeProblem
        val fromIssue = start.issue in distractionIssues &&
            now.coachStamp > start.coachStamp &&
            now.issue !in distractionIssues &&
            !now.edgeProblem
        if (!fromEdge && !fromIssue) return TrialVerdict(false)
        return TrialVerdict(
            true,
            "A step to the side moved the subject away from what was competing with it.",
        )
    }

    private fun scylla(start: FrameEvidence, now: FrameEvidence): TrialVerdict {
        val startSubject = start.subjectLuma ?: return TrialVerdict(false)
        val nowSubject = now.subjectLuma ?: return TrialVerdict(false)
        val startGap = gap(start) ?: return TrialVerdict(false)
        val nowGap = gap(now) ?: return TrialVerdict(false)
        val clipOk = (now.clippedShare ?: 0f) <= (start.clippedShare ?: 0f) + 0.02f
        if (!clipOk) return TrialVerdict(false)
        val stillSilhouette = nowSubject < 0.18f && nowGap > 0.22f
        if (stillSilhouette) return TrialVerdict(false)
        val gapClosed = startGap - nowGap >= 0.08f
        val broughtIn = (startSubject < 0.28f || startSubject > 0.82f) && nowSubject in 0.32f..0.78f
        if (!gapClosed && !broughtIn) return TrialVerdict(false)
        if (now.lightingCue && now.issue in lightIssues) return TrialVerdict(false)
        return TrialVerdict(
            true,
            "The subject reads more clearly against the background, and the highlights stayed intact.",
        )
    }

    private fun shift(start: Float?, now: Float?): Float {
        if (start == null || now == null) return 0f
        return abs(now - start)
    }

    private fun relativeChange(start: Float?, now: Float?): Float {
        if (start == null || now == null || start < 0.05f) return 0f
        return abs(now - start) / start
    }

    private fun gap(evidence: FrameEvidence): Float? {
        val subject = evidence.subjectLuma ?: return null
        val background = evidence.backgroundLuma ?: return null
        return background - subject
    }
}

/** One optional trial. Completion is decided by [OdysseyJourney.evaluate]. */
class TrialSession {
    var active: OdysseyTrial? = null
        private set
    var lesson: String? = null
        private set
    var skill: String? = null
        private set
    private var started: FrameEvidence? = null
    private var declined: OdysseyTrial? = null

    fun suggestion(evidence: FrameEvidence): OdysseyTrial? {
        if (active != null || lesson != null) return null
        val suggested = OdysseyJourney.suggest(evidence) ?: return null
        if (suggested == declined) return null
        return suggested
    }

    fun accept(trial: OdysseyTrial, now: FrameEvidence) {
        active = trial
        started = now
        lesson = null
        skill = null
        declined = null
    }

    fun decline(trial: OdysseyTrial) {
        if (active == null) declined = trial
    }

    fun observe(now: FrameEvidence) {
        val trial = active ?: return
        val start = started ?: return
        val verdict = OdysseyJourney.evaluate(trial, start, now)
        if (!verdict.completed) return
        lesson = verdict.lesson
        skill = trial.skill
        active = null
        started = null
    }

    fun consume(): Triple<String, String, String> {
        val note = Triple(activeTitle(), skill.orEmpty(), lesson.orEmpty())
        lesson = null
        skill = null
        return note
    }

    private fun activeTitle(): String = active?.name ?: skill?.let { finishedName(it) }.orEmpty()

    private fun finishedName(skill: String): String =
        OdysseyTrial.entries.firstOrNull { it.skill == skill }?.name.orEmpty()
}
