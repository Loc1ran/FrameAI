package com.team.frameai.guide

/**
 * Homer's Odyssey as a three-frame voyage. Geometry stays in [GuideEngine].
 * These lines are only the words and the order of the looks.
 */
object Odyssey {
    enum class Episode(val banner: String, val aim: String, val readyLine: String) {
        THRESHOLD(
            "1 · THE THRESHOLD",
            "A figure at the edge, open space ahead.",
            "Harbor light. The departure can be taken.",
        ),
        CROSSING(
            "2 · THE CROSSING",
            "A horizon on a third, or one small traveler in a large place.",
            "The sea holds. The crossing can be taken.",
        ),
        RETURN(
            "3 · THE RETURN",
            "A closer face. The open space from the first frame is filled.",
            "You are home. The return can be taken.",
        ),
        ;

        val place: String
            get() = when (this) {
                THRESHOLD -> "Threshold"
                CROSSING -> "Crossing"
                RETURN -> "Return"
            }
    }

    data class Look(val id: String, val name: String, val line: String)

    val looks: List<Look> = listOf(
        Look("warm_film", "Rosy-fingered dawn", "This is the departure."),
        Look("cool_cinema", "Wine-dark sea", "This is the crossing."),
        Look("g_outdoors1", "The olive grove", "This is the shore you rest on."),
        Look("eastman_neg_5247_warm", "The bronze shore", "This is the bronze light of the beach."),
        Look("cinematic_06", "The hall at night", "This is the hall after the voyage."),
    )

    val lookIds: List<String> = looks.map { it.id }

    const val ITHACA = "This is Ithaca, unchanged."

    fun episodeAt(index: Int): Episode = Episode.entries[index.coerceIn(0, Episode.entries.lastIndex)]

    fun nextIndex(index: Int): Int = (index + 1).coerceAtMost(Episode.entries.lastIndex)

    fun nameOf(lutId: String): String? = looks.firstOrNull { it.id == lutId }?.name

    fun sentence(lutId: String): String = looks.firstOrNull { it.id == lutId }?.line ?: ITHACA

    fun title(message: String, episode: Episode, ready: Boolean): String {
        if (ready) return when (episode) {
            Episode.THRESHOLD -> "Harbor light"
            Episode.CROSSING -> "The sea holds"
            Episode.RETURN -> "You are home"
        }
        return when {
            message == GuideEngine.HORIZON_LOWER || message == GuideEngine.HORIZON_LOWER_CUE ||
                message.startsWith("Tilt down") -> "Drop the horizon"
            message == GuideEngine.HORIZON_UPPER || message == GuideEngine.HORIZON_HIGHER_CUE ||
                message.startsWith("Tilt up") -> "Raise the shore"
            message == GuideEngine.LARGE_MESSAGE || message.startsWith("Zoom out") ||
                message.startsWith("Step back") -> "Make the traveler small"
            message == GuideEngine.SMALL_MESSAGE || message.startsWith("Step closer") ||
                message.startsWith("Zoom in") ->
                if (episode == Episode.CROSSING) "The traveler is small" else "Come closer to shore"
            message == GuideEngine.BREATHING_CUE || message == GuideEngine.LOOK_CUE -> "Leave sea room"
            message == GuideEngine.EDGE_MESSAGE -> "Step to the threshold"
            message == GuideEngine.ENVIRONMENT_CUE -> "Keep the shore in frame"
            message == CoachAction.MOVE_LEFT || message.startsWith("Move slightly left") ||
                message.startsWith("Move left") -> "Bear left"
            message == CoachAction.MOVE_RIGHT || message.startsWith("Move slightly right") ||
                message.startsWith("Move right") -> "Bear right"
            message == CoachAction.MOVE_UP || message.startsWith("Move camera upward") ||
                message.startsWith("Move up") -> "Lift the bow"
            message == CoachAction.MOVE_DOWN || message.startsWith("Move camera downward") ||
                message.startsWith("Move down") -> "Lower the bow"
            message == GuideEngine.NO_FACE_MESSAGE -> when (episode) {
                Episode.THRESHOLD -> "Find the traveler"
                Episode.CROSSING -> "Find the crossing"
                Episode.RETURN -> "Find the face"
            }
            else -> when (episode) {
                Episode.THRESHOLD -> "Step to the threshold"
                Episode.CROSSING -> "Hold the crossing"
                Episode.RETURN -> "Fill the space you left"
            }
        }
    }

    fun detail(episode: Episode, ready: Boolean): String =
        if (ready) episode.readyLine else episode.aim

    /** A small traveler is the crossing itself, even when a portrait rule asks them to grow. */
    fun countsAsReady(engineReady: Boolean, message: String, episode: Episode): Boolean {
        if (engineReady) return true
        if (episode != Episode.CROSSING) return false
        return message == GuideEngine.SMALL_MESSAGE ||
            message.startsWith("Step closer") ||
            message.startsWith("Zoom in") ||
            message == "The traveler is small"
    }
}
