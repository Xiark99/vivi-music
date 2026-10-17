package com.music.vivi.ui.player

import com.music.vivi.Motionartwork.Motionartwork
import com.music.vivi.constants.MotionartworkSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class AutoMotionartworkResolverTest {
    private val query = AutoMotionartworkQuery("Song", "Artist", "Album", "us")
    private val artwork = Motionartwork(name = "Song", artist = "Artist", animated = "https://example.org/art.m3u8")

    @Test fun reachesBetterLyricsWhenOnlyItHasArtwork() = runBlocking {
        val calls = mutableListOf<MotionartworkSource>()
        val resolver = AutoMotionartworkResolver(fetch = { source, _ ->
            calls.add(source)
            artwork.takeIf { source == MotionartworkSource.BETTER_LYRICS }
        })
        assertEquals(artwork, resolver.get(query))
        assertEquals(listOf(MotionartworkSource.APPLE_MUSIC, MotionartworkSource.TIDAL,
            MotionartworkSource.VIVIMUSIC, MotionartworkSource.BETTER_LYRICS), calls)
    }

    @Test fun legacySourcesStillWinWithoutRequestingLaterProviders() = runBlocking {
        for (winner in listOf(MotionartworkSource.APPLE_MUSIC, MotionartworkSource.TIDAL, MotionartworkSource.VIVIMUSIC)) {
            val calls = mutableListOf<MotionartworkSource>()
            val resolver = AutoMotionartworkResolver(fetch = { source, _ ->
                calls.add(source)
                artwork.takeIf { source == winner }
            })
            assertEquals(artwork, resolver.get(query))
            assertEquals(winner, calls.last())
            assertFalse(calls.contains(MotionartworkSource.BETTER_LYRICS))
        }
    }

    @Test fun exceptionsDoNotPreventFallback() = runBlocking {
        val resolver = AutoMotionartworkResolver(fetch = { source, _ ->
            if (source == MotionartworkSource.APPLE_MUSIC) throw IOException("offline")
            artwork
        })
        assertEquals(artwork, resolver.get(query))
    }

    @Test fun timeoutContinuesToNextProvider() = runBlocking {
        val resolver = AutoMotionartworkResolver(fetch = { source, _ ->
            if (source == MotionartworkSource.APPLE_MUSIC) delay(60_000)
            artwork
        }, timeoutMillis = 100)
        assertEquals(artwork, resolver.get(query))
    }

    @Test fun swallowedTimeoutDoesNotAcceptAnExpiredCandidate() = runBlocking {
        val calls = mutableListOf<MotionartworkSource>()
        val resolver = AutoMotionartworkResolver(fetch = { source, _ ->
            calls.add(source)
            if (source == MotionartworkSource.APPLE_MUSIC) {
                try { delay(60_000) } catch (_: Exception) { }
            }
            artwork
        }, timeoutMillis = 100)
        assertEquals(artwork, resolver.get(query))
        assertEquals(listOf(MotionartworkSource.APPLE_MUSIC, MotionartworkSource.TIDAL), calls)
    }

    @Test fun invalidAnimationFallsThroughToAnotherSource() = runBlocking {
        val resolver = AutoMotionartworkResolver(fetch = { source, _ ->
            if (source == MotionartworkSource.APPLE_MUSIC) artwork.copy(animated = "not a URL") else artwork
        })
        assertEquals(artwork, resolver.get(query))
    }

    @Test fun allFailuresReturnStaticFallbackAndCacheTheMiss() = runBlocking {
        var calls = 0
        val resolver = AutoMotionartworkResolver(fetch = { _, _ -> calls++; null })
        repeat(2) { assertNull(resolver.get(query)) }
        assertEquals(4, calls)
    }

    @Test fun sharesSuccessfulCacheAndRetriesAfterExpiry() = runBlocking {
        var time = 0L
        var calls = 0
        val resolver = AutoMotionartworkResolver(fetch = { _, _ -> calls++; artwork }, now = { time })
        repeat(2) { assertEquals(artwork, resolver.get(query)) }
        assertEquals(1, calls)
        time = 60_001L
        resolver.get(query)
        assertEquals(2, calls)
    }

    @Test fun concurrentPlayerLayoutsDoNotDuplicateRequests() = runBlocking {
        var calls = 0
        val resolver = AutoMotionartworkResolver(fetch = { _, _ -> calls++; delay(10); artwork })
        coroutineScope { List(3) { async { resolver.get(query) } }.awaitAll() }
        assertEquals(1, calls)
    }

    @Test fun changingSongCancelsWithoutTryingLaterSourcesOrCachingOldResult() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val calls = mutableListOf<MotionartworkSource>()
        var slow = true
        val resolver = AutoMotionartworkResolver(fetch = { source, _ ->
            calls.add(source)
            if (slow) { entered.complete(Unit); delay(60_000) }
            artwork
        })
        val job = launch { resolver.get(query) }
        entered.await()
        job.cancelAndJoin()
        assertEquals(listOf(MotionartworkSource.APPLE_MUSIC), calls)
        slow = false
        assertEquals(artwork, resolver.get(query.copy(song = "Next Song")))
        resolver.get(query)
        assertEquals(3, calls.size)
    }

    @Test fun cacheSeparatesSongArtistAlbumAndStorefront() = runBlocking {
        var calls = 0
        val resolver = AutoMotionartworkResolver(fetch = { _, _ -> calls++; artwork })
        for (q in listOf(query, query.copy(song = "Other"), query.copy(artist = "Other"),
            query.copy(album = "Other"), query.copy(storefront = "jp"))) resolver.get(q)
        assertEquals(5, calls)
    }
}
