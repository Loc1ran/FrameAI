package com.team.frameai.guide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OdysseyJourneyTest {
    @Test fun sirensStayOpenUntilTheSubjectActuallyMovesClear() {
        val start = evidence(subjectX = 0.50f, edgeProblem = true, issue = "distracting_background")
        val barely = evidence(subjectX = 0.54f, edgeProblem = true, issue = "distracting_background")
        val movedButStillCluttered = evidence(subjectX = 0.66f, edgeProblem = true, issue = "none", coachStamp = 2)
        val cleared = evidence(subjectX = 0.68f, edgeProblem = false, issue = "none")
        assertFalse(OdysseyJourney.evaluate(OdysseyTrial.SIRENS, start, barely).completed)
        assertFalse(OdysseyJourney.evaluate(OdysseyTrial.SIRENS, start, movedButStillCluttered).completed)
        assertTrue(OdysseyJourney.evaluate(OdysseyTrial.SIRENS, start, cleared).completed)
    }

    @Test fun aGeminiDistractionNeedsALaterCleanReading() {
        val start = evidence(subjectX = 0.40f, issue = "distracting_background", coachStamp = 10)
        val sameReply = evidence(subjectX = 0.55f, issue = "none", coachStamp = 10)
        val later = evidence(subjectX = 0.55f, issue = "none", coachStamp = 11)
        assertFalse(OdysseyJourney.evaluate(OdysseyTrial.SIRENS, start, sameReply).completed)
        assertTrue(OdysseyJourney.evaluate(OdysseyTrial.SIRENS, start, later).completed)
    }

    @Test fun cyclopsIgnoresASmallShiftAndAWorseFrame() {
        val start = evidence(subjectY = 0.50f, subjectHeight = 0.30f, ready = false, issue = "empty_space")
        val jitter = evidence(subjectY = 0.54f, subjectHeight = 0.31f, ready = true, issue = "none")
        val lowerButCutOff = evidence(subjectY = 0.70f, subjectHeight = 0.30f, ready = true, edgeProblem = true, issue = "none")
        val lowerAndHeld = evidence(subjectY = 0.68f, subjectHeight = 0.30f, ready = true, issue = "already_strong")
        assertFalse(OdysseyJourney.evaluate(OdysseyTrial.CYCLOPS, start, jitter).completed)
        assertFalse(OdysseyJourney.evaluate(OdysseyTrial.CYCLOPS, start, lowerButCutOff).completed)
        assertTrue(OdysseyJourney.evaluate(OdysseyTrial.CYCLOPS, start, lowerAndHeld).completed)
    }

    @Test fun scyllaNeedsAMeasuredBalanceAndAllowsASilhouette() {
        val start = evidence(issue = "backlit", subjectLuma = 0.20f, backgroundLuma = 0.70f, clippedShare = 0.04f)
        val stillASilhouette = evidence(subjectLuma = 0.16f, backgroundLuma = 0.62f, clippedShare = 0.04f)
        val blown = evidence(subjectLuma = 0.55f, backgroundLuma = 0.70f, clippedShare = 0.20f)
        val balanced = evidence(subjectLuma = 0.42f, backgroundLuma = 0.58f, clippedShare = 0.05f, issue = "none")
        assertFalse(OdysseyJourney.evaluate(OdysseyTrial.SCYLLA, start, stillASilhouette).completed)
        assertFalse(OdysseyJourney.evaluate(OdysseyTrial.SCYLLA, start, blown).completed)
        assertTrue(OdysseyJourney.evaluate(OdysseyTrial.SCYLLA, start, balanced).completed)
        assertNull(OdysseyJourney.evaluate(OdysseyTrial.SCYLLA, evidence(), evidence(subjectLuma = 0.5f)).lesson)
    }

    @Test fun suggestionFollowsTheMeasuredProblem() {
        assertEquals(OdysseyTrial.SIRENS, OdysseyJourney.suggest(evidence(edgeProblem = true)))
        assertEquals(OdysseyTrial.CYCLOPS, OdysseyJourney.suggest(evidence(action = "lower_camera")))
        assertEquals(OdysseyTrial.SCYLLA, OdysseyJourney.suggest(evidence(lightingCue = true)))
        assertEquals(OdysseyStage.TRANSFORMATION, OdysseyJourney.stage(trialActive = true, learned = true))
        assertEquals(OdysseyStage.DEPARTURE, OdysseyJourney.stage(trialActive = false, learned = false))
    }

    @Test fun journalRoundTripKeepsTheLessonWithThePhoto() {
        val file = File.createTempFile("odyssey-journal", ".json")
        file.delete()
        val journal = OdysseyJournal(file)
        val saved = journal.add(
            JournalEntry(
                id = "shot-1",
                photoPath = "/tmp/shot.jpg",
                capturedAt = "2026-11-15",
                style = "Portrait",
                trial = "SIRENS",
                skill = "Subject emphasis",
                lesson = "A step to the side cleared the background.",
                feedback = "The face is the brightest point.",
            ),
        )
        assertEquals(1, saved.size)
        assertEquals("SIRENS", OdysseyJournal(file).load().single().trial)
        file.delete()
    }

    private fun evidence(
        subjectX: Float? = 0.5f,
        subjectY: Float? = 0.5f,
        subjectHeight: Float? = 0.3f,
        ready: Boolean = false,
        edgeProblem: Boolean = false,
        lightingCue: Boolean = false,
        issue: String = "none",
        action: String = "hold",
        subjectLuma: Float? = null,
        backgroundLuma: Float? = null,
        clippedShare: Float? = null,
        coachStamp: Long = 1,
    ) = FrameEvidence(
        subjectX, subjectY, subjectHeight, ready, edgeProblem, lightingCue,
        issue, action, subjectLuma, backgroundLuma, clippedShare, coachStamp,
    )
}
