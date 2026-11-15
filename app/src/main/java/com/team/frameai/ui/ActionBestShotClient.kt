package com.team.frameai.ui

import com.team.frameai.camera.ImageSaver
import com.team.frameai.guide.ActionFrameScore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class ActionPick(val index: Int, val reason: String, val source: String, val usable: Boolean)

/** Sends one small copy of each burst frame. Originals remain on the phone. */
class ActionBestShotClient(
    private val baseUrl: String,
    private val demoToken: String,
) {
    val enabled: Boolean get() = baseUrl.isNotBlank() && demoToken.isNotBlank()

    suspend fun choose(files: List<File>): ActionPick? {
        if (!enabled || files.size < 2) return null
        return try {
            val images = withContext(Dispatchers.Default) {
                files.mapNotNull { file ->
                    ImageSaver.decodeOriented(file.absolutePath, 768)?.let { bitmap ->
                        try { ImageSaver.jpegForReview(bitmap) } finally { bitmap.recycle() }
                    }
                }
            }
            if (images.size != files.size) return null
            withTimeout(20_000) { withContext(Dispatchers.IO) { post(images) } }
        } catch (_: TimeoutCancellationException) {
            null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    suspend fun localBest(files: List<File>): Int = withContext(Dispatchers.Default) {
        val scores = files.map { file ->
            val bitmap = ImageSaver.decodeOriented(file.absolutePath, 256)
                ?: error("Could not decode a burst frame")
            try {
                val width = bitmap.width
                val height = bitmap.height
                val pixels = IntArray(width * height)
                bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
                val luma = IntArray(pixels.size) { i ->
                    val color = pixels[i]
                    val red = color shr 16 and 0xff
                    val green = color shr 8 and 0xff
                    val blue = color and 0xff
                    (red * 77 + green * 150 + blue * 29) shr 8
                }
                ActionFrameScore.sharpness(luma, width, height)
            } finally {
                bitmap.recycle()
            }
        }
        ActionFrameScore.best(scores)
    }

    private fun post(images: List<ByteArray>): ActionPick? {
        val boundary = "frameai-action-" + UUID.randomUUID()
        val connection = (URL(baseUrl.trimEnd('/') + "/v1/action-best-shot")
            .openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12_000
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setRequestProperty("X-Demo-Token", demoToken)
        }
        try {
            connection.outputStream.use { it.write(multipart(images, boundary)) }
            if (connection.responseCode != 200) return null
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val index = json.optInt("selectedIndex", -1)
            if (index !in images.indices) return null
            // Older servers cannot certify that any frame is usable.
            val usable = json.opt("usable") as? Boolean ?: return null
            return ActionPick(
                index = index,
                reason = json.optString("reason", "Selected from the action burst."),
                source = json.optString("source", ""),
                usable = usable,
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun multipart(images: List<ByteArray>, boundary: String): ByteArray {
        val out = ByteArrayOutputStream()
        images.forEachIndexed { index, jpeg ->
            out.write("--$boundary\r\n".toByteArray())
            out.write(
                "Content-Disposition: form-data; name=\"images\"; filename=\"burst_$index.jpg\"\r\n"
                    .toByteArray(),
            )
            out.write("Content-Type: image/jpeg\r\n\r\n".toByteArray())
            out.write(jpeg)
            out.write("\r\n".toByteArray())
        }
        out.write("--$boundary--\r\n".toByteArray())
        return out.toByteArray()
    }
}
