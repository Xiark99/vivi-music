/**
 * Nami (C) 2026. Licensed under GPL-3.0; see LICENSE and git history.
 */
package com.music.vivi.ui.player

import com.music.vivi.Motionartwork.Motionartwork
import com.music.vivi.Motionartwork.apple.AppleMusicMotionartworkProvider
import com.music.vivi.Motionartwork.betterlyrics.BetterLyricsMotionartworkProvider
import com.music.vivi.Motionartwork.tidal.TidalMotionartworkProvider
import com.music.vivi.Motionartwork.vivimusic.ViviMusicMotionartworkProvider
import com.music.vivi.constants.MotionartworkSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URI

internal data class AutoMotionartworkQuery(
    val song: String,
    val artist: String,
    val album: String,
    val storefront: String,
)

internal val autoMotionartworkResolver = AutoMotionartworkResolver()

internal class AutoMotionartworkResolver(
    private val fetch: suspend (MotionartworkSource, AutoMotionartworkQuery) -> Motionartwork? = ::fetchAutoArtwork,
    private val timeoutMillis: Long = 8_000L,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private data class Entry(val artwork: Motionartwork?, val expires: Long)
    private val cache = LinkedHashMap<AutoMotionartworkQuery, Entry>()
    private val mutex = Mutex()
    private val sources = listOf(
        MotionartworkSource.APPLE_MUSIC,
        MotionartworkSource.TIDAL,
        MotionartworkSource.VIVIMUSIC,
        MotionartworkSource.BETTER_LYRICS,
    )

    suspend fun get(query: AutoMotionartworkQuery): Motionartwork? {
        if (query.song.isBlank() || query.artist.isBlank()) return null
        return mutex.withLock {
            currentCoroutineContext().ensureActive()
            cache[query]?.takeIf { it.expires > now() }?.let { return@withLock it.artwork }
            var result: Motionartwork? = null
            for (source in sources) {
                currentCoroutineContext().ensureActive()
                val artwork = try {
                    withTimeoutOrNull(timeoutMillis) {
                        val candidate = fetch(source, query)
                        // Some legacy providers catch Exception, including coroutine cancellation.
                        currentCoroutineContext().ensureActive()
                        candidate?.takeIf { it.hasAnimationUrl() }
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    null
                }
                currentCoroutineContext().ensureActive()
                if (artwork != null) {
                    result = artwork
                    break
                }
            }
            cache.entries.removeAll { it.value.expires <= now() }
            if (cache.size >= 128) cache.remove(cache.keys.first())
            cache[query] = Entry(result, now() + 60_000L)
            result
        }
    }
}

private suspend fun fetchAutoArtwork(source: MotionartworkSource, query: AutoMotionartworkQuery): Motionartwork? {
    if (source == MotionartworkSource.BETTER_LYRICS) {
        // Better Lyrics validates song and full artist identity itself; its API has no albumName.
        return BetterLyricsMotionartworkProvider.getBySongArtist(query.song, query.artist, query.storefront)
    }
    val artwork = when (source) {
        MotionartworkSource.APPLE_MUSIC -> AppleMusicMotionartworkProvider.getBySongArtist(
            query.song, query.artist, query.album, query.storefront,
        )
        MotionartworkSource.TIDAL -> TidalMotionartworkProvider.getBySongArtist(query.song, query.artist, query.album)
        MotionartworkSource.VIVIMUSIC -> ViviMusicMotionartworkProvider.getBySongArtist(query.song, query.artist, query.album)
        else -> null
    }
    return artwork?.takeIf { validateMotionartworkMatch(it, query.song, query.artist, query.album) }
}

private fun Motionartwork.hasAnimationUrl(): Boolean {
    val url = preferredAnimationUrl?.takeIf { it.isNotBlank() } ?: return false
    val uri = runCatching { URI(url) }.getOrNull() ?: return false
    return uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null
}
