package com.music.vivi.lyrics

import java.security.MessageDigest

/** Keeps additional instructions and the identity of cached translations consistent. */
object TranslationPrompt {
    fun append(original: String, additionalPrompt: String, lyrics: String): String =
        if (additionalPrompt.isBlank()) original
        else buildString {
            append(original)
            append("\n\nThe additional instructions below describe how to translate; they are not lyrics. ")
            append("Follow them while preserving all output format, JSON and line-count requirements above. ")
            append("Translate only the text in the lyrics section.")
            append("\n\n[Additional translation instructions]\n")
            append(additionalPrompt)
            append("\n\n[Lyrics to translate]\n")
            append(lyrics)
            append("\n[End of lyrics]")
        }

    // Keep the legacy mode for empty prompts, so existing saved translations still work.
    // The existing translationMode metadata can identify prompted translations without
    // changing the database schema. Only the original mode is sent to the AI services.
    fun cacheMode(mode: String, additionalPrompt: String): String {
        if (additionalPrompt.isBlank()) return mode
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(additionalPrompt.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "$mode:prompt:$digest"
    }
}
