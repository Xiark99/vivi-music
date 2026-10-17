package com.music.vivi.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkLyricsColorTest {
    @Test
    fun artworkColorsStayReadableOnLightAndDarkBackdrops() {
        val accents = listOf(Color.Black, Color.White, Color.Red, Color.Blue, Color.Yellow, Color(0xFF777777))
        for (background in listOf(Color.White, Color.Black, Color(0xFF525252))) {
            for (accent in accents) {
                val result = lyricsContrastColor(accent, background)
                val a = result.luminance() + 0.05f
                val b = background.luminance() + 0.05f
                assertTrue("$accent on $background", maxOf(a, b) / minOf(a, b) >= 4.5f)
            }
        }
    }

    @Test
    fun alreadyReadableArtworkColorsArePreserved() {
        val accent = Color(0xFFFFAA77)
        assertEquals(accent, lyricsContrastColor(accent, Color.Black))
    }
}
