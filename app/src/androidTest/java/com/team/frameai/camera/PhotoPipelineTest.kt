package com.team.frameai.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.RectF
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.camera.view.TransformExperimental
import androidx.camera.view.transform.OutputTransform
import androidx.test.platform.app.InstrumentationRegistry
import com.team.frameai.lut.BitmapLutRepository
import com.team.frameai.lut.CubePhotoGrader
import com.team.frameai.model.RawFaceBox
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.io.IOException

class PhotoPipelineTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun packagedLooksChangePixelsWithoutChangingOriginal() {
        val source = Bitmap.createBitmap(3, 1, Bitmap.Config.ARGB_8888)
        source.setPixels(intArrayOf(0xFF808080.toInt(), 0xFFD2A082.toInt(), 0xFF28466E.toInt()), 0, 3, 0, 0, 3, 1)
        val before = source.copy(Bitmap.Config.ARGB_8888, false)
        val grader = CubePhotoGrader(BitmapLutRepository.fromAssets(context.assets))
        val warm = grader.apply(source, "warm_film", 0.85f)
        val cool = grader.apply(source, "cool_cinema", 0.85f)
        assertFalse(source.sameAs(warm))
        assertFalse(source.sameAs(cool))
        assertFalse(warm.sameAs(cool))
        assertTrue(source.sameAs(before))
        listOf(source, before, warm, cool).forEach { it.recycle() }
    }

    @Test fun galleryPublishesTheJpegAndRemovesFailedPendingWrites() {
        assumeTrue(Build.VERSION.SDK_INT >= 29)
        val name = "frameai_export_test_" + System.nanoTime() + ".jpg"
        val source = File(context.cacheDir, name)
        val bitmap = Bitmap.createBitmap(4, 6, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF4477AA.toInt()) }
        ImageSaver.writeJpeg(bitmap, source)
        bitmap.recycle()
        val resolver = context.contentResolver
        val uri = ImageSaver.exportToGallery(context, source)!!
        try {
            resolver.query(uri, arrayOf(MediaStore.Images.Media.IS_PENDING), null, null, null)!!.use {
                assertTrue(it.moveToFirst())
                assertEquals(0, it.getInt(0))
            }
            val saved = resolver.openInputStream(uri)!!.use(BitmapFactory::decodeStream)
            assertEquals(4, saved.width)
            assertEquals(6, saved.height)
            saved.recycle()
        } finally {
            resolver.delete(uri, null, null)
            source.delete()
        }

        try {
            ImageSaver.exportToGallery(context, source)
            fail("Missing source must not leave a published/pending entry")
        } catch (_: IOException) {
            resolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Images.Media._ID),
                MediaStore.Images.Media.DISPLAY_NAME + " = ?", arrayOf(name), null,
            )!!.use { assertEquals(0, it.count) }
        }
    }

    @androidx.annotation.OptIn(markerClass = [TransformExperimental::class])
    @Test fun rawCoordinatesMapOnceThroughRotationAndCrop() {
        val sourceMatrix = Matrix().apply {
            setRectToRect(RectF(-1f, -1f, 1f, 1f), RectF(0f, 0f, 640f, 480f), Matrix.ScaleToFit.FILL)
        }
        val targetMatrix = Matrix(sourceMatrix).apply {
            postRotate(90f)
            postTranslate(480f, 0f)
        }
        val source = OutputTransform(sourceMatrix, Size(640, 480))
        val target = OutputTransform(targetMatrix, Size(640, 480))
        val face = RawFaceBox(0.25f, 0.2f, 0.2f, 0.1f, 0.3f, 0.3f)
        val mapped = AnalysisCoordinateMapper.map(source, target, 640, 480, 480, 640, face)!!
        assertEquals(0.8f, mapped.centerX, 0.001f)
        assertEquals(0.25f, mapped.centerY, 0.001f)
        assertEquals(0.7f, mapped.left, 0.001f)
        assertEquals(0.9f, mapped.right, 0.001f)
        assertNull(AnalysisCoordinateMapper.map(source, target, 640, 480, 0, 640, face))
    }
}
