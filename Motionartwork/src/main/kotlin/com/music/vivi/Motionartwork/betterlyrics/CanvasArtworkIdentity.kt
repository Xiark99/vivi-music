/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

// Modified for Nami on 2026-10-17: use Motionartwork, full artists and featured-version identity.
package com.music.vivi.Motionartwork.betterlyrics

import com.music.vivi.Motionartwork.Motionartwork

import java.text.Normalizer
import java.util.Locale

fun Motionartwork.matchesSongIdentity(
    song: String,
    artist: String,
): Boolean {
    val requestedSong = song.toCanvasSongIdentity()
    val requestedArtist = artist.toCanvasIdentity()
    val resolvedSong = name?.toCanvasSongIdentity().orEmpty()
    val resolvedArtist = this.artist?.toCanvasIdentity().orEmpty()

    return requestedSong.isNotEmpty() &&
        requestedArtist.isNotEmpty() &&
        resolvedSong == requestedSong &&
        resolvedArtist == requestedArtist
}

private fun String.toCanvasSongIdentity(): String =
    replace(CanvasPresentationQualifierRegex, "")
        .toCanvasIdentity()

private fun String.toCanvasIdentity(): String =
    Normalizer
        .normalize(this, Normalizer.Form.NFKD)
        .replace(CanvasCombiningMarkRegex, "")
        .lowercase(Locale.ROOT)
        .replace(CanvasIdentitySeparatorRegex, " ")
        .trim()

private val CanvasPresentationQualifierRegex =
    Regex(
        """\s*(?:[-–—:]\s*)?[\[(]?(?:official\s+)?(?:music\s+)?(?:lyric\s+video|video|audio|lyrics?|visualizer|visualiser)[\])]?\s*$""",
        RegexOption.IGNORE_CASE,
    )

private val CanvasCombiningMarkRegex = Regex("\\p{M}+")
private val CanvasIdentitySeparatorRegex = Regex("[^\\p{L}\\p{N}]+")
