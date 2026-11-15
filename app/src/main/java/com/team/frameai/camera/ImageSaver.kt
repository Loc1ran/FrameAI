package com.team.frameai.camera

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.max

object ImageSaver {
    fun createOriginal(context: Context, stamp: String): File {
        val name = PhotoNames.original(stamp)
        return uniqueFile(pictures(context, "originals"), name)
    }

    fun createEdit(context: Context, stamp: String, lutId: String): File {
        val name = PhotoNames.edited(stamp, lutId)
        return uniqueFile(pictures(context, "edits"), name)
    }

    fun writeJpeg(bitmap: Bitmap, file: File, quality: Int = 92) {
        file.parentFile?.mkdirs()
        try {
            FileOutputStream(file).use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)) {
                    throw IOException("Could not encode JPEG")
                }
            }
        } catch (error: Exception) {
            file.delete()
            throw error
        }
    }

    fun decodeOriented(path: String, maxEdge: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        val longEdge = max(bounds.outWidth, bounds.outHeight)
        while (longEdge / sample > maxEdge) sample *= 2
        val decoded = BitmapFactory.decodeFile(
            path,
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            },
        ) ?: return null
        val orientation = ExifInterface(path).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.preScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.preScale(-1f, 1f)
            }
        }
        if (matrix.isIdentity) return decoded
        val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (rotated != decoded) decoded.recycle()
        return rotated
    }

    /** Re-encoded JPEG with no EXIF. Long edge stays between 768 and 1024 when possible, under 1 MB. */
    fun jpegForReview(bitmap: Bitmap): ByteArray {
        val attempts = listOf(1024 to 82, 768 to 75)
        var last = ByteArray(0)
        for ((edge, quality) in attempts) {
            val scaled = scaleLongEdge(bitmap, edge)
            last = ByteArrayOutputStream().use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
                out.toByteArray()
            }
            if (scaled !== bitmap) scaled.recycle()
            if (last.size <= 1_000_000) return last
        }
        return last
    }

    fun exportToGallery(context: Context, source: File): Uri? {
        if (Build.VERSION.SDK_INT < 29) return null
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, source.name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/FrameAI")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Could not create gallery entry")
        try {
            val stream = resolver.openOutputStream(uri)
                ?: throw IOException("Could not open gallery entry")
            stream.use { out -> source.inputStream().use { input -> input.copyTo(out) } }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            if (resolver.update(uri, values, null, null) != 1) {
                throw IOException("Could not publish gallery entry")
            }
            return uri
        } catch (error: Exception) {
            runCatching { resolver.delete(uri, null, null) }.exceptionOrNull()?.let(error::addSuppressed)
            throw error
        }
    }

    fun share(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share photo").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun scaleLongEdge(source: Bitmap, maxEdge: Int): Bitmap {
        val longEdge = max(source.width, source.height)
        if (longEdge <= maxEdge) return source
        val scale = maxEdge.toFloat() / longEdge
        val width = (source.width * scale).toInt().coerceAtLeast(1)
        val height = (source.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, width, height, true)
    }

    private fun pictures(context: Context, child: String): File {
        val root = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.cacheDir
        return File(root, child).apply { mkdirs() }
    }

    private fun uniqueFile(directory: File, fileName: String): File {
        val dot = fileName.lastIndexOf('.')
        val stem = if (dot > 0) fileName.substring(0, dot) else fileName
        val ext = if (dot > 0) fileName.substring(dot) else ".jpg"
        var candidate = File(directory, stem + ext)
        var n = 2
        while (candidate.exists()) {
            candidate = File(directory, "${stem}_$n$ext")
            n++
        }
        return candidate
    }
}
