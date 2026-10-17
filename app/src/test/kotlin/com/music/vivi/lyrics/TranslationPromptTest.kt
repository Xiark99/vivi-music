package com.music.vivi.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationPromptTest {
    @Test
    fun blankInstructionsKeepLegacyPromptAndCacheIdentity() {
        for (instructions in listOf("", " \n\t")) {
            assertEquals("original", TranslationPrompt.append("original", instructions, "lyrics"))
            assertEquals("line", TranslationPrompt.cacheMode("line", instructions))
        }
    }

    @Test
    fun instructionChangesCannotReuseAnOldTranslation() {
        val first = TranslationPrompt.cacheMode("line", "自然な日本語")
        assertEquals(first, TranslationPrompt.cacheMode("line", "自然な日本語"))
        assertNotEquals("line", first)
        assertNotEquals(first, TranslationPrompt.cacheMode("line", "直訳してください"))
        assertNotEquals(first, TranslationPrompt.cacheMode("word", "自然な日本語"))
    }

    @Test
    fun instructionsAndMultilineLyricsRemainSeparateWithFormatRequirements() {
        val lyrics = "First line\nSecond line"
        val prompt = TranslationPrompt.append("Return JSON with two lines", "Use natural Japanese", lyrics)
        assertTrue(prompt.startsWith("Return JSON with two lines"))
        assertTrue(prompt.contains("[Additional translation instructions]\nUse natural Japanese"))
        assertTrue(prompt.endsWith("[Lyrics to translate]\n$lyrics\n[End of lyrics]"))
    }
}
