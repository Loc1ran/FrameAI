package com.team.frameai.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MatchingSuggestionTest {
    @Test fun aLookLineNamesTheGradeAndNotAMove() {
        assertEquals("Suggested look · Warm Film", coachingLookLine("Warm Film"))
        assertNull(coachingLookLine(null))
        assertNull(coachingLookLine("  "))
    }
}
