package com.music.vivi.ui.player

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BetterLyricsCanvasTest {
    @Test
    fun solidArtworkKeepsItsColorAndDoesNotMutateTheSource() {
        val source = IntArray(16) { 0xFF123456.toInt() }
        val original = source.copyOf()
        assertArrayEquals(original, blurArtworkPixels(source, 4, 4))
        assertArrayEquals(original, source)
    }

    @Test
    fun blackAndWhiteArtworkBecomesASmoothOpaqueColorMap() {
        val source = IntArray(64) { if (it % 8 < 4) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        val result = blurArtworkPixels(source, 8, 8)
        assertEquals(64, result.size)
        assertTrue(result.all { (it ushr 24) == 255 })
        assertTrue((result[3] and 255) in 1..254)
        assertTrue((result[3] and 255) < (result[4] and 255))
        assertTrue(kotlin.math.abs((result[3] and 255) - (result[4] and 255)) < 100)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidArtworkDimensionsAreRejected() {
        blurArtworkPixels(IntArray(2), 4, 4)
    }

    @Test
    fun invisiblePixelsCannotIntroduceBrightColors() {
        assertArrayEquals(IntArray(16) { 0xFF000000.toInt() },
            blurArtworkPixels(IntArray(16) { 0x00FFFFFF }, 4, 4))
    }
}
