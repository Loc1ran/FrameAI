package com.team.frameai.guide

/** Body movement instructions are distinct from aiming the camera or using digital zoom. */
data class MovementCue(val label: String, val method: String, val symbol: String)

object ViewpointGuidance {
    fun cue(action: String): MovementCue? = when (action) {
        "step_left" -> MovementCue("MOVE YOURSELF LEFT", "Take a small step left, keeping the camera aimed at your subject.", "←")
        "step_right" -> MovementCue("MOVE YOURSELF RIGHT", "Take a small step right, keeping the camera aimed at your subject.", "→")
        "orbit_left" -> MovementCue("WALK AROUND TO YOUR LEFT", "Move a little around your subject to your left. Keep aiming at it from a similar distance.", "↶")
        "orbit_right" -> MovementCue("WALK AROUND TO YOUR RIGHT", "Move a little around your subject to your right. Keep aiming at it from a similar distance.", "↷")
        "lower_camera" -> MovementCue("TRY A LOWER VIEWPOINT", "Crouch slightly to lower the phone, then aim at your subject again.", "↓")
        "raise_camera" -> MovementCue("TRY A HIGHER VIEWPOINT", "Lift the phone a little higher, then aim at your subject again.", "↑")
        "move_closer" -> MovementCue("MOVE YOURSELF CLOSER", "Take a small step toward your subject, keeping important details in view.", "+")
        "move_farther" -> MovementCue("MOVE YOURSELF BACK", "Take a small step back where there is room, keeping your subject in view.", "–")
        else -> null
    }
}
