package com.team.frameai.ui

import com.team.frameai.model.ContractJson
import com.team.frameai.model.LiveCoach
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Posts the current camera frame to /v1/live-coach.
 * The demo token is not a Google key. A timeout or any other failure returns null.
 */
class LiveCoachClient(
    private val baseUrl: String,
    private val demoToken: String,
) {
    val enabled: Boolean get() = baseUrl.isNotBlank() && demoToken.isNotBlank()

    suspend fun coach(jpeg: ByteArray): LiveCoach? {
        if (!enabled) return null
        return try {
            withTimeout(12_000) {
                withContext(Dispatchers.IO) { post(jpeg) }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun post(jpeg: ByteArray): LiveCoach? {
        val endpoint = baseUrl.trimEnd('/') + "/v1/live-coach"
        val boundary = "frameai-" + UUID.randomUUID()
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12_000
            readTimeout = 12_000
            doOutput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setRequestProperty("X-Demo-Token", demoToken)
        }
        try {
            connection.outputStream.use { it.write(multipart(jpeg, boundary)) }
            if (connection.responseCode != 200) return null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            if (body.isBlank()) return null
            return ContractJson.parseLiveCoach(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun multipart(jpeg: ByteArray, boundary: String): ByteArray {
        val out = ByteArrayOutputStream()
        val header = buildString {
            append("--").append(boundary).append("\r\n")
            append("Content-Disposition: form-data; name=\"image\"; filename=\"frame.jpg\"\r\n")
            append("Content-Type: image/jpeg\r\n\r\n")
        }
        out.write(header.toByteArray(Charsets.UTF_8))
        out.write(jpeg)
        out.write("\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8))
        return out.toByteArray()
    }
}
