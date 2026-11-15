package com.team.frameai.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class CameraOrientationTest {
    @Test fun labelsFollowEachQuarterTurn() {
        assertEquals(0, cameraQuarterTurn(359, 0))
        assertEquals(90, cameraQuarterTurn(90, 0))
        assertEquals(180, cameraQuarterTurn(180, 90))
        assertEquals(270, cameraQuarterTurn(270, 180))
        assertEquals(0, cameraQuarterTurn(0, 270))
    }

    @Test fun diagonalNoiseAndUnknownOrientationKeepTheLastDirection() {
        assertEquals(0, cameraQuarterTurn(-1, 0))
        assertEquals(270, cameraQuarterTurn(-1, 270))
        assertEquals(0, cameraQuarterTurn(50, 0))
        assertEquals(90, cameraQuarterTurn(40, 90))
        assertEquals(90, cameraQuarterTurn(60, 0))
        assertEquals(0, cameraQuarterTurn(30, 90))
    }
}
