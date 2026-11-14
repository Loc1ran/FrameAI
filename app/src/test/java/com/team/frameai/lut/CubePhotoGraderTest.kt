package com.team.frameai.lut

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

/**
 * The production grader must not turn a failed lookup into a successful copy of the original.
 * The LUT is resolved before bitmap pixels are read.
 */
class CubePhotoGraderTest {
    @Test
    fun unknownLutFailsInsteadOfReturningTheOriginal() {
        val grader = CubePhotoGrader(BitmapLutRepository(LutRepository()))
        try {
            grader.apply(blankBitmap(), "not_a_real_lut", 0.85f)
            fail("unknown LUT must not look like a finished grade")
        } catch (expected: UnknownLutException) {
            assertEquals("not_a_real_lut", expected.lutId)
        }
    }

    @Test
    fun malformedCubeFailsInsteadOfReturningTheOriginal() {
        val repository = LutRepository(
            loaders = mapOf(LutIdsWarm to { throw CubeParseException("bad cube") }),
        )
        val grader = CubePhotoGrader(BitmapLutRepository(repository))
        try {
            grader.apply(blankBitmap(), LutIdsWarm, 0.85f)
            fail("malformed LUT must not look like a finished grade")
        } catch (expected: CubeParseException) {
            assertEquals("bad cube", expected.message)
        }
    }

    private fun blankBitmap(): Bitmap {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val field = unsafeClass.getDeclaredField("theUnsafe")
        field.isAccessible = true
        val unsafe = field.get(null)
        val allocate = unsafeClass.getMethod("allocateInstance", Class::class.java)
        return allocate.invoke(unsafe, Bitmap::class.java) as Bitmap
    }

    private companion object {
        const val LutIdsWarm = "warm_film"
    }
}
