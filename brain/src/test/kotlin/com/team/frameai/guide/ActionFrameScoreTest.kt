package com.team.frameai.guide

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ActionFrameScoreTest {
    @Test fun edgesScoreAboveAFlatFrame() {
        val flat = IntArray(25) { 100 }
        val edge = IntArray(25) { index -> if (index % 5 < 2) 0 else 255 }
        assertTrue(ActionFrameScore.sharpness(edge, 5, 5) > ActionFrameScore.sharpness(flat, 5, 5))
    }

    @Test fun bestReturnsHighestScore() {
        assertEquals(1, ActionFrameScore.best(listOf(2.0, 9.0, 4.0)))
        assertEquals(0, ActionFrameScore.best(emptyList()))
    }
}
