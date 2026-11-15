package com.team.frameai.guide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActionExposureTest {
    private fun plan(seconds: Double, iso: Int = 100, min: Long = 100_000L, maxIso: Int = 6400) =
        ActionExposure.plan(seconds, iso, min, 1_000_000_000L, 50, maxIso)

    @Test fun shortensExposureAndCompensatesWithIso() {
        assertEquals(ActionExposurePlan(1_000_000L, 2000), plan(0.02))
    }

    @Test fun keepsAnAlreadyFasterExposure() {
        assertEquals(ActionExposurePlan(500_000L, 100), plan(0.0005))
    }

    @Test fun refusesLightLevelsBeyondOneStopOfCompensation() {
        assertNull(plan(0.1, iso = 1600))
        assertEquals(ActionExposurePlan(1_000_000L, 6400), plan(0.01, iso = 1000))
    }

    @Test fun respectsSensorLimitsButRejectsSlowSensors() {
        assertEquals(ActionExposurePlan(2_000_000L, 1000), plan(0.02, min = 2_000_000L))
        assertNull(plan(0.02, min = 3_000_000L))
    }

    @Test fun rejectsMissingOrInvalidMetering() {
        assertNull(plan(0.0))
        assertNull(plan(Double.NaN))
        assertNull(plan(Double.POSITIVE_INFINITY))
        assertNull(plan(0.02, iso = 0))
        assertNull(plan(0.02, maxIso = 10))
    }
}
