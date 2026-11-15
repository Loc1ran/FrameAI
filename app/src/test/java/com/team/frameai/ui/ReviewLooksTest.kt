package com.team.frameai.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewLooksTest {
    @Test fun withoutARankingTheRowIsTenPhoneLooks() {
        val ids = reviewLookIds(emptyList(), "warm_film")
        assertEquals(10, ids.size)
        assertEquals("warm_film", ids.first())
        assertTrue(ids.contains("cool_cinema"))
        assertFalse(ids.contains("alexa_logc_to_rec709"))
    }

    @Test fun rankingStaysAtTenAndDropsLogConversions() {
        val ranked = listOf(
            "cool_cinema",
            "alexa_logc_to_rec709",
            "warm_film",
            "cinematic_01",
            "cinematic_02",
            "cinematic_03",
            "cinematic_04",
            "cinematic_06",
            "cinematic_07",
            "cinematic_08",
            "cinematic_09",
            "cinematic_10",
        )
        val ids = reviewLookIds(ranked, "warm_film")
        assertEquals(10, ids.size)
        assertEquals("warm_film", ids.first())
        assertEquals(ids.distinct(), ids)
        assertFalse(ids.contains("alexa_logc_to_rec709"))
        assertFalse(ids.contains("cinematic_10"))
    }

    @Test fun anAppliedLookStaysOnTheRow() {
        val ranked = (1..10).map { "cinematic_%02d".format(it) }
        val ids = reviewLookIds(ranked, null, "warm_film")
        assertEquals(10, ids.size)
        assertEquals("warm_film", ids.first())
        assertFalse(ids.contains("cinematic_10"))
    }
}
