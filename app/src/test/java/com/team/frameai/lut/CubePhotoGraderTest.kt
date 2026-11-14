package com.team.frameai.lut

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

class CubePhotoGraderTest {
    @Test
    fun failedGradePropagatesInsteadOfReturningTheOriginal() {
        val source = unusedBitmap()
        val grader = CubePhotoGrader { _, lutId, _ ->
            throw UnknownLutException(lutId)
        }

        try {
            grader.apply(source, "not_a_lut", 0.85f)
            fail("A failed grade was returned as a bitmap")
        } catch (error: UnknownLutException) {
            assertEquals("not_a_lut", error.lutId)
        }
    }

    @Test
    fun successfulGradeReturnsTheGradedBitmap() {
        val source = unusedBitmap()
        val graded = unusedBitmap()
        val grader = CubePhotoGrader { _, _, _ -> graded }

        assertSame(graded, grader.apply(source, "warm_film", 0.85f))
    }

    private fun unusedBitmap(): Bitmap {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val field = unsafeClass.getDeclaredField("theUnsafe")
        field.isAccessible = true
        val unsafe = field.get(null)
        val allocate = unsafeClass.getMethod("allocateInstance", Class::class.java)
        return allocate.invoke(unsafe, Bitmap::class.java) as Bitmap
    }
}
