/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Adapted for Nami on 2026-10-17 from ArchiveTuneCanvas.kt and CanvasArtworkModel.kt.
 * Changes: bounded positive/negative cache, injectable transport, HTTPS validation,
 * and conversion to the existing Motionartwork model. GPL-3.0; see repository LICENSE.
 */
package com.music.vivi.Motionartwork.betterlyrics

import com.music.vivi.Motionartwork.Motionartwork
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI
import java.util.Locale

object BetterLyricsMotionartworkProvider {
    private val client by lazy {
        HttpClient(OkHttp) {
            install(HttpTimeout) {
                connectTimeoutMillis = 12_000
                requestTimeoutMillis = 18_000
                socketTimeoutMillis = 18_000
            }
            expectSuccess = false
        }
    }
    private val json = Json { ignoreUnknownKeys = true }
    private val repository = BetterLyricsCanvasRepository(fetch = { song, artist, storefront ->
        val response = client.get("https://artwork.boidu.dev/") {
            parameter("s", song)
            parameter("a", artist)
            parameter("storefront", storefront)
        }
        if (response.status == HttpStatusCode.OK) decode(response.bodyAsText()) else null
    })

    suspend fun getBySongArtist(song: String, artist: String, storefront: String = "us"): Motionartwork? =
        repository.get(song, artist, storefront)

    internal fun decode(body: String): Motionartwork {
        val response = json.decodeFromString<BetterLyricsArtwork>(body)
        return Motionartwork(
            name = response.name, artist = response.artist, albumId = response.albumId,
            static = response.static, animated = response.animated, videoUrl = response.videoUrl,
            animatedTall = response.animatedVertical?.takeIf { it.isNotBlank() } ?: response.videoUrlVertical,
        )
    }
}

@Serializable
private data class BetterLyricsArtwork(
    val name: String? = null,
    val artist: String? = null,
    val albumId: String? = null,
    val static: String? = null,
    val animated: String? = null,
    val videoUrl: String? = null,
    val animatedVertical: String? = null,
    val videoUrlVertical: String? = null,
)

internal class BetterLyricsCanvasRepository(
    private val fetch: suspend (String, String, String) -> Motionartwork?,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private data class Query(val song: String, val artist: String, val storefront: String)
    private data class Entry(val artwork: Motionartwork?, val expires: Long)
    private val cache = LinkedHashMap<Query, Entry>()
    private val mutex = Mutex()

    suspend fun get(song: String, artist: String, storefront: String): Motionartwork? {
        if (song.isBlank() || artist.isBlank()) return null
        val region = storefront.lowercase(Locale.ROOT).takeIf { it.matches(Regex("[a-z]{2}")) } ?: "us"
        val query = Query(song.trim(), artist.trim(), region)
        return mutex.withLock {
            cache[query]?.takeIf { it.expires > now() }?.let { return@withLock it.artwork }
            val artwork = try {
                fetch(query.song, query.artist, region)
                    ?.takeIf { it.matchesSongIdentity(query.song, query.artist) }
                    ?.let { result ->
                        val primary = result.animated.safeAnimationUrl()
                        val fallback = result.videoUrl.safeAnimationUrl()
                        val vertical = result.animatedTall.safeAnimationUrl()
                        if (primary == null && fallback == null && vertical == null) null else result.copy(
                            animated = primary ?: fallback ?: vertical,
                            videoUrl = fallback,
                            animatedTall = vertical,
                        )
                    }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
            cache.entries.removeAll { it.value.expires <= now() }
            if (cache.size >= 128) cache.remove(cache.keys.first())
            cache[query] = Entry(artwork, now() + 60_000L)
            artwork
        }
    }
}

private fun String?.safeAnimationUrl(): String? {
    val url = this?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val parsed = runCatching { URI(url) }.getOrNull() ?: return null
    val host = parsed.host?.lowercase(Locale.ROOT) ?: return null
    if (parsed.scheme != "https" || parsed.userInfo != null) return null
    if (listOf("youtube.com", "youtu.be", "googlevideo.com").any { host == it || host.endsWith(".$it") }) return null
    return url
}
