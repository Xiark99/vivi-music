/**
 * vivimusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.music.simpmusic

import com.music.simpmusic.models.LyricsData
import com.music.simpmusic.models.SimpMusicApiResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlin.math.abs

object SimpMusicLyrics {
    private const val BASE_URL = "https://api-lyrics.simpmusic.org/v1/"
    private const val FALLBACK_URL = "https://vivi-yt-music-server.onrender.com/v1/"

    private val client by lazy {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(
                    Json {
                        isLenient = true
                        ignoreUnknownKeys = true
                        explicitNulls = false
                    },
                )
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 15000
                connectTimeoutMillis = 10000
                socketTimeoutMillis = 15000
            }
            defaultRequest {
                header(HttpHeaders.Accept, "application/json")
                header(HttpHeaders.UserAgent, "SimpMusicLyrics/1.0")
                header(HttpHeaders.ContentType, "application/json")
            }
            expectSuccess = false
        }
    }

    suspend fun getLyricsByVideoId(videoId: String): List<LyricsData> {
        if (videoId.isBlank()) return emptyList()

        val primaryAttempt = requestLyrics(BASE_URL, videoId)
        if (primaryAttempt != null) return primaryAttempt

        return requestLyrics(FALLBACK_URL, videoId).orEmpty()
    }

    private suspend fun requestLyrics(baseUrl: String, videoId: String): List<LyricsData>? = runCatching {
        val response = client.get(baseUrl + videoId)
        if (response.status != HttpStatusCode.OK) return@runCatching null

        response.body<SimpMusicApiResponse>()
            .takeIf { it.success }
            ?.data
            .orEmpty()
    }.getOrNull()

    suspend fun getLyrics(
        videoId: String,
        duration: Int = 0,
    ): Result<String> = runCatching {
        val tracks = getLyricsByVideoId(videoId)
        val validTracks = if (duration > 0) {
            tracks.filter { abs((it.duration ?: 0) - duration) <= 10 }
        } else {
            tracks
        }
        val bestMatch = validTracks.minByOrNull { abs((it.duration ?: 0) - duration) }
            ?: throw IllegalStateException("Lyrics unavailable")

        bestMatch.richSyncLyrics?.takeIf { it.isNotBlank() }
            ?: bestMatch.syncedLyrics?.takeIf { it.isNotBlank() }
            ?: bestMatch.plainLyrics?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Lyrics unavailable")
    }

    suspend fun getAllLyrics(
        videoId: String,
        duration: Int = 0,
        callback: (String) -> Unit,
    ) {
        getLyricsByVideoId(videoId)
            .asSequence()
            .filter { duration <= 0 || abs((it.duration ?: 0) - duration) <= 10 }
            .sortedBy { abs((it.duration ?: 0) - duration) }
            .flatMap { track ->
                sequenceOf(track.richSyncLyrics, track.syncedLyrics, track.plainLyrics)
            }
            .filterNotNull()
            .filter { it.isNotBlank() }
            .distinct()
            .take(5)
            .forEach(callback)
    }
}
