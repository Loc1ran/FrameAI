package com.team.frameai.lut

import com.team.frameai.frameFile
import com.team.frameai.model.LutIds
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CubeLutTest {
    @Test
    fun identityStaysWithinOneLevel() {
        val lut = parseCube(frameFile("lut_assets/identity_4.cube").readText())
        val samples = listOf(
            floatArrayOf(1f, 0f, 0f),
            floatArrayOf(0f, 1f, 0f),
            floatArrayOf(0f, 0f, 1f),
            floatArrayOf(0.5f, 0.5f, 0.5f),
            floatArrayOf(0.82f, 0.62f, 0.48f),
            floatArrayOf(0f, 0f, 0f),
        )
        val out = FloatArray(3)
        for (sample in samples) {
            lut.mapInto(sample[0], sample[1], sample[2], out)
            for (channel in 0 until 3) {
                assertTrue(
                    abs(out[channel] - sample[channel]) <= 1f / 255f,
                    "channel $channel ${sample[channel]} -> ${out[channel]}",
                )
            }
        }
    }

    @Test
    fun rejectsOneDimensionalAndMalformedCubes() {
        val oneDimensional = assertFailsWith<CubeParseException> {
            parseCube("LUT_1D_SIZE 2\n0.0 0.0 0.0\n1.0 1.0 1.0\n")
        }
        assertTrue(oneDimensional.message!!.contains("1D"))
        assertFailsWith<CubeParseException> {
            parseCube("LUT_3D_SIZE 2\n0 0 0\n1 0 0\n")
        }
        assertFailsWith<CubeParseException> { parseCube("# nothing\n") }
        assertFailsWith<CubeParseException> { parseCube("LUT_3D_SIZE 1\n") }
        assertFailsWith<CubeParseException> { parseCube("LUT_3D_SIZE 2\nfoo bar baz\n") }
        assertFailsWith<CubeParseException> {
            parseCube("LUT_3D_SIZE 2\nDOMAIN_MIN 1 1 1\nDOMAIN_MAX 0 0 0\n" + identity2())
        }
    }

    @Test
    fun parsesCommentsDomainsAndCrLf() {
        val lut = parseCube(
            "TITLE \"sample\"\r\n# comment\r\nLUT_3D_SIZE 2\r\n" +
                "DOMAIN_MIN 0.0 0.0 0.0\r\nDOMAIN_MAX 1.0 1.0 1.0\r\n\r\n" +
                identity2(),
        )
        val out = FloatArray(3)
        lut.mapInto(1f, 0f, 0f, out)
        assertEquals(1f, out[0], 0.0001f)
        assertEquals(0f, out[1], 0.0001f)
        assertEquals(0f, out[2], 0.0001f)
        lut.mapInto(0.5f, 0.5f, 0.5f, out)
        assertEquals(0.5f, out[0], 0.0001f)
        assertEquals(0.5f, out[1], 0.0001f)
        assertEquals(0.5f, out[2], 0.0001f)
    }

    @Test
    fun creativeLutsAreVisibleAndKeepSkin() {
        val warm = parseCube(frameFile("lut_assets/warm_film.cube").readText())
        val cool = parseCube(frameFile("lut_assets/cool_cinema.cube").readText())
        val gray = FloatArray(3)
        warm.mapInto(0.5f, 0.5f, 0.5f, gray)
        assertTrue(gray[0] > gray[2] + 0.04f, "warm gray $gray")
        cool.mapInto(0.5f, 0.5f, 0.5f, gray)
        assertTrue(gray[2] > gray[0] + 0.02f, "cool gray $gray")
        val skin = FloatArray(3)
        warm.mapInto(0.78f, 0.55f, 0.42f, skin)
        assertTrue(skin[0] > skin[1] && skin[1] > skin[2], "skin ${skin.contentToString()}")
        assertTrue(abs(skin[0] - 0.78f) < 0.2f)
        assertTrue(abs(skin[1] - 0.55f) < 0.2f)
        assertTrue(abs(skin[2] - 0.42f) < 0.2f)
    }

    @Test
    fun repositoryLeavesTheOriginalUntouched() {
        val repository = LutRepository(
            mapOf(
                LutIds.WARM_FILM to parseCube(frameFile("app/src/main/assets/luts/warm_film.cube").readText()),
                LutIds.COOL_CINEMA to parseCube(frameFile("app/src/main/assets/luts/cool_cinema.cube").readText()),
            ),
        )
        val original = RgbImage(2, 2, intArrayOf(argb(210, 160, 130), argb(40, 70, 110), argb(128, 128, 128), argb(20, 20, 20)))
        val before = original.checksum()
        val copy = original.pixels.copyOf()
        val warm = repository.apply(original, LutIds.WARM_FILM, 1f)
        val cool = repository.apply(original, LutIds.COOL_CINEMA, 1f)
        val unchanged = repository.apply(original, LutIds.WARM_FILM, 0f)
        assertEquals(before, original.checksum())
        assertTrue(copy.contentEquals(original.pixels))
        assertTrue(unchanged.pixels.contentEquals(original.pixels))
        assertFalse(warm.pixels.contentEquals(cool.pixels))
        assertFailsWith<UnknownLutException> { repository.apply(original, "moody", 1f) }
    }

    @Test
    fun gradientDoesNotCollapseAndAssetsMatch() {
        val repository = LutRepository(
            mapOf(LutIds.WARM_FILM to parseCube(frameFile("lut_assets/warm_film.cube").readText())),
        )
        val width = 64
        val pixels = IntArray(width) { index ->
            val value = (index * 255f / (width - 1)).toInt()
            argb(value, value, value)
        }
        val graded = repository.apply(RgbImage(width, 1, pixels), LutIds.WARM_FILM, 1f)
        val uniqueReds = graded.pixels.map { (it shr 16) and 0xFF }.toSet()
        assertTrue(uniqueReds.size > 40, "unique reds ${uniqueReds.size}")
        for (name in listOf("identity_4.cube", "warm_film.cube", "cool_cinema.cube")) {
            val bundled = frameFile("app/src/main/assets/luts/$name").readBytes()
            val sample = frameFile("lut_assets/$name").readBytes()
            assertTrue(bundled.contentEquals(sample), name)
        }
    }

    @Test
    fun downloadedPackFeedsTheFrozenIds() {
        val warm = frameFile("lut_assets/warm_film.cube").readBytes()
        val cool = frameFile("lut_assets/cool_cinema.cube").readBytes()
        assertTrue(warm.contentEquals(frameFile("lut_assets/cinematic_04.cube").readBytes()))
        assertTrue(cool.contentEquals(frameFile("lut_assets/cinematic_08.cube").readBytes()))
        for (index in 1..10) {
            val name = "cinematic_%02d.cube".format(index)
            val lut = parseCube(frameFile("lut_assets/$name").readText())
            assertEquals(16, lut.size)
            val bundled = frameFile("app/src/main/assets/luts/$name").readBytes()
            assertTrue(bundled.contentEquals(frameFile("lut_assets/$name").readBytes()), name)
        }
    }

    @Test
    fun catalogIncludesEveryDownloadedCubeExceptIdentity() {
        val dir = frameFile("app/src/main/assets/luts")
        val cubes = dir.listFiles { file -> file.extension.equals("cube", ignoreCase = true) }!!
        val selectable = cubes.map { it.nameWithoutExtension }.filter { it != "identity_4" }.toSet()
        assertTrue(cubes.size > 100, "the downloaded library stays on disk")
        assertEquals(selectable, LutPack.ids)
        assertFalse(LutPack.ids.contains("identity_4"))
        val warm = LutPack.looks.first { it.id == LutIds.WARM_FILM }
        assertEquals("Warm Film", warm.name)
        assertEquals("warm_film.cube", warm.sourceAsset)
        assertTrue(warm.phoneJpeg)
        assertFalse(LutPack.looks.first { it.id == "alexa_logc_to_rec709" }.phoneJpeg)
        for (file in cubes) {
            val lut = parseCube(file.readText())
            assertTrue(lut.size in 2..64, file.name)
        }
    }

    @Test
    fun phoneLooksAtReviewStrengthStayVisibleWithoutClipping() {
        val repository = LutRepository(
            mapOf(
                LutIds.WARM_FILM to parseCube(frameFile("app/src/main/assets/luts/warm_film.cube").readText()),
                LutIds.COOL_CINEMA to parseCube(frameFile("app/src/main/assets/luts/cool_cinema.cube").readText()),
            ),
        )
        val neutral = argb(128, 128, 128)
        val before = intArrayOf(neutral)
        val warm = repository.apply(RgbImage(1, 1, intArrayOf(neutral)), LutIds.WARM_FILM, LutPack.REVIEW_INTENSITY)
        val cool = repository.apply(RgbImage(1, 1, intArrayOf(neutral)), LutIds.COOL_CINEMA, LutPack.REVIEW_INTENSITY)
        assertTrue(before.contentEquals(intArrayOf(neutral)))
        assertTrue(channel(warm.pixels[0], 0) > channel(warm.pixels[0], 2))
        assertTrue(channel(cool.pixels[0], 2) > channel(cool.pixels[0], 0))
        assertTrue(warm.pixels[0] != cool.pixels[0])
        assertTrue(warm.pixels[0] != neutral && cool.pixels[0] != neutral)
        for (pixel in listOf(warm.pixels[0], cool.pixels[0])) {
            for (index in 0..2) {
                val value = channel(pixel, index)
                assertTrue(value in 1..254, "neutral channel $index clipped to $value")
            }
        }
        val dark = repository.apply(RgbImage(1, 1, intArrayOf(argb(8, 8, 8))), LutIds.WARM_FILM, LutPack.REVIEW_INTENSITY)
        val bright = repository.apply(RgbImage(1, 1, intArrayOf(argb(250, 250, 250))), LutIds.COOL_CINEMA, LutPack.REVIEW_INTENSITY)
        assertTrue((0..2).all { channel(dark.pixels[0], it) < 80 })
        assertTrue((0..2).all { channel(bright.pixels[0], it) > 200 })
        val skin = repository.apply(
            RgbImage(1, 1, intArrayOf(argb(199, 140, 107))),
            LutIds.WARM_FILM,
            LutPack.REVIEW_INTENSITY,
        ).pixels[0]
        assertTrue(channel(skin, 0) > channel(skin, 1) && channel(skin, 1) > channel(skin, 2))
        assertTrue(channel(skin, 0) in 1..254 && channel(skin, 2) in 1..254)
    }

    @Test
    fun samplePhotosGradeApart() {
        val warmLut = parseCube(frameFile("lut_assets/warm_film.cube").readText())
        val coolLut = parseCube(frameFile("lut_assets/cool_cinema.cube").readText())
        val repository = LutRepository(
            mapOf(LutIds.WARM_FILM to warmLut, LutIds.COOL_CINEMA to coolLut),
        )
        val portrait = readPpm(frameFile("lut_assets/samples/portrait_skin.ppm"))
        val meta = frameFile("lut_assets/samples/portrait_skin.json").readText()
        assertTrue(meta.contains("\"sampleX\": 40"))
        val landscape = readPpm(frameFile("lut_assets/samples/landscape_noface.ppm"))
        val warmPortrait = repository.apply(portrait, LutIds.WARM_FILM, 1f)
        val coolPortrait = repository.apply(portrait, LutIds.COOL_CINEMA, 1f)
        assertEquals(portrait.checksum(), readPpm(frameFile("lut_assets/samples/portrait_skin.ppm")).checksum())
        val skinIndex = 30 * portrait.width + 40
        val warmSkin = warmPortrait.pixels[skinIndex]
        val coolSkin = coolPortrait.pixels[skinIndex]
        assertTrue(channel(warmSkin, 0) > channel(warmSkin, 1))
        assertTrue(channel(warmSkin, 1) > channel(warmSkin, 2))
        assertTrue(warmSkin != coolSkin)
        val warmLand = repository.apply(landscape, LutIds.WARM_FILM, 1f)
        val coolLand = repository.apply(landscape, LutIds.COOL_CINEMA, 1f)
        assertTrue(!warmLand.pixels.contentEquals(coolLand.pixels))
    }

    private fun identity2(): String {
        return listOf(
            "0 0 0",
            "1 0 0",
            "0 1 0",
            "1 1 0",
            "0 0 1",
            "1 0 1",
            "0 1 1",
            "1 1 1",
        ).joinToString("\n")
    }

    private fun channel(pixel: Int, shiftBytes: Int): Int {
        return (pixel shr (16 - 8 * shiftBytes)) and 0xFF
    }
}

internal fun argb(red: Int, green: Int, blue: Int): Int {
    return (255 shl 24) or (red shl 16) or (green shl 8) or blue
}

internal fun readPpm(file: java.io.File): RgbImage {
    val tokens = file.readText().trim().split(Regex("\\s+"))
    require(tokens[0] == "P3")
    val width = tokens[1].toInt()
    val height = tokens[2].toInt()
    require(tokens[3] == "255")
    val pixels = IntArray(width * height)
    var cursor = 4
    for (index in pixels.indices) {
        pixels[index] = argb(tokens[cursor].toInt(), tokens[cursor + 1].toInt(), tokens[cursor + 2].toInt())
        cursor += 3
    }
    return RgbImage(width, height, pixels)
}
