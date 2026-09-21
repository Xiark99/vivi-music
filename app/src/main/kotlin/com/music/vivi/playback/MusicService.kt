/**
 * vivimusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

@file:Suppress("DEPRECATION")

package com.music.vivi.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.SQLException
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.media.audiofx.LoudnessEnhancer
import android.net.ConnectivityManager
import android.os.Binder
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.datastore.preferences.core.edit
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.Player.EVENT_POSITION_DISCONTINUITY
import androidx.media3.common.Player.EVENT_TIMELINE_CHANGED
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.REPEAT_MODE_ONE
import androidx.media3.common.Player.STATE_IDLE
import androidx.media3.common.Timeline
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.analytics.PlaybackStats
import androidx.media3.exoplayer.analytics.PlaybackStatsListener
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.dash.manifest.AdaptationSet
import androidx.media3.exoplayer.dash.manifest.BaseUrl
import androidx.media3.exoplayer.dash.manifest.DashManifest
import androidx.media3.exoplayer.dash.manifest.Period
import androidx.media3.exoplayer.dash.manifest.RangedUri
import androidx.media3.exoplayer.dash.manifest.Representation
import androidx.media3.exoplayer.dash.manifest.SegmentBase
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.source.MediaSourceEventListener
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.mkv.MatroskaExtractor
import androidx.media3.extractor.mp4.FragmentedMp4Extractor
import androidx.media3.extractor.mp4.Mp4Extractor
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaController
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_ATV
import com.music.innertube.pages.RadioChip
import com.music.lastfm.LastFM
import com.music.vivi.MainActivity
import com.music.vivi.R
import com.music.vivi.constants.AudioNormalizationKey
import com.music.vivi.constants.AudioOffload
import com.music.vivi.constants.AudioQualityKey
import com.music.vivi.constants.AutoDownloadOnLikeKey
import com.music.vivi.constants.AutoLoadMoreKey
import com.music.vivi.constants.AutoSkipNextOnErrorKey
import com.music.vivi.constants.CrossfadeCurve
import com.music.vivi.constants.CrossfadeCurveKey
import com.music.vivi.constants.CrossfadeDurationKey
import com.music.vivi.constants.CrossfadeEnabledKey
import com.music.vivi.constants.CrossfadeGaplessKey
import com.music.vivi.constants.CrossfadeManualSkipKey
import com.music.vivi.constants.DisableLoadMoreWhenRepeatAllKey
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.music.vivi.constants.DiscordActivityNameKey
import com.music.vivi.constants.DiscordActivityTypeKey
import com.music.vivi.constants.DiscordAdvancedModeKey
import com.music.vivi.constants.DiscordButton1TextKey
import com.music.vivi.constants.DiscordButton1VisibleKey
import com.music.vivi.constants.DiscordButton2TextKey
import com.music.vivi.constants.DiscordButton2VisibleKey
import com.music.vivi.constants.DiscordStatusKey
import com.music.vivi.constants.DiscordTokenKey
import com.music.vivi.constants.DiscordUseDetailsKey
import com.music.vivi.constants.EnableDiscordRPCKey
import com.music.vivi.constants.EnableLastFMScrobblingKey
import com.music.vivi.constants.EnableSaavnStreamingKey
import com.music.vivi.constants.HideExplicitKey
import com.music.vivi.constants.HideVideoSongsKey
import com.music.vivi.constants.HistoryDuration
import com.music.vivi.constants.LastFMUseNowPlaying
import com.music.vivi.constants.MediaSessionConstants.CommandToggleLike
import com.music.vivi.constants.MediaSessionConstants.CommandToggleRepeatMode
import com.music.vivi.constants.MediaSessionConstants.CommandToggleShuffle
import com.music.vivi.constants.MediaSessionConstants.CommandToggleStartRadio
import com.music.vivi.constants.PauseListenHistoryKey
import com.music.vivi.constants.PauseOnMute
import com.music.vivi.constants.PersistentQueueKey
import com.music.vivi.constants.PersistentShuffleAcrossQueuesKey
import com.music.vivi.constants.PlayerVolumeKey
import com.music.vivi.constants.PlayerBackgroundStyle
import com.music.vivi.constants.PlayerBackgroundStyleKey
import com.music.vivi.constants.RememberShuffleAndRepeatKey
import com.music.vivi.constants.RepeatModeKey
import com.music.vivi.constants.ResumeOnBluetoothConnectKey
import com.music.vivi.constants.ScrobbleDelayPercentKey
import com.music.vivi.constants.ScrobbleDelaySecondsKey
import com.music.vivi.constants.ScrobbleMinSongDurationKey
import com.music.vivi.constants.ShowLyricsKey
import com.music.vivi.constants.ShuffleModeKey
import com.music.vivi.constants.ShufflePlaylistFirstKey
import com.music.vivi.constants.StopMusicOnTaskClearKey
import com.music.vivi.constants.UsePlayerV2Key
import com.music.vivi.constants.VideoPlaybackKey
import com.music.vivi.constants.PreventDuplicateTracksInQueueKey
import com.music.vivi.constants.SimilarContent
import com.music.vivi.constants.SkipSilenceInstantKey
import com.music.vivi.constants.SkipSilenceKey
import com.music.vivi.constants.EnableSponsorBlockKey
import com.music.vivi.constants.SponsorBlockServerUrlKey
import com.music.vivi.constants.SponsorBlockSkipNonMusicKey
import com.music.vivi.constants.SponsorBlockSkipSponsorKey
import com.music.vivi.constants.SponsorBlockSkipSelfPromoKey
import com.music.vivi.constants.SponsorBlockSkipInteractionKey
import com.music.vivi.constants.SponsorBlockSkipIntroOutroKey
import com.music.vivi.constants.SponsorBlockSkipPreviewFillerKey
import com.music.vivi.constants.SponsorBlockShowToastKey
import com.music.vivi.sponsorblock.SponsorBlockApi
import com.music.vivi.sponsorblock.SponsorSegment
import com.music.vivi.constants.IpVersionKey
import com.music.innertube.models.IpVersion
import okhttp3.Dns
import java.net.InetAddress
import java.net.Inet4Address
import java.net.Inet6Address
import com.music.vivi.db.MusicDatabase
import com.music.vivi.db.entities.Event
import com.music.vivi.db.entities.FormatEntity
import com.music.vivi.db.entities.LyricsEntity
import com.music.vivi.db.entities.RelatedSongMap
import com.music.vivi.db.entities.Song
import com.music.vivi.di.DownloadCache
import com.music.vivi.di.PlayerCache
import com.music.vivi.eq.EqualizerService
import com.music.vivi.eq.audio.CustomEqualizerAudioProcessor
import com.music.vivi.eq.data.EQProfileRepository
import com.music.vivi.extensions.SilentHandler
import com.music.vivi.extensions.collect
import com.music.vivi.extensions.collectLatest
import com.music.vivi.extensions.currentMetadata
import com.music.vivi.extensions.findNextMediaItemById
import com.music.vivi.extensions.mediaItems
import com.music.vivi.extensions.metadata
import com.music.vivi.extensions.setOffloadEnabled
import com.music.vivi.extensions.toEnum
import com.music.vivi.extensions.toMediaItem
import com.music.vivi.extensions.toPersistQueue
import com.music.vivi.extensions.toQueue
import com.music.vivi.lyrics.LyricsHelper
import com.music.vivi.models.PersistPlayerState
import com.music.vivi.models.PersistQueue
import com.music.vivi.models.toMediaMetadata
import com.music.vivi.playback.audio.SilenceDetectorAudioProcessor
import com.music.vivi.playback.queues.EmptyQueue
import com.music.vivi.playback.queues.ListQueue
import com.music.vivi.playback.queues.Queue
import com.music.vivi.playback.queues.YouTubeQueue
import com.music.vivi.playback.queues.filterExplicit
import com.music.vivi.playback.queues.filterVideoSongs
import com.music.vivi.utils.CoilBitmapLoader
import com.music.vivi.utils.DiscordRPC
import com.music.vivi.utils.InnerTubeXPlayer
import com.music.vivi.utils.NetworkConnectivityObserver
import com.music.vivi.utils.ScrobbleManager
import com.music.vivi.utils.SyncUtils
import com.music.vivi.utils.YTPlayerUtils
import com.music.vivi.utils.dataStore
import com.music.vivi.utils.get
import com.music.vivi.utils.reportException
import com.music.vivi.widget.vivimusicWidgetManager
import com.music.vivi.widget.MusicWidgetReceiver
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import timber.log.Timber
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.IOException
import java.time.LocalDateTime
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.seconds

private const val INSTANT_SILENCE_SKIP_STEP_MS = 15_000L
private const val INSTANT_SILENCE_SKIP_SETTLE_MS = 350L
/**
 * Five seconds leaves normal seeks and short CDN rebuffering alone, while recovering the
 * reproducible merged-source stalls where both playback and buffering stop advancing.
 */
private const val VIDEO_STALL_TIMEOUT_MS = 5_000L
private const val VIDEO_STALL_PROGRESS_THRESHOLD_MS = 250L
/** Avoid giving startup stalls a second five-second window for a tiny video buffer increase. */
private const val VIDEO_STARTUP_MIN_BUFFER_MS = 500L
private const val VIDEO_AUTO_RETRY_MIN_OBSERVATION_MS = 2_000L
private const val VIDEO_AUTO_RETRY_MAX_STARTUP_POSITION_MS = 250L
private const val VIDEO_AUTO_RETRY_REQUIRED_RATE_FRACTION = 0.5
private const val SIDELOADED_DASH_SCHEME = "vivi-dash"
private val ACTUAL_MUSIC_VIDEO_TYPES = setOf("MUSIC_VIDEO_TYPE_OMV", "MUSIC_VIDEO_TYPE_UGC")

private data class VideoPlaybackSettings(
    val enabled: Boolean,
    val usePlayerV2: Boolean,
    val useSaavn: Boolean,
    val backgroundStyle: PlayerBackgroundStyle,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@androidx.annotation.OptIn(UnstableApi::class)
@AndroidEntryPoint
class MusicService :
    MediaLibraryService(),
    Player.Listener,
    PlaybackStatsListener.Callback {
    @Inject
    lateinit var database: MusicDatabase

    @Inject
    lateinit var lyricsHelper: LyricsHelper

    @Inject
    lateinit var syncUtils: SyncUtils

    @Inject
    lateinit var mediaLibrarySessionCallback: MediaLibrarySessionCallback

    @Inject
    lateinit var equalizerService: EqualizerService

    @Inject
    lateinit var eqProfileRepository: EQProfileRepository

    @Inject
    lateinit var widgetManager: vivimusicWidgetManager

    @Inject
    lateinit var listenTogetherManager: com.music.vivi.listentogether.ListenTogetherManager

    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var lastAudioFocusState = AudioManager.AUDIOFOCUS_NONE
    private var wasPlayingBeforeAudioFocusLoss = false
    private var hasAudioFocus = false
    private var reentrantFocusGain = false
    private var wasPlayingBeforeVolumeMute = false
    private var isPausedByVolumeMute = false
    var preferredDeviceId: Int? = null //added for audio device switching
        private set//improvement

    private var crossfadeEnabled = false
    private var crossfadeDuration = 5000f
    private var crossfadeGapless = true
    private var crossfadeManualSkipEnabled = false
    private var crossfadeCurve = CrossfadeCurve.EASE_OUT_QUAD
    private var crossfadeTriggerJob: Job? = null

    /** UI-visible state for the merged, primary-player video renderer. */
    val videoPlaybackRequestedMediaId = MutableStateFlow<String?>(null)
    val videoPlaybackActiveMediaId = MutableStateFlow<String?>(null)
    @Volatile private var videoPlaybackEnabled = false
    @Volatile private var playerV2Enabled = false
    @Volatile private var jioSaavnStreamingEnabled = false
    @Volatile private var playerBackgroundStyle = PlayerBackgroundStyle.DEFAULT
    private val videoFallbackMediaIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val resolvedMusicVideoTypes = java.util.concurrent.ConcurrentHashMap<String, String>()
    /** Tracks primary-player video sources, never the normal audio-only source. */
    private val videoSourceMediaIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    /** Keeps muxed and single-DASH paths distinct so merge-only policies never affect DASH. */
    private val videoPlaybackRoutes = java.util.concurrent.ConcurrentHashMap<String, VideoPlaybackRoute>()
    /** Sideloaded manifests are memory-only and are addressed through an opaque source instance. */
    private val dashRoutes = java.util.concurrent.ConcurrentHashMap<Long, InnerTubeXPlayer.DashRoute>()
    /** A video child that has actually begun a load for this service session. */
    private val videoLoadStartedMediaIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    /** Guards the narrow window while the primary item is being recreated as audio-only. */
    private var isRebuildingCurrentAsAudioOnly = false
    /** Written exclusively by the video resolver or video child MediaSource listener. */
    private val videoLoadFailures = java.util.concurrent.ConcurrentHashMap<String, Throwable>()
    /** Only observes the primary player's currently active merged source. */
    private var videoStallWatchdogJob: Job? = null
    private val videoTransferDiagnostics = java.util.concurrent.ConcurrentHashMap<DataSource, VideoTransferDiagnostics>()
    /** One diagnostic aggregate per created merged video source; never used for playback policy. */
    private val videoStreamDiagnostics = java.util.concurrent.ConcurrentHashMap<Long, VideoStreamDiagnostics>()
    private val videoSourceInstanceIds = java.util.concurrent.ConcurrentHashMap<String, Long>()
    /**
     * A media id alone is insufficient: Media3 may create a future queue item's source before it
     * becomes current. Keep its source URI too, so an AUTO transition cannot mistake an old or
     * pre-created audio source for the current item's video route.
     */
    private val videoSourceUris = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val playbackSourceInstanceSequence = java.util.concurrent.atomic.AtomicLong()
    private val videoRequestSequence = java.util.concurrent.atomic.AtomicLong()
    private val videoRouteReplacementSequence = java.util.concurrent.atomic.AtomicLong()
    /** Guards one forced current-item route resolution; it is cleared after a real route is made. */
    private var pendingCurrentVideoRouteActivation: CurrentVideoRouteActivation? = null
    /** A single startup retry is permitted for one merged source, never for audio-only playback. */
    private var videoAutoRetryMediaId: String? = null
    /** The queue index makes a hard source replacement distinct from an actual queue transition. */
    private var videoAutoRetryMediaItemIndex: Int? = null
    private var videoAutoRetrySourceInstanceId: Long? = null
    private var videoAutoRetryReason: String? = null
    private var videoAutoRetryResumePositionMs: Long = 0L
    private var videoAutoRetryStartedAtMs: Long? = null
    private var videoAutoRetrySucceeded = false
    private var audioOnlyFallbackMediaId: String? = null
    private var audioOnlyFallbackStartedAtMs: Long? = null

    private enum class VideoPlaybackRoute {
        MUXED,
        DASH,
    }

    private data class CurrentVideoRouteActivation(
        val mediaId: String,
        val mediaItemIndex: Int,
        val sourceUri: String?,
    )

    private data class VideoStreamRequest(
        val sourceInstanceId: Long?,
        val mediaId: String,
    )

    private data class VideoTransferDiagnostics(
        val sourceInstanceId: Long?,
        val mediaId: String,
        val requestSequence: Long,
        val position: Long,
        val length: Long,
        val uriScheme: String,
        val initializedAtMs: Long,
        var startedAtMs: Long = initializedAtMs,
        var totalBytes: Long = 0L,
        var firstByteLogged: Boolean = false,
        var lastProgressAtMs: Long = initializedAtMs,
    )

    private data class VideoStreamDiagnostics(
        val sourceInstanceId: Long,
        val mediaId: String,
        var openCount: Int = 0,
        var totalBytes: Long = 0L,
        var firstByteAtMs: Long? = null,
        var lastByteAtMs: Long? = null,
        var bitrate: Int? = null,
        var contentLength: Long? = null,
        var mimeType: String? = null,
        var codecs: String? = null,
        var itag: Int? = null,
    )

    /**
     * The video data source is uncached, so its custom cache key is only a resolver identifier.
     * New sources use video:<source instance>:<media id>; accepting the old form keeps this parser
     * harmless for an already-created source while the process is being replaced during an update.
     */
    private fun videoStreamRequest(dataSpec: DataSpec): VideoStreamRequest? {
        val key = dataSpec.key?.removePrefix("video:") ?: return null
        if (key == dataSpec.key || key.isBlank()) return null
        val separator = key.indexOf(':')
        if (separator <= 0 || separator == key.lastIndex) return VideoStreamRequest(null, key)
        return VideoStreamRequest(key.substring(0, separator).toLongOrNull(), key.substring(separator + 1))
    }

    private fun videoDiagnosticsFor(request: VideoStreamRequest): VideoStreamDiagnostics? =
        request.sourceInstanceId?.let { sourceInstanceId ->
            videoStreamDiagnostics.computeIfAbsent(sourceInstanceId) {
                VideoStreamDiagnostics(sourceInstanceId, request.mediaId)
            }
        }

    private fun sourceInstanceLabel(sourceInstanceId: Long?): String =
        sourceInstanceId?.toString() ?: "legacy"

    /** Keeps MediaSource event logs comparable without emitting the resolved CDN URI or headers. */
    private fun loadEventDiagnostics(loadEventInfo: LoadEventInfo, mediaLoadData: MediaLoadData): String =
        "bytesLoaded=${loadEventInfo.bytesLoaded} mediaStartTimeMs=${mediaLoadData.mediaStartTimeMs} " +
            "mediaEndTimeMs=${mediaLoadData.mediaEndTimeMs} dataSpecPosition=${loadEventInfo.dataSpec.position} " +
            "dataSpecLength=${loadEventInfo.dataSpec.length} uriScheme=${loadEventInfo.dataSpec.uri.scheme ?: "none"}"

    /** Logs only video transfer timing/byte counts; URLs and request headers are never logged. */
    private val videoTransferListener = object : TransferListener {
        override fun onTransferInitializing(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {
            val request = videoStreamRequest(dataSpec) ?: return
            val requestSequence = videoRequestSequence.incrementAndGet()
            val now = SystemClock.elapsedRealtime()
            val streamDiagnostics = videoDiagnosticsFor(request)
            streamDiagnostics?.let { diagnostics ->
                synchronized(diagnostics) { diagnostics.openCount += 1 }
            }
            videoTransferDiagnostics[source] = VideoTransferDiagnostics(
                sourceInstanceId = request.sourceInstanceId,
                mediaId = request.mediaId,
                requestSequence = requestSequence,
                position = dataSpec.position,
                length = dataSpec.length,
                uriScheme = dataSpec.uri.scheme ?: "none",
                initializedAtMs = now,
            )
            Timber.tag(TAG).d(
                "[VideoPlayback][dataSource] open requested sourceInstance=${sourceInstanceLabel(request.sourceInstanceId)} " +
                    "request=$requestSequence mediaId=${request.mediaId} position=${dataSpec.position} " +
                    "length=${dataSpec.length} uriScheme=${dataSpec.uri.scheme ?: "none"}",
            )
        }

        override fun onTransferStart(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {
            val diagnostics = videoTransferDiagnostics[source] ?: return
            val now = SystemClock.elapsedRealtime()
            diagnostics.startedAtMs = now
            diagnostics.lastProgressAtMs = now
            Timber.tag(TAG).d(
                "[VideoPlayback][dataSource] open started sourceInstance=${sourceInstanceLabel(diagnostics.sourceInstanceId)} " +
                    "request=${diagnostics.requestSequence} mediaId=${diagnostics.mediaId}",
            )
        }

        override fun onBytesTransferred(
            source: DataSource,
            dataSpec: DataSpec,
            isNetwork: Boolean,
            bytesTransferred: Int,
        ) {
            val diagnostics = videoTransferDiagnostics[source] ?: return
            val now = SystemClock.elapsedRealtime()
            diagnostics.totalBytes += bytesTransferred
            diagnostics.sourceInstanceId?.let { sourceInstanceId ->
                videoStreamDiagnostics[sourceInstanceId]?.let { streamDiagnostics ->
                    synchronized(streamDiagnostics) {
                        streamDiagnostics.totalBytes += bytesTransferred
                        if (streamDiagnostics.firstByteAtMs == null) {
                            streamDiagnostics.firstByteAtMs = now
                        }
                        streamDiagnostics.lastByteAtMs = now
                    }
                }
            }
            val elapsedMs = now - diagnostics.startedAtMs
            if (!diagnostics.firstByteLogged) {
                diagnostics.firstByteLogged = true
                diagnostics.lastProgressAtMs = now
                Timber.tag(TAG).d(
                    "[VideoPlayback][dataSource] first byte sourceInstance=${sourceInstanceLabel(diagnostics.sourceInstanceId)} " +
                        "request=${diagnostics.requestSequence} mediaId=${diagnostics.mediaId} " +
                        "openToFirstByteMs=$elapsedMs bytesRead=${diagnostics.totalBytes}",
                )
            } else if (now - diagnostics.lastProgressAtMs >= 3_000L) {
                diagnostics.lastProgressAtMs = now
                Timber.tag(TAG).d(
                    "[VideoPlayback][dataSource] transfer progress sourceInstance=${sourceInstanceLabel(diagnostics.sourceInstanceId)} " +
                        "request=${diagnostics.requestSequence} mediaId=${diagnostics.mediaId} " +
                        "elapsedMs=$elapsedMs bytesRead=${diagnostics.totalBytes}",
                )
            }
        }

        override fun onTransferEnd(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {
            val diagnostics = videoTransferDiagnostics.remove(source) ?: return
            Timber.tag(TAG).d(
                "[VideoPlayback][dataSource] open ended sourceInstance=${sourceInstanceLabel(diagnostics.sourceInstanceId)} " +
                    "request=${diagnostics.requestSequence} mediaId=${diagnostics.mediaId} " +
                    "bytesRead=${diagnostics.totalBytes} elapsedMs=${SystemClock.elapsedRealtime() - diagnostics.startedAtMs} " +
                    "endSignal=transferEnd",
            )
        }
    }

    /** Holds the combined crossfade-related settings emitted from DataStore. */
    private data class CrossfadeSettings(
        val enabled: Boolean,
        val durationSeconds: Float,
        val gapless: Boolean,
        val manualSkip: Boolean,
        val curve: CrossfadeCurve,
    )

    /**
     * Distinguishes *why* a crossfade transition is starting so [startCrossfade]
     * knows which media item to target (natural end-of-track vs. a manual
     * next/previous press).
     */
    private enum class CrossfadeTrigger {
        AUTO,
        MANUAL_NEXT,
        MANUAL_PREVIOUS,
    }

    private val secondaryPlayerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            Timber.tag(TAG).e(error, "Secondary player error")
            secondaryPlayer?.stop()
            secondaryPlayer?.clearMediaItems()
            secondaryPlayer = null
        }
    }

    private var scope = CoroutineScope(Dispatchers.Main) + Job()

    private val binder = MusicBinder()

    inner class MusicBinder : Binder() {
        val service: MusicService
            get() = this@MusicService
    }

    private lateinit var connectivityManager: ConnectivityManager
    lateinit var connectivityObserver: NetworkConnectivityObserver
    val waitingForNetworkConnection = MutableStateFlow(false)
    private val isNetworkConnected = MutableStateFlow(false)

    private lateinit var audioQuality: com.music.vivi.constants.AudioQuality
    private lateinit var ipVersion: IpVersion

    var currentQueue: Queue = EmptyQueue
    var queueTitle: String? = null
    val radioChips = MutableStateFlow<List<RadioChip>>(emptyList())
    private var radioChipsJob: Job? = null

    val currentMediaMetadata = MutableStateFlow<com.music.vivi.models.MediaMetadata?>(null)
    private val currentSong =
        currentMediaMetadata
            .flatMapLatest { mediaMetadata ->
                database.song(mediaMetadata?.id)
            }.stateIn(scope, SharingStarted.Lazily, null)
    private val currentFormat =
        currentMediaMetadata.flatMapLatest { mediaMetadata ->
            database.format(mediaMetadata?.id)
        }

    lateinit var playerVolume: MutableStateFlow<Float>
    val isMuted = MutableStateFlow(false)

    fun toggleMute() {
        val newMutedState = !isMuted.value
        isMuted.value = newMutedState
        // Immediately update player volume to ensure it takes effect
        player.volume = if (newMutedState) 0f else playerVolume.value
    }

    fun setMuted(muted: Boolean) {
        isMuted.value = muted
        // Immediately update player volume to ensure it takes effect
        // This handles cases where the player reference may have changed
        player.volume = if (muted) 0f else playerVolume.value
    }

    fun setPreferredAudioDevice(deviceId: Int?) { // this helps us to change between devices
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val deviceInfo = devices.find { it.id == deviceId }
            player.setPreferredAudioDevice(deviceInfo)
            preferredDeviceId = deviceId
        }
    }
//

    lateinit var sleepTimer: SleepTimer

    @Inject
    @PlayerCache
    lateinit var playerCache: SimpleCache

    @Inject
    @DownloadCache
    lateinit var downloadCache: SimpleCache

    lateinit var player: ExoPlayer
        private set
    private var secondaryPlayer: ExoPlayer? = null
    private var fadingPlayer: ExoPlayer? = null
    private var isCrossfading = false
    private var crossfadeJob: Job? = null

    private lateinit var mediaSession: MediaLibrarySession
    private var mediaSessionReleased = false

    // Tracks if player has been properly initilized
    private val playerInitialized = MutableStateFlow(false)
    val isPlayerReady: kotlinx.coroutines.flow.StateFlow<Boolean> = playerInitialized.asStateFlow()

    // Expose active player flow for UI/Connection updates
    private val _playerFlow = MutableStateFlow<ExoPlayer?>(null)
    val playerFlow = _playerFlow.asStateFlow()

    private val playerSilenceProcessors = HashMap<Player, SilenceDetectorAudioProcessor>()


    private val instantSilenceSkipEnabled = MutableStateFlow(false)

    private var isAudioEffectSessionOpened = false
    private var loudnessEnhancer: LoudnessEnhancer? = null

    private var discordRpc: DiscordRPC? = null
    private var lastPlaybackSpeed = 1.0f
    private var discordUpdateJob: kotlinx.coroutines.Job? = null

    private var scrobbleManager: ScrobbleManager? = null

    val automixItems = MutableStateFlow<List<MediaItem>>(emptyList())

    /**
     * Emits the video ID of a song immediately after [YouTube.registerPlayback] succeeds.
     * [HistoryViewModel] collects this to auto-refresh remote history without any
     * timing-based delays or tab-state checks.
     */
    val playbackRegistered = MutableSharedFlow<String>(extraBufferCapacity = 1)

    // Tracks the original queue size to distinguish original items from auto-added ones
    private var originalQueueSize: Int = 0

    private var consecutivePlaybackErr = 0
    private var retryJob: Job? = null
    private var retryCount = 0
    private var silenceSkipJob: Job? = null
    /** Background job that pre-resolves the next track's stream URL into [songUrlCache]. */
    private var prefetchJob: Job? = null

    // URL cache for stream URLs - class-level so it can be invalidated on errors
    private val songUrlCache = HashMap<String, Pair<String, Long>>()

    // Flag to bypass cache when quality changes - forces fresh stream fetch
    private val bypassCacheForQualityChange = mutableSetOf<String>()

    // Enhanced error tracking for strict retry management
    private var currentMediaIdRetryCount = mutableMapOf<String, Int>()
    private val MAX_RETRY_PER_SONG = 3
    private val RETRY_DELAY_MS = 1000L

    // Track failed songs to prevent infinite retry loops
    private val recentlyFailedSongs = mutableSetOf<String>()
    private var failedSongsClearJob: Job? = null

    // Google Cast support
    var castConnectionHandler: CastConnectionHandler? = null
        private set

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    if (!player.isPlaying) {
                        scope.launch(Dispatchers.IO) {
                            discordRpc?.closeRPC()
                        }
                    }
                }
                Intent.ACTION_SCREEN_ON -> {
                    if (player.isPlaying) {
                        scope.launch {
                            currentSong.value?.let { song ->
                                updateDiscordRPC(song)
                            }
                        }
                    }
                }
            }
        }
    }

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            super.onAudioDevicesAdded(addedDevices)
            val hasBluetooth = addedDevices?.any {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
            } == true

            if (hasBluetooth) {
                if (dataStore.get(ResumeOnBluetoothConnectKey, false)) {
                    if (player.playbackState == Player.STATE_READY && !player.isPlaying) {
                        player.play()
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true

        // Player rediness reset to false
        playerInitialized.value = false

        // 3. Connect the processor to the service
        // handled in createExoPlayer

        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.music_player),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
            val pending = PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE
            )
            val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.music_player))
                .setContentText("")
                .setSmallIcon(R.drawable.vivimusicnotification)  //vivimusicnotification
                .setContentIntent(pending)
                .setOngoing(true)
                .build()
            startForeground(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to create foreground notification")
            reportException(e)
        }

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider(
                this,
                { NOTIFICATION_ID },
                CHANNEL_ID,
                R.string.music_player
            )
                .apply {
                    setSmallIcon(R.drawable.vivimusicnotification)
                },
        )
        // Read these once before the player/factory can be used. The ongoing collector below
        // keeps the gate current after startup, while this prevents a Player V2 launch from
        // briefly creating a video source before DataStore emits its first value.
        videoPlaybackEnabled = dataStore.get(VideoPlaybackKey, false)
        playerV2Enabled = dataStore.get(UsePlayerV2Key, false)
        jioSaavnStreamingEnabled = dataStore.get(EnableSaavnStreamingKey, false)
        playerBackgroundStyle = dataStore.get(PlayerBackgroundStyleKey).toEnum(PlayerBackgroundStyle.DEFAULT)
        player = createExoPlayer(allowVideo = true)
        player.addListener(this@MusicService)
        sleepTimer = SleepTimer(scope, player)
        player.addListener(sleepTimer)

        // Mark player as initialized after successful creation
        playerInitialized.value = true
        Timber.tag(TAG).d("Player successfully initialized")

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        setupAudioFocusRequest()

        mediaLibrarySessionCallback.apply {
            service = this@MusicService
            toggleLike = ::toggleLike
            toggleStartRadio = ::toggleStartRadio
            toggleLibrary = ::toggleLibrary
        }
        mediaSession =
            MediaLibrarySession
                .Builder(this, player, mediaLibrarySessionCallback)
                .setSessionActivity(
                    PendingIntent.getActivity(
                        this,
                        0,
                        Intent(this, MainActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE,
                    ),
                ).setBitmapLoader(CoilBitmapLoader(this, scope))
                .build()
        player.repeatMode = dataStore.get(RepeatModeKey, REPEAT_MODE_OFF)

        // Restore shuffle mode if remember option is enabled
        if (dataStore.get(RememberShuffleAndRepeatKey, true)) {
            player.shuffleModeEnabled = dataStore.get(ShuffleModeKey, false)
        }

        // Keep a connected controller so that notification works
        val sessionToken = SessionToken(this, ComponentName(this, MusicService::class.java))
        val controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture.addListener({ controllerFuture.get() }, MoreExecutors.directExecutor())

        connectivityManager = getSystemService()!!
        connectivityObserver = NetworkConnectivityObserver(this)

        val screenStateFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(screenStateReceiver, screenStateFilter)

        audioManager.registerAudioDeviceCallback(audioDeviceCallback, null)

        audioQuality = dataStore.get(AudioQualityKey).toEnum(com.music.vivi.constants.AudioQuality.AUTO)
        ipVersion = dataStore.get(IpVersionKey).toEnum(IpVersion.AUTO)
        playerVolume = MutableStateFlow(dataStore.get(PlayerVolumeKey, 1f).coerceIn(0f, 1f))

        // Keep this as a service-side gate: the MediaSource factory must never depend on UI
        // composition state, and Player V2 is intentionally video-ineligible.
        scope.launch {
            combine(
                dataStore.data.map { preferences -> preferences[VideoPlaybackKey] ?: false },
                dataStore.data.map { preferences -> preferences[UsePlayerV2Key] ?: false },
                dataStore.data.map { preferences -> preferences[EnableSaavnStreamingKey] ?: false },
                dataStore.data.map { preferences ->
                    preferences[PlayerBackgroundStyleKey].toEnum(PlayerBackgroundStyle.DEFAULT)
                },
            ) { enabled, usePlayerV2, useSaavn, backgroundStyle ->
                VideoPlaybackSettings(enabled, usePlayerV2, useSaavn, backgroundStyle)
            }
                .collect { settings ->
                    val (enabled, usePlayerV2, useSaavn, backgroundStyle) = settings
                    val wasVideoEnabled = videoPlaybackEnabled
                    val wasPlayerV2Enabled = playerV2Enabled
                    val wasJioSaavnEnabled = jioSaavnStreamingEnabled
                    val wasAppleMusicBackground = playerBackgroundStyle == PlayerBackgroundStyle.APPLE_MUSIC
                    videoPlaybackEnabled = enabled
                    playerV2Enabled = usePlayerV2
                    jioSaavnStreamingEnabled = useSaavn
                    playerBackgroundStyle = backgroundStyle
                    val isAppleMusicBackground = backgroundStyle == PlayerBackgroundStyle.APPLE_MUSIC
                    if (!enabled || usePlayerV2 || useSaavn || isAppleMusicBackground) {
                        resetVideoAutoRetry("video playback mode changed")
                    }
                    if ((wasVideoEnabled && !enabled) ||
                        (!wasPlayerV2Enabled && usePlayerV2) ||
                        (!wasJioSaavnEnabled && useSaavn) ||
                        (!wasAppleMusicBackground && isAppleMusicBackground)
                    ) {
                        cancelVideoStallWatchdog("video playback mode changed")
                        rebuildCurrentAsAudioOnly(
                            reason = when {
                                usePlayerV2 -> "Player V2 enabled"
                                useSaavn -> "JioSaavn streaming enabled"
                                isAppleMusicBackground -> "Apple Music background enabled"
                                else -> "Video playback disabled"
                            },
                            permanentVideoFallback = false,
                        )
                    } else if (wasAppleMusicBackground && !isAppleMusicBackground &&
                        enabled && !usePlayerV2 && !useSaavn
                    ) {
                        // Keep the persisted VideoPlayback setting untouched. Returning to a
                        // supported background reuses the same guarded route rebuild as AUTO
                        // transitions, so only the current item is reconsidered for video.
                        player.currentMediaItem?.let { currentItem ->
                            handleCurrentMediaChanged(
                                currentItem,
                                Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED,
                            )
                        }
                    }
                    logVideoEligibility(player.currentMediaItem)
                }
        }

        // Initialize Google Cast
        initializeCast()

        // 4. Watch for EQ profile changes
        scope.launch {
            eqProfileRepository.activeProfile.collect { profile ->
                if (profile != null) {
                    val result = equalizerService.applyProfile(profile)
                    if (result.isSuccess && player.playbackState == Player.STATE_READY && player.isPlaying) {
                        // Instant update: flush buffers and seek slightly to re-process audio
                        // Small seek to force re-buffer through the new EQ settings
                        // Seek to current position effectively resets the pipeline
                        player.seekTo(player.currentPosition)
                    }
                } else {
                    equalizerService.disable()
                    if (player.playbackState == Player.STATE_READY && player.isPlaying) {
                        player.seekTo(player.currentPosition)
                    }
                }
            }
        }

        scope.launch {
            connectivityObserver.networkStatus.collect { isConnected ->
                isNetworkConnected.value = isConnected
                if (isConnected && waitingForNetworkConnection.value) {
                    triggerRetry()
                }
                // Update Discord RPC when network becomes available
                if (isConnected && discordRpc != null && player.isPlaying) {
                    val mediaId = player.currentMetadata?.id
                    if (mediaId != null) {
                        database.song(mediaId).first()?.let { song ->
                            updateDiscordRPC(song)
                        }
                    }
                }
            }
        }

        // Watch for audio quality setting changes
        var isFirstQualityEmit = true
        scope.launch {
            dataStore.data
                .map { it[AudioQualityKey]?.let { value ->
                    com.music.vivi.constants.AudioQuality.entries.find { it.name == value }
                } ?: com.music.vivi.constants.AudioQuality.AUTO }
                .distinctUntilChanged()
                .collect { newQuality ->
                    val oldQuality = audioQuality
                    audioQuality = newQuality

                    // Skip reload on first emit (app startup)
                    if (isFirstQualityEmit) {
                        isFirstQualityEmit = false
                        Timber.tag("MusicService").i("QUALITY INIT: $newQuality")
                        return@collect
                    }

                    Timber.tag("MusicService").i("QUALITY CHANGED: $oldQuality -> $newQuality")

                    // Reload current song with new quality
                    val mediaId = player.currentMediaItem?.mediaId ?: return@collect
                    val currentPosition = player.currentPosition
                    val wasPlaying = player.isPlaying
                    val currentIndex = player.currentMediaItemIndex

                    Timber.tag("MusicService").i("RELOADING STREAM: $mediaId at position ${currentPosition}ms")

                    // Clear cached URL to force fresh fetch
                    songUrlCache.remove(mediaId)

                    // CRITICAL: Clear caches synchronously to prevent format parsing errors
                    runBlocking(Dispatchers.IO) {
                        try {
                            playerCache.removeResource(mediaId)
                            downloadCache.removeResource(mediaId)
                            Timber.tag("MusicService").d("Cleared player and download cache for $mediaId")
                        } catch (e: Exception) {
                            Timber.tag("MusicService").e(e, "Failed to clear cache for $mediaId")
                        }
                    }

                    // Set bypass flag so resolver skips cache checks
                    bypassCacheForQualityChange.add(mediaId)
                    Timber.tag("MusicService").d("Set bypass cache flag for $mediaId")

                    // Reload player at same position
                    player.stop()
                    player.seekTo(currentIndex, currentPosition)
                    player.prepare()
                    if (wasPlaying) {
                        player.play()
                    }
                }
        }

        // Watch for IP version changes
        scope.launch {
            dataStore.data
                .map { it[IpVersionKey]?.toEnum(IpVersion.AUTO) ?: IpVersion.AUTO }
                .distinctUntilChanged()
                .collect { newIpVersion ->
                    val oldIpVersion = ipVersion
                    ipVersion = newIpVersion

                    if (isFirstQualityEmit) return@collect

                    Timber.tag("MusicService").i("IP VERSION CHANGED: $oldIpVersion -> $newIpVersion")

                    // Reload player to apply new DNS filter
                    val mediaId = player.currentMediaItem?.mediaId ?: return@collect
                    val currentPosition = player.currentPosition
                    val currentIndex = player.currentMediaItemIndex
                    val wasPlaying = player.isPlaying

                    // Clear cached URL
                    songUrlCache.remove(mediaId)

                    // Reload player
                    player.stop()
                    player.seekTo(currentIndex, currentPosition)
                    player.prepare()
                    if (wasPlaying) {
                        player.play()
                    }
                }
        }

        combine(playerVolume, isMuted) { volume, muted ->
            if (muted) 0f else volume
        }.collectLatest(scope) {
            player.volume = it
        }

        playerVolume.debounce(1000).collect(scope) { volume ->
            dataStore.edit { settings ->
                settings[PlayerVolumeKey] = volume
            }
        }

        currentSong.debounce(1000).collect(scope) { song ->
            updateNotification()
            updateWidgetUI(player.isPlaying)
        }

        combine(
            currentMediaMetadata.distinctUntilChangedBy { it?.id },
            dataStore.data.map { it[ShowLyricsKey] ?: false }.distinctUntilChanged(),
        ) { mediaMetadata, showLyrics ->
            mediaMetadata to showLyrics
        }.collectLatest(scope) { (mediaMetadata, showLyrics) ->
            if (showLyrics && mediaMetadata != null && database.lyrics(mediaMetadata.id)
                    .first() == null
            ) {
                val lyricsWithProvider = lyricsHelper.getLyrics(mediaMetadata)
                database.query {
                    upsert(
                        LyricsEntity(
                            id = mediaMetadata.id,
                            lyrics = lyricsWithProvider.lyrics,
                            provider = lyricsWithProvider.provider,
                        ),
                    )
                }
            }
        }

        dataStore.data
            .map { (it[SkipSilenceKey] ?: false) to (it[SkipSilenceInstantKey] ?: false) }
            .distinctUntilChanged()
            .collectLatest(scope) { (skipSilence, instantSkip) ->
                player.skipSilenceEnabled = skipSilence
                secondaryPlayer?.skipSilenceEnabled = skipSilence

                val enableInstant = skipSilence && instantSkip
                instantSilenceSkipEnabled.value = enableInstant

                playerSilenceProcessors.values.forEach { processor ->
                    processor.instantModeEnabled = enableInstant
                    if (!enableInstant) {
                        processor.resetTracking()
                    }
                }

                if (!enableInstant) {
                    silenceSkipJob?.cancel()
                }
            }

        // SponsorBlock automatic segment skipping flow
        var sponsorBlockJob: Job? = null
        combine(
            currentMediaMetadata,
            dataStore.data.map { prefs ->
                SponsorBlockConfig(
                    enabled = prefs[EnableSponsorBlockKey] ?: true,
                    serverUrl = prefs[SponsorBlockServerUrlKey] ?: "https://sponsor.ajay.app",
                    skipNonMusic = prefs[SponsorBlockSkipNonMusicKey] ?: true,
                    skipSponsor = prefs[SponsorBlockSkipSponsorKey] ?: true,
                    skipSelfPromo = prefs[SponsorBlockSkipSelfPromoKey] ?: true,
                    skipInteraction = prefs[SponsorBlockSkipInteractionKey] ?: true,
                    skipIntroOutro = prefs[SponsorBlockSkipIntroOutroKey] ?: true,
                    skipPreviewFiller = prefs[SponsorBlockSkipPreviewFillerKey] ?: false,
                    showToast = prefs[SponsorBlockShowToastKey] ?: true
                )
            }.distinctUntilChanged()
        ) { metadata, config ->
            metadata to config
        }.collectLatest(scope) { (metadata, config) ->
            sponsorBlockJob?.cancel()
            sponsorBlockJob = null

            if (!config.enabled || metadata == null) {
                return@collectLatest
            }

            val categories = mutableListOf<String>()
            if (config.skipNonMusic) categories.add("music_offtopic")
            if (config.skipSponsor) categories.add("sponsor")
            if (config.skipSelfPromo) categories.add("selfpromo")
            if (config.skipInteraction) categories.add("interaction")
            if (config.skipIntroOutro) {
                categories.add("intro")
                categories.add("outro")
            }
            if (config.skipPreviewFiller) {
                categories.add("preview")
                categories.add("filler")
            }

            if (categories.isEmpty()) {
                return@collectLatest
            }

            val result = SponsorBlockApi.getSkipSegments(metadata.id, categories, config.serverUrl)
            val segments = result.getOrDefault(emptyList())

            if (segments.isNotEmpty()) {
                sponsorBlockJob = scope.launch {
                    val skippedUuids = mutableSetOf<String>()
                    while (isActive) {
                        if (player.isPlaying) {
                            val currentPos = player.currentPosition
                            val segmentToSkip = segments.find { segment ->
                                currentPos >= segment.startMs && currentPos < (segment.endMs - 300) && !skippedUuids.contains(segment.uuid)
                            }

                            if (segmentToSkip != null) {
                                skippedUuids.add(segmentToSkip.uuid)
                                withContext(Dispatchers.Main) {
                                    player.seekTo(segmentToSkip.endMs)
                                    if (config.showToast) {
                                        val messageRes = when (segmentToSkip.category) {
                                            "music_offtopic" -> R.string.sponsorblock_skipped_non_music
                                            "sponsor" -> R.string.sponsorblock_skipped_sponsor
                                            "selfpromo" -> R.string.sponsorblock_skipped_selfpromo
                                            "interaction" -> R.string.sponsorblock_skipped_interaction
                                            "intro" -> R.string.sponsorblock_skipped_intro
                                            "outro" -> R.string.sponsorblock_skipped_outro
                                            else -> R.string.sponsorblock_skipped_segment
                                        }
                                        Toast.makeText(this@MusicService, getString(messageRes), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                        delay(250)
                    }
                }
            }
        }
        // end of sponser block

        combine(
            currentFormat,
            dataStore.data
                .map { it[AudioNormalizationKey] ?: true }
                .distinctUntilChanged(),
        ) { format, normalizeAudio ->
            format to normalizeAudio
        }.collectLatest(scope) { (format, normalizeAudio) -> setupLoudnessEnhancer()}

        combine(
            dataStore.data.map { it[AudioOffload] ?: false },
            dataStore.data.map { it[CrossfadeEnabledKey] ?: false }
        ) { offloadPref, crossfadeEnabled ->
             // Force disable offload if crossfade is enabled to prevent volume ramp issues
             if (crossfadeEnabled) false else offloadPref
        }.distinctUntilChanged()
        .collectLatest(scope) { useOffload ->
             player.setOffloadEnabled(useOffload)
             secondaryPlayer?.setOffloadEnabled(useOffload)
        }

        dataStore.data
            .map { it[DiscordTokenKey] to (it[EnableDiscordRPCKey] ?: true) }
            .debounce(300)
            .distinctUntilChanged()
            .collect(scope) { (key, enabled) ->
                if (discordRpc?.isRpcRunning() == true) {
                    discordRpc?.closeRPC()
                }
                discordRpc = null
                if (key != null && enabled) {
                    discordRpc = DiscordRPC(this, key)
                    if (player.playbackState == Player.STATE_READY && player.playWhenReady) {
                        currentSong.value?.let {
                            updateDiscordRPC(it, true)
                        }
                    }
                }
            }

        // Watch all Discord customization preferences
        dataStore.data
            .map {
                listOf(
                    it[DiscordUseDetailsKey],
                    it[DiscordAdvancedModeKey],
                    it[DiscordStatusKey],
                    it[DiscordButton1TextKey],
                    it[DiscordButton1VisibleKey],
                    it[DiscordButton2TextKey],
                    it[DiscordButton2VisibleKey],
                    it[DiscordActivityTypeKey],
                    it[DiscordActivityNameKey]
                )
            }
            .debounce(300)
            .distinctUntilChanged()
            .collect(scope) {
                if (player.playbackState == Player.STATE_READY) {
                    currentSong.value?.let { song ->
                        updateDiscordRPC(song, true)
                    }
                }
            }

        dataStore.data
            .map { it[EnableLastFMScrobblingKey] ?: false }
            .debounce(300)
            .distinctUntilChanged()
            .collect(scope) { enabled ->
                if (enabled && scrobbleManager == null) {
                    val delayPercent = dataStore.get(ScrobbleDelayPercentKey, LastFM.DEFAULT_SCROBBLE_DELAY_PERCENT)
                    val minSongDuration = dataStore.get(ScrobbleMinSongDurationKey, LastFM.DEFAULT_SCROBBLE_MIN_SONG_DURATION)
                    val delaySeconds = dataStore.get(ScrobbleDelaySecondsKey, LastFM.DEFAULT_SCROBBLE_DELAY_SECONDS)
                    scrobbleManager = ScrobbleManager(
                        scope,
                        minSongDuration = minSongDuration,
                        scrobbleDelayPercent = delayPercent,
                        scrobbleDelaySeconds = delaySeconds
                    )
                    scrobbleManager?.useNowPlaying = dataStore.get(LastFMUseNowPlaying, false)
                } else if (!enabled && scrobbleManager != null) {
                    scrobbleManager?.destroy()
                    scrobbleManager = null
                }
            }

        dataStore.data
            .map { it[LastFMUseNowPlaying] ?: false }
            .distinctUntilChanged()
            .collectLatest(scope) {
                scrobbleManager?.useNowPlaying = it
            }

        dataStore.data
            .map { prefs ->
                Triple(
                    prefs[ScrobbleDelayPercentKey] ?: LastFM.DEFAULT_SCROBBLE_DELAY_PERCENT,
                    prefs[ScrobbleMinSongDurationKey] ?: LastFM.DEFAULT_SCROBBLE_MIN_SONG_DURATION,
                    prefs[ScrobbleDelaySecondsKey] ?: LastFM.DEFAULT_SCROBBLE_DELAY_SECONDS
                )
            }
            .distinctUntilChanged()
            .collect(scope) { (delayPercent, minSongDuration, delaySeconds) ->
                scrobbleManager?.let {
                    it.scrobbleDelayPercent = delayPercent
                    it.minSongDuration = minSongDuration
                    it.scrobbleDelaySeconds = delaySeconds
                }
            }

        combine(
            dataStore.data.map { prefs ->
                CrossfadeSettings(
                    enabled = prefs[CrossfadeEnabledKey] ?: false,
                    durationSeconds = prefs[CrossfadeDurationKey] ?: 5f,
                    gapless = prefs[CrossfadeGaplessKey] ?: true,
                    manualSkip = prefs[CrossfadeManualSkipKey] ?: false,
                    curve = prefs[CrossfadeCurveKey].toEnum(CrossfadeCurve.EASE_OUT_QUAD),
                )
            },
            listenTogetherManager.roomState
        ) { settings, roomState ->
            // Disable crossfade if user is in a listen together room
            settings.copy(enabled = settings.enabled && roomState == null)
        }
            .distinctUntilChanged()
            .collect(scope) { settings ->
                crossfadeEnabled = settings.enabled
                crossfadeDuration = settings.durationSeconds * 1000f // Convert to ms
                crossfadeGapless = settings.gapless
                crossfadeManualSkipEnabled = settings.manualSkip
                crossfadeCurve = settings.curve
            }

        if (dataStore.get(PersistentQueueKey, true)) {
            val queueFile = filesDir.resolve(PERSISTENT_QUEUE_FILE)
            if (queueFile.exists()) {
                runCatching {
                    queueFile.inputStream().use { fis ->
                        ObjectInputStream(fis).use { oos ->
                            oos.readObject() as PersistQueue
                        }
                    }
                }.onSuccess { queue ->
                    runCatching {
                        // Convert back to proper queue type
                        val restoredQueue = queue.toQueue()
                        // Wait for player initialization before playing
                        scope.launch {
                            playerInitialized.first { it }
                            if (isActive) {
                                playQueue(
                                    queue = restoredQueue,
                                    playWhenReady = false,
                                )
                            }
                        }
                    }.onFailure { error ->
                        Timber.tag(TAG).w(error, "Failed to restore persisted queue, clearing data")
                        clearPersistedQueueFiles()
                    }
                }.onFailure { error ->
                    Timber.tag(TAG).w(error, "Failed to read persisted queue, clearing data")
                    clearPersistedQueueFiles()
                }
            }

            val automixFile = filesDir.resolve(PERSISTENT_AUTOMIX_FILE)
            if (automixFile.exists()) {
                runCatching {
                    automixFile.inputStream().use { fis ->
                        ObjectInputStream(fis).use { oos ->
                            oos.readObject() as PersistQueue
                        }
                    }
                }.onSuccess { queue ->
                    runCatching {
                        automixItems.value = queue.items.map { it.toMediaItem() }
                    }.onFailure { error ->
                        Timber.tag(TAG).w(error, "Failed to restore automix queue, clearing data")
                        clearPersistedQueueFiles()
                    }
                }.onFailure { error ->
                    Timber.tag(TAG).w(error, "Failed to read automix queue, clearing data")
                    clearPersistedQueueFiles()
                }
            }

            // Restore player state
            val playerStateFile = filesDir.resolve(PERSISTENT_PLAYER_STATE_FILE)
            if (playerStateFile.exists()) {
                runCatching {
                    playerStateFile.inputStream().use { fis ->
                        ObjectInputStream(fis).use { oos ->
                            oos.readObject() as PersistPlayerState
                        }
                    }
                }.onSuccess { playerState ->
                    // Restore player settings after queue is loaded
                    scope.launch {
                        delay(1000) // Wait for queue to be loaded
                        // Don't restore repeat/shuffle from playerState as they are already set from DataStore (source of truth)
                        // player.repeatMode = playerState.repeatMode
                        // player.shuffleModeEnabled = playerState.shuffleModeEnabled
                        playerVolume.value = playerState.volume

                        // Restore position if it's still valid
                        if (playerState.currentMediaItemIndex < player.mediaItemCount) {
                            player.seekTo(playerState.currentMediaItemIndex, playerState.currentPosition)
                        }

                        // Trigger initial load of radio filter chips for the restored song
                        refreshChipsForCurrentSong()
                    }
                }.onFailure { error ->
                    Timber.tag(TAG).w(error, "Failed to read player state, clearing data")
                    clearPersistedQueueFiles()
                }
            }
        }

        // Save queue periodically to prevent queue loss from crash or force kill
        scope.launch {
            while (isActive) {
                delay(30.seconds)
                if (dataStore.get(PersistentQueueKey, true)) {
                    saveQueueToDisk()
                }
            }
        }

        // Save queue more frequently when playing to ensure state is preserved
        scope.launch {
            while (isActive) {
                delay(10.seconds)
                if (dataStore.get(PersistentQueueKey, true) && player.isPlaying) {
                    saveQueueToDisk()
                }
            }
        }
    }

    private fun createExoPlayer(allowVideo: Boolean = false): ExoPlayer {
        val eqProcessor = CustomEqualizerAudioProcessor()
        equalizerService.addAudioProcessor(eqProcessor)

        val silenceProcessor = SilenceDetectorAudioProcessor { handleLongSilenceDetected() }

        // Set initial state
        runBlocking {
            val skipSilence = dataStore.get(SkipSilenceKey, false)
            val instantSkip = dataStore.get(SkipSilenceInstantKey, false)
            silenceProcessor.instantModeEnabled = skipSilence && instantSkip
        }

        val player = ExoPlayer.Builder(this)
            // Crossfade and preload use secondary players. Keep them audio-only in Phase 1 so
            // the sole video renderer always belongs to the primary service player.
            .setMediaSourceFactory(createMediaSourceFactory(allowVideo))
            .setRenderersFactory(createRenderersFactory(eqProcessor, silenceProcessor))
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                false,
            )
            .setSeekBackIncrementMs(5000)
            .setSeekForwardIncrementMs(5000)
            .setDeviceVolumeControlEnabled(true)
            .build()

        playerSilenceProcessors[player] = silenceProcessor

        player.apply {
                runBlocking {
                    val offload = dataStore.get(AudioOffload, false)
                    val crossfade = dataStore.get(CrossfadeEnabledKey, false)
                    setOffloadEnabled(if (crossfade) false else offload)
                    skipSilenceEnabled = dataStore.get(SkipSilenceKey, false)
                }
                addAnalyticsListener(PlaybackStatsListener(false, this@MusicService))

                // Cleanup handled manually in onDestroy/release
            }
        _playerFlow.value = player
        return player
    }

    private fun setupAudioFocusRequest() {
        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setOnAudioFocusChangeListener { focusChange ->
                handleAudioFocusChange(focusChange)
            }
            .setAcceptsDelayedFocusGain(true)
            .build()
    }

    private fun handleAudioFocusChange(focusChange: Int) {
        when (focusChange) {

            AudioManager.AUDIOFOCUS_GAIN,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT -> {
                hasAudioFocus = true

                if (wasPlayingBeforeAudioFocusLoss && !player.isPlaying && !reentrantFocusGain) {
                    reentrantFocusGain = true
                    scope.launch {
                        delay(300)
                        if (hasAudioFocus && wasPlayingBeforeAudioFocusLoss && !player.isPlaying) {
                            // Don't start local playback if casting
                            if (castConnectionHandler?.isCasting?.value != true) {
                                player.play()
                            }
                            wasPlayingBeforeAudioFocusLoss = false
                        }
                        reentrantFocusGain = false
                    }
                }

                player.volume = if (isMuted.value) 0f else playerVolume.value
                lastAudioFocusState = focusChange
            }

            AudioManager.AUDIOFOCUS_LOSS -> {
                hasAudioFocus = false
                wasPlayingBeforeAudioFocusLoss = player.isPlaying
                if (player.isPlaying) {
                    player.pause()
                }
                abandonAudioFocus()
                lastAudioFocusState = focusChange
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                hasAudioFocus = false
                wasPlayingBeforeAudioFocusLoss = player.isPlaying
                if (player.isPlaying) {
                    player.pause()
                }
                lastAudioFocusState = focusChange
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                hasAudioFocus = false
                wasPlayingBeforeAudioFocusLoss = player.isPlaying
                if (player.isPlaying) {
                    player.volume = if (isMuted.value) 0f else (playerVolume.value * 0.2f)
                }
                lastAudioFocusState = focusChange
            }

            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK -> {
                hasAudioFocus = true
                player.volume = if (isMuted.value) 0f else playerVolume.value
                lastAudioFocusState = focusChange
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        if (hasAudioFocus) return true

        audioFocusRequest?.let { request ->
            val result = audioManager.requestAudioFocus(request)
            hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            return hasAudioFocus
        }
        return false
    }

    private fun abandonAudioFocus() {
        if (hasAudioFocus) {
            audioFocusRequest?.let { request ->
                audioManager.abandonAudioFocusRequest(request)
                hasAudioFocus = false
            }
        }
    }

    private fun clearPersistedQueueFiles() {
        runCatching { filesDir.resolve(PERSISTENT_QUEUE_FILE).delete() }
        runCatching { filesDir.resolve(PERSISTENT_AUTOMIX_FILE).delete() }
        runCatching { filesDir.resolve(PERSISTENT_PLAYER_STATE_FILE).delete() }
    }

    fun hasAudioFocusForPlayback(): Boolean {
        return hasAudioFocus
    }

    private fun waitOnNetworkError() {
        if (waitingForNetworkConnection.value) return

        // Check if we've exceeded max retry attempts
        if (retryCount >= MAX_RETRY_COUNT) {
            Timber.tag(TAG).w("Max retry count ($MAX_RETRY_COUNT) reached, stopping playback")
            stopOnError()
            retryCount = 0
            return
        }

        waitingForNetworkConnection.value = true

        // Start a retry timer with exponential backoff
        retryJob?.cancel()
        retryJob = scope.launch {
            // Exponential backoff: 3s, 6s, 12s, 24s... max 30s
            val delayMs = minOf(3000L * (1 shl retryCount), 30000L)
            Timber.tag(TAG).d("Waiting ${delayMs}ms before retry attempt ${retryCount + 1}/$MAX_RETRY_COUNT")
            delay(delayMs)

            if (isNetworkConnected.value && waitingForNetworkConnection.value) {
                retryCount++
                triggerRetry()
            }
        }
    }

    private fun triggerRetry() {
        waitingForNetworkConnection.value = false
        retryJob?.cancel()

        if (player.currentMediaItem != null) {
            // After 3+ failed retries, try to refresh the stream URL by seeking to current position
            // This forces ExoPlayer to re-resolve the data source and get a fresh URL
            if (retryCount > 3) {
                Timber.tag(TAG).d("Retry count > 3, attempting to refresh stream URL")
                val currentPosition = player.currentPosition
                player.seekTo(player.currentMediaItemIndex, currentPosition)
            }
            player.prepare()
            // Don't call play() here - let the player auto-resume via playWhenReady
            // This avoids stealing audio focus during retry attempts
        }
    }

    private fun skipOnError() {
        /**
         * Auto skip to the next media item on error.
         *
         * To prevent a "runaway diesel engine" scenario, force the user to take action after
         * too many errors come up too quickly. Pause to show player "stopped" state
         */
        consecutivePlaybackErr += 2
        val nextWindowIndex = player.nextMediaItemIndex

        if (consecutivePlaybackErr <= MAX_CONSECUTIVE_ERR && nextWindowIndex != C.INDEX_UNSET) {
            player.seekTo(nextWindowIndex, C.TIME_UNSET)
            player.prepare()
            // Don't start local playback if casting
            if (castConnectionHandler?.isCasting?.value != true) {
                player.play()
            }
            return
        }

        player.pause()
        consecutivePlaybackErr = 0
    }

    private fun stopOnError() {
        player.pause()
    }

    private fun updateNotification() {
        mediaSession.setCustomLayout(
            listOf(
                CommandButton
                    .Builder()
                    .setDisplayName(
                        getString(
                            if (currentSong.value?.song?.liked ==
                                true
                            ) {
                                R.string.action_remove_like
                            } else {
                                R.string.action_like
                            },
                        ),
                    )
                    .setIconResId(if (currentSong.value?.song?.liked == true) R.drawable.ic_heart else R.drawable.ic_heart_outline)
                    .setSessionCommand(CommandToggleLike)
                    .setEnabled(currentSong.value != null)
                    .build(),
                CommandButton
                    .Builder()
                    .setDisplayName(
                        getString(
                            when (player.repeatMode) {
                                REPEAT_MODE_OFF -> R.string.repeat_mode_off
                                REPEAT_MODE_ONE -> R.string.repeat_mode_one
                                REPEAT_MODE_ALL -> R.string.repeat_mode_all
                                else -> throw IllegalStateException()
                            },
                        ),
                    ).setIconResId(
                        when (player.repeatMode) {
                            REPEAT_MODE_OFF -> R.drawable.repeat
                            REPEAT_MODE_ONE -> R.drawable.repeat_one_on
                            REPEAT_MODE_ALL -> R.drawable.repeat_on
                            else -> throw IllegalStateException()
                        },
                    ).setSessionCommand(CommandToggleRepeatMode)
                    .build(),
                CommandButton
                    .Builder()
                    .setDisplayName(getString(if (player.shuffleModeEnabled) R.string.action_shuffle_off else R.string.action_shuffle_on))
                    .setIconResId(if (player.shuffleModeEnabled) R.drawable.shuffle_on else R.drawable.shuffle)
                    .setSessionCommand(CommandToggleShuffle)
                    .build(),
                CommandButton.Builder()
                    .setDisplayName(getString(R.string.start_radio))
                    .setIconResId(R.drawable.radio)
                    .setSessionCommand(CommandToggleStartRadio)
                    .setEnabled(currentSong.value != null)
                    .build(),
            ),
        )
    }

    private suspend fun recoverSong(
        mediaId: String,
        playbackData: InnerTubeXPlayer.PlaybackData? = null
    ) {
        val song = database.song(mediaId).first()
        val mediaMetadata = withContext(Dispatchers.Main) {
            player.findNextMediaItemById(mediaId)?.metadata
        } ?: return
        val duration = song?.song?.duration?.takeIf { it != -1 }
            ?: mediaMetadata.duration.takeIf { it != -1 }
            ?: (playbackData?.videoDetails ?: YTPlayerUtils.playerResponseForMetadata(mediaId)
                .getOrNull()?.videoDetails)?.lengthSeconds?.toInt()
            ?: -1
        database.query {
            if (song == null) insert(mediaMetadata.copy(duration = duration))
            else {
                var updatedSong = song.song
                if (song.song.duration == -1) {
                    updatedSong = updatedSong.copy(duration = duration)
                }
                // Update isVideo flag if it's different from the current value
                if (song.song.isVideo != mediaMetadata.isVideoSong) {
                    updatedSong = updatedSong.copy(isVideo = mediaMetadata.isVideoSong)
                }
                if (updatedSong != song.song) {
                    update(updatedSong)
                }
            }
        }
        if (!database.hasRelatedSongs(mediaId)) {
            val relatedEndpoint =
                YouTube.next(WatchEndpoint(videoId = mediaId)).getOrNull()?.relatedEndpoint
                    ?: return
            val relatedPage = YouTube.related(relatedEndpoint).getOrNull() ?: return
            database.query {
                relatedPage.songs
                    .map(SongItem::toMediaMetadata)
                    .onEach(::insert)
                    .map {
                        RelatedSongMap(
                            songId = mediaId,
                            relatedSongId = it.id
                        )
                    }
                    .forEach(::insert)
            }
        }
    }

    fun playQueue(
        queue: Queue,
        playWhenReady: Boolean = true,
    ) {
        if (!scope.isActive) scope = CoroutineScope(Dispatchers.Main) + Job()

        // Safety Check : Ensuring player is initilized
        if (!playerInitialized.value) {
            Timber.tag(TAG).w("playQueue called before player initialization, queuing request")
            scope.launch {
                playerInitialized.first { it }
                playQueue(queue, playWhenReady)
            }
            return
        }

        // Manually switching to a different song — tapping a track in a
        // playlist/album/queue, not just pressing next/previous — is still a
        // manual navigation action, so it crossfades under the same
        // conditions as manual next/previous. This now also covers queues that
        // start from a single "preload" item (Charts, Explore, Stats, Listen
        // Together sync, and "Start radio"): we crossfade into the preload item
        // on the secondary player immediately, then let the background
        // coroutine fill in the rest of the queue around it once resolved.
        // Requires the player to actually be playing, since the fade ramp
        // pauses while the primary player is paused.
        val canAttemptQueueCrossfade =
            crossfadeEnabled &&
                crossfadeManualSkipEnabled &&
                !isCrossfading &&
                castConnectionHandler?.isCasting?.value != true &&
                player.duration != C.TIME_UNSET &&
                player.playbackState != STATE_IDLE &&
                player.isPlaying

        Timber.tag(TAG).d(
            "playQueue: canAttemptQueueCrossfade=%s (preloadItem=%s crossfadeEnabled=%s manualSkip=%s isCrossfading=%s casting=%s duration=%s playbackState=%s)",
            canAttemptQueueCrossfade,
            queue.preloadItem != null,
            crossfadeEnabled,
            crossfadeManualSkipEnabled,
            isCrossfading,
            castConnectionHandler?.isCasting?.value,
            player.duration,
            player.playbackState,
        )

        currentQueue = queue
        queueTitle = null
        radioChipsJob?.cancel()
        radioChipsJob = null
        radioChips.value = emptyList()
        val persistShuffleAcrossQueues = dataStore.get(PersistentShuffleAcrossQueuesKey, false)
        val previousShuffleEnabled = player.shuffleModeEnabled
        if (!canAttemptQueueCrossfade && !persistShuffleAcrossQueues) {
            player.shuffleModeEnabled = false
        }
        // Reset original queue size when starting a new queue
        originalQueueSize = 0
        // When crossfade is on and the queue starts from a preload item, begin
        // the fade immediately against just that item on the secondary player
        // (instead of the synchronous instant-replace below). The background
        // coroutine then inserts the rest of the queue around the now-playing
        // preload item once it resolves. Falls back to the instant preload if a
        // crossfade is already in flight or couldn't start.
        var preloadQueueCrossfadeStarted = false
        if (canAttemptQueueCrossfade && queue.preloadItem != null) {
            preloadQueueCrossfadeStarted =
                startQueueCrossfadeWithPreload(queue, persistShuffleAcrossQueues)
            if (!preloadQueueCrossfadeStarted && !persistShuffleAcrossQueues) {
                player.shuffleModeEnabled = false
            }
        }
        if (!preloadQueueCrossfadeStarted && queue.preloadItem != null) {
            player.setMediaItem(queue.preloadItem!!.toMediaItem())
            player.prepare()
            player.playWhenReady = playWhenReady
        }
        scope.launch(SilentHandler) {
            val initialStatus =
                withContext(Dispatchers.IO) {
                    queue.getInitialStatus()
                        .filterExplicit(dataStore.get(HideExplicitKey, false))
                        .filterVideoSongs(dataStore.get(HideVideoSongsKey, false))
                }
            if (queue.preloadItem != null && player.playbackState == STATE_IDLE) return@launch
            if (initialStatus.title != null) {
                queueTitle = initialStatus.title
            }

            if (queue.radioChips != null) {
                radioChipsJob = scope.launch {
                    queue.radioChips!!.collect {
                        timber.log.Timber.tag("Chippy").e("MusicService collected chips from Queue! Size: ${it.size}")
                        radioChips.value = it
                    }
                }
            }
            if (initialStatus.items.isEmpty()) return@launch
            // Track original queue size for shuffle playlist first feature
            originalQueueSize = initialStatus.items.size

            // Full-queue crossfade path: only when there was no preload item
            // (the preload path already started its fade above). Guarding here
            // prevents a second crossfade attempt once the full queue resolves.
            if (canAttemptQueueCrossfade && queue.preloadItem == null) {
                Timber.tag(TAG).d(
                    "playQueue: attempting queue crossfade (items=%d targetIndex=%d)",
                    initialStatus.items.size,
                    initialStatus.mediaItemIndex,
                )
                if (startQueueCrossfade(initialStatus, persistShuffleAcrossQueues)) {
                    Timber.tag(TAG).d("playQueue: queue crossfade started successfully")
                    return@launch
                } else {
                    Timber.tag(TAG).d("playQueue: startQueueCrossfade returned false, falling back to instant replace")
                }
            }

            if (queue.preloadItem != null) {
                val actualIndex = initialStatus.items.indexOfFirst { it.mediaId == queue.preloadItem!!.id }
                val targetIndex = if (actualIndex != -1) {
                    actualIndex
                } else {
                    initialStatus.mediaItemIndex.coerceIn(0, initialStatus.items.size)
                }

                if (targetIndex < initialStatus.items.size) {
                    player.addMediaItems(
                        0,
                        initialStatus.items.subList(0, targetIndex)
                    )
                    player.addMediaItems(
                        initialStatus.items.subList(
                            targetIndex + 1,
                            initialStatus.items.size
                        )
                    )
                    if (actualIndex != -1) {
                        player.replaceMediaItem(
                            targetIndex,
                            initialStatus.items[targetIndex]
                        )
                    }
                } else {
                    player.addMediaItems(0, initialStatus.items)
                }
            } else {
                player.setMediaItems(
                    initialStatus.items,
                    if (initialStatus.mediaItemIndex >
                        0
                    ) {
                        initialStatus.mediaItemIndex
                    } else {
                        0
                    },
                    initialStatus.position,
                )
                player.prepare()
                player.playWhenReady = playWhenReady
            }

            // Rebuild shuffle order if shuffle is enabled
            if (player.shuffleModeEnabled) {
                val shufflePlaylistFirst = dataStore.get(ShufflePlaylistFirstKey, false)
                applyShuffleOrder(player.currentMediaItemIndex, player.mediaItemCount, shufflePlaylistFirst)
            }
        }
    }

    fun startRadioSeamlessly() {
        // Safety Check: Ensure Player is initilized
        if (!playerInitialized.value) {
            Timber.tag(TAG).w("startRadioSeamlessly called before player initialization")
            return
        }

        val currentMediaMetadata = player.currentMetadata ?: return

        val currentIndex = player.currentMediaItemIndex
        val currentMediaId = currentMediaMetadata.id

        scope.launch(SilentHandler) {
            // Use simple videoId to let YouTube personalize recommendations
            val radioQueue = YouTubeQueue(
                endpoint = WatchEndpoint(
                    videoId = currentMediaId,
                    playlistId = "RDAMVM$currentMediaId"
                )
            )

            try {
                val initialStatus = withContext(Dispatchers.IO) {
                    radioQueue.getInitialStatus()
                        .filterExplicit(dataStore.get(HideExplicitKey, false))
                        .filterVideoSongs(dataStore.get(HideVideoSongsKey, false))
                }

                if (initialStatus.title != null) {
                    queueTitle = initialStatus.title
                }

                if (radioQueue.radioChips != null) {
                    radioChipsJob?.cancel()
                    radioChipsJob = scope.launch {
                        radioQueue.radioChips!!.collect {
                            radioChips.value = it
                        }
                    }
                }

                // Filter radio items to exclude current media item
                val radioItems = initialStatus.items.filter { item ->
                    item.mediaId != currentMediaId
                }

                if (radioItems.isNotEmpty()) {
                    val itemCount = player.mediaItemCount

                    if (itemCount > currentIndex + 1) {
                        player.removeMediaItems(currentIndex + 1, itemCount)
                    }

                    player.addMediaItems(currentIndex + 1, radioItems)
                    if (player.shuffleModeEnabled) {
                        val shufflePlaylistFirst = dataStore.get(ShufflePlaylistFirstKey, false)
                        applyShuffleOrder(player.currentMediaItemIndex, player.mediaItemCount, shufflePlaylistFirst)
                    }
                }

                currentQueue = radioQueue
            } catch (e: Exception) {
                // Fallback: try with related endpoint
                try {
                    val nextResult = withContext(Dispatchers.IO) {
                        YouTube.next(WatchEndpoint(videoId = currentMediaId)).getOrNull()
                    }
                    nextResult?.relatedEndpoint?.let { relatedEndpoint ->
                        val relatedPage = withContext(Dispatchers.IO) {
                            YouTube.related(relatedEndpoint).getOrNull()
                        }
                        relatedPage?.songs?.let { songs ->
                            val radioItems = songs
                                .filter { it.id != currentMediaId }
                                .map { it.toMediaItem() }
                                .filterExplicit(dataStore.get(HideExplicitKey, false))
                                .filterVideoSongs(dataStore.get(HideVideoSongsKey, false))

                            if (radioItems.isNotEmpty()) {
                                val itemCount = player.mediaItemCount
                                if (itemCount > currentIndex + 1) {
                                    player.removeMediaItems(currentIndex + 1, itemCount)
                                }
                                player.addMediaItems(currentIndex + 1, radioItems)
                                if (player.shuffleModeEnabled) {
                                    val shufflePlaylistFirst = dataStore.get(ShufflePlaylistFirstKey, false)
                                    applyShuffleOrder(player.currentMediaItemIndex, player.mediaItemCount, shufflePlaylistFirst)
                                }
                            }
                        }
                    }
                } catch (_: Exception) {
                    // Silent fail
                }
            }
        }
    }

    fun applyRadioChip(chip: RadioChip) {
        val currentMediaMetadata = player.currentMetadata ?: return
        val currentMediaId = currentMediaMetadata.id
        val currentIndex = player.currentMediaItemIndex

        val endpoint = (currentQueue as? YouTubeQueue)?.endpoint?.copy(params = chip.params)
            ?: com.music.innertube.models.WatchEndpoint(
                videoId = currentMediaId,
                playlistId = "RDAMVM$currentMediaId",
                params = chip.params
            )

        scope.launch(SilentHandler) {
            val radioQueue = YouTubeQueue(
                endpoint = endpoint
            )

            try {
                val initialStatus = withContext(Dispatchers.IO) {
                    radioQueue.getInitialStatus()
                        .filterExplicit(dataStore.get(HideExplicitKey, false))
                        .filterVideoSongs(dataStore.get(HideVideoSongsKey, false))
                }

                if (initialStatus.title != null) {
                    queueTitle = initialStatus.title
                }

                if (radioQueue.radioChips != null) {
                    radioChipsJob?.cancel()
                    radioChipsJob = scope.launch {
                        radioQueue.radioChips!!.collect {
                            radioChips.value = it.map { rc -> 
                                if (rc.title == chip.title) rc.copy(isSelected = true) else rc.copy(isSelected = false)
                            }
                        }
                    }
                }

                // Filter radio items to exclude current media item
                val radioItems = initialStatus.items.filter { item ->
                    item.mediaId != currentMediaId
                }

                if (radioItems.isNotEmpty()) {
                    val itemCount = player.mediaItemCount

                    if (itemCount > currentIndex + 1) {
                        player.removeMediaItems(currentIndex + 1, itemCount)
                    }

                    player.addMediaItems(currentIndex + 1, radioItems)
                    if (player.shuffleModeEnabled) {
                        val shufflePlaylistFirst = dataStore.get(ShufflePlaylistFirstKey, false)
                        applyShuffleOrder(player.currentMediaItemIndex, player.mediaItemCount, shufflePlaylistFirst)
                    }
                }

                this@MusicService.currentQueue = radioQueue
            } catch (e: Exception) {
                timber.log.Timber.tag(TAG).e(e, "Failed to apply radio chip")
            }
        }
    }

    fun refreshChipsForCurrentSong() {
        val currentQueue = currentQueue // Use smart cast via local variable
        if (currentQueue != null && currentQueue !is YouTubeQueue) {
            val isRestoredRadio = (currentQueue as? ListQueue)?.isRadio == true
            if (!isRestoredRadio) {
                radioChips.value = emptyList()
                return
            }
        }
        
        val currentMediaMetadata = player.currentMetadata ?: return
        val currentMediaId = currentMediaMetadata.id

        // Only auto-refresh when in a radio-style queue (RDAMVM or similar)
        // so we don't override chips for a chip-filtered queue the user selected
        scope.launch(SilentHandler) {
            val radioQueue = YouTubeQueue(
                endpoint = WatchEndpoint(
                    videoId = currentMediaId,
                    playlistId = "RDAMVM$currentMediaId"
                )
            )
            try {
                var nextResult = withContext(Dispatchers.IO) {
                    com.music.innertube.YouTube.next(
                        radioQueue.endpoint
                    ).getOrNull()
                }

                if (nextResult == null || nextResult.radioChips.isEmpty()) {
                    nextResult = withContext(Dispatchers.IO) {
                        com.music.innertube.YouTube.next(
                            WatchEndpoint(videoId = currentMediaId)
                        ).getOrNull()
                    }
                }

                if (nextResult != null && nextResult.radioChips.isNotEmpty()) {
                    radioChipsJob?.cancel()
                    // We directly update the chips' value from the nextResult
                    // no need to collect the dummy radioQueue stateflow
                    radioChips.value = nextResult.radioChips
                    timber.log.Timber.tag("Chippy").d("refreshChipsForCurrentSong: pushed ${nextResult.radioChips.size} chips for $currentMediaId")
                } else if (currentQueue == null) {
                    // No chips from API and no existing radio queue - clear chips
                    radioChips.value = emptyList()
                }
            } catch (_: Exception) {
                // Silently fail — chips are optional UX enhancement
            }
        }
    }

    fun getAutomixAlbum(albumId: String) {
        scope.launch(SilentHandler) {
            YouTube
                .album(albumId)
                .onSuccess {
                    getAutomix(it.album.playlistId)
                }
        }
    }

    fun getAutomix(playlistId: String) {
        if (dataStore.get(SimilarContent, true) &&
            !(dataStore.get(DisableLoadMoreWhenRepeatAllKey, false) && player.repeatMode == REPEAT_MODE_ALL)) {
            scope.launch(SilentHandler) {
                try {
                    // Try primary method
                    YouTube.next(WatchEndpoint(playlistId = playlistId))
                        .onSuccess { firstResult ->
                            YouTube.next(WatchEndpoint(playlistId = firstResult.endpoint.playlistId))
                                .onSuccess { secondResult ->
                                    automixItems.value = secondResult.items.map { song ->
                                        song.toMediaItem()
                                    }
                                }
                                .onFailure {
                                    // Fallback: use first result items
                                    if (firstResult.items.isNotEmpty()) {
                                        automixItems.value = firstResult.items.map { song ->
                                            song.toMediaItem()
                                        }
                                    }
                                }
                        }
                        .onFailure {
                            // Fallback: try with radio format
                            val currentSong = player.currentMetadata
                            if (currentSong != null) {
                                // Use simple videoId for better personalized recommendations
                                YouTube.next(WatchEndpoint(
                                    videoId = currentSong.id
                                )).onSuccess { radioResult ->
                                    val filteredItems = radioResult.items
                                        .filter { it.id != currentSong.id }
                                        .map { it.toMediaItem() }
                                    if (filteredItems.isNotEmpty()) {
                                        automixItems.value = filteredItems
                                    }
                                }.onFailure {
                                    // Final fallback: try related endpoint
                                    YouTube.next(WatchEndpoint(videoId = currentSong.id)).getOrNull()?.relatedEndpoint?.let { relatedEndpoint ->
                                        YouTube.related(relatedEndpoint).onSuccess { relatedPage ->
                                            val relatedItems = relatedPage.songs
                                                .filter { it.id != currentSong.id }
                                                .map { it.toMediaItem() }
                                            if (relatedItems.isNotEmpty()) {
                                                automixItems.value = relatedItems

                                            }
                                        }
                                    }
                                }
                            }
                        }
                } catch (_: Exception) {
                    // Silent fail
                }
            }
        }
    }

    fun addToQueueAutomix(
        item: MediaItem,
        position: Int,
    ) {
        automixItems.value =
            automixItems.value.toMutableList().apply {
                removeAt(position)
            }
        addToQueue(listOf(item))
    }

    fun playNextAutomix(
        item: MediaItem,
        position: Int,
    ) {
        automixItems.value =
            automixItems.value.toMutableList().apply {
                removeAt(position)
            }
        playNext(listOf(item))
    }

    fun clearAutomix() {
        automixItems.value = emptyList()
    }

    fun playNext(items: List<MediaItem>) {
        // If queue is empty or player is idle, play immediately instead
        if (player.mediaItemCount == 0 || player.playbackState == STATE_IDLE) {
            player.setMediaItems(items)
            player.prepare()
            // Don't start local playback if casting
            if (castConnectionHandler?.isCasting?.value != true) {
                player.play()
            }
            return
        }

        // Remove duplicates if enabled
        if (dataStore.get(PreventDuplicateTracksInQueueKey, false)) {
            val itemIds = items.map { it.mediaId }.toSet()
            val indicesToRemove = mutableListOf<Int>()
            val currentIndex = player.currentMediaItemIndex

            for (i in 0 until player.mediaItemCount) {
                if (i != currentIndex && player.getMediaItemAt(i).mediaId in itemIds) {
                    indicesToRemove.add(i)
                }
            }

            // Remove from highest index to lowest to maintain index stability
            indicesToRemove.sortedDescending().forEach { index ->
                player.removeMediaItem(index)
            }
        }

        val insertIndex = player.currentMediaItemIndex + 1
        val shuffleEnabled = player.shuffleModeEnabled

        // Insert items immediately after the current item in the window/index space
        player.addMediaItems(insertIndex, items)
        player.prepare()

        if (shuffleEnabled) {
            // Rebuild shuffle order so that newly inserted items are played next
            val timeline = player.currentTimeline
            if (!timeline.isEmpty) {
                val size = timeline.windowCount
                val currentIndex = player.currentMediaItemIndex

                // Newly inserted indices are a contiguous range [insertIndex, insertIndex + items.size)
                val newIndices = (insertIndex until (insertIndex + items.size)).toSet()

                // Collect existing shuffle traversal order excluding current index
                val orderAfter = mutableListOf<Int>()
                var idx = currentIndex
                while (true) {
                    idx = timeline.getNextWindowIndex(idx, Player.REPEAT_MODE_OFF, /*shuffleModeEnabled=*/true)
                    if (idx == C.INDEX_UNSET) break
                    if (idx != currentIndex) orderAfter.add(idx)
                }

                val prevList = mutableListOf<Int>()
                var pIdx = currentIndex
                while (true) {
                    pIdx = timeline.getPreviousWindowIndex(pIdx, Player.REPEAT_MODE_OFF, /*shuffleModeEnabled=*/true)
                    if (pIdx == C.INDEX_UNSET) break
                    if (pIdx != currentIndex) prevList.add(pIdx)
                }
                prevList.reverse() // preserve original forward order

                val existingOrder = (prevList + orderAfter).filter { it != currentIndex && it !in newIndices }

                // Build new shuffle order: current -> newly inserted (in insertion order) -> rest
                val nextBlock = (insertIndex until (insertIndex + items.size)).toList()
                val finalOrder = IntArray(size)
                var pos = 0
                finalOrder[pos++] = currentIndex
                nextBlock.forEach { if (it in 0 until size) finalOrder[pos++] = it }
                existingOrder.forEach { if (pos < size) finalOrder[pos++] = it }

                // Fill any missing indices (safety) to ensure a full permutation
                if (pos < size) {
                    for (i in 0 until size) {
                        if (!finalOrder.contains(i)) {
                            finalOrder[pos++] = i
                            if (pos == size) break
                        }
                    }
                }

                player.setShuffleOrder(DefaultShuffleOrder(finalOrder, System.currentTimeMillis()))
            }
        }
    }

    
    /**
     * Marks [mediaId] as belonging to the Cache Playlist by setting [dateDownload],
     * but ONLY if the full file (byte 0 through contentLength) is actually present
     * in playerCache. This must only be called from a genuine "track finished
     * naturally" signal (see onMediaItemTransition's AUTO-reason handling) —
     * never from raw dataSpec/chunk resolution, since the player's background
     * prefetch can finish downloading a short file in seconds, long before the
     * user has actually listened to it (or even if they skipped away early).
     *
     * No-op if already marked downloaded, or if we don't yet know the file's
     * contentLength (FormatEntity not fetched yet).
     */

    private suspend fun markCachedIfFullyDownloaded(mediaId: String) {
        val song = database.song(mediaId).first() ?: return
        if (song.song.dateDownload != null || song.song.isDownloaded) return
        val contentLength = song.format?.contentLength ?: return
        // Do not use runBlocking here, just suspend function call
        if (!playerCache.isCached(mediaId, 0, contentLength)) return
        database.query {
            update(song.song.copy(dateDownload = java.time.LocalDateTime.now()))
        }
    }

    fun addToQueue(items: List<MediaItem>) {
        // Remove duplicates if enabled
        if (dataStore.get(PreventDuplicateTracksInQueueKey, false)) {
            val itemIds = items.map { it.mediaId }.toSet()
            val indicesToRemove = mutableListOf<Int>()
            val currentIndex = player.currentMediaItemIndex

            for (i in 0 until player.mediaItemCount) {
                if (i != currentIndex && player.getMediaItemAt(i).mediaId in itemIds) {
                    indicesToRemove.add(i)
                }
            }

            // Remove from highest index to lowest to maintain index stability
            indicesToRemove.sortedDescending().forEach { index ->
                player.removeMediaItem(index)
            }
        }

        player.addMediaItems(items)
        if (player.shuffleModeEnabled) {
            val shufflePlaylistFirst = dataStore.get(ShufflePlaylistFirstKey, false)
            applyShuffleOrder(player.currentMediaItemIndex, player.mediaItemCount, shufflePlaylistFirst)
        }
        player.prepare()
    }

    fun toggleLibrary() {
        scope.launch {
            val songToToggle = currentSong.first()
            songToToggle?.let {
                val isInLibrary = it.song.inLibrary != null
                val token = if (isInLibrary) it.song.libraryRemoveToken else it.song.libraryAddToken

                // Call YouTube API with feedback token if available
                token?.let { feedbackToken ->
                    YouTube.feedback(listOf(feedbackToken))
                }

                // Update local database
                database.query {
                    update(it.song.toggleLibrary())
                }
                currentMediaMetadata.value = player.currentMetadata
            }
        }
    }

    fun toggleLike() {
        scope.launch {
            val songToToggle = currentSong.first()
            songToToggle?.let {
                val song = it.song.toggleLike()
                database.query {
                    update(song)
                    syncUtils.likeSong(song)

                    // Check if auto-download on like is enabled and the song is now liked
                    if (dataStore.get(AutoDownloadOnLikeKey, false) && song.liked) {
                        // Trigger download for the liked song
                        val downloadRequest =
                            androidx.media3.exoplayer.offline.DownloadRequest
                                .Builder(song.id, song.id.toUri())
                                .setCustomCacheKey(song.id)
                                .setData(song.title.toByteArray())
                                .build()
                        androidx.media3.exoplayer.offline.DownloadService.sendAddDownload(
                            this@MusicService,
                            ExoDownloadService::class.java,
                            downloadRequest,
                            false
                        )
                    }
                }
                currentMediaMetadata.value = player.currentMetadata
            }
        }
    }

    fun toggleStartRadio() {
        startRadioSeamlessly()
    }

    private fun setupLoudnessEnhancer() {
        val audioSessionId = player.audioSessionId

        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET || audioSessionId <= 0) {
            Timber.tag(TAG).w("setupLoudnessEnhancer: invalid audioSessionId ($audioSessionId), cannot create effect yet")
            return
        }

        // Create or recreate enhancer if needed
        if (loudnessEnhancer == null) {
            try {
                loudnessEnhancer = LoudnessEnhancer(audioSessionId)
                Timber.tag(TAG).d("LoudnessEnhancer created for sessionId=$audioSessionId")
            } catch (e: Exception) {
                reportException(e)
                loudnessEnhancer = null
                return
            }
        }

        scope.launch {
            try {
                val currentMediaId = withContext(Dispatchers.Main) {
                    player.currentMediaItem?.mediaId
                }

                val normalizeAudio = withContext(Dispatchers.IO) {
                    dataStore.data.map { it[AudioNormalizationKey] ?: true }.first()
                }

                if (normalizeAudio && currentMediaId != null) {
                    val format = withContext(Dispatchers.IO) {
                        database.format(currentMediaId).first()
                    }

                    Timber.tag(TAG).d("Audio normalization enabled: $normalizeAudio")
                    Timber.tag(TAG).d("Format loudnessDb: ${format?.loudnessDb}, perceptualLoudnessDb: ${format?.perceptualLoudnessDb}")

                    // Use loudnessDb if available, otherwise fall back to perceptualLoudnessDb
                    val loudness = format?.loudnessDb ?: format?.perceptualLoudnessDb

                    withContext(Dispatchers.Main) {
                        if (loudness != null) {
                            val loudnessDb = loudness.toFloat()
                            val targetGain = (-loudnessDb * 100).toInt()
                            val clampedGain = targetGain.coerceIn(MIN_GAIN_MB, MAX_GAIN_MB)

                            Timber.tag(TAG).d("Calculated raw normalization gain: $targetGain mB (from loudness: $loudnessDb)")

                            try {
                                loudnessEnhancer?.setTargetGain(clampedGain)
                                loudnessEnhancer?.enabled = true
                                Timber.tag(TAG).i("LoudnessEnhancer gain applied: $clampedGain mB")
                            } catch (e: Exception) {
                                Timber.tag(TAG).e(e, "Failed to apply loudness enhancement")
                                reportException(e)
                                releaseLoudnessEnhancer()
                            }
                        } else {
                            loudnessEnhancer?.enabled = false
                            Timber.tag(TAG).w("Normalization enabled but no loudness data available - no normalization applied")
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        loudnessEnhancer?.enabled = false
                        Timber.tag(TAG).d("setupLoudnessEnhancer: normalization disabled or mediaId unavailable")
                    }
                }
            } catch (e: Exception) {
                reportException(e)
                releaseLoudnessEnhancer()
            }
        }
    }

    private fun releaseLoudnessEnhancer() {
        try {
            loudnessEnhancer?.release()
            Timber.tag(TAG).d("LoudnessEnhancer released")
        } catch (e: Exception) {
            reportException(e)
            Timber.tag(TAG).e(e, "Error releasing LoudnessEnhancer: ${e.message}")
        } finally {
            loudnessEnhancer = null
        }
    }

    private fun openAudioEffectSession() {
        if (isAudioEffectSessionOpened) return
        isAudioEffectSessionOpened = true
        setupLoudnessEnhancer()
        sendBroadcast(
            Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, player.audioSessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
            },
        )
    }

    private fun closeAudioEffectSession() {
        if (!isAudioEffectSessionOpened) return
        isAudioEffectSessionOpened = false
        releaseLoudnessEnhancer()
        sendBroadcast(
            Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, player.audioSessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
            },
        )
    }

    private var previousMediaItemIndex = C.INDEX_UNSET

    private fun transitionReasonName(reason: Int): String = when (reason) {
        Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> "AUTO"
        Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT -> "REPEAT"
        Player.MEDIA_ITEM_TRANSITION_REASON_SEEK -> "SEEK"
        Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED -> "PLAYLIST_CHANGED"
        else -> "UNKNOWN($reason)"
    }

    private fun mediaSourceUri(mediaItem: MediaItem): String? =
        mediaItem.localConfiguration?.uri?.toString()

    /** True only when the source selected for this exact queue item is already a video route. */
    private fun hasCurrentVideoRoute(mediaItem: MediaItem): Boolean {
        val mediaId = mediaItem.mediaId
        return mediaId in videoSourceMediaIds &&
            videoPlaybackRoutes[mediaId] != null &&
            videoSourceUris[mediaId] == mediaSourceUri(mediaItem)
    }

    /**
     * Media3 can resolve an upcoming item while another item remains current. For a muxed source
     * that is fine because the source itself already contains video. A DASH route, however, needs
     * a main-thread source replacement and was previously discarded in that preloading window.
     * Recreate only a confirmed video item once, so AUTO, SEEK, REPEAT and shuffle use the same
     * resolver/install path as an explicit manual next. Unknown items keep their current audio
     * source until the resolver has confirmed OMV/UGC; otherwise an Art Track can be restarted
     * merely because its queue metadata arrived late.
     */
    private fun handleCurrentMediaChanged(mediaItem: MediaItem, reason: Int) {
        val mediaId = mediaItem.mediaId
        val index = player.currentMediaItemIndex
        val routeBefore = videoPlaybackRoutes[mediaId]?.name ?: "AUDIO_ONLY"
        val requestedBefore = videoPlaybackRequestedMediaId.value == mediaId
        val reasonName = transitionReasonName(reason)
        val action: String
        logVideoEligibility(mediaItem)

        if (index !in 0 until player.mediaItemCount || player.getMediaItemAt(index).mediaId != mediaId) {
            action = "SKIP_INVALID_CURRENT_ITEM"
        } else if (!shouldAttemptVideoFor(mediaItem)) {
            pendingCurrentVideoRouteActivation = null
            action = "KEEP_AUDIO_ONLY"
        } else if (!isConfirmedMusicVideoTrack(mediaItem)) {
            pendingCurrentVideoRouteActivation = null
            action = "KEEP_CURRENT_SOURCE_UNTIL_VIDEO_CONFIRMED"
        } else if (hasCurrentVideoRoute(mediaItem)) {
            pendingCurrentVideoRouteActivation = null
            videoPlaybackRequestedMediaId.value = mediaId
            if (videoPlaybackRoutes[mediaId] == VideoPlaybackRoute.MUXED) {
                maybeArmVideoStallWatchdog("transitionExistingMuxed")
            }
            action = "USE_EXISTING_${videoPlaybackRoutes[mediaId]}_SOURCE"
        } else if (pendingCurrentVideoRouteActivation?.let {
                it.mediaId == mediaId && it.mediaItemIndex == index
            } == true
        ) {
            action = "ROUTE_REBUILD_ALREADY_REQUESTED"
        } else {
            val position = safeCurrentPlaybackPosition()
            val shouldResume = player.playWhenReady
            val replacementId = videoRouteReplacementSequence.incrementAndGet()
            pendingCurrentVideoRouteActivation = CurrentVideoRouteActivation(
                mediaId = mediaId,
                mediaItemIndex = index,
                sourceUri = mediaSourceUri(mediaItem),
            )
            // The URI is only an opaque source-generation marker. mediaId, metadata and the
            // existing audio cache key remain stable, and the factory will still fail closed to
            // the normal Opus resolver when no supported video route exists.
            val routingItem = mediaItem.buildUpon()
                .setUri("video-route:$replacementId:$mediaId")
                .setCustomCacheKey(mediaId)
                .build()
            Timber.tag(TAG).i(
                "[VideoPlayback][transition] oldMediaId=${previousMediaItemIndex.takeIf { it in 0 until player.mediaItemCount }?.let { player.getMediaItemAt(it).mediaId } ?: "none"} " +
                    "newMediaId=$mediaId index=$index reason=$reasonName videoRequested=$requestedBefore " +
                    "routeBefore=$routeBefore action=REBUILD_CURRENT_ROUTE position=$position",
            )
            player.replaceMediaItem(index, routingItem)
            player.prepare()
            player.seekTo(index, position)
            player.playWhenReady = shouldResume
            return
        }

        Timber.tag(TAG).i(
            "[VideoPlayback][transition] oldMediaId=${previousMediaItemIndex.takeIf { it in 0 until player.mediaItemCount }?.let { player.getMediaItemAt(it).mediaId } ?: "none"} " +
                "newMediaId=$mediaId index=$index reason=$reasonName videoRequested=$requestedBefore " +
                "routeBefore=$routeBefore action=$action",
        )
    }

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        cancelVideoStallWatchdog("media item changed")
        if (mediaItem?.mediaId != videoAutoRetryMediaId ||
            player.currentMediaItemIndex != videoAutoRetryMediaItemIndex
        ) {
            resetVideoAutoRetry("media item changed")
        }
        if (mediaItem?.mediaId != audioOnlyFallbackMediaId) {
            audioOnlyFallbackMediaId = null
            audioOnlyFallbackStartedAtMs = null
        }
        // A newly created source may attempt video independently of the audio source. Do not let
        // an old first-frame signal keep a new song's artwork hidden.
        videoPlaybackRequestedMediaId.value = null
        videoPlaybackActiveMediaId.value = null
        maybeArmVideoStallWatchdog("mediaItemTransition")
        // Media3 may have already created upcoming sources before the user switches to Player V2,
        // disables video, or begins Cast. Convert such a pre-created merged source on arrival.
        if (mediaItem != null &&
            mediaItem.mediaId in videoSourceMediaIds &&
            !shouldAttemptVideoFor(mediaItem)
        ) {
            scope.launch {
                rebuildCurrentAsAudioOnly(
                    reason = "Current player mode does not support video",
                    permanentVideoFallback = false,
                    expectedMediaId = mediaItem.mediaId,
                )
            }
        }
        // Force Repeat One if the player ignored it and auto-advanced.
        var redirectedForRepeatOne = false
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
            if (previousMediaItemIndex != C.INDEX_UNSET && previousMediaItemIndex < player.mediaItemCount) {
                val finishedMediaId = player.getMediaItemAt(previousMediaItemIndex).mediaId
                scope.launch(Dispatchers.IO) { markCachedIfFullyDownloaded(finishedMediaId) }
            }

            val repeatMode = runBlocking { dataStore.get(RepeatModeKey, REPEAT_MODE_OFF) }
            if (repeatMode == REPEAT_MODE_ONE &&
                previousMediaItemIndex != C.INDEX_UNSET &&
                previousMediaItemIndex != player.currentMediaItemIndex) {

                player.seekTo(previousMediaItemIndex, 0)
                redirectedForRepeatOne = true
            }
        }
        // Do not resolve the transient next item while redirecting Repeat One. The following SEEK
        // transition for the actual repeated item enters this same common routing path.
        if (mediaItem != null && !redirectedForRepeatOne) {
            handleCurrentMediaChanged(mediaItem, reason)
        }
        previousMediaItemIndex = player.currentMediaItemIndex

        lastPlaybackSpeed = -1.0f // force update song

        setupLoudnessEnhancer()

        discordUpdateJob?.cancel()

        scrobbleManager?.onSongStop()
        if (player.playWhenReady && player.playbackState == Player.STATE_READY) {
            scrobbleManager?.onSongStart(player.currentMetadata, duration = player.duration)
        }

        // Sync Cast when media changes and Cast is connected
        // Skip if this change was triggered by Cast sync (to prevent loops)
        if (castConnectionHandler?.isCasting?.value == true &&
            castConnectionHandler?.isSyncingFromCast != true &&
            mediaItem != null) {
            val metadata = mediaItem.metadata
            if (metadata != null) {
                // Try to navigate to the item if it's already in Cast queue
                // This avoids a full reload which causes the widget to refresh
                val navigated = castConnectionHandler?.navigateToMediaIfInQueue(metadata.id) ?: false
                if (!navigated) {
                    // Item not in Cast queue, need to reload
                    castConnectionHandler?.loadMedia(metadata)
                }
            }
        }

        // Auto load more songs from queue
        if (dataStore.get(AutoLoadMoreKey, true) &&
            reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT &&
            player.mediaItemCount - player.currentMediaItemIndex <= 5 &&
            currentQueue.hasNextPage() &&
            !(dataStore.get(DisableLoadMoreWhenRepeatAllKey, false) && player.repeatMode == REPEAT_MODE_ALL)
        ) {
            scope.launch(SilentHandler) {
                val mediaItems = withContext(Dispatchers.IO) {
                    currentQueue.nextPage()
                        .filterExplicit(dataStore.get(HideExplicitKey, false))
                        .filterVideoSongs(dataStore.get(HideVideoSongsKey, false))
                }
                if (player.playbackState != STATE_IDLE && mediaItems.isNotEmpty()) {
                    player.addMediaItems(mediaItems)
                    if (player.shuffleModeEnabled) {
                        val shufflePlaylistFirst = dataStore.get(ShufflePlaylistFirstKey, false)
                        applyShuffleOrder(player.currentMediaItemIndex, player.mediaItemCount, shufflePlaylistFirst)
                    }
                }
            }
        }

        // Pre-resolve the next track's stream URL so the transition feels instant
        prefetchNextTrack()

        // Save state when media item changes
        if (dataStore.get(PersistentQueueKey, true)) {
            saveQueueToDisk()
        }
    }

    /**
     * Look-ahead stream prefetcher.
     *
     * Resolves the next queued track's signed CDN stream URL in the background and stores it
     * in [songUrlCache].  When ExoPlayer later transitions to that track, [createDataSourceFactory]
     * reads the cached URL instantly — eliminating the 500 ms–2 s network round-trip that would
     * otherwise cause an audible gap between songs.
     *
     * Only the **immediate next** track is prefetched to keep bandwidth usage minimal.  The job is
     * cancelled and restarted on every track change, so stale URLs from shuffle/queue edits are
     * automatically discarded.
     *
     * Note: this only resolves a small InnerTube JSON response (~10–30 KB).  No audio bytes are
     * downloaded here — that is handled by ExoPlayer's own CacheDataSource pipeline.
     */
    private fun prefetchNextTrack() {
        // Cancel any in-flight prefetch from the previous song
        prefetchJob?.cancel()

        val nextIndex = player.nextMediaItemIndex
        if (nextIndex == C.INDEX_UNSET) return  // no next track (end of queue / repeat-one)

        val nextMediaId = player.getMediaItemAt(nextIndex).mediaId

        // Nothing to do — URL is already cached and hasn't expired
        val cachedEntry = songUrlCache[nextMediaId]
        if (cachedEntry != null && cachedEntry.second > System.currentTimeMillis()) return

        prefetchJob = scope.launch(Dispatchers.IO + SilentHandler) {
            Timber.tag(TAG).d("[Prefetch] Resolving stream URL for next track: $nextMediaId")
            val result = runCatching {
                YTPlayerUtils.playerResponseForPlayback(
                    videoId = nextMediaId,
                    audioQuality = audioQuality,
                    connectivityManager = connectivityManager,
                    context = this@MusicService,
                )
            }
            result.getOrNull()?.getOrNull()?.let { playbackData ->
                // Only write to cache if the job wasn't cancelled while we were resolving
                if (isActive) {
                    songUrlCache[nextMediaId] =
                        playbackData.streamUrl to
                            System.currentTimeMillis() + (playbackData.streamExpiresInSeconds * 1000L)
                    Timber.tag(TAG).d("[Prefetch] Cached stream URL for $nextMediaId (expires in ${playbackData.streamExpiresInSeconds}s)")

                    playbackData.format?.let { format ->
                        val loudnessDb = playbackData.audioConfig?.loudnessDb
                        val perceptualLoudnessDb = playbackData.audioConfig?.perceptualLoudnessDb
                        database.query {
                            upsert(
                                FormatEntity(
                                    id = nextMediaId,
                                    itag = format.itag,
                                    mimeType = format.mimeType.split(";")[0],
                                    codecs = format.mimeType.split("codecs=").getOrNull(1)?.removeSurrounding("\"") ?: "mp3",
                                    bitrate = format.bitrate,
                                    sampleRate = format.audioSampleRate,
                                    contentLength = format.contentLength ?: 0L,
                                    loudnessDb = loudnessDb,
                                    perceptualLoudnessDb = perceptualLoudnessDb,
                                    playbackUrl = playbackData.playbackTracking?.videostatsPlaybackUrl?.baseUrl
                                )
                            )
                        }
                    }
                }
            } ?: Timber.tag(TAG).d("[Prefetch] Could not resolve stream URL for $nextMediaId — will resolve on demand")
        }
    }

    override fun onPlaybackStateChanged(
        @Player.State playbackState: Int,
    ) {
        when (playbackState) {
            Player.STATE_BUFFERING -> {
                maybeArmVideoStallWatchdog("playbackState")
                logVideoPlaybackTimeline("buffering")
            }
            Player.STATE_READY, Player.STATE_IDLE, Player.STATE_ENDED ->
                cancelVideoStallWatchdog("player state=$playbackState")
        }
        // Force Repeat All if the player ignored it and ended playback
        if (playbackState == Player.STATE_ENDED) {
            val repeatMode = runBlocking { dataStore.get(RepeatModeKey, REPEAT_MODE_OFF) }
            if (repeatMode == REPEAT_MODE_ALL && player.mediaItemCount > 0) {
                player.seekTo(0, 0)
                player.prepare()
                player.play()
            }
        }

        // Save state when playback state changes (but not during silence skipping)
        if (dataStore.get(PersistentQueueKey, true) && !isSilenceSkipping) {
            saveQueueToDisk()
        }

        if (playbackState == Player.STATE_READY) {
            logAudioOnlyFallbackState("READY")
            logVideoAutoRetrySuccessIfNeeded()
            consecutivePlaybackErr = 0
            retryCount = 0
            waitingForNetworkConnection.value = false
            retryJob?.cancel()

            // Reset retry count for current song on successful playback
            player.currentMediaItem?.mediaId?.let { mediaId ->
                resetRetryCount(mediaId)
                Timber.tag(TAG).d("Playback successful for $mediaId, reset retry count")
            }
            scheduleCrossfade()
        }

        if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
            scrobbleManager?.onSongStop()
        }
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        if (!playWhenReady) {
            cancelVideoStallWatchdog("playback paused")
        } else if (player.playbackState == Player.STATE_BUFFERING) {
            maybeArmVideoStallWatchdog("playWhenReady")
        }
        // Safety net: if local player tries to start while casting, immediately pause it
        if (playWhenReady && castConnectionHandler?.isCasting?.value == true) {
            player.pause()
            return
        }

        if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) {
            if (playWhenReady) {
                isPausedByVolumeMute = false
            }

            if (!playWhenReady && !isPausedByVolumeMute) {
                wasPlayingBeforeVolumeMute = false
            }
        }

        if (playWhenReady) {
            setupLoudnessEnhancer()
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) {
            logAudioOnlyFallbackState("PLAYING")
            audioOnlyFallbackMediaId = null
            audioOnlyFallbackStartedAtMs = null
        }
    }

    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        logVideoPlaybackTimeline("timelineChanged")
    }

    override fun onTracksChanged(tracks: Tracks) {
        logVideoPlaybackTracks()
        val item = player.currentMediaItem ?: return
        val hasVideo = tracks.groups.any { it.type == C.TRACK_TYPE_VIDEO && it.isSelected }
        if (hasVideo && item.mediaId in videoSourceMediaIds && shouldAttemptVideoFor(item)) {
            videoPlaybackRequestedMediaId.value = item.mediaId
        }
        val selectedVideo = tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_VIDEO }
            ?.let { group -> (0 until group.length).firstOrNull(group::isTrackSelected)?.let(group::getTrackFormat) }
        val selectedAudio = tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_AUDIO }
            ?.let { group -> (0 until group.length).firstOrNull(group::isTrackSelected)?.let(group::getTrackFormat) }
        if (hasVideo && videoPlaybackRoutes[item.mediaId] == VideoPlaybackRoute.DASH &&
            selectedVideo != null && selectedAudio != null
        ) {
            Timber.tag(TAG).i(
                "[VideoPlayback][route] mediaId=${item.mediaId} " +
                    "musicVideoType=${resolvedMusicVideoTypes[item.mediaId] ?: item.metadata?.musicVideoType ?: "unknown"} " +
                    "route=SINGLE_DASH_VIDEO videoMime=${selectedVideo.sampleMimeType} " +
                    "videoCodec=${selectedVideo.codecs ?: "unknown"} audioMime=${selectedAudio.sampleMimeType} " +
                    "audioCodec=${selectedAudio.codecs ?: "unknown"}",
            )
        }
        tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }.forEach { group ->
            for (index in 0 until group.length) {
                if (!group.isTrackSelected(index)) continue
                val format = group.getTrackFormat(index)
                val opus = format.sampleMimeType == "audio/opus"
                val route = if (hasVideo && videoPlaybackRoutes[item.mediaId] == VideoPlaybackRoute.DASH) {
                    "SINGLE_DASH_VIDEO"
                } else if (hasVideo) "SINGLE_MUXED_VIDEO"
                    else if (opus) "AUDIO_ONLY_OPUS" else "AUDIO_ONLY"
                Timber.tag(TAG).i(
                    "[VideoPlayback][route] mediaId=${item.mediaId} " +
                        "musicVideoType=${resolvedMusicVideoTypes[item.mediaId] ?: item.metadata?.musicVideoType ?: "unknown"} " +
                        "route=$route audioMime=${format.sampleMimeType} " +
                        "audioCodec=${format.codecs ?: if (opus) "opus" else "unknown"} audioBitrate=${format.bitrate}",
                )
            }
        }
    }

    override fun onEvents(
        player: Player,
        events: Player.Events,
    ) {
        if (events.containsAny(
                Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_PLAY_WHEN_READY_CHANGED
            )
        ) {
            scheduleCrossfade()
            val isBufferingOrReady =
                player.playbackState == Player.STATE_BUFFERING || player.playbackState == Player.STATE_READY
            if (isBufferingOrReady && player.playWhenReady) {
                val focusGranted = requestAudioFocus()
                if (focusGranted) {
                    openAudioEffectSession()
                }
            } else {
                closeAudioEffectSession()
            }
        }
        if (events.containsAny(EVENT_TIMELINE_CHANGED, EVENT_POSITION_DISCONTINUITY)) {
            currentMediaMetadata.value = player.currentMetadata
        }

        // Widget and Discord RPC updates
        if (events.containsAny(Player.EVENT_IS_PLAYING_CHANGED)) {
            updateWidgetUI(player.isPlaying)
            if (player.isPlaying) {
                startWidgetUpdates()
            } else {
                stopWidgetUpdates()
            }
            if (!player.isPlaying && !events.containsAny(Player.EVENT_POSITION_DISCONTINUITY, Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                scope.launch {
                    discordRpc?.close()
                }
            }
        }

        // Update Discord RPC when media item changes or playback starts
        if (events.containsAny(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_IS_PLAYING_CHANGED) && player.isPlaying) {
            val mediaId = player.currentMetadata?.id
            if (mediaId != null) {
                scope.launch {
                    // Fetch song from database to get full info
                    database.song(mediaId).first()?.let { song ->
                        updateDiscordRPC(song)
                    }
                }
            }
        }

        // Scrobbling
        if (events.containsAny(Player.EVENT_IS_PLAYING_CHANGED)) {
            scrobbleManager?.onPlayerStateChanged(player.isPlaying, player.currentMetadata, duration = player.duration)
        }

    }

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        updateNotification()
        if (shuffleModeEnabled) {
            // If queue is empty, don't shuffle
            if (player.mediaItemCount == 0) return

            val shufflePlaylistFirst = dataStore.get(ShufflePlaylistFirstKey, false)
            val currentIndex = player.currentMediaItemIndex
            val totalCount = player.mediaItemCount

            applyShuffleOrder(currentIndex, totalCount, shufflePlaylistFirst)
        }

        // Save shuffle mode to preferences
        if (dataStore.get(RememberShuffleAndRepeatKey, true)) {
            scope.launch {
                dataStore.edit { settings ->
                    settings[ShuffleModeKey] = shuffleModeEnabled
                }
            }
        }

        // Save state when shuffle mode changes
        if (dataStore.get(PersistentQueueKey, true)) {
            saveQueueToDisk()
        }
    }

    override fun onRepeatModeChanged(repeatMode: Int) {
        updateNotification()
        scope.launch {
            dataStore.edit { settings ->
                settings[RepeatModeKey] = repeatMode
            }
        }

        // Save state when repeat mode changes
        if (dataStore.get(PersistentQueueKey, true)) {
            saveQueueToDisk()
        }
    }

    /**
     * Reads the primary player's currently-active shuffle traversal order
     * straight from its [Timeline] (using getNext/getPreviousWindowIndex
     * with shuffle enabled, the same trick [playNext] uses) and returns it as an
     * explicit index array reproducing the full active traversal order, with
     * the current item at its real position (not necessarily first).
     *
     * ExoPlayer has no public getter for the live [ShuffleOrder], so this is how
     * we capture it just before a crossfade player swap in order to replay the
     * *same* order on the secondary player — instead of calling
     * [applyShuffleOrder], which regenerates a fresh random order and was the
     * cause of shuffle re-randomizing on every skip / song selection (and of
     * already-played tracks reappearing).
     */
    private fun getCurrentShuffleOrderIndices(sourcePlayer: Player): IntArray? {
        val timeline = sourcePlayer.currentTimeline
        if (timeline.isEmpty) return null
        val currentIndex = sourcePlayer.currentMediaItemIndex
        if (currentIndex == C.INDEX_UNSET) return null

        val before = mutableListOf<Int>()
        var prev = currentIndex
        while (true) {
            prev = timeline.getPreviousWindowIndex(prev, Player.REPEAT_MODE_OFF, true)
            if (prev == C.INDEX_UNSET) break
            before.add(prev)
        }
        before.reverse()

        val after = mutableListOf<Int>()
        var next = currentIndex
        while (true) {
            next = timeline.getNextWindowIndex(next, Player.REPEAT_MODE_OFF, true)
            if (next == C.INDEX_UNSET) break
            after.add(next)
        }

        return (before + currentIndex + after).toIntArray()
    }

    /**
     * Applies a new shuffle order to the player, maintaining the current item's position.
     * If `shufflePlaylistFirst` is true, it attempts to shuffle original items separately from added items.
     */
    private fun applyShuffleOrder(
        currentIndex: Int,
        totalCount: Int,
        shufflePlaylistFirst: Boolean
    ) {
        if (totalCount == 0) return

        if (shufflePlaylistFirst && originalQueueSize > 0 && originalQueueSize < totalCount) {
            // Shuffle original items and added items separately
            val originalIndices = (0 until originalQueueSize).filter { it != currentIndex }.toMutableList()
            val addedIndices = (originalQueueSize until totalCount).filter { it != currentIndex }.toMutableList()

            originalIndices.shuffle()
            addedIndices.shuffle()

            val shuffledIndices = IntArray(totalCount)
            var pos = 0
            shuffledIndices[pos++] = currentIndex

            if (currentIndex < originalQueueSize) {
                originalIndices.forEach { shuffledIndices[pos++] = it }
                addedIndices.forEach { shuffledIndices[pos++] = it }
            } else {
                (0 until originalQueueSize).shuffled().forEach { shuffledIndices[pos++] = it }
                addedIndices.forEach { shuffledIndices[pos++] = it }
            }
            player.setShuffleOrder(DefaultShuffleOrder(shuffledIndices, System.currentTimeMillis()))
        } else {
            val shuffledIndices = IntArray(totalCount) { it }
            shuffledIndices.shuffle()
            // Ensure current item is first in the shuffle order
            val currentItemIndexInShuffled = shuffledIndices.indexOf(currentIndex)
            if (currentItemIndexInShuffled != -1) { // Should always be true if totalCount > 0
                val temp = shuffledIndices[0]
                shuffledIndices[0] = shuffledIndices[currentItemIndexInShuffled]
                shuffledIndices[currentItemIndexInShuffled] = temp
            }
            player.setShuffleOrder(DefaultShuffleOrder(shuffledIndices, System.currentTimeMillis()))
        }
    }

    override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
        super.onPlaybackParametersChanged(playbackParameters)
        if (playbackParameters.speed != lastPlaybackSpeed) {
            lastPlaybackSpeed = playbackParameters.speed
            discordUpdateJob?.cancel()

            // update scheduling thingy
            discordUpdateJob = scope.launch {
                delay(1000)
                if (player.playWhenReady && player.playbackState == Player.STATE_READY) {
                    currentSong.value?.let { song ->
                        updateDiscordRPC(song)
                    }
                }
            }
        }
    }

    /**
     * Extracts the HTTP response code from an error's cause chain.
     * Returns null if no HTTP response code is found.
     */
    private fun getHttpResponseCode(error: PlaybackException): Int? {
        var cause: Throwable? = error.cause
        while (cause != null) {
            if (cause is HttpDataSource.InvalidResponseCodeException) {
                return cause.responseCode
            }
            cause = cause.cause
        }
        return null
    }

    /**
     * Checks if the error is caused by an expired/forbidden URL (HTTP 403).
     * This typically happens when a YouTube stream URL expires.
     */
    private fun isExpiredUrlError(error: PlaybackException): Boolean {
        val responseCode = getHttpResponseCode(error)
        return responseCode == 403
    }

    /**
     * Checks if the error is a Range Not Satisfiable error (HTTP 416).
     * This happens when cached data doesn't match the actual stream size.
     */
    private fun isRangeNotSatisfiableError(error: PlaybackException): Boolean {
        val responseCode = getHttpResponseCode(error)
        return responseCode == 416
    }

    /**
     * Checks if the error is a "page needs to be reloaded" error.
     * This is a YouTube-specific error that requires refreshing the stream.
     */
    private fun isPageReloadError(error: PlaybackException): Boolean {
        val errorMessage = error.message?.lowercase() ?: ""
        val causeMessage = error.cause?.message?.lowercase() ?: ""
        val innerCauseMessage = error.cause?.cause?.message?.lowercase() ?: ""

        val reloadKeywords = listOf(
            "page needs to be reloaded",
            "pagina deve essere ricaricata",
            "la pagina deve essere ricaricata",
            "page must be reloaded",
            "reload",
            "ricaricata"
        )

        return reloadKeywords.any { keyword ->
            errorMessage.contains(keyword) ||
            causeMessage.contains(keyword) ||
            innerCauseMessage.contains(keyword)
        }
    }

    private fun isNetworkRelatedError(error: PlaybackException): Boolean {
        // Don't treat specific errors as network errors - they need special handling
        if (isExpiredUrlError(error) || isRangeNotSatisfiableError(error) || isPageReloadError(error)) {
            return false
        }
        return error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE ||
                error.cause is java.net.ConnectException ||
                error.cause is java.net.UnknownHostException ||
                (error.cause as? PlaybackException)?.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
    }

    /**
     * Checks if the error is caused by AudioTrack write or initialization failures.
     * These errors indicate the audio renderer is in a corrupted/invalid state.
     */
    private fun isAudioRendererError(error: PlaybackException): Boolean {
        return error.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED ||
                (error.cause as? PlaybackException)?.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED ||
                (error.cause as? PlaybackException)?.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED
    }

    override fun onPlayerError(error: PlaybackException) {
        super.onPlayerError(error)

        // Safety check : ensuring player is still initialized
        if (!playerInitialized.value) {
            Timber.tag(TAG).e(error, "Player error occurred but player not initialized")
            return
        }

        val mediaId = player.currentMediaItem?.mediaId
        Timber.tag(TAG).w(error, "Player error occurred for $mediaId: errorCode=${error.errorCode}, message=${error.message}")

        // Keep automatic-retry diagnostics complete without broadening the video-failure
        // classification below. A player error remains eligible for fallback only through
        // isVideoOnlyFailure, so audio errors still follow the existing recovery policy.
        if (mediaId != null && isCurrentAutoRetryConsumed(mediaId) && !videoAutoRetrySucceeded) {
            Timber.tag(TAG).w(
                "[VideoPlayback][autoRetry] retry failed mediaId=$mediaId attempt=1 " +
                    "reason=PLAYER_ERROR errorCode=${error.errorCodeName}",
            )
        }

        // MergingMediaSource reports child failures through the primary player. Before the normal
        // audio recovery policy can clear caches, retry this one item without its video child.
        // This preserves the queue, MediaSession and audio-only resolver unchanged.
        if (mediaId != null && isVideoOnlyFailure(mediaId, error)) {
            if (fallbackToAudioOnly(mediaId, error)) return
        }
        reportException(error)

        // Check if this song has failed too many times
        if (mediaId != null && hasExceededRetryLimit(mediaId)) {
            Timber.tag(TAG).w("Song $mediaId has exceeded retry limit, skipping")
            markSongAsFailed(mediaId)
            handleFinalFailure()
            return
        }

        // Aggressive cache clearing for all playback errors
        if (mediaId != null) {
            performAggressiveCacheClear(mediaId)
        }

        // Handle specific error types with strict strategies
        when {
            isAudioRendererError(error) -> {
                Timber.tag(TAG).d("AudioTrack error detected (${error.errorCode}), performing safe recovery")
                handleAudioRendererError(mediaId)
                return
            }
            isRangeNotSatisfiableError(error) -> {
                Timber.tag(TAG).d("Range Not Satisfiable (416) detected, performing strict recovery")
                handleRangeNotSatisfiableError(mediaId)
                return
            }
            isPageReloadError(error) -> {
                Timber.tag(TAG).d("Page reload error detected, performing strict recovery")
                handlePageReloadError(mediaId)
                return
            }
            isExpiredUrlError(error) -> {
                Timber.tag(TAG).d("Expired URL (403) detected, refreshing stream URL")
                handleExpiredUrlError(mediaId)
                return
            }

            !isNetworkConnected.value || isNetworkRelatedError(error) -> {
                Timber.tag(TAG).d("Network-related error detected, waiting for connection")
                waitOnNetworkError()
                return
            }
        }

        // For IO_UNSPECIFIED and IO_BAD_HTTP_STATUS, try recovery first
        if (error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED ||
            error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS) {
            Timber.tag(TAG).d("IO error detected (${error.errorCode}), attempting recovery")
            handleGenericIOError(mediaId)
            return
        }

        // Final fallback
        if (dataStore.get(AutoSkipNextOnErrorKey, false)) {
            Timber.tag(TAG).d("Auto-skipping to next track due to unrecoverable error")
            skipOnError()
        } else {
            Timber.tag(TAG).d("Stopping playback due to unrecoverable error")
            stopOnError()
        }
    }

    /**
     * Performs aggressive cache clearing for a media item.
     * Clears both player cache and download cache, plus URL cache.
     */
    private fun performAggressiveCacheClear(mediaId: String) {
        Timber.tag(TAG).d("Performing aggressive cache clear for $mediaId")

        // Clear URL cache
        songUrlCache.remove(mediaId)

        // Clear player cache
        try {
            playerCache.removeResource(mediaId)
            Timber.tag(TAG).d("Cleared player cache for $mediaId")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to clear player cache for $mediaId")
        }


    }

    /**
     * Stops the player, clears the current audio cache/URL entry, then re-prepares from zero.
     * The video source is uncached; prepare causes its existing resolving data source to reopen.
     */
    fun retryCurrentStream() {
        val mediaId = player.currentMediaItem?.mediaId ?: return
        restartCurrentStream(mediaId, clearAudioCache = true, resetVideoDiagnostics = false)
    }

    /**
     * Shared stop/prepare restart path. The automatic video retry deliberately leaves the audio
     * cache and URL cache intact, so it only asks the uncached video child to make a new request.
     */
    private fun restartCurrentStream(
        expectedMediaId: String,
        clearAudioCache: Boolean,
        resetVideoDiagnostics: Boolean,
        resumePositionMs: Long = 0L,
    ): Boolean {
        if (player.currentMediaItem?.mediaId != expectedMediaId) return false
        cancelVideoStallWatchdog("stream restart")
        player.stop()
        if (clearAudioCache) {
            runBlocking(Dispatchers.IO) { performAggressiveCacheClear(expectedMediaId) }
        }
        if (resetVideoDiagnostics) {
            videoSourceInstanceIds[expectedMediaId]?.let { sourceInstanceId ->
                videoStreamDiagnostics[sourceInstanceId] = VideoStreamDiagnostics(
                    sourceInstanceId = sourceInstanceId,
                    mediaId = expectedMediaId,
                )
            }
            if (videoPlaybackActiveMediaId.value == expectedMediaId) {
                videoPlaybackActiveMediaId.value = null
            }
        }
        if (player.currentMediaItem?.mediaId != expectedMediaId) return false
        player.seekTo(resumePositionMs.coerceAtLeast(0L))
        player.prepare()
        player.play()
        return true
    }

    /**
     * Checks if a song has exceeded the retry limit.
     */
    private fun hasExceededRetryLimit(mediaId: String): Boolean {
        val currentRetries = currentMediaIdRetryCount[mediaId] ?: 0
        return currentRetries >= MAX_RETRY_PER_SONG
    }

    /**
     * Increments the retry count for a song.
     */
    private fun incrementRetryCount(mediaId: String) {
        val currentRetries = currentMediaIdRetryCount[mediaId] ?: 0
        currentMediaIdRetryCount[mediaId] = currentRetries + 1
        Timber.tag(TAG).d("Retry count for $mediaId: ${currentRetries + 1}/$MAX_RETRY_PER_SONG")
    }

    /**
     * Resets the retry count for a song (called on successful playback).
     */
    private fun resetRetryCount(mediaId: String) {
        currentMediaIdRetryCount.remove(mediaId)
        recentlyFailedSongs.remove(mediaId)
    }

    /**
     * Marks a song as failed to prevent further retry attempts.
     */
    private fun markSongAsFailed(mediaId: String) {
        recentlyFailedSongs.add(mediaId)
        currentMediaIdRetryCount.remove(mediaId)

        // Schedule cleanup of failed songs list after 5 minutes
        failedSongsClearJob?.cancel()
        failedSongsClearJob = scope.launch {
            delay(5 * 60 * 1000L) // 5 minutes
            recentlyFailedSongs.clear()
            Timber.tag(TAG).d("Cleared recently failed songs list")
        }
    }

    /**
     * Handles AudioTrack errors (write failed, init failed) with safe recovery.
     * These errors indicate the audio renderer is corrupted and needs careful reset.
     */
    private fun handleAudioRendererError(mediaId: String?) {
        if (mediaId == null) {
            handleFinalFailure()
            return
        }

        incrementRetryCount(mediaId)

        retryJob?.cancel()
        retryJob = scope.launch {
            try {
                // Pause playback immediately to stop the renderer
                player.pause()
                Timber.tag(TAG).d("Paused playback due to AudioTrack error")

                // Wait longer for audio renderer to settle before retry
                // This prevents the renderer from continuing to fail in a loop
                delay(RETRY_DELAY_MS * 3) // 3 seconds instead of 1 second

                // Check if player is still initialized before attempting recovery
                if (!playerInitialized.value) {
                    Timber.tag(TAG).w("Player no longer initialized, aborting AudioTrack recovery")
                    return@launch
                }

                val currentIndex = player.currentMediaItemIndex
                if (currentIndex != C.INDEX_UNSET) {
                    // Seek to current position to force a clean audio renderer reinit
                    val currentPosition = player.currentPosition
                    player.seekTo(currentIndex, currentPosition)
                    player.prepare()

                    Timber.tag(TAG).d("Retrying playback for $mediaId after AudioTrack error")

                    // Resume playback if it wasn't paused by user
                    if (wasPlayingBeforeAudioFocusLoss) {
                        delay(500) // Brief delay to allow renderer to be ready
                        if (hasAudioFocus && playerInitialized.value) {
                            if (castConnectionHandler?.isCasting?.value != true) {
                                player.play()
                            }
                        }
                    }
                } else {
                    Timber.tag(TAG).w("Invalid media item index during AudioTrack recovery")
                    handleFinalFailure()
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error during AudioTrack error recovery")
                handleFinalFailure()
            }
        }
    }

    /**
     * Handles Range Not Satisfiable (416) errors with strict recovery.
     * This error occurs when cached data doesn't match the actual stream size.
     */
    private fun handleRangeNotSatisfiableError(mediaId: String?) {
        if (mediaId == null) {
            handleFinalFailure()
            return
        }

        incrementRetryCount(mediaId)

        retryJob?.cancel()
        retryJob = scope.launch {
            // Clear all caches aggressively
            performAggressiveCacheClear(mediaId)

            // Wait before retry
            delay(RETRY_DELAY_MS)

            // Force re-prepare from position 0 to avoid range issues
            val currentIndex = player.currentMediaItemIndex
            player.seekTo(currentIndex, 0)
            player.prepare()

            Timber.tag(TAG).d("Retrying playback for $mediaId after 416 error (from position 0)")
        }
    }

    /**
     * Handles "page needs to be reloaded" errors with strict recovery.
     * This requires clearing decryption caches and getting fresh stream URLs.
     */
    private fun handlePageReloadError(mediaId: String?) {
        if (mediaId == null) {
            handleFinalFailure()
            return
        }

        incrementRetryCount(mediaId)

        retryJob?.cancel()
        retryJob = scope.launch {
            Timber.tag(TAG).d("Handling page reload error for $mediaId")

            // Clear all caches including decryption caches
            performAggressiveCacheClear(mediaId)

            // Short delay - the cipher JS is cached after the first attempt, 
            // so the second attempt resolves quickly.
            delay(RETRY_DELAY_MS)

            // Re-prepare the player
            val currentPosition = player.currentPosition
            val currentIndex = player.currentMediaItemIndex
            player.seekTo(currentIndex, currentPosition)
            player.prepare()

            Timber.tag(TAG).d("Retrying playback for $mediaId after page reload error")
        }
    }

    /**
     * Handles expired URL (403) errors by clearing caches and retrying.
     */
    private fun handleExpiredUrlError(mediaId: String?) {
        if (mediaId == null) {
            handleFinalFailure()
            return
        }

        incrementRetryCount(mediaId)

        // Clear the cached URL
        songUrlCache.remove(mediaId)
        Timber.tag(TAG).d("Cleared cached URL for $mediaId")



        retryJob?.cancel()
        retryJob = scope.launch {
            delay(RETRY_DELAY_MS)

            // Seek to current position to force URL re-resolution
            val currentPosition = player.currentPosition
            val currentIndex = player.currentMediaItemIndex
            player.seekTo(currentIndex, currentPosition)
            player.prepare()

            Timber.tag(TAG).d("Retrying playback for $mediaId after 403 error")
        }
    }

    /**
     * Handles generic IO errors with recovery attempt.
     */
    private fun handleGenericIOError(mediaId: String?) {
        if (mediaId == null) {
            handleFinalFailure()
            return
        }

        incrementRetryCount(mediaId)

        retryJob?.cancel()
        retryJob = scope.launch {
            performAggressiveCacheClear(mediaId)
            delay(RETRY_DELAY_MS)

            val currentPosition = player.currentPosition
            val currentIndex = player.currentMediaItemIndex
            player.seekTo(currentIndex, currentPosition)
            player.prepare()

            Timber.tag(TAG).d("Retrying playback for $mediaId after generic IO error")
        }
    }

    /**
     * Handles final failure when all recovery attempts have been exhausted.
     */
    private fun handleFinalFailure() {
        if (dataStore.get(AutoSkipNextOnErrorKey, false)) {
            Timber.tag(TAG).d("All recovery attempts exhausted, auto-skipping to next track")
            skipOnError()
        } else {
            Timber.tag(TAG).d("All recovery attempts exhausted, stopping playback")
            stopOnError()
        }
    }

    override fun onDeviceVolumeChanged(volume: Int, muted: Boolean) {
        super.onDeviceVolumeChanged(volume, muted)
        val pauseOnMute = dataStore.get(PauseOnMute, false)

        if ((volume == 0 || muted) && pauseOnMute) {
            if (player.isPlaying) {
                wasPlayingBeforeVolumeMute = true
                isPausedByVolumeMute = true
                player.pause()
            }
        } else if (volume > 0 && !muted && pauseOnMute) {
            if (wasPlayingBeforeVolumeMute && !player.isPlaying && castConnectionHandler?.isCasting?.value != true) {
                wasPlayingBeforeVolumeMute = false
                isPausedByVolumeMute = false
                player.play()
            }
        }
    }

    private fun createCacheDataSource(): CacheDataSource.Factory =
        CacheDataSource
            .Factory()
            .setCache(downloadCache)
            .setUpstreamDataSourceFactory(
                CacheDataSource
                    .Factory()
                    .setCache(playerCache)
                    .setUpstreamDataSourceFactory(
                        DefaultDataSource.Factory(
                            this,
                            OkHttpDataSource.Factory(
                                OkHttpClient
                                    .Builder()
                                    .dns(object : Dns {
                                        override fun lookup(hostname: String): List<InetAddress> {
                                            val addresses = Dns.SYSTEM.lookup(hostname)
                                            return when (this@MusicService.ipVersion) {
                                                IpVersion.IPV4 -> addresses.filter { it is Inet4Address }.ifEmpty { addresses }
                                                IpVersion.IPV6 -> addresses.filter { it is Inet6Address }.ifEmpty { addresses }
                                                IpVersion.AUTO -> addresses
                                            }
                                        }
                                    })
                                    .proxy(YouTube.proxy)
                                    .proxyAuthenticator { _, response ->
                                        YouTube.proxyAuth?.let { auth ->
                                            response.request.newBuilder()
                                                .header("Proxy-Authorization", auth)
                                                .build()
                                        } ?: response.request
                                    }
                                    .build(),
                            ),
                        ),
                    ),
            ).setCacheWriteDataSinkFactory(null)
            .setFlags(FLAG_IGNORE_CACHE_ON_ERROR)

    // Flag to prevent queue saving during silence skip operations
    private var isSilenceSkipping = false

    private fun handleLongSilenceDetected() {
        if (!instantSilenceSkipEnabled.value) return
        if (silenceSkipJob?.isActive == true) return

        silenceSkipJob = scope.launch {
            // Debounce so short fades or transitions do not trigger a jump.
            delay(200)
            performInstantSilenceSkip()
        }
    }

    private suspend fun performInstantSilenceSkip() {
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: return
        if (duration <= INSTANT_SILENCE_SKIP_STEP_MS) return

        isSilenceSkipping = true
        try {
            var hops = 0
            val silenceProcessor = playerSilenceProcessors[player] ?: return
            while (coroutineContext.isActive && instantSilenceSkipEnabled.value && silenceProcessor.isCurrentlySilent()) {
                val current = player.currentPosition
                val target = (current + INSTANT_SILENCE_SKIP_STEP_MS).coerceAtMost(duration - 500)

                if (target <= current) break

                // Reset silence tracking before seeking to prevent immediate re-trigger
                silenceProcessor.resetTracking()
                player.seekTo(target)
                hops++

                if (hops >= 80 || target >= duration - 500) break

                delay(INSTANT_SILENCE_SKIP_SETTLE_MS)
            }
            if (hops > 0) {
                Timber.tag(TAG).d("Silence skip: jumped $hops times")
            }
        } finally {
            isSilenceSkipping = false
        }
    }

    private fun updateDiscordRPC(song: Song, showFeedback: Boolean = false) {
        val useDetails = dataStore.get(DiscordUseDetailsKey, false)
        val advancedMode = dataStore.get(DiscordAdvancedModeKey, false)

        val status = if (advancedMode) dataStore.get(DiscordStatusKey, "online") else "online"
        val b1Text = if (advancedMode) dataStore.get(DiscordButton1TextKey, "") else ""
        val b1Visible = if (advancedMode) dataStore.get(DiscordButton1VisibleKey, true) else true
        val b2Text = if (advancedMode) dataStore.get(DiscordButton2TextKey, "") else ""
        val b2Visible = if (advancedMode) dataStore.get(DiscordButton2VisibleKey, true) else true
        val activityType = if (advancedMode) dataStore.get(DiscordActivityTypeKey, "listening") else "listening"
        val activityName = if (advancedMode) dataStore.get(DiscordActivityNameKey, "") else ""

        discordUpdateJob?.cancel()
        discordUpdateJob = scope.launch {
            discordRpc?.updateSong(
                song,
                player.currentPosition,
                player.playbackParameters.speed,
                useDetails,
                status,
                b1Text,
                b1Visible,
                b2Text,
                b2Visible,
                activityType,
                activityName
            )?.onFailure {
                // Rate limited or error
                if (showFeedback) {
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(this@MusicService, "Discord RPC update failed: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun createDataSourceFactory(): DataSource.Factory {
        return ResolvingDataSource.Factory(createCacheDataSource()) { dataSpec ->
            val mediaId = dataSpec.key ?: error("No media id")

            // Check if we need to bypass cache for quality change
            val shouldBypassCache = bypassCacheForQualityChange.contains(mediaId)

            if (!shouldBypassCache) {
                val contentLength = runBlocking(Dispatchers.IO) {
                    database.song(mediaId).first()?.format?.contentLength
                }
                val requiredLength = when {
                    dataSpec.length >= 0 -> dataSpec.length
                    contentLength != null -> (contentLength - dataSpec.position).coerceAtLeast(1)
                    else -> CHUNK_LENGTH
                }

                if (downloadCache.isCached(mediaId, dataSpec.position, requiredLength) ||
                    playerCache.isCached(mediaId, dataSpec.position, CHUNK_LENGTH)
                ) {
                    scope.launch(Dispatchers.IO) { recoverSong(mediaId) }
                    return@Factory dataSpec
                }

                songUrlCache[mediaId]?.takeIf { it.second > System.currentTimeMillis() }?.let {
                    scope.launch(Dispatchers.IO) { recoverSong(mediaId) }
                    return@Factory dataSpec.withUri(it.first.toUri())
                }
            } else {
                Timber.tag("MusicService").i("BYPASSING CACHE for $mediaId due to quality change")
            }

            Timber.tag("MusicService").i("FETCHING STREAM: $mediaId | quality=$audioQuality")
            val playbackData = runBlocking(Dispatchers.IO) {
                YTPlayerUtils.playerResponseForPlayback(
                    mediaId,
                    audioQuality = audioQuality,
                    connectivityManager = connectivityManager,
                    context = this@MusicService,
                )
            }.getOrElse { throwable ->
                when (throwable) {
                    is PlaybackException -> throw throwable

                    is java.net.ConnectException, is java.net.UnknownHostException -> {
                        throw PlaybackException(
                            getString(R.string.error_no_internet),
                            throwable,
                            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
                        )
                    }

                    is java.net.SocketTimeoutException -> {
                        throw PlaybackException(
                            getString(R.string.error_timeout),
                            throwable,
                            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
                        )
                    }

                    else -> throw PlaybackException(
                        getString(R.string.error_unknown),
                        throwable,
                        PlaybackException.ERROR_CODE_REMOTE_ERROR
                    )
                }
            }

            val nonNullPlayback = requireNotNull(playbackData) {
                getString(R.string.error_unknown)
            }
            run {
                val format = nonNullPlayback.format
                val loudnessDb = nonNullPlayback.audioConfig?.loudnessDb
                val perceptualLoudnessDb = nonNullPlayback.audioConfig?.perceptualLoudnessDb

                Timber.tag(TAG).d("Storing format for $mediaId with loudnessDb: $loudnessDb, perceptualLoudnessDb: $perceptualLoudnessDb")
                if (loudnessDb == null && perceptualLoudnessDb == null) {
                    Timber.tag(TAG).w("No loudness data available from YouTube for video: $mediaId")
                }

                database.query {
                    upsert(
                        FormatEntity(
                            id = mediaId,
                            itag = format.itag,
                            mimeType = format.mimeType.split(";")[0],
                            codecs = format.mimeType.split("codecs=").getOrNull(1)?.removeSurrounding("\"") ?: "mp3",
                            bitrate = format.bitrate,
                            sampleRate = format.audioSampleRate,
                            contentLength = format.contentLength ?: 0L,
                            loudnessDb = loudnessDb,
                            perceptualLoudnessDb = perceptualLoudnessDb,
                            playbackUrl = nonNullPlayback.playbackTracking?.videostatsPlaybackUrl?.baseUrl
                        )
                    )
                }
                scope.launch(Dispatchers.IO) { recoverSong(mediaId, nonNullPlayback) }

                // Clear bypass flag now that we've fetched fresh stream
                if (bypassCacheForQualityChange.remove(mediaId)) {
                    Timber.tag("MusicService").d("Cleared bypass cache flag for $mediaId after fresh fetch")
                }

                val streamUrl = nonNullPlayback.streamUrl

                songUrlCache[mediaId] =
                    streamUrl to System.currentTimeMillis() + (nonNullPlayback.streamExpiresInSeconds * 1000L)
                return@Factory dataSpec.withUri(streamUrl.toUri()).subrange(dataSpec.uriPositionOffset, CHUNK_LENGTH)
            }
        }
    }

    private fun createMediaSourceFactory(allowVideo: Boolean): MediaSource.Factory {
        val audioFactory = createLegacyMediaSourceFactory(allowVideo = false)
        return object : MediaSource.Factory {
            override fun createMediaSource(mediaItem: MediaItem): MediaSource {
                val dashRoute = dashRouteFor(mediaItem)
                if (dashRoute != null && allowVideo && shouldAttemptVideoFor(mediaItem)) {
                    return createSideloadedDashMediaSource(
                        mediaItem = mediaItem,
                        sourceInstanceId = dashRoute.first,
                        route = dashRoute.second,
                        audioFactory = audioFactory,
                    )
                }
                if (!allowVideo || !shouldAttemptVideoFor(mediaItem)) {
                    return audioFactory.createMediaSource(mediaItem)
                }
                val mediaId = mediaItem.mediaId
                val instance = playbackSourceInstanceSequence.incrementAndGet()
                val isMuxed = java.util.concurrent.atomic.AtomicBoolean(false)
                val audioDataSource = createDataSourceFactory()
                val muxedDataSource = DefaultDataSource.Factory(this@MusicService,
                    OkHttpDataSource.Factory(OkHttpClient.Builder().proxy(YouTube.proxy).build()))
                    .setTransferListener(videoTransferListener)
                val routingFactory = DataSource.Factory {
                    MuxedRoutingDataSource(audioDataSource, muxedDataSource, resolve = {
                        Timber.tag(TAG).i(
                            "[VideoPlayback][route] mediaId=$mediaId sourceInstance=$instance " +
                                "routeResolverStart=true",
                        )
                        val route = try {
                            runBlocking(Dispatchers.IO) {
                                InnerTubeXPlayer.resolveMuxed360(mediaId, mediaItem.metadata?.musicVideoType)
                            }
                        } catch (error: Exception) {
                            // Do not expose signed URLs or request headers in resolver diagnostics.
                            Timber.tag(TAG).w("[VideoPlayback][route] mediaId=$mediaId muxed unavailable errorType=${error.javaClass.simpleName}")
                            null
                        }
                        route?.musicVideoType?.let { resolvedMusicVideoTypes[mediaId] = it }
                        val stream = route?.stream
                        val dashRoute = if (stream == null && route?.musicVideoType in ACTUAL_MUSIC_VIDEO_TYPES &&
                            shouldAttemptVideoFor(mediaItem)
                        ) {
                            try {
                                runBlocking(Dispatchers.IO) {
                                    InnerTubeXPlayer.resolveDash360(mediaId, route?.musicVideoType)
                                }
                            } catch (error: Exception) {
                                // Raw response parsing and cipher resolution must fail closed to the
                                // original audio path; keep sensitive stream details out of logs.
                                Timber.tag(TAG).w(
                                    "[VideoPlayback][dash] mediaId=$mediaId unavailable " +
                                        "errorType=${error.javaClass.simpleName}",
                                )
                                null
                            }
                        } else {
                            null
                        }
                        dashRoute?.musicVideoType?.let { resolvedMusicVideoTypes[mediaId] = it }
                        if (stream == null && dashRoute != null && shouldAttemptVideoFor(mediaItem)) {
                            dashRoutes[instance] = dashRoute
                            scope.launch {
                                installSideloadedDashRoute(mediaItem, instance, dashRoute)
                            }
                            // Keep the current audio-only DataSource until the main-thread source
                            // replacement installs the one DashMediaSource. This never alters the
                            // existing audio resolver/cache/Opus selection for the fallback path.
                            null
                        } else if (stream == null || !shouldAttemptVideoFor(mediaItem)) {
                            null // Original audio resolver/cache/quality selection, never muxed AAC.
                        } else {
                            isMuxed.set(true)
                            videoSourceMediaIds += mediaId
                            videoPlaybackRoutes[mediaId] = VideoPlaybackRoute.MUXED
                            videoSourceInstanceIds[mediaId] = instance
                            mediaSourceUri(mediaItem)?.let { videoSourceUris[mediaId] = it }
                            videoStreamDiagnostics[instance] = VideoStreamDiagnostics(instance, mediaId).apply {
                                bitrate = stream.bitrate
                                contentLength = stream.contentLength
                                mimeType = stream.mimeType
                                codecs = stream.codecs
                                itag = stream.itag
                            }
                            Timber.tag(TAG).i(
                                "[VideoPlayback][route] mediaId=$mediaId musicVideoType=${route.musicVideoType} " +
                                    "route=SINGLE_MUXED_VIDEO itag=${stream.itag} mime=${stream.mimeType} " +
                                    "codecs=${stream.codecs} audioCodec=${stream.codecs?.split(',')?.firstOrNull { it.trim().startsWith("mp4a") }?.trim()} " +
                                    "width=${stream.width} height=${stream.height} bitrate=${stream.bitrate}",
                            )
                            scope.launch {
                                val current = player.currentMediaItem
                                if (current?.mediaId == mediaId &&
                                    current.localConfiguration == mediaItem.localConfiguration &&
                                    videoSourceInstanceIds[mediaId] == instance
                                ) {
                                    if (!shouldAttemptVideoFor(mediaItem)) {
                                        rebuildCurrentAsAudioOnly("muxed route disabled", false, expectedMediaId = mediaId)
                                    } else {
                                        pendingCurrentVideoRouteActivation = null
                                        videoPlaybackRequestedMediaId.value = mediaId
                                        maybeArmVideoStallWatchdog("muxedResolved")
                                    }
                                }
                            }
                            DataSpec.Builder().setUri(stream.streamUrl).setKey("video:$instance:$mediaId")
                                .setHttpRequestHeaders(stream.headers).build()
                        }
                    }, onMuxedError = { error ->
                        if (videoSourceInstanceIds[mediaId] == instance) markVideoLoadFailure(mediaId, error)
                    })
                }
                return androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(
                    routingFactory,
                    ExtractorsFactory { arrayOf(MatroskaExtractor(), FragmentedMp4Extractor(), Mp4Extractor()) },
                ).createMediaSource(mediaItem).also { source ->
                    source.addEventListener(Handler(Looper.getMainLooper()), object : MediaSourceEventListener {
                        override fun onLoadError(
                            windowIndex: Int, mediaPeriodId: MediaSource.MediaPeriodId?,
                            loadEventInfo: LoadEventInfo, mediaLoadData: MediaLoadData,
                            error: IOException, wasCanceled: Boolean,
                        ) {
                            // The muxed container is one source. Do not tag errors from the delegated
                            // original audio resolver/cache when no muxed stream was selected.
                            if (isMuxed.get() && !wasCanceled && videoSourceInstanceIds[mediaId] == instance) {
                                markVideoLoadFailure(mediaId, error)
                            }
                        }
                    })
                }
            }

            override fun setDrmSessionManagerProvider(provider: DrmSessionManagerProvider): MediaSource.Factory {
                audioFactory.setDrmSessionManagerProvider(provider)
                return this
            }
            override fun setLoadErrorHandlingPolicy(policy: LoadErrorHandlingPolicy): MediaSource.Factory {
                audioFactory.setLoadErrorHandlingPolicy(policy)
                return this
            }
            override fun getSupportedTypes(): IntArray = audioFactory.supportedTypes
        }
    }

    /** Returns the memory-only route addressed by the opaque URI used for source replacement. */
    private fun dashRouteFor(mediaItem: MediaItem): Pair<Long, InnerTubeXPlayer.DashRoute>? {
        val uri = mediaItem.localConfiguration?.uri ?: return null
        if (uri.scheme != SIDELOADED_DASH_SCHEME) return null
        val sourceInstanceId = uri.schemeSpecificPart.toLongOrNull() ?: return null
        return dashRoutes[sourceInstanceId]?.let { sourceInstanceId to it }
    }

    /**
     * The muxed resolver runs on a Loader thread. Only this main-scope continuation reads or
     * mutates Player state, so resolving a DASH route cannot reintroduce wrong-thread access.
     */
    private fun installSideloadedDashRoute(
        originalItem: MediaItem,
        sourceInstanceId: Long,
        route: InnerTubeXPlayer.DashRoute,
    ) {
        val current = player.currentMediaItem
        val mediaId = originalItem.mediaId
        val index = player.currentMediaItemIndex
        if (current == null ||
            current.mediaId != mediaId ||
            current.localConfiguration != originalItem.localConfiguration ||
            index !in 0 until player.mediaItemCount ||
            player.getMediaItemAt(index).mediaId != mediaId ||
            !shouldAttemptVideoFor(originalItem) ||
            !isConfirmedMusicVideoTrack(originalItem) ||
            resolvedMusicVideoTypes[mediaId] != route.musicVideoType ||
            hasCurrentVideoRoute(current) ||
            dashRoutes[sourceInstanceId] !== route
        ) {
            dashRoutes.remove(sourceInstanceId, route)
            return
        }

        val position = safeCurrentPlaybackPosition()
        val shouldResume = player.playWhenReady
        cancelVideoStallWatchdog("installing single DASH source")
        val dashItem = originalItem.buildUpon()
            .setUri("$SIDELOADED_DASH_SCHEME:$sourceInstanceId")
            // Retain the existing audio cache key if this route has to fail closed to audio-only.
            .setCustomCacheKey(mediaId)
            .build()
        Timber.tag(TAG).i(
            "[VideoPlayback][dash] mediaId=$mediaId musicVideoType=${route.musicVideoType} " +
                "installing=SINGLE_DASH_VIDEO sourceInstance=$sourceInstanceId position=$position",
        )
        player.replaceMediaItem(index, dashItem)
        player.prepare()
        player.seekTo(index, position)
        player.playWhenReady = shouldResume
    }

    /** Builds one audio + one video SegmentBase manifest; it never creates a MergingMediaSource. */
    private fun createSideloadedDashMediaSource(
        mediaItem: MediaItem,
        sourceInstanceId: Long,
        route: InnerTubeXPlayer.DashRoute,
        audioFactory: MediaSource.Factory,
    ): MediaSource = try {
        val manifest = createSideloadedDashManifest(route)
        videoSourceMediaIds += mediaItem.mediaId
        videoPlaybackRoutes[mediaItem.mediaId] = VideoPlaybackRoute.DASH
        videoSourceInstanceIds[mediaItem.mediaId] = sourceInstanceId
        mediaSourceUri(mediaItem)?.let { videoSourceUris[mediaItem.mediaId] = it }
        pendingCurrentVideoRouteActivation = null
        videoPlaybackRequestedMediaId.value = mediaItem.mediaId
        Timber.tag(TAG).i(
            "[VideoPlayback][dash] mediaId=${mediaItem.mediaId} musicVideoType=${route.musicVideoType} " +
                "route=SINGLE_DASH_VIDEO videoItag=${route.video.itag} " +
                "videoMime=${route.video.mimeType.substringBefore(';')} videoCodec=${route.video.codecs} " +
                "videoWidth=${route.video.width} videoHeight=${route.video.height} " +
                "videoBitrate=${route.video.bitrate} audioItag=${route.audio.itag} " +
                "audioMime=${route.audio.mimeType.substringBefore(';')} audioCodec=${route.audio.codecs} " +
                "audioBitrate=${route.audio.bitrate} videoInitRange=${route.video.initRange.start}-${route.video.initRange.end} " +
                "videoIndexRange=${route.video.indexRange.start}-${route.video.indexRange.end} " +
                "audioInitRange=${route.audio.initRange.start}-${route.audio.initRange.end} " +
                "audioIndexRange=${route.audio.indexRange.start}-${route.audio.indexRange.end}",
        )
        DashMediaSource.Factory(createSideloadedDashDataSourceFactory(route))
            .createMediaSource(manifest, mediaItem)
            .also { source ->
                source.addEventListener(Handler(Looper.getMainLooper()), object : MediaSourceEventListener {
                    override fun onLoadError(
                        windowIndex: Int,
                        mediaPeriodId: MediaSource.MediaPeriodId?,
                        loadEventInfo: LoadEventInfo,
                        mediaLoadData: MediaLoadData,
                        error: IOException,
                        wasCanceled: Boolean,
                    ) {
                        if (!wasCanceled && videoSourceInstanceIds[mediaItem.mediaId] == sourceInstanceId) {
                            markVideoLoadFailure(mediaItem.mediaId, error)
                        }
                    }
                })
            }
    } catch (error: Exception) {
        // A malformed response must never leave a synthetic URI on the normal audio resolver.
        dashRoutes.remove(sourceInstanceId)
        videoFallbackMediaIds += mediaItem.mediaId
        if (videoSourceInstanceIds[mediaItem.mediaId] == sourceInstanceId) {
            videoSourceInstanceIds.remove(mediaItem.mediaId)
            videoSourceMediaIds.remove(mediaItem.mediaId)
            videoSourceUris.remove(mediaItem.mediaId)
            videoPlaybackRoutes.remove(mediaItem.mediaId)
            videoPlaybackRequestedMediaId.value = null
        }
        Timber.tag(TAG).w(
            "[VideoPlayback][dash] mediaId=${mediaItem.mediaId} manifest unavailable " +
                "errorType=${error.javaClass.simpleName}; using audio-only",
        )
        audioFactory.createMediaSource(mediaItem)
    }

    private fun createSideloadedDashManifest(route: InnerTubeXPlayer.DashRoute): DashManifest {
        val audioRepresentation = createDashRepresentation(route.audio, isVideo = false)
        val videoRepresentation = createDashRepresentation(route.video, isVideo = true)
        val adaptationSets = listOf(
            AdaptationSet(0, C.TRACK_TYPE_AUDIO, listOf(audioRepresentation), emptyList(), emptyList(), emptyList()),
            AdaptationSet(1, C.TRACK_TYPE_VIDEO, listOf(videoRepresentation), emptyList(), emptyList(), emptyList()),
        )
        return DashManifest(
            C.TIME_UNSET,
            route.durationMs,
            0L,
            false,
            C.TIME_UNSET,
            C.TIME_UNSET,
            C.TIME_UNSET,
            C.TIME_UNSET,
            null,
            null,
            null,
            null,
            listOf(Period("0", 0L, adaptationSets)),
        )
    }

    private fun createDashRepresentation(
        stream: InnerTubeXPlayer.DashStream,
        isVideo: Boolean,
    ): Representation {
        val codecs = stream.codecs
        val containerMimeType = stream.mimeType.substringBefore(';')
        val sampleMimeType = if (isVideo) {
            MimeTypes.getVideoMediaMimeType(codecs)
        } else {
            MimeTypes.getAudioMediaMimeType(codecs)
        }
        val formatBuilder = Format.Builder()
            .setId(stream.itag.toString())
            .setContainerMimeType(containerMimeType)
            .setSampleMimeType(sampleMimeType)
            .setCodecs(codecs)
            .setAverageBitrate(stream.bitrate)
            .setPeakBitrate(stream.bitrate)
        if (isVideo) {
            formatBuilder.setWidth(requireNotNull(stream.width)).setHeight(requireNotNull(stream.height))
        } else {
            stream.audioChannels?.let(formatBuilder::setChannelCount)
            stream.audioSampleRate?.let(formatBuilder::setSampleRate)
        }
        val segmentBase = SegmentBase.SingleSegmentBase(
            RangedUri(null, stream.initRange.start, stream.initRange.length),
            1L,
            0L,
            stream.indexRange.start,
            stream.indexRange.length,
        )
        return Representation.newInstance(
            0L,
            formatBuilder.build(),
            listOf(BaseUrl(stream.streamUrl)),
            segmentBase,
        )
    }

    /** DASH streams remain explicitly uncached and have no overlap with the audio cache key. */
    private fun createSideloadedDashDataSourceFactory(
        route: InnerTubeXPlayer.DashRoute,
    ): DataSource.Factory = ResolvingDataSource.Factory(
        DefaultDataSource.Factory(
            this,
            OkHttpDataSource.Factory(OkHttpClient.Builder().proxy(YouTube.proxy).build()),
        ),
    ) { dataSpec ->
        when (dataSpec.uri.toString()) {
            route.video.streamUrl -> dataSpec.withRequestHeaders(route.video.headers)
            route.audio.streamUrl -> dataSpec.withRequestHeaders(route.audio.headers)
            else -> dataSpec
        }
    }

    private fun safeCurrentPlaybackPosition(): Long = player.currentPosition.coerceAtLeast(0L).let { position ->
        player.duration
            .takeIf { it != C.TIME_UNSET && it > 0L }
            ?.let { duration -> position.coerceAtMost((duration - 1L).coerceAtLeast(0L)) }
            ?: position
    }

    // Retained for a future DASH/adaptive comparison; production routing above never enables it.
    private fun createLegacyMediaSourceFactory(allowVideo: Boolean): MediaSource.Factory {
        val extractorsFactory = ExtractorsFactory {
            arrayOf(
                MatroskaExtractor(),        // .webm / Opus / VP9
                FragmentedMp4Extractor(),   // fragmented .mp4 / AAC (YouTube)
                Mp4Extractor(),             // regular .mp4 / AAC (JioSaavn)
            )
        }
        val audioFactory = DefaultMediaSourceFactory(createDataSourceFactory(), extractorsFactory)
        val videoFactory = DefaultMediaSourceFactory(createVideoDataSourceFactory(), extractorsFactory)

        return object : MediaSource.Factory {
            override fun createMediaSource(mediaItem: MediaItem): MediaSource {
                val audioSource = audioFactory.createMediaSource(mediaItem)
                val sourceInstanceId = playbackSourceInstanceSequence.incrementAndGet()
                val shouldMergeVideo = allowVideo && shouldAttemptVideoFor(mediaItem)
                val isFallbackSource = mediaItem.mediaId == audioOnlyFallbackMediaId
                val sourceLabel = when {
                    shouldMergeVideo -> "merged#$sourceInstanceId"
                    isFallbackSource -> "fallback#$sourceInstanceId"
                    else -> "audio#$sourceInstanceId"
                }
                if (allowVideo && !shouldMergeVideo && isKnownAudioTrack(mediaItem)) {
                    Timber.tag(TAG).i(
                        "[VideoPlayback] skipping video for known audio track mediaId=${mediaItem.mediaId} " +
                            "musicVideoType=${mediaItem.metadata?.musicVideoType}",
                    )
                }
                if (!shouldMergeVideo && !isFallbackSource) return audioSource

                // This is diagnostic-only and is attached for merged and fallback sources only.
                // It does not alter the existing audio resolver, cache, or error recovery path.
                audioSource.addEventListener(
                    Handler(Looper.getMainLooper()),
                    object : MediaSourceEventListener {
                        override fun onLoadStarted(
                            windowIndex: Int,
                            mediaPeriodId: MediaSource.MediaPeriodId?,
                            loadEventInfo: LoadEventInfo,
                            mediaLoadData: MediaLoadData,
                            elapsedRealtimeMs: Int,
                        ) {
                            Timber.tag(TAG).d(
                                "[PlaybackDiag][audio][$sourceLabel] load started mediaId=${mediaItem.mediaId} " +
                                    loadEventDiagnostics(loadEventInfo, mediaLoadData),
                            )
                        }

                        override fun onLoadCompleted(
                            windowIndex: Int,
                            mediaPeriodId: MediaSource.MediaPeriodId?,
                            loadEventInfo: LoadEventInfo,
                            mediaLoadData: MediaLoadData,
                        ) {
                            Timber.tag(TAG).d(
                                "[PlaybackDiag][audio][$sourceLabel] load completed mediaId=${mediaItem.mediaId} " +
                                    loadEventDiagnostics(loadEventInfo, mediaLoadData),
                            )
                        }

                        override fun onLoadCanceled(
                            windowIndex: Int,
                            mediaPeriodId: MediaSource.MediaPeriodId?,
                            loadEventInfo: LoadEventInfo,
                            mediaLoadData: MediaLoadData,
                        ) {
                            Timber.tag(TAG).d(
                                "[PlaybackDiag][audio][$sourceLabel] load canceled mediaId=${mediaItem.mediaId} " +
                                    loadEventDiagnostics(loadEventInfo, mediaLoadData),
                            )
                        }

                        override fun onLoadError(
                            windowIndex: Int,
                            mediaPeriodId: MediaSource.MediaPeriodId?,
                            loadEventInfo: LoadEventInfo,
                            mediaLoadData: MediaLoadData,
                            error: IOException,
                            wasCanceled: Boolean,
                        ) {
                            if (!wasCanceled) {
                                Timber.tag(TAG).w(
                                    "[PlaybackDiag][audio][$sourceLabel] load error mediaId=${mediaItem.mediaId} " +
                                        "errorType=${error.javaClass.simpleName} " +
                                        loadEventDiagnostics(loadEventInfo, mediaLoadData),
                                )
                            }
                        }
                    },
                )

                if (!shouldMergeVideo) return audioSource

                // A distinct cache key prevents the video bytes from colliding with the existing
                // audio cache. The video data source itself intentionally has no cache in Phase 1.
                val videoItem = mediaItem.buildUpon()
                    .setUri("video:${mediaItem.mediaId}")
                    .setCustomCacheKey("video:$sourceInstanceId:${mediaItem.mediaId}")
                    .build()
                videoSourceMediaIds += mediaItem.mediaId
                videoSourceInstanceIds[mediaItem.mediaId] = sourceInstanceId
                videoStreamDiagnostics[sourceInstanceId] = VideoStreamDiagnostics(
                    sourceInstanceId = sourceInstanceId,
                    mediaId = mediaItem.mediaId,
                )
                scope.launch { maybeArmVideoStallWatchdog("mergedSource") }
                val videoSource = videoFactory.createMediaSource(videoItem)
                videoSource.addEventListener(
                    Handler(Looper.getMainLooper()),
                    object : MediaSourceEventListener {
                        override fun onLoadStarted(
                            windowIndex: Int,
                            mediaPeriodId: MediaSource.MediaPeriodId?,
                            loadEventInfo: LoadEventInfo,
                            mediaLoadData: MediaLoadData,
                            elapsedRealtimeMs: Int,
                        ) {
                            videoLoadStartedMediaIds += mediaItem.mediaId
                            Timber.tag(TAG).d(
                                "[VideoPlayback][$sourceLabel] load started mediaId=${mediaItem.mediaId} " +
                                    loadEventDiagnostics(loadEventInfo, mediaLoadData),
                            )
                            maybeArmVideoStallWatchdog("videoLoadStarted")
                            logVideoPlaybackTimeline("videoLoadStarted")
                        }

                        override fun onLoadCompleted(
                            windowIndex: Int,
                            mediaPeriodId: MediaSource.MediaPeriodId?,
                            loadEventInfo: LoadEventInfo,
                            mediaLoadData: MediaLoadData,
                        ) {
                            Timber.tag(TAG).d(
                                "[VideoPlayback][$sourceLabel] load completed mediaId=${mediaItem.mediaId} " +
                                    loadEventDiagnostics(loadEventInfo, mediaLoadData),
                            )
                        }

                        override fun onLoadCanceled(
                            windowIndex: Int,
                            mediaPeriodId: MediaSource.MediaPeriodId?,
                            loadEventInfo: LoadEventInfo,
                            mediaLoadData: MediaLoadData,
                        ) {
                            videoLoadStartedMediaIds.remove(mediaItem.mediaId)
                            Timber.tag(TAG).d(
                                "[VideoPlayback][$sourceLabel] old merged video load canceled mediaId=${mediaItem.mediaId} " +
                                    loadEventDiagnostics(loadEventInfo, mediaLoadData),
                            )
                        }

                        override fun onLoadError(
                            windowIndex: Int,
                            mediaPeriodId: MediaSource.MediaPeriodId?,
                            loadEventInfo: LoadEventInfo,
                            mediaLoadData: MediaLoadData,
                            error: IOException,
                            wasCanceled: Boolean,
                        ) {
                            if (!wasCanceled) {
                                Timber.tag(TAG).w(
                                    "[VideoPlayback][$sourceLabel] load error mediaId=${mediaItem.mediaId} " +
                                        "errorType=${error.javaClass.simpleName} " +
                                        loadEventDiagnostics(loadEventInfo, mediaLoadData),
                                )
                                markVideoLoadFailure(mediaItem.mediaId, error)
                            }
                        }
                    },
                )
                return MergingMediaSource(audioSource, videoSource)
            }

            override fun setDrmSessionManagerProvider(provider: DrmSessionManagerProvider): MediaSource.Factory {
                audioFactory.setDrmSessionManagerProvider(provider)
                videoFactory.setDrmSessionManagerProvider(provider)
                return this
            }

            override fun setLoadErrorHandlingPolicy(policy: LoadErrorHandlingPolicy): MediaSource.Factory {
                audioFactory.setLoadErrorHandlingPolicy(policy)
                videoFactory.setLoadErrorHandlingPolicy(policy)
                return this
            }

            override fun getSupportedTypes(): IntArray = audioFactory.supportedTypes
        }
    }

    override fun onRenderedFirstFrame() {
        val mediaId = player.currentMediaItem?.mediaId ?: return
        if (videoPlaybackRequestedMediaId.value == mediaId) {
            videoPlaybackActiveMediaId.value = mediaId
            logVideoPlaybackTimeline("firstFrame")
        }
    }

    private fun fallbackToAudioOnly(mediaId: String, error: PlaybackException): Boolean =
        rebuildCurrentAsAudioOnly(
            reason = "Video failed: ${error.errorCodeName}",
            permanentVideoFallback = true,
            error = error,
            expectedMediaId = mediaId,
        )

    /** Rebuilds only the current merged source through the existing factory. */
    private fun rebuildCurrentAsAudioOnly(
        reason: String,
        permanentVideoFallback: Boolean,
        error: PlaybackException? = null,
        expectedMediaId: String? = null,
    ): Boolean {
        if (isRebuildingCurrentAsAudioOnly) return false
        val item = player.currentMediaItem ?: return false
        val mediaId = item.mediaId
        if (expectedMediaId != null && mediaId != expectedMediaId) return false
        if (mediaId !in videoSourceMediaIds && videoPlaybackRequestedMediaId.value != mediaId) return false
        val index = player.currentMediaItemIndex
        if (index !in 0 until player.mediaItemCount || player.getMediaItemAt(index).mediaId != mediaId) return false
        val position = safeCurrentPlaybackPosition()
        val shouldResume = player.playWhenReady
        if (permanentVideoFallback && mediaId in videoFallbackMediaIds) return false
        isRebuildingCurrentAsAudioOnly = true
        try {
            cancelVideoStallWatchdog("audio-only rebuild")
            val rebuildStartedAtMs = SystemClock.elapsedRealtime()
            audioOnlyFallbackMediaId = mediaId
            audioOnlyFallbackStartedAtMs = rebuildStartedAtMs
            Timber.tag(TAG).i(
                "[VideoPlayback] audio-only rebuild start mediaId=$mediaId " +
                    "position=$position playWhenReady=$shouldResume",
            )
            if (error != null) {
                Timber.tag(TAG).w(error, "[VideoPlayback] falling back to audio-only mediaId=$mediaId")
            } else {
                Timber.tag(TAG).i("[VideoPlayback] falling back to audio-only mediaId=$mediaId reason=$reason")
            }
            if (permanentVideoFallback) videoFallbackMediaIds += mediaId
            videoLoadFailures.remove(mediaId)
            videoLoadStartedMediaIds.remove(mediaId)
            videoSourceMediaIds.remove(mediaId)
            videoSourceUris.remove(mediaId)
            videoPlaybackRoutes.remove(mediaId)
            videoSourceInstanceIds.remove(mediaId)?.let(dashRoutes::remove)
            videoPlaybackRequestedMediaId.value = null
            videoPlaybackActiveMediaId.value = null
            // ProgressiveMediaSource can update an equal MediaItem in-place, which would leave the
            // old MergingMediaSource alive. A fallback-only URI forces replaceMediaItem to create
            // and release the current source while preserving its mediaId, metadata, cache key and queue.
            val audioOnlyItem = item.buildUpon()
                .setUri("fallback:$mediaId")
                .setCustomCacheKey(mediaId)
                .build()
            Timber.tag(TAG).d("[VideoPlayback] replacing current source with audio-only mediaId=$mediaId")
            player.replaceMediaItem(index, audioOnlyItem)
            Timber.tag(TAG).d("[VideoPlayback] audio-only source installed mediaId=$mediaId")
            player.prepare()
            Timber.tag(TAG).d("[VideoPlayback] audio-only prepare requested mediaId=$mediaId")
            player.seekTo(index, position)
            player.playWhenReady = shouldResume
            return true
        } finally {
            isRebuildingCurrentAsAudioOnly = false
        }
    }

    private fun markVideoLoadFailure(mediaId: String, error: Throwable) {
        videoLoadFailures[mediaId] = error
        Timber.tag(TAG).w(error, "[VideoPlayback] load error mediaId=$mediaId")
    }

    /**
     * Audio-only errors remain on the existing recovery path. A DASH source is itself the video
     * route, so an error from it intentionally falls back to audio-only rather than retrying it.
     */
    private fun isVideoOnlyFailure(mediaId: String, error: PlaybackException): Boolean =
        videoPlaybackRoutes[mediaId] == VideoPlaybackRoute.DASH ||
            isRecordedVideoLoadFailure(mediaId, error) ||
            (mediaId in videoSourceMediaIds && isVideoRendererFailure(error))

    /**
     * onLoadError is also emitted for retryable loads. Match the exact throwable that reached
     * Player.onPlayerError so a transient video retry cannot later misclassify an audio error.
     */
    private fun isRecordedVideoLoadFailure(mediaId: String, error: PlaybackException): Boolean {
        val recorded = videoLoadFailures[mediaId] ?: return false
        var cause: Throwable? = error
        while (cause != null) {
            if (cause === recorded) return true
            cause = cause.cause
        }
        return false
    }

    private fun isVideoRendererFailure(error: PlaybackException): Boolean {
        var cause: Throwable? = error
        while (cause != null) {
            if (cause is ExoPlaybackException &&
                cause.type == ExoPlaybackException.TYPE_RENDERER &&
                cause.rendererIndex != C.INDEX_UNSET &&
                player.getRendererType(cause.rendererIndex) == C.TRACK_TYPE_VIDEO
            ) {
                return true
            }
            cause = cause.cause
        }
        return false
    }

    /**
     * Detects a silent MergingMediaSource stall without ever treating an audio-only rebuffer as a
     * video failure. A sample that advances re-arms the full timeout; a paused player never arms.
     */
    private fun maybeArmVideoStallWatchdog(reason: String) {
        val mediaId = player.currentMediaItem?.mediaId ?: return
        if (!isCurrentMergedVideoPlayback(mediaId) || !player.playWhenReady ||
            player.playbackState != Player.STATE_BUFFERING
        ) {
            return
        }
        if (videoStallWatchdogJob?.isActive == true) return

        val initialPosition = normalizedPlaybackPosition(player.currentPosition)
        val initialBufferedPosition = normalizedPlaybackPosition(player.bufferedPosition)
        Timber.tag(TAG).d(
            "[VideoPlayback] stall watchdog armed mediaId=$mediaId reason=$reason " +
                "position=$initialPosition buffered=$initialBufferedPosition",
        )
        videoStallWatchdogJob = scope.launch {
            delay(VIDEO_STALL_TIMEOUT_MS)
            if (!isActive || !isCurrentMergedVideoPlayback(mediaId) ||
                !player.playWhenReady || player.playbackState != Player.STATE_BUFFERING
            ) {
                return@launch
            }

            val currentPosition = normalizedPlaybackPosition(player.currentPosition)
            val currentBufferedPosition = normalizedPlaybackPosition(player.bufferedPosition)
            val startupBufferStillInsufficient =
                initialPosition == 0L &&
                    currentPosition == 0L &&
                    currentBufferedPosition < VIDEO_STARTUP_MIN_BUFFER_MS
            val progressed = !startupBufferStillInsufficient &&
                (currentPosition >= initialPosition + VIDEO_STALL_PROGRESS_THRESHOLD_MS ||
                    currentBufferedPosition >= initialBufferedPosition + VIDEO_STALL_PROGRESS_THRESHOLD_MS)
            if (progressed) {
                Timber.tag(TAG).d(
                    "[VideoPlayback] buffering progress mediaId=$mediaId " +
                        "position=$currentPosition buffered=$currentBufferedPosition",
                )
                videoStallWatchdogJob = null
                maybeArmVideoStallWatchdog("bufferingProgress")
                return@launch
            }

            val firstFrameRendered = videoPlaybackActiveMediaId.value == mediaId
            if (maybeRetryLowThroughputVideoStartup(
                    mediaId = mediaId,
                    currentPosition = currentPosition,
                    currentBufferedPosition = currentBufferedPosition,
                )
            ) {
                videoStallWatchdogJob = null
                return@launch
            }
            if (maybeRetryExtractorOrSampleVideoStall(
                    mediaId = mediaId,
                    currentPosition = currentPosition,
                    currentBufferedPosition = currentBufferedPosition,
                )
            ) {
                videoStallWatchdogJob = null
                return@launch
            }
            if (maybeRetryNetworkVideoStall(
                    mediaId = mediaId,
                    currentPosition = currentPosition,
                    currentBufferedPosition = currentBufferedPosition,
                )
            ) {
                videoStallWatchdogJob = null
                return@launch
            }
            if (isCurrentAutoRetryConsumed(mediaId) && !videoAutoRetrySucceeded) {
                when {
                    isExtractorOrSampleVideoStall(mediaId) ->
                    Timber.tag(TAG).w(
                        "[VideoPlayback][autoRetry] retry exhausted mediaId=$mediaId " +
                            "reason=EXTRACTOR_OR_SAMPLE_STALL attempt=1",
                    )
                    isNetworkVideoStall(mediaId) ->
                    Timber.tag(TAG).w(
                        "[VideoPlayback][autoRetry] retry exhausted mediaId=$mediaId " +
                            "reason=NETWORK_STALL attempt=1",
                    )
                    else ->
                    Timber.tag(TAG).w(
                        "[VideoPlayback][autoRetry] retry failed mediaId=$mediaId attempt=1 " +
                            "reason=STALL_AFTER_RETRY position=$currentPosition buffered=$currentBufferedPosition",
                    )
                }
            }
            logVideoStallDiagnostics(
                mediaId = mediaId,
                currentPosition = currentPosition,
                currentBufferedPosition = currentBufferedPosition,
                firstFrameRendered = firstFrameRendered,
            )
            Timber.tag(TAG).w(
                "[VideoPlayback] stall watchdog triggered mediaId=$mediaId " +
                    "position=$currentPosition buffered=$currentBufferedPosition firstFrame=$firstFrameRendered",
            )
            videoStallWatchdogJob = null
            rebuildCurrentAsAudioOnly(
                reason = "Merged video buffering stalled for ${VIDEO_STALL_TIMEOUT_MS}ms",
                permanentVideoFallback = true,
                expectedMediaId = mediaId,
            )
        }
    }

    private fun cancelVideoStallWatchdog(reason: String) {
        if (videoStallWatchdogJob?.isActive == true) {
            Timber.tag(TAG).d("[VideoPlayback] stall watchdog canceled reason=$reason")
        }
        videoStallWatchdogJob?.cancel()
        videoStallWatchdogJob = null
    }

    /** This intentionally only identifies the primary player's active merged source. */
    private fun isCurrentMergedVideoPlayback(mediaId: String): Boolean =
        player.currentMediaItem?.mediaId == mediaId &&
            !isRebuildingCurrentAsAudioOnly &&
            mediaId in videoSourceMediaIds &&
            videoPlaybackRoutes[mediaId] != VideoPlaybackRoute.DASH &&
            (videoPlaybackRequestedMediaId.value == mediaId || mediaId in videoLoadStartedMediaIds) &&
            mediaId !in videoFallbackMediaIds &&
            videoPlaybackEnabled &&
            !playerV2Enabled &&
            !jioSaavnStreamingEnabled &&
            playerBackgroundStyle != PlayerBackgroundStyle.APPLE_MUSIC &&
            castConnectionHandler?.isCasting?.value != true

    /** Media3 uses [C.TIME_UNSET] before a buffered position is known; it is not progress. */
    private fun normalizedPlaybackPosition(position: Long): Long =
        position.takeIf { it != C.TIME_UNSET && it >= 0L } ?: 0L

    /** The automatic retry shares the manual retry's stop/prepare reopen path, but never clears audio cache. */
    private fun maybeRetryLowThroughputVideoStartup(
        mediaId: String,
        currentPosition: Long,
        currentBufferedPosition: Long,
    ): Boolean {
        if (currentPosition > VIDEO_AUTO_RETRY_MAX_STARTUP_POSITION_MS ||
            currentBufferedPosition >= VIDEO_STARTUP_MIN_BUFFER_MS ||
            !player.playWhenReady
        ) {
            return false
        }
        val sourceInstanceId = videoSourceInstanceIds[mediaId] ?: return false
        if (isCurrentAutoRetryConsumed(mediaId)) {
            return false
        }
        val snapshot = videoDiagnosticsSnapshot(sourceInstanceId) ?: return false
        val firstByteAtMs = snapshot.firstByteAtMs ?: return false
        val bitrate = snapshot.bitrate?.takeIf { it > 0 } ?: return false
        val elapsedMs = SystemClock.elapsedRealtime() - firstByteAtMs
        if (snapshot.totalBytes <= 0L || elapsedMs < VIDEO_AUTO_RETRY_MIN_OBSERVATION_MS) return false

        val bytesPerSecond = snapshot.totalBytes * 1_000L / elapsedMs
        val requiredBytesPerSecond = bitrate / 8L
        if (bytesPerSecond.toDouble() >= requiredBytesPerSecond * VIDEO_AUTO_RETRY_REQUIRED_RATE_FRACTION) {
            return false
        }

        Timber.tag(TAG).w(
            "[VideoPlayback][autoRetry] mediaId=$mediaId reason=LOW_VIDEO_THROUGHPUT attempt=1 " +
                "elapsedMs=$elapsedMs bytes=${snapshot.totalBytes} bytesPerSecond=$bytesPerSecond " +
                "requiredBytesPerSecond=$requiredBytesPerSecond position=$currentPosition " +
                "buffered=$currentBufferedPosition",
        )
        return restartVideoPlaybackOnce(
            mediaId = mediaId,
            sourceInstanceId = sourceInstanceId,
            reason = "LOW_VIDEO_THROUGHPUT",
        )
    }

    /** Retries a confirmed non-network video stall through the same one-shot reprepare path. */
    private fun maybeRetryExtractorOrSampleVideoStall(
        mediaId: String,
        currentPosition: Long,
        currentBufferedPosition: Long,
    ): Boolean {
        if (!player.playWhenReady) return false
        val sourceInstanceId = videoSourceInstanceIds[mediaId] ?: return false
        if (isCurrentAutoRetryConsumed(mediaId)) {
            return false
        }
        val snapshot = videoDiagnosticsSnapshot(sourceInstanceId) ?: return false
        val lastByteAgeMs = snapshot.lastByteAtMs
            ?.let { SystemClock.elapsedRealtime() - it }
            ?: return false
        if (snapshot.totalBytes <= 0L || lastByteAgeMs >= VIDEO_STALL_TIMEOUT_MS) return false

        Timber.tag(TAG).w(
            "[VideoPlayback][autoRetry] mediaId=$mediaId reason=EXTRACTOR_OR_SAMPLE_STALL attempt=1 " +
                "sourceInstance=$sourceInstanceId position=$currentPosition buffered=$currentBufferedPosition " +
                "bytes=${snapshot.totalBytes} lastByteAgeMs=$lastByteAgeMs",
        )
        return hardRetryExtractorOrSampleVideoStall(mediaId, sourceInstanceId)
    }

    /** Soft retry: reuse the current source but force a fresh uncached video DataSource open. */
    private fun restartVideoPlaybackOnce(
        mediaId: String,
        sourceInstanceId: Long,
        reason: String,
        resumePositionMs: Long = 0L,
    ): Boolean {
        recordVideoAutoRetry(mediaId, sourceInstanceId, reason, resumePositionMs)
        if (restartCurrentStream(
                expectedMediaId = mediaId,
                clearAudioCache = false,
                resetVideoDiagnostics = true,
                resumePositionMs = resumePositionMs,
            )
        ) {
            Timber.tag(TAG).i("[VideoPlayback][autoRetry] retry started mediaId=$mediaId attempt=1")
            return true
        }
        Timber.tag(TAG).w("[VideoPlayback][autoRetry] retry failed mediaId=$mediaId reason=RESTART_REJECTED")
        return false
    }

    /**
     * Replaces the current item with a URI-distinct equivalent so Media3 releases the old merged
     * source and creates a new MergingMediaSource/extractor pair, while retaining queue metadata.
     */
    private fun hardRetryExtractorOrSampleVideoStall(mediaId: String, sourceInstanceId: Long): Boolean {
        val item = player.currentMediaItem ?: return false
        val index = player.currentMediaItemIndex
        if (item.mediaId != mediaId || index !in 0 until player.mediaItemCount ||
            player.getMediaItemAt(index).mediaId != mediaId
        ) {
            return false
        }
        val resumePositionMs = player.currentPosition.coerceAtLeast(0L).let { position ->
            player.duration.takeIf { it != C.TIME_UNSET && it > 0L }
                ?.let { duration -> position.coerceAtMost((duration - 1L).coerceAtLeast(0L)) }
                ?: position
        }
        val shouldResume = player.playWhenReady
        recordVideoAutoRetry(mediaId, sourceInstanceId, "EXTRACTOR_OR_SAMPLE_STALL", resumePositionMs)
        cancelVideoStallWatchdog("EXTRACTOR_OR_SAMPLE_STALL hard retry")
        videoLoadFailures.remove(mediaId)
        videoLoadStartedMediaIds.remove(mediaId)
        videoPlaybackRequestedMediaId.value = null
        videoPlaybackActiveMediaId.value = null
        val retryItem = item.buildUpon()
            .setUri("hard-retry:$mediaId")
            .setCustomCacheKey(mediaId)
            .build()
        Timber.tag(TAG).w(
            "[VideoPlayback][autoRetry] hard retry replacing merged source mediaId=$mediaId " +
                "oldSourceInstance=$sourceInstanceId resumePositionMs=$resumePositionMs",
        )
        player.replaceMediaItem(index, retryItem)
        player.prepare()
        player.seekTo(index, resumePositionMs)
        player.playWhenReady = shouldResume
        Timber.tag(TAG).i("[VideoPlayback][autoRetry] retry started mediaId=$mediaId attempt=1")
        return true
    }

    /** NETWORK_STALL only reopens the stream and resumes at the watchdog's current position. */
    private fun maybeRetryNetworkVideoStall(
        mediaId: String,
        currentPosition: Long,
        currentBufferedPosition: Long,
    ): Boolean {
        if (!player.playWhenReady || isCurrentAutoRetryConsumed(mediaId)) return false
        val sourceInstanceId = videoSourceInstanceIds[mediaId] ?: return false
        val snapshot = videoDiagnosticsSnapshot(sourceInstanceId) ?: return false
        val lastByteAgeMs = snapshot.lastByteAtMs
            ?.let { SystemClock.elapsedRealtime() - it }
            ?: return false
        if (snapshot.totalBytes <= 0L || lastByteAgeMs < VIDEO_STALL_TIMEOUT_MS) return false
        Timber.tag(TAG).w(
            "[VideoPlayback][autoRetry] mediaId=$mediaId reason=NETWORK_STALL attempt=1 " +
                "sourceInstance=$sourceInstanceId position=$currentPosition buffered=$currentBufferedPosition " +
                "bytes=${snapshot.totalBytes} lastByteAgeMs=$lastByteAgeMs " +
                "resumePositionMs=$currentPosition",
        )
        return restartVideoPlaybackOnce(
            mediaId = mediaId,
            sourceInstanceId = sourceInstanceId,
            reason = "NETWORK_STALL",
            resumePositionMs = currentPosition,
        )
    }

    private fun recordVideoAutoRetry(
        mediaId: String,
        sourceInstanceId: Long,
        reason: String,
        resumePositionMs: Long,
    ) {
        videoAutoRetryMediaId = mediaId
        videoAutoRetryMediaItemIndex = player.currentMediaItemIndex
        videoAutoRetrySourceInstanceId = sourceInstanceId
        videoAutoRetryReason = reason
        videoAutoRetryResumePositionMs = resumePositionMs
        videoAutoRetryStartedAtMs = SystemClock.elapsedRealtime()
        videoAutoRetrySucceeded = false
    }

    /** Must only be called from the main-thread Player/watchdog callbacks. */
    private fun isCurrentAutoRetryConsumed(mediaId: String): Boolean =
        videoAutoRetryMediaId == mediaId &&
            videoAutoRetryMediaItemIndex == player.currentMediaItemIndex

    private fun isExtractorOrSampleVideoStall(mediaId: String): Boolean {
        val sourceInstanceId = videoSourceInstanceIds[mediaId] ?: return false
        val snapshot = videoDiagnosticsSnapshot(sourceInstanceId) ?: return false
        val lastByteAgeMs = snapshot.lastByteAtMs
            ?.let { SystemClock.elapsedRealtime() - it }
            ?: return false
        return snapshot.totalBytes > 0L && lastByteAgeMs < VIDEO_STALL_TIMEOUT_MS
    }

    private fun isNetworkVideoStall(mediaId: String): Boolean {
        val sourceInstanceId = videoSourceInstanceIds[mediaId] ?: return false
        val snapshot = videoDiagnosticsSnapshot(sourceInstanceId) ?: return false
        val lastByteAgeMs = snapshot.lastByteAtMs
            ?.let { SystemClock.elapsedRealtime() - it }
            ?: return false
        return snapshot.totalBytes > 0L && lastByteAgeMs >= VIDEO_STALL_TIMEOUT_MS
    }

    /** Copies Loader-thread transfer values before the main-thread watchdog evaluates them. */
    private fun videoDiagnosticsSnapshot(sourceInstanceId: Long): VideoStreamDiagnostics? {
        val diagnostics = videoStreamDiagnostics[sourceInstanceId] ?: return null
        return synchronized(diagnostics) {
            VideoStreamDiagnostics(
                sourceInstanceId = diagnostics.sourceInstanceId,
                mediaId = diagnostics.mediaId,
                openCount = diagnostics.openCount,
                totalBytes = diagnostics.totalBytes,
                firstByteAtMs = diagnostics.firstByteAtMs,
                lastByteAtMs = diagnostics.lastByteAtMs,
                bitrate = diagnostics.bitrate,
                contentLength = diagnostics.contentLength,
                mimeType = diagnostics.mimeType,
                codecs = diagnostics.codecs,
                itag = diagnostics.itag,
            )
        }
    }

    private fun logVideoAutoRetrySuccessIfNeeded() {
        val mediaId = videoAutoRetryMediaId ?: return
        if (videoAutoRetrySucceeded || player.currentMediaItem?.mediaId != mediaId ||
            !isCurrentMergedVideoPlayback(mediaId)
        ) {
            return
        }
        videoAutoRetrySucceeded = true
        val elapsedMs = videoAutoRetryStartedAtMs?.let { SystemClock.elapsedRealtime() - it } ?: 0L
        val reason = videoAutoRetryReason
        val resumePositionMs = videoAutoRetryResumePositionMs
        if (reason == "NETWORK_STALL") {
            Timber.tag(TAG).i(
                "[VideoPlayback][autoRetry] retry succeeded mediaId=$mediaId reason=$reason " +
                    "resumePositionMs=$resumePositionMs attempt=1 elapsedMs=$elapsedMs",
            )
        } else {
            Timber.tag(TAG).i(
                "[VideoPlayback][autoRetry] retry succeeded mediaId=$mediaId attempt=1 elapsedMs=$elapsedMs",
            )
        }
    }

    private fun resetVideoAutoRetry(reason: String) {
        if (videoAutoRetryMediaId != null) {
            Timber.tag(TAG).d("[VideoPlayback][autoRetry] reset mediaId=$videoAutoRetryMediaId reason=$reason")
        }
        videoAutoRetryMediaId = null
        videoAutoRetryMediaItemIndex = null
        videoAutoRetrySourceInstanceId = null
        videoAutoRetryReason = null
        videoAutoRetryResumePositionMs = 0L
        videoAutoRetryStartedAtMs = null
        videoAutoRetrySucceeded = false
    }

    /**
     * Classifies only the observed transfer state at watchdog expiry. It deliberately does not
     * affect the common audio-only fallback path: a short-lived transfer can still be unable to
     * yield samples, while a five-second byte gap is a network-side symptom.
     */
    private fun logVideoStallDiagnostics(
        mediaId: String,
        currentPosition: Long,
        currentBufferedPosition: Long,
        firstFrameRendered: Boolean,
    ) {
        val sourceInstanceId = videoSourceInstanceIds[mediaId]
        val diagnostics = sourceInstanceId?.let(videoStreamDiagnostics::get)
        val now = SystemClock.elapsedRealtime()
        val snapshot = diagnostics?.let {
            synchronized(it) {
                VideoStreamDiagnostics(
                    sourceInstanceId = it.sourceInstanceId,
                    mediaId = it.mediaId,
                    openCount = it.openCount,
                    totalBytes = it.totalBytes,
                    lastByteAtMs = it.lastByteAtMs,
                    bitrate = it.bitrate,
                    contentLength = it.contentLength,
                    mimeType = it.mimeType,
                    codecs = it.codecs,
                    itag = it.itag,
                )
            }
        }
        val lastByteAgeMs = snapshot?.lastByteAtMs?.let { now - it }
        val classification = when {
            snapshot == null || snapshot.totalBytes == 0L ||
                lastByteAgeMs == null || lastByteAgeMs >= VIDEO_STALL_TIMEOUT_MS -> "NETWORK_STALL"
            else -> "EXTRACTOR_OR_SAMPLE_STALL"
        }
        val playbackStarted = currentPosition > 0L || firstFrameRendered
        Timber.tag(TAG).w(
            "[VideoPlayback][stall] classification=$classification mediaId=$mediaId " +
                "sourceInstance=${sourceInstanceLabel(sourceInstanceId)} position=$currentPosition " +
                "buffered=$currentBufferedPosition transferTotalBytes=${snapshot?.totalBytes ?: 0L} " +
                "lastByteAgeMs=${lastByteAgeMs?.toString() ?: "none"} bitrate=${snapshot?.bitrate ?: "unknown"} " +
                "contentLength=${snapshot?.contentLength ?: "unknown"} firstFrame=$firstFrameRendered " +
                "playbackStarted=$playbackStarted openCount=${snapshot?.openCount ?: 0} " +
                "mime=${snapshot?.mimeType ?: "unknown"} codecs=${snapshot?.codecs ?: "unknown"} " +
                "itag=${snapshot?.itag ?: "unknown"}",
        )
    }

    /** The merged player timeline is the primary (audio) child timeline in Media3. */
    private fun logVideoPlaybackTimeline(reason: String) {
        val mediaId = player.currentMediaItem?.mediaId ?: return
        if (!isCurrentMergedVideoPlayback(mediaId)) return
        val timeline = player.currentTimeline
        val periodIndex = player.currentPeriodIndex
        if (timeline.isEmpty || periodIndex !in 0 until timeline.periodCount) {
            Timber.tag(TAG).d(
                "[VideoPlayback] timeline mediaId=$mediaId reason=$reason " +
                    "playerDuration=${diagnosticDurationMs(player.duration)} periodDuration=TIME_UNSET " +
                    "positionInWindow=TIME_UNSET",
            )
            return
        }
        val period = timeline.getPeriod(periodIndex, Timeline.Period())
        Timber.tag(TAG).d(
            "[VideoPlayback] timeline mediaId=$mediaId reason=$reason " +
                "playerDuration=${diagnosticDurationMs(player.duration)} " +
                "periodDuration=${diagnosticDurationUs(period.durationUs)} " +
                "positionInWindow=${diagnosticDurationUs(period.positionInWindowUs)}",
        )
    }

    private fun logVideoPlaybackTracks() {
        val mediaId = player.currentMediaItem?.mediaId ?: return
        if (!isCurrentMergedVideoPlayback(mediaId)) return
        player.currentTracks.groups.forEach { group ->
            val trackType = group.type
            if (trackType != C.TRACK_TYPE_AUDIO && trackType != C.TRACK_TYPE_VIDEO) return@forEach
            for (trackIndex in 0 until group.length) {
                if (!group.isTrackSelected(trackIndex)) continue
                val format = group.getTrackFormat(trackIndex)
                val type = if (trackType == C.TRACK_TYPE_AUDIO) "audio" else "video"
                Timber.tag(TAG).d(
                    "[VideoPlayback] tracks mediaId=$mediaId type=$type " +
                        "mime=${format.sampleMimeType} codecs=${format.codecs} " +
                        "width=${format.width} height=${format.height} frameRate=${format.frameRate} " +
                        "bitrate=${format.bitrate}",
                )
            }
        }
    }

    private fun diagnosticDurationMs(durationMs: Long): String =
        if (durationMs == C.TIME_UNSET) "TIME_UNSET" else "${durationMs}ms"

    private fun diagnosticDurationUs(durationUs: Long): String =
        if (durationUs == C.TIME_UNSET) "TIME_UNSET" else "${durationUs / 1_000L}ms"

    private fun logAudioOnlyFallbackState(state: String) {
        val mediaId = audioOnlyFallbackMediaId ?: return
        if (player.currentMediaItem?.mediaId != mediaId) return
        val startedAtMs = audioOnlyFallbackStartedAtMs ?: return
        Timber.tag(TAG).i(
            "[VideoPlayback] audio-only $state mediaId=$mediaId " +
                "elapsedMs=${SystemClock.elapsedRealtime() - startedAtMs}",
        )
    }

    /**
     * One service-side eligibility gate for every video resolver and source route. It deliberately
     * includes the legacy Apple Music background: that design owns its artwork/canvas layers and
     * must retain the original audio-only (Opus-preferred) path.
     */
    private fun videoEligibilityBlockedReason(mediaItem: MediaItem?): String? = when {
        !videoPlaybackEnabled -> "VIDEO_PLAYBACK_DISABLED"
        playerV2Enabled -> "PLAYER_V2"
        jioSaavnStreamingEnabled -> "JIOSAAVN"
        playerBackgroundStyle == PlayerBackgroundStyle.APPLE_MUSIC -> "APPLE_MUSIC_BACKGROUND"
        castConnectionHandler?.isCasting?.value == true -> "CAST"
        mediaItem == null -> "NO_MEDIA_ITEM"
        mediaItem.mediaId.isBlank() -> "EMPTY_MEDIA_ID"
        isKnownAudioTrack(mediaItem) -> "AUDIO_TRACK"
        mediaItem.mediaId in videoFallbackMediaIds -> "VIDEO_FALLBACK"
        else -> null
    }

    private fun shouldAttemptVideoFor(mediaItem: MediaItem): Boolean =
        videoEligibilityBlockedReason(mediaItem) == null

    private fun logVideoEligibility(mediaItem: MediaItem?) {
        val reason = videoEligibilityBlockedReason(mediaItem)
        Timber.tag(TAG).i(
            "[VideoPlayback][eligibility] mediaId=${mediaItem?.mediaId ?: "none"} " +
                "settingEnabled=$videoPlaybackEnabled backgroundStyle=$playerBackgroundStyle " +
                "eligible=${reason == null} blockedReason=${reason ?: "NONE"}",
        )
    }

    /** Explicit response classification wins over stale queue metadata. */
    private fun isKnownAudioTrack(mediaItem: MediaItem): Boolean =
        mediaItem.metadata?.musicVideoType == MUSIC_VIDEO_TYPE_ATV ||
            resolvedMusicVideoTypes[mediaItem.mediaId] == MUSIC_VIDEO_TYPE_ATV

    /** A transition may recreate its source only after queue metadata or a resolver proved video. */
    private fun isConfirmedMusicVideoTrack(mediaItem: MediaItem): Boolean =
        resolvedMusicVideoTypes[mediaItem.mediaId] in ACTUAL_MUSIC_VIDEO_TYPES ||
            mediaItem.metadata?.musicVideoType in ACTUAL_MUSIC_VIDEO_TYPES

    /**
     * Video uses an uncached resolving data source. It never changes the existing audio resolver,
     * audio cache key, audio quality selection, or audio URL prefetch path.
     */
    private fun createVideoDataSourceFactory(): DataSource.Factory =
        ResolvingDataSource.Factory(
            DefaultDataSource.Factory(
                this,
                OkHttpDataSource.Factory(
                    OkHttpClient.Builder()
                        .proxy(YouTube.proxy)
                        .build(),
                ),
        ).setTransferListener(videoTransferListener),
        ) { dataSpec ->
            val request = videoStreamRequest(dataSpec)
                ?: throw IOException("Invalid video stream cache key")
            val mediaId = request.mediaId

            try {
                Timber.tag(TAG).d(
                    "[VideoPlayback] resolving stream mediaId=$mediaId " +
                        "sourceInstance=${sourceInstanceLabel(request.sourceInstanceId)} " +
                        "dataSpecPosition=${dataSpec.position} dataSpecLength=${dataSpec.length} " +
                        "uriScheme=${dataSpec.uri.scheme ?: "none"}",
                )
                val playback = runBlocking(Dispatchers.IO) {
                    YTPlayerUtils.playerResponseForPlayback(
                        videoId = mediaId,
                        audioQuality = audioQuality,
                        connectivityManager = connectivityManager,
                        context = this@MusicService,
                        wantVideo = true,
                    )
                }.getOrElse { throw IOException("Unable to resolve 360p video stream", it) }

                val video = playback?.takeUnless { it.isSaavnStream }?.video
                    ?: throw IOException("No supported 360p video stream")
                val responseMusicVideoType = playback?.videoDetails?.musicVideoType
                videoDiagnosticsFor(request)?.let { diagnostics ->
                    synchronized(diagnostics) {
                        diagnostics.bitrate = video.bitrate
                        diagnostics.contentLength = video.contentLength
                        diagnostics.mimeType = video.mimeType
                        diagnostics.codecs = video.codecs
                        diagnostics.itag = video.itag
                    }
                }
                Timber.tag(TAG).d(
                    "[VideoPlayback] selected stream mediaId=$mediaId mime=${video.mimeType} " +
                        "codec=${video.codecs ?: video.mimeType} width=${video.width} height=${video.height} " +
                        "bitrate=${video.bitrate} contentLength=${video.contentLength} itag=${video.itag} " +
                        "sourceInstance=${sourceInstanceLabel(request.sourceInstanceId)}",
                )
                scope.launch {
                    // ResolvingDataSource runs on ProgressiveMediaPeriod's Loader thread. Keep all
                    // Player reads and UI-facing state updates on the service's main coroutine.
                    val currentMediaItem = player.currentMediaItem
                    Timber.tag(TAG).d(
                        "[VideoPlayback][metadata] mediaId=$mediaId " +
                            "mediaItemMusicVideoType=${currentMediaItem?.metadata?.musicVideoType} " +
                            "responseMusicVideoType=${responseMusicVideoType ?: "unknown"}",
                    )
                    if (currentMediaItem?.mediaId != mediaId) return@launch
                    videoPlaybackRequestedMediaId.value = mediaId
                    maybeArmVideoStallWatchdog("videoResolved")
                    logVideoPlaybackTimeline("videoResolved")
                }
                dataSpec.withUri(video.streamUrl.toUri()).withRequestHeaders(video.headers)
            } catch (error: IOException) {
                markVideoLoadFailure(mediaId, error)
                throw error
            } catch (error: Exception) {
                val ioError = IOException("Unable to resolve 360p video stream", error)
                markVideoLoadFailure(mediaId, ioError)
                throw ioError
            }
        }

    private fun createRenderersFactory(
        eqProcessor: CustomEqualizerAudioProcessor,
        silenceProcessor: SilenceDetectorAudioProcessor
    ) =
        object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ) = DefaultAudioSink
                .Builder(this@MusicService)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setAudioProcessorChain(
                    DefaultAudioSink.DefaultAudioProcessorChain(
                        // 2. Inject processor into audio pipeline
                        arrayOf(
                            eqProcessor,
                            silenceProcessor,
                        ),
                        SilenceSkippingAudioProcessor(2_000_000, 20_000, 256),
                        SonicAudioProcessor(),
                    ),
                ).build()
        }

    override fun onPlaybackStatsReady(
        eventTime: AnalyticsListener.EventTime,
        playbackStats: PlaybackStats,
    ) {
        val mediaItem = eventTime.timeline.getWindow(eventTime.windowIndex, Timeline.Window()).mediaItem
        val historyDurationMs = dataStore[HistoryDuration]?.times(1000f) ?: 30000f

        if (playbackStats.totalPlayTimeMs >= historyDurationMs &&
            !dataStore.get(PauseListenHistoryKey, false)
        ) {
            database.query {
                incrementTotalPlayTime(mediaItem.mediaId, playbackStats.totalPlayTimeMs)
                try {
                    insert(
                        Event(
                            songId = mediaItem.mediaId,
                            timestamp = LocalDateTime.now(),
                            playTime = playbackStats.totalPlayTimeMs,
                        ),
                    )
                } catch (_: SQLException) {
                }
            }
        }

        if (playbackStats.totalPlayTimeMs >= historyDurationMs) {
            CoroutineScope(Dispatchers.IO).launch {
                // Must fetch a FRESH playback tracking object. Cached `playbackUrl`s from the database
                // have expired security nonces/signatures and will result in YouTube silently ignoring the
                // history registration despite returning an HTTP 200/204 response.
                val freshPlayerResponse = YTPlayerUtils.playerResponseForMetadata(mediaItem.mediaId, null).getOrNull()
                
                val playbackUrl = freshPlayerResponse?.playbackTracking?.videostatsPlaybackUrl?.baseUrl
                val watchtimeUrl = freshPlayerResponse?.playbackTracking?.videostatsWatchtimeUrl?.baseUrl
                
                playbackUrl?.let { baseUrl ->
                    YouTube.registerPlayback(null, baseUrl)
                        .onSuccess {
                            // Also optionally ping watchtime to be absolutely sure the view registers
                            watchtimeUrl?.let { wtUrl -> 
                                YouTube.registerPlayback(null, wtUrl)
                            }
                            playbackRegistered.tryEmit(mediaItem.mediaId)
                        }
                        .onFailure {
                            reportException(it)
                        }
                }
            }
        }
    }

    private fun saveQueueToDisk() {
        if (player.mediaItemCount == 0) {
            Timber.tag(TAG).d("Skipping queue save - no media items")
            return
        }

        try {
            // Save current queue with proper type information
            val persistQueue = currentQueue.toPersistQueue(
                title = queueTitle,
                items = player.mediaItems.mapNotNull { it.metadata },
                mediaItemIndex = player.currentMediaItemIndex,
                position = player.currentPosition
            )

            val persistAutomix =
                PersistQueue(
                    title = "automix",
                    items = automixItems.value.mapNotNull { it.metadata },
                    mediaItemIndex = 0,
                    position = 0,
                )

            // Save player state
            val persistPlayerState = PersistPlayerState(
                playWhenReady = player.playWhenReady,
                repeatMode = player.repeatMode,
                shuffleModeEnabled = player.shuffleModeEnabled,
                volume = playerVolume.value,
                currentPosition = player.currentPosition,
                currentMediaItemIndex = player.currentMediaItemIndex,
                playbackState = player.playbackState
            )

            runCatching {
                filesDir.resolve(PERSISTENT_QUEUE_FILE).outputStream().use { fos ->
                    ObjectOutputStream(fos).use { oos ->
                        oos.writeObject(persistQueue)
                    }
                }
                Timber.tag(TAG).d("Queue saved successfully")
            }.onFailure {
                Timber.tag(TAG).e(it, "Failed to save queue")
                reportException(it)
            }

            runCatching {
            filesDir.resolve(PERSISTENT_AUTOMIX_FILE).outputStream().use { fos ->
                ObjectOutputStream(fos).use { oos ->
                        oos.writeObject(persistAutomix)
                    }
                }
                Timber.tag(TAG).d("Automix saved successfully")
            }.onFailure {
                Timber.tag(TAG).e(it, "Failed to save automix")
                reportException(it)
            }

            runCatching {
                filesDir.resolve(PERSISTENT_PLAYER_STATE_FILE).outputStream().use { fos ->
                    ObjectOutputStream(fos).use { oos ->
                        oos.writeObject(persistPlayerState)
                    }
                }
                Timber.tag(TAG).d("Player state saved successfully")
            }.onFailure {
                Timber.tag(TAG).e(it, "Failed to save player state")
                reportException(it)
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error during queue save operation")
            reportException(e)
        }
    }

    override fun onDestroy() {
        isRunning = false
        cancelVideoStallWatchdog("service destroy")
        resetVideoAutoRetry("service destroy")
        dashRoutes.clear()
        videoSourceUris.clear()
        videoPlaybackRoutes.clear()
        pendingCurrentVideoRouteActivation = null

        try {
            unregisterReceiver(screenStateReceiver)
        } catch (e: Exception) {
            // Ignore
        }
        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        castConnectionHandler?.release()
        if (dataStore.get(PersistentQueueKey, true)) {
            saveQueueToDisk()
        }
        if (discordRpc?.isRpcRunning() == true) {
            discordRpc?.closeRPC()
        }
        discordRpc = null
        connectivityObserver.unregister()
        abandonAudioFocus()
        releaseLoudnessEnhancer()
        releaseMediaSession()
        player.removeListener(this)
        player.removeListener(sleepTimer)
        playerSilenceProcessors.remove(player)
        // Note: equalizerService audio processors are cleared in equalizerService.release() if needed,
        // or we can't easily reference the specific processor created in createExoPlayer here without storing it.
        // But since we are destroying the service, it's fine.
        player.release()
        discordUpdateJob?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = super.onBind(intent) ?: binder

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (dataStore.get(StopMusicOnTaskClearKey, false)) {
            player.pause()
            releaseMediaSession()
            pauseAllPlayersAndStopSelf()
        } else {
            super.onTaskRemoved(rootIntent)
        }
    }

    private fun releaseMediaSession() {
        if (!mediaSessionReleased) {
            mediaSession.release()
            mediaSessionReleased = true
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            MusicWidgetReceiver.ACTION_PLAY_PAUSE -> {
                if (player.isPlaying) player.pause() else player.play()
                updateWidgetUI(player.isPlaying)
            }
            MusicWidgetReceiver.ACTION_LIKE -> {
                toggleLike()
            }
            MusicWidgetReceiver.ACTION_NEXT -> {
                if (!manualSkipToNextWithCrossfade()) {
                    player.seekToNext()
                }
                updateWidgetUI(player.isPlaying)
            }
            MusicWidgetReceiver.ACTION_PREVIOUS -> {
                if (!manualSkipToPreviousWithCrossfade()) {
                    player.seekToPrevious()
                }
                updateWidgetUI(player.isPlaying)
            }
            MusicWidgetReceiver.ACTION_UPDATE_WIDGET -> {
                updateWidgetUI(player.isPlaying)
            }
        }

        return super.onStartCommand(intent, flags, startId)
    }

    /**
     * Updates all app widgets with current playback state
     */
    private fun updateWidgetUI(isPlaying: Boolean) {
        scope.launch {
            try {
                val songData = currentSong.value
                val song = songData?.song
                val songTitle = song?.title ?: getString(R.string.no_song_playing)
                val artistName = songData?.artists?.joinToString(", ") { it.name } ?: getString(R.string.tap_to_open)
                val isLiked = songData?.song?.liked == true

                widgetManager.updateWidgets(
                    title = songTitle,
                    artist = artistName,
                    artworkUri = song?.thumbnailUrl,
                    isPlaying = isPlaying,
                    isLiked = isLiked,
                    duration = if (player.duration != C.TIME_UNSET) player.duration else 0,
                    currentPosition = player.currentPosition
                )
            } catch (e: Exception) {
                // Widget not added to home screen or other error
            }
        }
    }

    private var widgetUpdateJob: Job? = null

    private fun startWidgetUpdates() {
        widgetUpdateJob?.cancel()
        widgetUpdateJob = scope.launch {
            while (isActive) {
                if (player.isPlaying) {
                    updateWidgetUI(true)
                }
                delay(200)
            }
        }
    }

    private fun stopWidgetUpdates() {
        widgetUpdateJob?.cancel()
        widgetUpdateJob = null
    }

    private fun shareSong() {
        val songData = currentSong.value
        val songId = songData?.song?.id ?: return

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "https://music.youtube.com/watch?v=$songId")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(Intent.createChooser(shareIntent, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    /**
     * Get the stream URL for a given media ID.
     * This is used for Google Cast to send the audio URL to Chromecast.
     */
    suspend fun getStreamUrl(mediaId: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val playbackData = YTPlayerUtils.playerResponseForPlayback(
                    videoId = mediaId,
                    audioQuality = audioQuality,
                    connectivityManager = connectivityManager,
                    context = this@MusicService,
                ).getOrNull()
                playbackData?.streamUrl
            } catch (e: Exception) {
                timber.log.Timber.e(e, "Failed to get stream URL for Cast")
                null
            }
        }
    }

    /**
     * Initialize Google Cast support
     */
    private fun initializeCast() {
        if (dataStore.get(com.music.vivi.constants.EnableGoogleCastKey, true)) {
            try {
                castConnectionHandler = CastConnectionHandler(this, scope, this)
                castConnectionHandler?.initialize()
                castConnectionHandler?.let { handler ->
                    scope.launch {
                        handler.isCasting.collect { isCasting ->
                            if (isCasting) {
                                cancelVideoStallWatchdog("Cast started")
                                resetVideoAutoRetry("Cast started")
                                rebuildCurrentAsAudioOnly(
                                    reason = "Cast started",
                                    permanentVideoFallback = false,
                                )
                            }
                        }
                    }
                }
                timber.log.Timber.d("Google Cast initialized")
            } catch (e: Exception) {
                timber.log.Timber.e(e, "Failed to initialize Google Cast")
            }
        }
    }


    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int
    ) {
        if (reason == Player.DISCONTINUITY_REASON_SEEK) {
            cancelVideoStallWatchdog("seek")
            if (player.playWhenReady && player.playbackState == Player.STATE_BUFFERING) {
                maybeArmVideoStallWatchdog("seek")
            }
            scheduleCrossfade()
        }
    }

    private fun scheduleCrossfade() {
        crossfadeTriggerJob?.cancel()
        crossfadeTriggerJob = null
        if (videoPlaybackRequestedMediaId.value == player.currentMediaItem?.mediaId) return
        if (!crossfadeEnabled || player.duration == C.TIME_UNSET || player.duration <= crossfadeDuration) return
        if (crossfadeGapless && isNextItemGapless()) return
        if (!player.hasNextMediaItem() && player.repeatMode != REPEAT_MODE_ONE) return

        val triggerTime = player.duration - crossfadeDuration.toLong()
        val delayMs = triggerTime - player.currentPosition
        if (delayMs <= 0) return

        val targetMediaId = player.currentMediaItem?.mediaId

        crossfadeTriggerJob = scope.launch {
            delay(delayMs)
            if (isActive && player.isPlaying && player.currentMediaItem?.mediaId == targetMediaId && !sleepTimer.pauseWhenSongEnd) {
                startCrossfade()
            }
        }
    }

    private fun isNextItemGapless(): Boolean {
        val current = player.currentMediaItem?.mediaMetadata ?: return false
        val nextIndex = player.nextMediaItemIndex
        if (nextIndex == C.INDEX_UNSET) return false
        val next = player.getMediaItemAt(nextIndex).mediaMetadata
        return current.albumTitle != null && current.albumTitle == next.albumTitle
    }

    private fun startCrossfade(trigger: CrossfadeTrigger = CrossfadeTrigger.AUTO) {
        if (isCrossfading) return

        // Preserve player state before creating the secondary player
        // Use runBlocking to ensure we get the correct state from DataStore
        val savedRepeatMode = runBlocking { dataStore.get(RepeatModeKey, REPEAT_MODE_OFF) }
        val savedShuffleEnabled = runBlocking { dataStore.get(ShuffleModeKey, false) }

        val targetIndex = when {
            // Manual "previous" skip: crossfade back into the previous track.
            trigger == CrossfadeTrigger.MANUAL_PREVIOUS -> {
                if (!player.hasPreviousMediaItem()) return
                player.previousMediaItemIndex
            }
            // For repeat-one at the natural end of the track, crossfade back into the same track.
            trigger == CrossfadeTrigger.AUTO && savedRepeatMode == REPEAT_MODE_ONE -> player.currentMediaItemIndex
            // Natural end-of-track advance, or a manual "next" skip.
            else -> player.nextMediaItemIndex
        }
        if (targetIndex == C.INDEX_UNSET) return

        secondaryPlayer = createExoPlayer()
        val secPlayer = secondaryPlayer!!
        secPlayer.addListener(secondaryPlayerListener)

        val itemCount = player.mediaItemCount
        val items = mutableListOf<MediaItem>()
        // Copy entire queue history + future
        for (i in 0 until itemCount) {
            items.add(player.getMediaItemAt(i))
        }

        // Capture the primary's current shuffle traversal before the swap.
        // Regenerating a fresh random order here was what caused shuffle to
        // re-randomize on every skip / song selection.
        val preservedShuffleOrder =
            if (savedShuffleEnabled) getCurrentShuffleOrderIndices(player) else null

        secPlayer.setMediaItems(items)
        // Seek to target track (next track, or current track for repeat-one)
        secPlayer.seekTo(targetIndex, 0)
        secPlayer.volume = 0f

        // Copy repeat and shuffle state to the new player
        secPlayer.repeatMode = savedRepeatMode
        secPlayer.shuffleModeEnabled = savedShuffleEnabled

        // Replay the captured shuffle order (same items, same order) instead
        // of generating a new random one. Track whether it was actually applied
        // so we can fall back to a fresh order below if it wasn't.
        val restoredShuffleOrder = savedShuffleEnabled &&
            preservedShuffleOrder != null &&
            preservedShuffleOrder.size == secPlayer.mediaItemCount

        if (restoredShuffleOrder) {
            secPlayer.setShuffleOrder(
                DefaultShuffleOrder(preservedShuffleOrder!!, System.currentTimeMillis())
            )
        }

        secPlayer.prepare()
        secPlayer.playWhenReady = true

        performCrossfadeSwap()

        // If we couldn't carry the order over (e.g. a size mismatch), build a
        // fresh shuffle order on the new primary player as a fallback.
        if (savedShuffleEnabled && !restoredShuffleOrder) {
            val shufflePlaylistFirst = dataStore.get(ShufflePlaylistFirstKey, false)
            applyShuffleOrder(player.currentMediaItemIndex, player.mediaItemCount, shufflePlaylistFirst)
        }
    }

    /**
     * Attempts a crossfaded manual skip to the next track, in response to the
     * user pressing "next" (in-app button/gesture, notification, widget,
     * Bluetooth/headset button, Android Auto, etc.) rather than the track
     * ending naturally.
     *
     * Returns `true` if the crossfade transition was started, meaning the
     * caller should NOT also perform an instant [Player.seekToNext]. Returns
     * `false` when crossfade (or crossfade-on-manual-skip) is disabled, when
     * a crossfade is already in progress, or when there is no next item to
     * crossfade into — in which case the caller should fall back to its
     * normal instant-skip behavior.
     */
    fun manualSkipToNextWithCrossfade(): Boolean {
        if (videoPlaybackRequestedMediaId.value == player.currentMediaItem?.mediaId) return false
        if (!crossfadeEnabled || !crossfadeManualSkipEnabled) return false
        if (isCrossfading) return false
        if (castConnectionHandler?.isCasting?.value == true) return false
        if (!player.hasNextMediaItem()) return false
        if (player.duration == C.TIME_UNSET) return false
        if (crossfadeGapless && isNextItemGapless()) return false

        crossfadeTriggerJob?.cancel()
        crossfadeTriggerJob = null
        startCrossfade(CrossfadeTrigger.MANUAL_NEXT)
        return isCrossfading
    }

    /**
     * Attempts a crossfaded manual skip to the previous track. Same contract
     * as [manualSkipToNextWithCrossfade], but for the "previous" direction.
     * Callers that implement a "restart the current song if we're more than
     * a few seconds in" behavior should only call this from the branch that
     * actually moves to the previous track, not the restart branch.
     */
    fun manualSkipToPreviousWithCrossfade(): Boolean {
        if (videoPlaybackRequestedMediaId.value == player.currentMediaItem?.mediaId) return false
        if (!crossfadeEnabled || !crossfadeManualSkipEnabled) return false
        if (isCrossfading) return false
        if (castConnectionHandler?.isCasting?.value == true) return false
        if (!player.hasPreviousMediaItem()) return false
        if (player.duration == C.TIME_UNSET) return false

        crossfadeTriggerJob?.cancel()
        crossfadeTriggerJob = null
        startCrossfade(CrossfadeTrigger.MANUAL_PREVIOUS)
        return isCrossfading
    }

    /**
     * Attempts a crossfaded jump to an arbitrary track already loaded in the
     * current queue — e.g. tapping an upcoming song in the queue/playlist view
     * (which otherwise does an instant [Player.seekToDefaultPosition] and cuts
     * the audio). Behaves like [manualSkipToNextWithCrossfade] but targets an
     * explicit media-item index instead of the next/previous one.
     *
     * Returns `true` if the crossfade was started, meaning the caller should
     * NOT also perform an instant seek. Returns `false` when crossfade (or
     * crossfade-on-manual-skip) is disabled, when a crossfade is already in
     * progress, when [targetIndex] is the current item or out of range, or when
     * the player isn't actually playing — in which case the caller should fall
     * back to its normal instant-seek behavior.
     */
    fun manualSeekToIndexWithCrossfade(targetIndex: Int): Boolean {
        if (!crossfadeEnabled || !crossfadeManualSkipEnabled) return false
        if (isCrossfading) return false
        if (castConnectionHandler?.isCasting?.value == true) return false
        if (targetIndex < 0 || targetIndex >= player.mediaItemCount) return false
        if (targetIndex == player.currentMediaItemIndex) return false
        if (player.duration == C.TIME_UNSET) return false
        if (!player.isPlaying) return false

        crossfadeTriggerJob?.cancel()
        crossfadeTriggerJob = null
        startCrossfadeToIndex(targetIndex)
        return isCrossfading
    }

    /**
     * Like [startCrossfade], but crossfades into an explicit [targetIndex]
     * within the current queue (the item the user tapped in the queue view)
     * instead of the next/previous item. Copies the primary player's existing
     * (already-resolved) media items onto the secondary player so the target
     * track can start buffering immediately, then swaps and fades.
     */
    private fun startCrossfadeToIndex(targetIndex: Int) {
        if (isCrossfading) return
        if (targetIndex < 0 || targetIndex >= player.mediaItemCount) return

        val savedRepeatMode = runBlocking { dataStore.get(RepeatModeKey, REPEAT_MODE_OFF) }
        val savedShuffleEnabled = runBlocking { dataStore.get(ShuffleModeKey, false) }

        secondaryPlayer = createExoPlayer()
        val secPlayer = secondaryPlayer!!
        secPlayer.addListener(secondaryPlayerListener)

        val itemCount = player.mediaItemCount
        val items = mutableListOf<MediaItem>()
        for (i in 0 until itemCount) {
            items.add(player.getMediaItemAt(i))
        }

        // Capture the primary's current shuffle traversal before the swap so we
        // can replay it on the secondary player instead of re-randomizing.
        val preservedShuffleOrder =
            if (savedShuffleEnabled) getCurrentShuffleOrderIndices(player) else null

        secPlayer.setMediaItems(items)
        secPlayer.seekTo(targetIndex, 0)
        secPlayer.volume = 0f

        secPlayer.repeatMode = savedRepeatMode
        secPlayer.shuffleModeEnabled = savedShuffleEnabled

        val restoredShuffleOrder = savedShuffleEnabled &&
            preservedShuffleOrder != null &&
            preservedShuffleOrder.size == secPlayer.mediaItemCount

        if (restoredShuffleOrder) {
            secPlayer.setShuffleOrder(
                DefaultShuffleOrder(preservedShuffleOrder!!, System.currentTimeMillis())
            )
        }

        secPlayer.prepare()
        secPlayer.playWhenReady = true

        performCrossfadeSwap()

        if (savedShuffleEnabled && !restoredShuffleOrder) {
            val shufflePlaylistFirst = dataStore.get(ShufflePlaylistFirstKey, false)
            applyShuffleOrder(player.currentMediaItemIndex, player.mediaItemCount, shufflePlaylistFirst)
        }
    }

    /**
     * Attempts to crossfade from the currently playing track directly into a
     * manually selected queue — e.g. tapping a song in a playlist, album, or
     * queue screen — instead of abruptly cutting to it. Reuses the same
     * player-swap/fade mechanism as [startCrossfade], but loads a brand new
     * queue on the secondary player instead of items already queued on the
     * primary one.
     *
     * Returns `true` if the crossfade was started, meaning the caller should
     * skip the normal instant-replace path. Returns `false` if a crossfade
     * couldn't be started (e.g. one is already in flight), in which case the
     * caller should fall back to its normal instant-replace behavior.
     */
    private fun startQueueCrossfade(
        status: Queue.Status,
        persistShuffleAcrossQueues: Boolean,
    ): Boolean {
        if (isCrossfading || secondaryPlayer != null) {
            Timber.tag(TAG).d(
                "startQueueCrossfade: aborting, isCrossfading=%s secondaryPlayer!=null=%s",
                isCrossfading,
                secondaryPlayer != null,
            )
            return false
        }

        val savedRepeatMode = runBlocking { dataStore.get(RepeatModeKey, REPEAT_MODE_OFF) }
        val targetIndex = if (status.mediaItemIndex > 0) status.mediaItemIndex else 0

        // If the new queue is actually the same items as the current queue,
        // preserve the existing shuffle traversal instead of re-randomizing.
        val sameQueue = status.items.size == player.mediaItemCount &&
            status.items.indices.all { i ->
                status.items[i].mediaId == player.getMediaItemAt(i).mediaId
            }
        val carryingShuffle = persistShuffleAcrossQueues && player.shuffleModeEnabled
        val preservedShuffleOrder =
            if (carryingShuffle && sameQueue) getCurrentShuffleOrderIndices(player) else null

        secondaryPlayer = createExoPlayer()
        val secPlayer = secondaryPlayer!!
        secPlayer.addListener(secondaryPlayerListener)

        secPlayer.setMediaItems(status.items, targetIndex, status.position)
        secPlayer.volume = 0f
        secPlayer.repeatMode = savedRepeatMode
        secPlayer.shuffleModeEnabled = carryingShuffle

        val restoredShuffleOrder = carryingShuffle && preservedShuffleOrder != null &&
            preservedShuffleOrder.size == secPlayer.mediaItemCount

        if (restoredShuffleOrder) {
            secPlayer.setShuffleOrder(
                DefaultShuffleOrder(preservedShuffleOrder!!, System.currentTimeMillis())
            )
        }

        secPlayer.prepare()
        secPlayer.playWhenReady = true

        performCrossfadeSwap()

        // If shuffle is on but the order wasn't carried over (a genuinely
        // different queue, or a same-queue size mismatch), build a fresh order.
        if (player.shuffleModeEnabled && !restoredShuffleOrder) {
            val shufflePlaylistFirst = dataStore.get(ShufflePlaylistFirstKey, false)
            applyShuffleOrder(player.currentMediaItemIndex, player.mediaItemCount, shufflePlaylistFirst)
        }
        return true
    }

    /**
     * Preload variant of [startQueueCrossfade] for queues that begin from a
     * single "preload" item (Charts, Explore, Stats, Listen Together sync,
     * "Start radio", etc.) whose full contents resolve asynchronously.
     *
     * Instead of waiting for the whole queue, we load just the preload item on
     * the secondary player and start the fade immediately. The caller still
     * runs the background coroutine that resolves the full queue; once it
     * arrives, the existing preload-merge logic inserts the remaining items
     * around the now-playing preload item on the new primary player (which is
     * the former secondary), preserving playback position.
     *
     * Returns `true` if the crossfade was started, meaning the caller should
     * skip the synchronous instant-replace of the preload item. Returns `false`
     * if a crossfade is already in flight, in which case the caller falls back
     * to the normal instant-preload behavior.
     */
    private fun startQueueCrossfadeWithPreload(
        queue: Queue,
        persistShuffleAcrossQueues: Boolean,
    ): Boolean {
        val preloadItem = queue.preloadItem ?: return false
        if (isCrossfading || secondaryPlayer != null) {
            Timber.tag(TAG).d(
                "startQueueCrossfadeWithPreload: aborting, isCrossfading=%s secondaryPlayer!=null=%s",
                isCrossfading,
                secondaryPlayer != null,
            )
            return false
        }

        val savedRepeatMode = runBlocking { dataStore.get(RepeatModeKey, REPEAT_MODE_OFF) }

        secondaryPlayer = createExoPlayer()
        val secPlayer = secondaryPlayer!!
        secPlayer.addListener(secondaryPlayerListener)

        secPlayer.setMediaItem(preloadItem.toMediaItem())
        secPlayer.volume = 0f
        secPlayer.repeatMode = savedRepeatMode
        secPlayer.shuffleModeEnabled = persistShuffleAcrossQueues && player.shuffleModeEnabled

        secPlayer.prepare()
        secPlayer.playWhenReady = true

        Timber.tag(TAG).d("startQueueCrossfadeWithPreload: starting crossfade into preload item")
        performCrossfadeSwap()

        // Rebuild shuffle order on the new primary player if it carried shuffle over
        if (player.shuffleModeEnabled) {
            val shufflePlaylistFirst = dataStore.get(ShufflePlaylistFirstKey, false)
            applyShuffleOrder(player.currentMediaItemIndex, player.mediaItemCount, shufflePlaylistFirst)
        }
        return true
    }

    private fun performCrossfadeSwap() {
        isCrossfading = true
        val nextPlayer = secondaryPlayer ?: return
        val currentPlayer = player

        fadingPlayer = currentPlayer
        player = nextPlayer
        _playerFlow.value = player
        secondaryPlayer = null

        fadingPlayer?.removeListener(this)
        fadingPlayer?.removeListener(sleepTimer)

        // Add listener to sync play/pause state
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isCrossfading && fadingPlayer != null) {
                    if (isPlaying) {
                        fadingPlayer?.play()
                    } else {
                        fadingPlayer?.pause()
                    }
                } else {
                    player.removeListener(this)
                }
            }
        })

        nextPlayer.removeListener(secondaryPlayerListener)
        nextPlayer.addListener(this)
        nextPlayer.addListener(sleepTimer)

        sleepTimer.player = player

        try {
            (mediaSession as MediaSession).player = player
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Failed to swap player in MediaSession")
        }

        crossfadeJob = scope.launch {
            val duration = crossfadeDuration.toLong()
            val steps = 20
            val stepTime = duration / steps
            val startVolume = try { fadingPlayer?.volume ?: 1f } catch(e:Exception) { 1f }

            for (i in 0..steps) {
                if (!isActive) break
                // Pause volume ramp if player is paused
                while (!player.isPlaying && isActive) {
                    delay(100)
                }

                val progress = i / steps.toFloat()
                val fadeIn = crossfadeCurve.fadeIn(progress)
                val fadeOut = crossfadeCurve.fadeOut(progress)

                try {
                    player.volume = startVolume * fadeIn
                    fadingPlayer?.volume = startVolume * fadeOut
                } catch (e: Exception) { break }

                delay(stepTime)
            }

            try {
                fadingPlayer?.volume = 0f
                player.volume = startVolume
                cleanupCrossfade()
            } catch (e: Exception) { }
        }
    }

    private fun cleanupCrossfade() {
        fadingPlayer?.stop()
        fadingPlayer?.clearMediaItems()
        fadingPlayer?.release()
        fadingPlayer = null
        isCrossfading = false
        sleepTimer.notifySongTransition()
    }

    companion object {
        const val ROOT = "root"
        const val SONG = "song"
        const val ARTIST = "artist"
        const val ALBUM = "album"
        const val PLAYLIST = "playlist"
        const val YOUTUBE_PLAYLIST = "youtube_playlist"
        const val SEARCH = "search"
        const val SHUFFLE_ACTION = "__shuffle__"

        const val CHANNEL_ID = "music_channel_01"
        const val NOTIFICATION_ID = 888
        const val ERROR_CODE_NO_STREAM = 1000001
        const val CHUNK_LENGTH = 512 * 1024L
        const val PERSISTENT_QUEUE_FILE = "persistent_queue.data"
        const val PERSISTENT_AUTOMIX_FILE = "persistent_automix.data"
        const val PERSISTENT_PLAYER_STATE_FILE = "persistent_player_state.data"
        const val MAX_CONSECUTIVE_ERR = 5
        const val MAX_RETRY_COUNT = 10
        // Constants for audio normalization
        private const val MAX_GAIN_MB = 300 // Maximum gain in millibels (3 dB)
        private const val MIN_GAIN_MB = -1500 // Minimum gain in millibels (-15 dB)

        private const val TAG = "MusicService"

        @Volatile
        var isRunning = false
            private set
    }
}

private data class SponsorBlockConfig(
    val enabled: Boolean,
    val serverUrl: String,
    val skipNonMusic: Boolean,
    val skipSponsor: Boolean,
    val skipSelfPromo: Boolean,
    val skipInteraction: Boolean,
    val skipIntroOutro: Boolean,
    val skipPreviewFiller: Boolean,
    val showToast: Boolean,
)
