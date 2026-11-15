package com.team.frameai.guide

import com.team.frameai.model.LiveCoach
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SceneCompositionTest {
    private fun reply(source: String = "gemini", ready: Boolean = false) = LiveCoach(
        "other", "Golfer, club and ball", "none",
        "Keep the golfer left and leave room toward the range.", "hold", 1.0,
        0.4, 0.6, ready, "none", source,
    )
    @Test fun wholeActivityIsNamedInsteadOfCenteringPerson() {
        val advice = SceneComposition.suggest(reply(), true, true)
        assertEquals("Golfer, club and ball", advice.title)
        assertEquals(reply().message, advice.detail)
        assertFalse(advice.ready)
    }
    @Test fun onlyRealReadyReplyCanShowReady() {
        assertTrue(SceneComposition.suggest(reply(ready = true), true, true).ready)
        assertFalse(SceneComposition.suggest(reply("demo_mock", true), true, true).ready)
        assertFalse(SceneComposition.suggest(reply("offline_rules", true), true, true).ready)
    }
    @Test fun failedRequestShowsUnavailable() {
        assertEquals("Scene AI unavailable", SceneComposition.suggest(null, true, true, true).title)
    }
    @Test fun noAnalysisMeansNoInventedAdviceOrReady() {
        assertFalse(SceneComposition.suggest(null, true, true).ready)
        assertFalse(SceneComposition.suggest(reply(ready = true), false, true).ready)
        assertFalse(SceneComposition.suggest(reply(ready = true), true, false).ready)
    }
}
