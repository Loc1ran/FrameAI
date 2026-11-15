package com.team.frameai.guide

import com.team.frameai.model.RawFaceBox
import org.junit.Assert.*
import org.junit.Test

class SubjectFramingTest {
    private val person = RawFaceBox(0.3f, 0.5f, 0.1f, 0.1f, 0.5f, 0.9f)
    private val face = RawFaceBox(0.3f, 0.3f, 0.2f, 0.2f, 0.4f, 0.4f)

    @Test fun automaticPortraitsUseTheFaceEvenWithoutObjectDetection() {
        assertSame(face, SubjectFraming.choose(face, null, false))
        assertSame(face, SubjectFraming.choose(face, person, false))
    }
    @Test fun selectedPersonRetainsFaceFraming() {
        assertSame(face, SubjectFraming.choose(face, person, true))
    }
    @Test fun anotherPersonsFaceCannotOverrideTheTappedSubject() {
        val other = RawFaceBox(0.8f, 0.3f, 0.7f, 0.2f, 0.9f, 0.4f)
        assertSame(person, SubjectFraming.choose(other, person, true))
        assertNull(SubjectFraming.choose(other, null, true))
    }
}
