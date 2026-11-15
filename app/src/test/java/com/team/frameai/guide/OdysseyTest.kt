package com.team.frameai.guide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OdysseyTest {
    @Test fun looksStayInVoyageOrder() {
        assertEquals(
            listOf("warm_film", "cool_cinema", "g_outdoors1", "eastman_neg_5247_warm", "cinematic_06"),
            Odyssey.lookIds,
        )
        assertEquals("Rosy-fingered dawn", Odyssey.nameOf("warm_film"))
        assertEquals(Odyssey.ITHACA, Odyssey.sentence("original"))
    }

    @Test fun wordsNameTheMoveWithoutChangingItsMeaning() {
        assertEquals("Drop the horizon", Odyssey.title(GuideEngine.HORIZON_LOWER, Odyssey.Episode.CROSSING, false))
        assertEquals("Leave sea room", Odyssey.title(GuideEngine.BREATHING_CUE, Odyssey.Episode.THRESHOLD, false))
        assertEquals("Make the traveler small", Odyssey.title(GuideEngine.LARGE_MESSAGE, Odyssey.Episode.CROSSING, false))
        assertEquals("Bear left", Odyssey.title(CoachAction.MOVE_LEFT, Odyssey.Episode.THRESHOLD, false))
        assertEquals("Harbor light", Odyssey.title(GuideEngine.GREAT_CUE, Odyssey.Episode.THRESHOLD, true))
    }

    @Test fun aSmallTravelerFinishesTheCrossing() {
        assertTrue(Odyssey.countsAsReady(false, GuideEngine.SMALL_MESSAGE, Odyssey.Episode.CROSSING))
        assertFalse(Odyssey.countsAsReady(false, GuideEngine.SMALL_MESSAGE, Odyssey.Episode.RETURN))
        assertEquals(1, Odyssey.nextIndex(0))
        assertEquals(2, Odyssey.nextIndex(2))
    }
}
