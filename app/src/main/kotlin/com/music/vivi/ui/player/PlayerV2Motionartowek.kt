/**
 * vivimusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.music.vivi.ui.player

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.music.vivi.Motionartwork.betterlyrics.BetterLyricsMotionartworkProvider
import com.music.vivi.Motionartwork.Motionartwork
import com.music.vivi.Motionartwork.tidal.TidalMotionartworkProvider
import com.music.vivi.Motionartwork.apple.AppleMusicMotionartworkProvider
import com.music.vivi.Motionartwork.vivimusic.ViviMusicMotionartworkProvider
import com.music.vivi.constants.MotionartworkSource
import com.music.vivi.constants.MotionartworkSourceKey
import com.music.vivi.models.MediaMetadata
import com.music.vivi.utils.rememberEnumPreference
import com.music.vivi.utils.rememberPreference
import com.music.vivi.constants.CanvasThumbnailAnimationKey
import com.music.vivi.constants.CanvasLoadOnlyWifiKey
import com.music.vivi.utils.isWifiConnected
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun PlayerV2Motionartowek(
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    if (mediaMetadata == null) return

    val enableCanvas by rememberPreference(CanvasThumbnailAnimationKey, defaultValue = true)
    if (!enableCanvas) return
    val canvasLoadOnlyWifi by rememberPreference(CanvasLoadOnlyWifiKey, defaultValue = false)
    val context = androidx.compose.ui.platform.LocalContext.current
    if (canvasLoadOnlyWifi && !isWifiConnected(context)) return

    val (motionartworkSource) = rememberEnumPreference(MotionartworkSourceKey, defaultValue = MotionartworkSource.AUTO)
    val albumTitle = mediaMetadata.album?.title
    var canvasArtwork by remember(mediaMetadata.id, albumTitle, motionartworkSource) { mutableStateOf<Motionartwork?>(null) }
    
    val storefront = remember {
        val country = Locale.getDefault().country
        if (country.length == 2) country.lowercase(Locale.ROOT) else "us"
    }

    LaunchedEffect(mediaMetadata.id, albumTitle, motionartworkSource) {
        val cacheKey = "${mediaMetadata.id}:${motionartworkSource.name}"
        MotionartworkPlaybackCache.get(cacheKey)?.takeIf { motionartworkSource !in setOf(MotionartworkSource.BETTER_LYRICS, MotionartworkSource.AUTO) }?.let { cached ->
            canvasArtwork = cached
            return@LaunchedEffect
        }


        val fetched = withContext(Dispatchers.IO) {
            val songTitle = mediaMetadata.title ?: ""
            val artistName = mediaMetadata.artists.joinToString { it.name }.ifBlank { mediaMetadata.artists.firstOrNull()?.name ?: "" }
            val albumName = albumTitle ?: ""

            when (motionartworkSource) {
                MotionartworkSource.BETTER_LYRICS -> {
                    BetterLyricsMotionartworkProvider.getBySongArtist(songTitle, artistName, storefront)
                }
                MotionartworkSource.AUTO -> {
                    autoMotionartworkResolver.get(AutoMotionartworkQuery(songTitle, artistName, albumName, storefront))
                }
                MotionartworkSource.APPLE_MUSIC -> {
                    AppleMusicMotionartworkProvider.getBySongArtist(songTitle, artistName, albumName, storefront)
                        ?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
                }
                MotionartworkSource.VIVIMUSIC -> {
                    ViviMusicMotionartworkProvider.getBySongArtist(songTitle, artistName, albumName)
                        ?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
                }
                MotionartworkSource.TIDAL -> {
                    TidalMotionartworkProvider.getBySongArtist(songTitle, artistName, albumName)
                        ?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
                }
            }
        }

        canvasArtwork = fetched
        if (fetched != null && motionartworkSource !in setOf(MotionartworkSource.BETTER_LYRICS, MotionartworkSource.AUTO)) {
            MotionartworkPlaybackCache.put(cacheKey, fetched)
        }

    }

    canvasArtwork?.let { artwork ->
        MotionartworkPlayer(
            primaryUrl = artwork.preferredAnimationUrl ?: artwork.animatedTall,
            fallbackUrl = artwork.animatedTall,
            isPlaying = isPlaying,
            modifier = modifier
        )
    }
}
