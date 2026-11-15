package com.team.frameai.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MatchingSuggestionTest {
    @Test fun aDifferentMoveIsReplacedByTheCurrentInstruction() {
        val shown = matchingSuggestion(
            instruction = "Step closer.",
            message = "Tilt the phone up.",
            sentFor = "Step closer.",
        )
        assertEquals("Step closer.", shown)
    }

    @Test fun aReasonAfterTheSameInstructionIsKept() {
        val shown = matchingSuggestion(
            instruction = "Step closer.",
            message = "Step closer. The subject is small.",
            sentFor = "Step closer.",
        )
        assertEquals("Step closer. The subject is small.", shown)
    }

    @Test fun anOlderReplyDoesNotOverrideTheNewInstruction() {
        val shown = matchingSuggestion(
            instruction = "Hold steady.",
            message = "Step closer. The subject is small.",
            sentFor = "Step closer.",
        )
        assertEquals("Hold steady.", shown)
    }
}
