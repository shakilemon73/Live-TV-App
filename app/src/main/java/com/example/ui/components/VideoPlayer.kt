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
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    videoUrl: String,
    isPlaying: Boolean,
    isMuted: Boolean,
    resizeMode: Int,
    maxVideoWidth: Int = Int.MAX_VALUE,
    maxVideoHeight: Int = Int.MAX_VALUE,
    onVideoSizeChanged: ((Int, Int) -> Unit)? = null,
    onPlayerErrorOccurred: ((PlaybackException) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var hasError by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }

    // Create optimized ExoPlayer instance with customized high-performance live buffers and adaptive track selection
    val exoPlayer = remember(context) {
        // 1. Configure the Adaptive Track Selection Factory to drop quality aggressively but upgrade conservatively
        val trackSelectionFactory = AdaptiveTrackSelection.Factory(
            /* minDurationForQualityIncreaseMs = */ 12000,  // Wait 12s of high bandwidth before upgrading quality to avoid toggle loops
            /* maxDurationForQualityDecreaseMs = */ 1000,   // Instantly (1s) drop resolution if bandwidth plummets to avoid freezing
            /* minDurationToRetainAfterDiscardMs = */ 15000, // Safe discard distance to maintain stream continuity
            /* bandwidthFraction = */ 0.70f                // Assume only 70% of estimated bandwidth is usable to be cautious
        )
        val trackSelector = DefaultTrackSelector(context, trackSelectionFactory)

        // 2. Customize LoadControl for sub-second live stream zapping and minimal latency
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                1500,  // minBufferMs
                2500,  // maxBufferMs (Maximum buffer size of 2500ms as requested)
                600,   // bufferForPlaybackMs (Playback start buffer of 600ms for instant channel switching)
                1000   // bufferForPlaybackAfterRebufferMs
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val renderersFactory = DefaultRenderersFactory(context).apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        }

        ExoPlayer.Builder(context)
            .setRenderersFactory(renderersFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .build().apply {
                repeatMode = ExoPlayer.REPEAT_MODE_OFF
            }
    }

    // Add listener to monitor buffering and player errors
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    hasError = false
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                hasError = true
                onPlayerErrorOccurred?.invoke(error)
            }

            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                onVideoSizeChanged?.invoke(videoSize.width, videoSize.height)
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    // Play/prepare when URL changes with precise Live Configuration
    LaunchedEffect(videoUrl) {
        hasError = false
        isBuffering = true
        val isHls = videoUrl.contains(".m3u8") || videoUrl.contains("/hls/")
        val mediaItem = MediaItem.Builder()
            .setUri(videoUrl)
            .setMimeType(if (isHls) MimeTypes.APPLICATION_M3U8 else null)
            .setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder()
                    .setTargetOffsetMs(5500) // Optimal 5.5s live edge offset to give the jitter buffer room to breathe
                    .setMinPlaybackSpeed(0.95f)
                    .setMaxPlaybackSpeed(1.05f)
                    .build()
            )
            .build()
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = isPlaying
    }

    // Sync playing state
    LaunchedEffect(isPlaying) {
        exoPlayer.playWhenReady = isPlaying
    }

    // Sync video quality constraints
    LaunchedEffect(maxVideoWidth, maxVideoHeight) {
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
            .buildUpon()
            .setMaxVideoSize(maxVideoWidth, maxVideoHeight)
            .build()
    }

    // Sync mute state
    LaunchedEffect(isMuted) {
        exoPlayer.volume = if (isMuted) 0f else 1f
    }

    // Release player on dispose
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
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
            },
            modifier = Modifier.fillMaxSize()
        )

        // Overlay 1: Buffering/Loading Indicator (only when there's no error)
        if (isBuffering && !hasError) {
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
        if (hasError) {
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

