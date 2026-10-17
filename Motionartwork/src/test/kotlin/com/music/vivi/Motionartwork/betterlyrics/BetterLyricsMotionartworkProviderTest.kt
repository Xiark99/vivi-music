package com.music.vivi.Motionartwork.betterlyrics

import com.music.vivi.Motionartwork.Motionartwork
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class BetterLyricsMotionartworkProviderTest {
    private val artwork = Motionartwork(
        name = "Anti-Hero", artist = "Taylor Swift",
        animated = "https://mvod.itunes.apple.com/artwork.m3u8",
    )

    @Test fun matchesUnicodeAndPresentationButNotOtherSongsOrVersions() {
        assertTrue(artwork.matchesSongIdentity("Anti-Hero (Official Audio)", "Taylor Swift"))
        assertFalse(artwork.matchesSongIdentity("Anti-Hero (Live)", "Taylor Swift"))
        assertFalse(artwork.matchesSongIdentity("Anti", "Taylor Swift"))
        assertFalse(artwork.matchesSongIdentity("Anti-Hero", "Someone Else"))
        assertFalse(artwork.matchesSongIdentity("Anti-Hero", "Taylor Swift & Someone Else"))
        assertFalse(artwork.copy(name = "Song (feat. A)").matchesSongIdentity("Song (feat. B)", "Taylor Swift"))
        val japanese = artwork.copy(name = "夜に駆ける", artist = "YOASOBI")
        assertTrue(japanese.matchesSongIdentity("夜に駆ける", "YOASOBI"))
        assertFalse(japanese.matchesSongIdentity("群青", "YOASOBI"))
        assertFalse(artwork.copy(name = null).matchesSongIdentity("Anti-Hero", "Taylor Swift"))
    }

    @Test fun decodesServiceShapeWithoutAlbumNameAndMapsVerticalArtwork() {
        val value = BetterLyricsMotionartworkProvider.decode("""{
            "name":"Anti-Hero", "artist":"Taylor Swift", "albumId":"1651516410",
            "animated":"https://mvod.itunes.apple.com/square.m3u8",
            "animatedVertical":"https://mvod.itunes.apple.com/tall.m3u8",
            "videoUrl":"https://mvod.itunes.apple.com/square.mp4", "unknown":true
        }""")
        assertNull(value.albumName)
        assertEquals("1651516410", value.albumId)
        assertEquals("https://mvod.itunes.apple.com/tall.m3u8", value.animatedTall)
    }

    @Test fun cachesSuccessAndExpiresIt() = runBlocking {
        var time = 1_000L
        var requests = 0
        val repository = BetterLyricsCanvasRepository({ _, _, _ -> requests++; artwork }, { time })
        repeat(2) { assertEquals(artwork, repository.get("Anti-Hero", "Taylor Swift", "us")) }
        assertEquals(1, requests)
        time += 60_001
        repository.get("Anti-Hero", "Taylor Swift", "us")
        assertEquals(2, requests)
    }

    @Test fun cachesMissAndRetriesAfterExpiry() = runBlocking {
        var time = 0L
        var requests = 0
        val repository = BetterLyricsCanvasRepository({ _, _, _ -> requests++; null }, { time })
        repeat(2) { assertNull(repository.get("Missing Song", "Artist", "us")) }
        assertEquals(1, requests)
        time = 60_001
        repository.get("Missing Song", "Artist", "us")
        assertEquals(2, requests)
    }

    @Test fun rejectsWrongSongEvenWhenServiceReturnsAnimation() = runBlocking {
        val repository = BetterLyricsCanvasRepository({ _, _, _ -> artwork.copy(name = "Midnight Rain") })
        assertNull(repository.get("Anti-Hero", "Taylor Swift", "us"))
    }

    @Test fun staticOnlyOrEmptyAnimationIsAMiss() = runBlocking {
        val repository = BetterLyricsCanvasRepository({ _, _, _ -> artwork.copy(animated = "") })
        assertNull(repository.get("Anti-Hero", "Taylor Swift", "us"))
    }

    @Test fun fallsBackToMp4WhenHlsIsMissing() = runBlocking {
        val url = "https://mvod.itunes.apple.com/art.mp4"
        val repository = BetterLyricsCanvasRepository({ _, _, _ -> artwork.copy(animated = null, videoUrl = url) })
        assertEquals(url, repository.get("Anti-Hero", "Taylor Swift", "us")?.preferredAnimationUrl)
    }

    @Test fun verticalOnlyArtworkCanUseTheExistingRenderer() = runBlocking {
        val url = "https://mvod.itunes.apple.com/tall.m3u8"
        val repository = BetterLyricsCanvasRepository({ _, _, _ -> artwork.copy(animated = null, animatedTall = url) })
        assertEquals(url, repository.get("Anti-Hero", "Taylor Swift", "us")?.preferredAnimationUrl)
    }

    @Test fun rejectsInsecureOrYouTubeStreams() = runBlocking {
        for (url in listOf("http://example.org/a.mp4", "https://x.googlevideo.com/a.mp4", "file:///a.mp4")) {
            val repository = BetterLyricsCanvasRepository({ _, _, _ -> artwork.copy(animated = url) })
            assertNull(repository.get("Anti-Hero", "Taylor Swift", "us"))
        }
    }

    @Test fun errorsAreMissesAndCancellationPropagates() = runBlocking {
        val failing = BetterLyricsCanvasRepository({ _, _, _ -> throw IOException("offline") })
        assertNull(failing.get("Anti-Hero", "Taylor Swift", "us"))
        val cancelled = BetterLyricsCanvasRepository({ _, _, _ -> throw CancellationException() })
        try {
            cancelled.get("Anti-Hero", "Taylor Swift", "us")
            fail("Cancellation must not become a cached miss")
        } catch (_: CancellationException) { }
    }

    @Test fun simultaneousConsumersShareOneRequest() = runBlocking {
        var requests = 0
        val repository = BetterLyricsCanvasRepository({ _, _, _ -> requests++; delay(10); artwork })
        coroutineScope {
            List(3) { async { repository.get("Anti-Hero", "Taylor Swift", "us") } }.awaitAll()
        }
        assertEquals(1, requests)
    }

    @Test fun cacheKeysSeparateTracksAndRegionsAndRejectBlankMetadata() = runBlocking {
        val requests = mutableListOf<Triple<String, String, String>>()
        val repository = BetterLyricsCanvasRepository({ s, a, r -> requests.add(Triple(s, a, r)); artwork })
        repository.get("", "Taylor Swift", "us")
        assertTrue(requests.isEmpty())
        assertNotNull(repository.get("Anti-Hero", "Taylor Swift", "JP"))
        assertNull(repository.get("Different Song", "Taylor Swift", "JP"))
        repository.get("Anti-Hero", "Taylor Swift", "invalid")
        assertEquals(listOf("jp", "jp", "us"), requests.map { it.third })
    }

    @Test fun cacheIsBounded() = runBlocking {
        var requests = 0
        val repository = BetterLyricsCanvasRepository({ s, a, _ -> requests++; artwork.copy(name = s, artist = a) })
        for (i in 0..128) repository.get("Song $i", "Artist", "us")
        repository.get("Song 0", "Artist", "us")
        assertEquals(130, requests)
    }
}
