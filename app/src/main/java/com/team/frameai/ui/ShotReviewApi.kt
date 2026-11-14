package com.team.frameai.ui

import com.team.frameai.model.ContractJson
import com.team.frameai.model.ShotReview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * One explicit POST /v1/shot-review. Empty base URL, timeout, and bad JSON
 * all return the labeled offline review. Nothing here is called from the preview loop.
 */
class ShotReviewClient(
    private val baseUrl: String,
    private val demoToken: String,
    private val offlineReview: ShotReview,
) {
    val cloudConfigured: Boolean get() = baseUrl.isNotBlank()

    suspend fun review(jpeg: ByteArray, cloudEnabled: Boolean): ShotReview {
        if (!cloudEnabled || baseUrl.isBlank()) return offlineReview
        return try {
            withTimeout(12_000) {
                withContext(Dispatchers.IO) { post(jpeg) }
            }
        } catch (_: Exception) {
            offlineReview
        }
    }

    private fun post(jpeg: ByteArray): ShotReview {
        val endpoint = baseUrl.trimEnd('/') + "/v1/shot-review"
        val boundary = "frameai-" + UUID.randomUUID()
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12_000
            readTimeout = 12_000
            doOutput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            if (demoToken.isNotBlank()) {
                setRequestProperty("X-Demo-Token", demoToken)
            }
        }
        try {
            connection.outputStream.use { it.write(multipart(jpeg, boundary)) }
            if (connection.responseCode != 200) return offlineReview
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            if (body.isBlank()) return offlineReview
            return ContractJson.parseShotReview(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun multipart(jpeg: ByteArray, boundary: String): ByteArray {
        val out = ByteArrayOutputStream()
        val header = buildString {
            append("--").append(boundary).append("\r\n")
            append("Content-Disposition: form-data; name=\"image\"; filename=\"shot.jpg\"\r\n")
            append("Content-Type: image/jpeg\r\n\r\n")
        }
        out.write(header.toByteArray(Charsets.UTF_8))
        out.write(jpeg)
        out.write("\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8))
        return out.toByteArray()
    }
}
