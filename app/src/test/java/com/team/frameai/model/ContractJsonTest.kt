package com.team.frameai.model

import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class ContractJsonTest {
    @Test
    fun parsesFrozenGuidesAndReviews() {
        listOf(
            "live-guide.mock.json",
            "live-guide.ready.mock.json",
            "live-guide.noface.mock.json",
        ).forEach { name ->
            val guide = ContractJson.parseLiveGuide(contract(name))
            assertTrue(guide.kind in setOf("ALIGN_SUBJECT", "READY", "NO_FACE"))
        }
        val review = ContractJson.parseShotReview(contract("shot-review.mock.json"))
        assertEquals("warm_film", review.suggestedLutId)
        assertEquals("demo_mock", review.source)
        val offline = ContractJson.parseShotReview(contract("shot-review.offline.json"))
        assertEquals("offline_rules", offline.source)
    }

    @Test
    fun packagedAssetsMatchFrozenContracts() {
        listOf(
            "live-guide.mock.json",
            "live-guide.ready.mock.json",
            "live-guide.noface.mock.json",
            "shot-review.mock.json",
            "shot-review.offline.json",
        ).forEach { name ->
            assertEquals(contract(name), asset(name))
        }
    }

    @Test
    fun rejectsUnknownLutAndMissingSource() {
        assertRejected(
            """
            {"sceneType":"portrait","summary":"x","improvement":"y","principle":"z","suggestedLutId":"vintage","lutReason":"no","source":"gemini"}
            """.trimIndent(),
        )
        assertRejected(
            """
            {"sceneType":"portrait","summary":"x","improvement":"y","principle":"z","suggestedLutId":"warm_film","lutReason":"no"}
            """.trimIndent(),
        )
        assertRejected(
            """
            {"sceneType":"portrait","summary":"${"x".repeat(201)}","improvement":"y","principle":"z","suggestedLutId":"none","lutReason":"no","source":"offline_rules"}
            """.trimIndent(),
        )
    }

    @Test
    fun originalAndEditedNamesStayDistinct() {
        val stamp = "20261114_140000"
        val original = com.team.frameai.camera.PhotoNames.original(stamp)
        val warm = com.team.frameai.camera.PhotoNames.edited(stamp, "warm_film")
        val cool = com.team.frameai.camera.PhotoNames.edited(stamp, "cool_cinema")
        assertTrue(original.endsWith("_original.jpg"))
        assertNotEquals(original, warm)
        assertNotEquals(warm, cool)
        val unsafe = com.team.frameai.camera.PhotoNames.edited(stamp, "../cool")
        assertTrue(!unsafe.contains("/"))
        assertTrue(!unsafe.contains(".."))
    }

    private fun assertRejected(raw: String) {
        try {
            ContractJson.parseShotReview(raw)
            fail("expected rejection")
        } catch (expected: Exception) {
            assertTrue(expected is IllegalArgumentException || expected is SerializationException)
        }
    }

    private fun contract(name: String): String = File("../contracts/$name").readText()

    private fun asset(name: String): String = File("src/main/assets/contracts/$name").readText()
}
