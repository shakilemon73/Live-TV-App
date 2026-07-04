package com.example.ui.components

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.data.StreamSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// Resilience Playback Buffer Constants
const val LOW_LATENCY_MIN_BUFFER_MS = 10000
const val LOW_LATENCY_MAX_BUFFER_MS = 30000
const val LOW_LATENCY_BUFFER_FOR_PLAYBACK_MS = 1500
const val LOW_LATENCY_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 3000

const val STANDARD_MIN_BUFFER_MS = 45000
const val STANDARD_MAX_BUFFER_MS = 90000
const val STANDARD_BUFFER_FOR_PLAYBACK_MS = 3000
const val STANDARD_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 6000

const val LOW_LATENCY_BACK_BUFFER_MS = 10000
const val STANDARD_BACK_BUFFER_MS = 20000

@UnstableApi
data class PlaybackDiagnostics(
    val state: String = "Idle",
    val isBuffering: Boolean = false,
    val bufferDurationMs: Long = 0L,
    val liveOffsetMs: Long = -1L,
    val playbackSpeed: Float = 1.0f,
    val isLowLatencyActive: Boolean = false,
    val targetOffsetMs: Long = 0L,
    val bitrateEstimate: Long = 0L,
    val timeToFirstFrameMs: Long = 0L,
    val rebufferCount: Int = 0
)

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    videoUrl: String,
    isPlaying: Boolean,
    isMuted: Boolean,
    resizeMode: Int,
    lowLatencyEnabled: Boolean = true,
    maxVideoWidth: Int = Int.MAX_VALUE,
    maxVideoHeight: Int = Int.MAX_VALUE,
    onVideoSizeChanged: ((Int, Int) -> Unit)? = null,
    onPlayerErrorOccurred: ((PlaybackException) -> Unit)? = null,
    onDiagnosticsUpdated: ((PlaybackDiagnostics) -> Unit)? = null,
    onPlayerReady: ((Player?) -> Unit)? = null,
    modifier: Modifier = Modifier,
    streams: List<StreamSource> = emptyList(),
    onStreamSwapped: ((StreamSource) -> Unit)? = null
) {
    val context = LocalContext.current
    var hasError by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var isLocked by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    var retryCount by remember(videoUrl) { mutableStateOf(0) }
    val failedUrlsState = remember(streams) { mutableStateOf(emptySet<String>()) }
    var prepareStartTime by remember(videoUrl) { mutableStateOf(0L) }
    var timeToFirstFrameMs by remember(videoUrl) { mutableStateOf(0L) }
    var rebufferCount by remember(videoUrl) { mutableStateOf(0) }
    var hasReachedReady by remember(videoUrl) { mutableStateOf(false) }

    var isAppActive by remember { mutableStateOf(true) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                isAppActive = true
            } else if (event == Lifecycle.Event.ON_STOP) {
                isAppActive = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val customBandwidthMeter = remember(context, isAppActive) {
        if (!isAppActive) null
        else androidx.media3.exoplayer.upstream.DefaultBandwidthMeter.Builder(context)
            .setInitialBitrateEstimate(2_000_000L) // 2 Mbps initial estimate for snappy start
            .setSlidingWindowMaxWeight(1000)      // Highly responsive sliding window
            .build()
    }

    // Create optimized ExoPlayer instance with customized high-performance live buffers and adaptive track selection
    val exoPlayer = remember(context, lowLatencyEnabled, isAppActive, customBandwidthMeter) {
        if (!isAppActive || customBandwidthMeter == null) return@remember null

        val playerContext = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            context.createAttributionContext("default")
        } else {
            context
        }

        // 1. Configure the Adaptive Track Selection Factory to drop quality aggressively but upgrade conservatively
        val trackSelectionFactory = AdaptiveTrackSelection.Factory(
            /* minDurationForQualityIncreaseMs = */ 12000,  // Wait 12s of high bandwidth before upgrading quality to avoid toggle loops
            /* maxDurationForQualityDecreaseMs = */ 1000,   // Instantly (1s) drop resolution if bandwidth plummets to avoid freezing
            /* minDurationToRetainAfterDiscardMs = */ 15000, // Safe discard distance to maintain stream continuity
            /* bandwidthFraction = */ 0.70f                // Assume only 70% of estimated bandwidth is usable to be cautious
        )
        val trackSelector = DefaultTrackSelector(playerContext, trackSelectionFactory)

        // 2. Customize LoadControl for stable live stream zapping and robust buffer management
        val loadControl = if (lowLatencyEnabled) {
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    LOW_LATENCY_MIN_BUFFER_MS,
                    LOW_LATENCY_MAX_BUFFER_MS,
                    LOW_LATENCY_BUFFER_FOR_PLAYBACK_MS,
                    LOW_LATENCY_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
                )
                .setBackBuffer(LOW_LATENCY_BACK_BUFFER_MS, true)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()
        } else {
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    STANDARD_MIN_BUFFER_MS,
                    STANDARD_MAX_BUFFER_MS,
                    STANDARD_BUFFER_FOR_PLAYBACK_MS,
                    STANDARD_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
                )
                .setBackBuffer(STANDARD_BACK_BUFFER_MS, true)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()
        }

        val renderersFactory = DefaultRenderersFactory(playerContext).apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        }

        // 3. Integrate shared cache datasource into the MediaSource factory with playlist-aware live routing
        val httpDataSourceFactory = androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(com.example.data.CachedHttpClient.getClient(playerContext))

        val liveTvDataSourceFactory = LiveTvDataSourceFactory(
            playerContext,
            VideoCacheManager.getCache(playerContext),
            httpDataSourceFactory
        )

        val mediaSourceFactory = DefaultMediaSourceFactory(playerContext)
            .setDataSourceFactory(liveTvDataSourceFactory)

        ExoPlayer.Builder(playerContext)
            .setRenderersFactory(renderersFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setBandwidthMeter(customBandwidthMeter)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().apply {
                repeatMode = ExoPlayer.REPEAT_MODE_OFF
                
                // Register custom high-fidelity diagnostic listeners to track bandwidth metrics and exact events
                addAnalyticsListener(object : androidx.media3.exoplayer.analytics.AnalyticsListener {
                    override fun onBandwidthEstimate(
                        eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                        elapsedMs: Int,
                        bytes: Long,
                        bitrateEstimate: Long
                    ) {
                        android.util.Log.d(
                            "ExoPlayerDiagnostics",
                            "Bandwidth update: bitrate=${bitrateEstimate / 1000} kbps, bytes=$bytes, elapsedMs=$elapsedMs"
                        )
                    }

                    override fun onLoadError(
                        eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                        loadEventInfo: androidx.media3.exoplayer.source.LoadEventInfo,
                        mediaLoadData: androidx.media3.exoplayer.source.MediaLoadData,
                        error: java.io.IOException,
                        wasCanceled: Boolean
                    ) {
                        android.util.Log.e(
                            "ExoPlayerDiagnostics",
                            "HLS/Stream Load Error: uri=${loadEventInfo.uri}, loadDuration=${loadEventInfo.loadDurationMs}ms, error=${error.message}, wasCanceled=$wasCanceled"
                        )
                    }

                    override fun onPlayerError(
                        eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                        error: PlaybackException
                    ) {
                        android.util.Log.e(
                            "ExoPlayerDiagnostics",
                            "Playback error: code=${error.errorCodeName} (${error.errorCode}), msg=${error.message}"
                        )
                    }

                    override fun onPlaybackStateChanged(
                        eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                        state: Int
                    ) {
                        val name = when (state) {
                            Player.STATE_IDLE -> "IDLE"
                            Player.STATE_BUFFERING -> "BUFFERING"
                            Player.STATE_READY -> "READY"
                            Player.STATE_ENDED -> "ENDED"
                            else -> "UNKNOWN"
                        }
                        android.util.Log.d("ExoPlayerDiagnostics", "Playback State changed to: $name")
                    }
                })
            }
    }

    // Add listener to monitor buffering and player errors
    DisposableEffect(exoPlayer) {
        if (exoPlayer == null) return@DisposableEffect onDispose {}
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    hasError = false
                    if (!hasReachedReady) {
                        hasReachedReady = true
                    }
                }
                if (state == Player.STATE_BUFFERING && hasReachedReady) {
                    rebufferCount++
                    android.util.Log.d("LiveTvTelemetry", "Rebuffer detected. Total rebuffers in session: $rebufferCount")
                }
                // Introduce isLocked flag: true while buffer event or current playback state is active
                isLocked = (state == Player.STATE_BUFFERING || state == Player.STATE_READY)
                android.util.Log.d("VideoPlayer", "onPlaybackStateChanged: state=$state, isLocked=$isLocked")
            }

            override fun onPlayerError(error: PlaybackException) {
                isLocked = false // Release lock on error to allow manual retry or automatic channel switch
                
                if (retryCount < 3) {
                    val backoffDelay = when (retryCount) {
                        0 -> 1000L
                        1 -> 2000L
                        else -> 4000L
                    }
                    android.util.Log.w("VideoPlayer", "Playback error (code=${error.errorCodeName}). Retrying in ${backoffDelay}ms (Attempt ${retryCount + 1}/3)...")
                    retryCount++
                    scope.launch {
                        delay(backoffDelay)
                        if (exoPlayer != null) {
                            isBuffering = true
                            exoPlayer.prepare()
                            exoPlayer.play()
                        }
                    }
                } else {
                    android.util.Log.e("VideoPlayer", "All 3 retries failed for current source: $videoUrl. Checking for other sources...")
                    failedUrlsState.value = failedUrlsState.value + videoUrl
                    val untriedStreams = streams.filter { it.url !in failedUrlsState.value && !it.isBroken }
                    val nextStream = untriedStreams.firstOrNull() ?: streams.filter { it.url !in failedUrlsState.value }.firstOrNull()
                    
                    if (nextStream != null && onStreamSwapped != null) {
                        android.util.Log.i("VideoPlayer", "Automatic failover: swapping from $videoUrl to alternative source: ${nextStream.subName} (${nextStream.url})")
                        onStreamSwapped.invoke(nextStream)
                    } else {
                        android.util.Log.e("VideoPlayer", "All alternative streams failed or no other sources available.")
                        hasError = true
                        onPlayerErrorOccurred?.invoke(error)
                    }
                }
            }

            override fun onRenderedFirstFrame() {
                if (prepareStartTime > 0L && timeToFirstFrameMs == 0L) {
                    timeToFirstFrameMs = System.currentTimeMillis() - prepareStartTime
                    android.util.Log.i("LiveTvTelemetry", "Time-To-First-Frame (TTFF): ${timeToFirstFrameMs}ms for url: $videoUrl")
                }
            }

            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                onVideoSizeChanged?.invoke(videoSize.width, videoSize.height)
            }
        }
        exoPlayer.addListener(listener)
        onPlayerReady?.invoke(exoPlayer)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
            onPlayerReady?.invoke(null)
        }
    }

    // Play/prepare when URL or settings change with precise Live Configuration
    LaunchedEffect(exoPlayer, videoUrl, lowLatencyEnabled) {
        if (exoPlayer == null) return@LaunchedEffect
        
        // Prevent redundant channel selection logic from triggering prepare() if already actively playing/buffering the same URL
        val currentMediaUri = exoPlayer.currentMediaItem?.localConfiguration?.uri?.toString()
        val isSameUrl = currentMediaUri == videoUrl
        
        if (isLocked && isSameUrl) {
            android.util.Log.d("VideoPlayer", "Playback lock is ACTIVE; skipping ExoPlayer.prepare() to prevent state thrashing.")
            return@LaunchedEffect
        }
        
        // Reset lock on loading a new stream
        if (!isSameUrl) {
            isLocked = false
        }
        
        hasError = false
        isBuffering = true
        
        prepareStartTime = System.currentTimeMillis()
        timeToFirstFrameMs = 0L
        rebufferCount = 0
        hasReachedReady = false
        
        val mimeType = getMimeTypeForUrl(videoUrl)
        
        val liveConfig = if (lowLatencyEnabled) {
            MediaItem.LiveConfiguration.Builder()
                .setTargetOffsetMs(2000) // Aim to stay exactly 2 seconds behind live edge
                .setMinPlaybackSpeed(0.95f)
                .setMaxPlaybackSpeed(1.05f)
                .build()
        } else {
            MediaItem.LiveConfiguration.Builder()
                .setTargetOffsetMs(5500) // Optimal 5.5s live edge offset fallback
                .setMinPlaybackSpeed(0.95f)
                .setMaxPlaybackSpeed(1.05f)
                .build()
        }

        val mediaItem = MediaItem.Builder()
            .setUri(videoUrl)
            .setMimeType(mimeType)
            .setLiveConfiguration(liveConfig)
            .build()
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = isPlaying
    }

    // Sync playing state
    LaunchedEffect(exoPlayer, isPlaying) {
        exoPlayer?.playWhenReady = isPlaying
    }

    // Sync video quality constraints
    LaunchedEffect(exoPlayer, maxVideoWidth, maxVideoHeight) {
        if (exoPlayer == null) return@LaunchedEffect
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
            .buildUpon()
            .setMaxVideoSize(maxVideoWidth, maxVideoHeight)
            .build()
    }

    // Sync mute state
    LaunchedEffect(exoPlayer, isMuted) {
        if (exoPlayer == null) return@LaunchedEffect
        exoPlayer.volume = if (isMuted) 0f else 1f
    }

    // Real-time diagnostics polling
    LaunchedEffect(exoPlayer, lowLatencyEnabled, customBandwidthMeter, timeToFirstFrameMs, rebufferCount) {
        if (exoPlayer == null || onDiagnosticsUpdated == null) return@LaunchedEffect
        while (isActive) {
            val stateStr = when (exoPlayer.playbackState) {
                Player.STATE_IDLE -> "Idle"
                Player.STATE_BUFFERING -> "Buffering"
                Player.STATE_READY -> "Ready"
                Player.STATE_ENDED -> "Ended"
                else -> "Unknown"
            }
            val bufDuration = (exoPlayer.bufferedPosition - exoPlayer.currentPosition).coerceAtLeast(0L)
            val liveOffset = exoPlayer.currentLiveOffset
            val speed = exoPlayer.playbackParameters.speed
            val targetOffset = if (lowLatencyEnabled) 2000L else 5500L
            val bitrateEst = customBandwidthMeter?.bitrateEstimate ?: 0L

            onDiagnosticsUpdated(
                PlaybackDiagnostics(
                    state = stateStr,
                    isBuffering = exoPlayer.playbackState == Player.STATE_BUFFERING,
                    bufferDurationMs = bufDuration,
                    liveOffsetMs = if (liveOffset == androidx.media3.common.C.TIME_UNSET) -1L else liveOffset,
                    playbackSpeed = speed,
                    isLowLatencyActive = lowLatencyEnabled,
                    targetOffsetMs = targetOffset,
                    bitrateEstimate = bitrateEst,
                    timeToFirstFrameMs = timeToFirstFrameMs,
                    rebufferCount = rebufferCount
                )
            )
            delay(250)
        }
    }

    // View Container containing AndroidView and Overlay States
    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false // Custom controllers are managed in Compose HUD
                    keepScreenOn = true
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { view ->
                view.resizeMode = resizeMode
                if (view.player != exoPlayer) {
                    view.player = exoPlayer
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Overlay 1: Buffering/Loading Indicator (only when there's no error)
        if ((isBuffering || exoPlayer == null) && !hasError) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFFA855F7), // Elegant matching accent color
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Buffering Stream...",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Overlay 2: No Internet / Stream Unavailable Overlay with Retry Action
        if (hasError && exoPlayer != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth(0.85f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Stream Unavailable",
                        tint = Color(0xFFFF5252), // Error color Red
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Stream Unavailable or Offline",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "The live broadcast is currently unreachable. Please check your internet connection or try again shortly.",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = {
                            hasError = false
                            isBuffering = true
                            exoPlayer.prepare()
                            exoPlayer.play()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFA855F7),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Retry Connection",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Retry Playback",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@UnstableApi
private fun getMimeTypeForUrl(url: String): String? {
    val lowerUrl = url.lowercase()
    return when {
        lowerUrl.contains(".m3u8") || lowerUrl.contains("/hls/") || lowerUrl.contains("/m3u8") -> MimeTypes.APPLICATION_M3U8
        lowerUrl.contains(".mpd") || lowerUrl.contains("/dash/") || lowerUrl.contains("/mpd") -> MimeTypes.APPLICATION_MPD
        lowerUrl.contains(".ism") || lowerUrl.contains("/smooth/") || lowerUrl.contains("/manifest") -> MimeTypes.APPLICATION_SS
        lowerUrl.contains(".ts") || lowerUrl.contains("/mpegts") || lowerUrl.contains("ext=ts") || lowerUrl.contains("/ts") -> "video/mp2t"
        lowerUrl.contains(".mp4") -> MimeTypes.VIDEO_MP4
        lowerUrl.contains(".mkv") -> "video/x-matroska"
        lowerUrl.contains(".flv") -> "video/x-flv"
        lowerUrl.startsWith("rtsp://") || lowerUrl.startsWith("rtsps://") -> MimeTypes.APPLICATION_RTSP
        else -> null
    }
}



@UnstableApi
class LiveTvDataSourceFactory(
    private val context: android.content.Context,
    private val cache: androidx.media3.datasource.cache.SimpleCache,
    private val upstreamFactory: androidx.media3.datasource.DataSource.Factory
) : androidx.media3.datasource.DataSource.Factory {
    override fun createDataSource(): androidx.media3.datasource.DataSource {
        val upstreamDataSource = upstreamFactory.createDataSource()
        val cacheDataSource = androidx.media3.datasource.cache.CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            .createDataSource()

        return LiveTvDataSource(upstreamDataSource, cacheDataSource)
    }
}

@UnstableApi
class LiveTvDataSource(
    private val upstreamDataSource: androidx.media3.datasource.DataSource,
    private val cacheDataSource: androidx.media3.datasource.DataSource
) : androidx.media3.datasource.DataSource {
    private var activeDataSource: androidx.media3.datasource.DataSource = cacheDataSource

    override fun addTransferListener(transferListener: androidx.media3.datasource.TransferListener) {
        upstreamDataSource.addTransferListener(transferListener)
        cacheDataSource.addTransferListener(transferListener)
    }

    @Throws(java.io.IOException::class)
    override fun open(dataSpec: androidx.media3.datasource.DataSpec): Long {
        val uriString = dataSpec.uri.toString().lowercase()
        val isPlaylist = uriString.contains(".m3u8") || 
                         uriString.contains(".mpd") || 
                         uriString.contains(".ism") || 
                         uriString.contains("/hls/") || 
                         uriString.contains("/dash/") || 
                         uriString.contains("/m3u8") ||
                         uriString.contains("/smooth/") ||
                         uriString.contains("/manifest")

        activeDataSource = if (isPlaylist) {
            upstreamDataSource
        } else {
            cacheDataSource
        }
        return activeDataSource.open(dataSpec)
    }

    @Throws(java.io.IOException::class)
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        return activeDataSource.read(buffer, offset, length)
    }

    override fun getUri(): android.net.Uri? {
        return activeDataSource.getUri()
    }

    override fun getResponseHeaders(): Map<String, List<String>> {
        return activeDataSource.getResponseHeaders()
    }

    @Throws(java.io.IOException::class)
    override fun close() {
        activeDataSource.close()
    }
}

