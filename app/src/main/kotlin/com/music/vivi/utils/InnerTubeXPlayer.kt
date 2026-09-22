package com.music.vivi.utils

import android.content.Context
import android.net.ConnectivityManager
import com.music.innertube.models.Thumbnail
import com.music.innertube.models.response.PlayerResponse
import com.metrolist.innertubex.models.response.PlayerResponse as InnerTubeXResponse
import com.metrolist.innertubex.InnerTube
import com.metrolist.innertubex.InnerTubeLogLevel
import com.metrolist.innertubex.InnerTubeLogger
import com.metrolist.innertubex.models.YouTubeLocale
import com.metrolist.innertubex.cipher.PlayerConfigRepository
import com.metrolist.innertubex.cipher.RemotePlayerConfigStore
import com.metrolist.innertubex.cipher.YouTubeCipherService
import com.metrolist.innertubex.extraction.AudioQuality as InnerTubeXAudioQuality
import com.metrolist.innertubex.extraction.ContentHints
import com.metrolist.innertubex.extraction.ExtractedStream
import com.metrolist.innertubex.extraction.InnerTubeExtractor
import com.metrolist.innertubex.extraction.PoTokenResult
import com.metrolist.innertubex.extraction.StreamResolveException
import com.metrolist.innertubex.extraction.TokenProvider
import com.metrolist.innertubex.extraction.TokenProviderCapabilities
import com.metrolist.innertubex.extraction.YtConfigParserImpl
import com.metrolist.innertubex.extraction.generateClientPlaybackNonce
import com.metrolist.innertubex.extraction.strategy.PoTokenProviderKind
import com.music.vivi.constants.AudioQuality
import com.music.vivi.utils.potoken.PoTokenGenerator
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import timber.log.Timber
import kotlin.time.Clock

/** The sole stream extraction entry point for the Android app using innertubex back-end. */
object InnerTubeXPlayer {
    private const val TAG = "InnerTubeXPlayer"
    private const val WEB_REMIX_FAILURE_TTL_MS = 5 * 60 * 1000L
    private const val DEFAULT_STREAM_TTL_SECONDS = 5 * 60
    private const val POTOKEN_WARMUP_VIDEO_ID = "jNQXAC9IVRw"

    @Volatile
    private var applicationContext: Context? = null

    @Volatile
    private var currentBundle: ExtractionBundle? = null
    private val bundleMutex = Mutex()
    private val webRemixFailures = java.util.concurrent.ConcurrentHashMap<String, Long>()

    @Volatile
    var disabledStreamClients: Set<String> = emptySet()

    // Isolated Streaming HttpClient & InnerTube instance
    private val httpClient = HttpClient(OkHttp) {
        expectSuccess = false
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                    encodeDefaults = true
                }
            )
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 30_000
            socketTimeoutMillis = 60_000
        }
    }
    val innerTubeX = InnerTube(httpClient)

    @Synchronized
    fun initialize(context: Context) {
        if (applicationContext == null) applicationContext = context.applicationContext
    }

    fun setLocale(locale: YouTubeLocale) {
        if (innerTubeX.locale != locale) {
            innerTubeX.locale = locale
        }
    }

    suspend fun prewarm() {
        bundle().extractor.prewarm()
        tokenProvider.prewarm()
    }

    suspend fun playerResponseForPlayback(
        videoId: String,
        playlistId: String? = null,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
        contentHints: ContentHints = ContentHints(),
        allowBoundedRange: Boolean = true,
        wantVideo: Boolean = false,
    ): Result<PlaybackData> =
        try {
            val hints =
                contentHints.copy(
                    isUploaded =
                        contentHints.isUploaded == true ||
                            playlistId == "MLPT" ||
                            playlistId?.contains("MLPT") == true,
                ).withStreamCapabilities(
                    allowHls = false,
                    allowSabr = false,
                    allowBoundedRange = allowBoundedRange,
                ).copy(
                    wantVideo = wantVideo,
                    maxVideoHeight = if (wantVideo) 360 else null,
                )
            val excludedClients =
                buildSet {
                    addAll(disabledStreamClients)
                    if (hasRecentWebRemixFailure(videoId)) add("WEB_REMIX")
                }
            val stream =
                requireNotNull(
                    bundle().extractor.extract(
                        videoId = videoId,
                        hints = hints,
                        excludedClients = excludedClients,
                        audioQuality = audioQuality.toInnerTubeX(connectivityManager),
                        clientPlaybackNonce = generateClientPlaybackNonce(),
                    ),
                ) { "InnerTubeX returned no playable stream" }
            check(stream.sabrBootstrap == null) { "SABR is not supported by this playback engine" }
            Result.success(stream.toPlaybackData())
        } catch (error: CancellationException) {
            throw error
        } catch (error: StreamResolveException) {
            val cause = error.cause
            Result.failure(
                if (error.reason == StreamResolveException.Reason.NETWORK && cause != null) {
                    cause
                } else {
                    error
                },
            )
        } catch (error: Exception) {
            Result.failure(error)
        }

    fun markWebRemixFailed(videoId: String) {
        webRemixFailures[videoId] = System.currentTimeMillis()
    }

    data class MuxedRoute(val musicVideoType: String?, val stream: VideoStream?)

    /** A resolved adaptive stream plus the byte ranges required for a DASH SegmentBase. */
    data class DashStream(
        val streamUrl: String,
        val mimeType: String,
        val codecs: String?,
        val width: Int?,
        val height: Int?,
        val bitrate: Int,
        val itag: Int,
        val contentLength: Long?,
        val approxDurationMs: Long?,
        val audioChannels: Int?,
        val audioSampleRate: Int?,
        val initRange: ByteRange,
        val indexRange: ByteRange,
        val headers: Map<String, String>,
    )

    data class ByteRange(val start: Long, val end: Long) {
        val length: Long get() = end - start + 1L
    }

    data class DashRoute(
        val musicVideoType: String,
        val video: DashStream,
        val audio: DashStream,
        val durationMs: Long,
    )

    /** Carries classification even when VISIONOS has no usable DASH formats. */
    data class DashResolution(
        val musicVideoType: String?,
        val route: DashRoute?,
        /** Diagnostic-only reason when a confirmed video has no usable DASH route. */
        val noDashReason: String? = null,
    )

    /** Separate experiment: never changes extract()'s audio format selection. */
    suspend fun resolveMuxed360(videoId: String, knownType: String?): MuxedRoute {
        var type = knownType
        if (type == "MUSIC_VIDEO_TYPE_ATV") return MuxedRoute(type, null)
        if (type !in ACTUAL_VIDEO_TYPES) {
            type = YTPlayerUtils.playerResponseForMetadata(videoId, null).getOrNull()
                ?.videoDetails?.musicVideoType
        }
        if (type !in ACTUAL_VIDEO_TYPES) return MuxedRoute(type, null)

        val extraction = bundle()
        val config = extraction.configParser.fetchConfig(videoId, false)
        val visitor = config.visitorData ?: innerTubeX.visitorData
        val token = visitor?.let { tokenProvider.getPoToken(videoId, it, innerTubeX.cookie) }
        // Preserve the existing progressive muxed fast path and its response inventory.
        val client = com.metrolist.innertubex.models.YouTubeClient.WEB
        val response = innerTubeX.player(
            client = client, videoId = videoId, signatureTimestamp = config.signatureTimestamp,
            poToken = token?.playerRequestToken, requestVisitorData = token?.visitorData ?: visitor,
            encryptedHostFlags = config.encryptedHostFlags,
        ).body<com.metrolist.innertubex.models.response.PlayerResponse>()
        val responseMusicVideoType = response.videoDetails?.musicVideoType ?: type
        if (responseMusicVideoType in ACTUAL_VIDEO_TYPES) {
            logMuxedFormatDiagnostics(
                videoId = videoId,
                musicVideoType = responseMusicVideoType,
                clientName = client.clientName,
                formats = response.streamingData?.formats.orEmpty(),
            )
        }
        // A response explicitly identifying an Art Track overrides queue metadata.
        if (response.videoDetails?.musicVideoType == "MUSIC_VIDEO_TYPE_ATV") {
            return MuxedRoute("MUSIC_VIDEO_TYPE_ATV", null)
        }
        // Carry the response classification forward when no progressive stream exists. Without
        // this, a queue item whose metadata was unknown could not enter the safe DASH route even
        // though this response had already proved it is an actual music video.
        if (responseMusicVideoType !in ACTUAL_VIDEO_TYPES) {
            return MuxedRoute(responseMusicVideoType, null)
        }
        val candidates = response.streamingData?.formats.orEmpty().filter { format ->
            format.is360pClass() &&
                format.mimeType.startsWith("video/") &&
                format.mimeType.contains("avc1", ignoreCase = true) &&
                format.mimeType.contains("mp4a", ignoreCase = true) &&
                (format.audioChannels ?: 0) > 0
        }.sortedWith(compareBy({ it.bitrate }, { it.itag }))
        val selected = candidates.firstOrNull() ?: return MuxedRoute(responseMusicVideoType, null)
        val resolved = try {
            extraction.cipherService.processFormats(config.playerUrl, listOf(selected)).firstOrNull()
        } catch (error: Exception) {
            Timber.tag(TAG).w(
                "[VideoPlayback][muxedDiag] mediaId=$videoId rejected itag=${selected.itag} " +
                    "reason=CIPHER_RESOLVE_FAILED errorType=${error.javaClass.simpleName}",
            )
            throw error
        } ?: run {
            Timber.tag(TAG).w(
                "[VideoPlayback][muxedDiag] mediaId=$videoId rejected itag=${selected.itag} " +
                    "reason=CIPHER_RESOLVE_FAILED errorType=ResolvedFormatMissing",
            )
            return MuxedRoute(responseMusicVideoType, null)
        }
        val url = resolved.url ?: run {
            Timber.tag(TAG).w(
                "[VideoPlayback][muxedDiag] mediaId=$videoId rejected itag=${selected.itag} " +
                    "reason=CIPHER_RESOLVE_FAILED errorType=ResolvedUrlMissing",
            )
            return MuxedRoute(responseMusicVideoType, null)
        }
        val uri = android.net.Uri.parse(url)
        if (uri.scheme != "https") return MuxedRoute(responseMusicVideoType, null)
        val finalUri = uri.buildUpon().apply {
            token?.streamingDataToken?.let { if (uri.getQueryParameter("pot") == null) appendQueryParameter("pot", it) }
            if (uri.getQueryParameter("cpn") == null) appendQueryParameter("cpn", generateClientPlaybackNonce())
        }.build()
        val codecs = resolved.mimeType.substringAfter("codecs=", "").trim('"', ' ')
        return MuxedRoute(responseMusicVideoType, VideoStream(
            streamUrl = finalUri.toString(), mimeType = resolved.mimeType, codecs = codecs,
            width = requireNotNull(resolved.width), height = requireNotNull(resolved.height),
            bitrate = resolved.bitrate, itag = resolved.itag, contentLength = resolved.contentLength,
            headers = mapOf("User-Agent" to client.userAgent, "Origin" to "https://www.youtube.com"),
        ))
    }

    /**
     * Resolves one audio and one 360p video adaptive format into a single-period sideloaded DASH
     * route. Its classification lets callers use WEB only as a confirmed-video fallback.
     */
    suspend fun resolveDash360(videoId: String, knownType: String?): DashResolution {
        var type = knownType
        if (type == "MUSIC_VIDEO_TYPE_ATV") return DashResolution(type, null, "KNOWN_ATV")
        if (type !in ACTUAL_VIDEO_TYPES) {
            type = YTPlayerUtils.playerResponseForMetadata(videoId, null).getOrNull()
                ?.videoDetails?.musicVideoType
        }
        if (type !in ACTUAL_VIDEO_TYPES) return DashResolution(type, null, "TYPE_NOT_CONFIRMED")

        val extraction = bundle()
        val config = extraction.configParser.fetchConfig(videoId, false)
        val visitor = config.visitorData ?: innerTubeX.visitorData
        // The normal extractor selected VISIONOS for the same videos in device logs and received
        // 17-19 adaptive formats. WEB often returns no formats at all, so it cannot bootstrap
        // a SegmentBase manifest for those videos. That extractor selected the nopo profile, so
        // do not attach a WEB PoToken to this VISIONOS request.
        val client = com.metrolist.innertubex.models.YouTubeClient.VISIONOS
        val response = innerTubeX.player(
            client = client,
            videoId = videoId,
            signatureTimestamp = config.signatureTimestamp,
            poToken = null,
            requestVisitorData = visitor,
            encryptedHostFlags = config.encryptedHostFlags,
        ).body<JsonObject>()
        val responseType = response["videoDetails"]?.jsonObject?.string("musicVideoType") ?: type
        if (responseType == "MUSIC_VIDEO_TYPE_ATV" || responseType !in ACTUAL_VIDEO_TYPES) {
            return DashResolution(responseType, null, "VISIONOS_RESPONSE_NOT_VIDEO")
        }
        val actualMusicVideoType = requireNotNull(responseType)
        val adaptiveFormats = response["streamingData"]?.jsonObject
            ?.get("adaptiveFormats")?.jsonArray.orEmpty().mapNotNull { it as? JsonObject }
            .mapNotNull(::dashRawFormat)
        val video = selectDashVideo(adaptiveFormats)
        val audio = selectDashAudio(adaptiveFormats)
        val videoFormats = adaptiveFormats.filter { format ->
            format.mimeType.startsWith("video/", ignoreCase = true) ||
                format.width != null || format.height != null
        }
        val video360WithRanges = adaptiveFormats.count { format ->
            format.is360pClass() &&
                format.mimeType.startsWith("video/", ignoreCase = true) &&
                !format.hasAudioCodec && format.hasRequiredDashRanges
        }
        val audioWithRanges = adaptiveFormats.count { format ->
            format.mimeType.startsWith("audio/", ignoreCase = true) && format.hasRequiredDashRanges
        }
        val selectedVideoItag = video?.itag?.toString() ?: "none"
        val selectedAudioItag = audio?.itag?.toString() ?: "none"
        Timber.tag(TAG).i(
            "[VideoPlayback][dash] mediaId=$videoId musicVideoType=$actualMusicVideoType " +
                "client=${client.clientName} diagVersion=vertical2 adaptiveFormats=${adaptiveFormats.size} " +
                "video360WithRanges=$video360WithRanges audioWithRanges=$audioWithRanges " +
                "selectedVideo=$selectedVideoItag selectedAudio=$selectedAudioItag",
        )
        logDashVideoFormatDiagnostics(videoId, actualMusicVideoType, client.clientName, videoFormats)
        if (video == null || audio == null) {
            return DashResolution(actualMusicVideoType, null, "MISSING_360_VIDEO_OR_AUDIO_CANDIDATE")
        }
        val resolvedVideo = try {
            resolveDashStream(extraction, config.playerUrl, null, client, video)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        } ?: return DashResolution(actualMusicVideoType, null, "VIDEO_CIPHER_OR_URL_RESOLVE_FAILED")
        val resolvedAudio = try {
            resolveDashStream(extraction, config.playerUrl, null, client, audio)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        } ?: return DashResolution(actualMusicVideoType, null, "AUDIO_CIPHER_OR_URL_RESOLVE_FAILED")
        val durationMs = (video.approxDurationMs ?: audio.approxDurationMs)?.takeIf { it > 0L }
            ?: return DashResolution(actualMusicVideoType, null, "MISSING_STREAM_DURATION")
        return DashResolution(
            musicVideoType = actualMusicVideoType,
            route = DashRoute(
                musicVideoType = actualMusicVideoType,
                video = resolvedVideo,
                audio = resolvedAudio,
                durationMs = durationMs,
            ),
        )
    }

    private val ACTUAL_VIDEO_TYPES = setOf("MUSIC_VIDEO_TYPE_OMV", "MUSIC_VIDEO_TYPE_UGC")

    private data class DashRawFormat(
        val itag: Int,
        val url: String?,
        val mimeType: String,
        val bitrate: Int,
        val width: Int?,
        val height: Int?,
        val contentLength: Long?,
        val quality: String?,
        val qualityLabel: String?,
        val audioQuality: String?,
        val approxDurationMs: Long?,
        val audioSampleRate: Int?,
        val audioChannels: Int?,
        val lastModified: String?,
        val signatureCipher: String?,
        val cipher: String?,
        val initRange: ByteRange?,
        val indexRange: ByteRange?,
    )

    private fun dashRawFormat(element: JsonObject): DashRawFormat? {
        val itag = element.int("itag") ?: return null
        val mimeType = element.string("mimeType") ?: return null
        val bitrate = element.int("bitrate") ?: return null
        return DashRawFormat(
            itag = itag,
            url = element.string("url"),
            mimeType = mimeType,
            bitrate = bitrate,
            width = element.int("width"),
            height = element.int("height"),
            contentLength = element.long("contentLength"),
            quality = element.string("quality"),
            qualityLabel = element.string("qualityLabel"),
            audioQuality = element.string("audioQuality"),
            approxDurationMs = element.long("approxDurationMs"),
            audioSampleRate = element.int("audioSampleRate"),
            audioChannels = element.int("audioChannels"),
            lastModified = element.string("lastModified"),
            signatureCipher = element.string("signatureCipher"),
            cipher = element.string("cipher"),
            initRange = element.byteRange("initRange"),
            indexRange = element.byteRange("indexRange"),
        )
    }

    private fun selectDashVideo(formats: List<DashRawFormat>): DashRawFormat? {
        val videoOnly360 = formats.filter { format ->
            format.is360pClass() &&
                format.mimeType.startsWith("video/", ignoreCase = true) &&
                !format.hasAudioCodec && format.hasRequiredDashRanges
        }
        return videoOnly360.filter { it.hasAvc }.ifEmpty {
            videoOnly360.filter { it.hasVp9 }
        }.minWithOrNull(compareBy({ it.bitrate }, { it.itag }))
    }

    private fun selectDashAudio(formats: List<DashRawFormat>): DashRawFormat? {
        val audioOnly = formats.filter { format ->
            format.mimeType.startsWith("audio/", ignoreCase = true) && format.hasRequiredDashRanges
        }
        // This route is independent from ATV's normal Opus resolver. Prefer Opus when the same
        // response exposes it, then use the best available adaptive audio track.
        return audioOnly.filter { it.hasOpus }.ifEmpty { audioOnly }
            .maxWithOrNull(compareBy<DashRawFormat> { it.bitrate }.thenBy { it.itag })
    }

    private val DashRawFormat.hasRequiredDashRanges: Boolean
        get() = initRange != null && indexRange != null && initRange.length > 0L && indexRange.length > 0L

    private val DashRawFormat.hasAvc: Boolean
        get() = mimeType.contains("avc1", ignoreCase = true)

    private val DashRawFormat.hasVp9: Boolean
        get() = mimeType.contains("vp9", ignoreCase = true) || mimeType.contains("vp09", ignoreCase = true)

    private val DashRawFormat.hasOpus: Boolean
        get() = mimeType.contains("opus", ignoreCase = true)

    private val DashRawFormat.hasAudioCodec: Boolean
        get() = listOf("mp4a", "opus", "vorbis", "ac-3", "ec-3")
            .any { codec -> mimeType.contains(codec, ignoreCase = true) }

    /**
     * DASH responses provide YouTube's quality classification. It is authoritative when present:
     * a 360p ultrawide stream can be 640x268, while portrait can be 360x640. Models without a
     * quality label retain the exact short-side fallback so 480p/720p cannot be promoted.
     */
    private fun is360pClass(width: Int?, height: Int?): Boolean =
        width != null && height != null && minOf(width, height) == 360

    private fun DashRawFormat.is360pClass(): Boolean =
        if (!qualityLabel.isNullOrBlank()) is360pQualityLabel() else is360pClass(width, height)

    private fun DashRawFormat.is360pByDimensions(): Boolean = is360pClass(width, height)

    /** Strictly recognizes YouTube's 360p quality label when the raw DASH response provides it. */
    private fun DashRawFormat.is360pQualityLabel(): Boolean =
        qualityLabel?.trim()?.matches(Regex("360p(?:[0-9]+)?", RegexOption.IGNORE_CASE)) == true

    private fun com.metrolist.innertubex.models.response.PlayerResponse.StreamingData.Format.is360pClass(): Boolean =
        is360pClass(width, height)

    /** Diagnostics only: no stream URL, cipher payload, cookie, token, or header is emitted. */
    private fun logDashVideoFormatDiagnostics(
        videoId: String,
        musicVideoType: String,
        clientName: String,
        videoFormats: List<DashRawFormat>,
    ) {
        val dimension360 = videoFormats.filter { it.is360pByDimensions() }
        val selected360 = videoFormats.filter { it.is360pClass() }
        Timber.tag(TAG).i(
            "[VideoPlayback][dashDiag] mediaId=$videoId musicVideoType=$musicVideoType " +
                "client=$clientName diagVersion=vertical2 videoFormats=${videoFormats.size} " +
                "video360ByDimensions=${dimension360.size} " +
                "video360ByQualityLabel=${videoFormats.count { it.is360pQualityLabel() }} " +
                "video360WithInitRange=${selected360.count { it.initRange != null }} " +
                "video360WithIndexRange=${selected360.count { it.indexRange != null }} " +
                "video360WithBothRanges=${selected360.count { it.hasRequiredDashRanges }}",
        )
        videoFormats.forEach { format ->
            val isVideo = format.mimeType.startsWith("video/", ignoreCase = true)
            val supportedCodec = format.hasAvc || format.hasVp9
            val candidate = isVideo && format.is360pClass() && !format.hasAudioCodec &&
                format.hasRequiredDashRanges && supportedCodec
            val reason = when {
                !isVideo -> "NOT_VIDEO"
                !format.is360pClass() -> "NOT_360P"
                !supportedCodec -> "NOT_AVC_OR_VP9"
                format.hasAudioCodec -> "NOT_VIDEO_ONLY"
                format.initRange == null -> "NO_INIT_RANGE"
                format.indexRange == null -> "NO_INDEX_RANGE"
                else -> "SELECTABLE"
            }
            Timber.tag(TAG).i(
                "[VideoPlayback][dashDiag] mediaId=$videoId musicVideoType=$musicVideoType " +
                    "client=$clientName itag=${format.itag} " +
                    "mime=${format.mimeType.substringBefore(';')} codecs=${safeCodecDescription(format.mimeType)} " +
                    "width=${format.width} height=${format.height} qualityLabel=${format.qualityLabel} " +
                    "bitrate=${format.bitrate} hasInitRange=${format.initRange != null} " +
                    "hasIndexRange=${format.indexRange != null} contentLengthPresent=${format.contentLength != null} " +
                    "approxDurationMsPresent=${format.approxDurationMs != null} " +
                    "is360Class=${format.is360pClass()} is360ByDimensions=${format.is360pByDimensions()} " +
                    "candidate=$candidate rejectReason=$reason",
            )
        }
    }

    private suspend fun resolveDashStream(
        extraction: ExtractionBundle,
        playerUrl: String,
        token: PoTokenResult?,
        client: com.metrolist.innertubex.models.YouTubeClient,
        raw: DashRawFormat,
    ): DashStream? {
        val cipherFormat = InnerTubeXResponse.StreamingData.Format(
            raw.itag,
            raw.url,
            raw.mimeType,
            raw.bitrate,
            raw.width,
            raw.height,
            raw.contentLength,
            raw.quality,
            raw.audioQuality,
            raw.approxDurationMs?.toString(),
            raw.audioSampleRate,
            raw.audioChannels,
            null,
            raw.lastModified,
            null,
            null,
            false,
            raw.signatureCipher,
            raw.cipher,
        )
        val resolved = extraction.cipherService.processFormats(playerUrl, listOf(cipherFormat)).firstOrNull()
            ?: return null
        val streamUrl = resolved.url ?: return null
        val uri = android.net.Uri.parse(streamUrl)
        if (uri.scheme != "https") return null
        val finalUri = uri.buildUpon().apply {
            token?.streamingDataToken?.let { if (uri.getQueryParameter("pot") == null) appendQueryParameter("pot", it) }
            if (uri.getQueryParameter("cpn") == null) appendQueryParameter("cpn", generateClientPlaybackNonce())
        }.build()
        return DashStream(
            streamUrl = finalUri.toString(),
            mimeType = raw.mimeType,
            codecs = safeCodecDescription(raw.mimeType).ifBlank { null },
            width = raw.width,
            height = raw.height,
            bitrate = raw.bitrate,
            itag = raw.itag,
            contentLength = raw.contentLength,
            approxDurationMs = raw.approxDurationMs,
            audioChannels = raw.audioChannels,
            audioSampleRate = raw.audioSampleRate,
            initRange = requireNotNull(raw.initRange),
            indexRange = requireNotNull(raw.indexRange),
            headers = mapOf("User-Agent" to client.userAgent, "Origin" to "https://www.youtube.com"),
        )
    }

    private fun JsonObject.string(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.long(name: String): Long? =
        this[name]?.jsonPrimitive?.longOrNull ?: string(name)?.toLongOrNull()

    private fun JsonObject.int(name: String): Int? =
        this[name]?.jsonPrimitive?.intOrNull ?: string(name)?.toIntOrNull()

    private fun JsonObject.byteRange(name: String): ByteRange? {
        val range = this[name]?.jsonObject ?: return null
        val start = range.long("start") ?: return null
        val end = range.long("end") ?: return null
        return ByteRange(start, end).takeIf { it.start >= 0L && it.end >= it.start }
    }

    /**
     * Records format inventory only. It deliberately has no influence on muxed selection or
     * audio fallback, and never emits a URL, signature, cookie, or request token.
     */
    private fun logMuxedFormatDiagnostics(
        videoId: String,
        musicVideoType: String?,
        clientName: String,
        formats: List<com.metrolist.innertubex.models.response.PlayerResponse.StreamingData.Format>,
    ) {
        val videoFormats = formats.filter { format ->
            format.mimeType.startsWith("video/", ignoreCase = true) ||
                format.width != null || format.height != null
        }
        val decisions = videoFormats.map(::muxedFormatDecision)
        val candidates = decisions.filter { it.selectorCandidate }
        val selected = candidates.sortedWith(compareBy({ it.format.bitrate }, { it.format.itag })).firstOrNull()
        Timber.tag(TAG).i(
            "[VideoPlayback][muxedDiag] mediaId=$videoId musicVideoType=$musicVideoType " +
                "client=$clientName totalFormats=${formats.size} progressiveVideoFormats=${videoFormats.size} " +
                "muxedCandidates=${candidates.size} selected=${selected?.let { "itag=${it.format.itag}" } ?: "none"}",
        )
        decisions.forEach { decision ->
            val format = decision.format
            Timber.tag(TAG).i(
                    "[VideoPlayback][muxedDiag] mediaId=$videoId itag=${format.itag} " +
                    "mime=${format.mimeType.substringBefore(';')} codecs=${safeCodecDescription(format.mimeType)} " +
                    // The WEB model does not expose qualityLabel; its quality field is logged
                    // separately rather than inferring a label from dimensions.
                    "width=${format.width} height=${format.height} qualityLabel=unavailable quality=${format.quality} bitrate=${format.bitrate} " +
                    "audioChannels=${format.audioChannels} audioSampleRate=${format.audioSampleRate} " +
                    "audioQuality=${format.audioQuality} hasAudioMetadata=${decision.hasAudioMetadata} " +
                    "hasVideo=${decision.hasVideo} ciphered=${decision.ciphered} " +
                    "${if (decision.selectorCandidate) "candidate=true${decision.reason?.let { " resolutionRisk=$it" }.orEmpty()}" else "rejected reason=${decision.reason}"}",
            )
        }
    }

    private data class MuxedFormatDecision(
        val format: com.metrolist.innertubex.models.response.PlayerResponse.StreamingData.Format,
        val hasVideo: Boolean,
        val hasAudioMetadata: Boolean,
        val ciphered: Boolean,
        val selectorCandidate: Boolean,
        val reason: String?,
    )

    /** This ordering mirrors the existing filter; it is diagnostics, not a second selector. */
    private fun muxedFormatDecision(
        format: com.metrolist.innertubex.models.response.PlayerResponse.StreamingData.Format,
    ): MuxedFormatDecision {
        val mime = format.mimeType
        val hasVideo = mime.startsWith("video/", ignoreCase = true) &&
            format.width != null && format.height != null
        val hasAudioMetadata = format.audioChannels != null || format.audioSampleRate != null ||
            !format.audioQuality.isNullOrBlank()
        val ciphered = !format.signatureCipher.isNullOrBlank() || !format.cipher.isNullOrBlank()
        val hasAvc = mime.contains("avc1", ignoreCase = true)
        val hasAac = mime.contains("mp4a", ignoreCase = true)
        val selectorCandidate = hasVideo && format.is360pClass() && hasAvc && hasAac &&
            (format.audioChannels ?: 0) > 0
        val reason = when {
            !hasVideo -> "NOT_VIDEO"
            !format.is360pClass() -> "NOT_360P"
            !hasAvc -> "NOT_AVC"
            !hasAac && !hasAudioMetadata -> "NOT_MUXED"
            !hasAac -> "NO_AAC"
            (format.audioChannels ?: 0) <= 0 -> "NO_AUDIO_CHANNELS"
            format.url.isNullOrBlank() && !ciphered -> "NO_URL"
            else -> null
        }
        return MuxedFormatDecision(format, hasVideo, hasAudioMetadata, ciphered, selectorCandidate, reason)
    }

    private fun safeCodecDescription(mimeType: String): String =
        mimeType.substringAfter("codecs=", "").trim('"', ' ')

    fun clearWebRemixFailures() {
        webRemixFailures.clear()
    }

    suspend fun refreshAfterStreamRejection(): Boolean {
        val changed = bundle().cipherService.refreshAfterStreamRejection()
        if (changed) clearWebRemixFailures()
        return changed
    }

    private fun hasRecentWebRemixFailure(videoId: String): Boolean {
        val failedAt = webRemixFailures[videoId] ?: return false
        if ((System.currentTimeMillis() - failedAt) !in 0 until WEB_REMIX_FAILURE_TTL_MS) {
            webRemixFailures.remove(videoId, failedAt)
            return false
        }
        return true
    }

    private suspend fun bundle(): ExtractionBundle {
        val currentGeneration = 0L // Hardcode logic generation since we don't hot-reload proxies in this hybrid mode
        currentBundle?.let { return it }
        return bundleMutex.withLock {
            currentBundle?.let { return@withLock it }
            try {
                currentBundle?.cipherService?.dispose()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                logger.log(
                    com.metrolist.innertubex.InnerTubeLogEvent(
                        level = InnerTubeLogLevel.WARN,
                        tag = TAG,
                        message = "old cipher disposal failed",
                        details = mapOf("exceptionType" to (error::class.simpleName ?: "unknown")),
                    ),
                )
            }

            val remoteStore = RemotePlayerConfigStore(httpClient, configRepository, logger)
            val cipherService = YouTubeCipherService(httpClient, remoteStore, logger)
            val configParser = YtConfigParserImpl(httpClient, innerTubeX, remoteStore, logger)
            val extractor =
                InnerTubeExtractor(
                    configParser = configParser,
                    cipherService = cipherService,
                    innerTube = innerTubeX,
                    tokenProvider = tokenProvider,
                    logger = logger,
                )
            ExtractionBundle(currentGeneration, cipherService, extractor, configParser).also { currentBundle = it }
        }
    }

    private val configRepository: PlayerConfigRepository by lazy {
        AndroidPlayerConfigRepository(requireNotNull(applicationContext) { "InnerTubeXPlayer is not initialized" })
    }

    private val poTokenGenerator: PoTokenGenerator by lazy {
        PoTokenGenerator(requireNotNull(applicationContext) { "InnerTubeXPlayer is not initialized" })
    }

    private val tokenProvider =
        object : TokenProvider {
            override val capabilities =
                TokenProviderCapabilities(
                    providers = setOf(PoTokenProviderKind.WEB_BOTGUARD),
                    usesWebView = true,
                )

            override suspend fun getPoToken(
                videoId: String,
                visitorData: String,
                cookie: String?,
            ): PoTokenResult? =
                poTokenGenerator.getWebClientPoToken(videoId, visitorData)?.let { token ->
                    PoTokenResult(
                        playerRequestToken = token.playerRequestPoToken,
                        streamingDataToken = token.streamingDataPoToken,
                        visitorData = visitorData,
                    )
                }

            override suspend fun prewarm(cookie: String?) {
                innerTubeX.visitorData?.let { poTokenGenerator.getWebClientPoToken(POTOKEN_WARMUP_VIDEO_ID, it) }
            }

            override suspend fun close() {
                poTokenGenerator.close()
            }
        }

    private val logger =
        InnerTubeLogger { event ->
            val details = event.details.entries.joinToString(prefix = " [", postfix = "]") { "${it.key}=${it.value}" }
            val message = event.message + details.takeUnless { event.details.isEmpty() }.orEmpty()
            when (event.level) {
                InnerTubeLogLevel.DEBUG -> Timber.tag(event.tag).d(message)
                InnerTubeLogLevel.INFO -> Timber.tag(event.tag).i(message)
                InnerTubeLogLevel.WARN -> Timber.tag(event.tag).w(message)
                InnerTubeLogLevel.ERROR -> Timber.tag(event.tag).e(message)
            }
        }

    class PlaybackData(
        val audioConfig: PlayerResponse.PlayerConfig.AudioConfig?,
        val videoDetails: PlayerResponse.VideoDetails?,
        val playbackTracking: PlayerResponse.PlaybackTracking?,
        val format: PlayerResponse.StreamingData.Format,
        val streamUrl: String,
        val streamExpiresInSeconds: Int,
        val streamClient: String,
        val streamHeaders: Map<String, String>,
        val requireBoundedRange: Boolean,
        val rangeChunkSizeBytes: Long,
        val useRangeChunks: Boolean,
        val isSaavnStream: Boolean = false,
        val video: VideoStream? = null,
    )

    /** Memory-only resolved stream; adaptive callers return video-only, MuxedRoute includes audio. */
    data class VideoStream(
        val streamUrl: String,
        val mimeType: String,
        val codecs: String?,
        val width: Int,
        val height: Int,
        val bitrate: Int?,
        val itag: Int?,
        val contentLength: Long?,
        val headers: Map<String, String>,
    )

    private data class ExtractionBundle(
        val transportGeneration: Long,
        val cipherService: YouTubeCipherService,
        val extractor: InnerTubeExtractor,
        val configParser: YtConfigParserImpl,
    )

    private class AndroidPlayerConfigRepository(context: Context) : PlayerConfigRepository {
        private val preferences = context.getSharedPreferences("innertubex_player_config", Context.MODE_PRIVATE)

        override val enabled: Boolean = true
        override val sourceUrl: String = PLAYER_CONFIG_URL
        override val defaultSourceUrl: String = PLAYER_CONFIG_URL
        override var cachedJson: String
            get() = preferences.getString("json", "").orEmpty()
            set(value) = preferences.edit().putString("json", value).apply()
        override var cachedAtMs: Long
            get() = preferences.getLong("cached_at_ms", 0L)
            set(value) = preferences.edit().putLong("cached_at_ms", value).apply()
        override var cachedSourceUrl: String
            get() = preferences.getString("source_url", "").orEmpty()
            set(value) = preferences.edit().putString("source_url", value).apply()
        override var cachedEtag: String
            get() = preferences.getString("etag", "").orEmpty()
            set(value) = preferences.edit().putString("etag", value).apply()

        private companion object {
            const val PLAYER_CONFIG_URL =
                "https://raw.githubusercontent.com/ZemerTeam/zemer-cipher/master/library/src/main/assets/player_configs.json"
        }
    }

    private fun AudioQuality.toInnerTubeX(connectivityManager: ConnectivityManager): InnerTubeXAudioQuality =
        when (this) {
            AudioQuality.HIGH -> InnerTubeXAudioQuality.HIGH
            AudioQuality.LOW -> InnerTubeXAudioQuality.LOW
            AudioQuality.AUTO -> InnerTubeXAudioQuality.AUTO
        }


    private fun ExtractedStream.toPlaybackData(): PlaybackData {
        val metadata = mediaMetadata
        val tracking = playbackTracking
        val fullMimeType =
            if (codecs.isNullOrBlank()) {
                mimeType.orEmpty()
            } else {
                "${mimeType.orEmpty()}; codecs=\"$codecs\""
            }
        val video = videoUrl?.takeIf { it.isNotBlank() }?.let { url ->
            val mime = videoMimeType.orEmpty()
            val codecDescription = listOf(mime, videoCodecs.orEmpty()).joinToString(";")
            val supportedCodec = mime.contains("avc1", ignoreCase = true) ||
                videoCodecs?.contains("avc1", ignoreCase = true) == true ||
                mime.contains("vp9", ignoreCase = true) ||
                mime.contains("vp09", ignoreCase = true) ||
                videoCodecs?.contains("vp9", ignoreCase = true) == true ||
                videoCodecs?.contains("vp09", ignoreCase = true) == true
            // Adaptive video-only formats never declare an audio codec. Reject a muxed stream
            // even if a future extractor exposes one through videoUrl, avoiding double audio.
            val containsAudioCodec = listOf("mp4a", "opus", "vorbis", "ac-3", "ec-3")
                .any { audioCodec -> codecDescription.contains(audioCodec, ignoreCase = true) }
            val width = videoWidth
            val height = videoHeight
            if (is360pClass(width, height) &&
                mime.startsWith("video/", ignoreCase = true) && supportedCodec && !containsAudioCodec
            ) {
                VideoStream(
                    streamUrl = url,
                    mimeType = mime,
                    codecs = videoCodecs,
                    width = requireNotNull(width),
                    height = requireNotNull(height),
                    bitrate = videoBitrate,
                    itag = videoItag,
                    contentLength = videoContentLengthBytes,
                    headers = headers,
                )
            } else null
        }
        return PlaybackData(
            audioConfig =
                if (loudnessDb != null || perceptualLoudnessDb != null) {
                    PlayerResponse.PlayerConfig.AudioConfig(loudnessDb, perceptualLoudnessDb)
                } else {
                    null
                },
            videoDetails =
                metadata?.let {
                    PlayerResponse.VideoDetails(
                        videoId = videoId,
                        title = it.title,
                        author = it.author,
                        channelId = it.channelId.orEmpty(),
                        lengthSeconds = it.durationSeconds?.toString().orEmpty(),
                        musicVideoType = it.musicVideoType,
                        viewCount = it.viewCount,
                        thumbnail = com.music.innertube.models.Thumbnails(
                            it.thumbnails.map { thumbnail ->
                                Thumbnail(thumbnail.url, thumbnail.width, thumbnail.height)
                            },
                        )
                    )
                },
            playbackTracking =
                tracking?.let {
                    PlayerResponse.PlaybackTracking(
                        videostatsPlaybackUrl =
                            it.playbackUrl?.let(PlayerResponse.PlaybackTracking::VideostatsPlaybackUrl),
                        videostatsWatchtimeUrl =
                            it.watchtimeUrl?.let(PlayerResponse.PlaybackTracking::VideostatsWatchtimeUrl),
                        atrUrl = null,
                    )
                },
            format =
                PlayerResponse.StreamingData.Format(
                    itag = itag,
                    url = audioUrl,
                    mimeType = fullMimeType,
                    bitrate = bitrate ?: 0,
                    width = null,
                    height = null,
                    contentLength = contentLengthBytes,
                    quality = "",
                    fps = null,
                    qualityLabel = null,
                    averageBitrate = bitrate,
                    audioQuality = null,
                    approxDurationMs = metadata?.durationSeconds?.times(1000)?.toString(),
                    audioSampleRate = sampleRate,
                    audioChannels = null,
                    loudnessDb = loudnessDb,
                    lastModified = null,
                    signatureCipher = null,
                    cipher = null,
                    audioTrack = null,
                ),
            streamUrl = audioUrl,
            streamExpiresInSeconds =
                expiresAt
                    ?.let { ((it.toEpochMilliseconds() - Clock.System.now().toEpochMilliseconds()) / 1000L).toInt() }
                    ?.coerceAtLeast(1)
                    ?: DEFAULT_STREAM_TTL_SECONDS,
            streamClient = clientName,
            streamHeaders = headers,
            requireBoundedRange = this.requireBoundedRange,
            rangeChunkSizeBytes = this.rangeChunkSizeBytes,
            useRangeChunks = this.useRangeChunks,
            video = video,
        )
    }
}
