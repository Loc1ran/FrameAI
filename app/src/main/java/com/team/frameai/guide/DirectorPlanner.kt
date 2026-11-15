package com.team.frameai.guide

import com.team.frameai.model.AdviceSource
import com.team.frameai.model.FaceBoxNormalized
import com.team.frameai.model.LiveCoach
import com.team.frameai.model.LiveGuide
import com.team.frameai.model.SceneRead
import kotlin.math.abs

/**
 * One photography decision the phone is willing to follow.
 * Coordinates in the model reply are not copied onto the preview.
 */
data class DirectorGoal(
    val style: String,
    val preset: String,
    val issue: String,
    val action: String,
    val instruction: String,
    val reason: String,
    val confidence: Float,
    val receivedAtMs: Long,
    val generation: Int,
) {
    fun replaces(previous: DirectorGoal): Boolean {
        if (action == previous.action && preset == previous.preset) return true
        return confidence >= previous.confidence + SWITCH_MARGIN && confidence >= MIN_SWITCH
    }

    companion object {
        const val STALE_MS = 8_000L
        const val MIN_CONFIDENCE = 0.62f
        private const val SWITCH_MARGIN = 0.12f
        private const val MIN_SWITCH = 0.70f

        private val GENERIC = setOf(
            "move slightly left.",
            "move slightly right.",
            "move camera upward.",
            "move camera downward.",
            "move toward the dashed frame.",
            "adjust your position.",
            "improve composition.",
            "lighting could be better.",
        )

        fun from(reply: LiveCoach, nowMs: Long, generation: Int): DirectorGoal? {
            if (reply.source != AdviceSource.GEMINI.wire) return null
            if (reply.photographyStyle !in LiveCoach.STYLES) return null
            if (reply.compositionPreset !in LiveCoach.PRESETS) return null
            if (reply.primaryIssue !in LiveCoach.ISSUES) return null
            if (reply.actionType !in LiveCoach.ACTIONS) return null
            if (reply.confidence < MIN_CONFIDENCE || reply.confidence > 1.0) return null
            val instruction = reply.message.trim()
            val reason = reply.actionReason.trim()
            if (instruction.length !in 8..120 || reason.length !in 12..160) return null
            if (instruction.lowercase() in GENERIC) return null
            return DirectorGoal(
                style = reply.photographyStyle,
                preset = reply.compositionPreset,
                issue = reply.primaryIssue,
                action = reply.actionType,
                instruction = instruction,
                reason = reason,
                confidence = reply.confidence.toFloat(),
                receivedAtMs = nowMs,
                generation = generation,
            )
        }
    }
}

/**
 * Latest director reply. A weak, stale, or late reply cannot replace the one on screen.
 * Camera and UI threads share this, so the updates are serialized.
 */
class DirectorSession {
    private var generation: Int = 0
    private var goal: DirectorGoal? = null

    @Synchronized
    fun current(nowMs: Long): DirectorGoal? {
        val held = goal ?: return null
        if (held.generation != generation) return null
        if (nowMs - held.receivedAtMs > DirectorGoal.STALE_MS) return null
        return held
    }

    @Synchronized
    fun bump() {
        generation += 1
        goal = null
    }

    @Synchronized
    fun offer(reply: LiveCoach, nowMs: Long) {
        val stamp = generation
        val next = DirectorGoal.from(reply, nowMs, stamp) ?: return
        val held = goal?.takeIf { it.generation == stamp && nowMs - it.receivedAtMs <= DirectorGoal.STALE_MS }
        if (held != null && !next.replaces(held)) return
        if (generation != stamp) return
        goal = next
    }
}

private data class Aim(
    val strategy: CompositionStrategy,
    val x: Float?,
    val y: Float?,
)

/**
 * Turns a director decision into a [GuideEngine] target, or into a viewpoint line with no dashed box.
 * Clipping, a lost subject, and a measured tilt still win.
 */
class DirectorPlanner {
    private var applied: DirectorGoal? = null

    fun merge(
        framing: Framing,
        goal: DirectorGoal?,
        nowMs: Long,
        engine: GuideEngine,
        face: FaceBoxNormalized?,
        scene: SceneRead?,
    ): Framing {
        if (goal == null || nowMs - goal.receivedAtMs > DirectorGoal.STALE_MS) {
            applied = null
            return framing
        }
        if (applied != null && applied?.generation != goal.generation) applied = null
        if (safety(framing)) return framing
        val rendered = render(framing, goal, engine, face, scene)
        if (rendered !== framing) {
            applied = goal
            return rendered
        }
        val previous = applied?.takeIf {
            it != goal && it.generation == goal.generation && nowMs - it.receivedAtMs <= DirectorGoal.STALE_MS
        }
        if (previous != null) {
            val again = render(framing, previous, engine, face, scene)
            if (again !== framing) return again
        }
        return framing
    }

    private fun safety(framing: Framing): Boolean {
        return framing.strategy == CompositionStrategy.BREATHING_ROOM ||
            framing.strategy == CompositionStrategy.NO_SUBJECT
    }

    private fun render(
        framing: Framing,
        goal: DirectorGoal,
        engine: GuideEngine,
        face: FaceBoxNormalized?,
        scene: SceneRead?,
    ): Framing {
        val message = framing.guide.message
        val roll = message == GeometryCoach.ROTATE_CLOCKWISE || message == GeometryCoach.ROTATE_COUNTERCLOCKWISE
        if (roll) {
            if (goal.action == "straighten") {
                return framing.copy(guide = framing.guide.copy(detail = goal.reason))
            }
            return framing
        }
        if (goal.action == "straighten") return framing
        if (LightingAdviceEngine.isCue(message) && goal.action !in LiveCoach.PHYSICAL) return framing
        if (GeometryCoach.isCue(message) && goal.action !in LiveCoach.PHYSICAL) return framing
        if (goal.action in LiveCoach.PHYSICAL) return viewpoint(framing, goal)
        if (goal.preset == "low_horizon" || goal.preset == "high_horizon") {
            return horizon(framing, goal, engine, face, scene)
        }
        if (goal.action == "hold" && goal.preset == "none") {
            if (!framing.guide.isReady) return framing
            return decorate(framing, goal)
        }
        val faceBox = face ?: return framing
        val aim = aim(goal, faceBox) ?: return framing
        val directed = engine.advise(
            faceBox,
            scene = scene,
            lockedStrategy = aim.strategy,
            lockedTargetX = aim.x,
            lockedTargetY = aim.y,
        )
        if (directed.strategy == CompositionStrategy.BREATHING_ROOM || directed.strategy == CompositionStrategy.NO_SUBJECT) {
            return framing
        }
        if (GeometryCoach.isCue(directed.guide.message)) return framing
        if (!agrees(faceBox, directed.guide, goal.action)) return framing
        return decorate(directed, goal)
    }

    private fun horizon(
        framing: Framing,
        goal: DirectorGoal,
        engine: GuideEngine,
        face: FaceBoxNormalized?,
        scene: SceneRead?,
    ): Framing {
        if (face != null || scene?.hasHorizon() != true) return framing
        val horizon = scene.horizonY ?: return framing
        val target = if (goal.preset == "low_horizon") GuideEngine.THIRD_HI else GuideEngine.THIRD_LO
        val directed = engine.advise(
            null,
            scene = scene,
            lockedStrategy = CompositionStrategy.HORIZON,
            lockedHorizonY = target,
        )
        if (!agreesHorizon(horizon, directed.guide.targetY.toFloat(), goal.action)) return framing
        return decorate(directed, goal)
    }

    private fun aim(goal: DirectorGoal, face: FaceBoxNormalized): Aim? {
        return when (goal.preset) {
            "centered" -> Aim(CompositionStrategy.CENTERED, 0.5f, null)
            "negative_space_left" -> Aim(CompositionStrategy.THIRDS, GuideEngine.THIRD_HI, null)
            "negative_space_right" -> Aim(CompositionStrategy.THIRDS, GuideEngine.THIRD_LO, null)
            "leading_space" -> Aim(CompositionStrategy.LOOK_ROOM, leadingX(goal), null)
            "environmental" -> Aim(CompositionStrategy.ENVIRONMENTAL, null, null)
            "street_layer" -> Aim(CompositionStrategy.THIRDS, null, null)
            "none" -> nudge(goal, face)
            else -> null
        }
    }

    private fun leadingX(goal: DirectorGoal): Float? = when (goal.action) {
        "pan_left" -> GuideEngine.THIRD_HI
        "pan_right" -> GuideEngine.THIRD_LO
        else -> null
    }

    private fun nudge(goal: DirectorGoal, face: FaceBoxNormalized): Aim? {
        return when (goal.action) {
            "pan_left" -> Aim(CompositionStrategy.THIRDS, GuideEngine.THIRD_HI, null)
            "pan_right" -> Aim(CompositionStrategy.THIRDS, GuideEngine.THIRD_LO, null)
            "tilt_up" -> tilt(face, up = true)
            "tilt_down" -> tilt(face, up = false)
            else -> null
        }
    }

    private fun tilt(face: FaceBoxNormalized, up: Boolean): Aim? {
        val lines = listOf(GuideEngine.THIRD_LO, GuideEngine.THIRD_HI)
        val y = if (up) {
            lines.filter { it > face.anchorY + 0.06f }.minOrNull()
        } else {
            lines.filter { it < face.anchorY - 0.06f }.maxOrNull()
        } ?: return null
        return Aim(CompositionStrategy.THIRDS, face.anchorX.coerceIn(0.33f, 0.67f), y)
    }

    private fun agrees(face: FaceBoxNormalized, guide: LiveGuide, action: String): Boolean {
        val phoneX = face.anchorX - guide.targetX.toFloat()
        val phoneY = face.anchorY - guide.targetY.toFloat()
        val ax = abs(phoneX)
        val ay = abs(phoneY)
        return when (action) {
            "hold" -> ax <= GuideEngine.DEFAULT_TOLERANCE_X && ay <= GuideEngine.DEFAULT_TOLERANCE_Y
            "pan_left" -> phoneX < -0.04f && ax >= ay
            "pan_right" -> phoneX > 0.04f && ax >= ay
            "tilt_up" -> phoneY < -0.04f && ay > ax
            "tilt_down" -> phoneY > 0.04f && ay >= ax
            else -> false
        }
    }

    private fun agreesHorizon(horizon: Float, target: Float, action: String): Boolean {
        val phoneY = horizon - target
        return when (action) {
            "hold" -> abs(phoneY) <= GuideEngine.DEFAULT_TOLERANCE_Y
            "tilt_up" -> phoneY < -0.04f
            "tilt_down" -> phoneY > 0.04f
            else -> false
        }
    }

    private fun viewpoint(framing: Framing, goal: DirectorGoal): Framing {
        return framing.copy(
            guide = framing.guide.copy(
                kind = GuideEngine.KIND_ALIGN,
                message = goal.instruction,
                detail = goal.reason,
                hideGoal = true,
                cueSymbol = symbol(goal.action),
                isReady = false,
                zoom = GuideEngine.ZOOM_HOLD,
                targetZoomRatio = 1.0,
                source = AdviceSource.GEMINI.wire,
            ),
            confidence = goal.confidence,
            urgent = false,
        )
    }

    private fun decorate(framing: Framing, goal: DirectorGoal): Framing {
        val ready = framing.guide.isReady
        val title = when {
            ready && goal.action == "hold" -> goal.instruction
            ready -> "Hold this angle."
            else -> goal.instruction
        }
        return framing.copy(
            guide = framing.guide.copy(
                message = title,
                detail = goal.reason,
                hideGoal = false,
                cueSymbol = null,
                source = AdviceSource.GEMINI.wire,
            ),
            confidence = goal.confidence,
        )
    }

    private fun symbol(action: String): String = when (action) {
        "orbit_left" -> "↶"
        "orbit_right" -> "↷"
        "step_left", "step_right" -> "⇄"
        "move_closer" -> "+"
        "move_farther" -> "–"
        "lower_camera" -> "⌄"
        "raise_camera" -> "⌃"
        "change_lighting" -> "◐"
        "expose_for_subject" -> "☀"
        else -> "◎"
    }
}
