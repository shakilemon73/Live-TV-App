package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import coil.request.CachePolicy
import coil.request.ImageRequest
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.common.PlaybackException
import coil.compose.AsyncImage
import com.example.data.ChannelEntity
import com.example.data.GroupedChannel
import com.example.data.StreamSource
import com.example.ui.ChannelViewModel
import com.example.ui.components.VideoPlayer
import com.example.ui.components.rememberResponsiveGridSpec
import com.example.ui.components.ResponsiveGridSpec
import androidx.compose.ui.draw.scale
import kotlinx.coroutines.delay
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.media3.common.Player
import android.media.AudioManager
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.shape.CircleShape

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun PlayerScreen(
    viewModel: ChannelViewModel,
    onNavigateBack: () -> Unit,
    onEnterPip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spec = rememberResponsiveGridSpec()
    val handleBack = remember(viewModel, onNavigateBack) {
        { viewModel.handleBackNavigation(onNavigateBack) }
    }
    BackHandler {
        handleBack()
    }
    val selectedChannel by viewModel.selectedChannel.collectAsStateWithLifecycle()
    val currentStreamUrl by viewModel.currentStreamUrl.collectAsStateWithLifecycle()
    val currentStreamName by viewModel.currentStreamName.collectAsStateWithLifecycle()
    val currentEpgProgram by viewModel.currentChannelEpgProgram.collectAsStateWithLifecycle()
    val epgPrograms by viewModel.currentChannelEpgPrograms.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val allChannels by viewModel.filteredChannels.collectAsStateWithLifecycle()
    val lowLatencyEnabled by viewModel.lowLatencyMode.collectAsStateWithLifecycle()
    val allDbChannels by viewModel.channels.collectAsStateWithLifecycle()
    val isInPipMode by viewModel.isInPipMode.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    val groupedChannel = remember(selectedChannel, allDbChannels) {
        if (selectedChannel == null) null
        else {
            val groups = viewModel.groupChannels(allDbChannels)
            groups.find { group ->
                group.originalChannelIds.contains(selectedChannel?.id)
            }
        }
    }

    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    val recordingDuration by viewModel.recordingDuration.collectAsStateWithLifecycle()
    val recordingChannelId by viewModel.recordingChannelId.collectAsStateWithLifecycle()

    val formattedDuration = remember(recordingDuration) {
        val mins = recordingDuration / 60
        val secs = recordingDuration % 60
        String.format("%02d:%02d", mins, secs)
    }

    var showControls by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var isMuted by remember { mutableStateOf(false) }
    var resizeMode by remember { mutableStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }

    var aspectScalingMode by remember { mutableStateOf("Auto") }

    val lazyListState = rememberLazyListState()
    val isScrolling by remember {
        derivedStateOf { lazyListState.isScrollInProgress }
    }
    var videoWidth by remember { mutableStateOf(0) }
    var videoHeight by remember { mutableStateOf(0) }

    var showDescriptionCard by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showStreamsDropdown by remember { mutableStateOf(false) }
    var unmeteredSyncOnly by remember { mutableStateOf(viewModel.getUnmeteredSyncOnly()) }
    var diagnostics by remember { mutableStateOf<com.example.ui.components.PlaybackDiagnostics?>(null) }

    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var isLandscape by remember { mutableStateOf(true) }
    var isRotationLocked by remember { mutableStateOf(false) }

    LaunchedEffect(isRotationLocked) {
        activity?.let { act ->
            if (isRotationLocked) {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            } else {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
        }
    }

    val isTv = remember(context) {
        val pm = context.packageManager
        pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK) ||
        (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK) == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
    }

    val fittingStrategyLabel = remember(aspectScalingMode, videoWidth, videoHeight, isTv, isLandscape) {
        val screenType = when {
            isTv -> "TV"
            isLandscape -> "Mobile Landscape"
            else -> "Mobile Portrait"
        }
        val videoText = if (videoWidth > 0 && videoHeight > 0) "${videoWidth}x${videoHeight}" else "Detecting..."
        
        when (aspectScalingMode) {
            "Fit" -> "Fit ($videoText)"
            "Zoom" -> "Zoom ($videoText)"
            "Stretch" -> "Stretch ($videoText)"
            else -> {
                "Auto Fit: $screenType (Perfect Fit)"
            }
        }
    }

    val effectiveResizeMode = remember(aspectScalingMode, videoWidth, videoHeight, isTv, isLandscape) {
        when (aspectScalingMode) {
            "Fit" -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            "Zoom" -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            "Stretch" -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            else -> {
                // To prevent cutting off any part of the channel's stream on any mobile or TV screen,
                // Auto-select mode always utilizes RESIZE_MODE_FIT to keep the entire content visible.
                AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        }
    }

    var brightness by remember {
        mutableStateOf(
            try {
                val systemBrightness = android.provider.Settings.System.getInt(
                    context.contentResolver,
                    android.provider.Settings.System.SCREEN_BRIGHTNESS
                ) / 255f
                systemBrightness.coerceIn(0.01f, 1.0f)
            } catch (e: Exception) {
                0.5f
            }
        )
    }
    var showBrightnessIndicator by remember { mutableStateOf(false) }

    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val maxVolume = remember(audioManager) {
        audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    }
    var volumeFloat by remember {
        mutableStateOf(
            try {
                audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume
            } catch (e: Exception) {
                0.5f
            }
        )
    }
    var showVolumeIndicator by remember { mutableStateOf(false) }

    var activePlayer by remember { mutableStateOf<Player?>(null) }
    var showRewindIndicator by remember { mutableStateOf(false) }
    var showForwardIndicator by remember { mutableStateOf(false) }

    // Sync volume settings with AudioManager
    LaunchedEffect(volumeFloat) {
        try {
            val targetVolume = (volumeFloat * maxVolume).toInt().coerceIn(0, maxVolume)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0)
        } catch (e: Exception) {
            // Ignore volume errors
        }
    }

    // Auto-hide volume slider after 1.5 seconds of no drag action
    LaunchedEffect(volumeFloat, showVolumeIndicator) {
        if (showVolumeIndicator) {
            delay(1500)
            showVolumeIndicator = false
        }
    }

    // Auto-hide double tap seek indicators
    LaunchedEffect(showRewindIndicator) {
        if (showRewindIndicator) {
            delay(650)
            showRewindIndicator = false
        }
    }
    LaunchedEffect(showForwardIndicator) {
        if (showForwardIndicator) {
            delay(650)
            showForwardIndicator = false
        }
    }

    var selectedQuality by remember { mutableStateOf("Auto") }
    var networkQualityLabel by remember { mutableStateOf("Checking...") }
    var activeMaxWidth by remember { mutableStateOf(Int.MAX_VALUE) }
    var activeMaxHeight by remember { mutableStateOf(Int.MAX_VALUE) }

    // Dynamically monitor network quality if Auto is selected
    LaunchedEffect(selectedQuality) {
        if (selectedQuality == "Auto") {
            while (true) {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                if (cm != null) {
                    val activeNetwork = cm.activeNetwork
                    val capabilities = cm.getNetworkCapabilities(activeNetwork)
                    if (capabilities != null) {
                        val speedKbps = capabilities.linkDownstreamBandwidthKbps
                        when {
                            speedKbps >= 15000 -> {
                                networkQualityLabel = "Auto (Excellent ~${speedKbps / 1000} Mbps)"
                                activeMaxWidth = 1920
                                activeMaxHeight = 1080
                            }
                            speedKbps >= 5000 -> {
                                networkQualityLabel = "Auto (Good ~${speedKbps / 1000} Mbps)"
                                activeMaxWidth = 1280
                                activeMaxHeight = 720
                            }
                            speedKbps >= 1500 -> {
                                networkQualityLabel = "Auto (Moderate ~${speedKbps / 1000} Mbps)"
                                activeMaxWidth = 854
                                activeMaxHeight = 480
                            }
                            else -> {
                                val speedText = if (speedKbps > 0) "~$speedKbps Kbps" else "Low"
                                networkQualityLabel = "Auto (Low $speedText)"
                                activeMaxWidth = 640
                                activeMaxHeight = 360
                            }
                        }
                    } else {
                        networkQualityLabel = "Auto (No Speed info)"
                        activeMaxWidth = 640
                        activeMaxHeight = 360
                    }
                } else {
                    networkQualityLabel = "Auto (Default)"
                    activeMaxWidth = Int.MAX_VALUE
                    activeMaxHeight = Int.MAX_VALUE
                }
                delay(4000)
            }
        } else {
            networkQualityLabel = ""
            when (selectedQuality) {
                "1080p" -> {
                    activeMaxWidth = 1920
                    activeMaxHeight = 1080
                }
                "720p" -> {
                    activeMaxWidth = 1280
                    activeMaxHeight = 720
                }
                "480p" -> {
                    activeMaxWidth = 854
                    activeMaxHeight = 480
                }
                "360p" -> {
                    activeMaxWidth = 640
                    activeMaxHeight = 360
                }
            }
        }
    }

    // Set screen brightness on start and on brightness changes
    LaunchedEffect(brightness) {
        activity?.let { act ->
            val lp = act.window.attributes
            lp.screenBrightness = brightness
            act.window.attributes = lp
        }
    }

    // Auto-hide brightness slider after 1.5 seconds of no drag action
    LaunchedEffect(brightness, showBrightnessIndicator) {
        if (showBrightnessIndicator) {
            delay(1500)
            showBrightnessIndicator = false
        }
    }

    // Control status bars & navigation bars depending on orientation
    val window = activity?.window
    if (window != null) {
        val windowInsetsController = remember(window) {
            WindowCompat.getInsetsController(window, window.decorView)
        }
        LaunchedEffect(isLandscape) {
            if (isLandscape) {
                // Hide status and navigation bars for full immersive stream
                windowInsetsController.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
                windowInsetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                windowInsetsController.show(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
            }
        }
    }

    DisposableEffect(activity, viewModel) {
        // Force Landscape Lock on startup
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        onDispose {
            // Reset selected channel to completely stop streaming when leaving the player screen
            viewModel.selectChannel(null)

            // Restore orientation and system bars when leaving
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            val win = activity?.window
            if (win != null) {
                val controller = WindowCompat.getInsetsController(win, win.decorView)
                controller.show(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
            }
        }
    }

    // Force hide controls when entering PiP mode
    LaunchedEffect(isInPipMode) {
        if (isInPipMode) {
            showControls = false
        }
    }

    // Auto-hide controls after 6 seconds of inactivity
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(6000)
            showControls = false
        }
    }

    val channel = selectedChannel
    val sameTypeChannels = remember(channel, allDbChannels) {
        if (channel != null) {
            allDbChannels.filter { it.categoryId == channel.categoryId }
        } else {
            emptyList()
        }
    }
    if (channel == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = "No Stream",
                    tint = Color.Gray,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("No stream selected", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = handleBack,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD0BCFF), contentColor = Color(0xFF381E72))
                ) {
                    Text("Go Back")
                }
            }
        }
        return
    }

    val categoryName = categories.find { it.id == channel.categoryId }?.name ?: "General"

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { offset ->
                        val width = size.width
                        if (offset.x < width / 2) {
                            activePlayer?.let { player ->
                                val target = (player.currentPosition - 10000).coerceAtLeast(0)
                                player.seekTo(target)
                            }
                            showRewindIndicator = true
                        } else {
                            activePlayer?.let { player ->
                                val target = player.currentPosition + 10000
                                val duration = if (player.duration > 0) player.duration else Long.MAX_VALUE
                                player.seekTo(target.coerceIn(0L, duration))
                            }
                            showForwardIndicator = true
                        }
                    },
                    onTap = {
                        if (!isInPipMode) {
                            showControls = !showControls
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                var dragType = 0 // 0 = none, 1 = brightness (left edge), 2 = volume (right edge)
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        val width = size.width
                        if (offset.x < width * 0.35f) {
                            dragType = 1
                            showBrightnessIndicator = true
                        } else if (offset.x > width * 0.65f) {
                            dragType = 2
                            showVolumeIndicator = true
                        } else {
                            dragType = 0
                        }
                    },
                    onDragEnd = {
                        showBrightnessIndicator = false
                        showVolumeIndicator = false
                    },
                    onDragCancel = {
                        showBrightnessIndicator = false
                        showVolumeIndicator = false
                    },
                    onVerticalDrag = { change, dragAmount ->
                        if (dragType != 0) {
                            change.consume()
                            val sensitivity = 500f
                            val delta = -dragAmount / sensitivity
                            if (dragType == 1) {
                                brightness = (brightness + delta).coerceIn(0.01f, 1.0f)
                                showBrightnessIndicator = true
                            } else if (dragType == 2) {
                                volumeFloat = (volumeFloat + delta).coerceIn(0.0f, 1.0f)
                                showVolumeIndicator = true
                            }
                        }
                    }
                )
            }
    ) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight
        val isShortScreen = screenHeight < 400.dp
        val isNarrowScreen = screenWidth < 500.dp

        val buttonSize = if (isNarrowScreen) 40.dp else 52.dp
        val playButtonSize = if (isNarrowScreen) 56.dp else 76.dp
        val iconSize = if (isNarrowScreen) 18.dp else 22.dp
        val playIconSize = if (isNarrowScreen) 28.dp else 36.dp
        val controlSpacing = if (isNarrowScreen) 10.dp else 20.dp
        val indicatorPadding = if (isNarrowScreen) 40.dp else 80.dp

        val sliderWidth = if (isShortScreen) 36.dp else 44.dp
        val sliderHeight = if (isShortScreen) 130.dp else 200.dp
        val sliderPadding = if (isShortScreen) 12.dp else 24.dp

        // High-Fidelity Custom Video Player
        VideoPlayer(
            videoUrl = currentStreamUrl ?: channel.streamUrl,
            isPlaying = isPlaying,
            isMuted = isMuted,
            resizeMode = effectiveResizeMode,
            lowLatencyEnabled = lowLatencyEnabled,
            maxVideoWidth = activeMaxWidth,
            maxVideoHeight = activeMaxHeight,
            onVideoSizeChanged = { w, h ->
                videoWidth = w
                videoHeight = h
            },
            onPlayerErrorOccurred = { error ->
                val code = error.errorCode
                val isBadHttpStatusOrNetwork = code == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
                        code == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                        code == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
                        code == PlaybackException.ERROR_CODE_IO_UNSPECIFIED ||
                        code == PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE
                
                if (isBadHttpStatusOrNetwork) {
                    viewModel.handlePlaybackError(channel.id, currentStreamUrl ?: channel.streamUrl)
                }
            },
            onDiagnosticsUpdated = { diagnostics = it },
            onPlayerReady = { activePlayer = it },
            modifier = Modifier.fillMaxSize(),
            streams = groupedChannel?.streams ?: emptyList(),
            onStreamSwapped = { nextStream ->
                viewModel.selectStreamSource(nextStream.url, nextStream.subName)
            }
        )

        // Semi-transparent gradient overlay for controls to ensure text readability over bright content
        AnimatedVisibility(
            visible = showControls && !isInPipMode,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.85f),
                                Color.Black.copy(alpha = 0.45f),
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
            )
        }

        // Vertical Brightness Slider Overlay on the left side
        AnimatedVisibility(
            visible = (showBrightnessIndicator || (showControls && !isInPipMode)) && !isInPipMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = sliderPadding)
        ) {
            VerticalBrightnessSlider(
                brightness = brightness,
                onBrightnessChange = {
                    brightness = it
                    showBrightnessIndicator = true
                },
                modifier = Modifier
                    .width(sliderWidth)
                    .height(sliderHeight)
            )
        }

        // Vertical Volume Slider Overlay on the right side
        AnimatedVisibility(
            visible = (showVolumeIndicator || (showControls && !isInPipMode)) && !isInPipMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = sliderPadding)
        ) {
            VerticalVolumeSlider(
                volume = volumeFloat,
                onVolumeChange = {
                    volumeFloat = it
                    showVolumeIndicator = true
                },
                modifier = Modifier
                    .width(sliderWidth)
                    .height(sliderHeight)
            )
        }


        // Double-Tap Seek Indicators
        // Left (Rewind) Indicator
        AnimatedVisibility(
            visible = showRewindIndicator,
            enter = fadeIn(animationSpec = tween(150)),
            exit = fadeOut(animationSpec = tween(150)),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = indicatorPadding)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "◀◀",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "-10s",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Right (Forward) Indicator
        AnimatedVisibility(
            visible = showForwardIndicator,
            enter = fadeIn(animationSpec = tween(150)),
            exit = fadeOut(animationSpec = tween(150)),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = indicatorPadding)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "▶▶",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "+10s",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // --- HUD - CONTROLS OVERLAY ---

        // 1. TOP HEADER OVERLAY
        AnimatedVisibility(
            visible = showControls && !isInPipMode,
            enter = slideInVertically(initialOffsetY = { -it }),
            exit = slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)
                        )
                    )
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(vertical = if (isNarrowScreen) 8.dp else 12.dp),
                contentAlignment = Alignment.Center
            ) {
                val widthModifier = if (spec.maxContentWidth != androidx.compose.ui.unit.Dp.Unspecified) {
                    Modifier.widthIn(max = spec.maxContentWidth)
                } else {
                    Modifier.fillMaxWidth()
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = widthModifier
                        .fillMaxWidth()
                        .padding(horizontal = spec.margin)
                ) {
                    IconButton(
                        onClick = handleBack,
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(50))
                            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(50))
                            .size(if (isNarrowScreen) 40.dp else 48.dp)
                            .testTag("player_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(if (isNarrowScreen) 18.dp else 24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(if (isNarrowScreen) 8.dp else 16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = channel.name,
                            fontSize = if (isNarrowScreen) 16.sp else 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (currentEpgProgram != null) {
                            val timeSdf = remember { java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()) }
                            val startTimeStr = remember(currentEpgProgram) { timeSdf.format(java.util.Date(currentEpgProgram!!.startTime)) }
                            val endTimeStr = remember(currentEpgProgram) { timeSdf.format(java.util.Date(currentEpgProgram!!.endTime)) }
                            Text(
                                text = "${currentEpgProgram!!.title} ($startTimeStr - $endTimeStr)",
                                fontSize = if (isNarrowScreen) 11.sp else 13.sp,
                                fontWeight = FontWeight.Normal,
                                color = Color(0xFFD0BCFF),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            // Pulsating Live Badge
                            PulsatingLiveIndicator()
                            Spacer(modifier = Modifier.width(if (isNarrowScreen) 4.dp else 8.dp))
                            Text(
                                text = categoryName.uppercase(),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFD0BCFF),
                                letterSpacing = 0.5.sp
                            )

                            if (!currentStreamName.isNullOrEmpty()) {
                                Spacer(modifier = Modifier.width(if (isNarrowScreen) 4.dp else 8.dp))
                                Text(
                                    text = "•",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.width(if (isNarrowScreen) 4.dp else 8.dp))
                                Text(
                                    text = currentStreamName!!.uppercase(),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF81C784),
                                    letterSpacing = 0.5.sp
                                )
                            }

                            // Show recording indicator if recording
                            val isThisChannelRecording = isRecording && recordingChannelId == channel.id
                            if (isThisChannelRecording) {
                                Spacer(modifier = Modifier.width(if (isNarrowScreen) 4.dp else 8.dp))
                                Text(
                                    text = "•",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.width(if (isNarrowScreen) 4.dp else 8.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .background(Color(0xFFFF5252).copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                        .border(0.5.dp, Color(0xFFFF5252).copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .background(Color(0xFFFF5252), CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "REC $formattedDuration",
                                        color = Color(0xFFFF5252),
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }
                        }

                    }

                    Spacer(modifier = Modifier.width(if (isNarrowScreen) 8.dp else 12.dp))

                    if (!isNarrowScreen) {
                        // PiP Button
                        IconButton(
                            onClick = onEnterPip,
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(50))
                                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(50))
                                .testTag("player_pip_button")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .border(1.5.dp, Color.White, RoundedCornerShape(2.dp))
                                    .padding(2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Color.White, RoundedCornerShape(1.dp))
                                        .align(Alignment.BottomEnd)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Rotation Lock Toggle Button
                        IconButton(
                            onClick = { isRotationLocked = !isRotationLocked },
                            modifier = Modifier
                                .background(
                                    if (isRotationLocked) Color(0xFFD0BCFF).copy(alpha = 0.25f)
                                    else Color.White.copy(alpha = 0.12f),
                                    RoundedCornerShape(50)
                                )
                                .border(
                                    1.dp,
                                    if (isRotationLocked) Color(0xFFD0BCFF) else Color.White.copy(alpha = 0.18f),
                                    RoundedCornerShape(50)
                                )
                                .testTag("player_rotation_lock_button")
                        ) {
                            Icon(
                                imageVector = if (isRotationLocked) Icons.Default.Lock else Icons.Default.Refresh,
                                contentDescription = "Rotation Lock",
                                tint = if (isRotationLocked) Color(0xFFD0BCFF) else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))
                    }

                    // Stream Selector Dropdown Menu
                    if (groupedChannel != null && groupedChannel.streams.size > 1) {
                        Box {
                            IconButton(
                                onClick = { showStreamsDropdown = true },
                                modifier = Modifier
                                    .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(50))
                                    .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(50))
                                    .size(if (isNarrowScreen) 40.dp else 48.dp)
                                    .testTag("player_streams_dropdown_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.List,
                                    contentDescription = "Switch Server/Source",
                                    tint = Color.White,
                                    modifier = Modifier.size(if (isNarrowScreen) 18.dp else 20.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showStreamsDropdown,
                                onDismissRequest = { showStreamsDropdown = false },
                                modifier = Modifier
                                    .background(Color(0xFF1D1B20))
                                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                            ) {
                                Text(
                                    text = "AVAILABLE SOURCES",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFD0BCFF),
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                                groupedChannel.streams.forEach { stream ->
                                    val isCurrent = (currentStreamUrl ?: selectedChannel?.streamUrl) == stream.url
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                if (isCurrent) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = "Current",
                                                        tint = Color(0xFFD0BCFF),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                                Text(
                                                    text = stream.subName,
                                                    color = if (isCurrent) Color(0xFFD0BCFF) else Color.White,
                                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                                )
                                            }
                                        },
                                        onClick = {
                                            showStreamsDropdown = false
                                            viewModel.selectStreamSource(stream.url, stream.subName)
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(if (isNarrowScreen) 8.dp else 12.dp))
                    }

                    // Stream Settings Button (Unified Quality, Aspect, Favorite, Recording, Rotate)
                    IconButton(
                        onClick = { showSettingsSheet = true },
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(50))
                            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(50))
                            .size(if (isNarrowScreen) 40.dp else 48.dp)
                            .testTag("player_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Stream Settings",
                            tint = Color.White,
                            modifier = Modifier.size(if (isNarrowScreen) 18.dp else 20.dp)
                        )
                    }
                }
            }
        }

        // 2. CENTRAL PLAY/PAUSE INTERACTION
        AnimatedVisibility(
            visible = showControls && !isInPipMode,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(controlSpacing),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Mute/Unmute toggle (Volume Icon)
                Box(
                    modifier = Modifier
                        .size(buttonSize)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                        .clickable { isMuted = !isMuted }
                        .testTag("player_mute_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        contentDescription = if (isMuted) "Unmute" else "Mute",
                        tint = Color.White,
                        modifier = Modifier.size(iconSize)
                    )
                }

                // 2. Seek Backward 10s button
                Box(
                    modifier = Modifier
                        .size(buttonSize)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                        .clickable {
                            activePlayer?.let { player ->
                                val target = (player.currentPosition - 10000).coerceAtLeast(0)
                                player.seekTo(target)
                            }
                            showRewindIndicator = true
                        }
                        .testTag("player_seek_backward_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Seek Backward 10s",
                            tint = Color.White,
                            modifier = Modifier
                                .size(if (isNarrowScreen) 14.dp else 18.dp)
                                .graphicsLayer(scaleX = -1f) // Flip horizontally for reverse direction
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "10s",
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // 3. Main Play/Pause Button
                Box(
                    modifier = Modifier
                        .size(playButtonSize)
                        .background(Color.White, CircleShape) // Ultra-premium play circle
                        .clickable { isPlaying = !isPlaying }
                        .testTag("player_play_pause_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.Black,
                        modifier = Modifier.size(playIconSize)
                    )
                }

                // 4. Seek Forward 10s button
                Box(
                    modifier = Modifier
                        .size(buttonSize)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                        .clickable {
                            activePlayer?.let { player ->
                                val target = player.currentPosition + 10000
                                val duration = if (player.duration > 0) player.duration else Long.MAX_VALUE
                                player.seekTo(target.coerceIn(0L, duration))
                            }
                            showForwardIndicator = true
                        }
                        .testTag("player_seek_forward_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Seek Forward 10s",
                            tint = Color.White,
                            modifier = Modifier.size(if (isNarrowScreen) 14.dp else 18.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "10s",
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // 5. Info / Description toggle button
                Box(
                    modifier = Modifier
                        .size(buttonSize)
                        .background(
                            if (showDescriptionCard) Color.White.copy(alpha = 0.2f)
                            else Color.Black.copy(alpha = 0.5f),
                            CircleShape
                        )
                        .border(
                            1.dp,
                            if (showDescriptionCard) Color.White else Color.White.copy(alpha = 0.15f),
                            CircleShape
                        )
                        .clickable { showDescriptionCard = !showDescriptionCard }
                        .testTag("player_info_toggle_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Toggle Description",
                        tint = Color.White,
                        modifier = Modifier.size(iconSize)
                    )
                }
            }
        }

        // 3. BOTTOM DETAILS & QUICK CHANNEL SWITCHER HUD PANEL
        AnimatedVisibility(
            visible = showControls && !isInPipMode,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.95f))
                        )
                    )
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val widthModifier = if (spec.maxContentWidth != androidx.compose.ui.unit.Dp.Unspecified) {
                    Modifier.widthIn(max = spec.maxContentWidth)
                } else {
                    Modifier.fillMaxWidth()
                }
                // QUICK SWITCH DRAWER (Horizontal channel line-up)
                Column(
                    modifier = widthModifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spec.margin)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "TV",
                            tint = Color(0xFFD0BCFF),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "RECOMMENDED: SAME GENRE FEEDS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFD0BCFF),
                            letterSpacing = 1.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (isLoading) {
                        val infiniteTransition = rememberInfiniteTransition(label = "horizontal_skeleton_shimmer")
                        val alpha by infiniteTransition.animateFloat(
                            initialValue = 0.4f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 800, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "shimmer_alpha"
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(spec.gutter),
                            contentPadding = PaddingValues(horizontal = spec.margin, vertical = 4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(5, contentType = { "recommended_skeleton" }) {
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color(0xFF2B2930).copy(alpha = 0.75f)
                                    ),
                                    modifier = Modifier.width(140.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .padding(8.dp)
                                            .fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color.White.copy(alpha = 0.05f * alpha))
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Box(
                                                modifier = Modifier
                                                    .width(70.dp)
                                                    .height(11.dp)
                                                    .background(Color.White.copy(alpha = 0.1f * alpha), RoundedCornerShape(2.dp))
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .width(40.dp)
                                                    .height(8.dp)
                                                    .background(Color.White.copy(alpha = 0.05f * alpha), RoundedCornerShape(2.dp))
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        LazyRow(
                            state = lazyListState,
                            horizontalArrangement = Arrangement.spacedBy(spec.gutter),
                            contentPadding = PaddingValues(horizontal = spec.margin, vertical = 4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(
                                items = sameTypeChannels,
                                key = { it.id },
                                contentType = { "recommended_item" }
                            ) { otherChannel ->
                                val isCurrent = otherChannel.id == channel.id
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(
                                        width = if (isCurrent) 2.dp else 1.dp,
                                        color = if (isCurrent) Color(0xFFD0BCFF) else Color.White.copy(alpha = 0.15f)
                                    ),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isCurrent) Color(0xFF381E72).copy(alpha = 0.85f) else Color(0xFF2B2930).copy(alpha = 0.75f)
                                    ),
                                    modifier = Modifier
                                        .width(140.dp)
                                        .clickable {
                                            viewModel.selectChannel(otherChannel)
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .padding(8.dp)
                                            .fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val context = LocalContext.current
                                        val imageRequest = remember(otherChannel.logoUrl, isScrolling) {
                                            ImageRequest.Builder(context)
                                                .data(otherChannel.logoUrl)
                                                .apply {
                                                    if (isScrolling) {
                                                        networkCachePolicy(CachePolicy.DISABLED)
                                                        diskCachePolicy(CachePolicy.DISABLED)
                                                    } else {
                                                        crossfade(true)
                                                    }
                                                }
                                                .build()
                                        }
                                        // Small Logo
                                        AsyncImage(
                                            model = imageRequest,
                                            contentDescription = otherChannel.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color.White.copy(alpha = 0.1f))
                                        )

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Column {
                                            Text(
                                                text = otherChannel.name,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = if (isCurrent) "PLAYING" else "TAP TO WATCH",
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isCurrent) Color(0xFFD0BCFF) else Color.Gray
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. UNIFIED STREAM SETTINGS OVERLAY SHEET
        if (showSettingsSheet && !isInPipMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable { showSettingsSheet = false }
            ) {
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {} // Prevent clicks on card from closing
                        )
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(if (isShortScreen) 8.dp else 16.dp)
                        .widthIn(max = 600.dp)
                        .heightIn(max = screenHeight - 32.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1D1B20)),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                ) {
                    Column(
                        modifier = Modifier.padding(if (isShortScreen) 12.dp else 20.dp)
                    ) {
                        // Header (Fixed)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Stream Settings",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            IconButton(
                                onClick = { showSettingsSheet = false },
                                modifier = Modifier.background(Color.White.copy(alpha = 0.08f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Scrollable content column
                        Column(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState())
                        ) {
                            // Video Quality Selection
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "VIDEO QUALITY",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFFD0BCFF),
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "HIGH PRIORITY",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF81C784),
                                    modifier = Modifier
                                        .background(Color(0xFF81C784).copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val qualities = listOf("Auto", "1080p", "720p", "480p", "360p")
                            qualities.forEach { quality ->
                                val isSelected = selectedQuality == quality
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(
                                            if (isSelected) Color(0xFFD0BCFF) else Color.White.copy(alpha = 0.08f),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (isSelected) Color(0xFFD0BCFF) else Color.White.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { selectedQuality = quality }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = quality,
                                            color = if (isSelected) Color(0xFF381E72) else Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                        if (quality == "Auto" && isSelected && networkQualityLabel.isNotEmpty()) {
                                            Text(
                                                text = "Active",
                                                color = Color(0xFF381E72).copy(alpha = 0.7f),
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Aspect Ratio / Scaling Selection
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "ASPECT RATIO",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFD0BCFF),
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "HIGH PRIORITY",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF81C784),
                                modifier = Modifier
                                    .background(Color(0xFF81C784).copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val modes = listOf("Auto", "Fit", "Zoom", "Stretch")
                            modes.forEach { mode ->
                                val isSelected = aspectScalingMode == mode
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(
                                            if (isSelected) Color(0xFFD0BCFF) else Color.White.copy(alpha = 0.08f),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (isSelected) Color(0xFFD0BCFF) else Color.White.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { aspectScalingMode = mode }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = mode.uppercase(),
                                        color = if (isSelected) Color(0xFF381E72) else Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Action buttons (Record, Favorite & PiP)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "QUICK ACTIONS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFD0BCFF),
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "MEDIUM PRIORITY",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFD54F),
                                modifier = Modifier
                                    .background(Color(0xFFFFD54F).copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val actionTextSize = if (isNarrowScreen) 11.sp else 13.sp

                            // Favorite toggle
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .background(
                                        if (channel.isFavorite) Color(0xFFD0BCFF).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (channel.isFavorite) Color(0xFFD0BCFF) else Color.White.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable { viewModel.toggleFavorite(channel.id, !channel.isFavorite) }
                            ) {
                                Icon(
                                    imageVector = if (channel.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Favorite",
                                    tint = if (channel.isFavorite) Color(0xFFD0BCFF) else Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (channel.isFavorite) "Favorited" else "Favorite",
                                    color = if (channel.isFavorite) Color(0xFFD0BCFF) else Color.White,
                                    fontSize = actionTextSize,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Record toggle
                            val isThisChannelRecording = isRecording && recordingChannelId == channel.id
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .background(
                                        if (isThisChannelRecording) Color(0xFFFF5252).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isThisChannelRecording) Color(0xFFFF5252) else Color.White.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        if (isThisChannelRecording) {
                                            viewModel.stopRecording()
                                        } else {
                                            viewModel.startRecording(channel)
                                        }
                                    }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(if (isThisChannelRecording) 6.dp else 8.dp)
                                        .background(
                                            if (isThisChannelRecording) Color(0xFFFF5252) else Color.White,
                                            if (isThisChannelRecording) RoundedCornerShape(2.dp) else CircleShape
                                        )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isThisChannelRecording) "Stop REC" else "Record",
                                    color = if (isThisChannelRecording) Color(0xFFFF5252) else Color.White,
                                    fontSize = actionTextSize,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // PiP Mode action button
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                    .clickable {
                                        showSettingsSheet = false
                                        onEnterPip()
                                    }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .border(1.5.dp, Color.White, RoundedCornerShape(2.dp))
                                        .padding(2.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(Color.White, RoundedCornerShape(1.dp))
                                            .align(Alignment.BottomEnd)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "PiP Mode",
                                    color = Color.White,
                                    fontSize = actionTextSize,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(if (isShortScreen) 12.dp else 20.dp))

                        // Low Latency Config Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "LATENCY OPTIMIZATION",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFD0BCFF),
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "MEDIUM PRIORITY",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFD54F),
                                modifier = Modifier
                                    .background(Color(0xFFFFD54F).copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                .clickable { viewModel.setLowLatencyMode(!lowLatencyEnabled) }
                                .padding(horizontal = 16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "⚡",
                                    fontSize = 16.sp,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Column {
                                    Text(
                                        text = "Low-Latency Live Player",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Reduces live lag and minimizes playback delay",
                                        color = Color.LightGray.copy(alpha = 0.8f),
                                        fontSize = 10.sp
                                    )
                                }
                            }
                            Switch(
                                checked = lowLatencyEnabled,
                                onCheckedChange = { viewModel.setLowLatencyMode(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF381E72),
                                    checkedTrackColor = Color(0xFFD0BCFF),
                                    uncheckedThumbColor = Color.Gray,
                                    uncheckedTrackColor = Color.White.copy(alpha = 0.12f)
                                ),
                                modifier = Modifier.testTag("settings_low_latency_switch")
                            )
                        }

                        // Background Sync Config Row
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "RESOURCE CONSTRAINTS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFD0BCFF),
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "BATTERY SAVER",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFD54F),
                                modifier = Modifier
                                    .background(Color(0xFFFFD54F).copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                .clickable { 
                                    val newValue = !unmeteredSyncOnly
                                    unmeteredSyncOnly = newValue
                                    viewModel.setUnmeteredSyncOnly(newValue)
                                }
                                .padding(horizontal = 16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "📶",
                                    fontSize = 16.sp,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Column {
                                    Text(
                                        text = "Sync over Wi-Fi Only",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Only sync and verify channels when on Wi-Fi",
                                        color = Color.LightGray.copy(alpha = 0.8f),
                                        fontSize = 10.sp
                                    )
                                }
                            }
                            Switch(
                                checked = unmeteredSyncOnly,
                                onCheckedChange = { newValue ->
                                    unmeteredSyncOnly = newValue
                                    viewModel.setUnmeteredSyncOnly(newValue)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF381E72),
                                    checkedTrackColor = Color(0xFFD0BCFF),
                                    uncheckedThumbColor = Color.Gray,
                                    uncheckedTrackColor = Color.White.copy(alpha = 0.12f)
                                ),
                                modifier = Modifier.testTag("settings_unmetered_sync_switch")
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Rotation action row allowing toggling
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .background(
                                    if (isRotationLocked) Color(0xFFD0BCFF).copy(alpha = 0.12f)
                                    else Color.White.copy(alpha = 0.08f),
                                    RoundedCornerShape(12.dp)
                                )
                                .border(
                                    1.dp,
                                    if (isRotationLocked) Color(0xFFD0BCFF).copy(alpha = 0.3f)
                                    else Color.White.copy(alpha = 0.15f),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { isRotationLocked = !isRotationLocked }
                                .padding(horizontal = 16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isRotationLocked) Icons.Default.Lock else Icons.Default.Refresh,
                                    contentDescription = "Rotation Lock",
                                    tint = if (isRotationLocked) Color(0xFFD0BCFF) else Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Rotation Lock",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (isRotationLocked) "Forced Landscape Mode Active" else "Auto-rotate with screen tilt",
                                        color = Color.LightGray.copy(alpha = 0.8f),
                                        fontSize = 10.sp
                                    )
                                }
                            }
                            Switch(
                                checked = isRotationLocked,
                                onCheckedChange = { isRotationLocked = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF381E72),
                                    checkedTrackColor = Color(0xFFD0BCFF),
                                    uncheckedThumbColor = Color.Gray,
                                    uncheckedTrackColor = Color.White.copy(alpha = 0.12f)
                                ),
                                modifier = Modifier.testTag("settings_rotation_lock_switch")
                            )
                        }
                    }
                }
            }
        }
    }

        // 5. SIDE-PANEL DETAILED CHANNEL OVERLAY (Sliding from the right)
        AnimatedVisibility(
            visible = showDescriptionCard && !isInPipMode,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .widthIn(max = 360.dp)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1D1B20).copy(alpha = 0.95f)
                ),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Info",
                                tint = Color(0xFFD0BCFF),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "About this channel",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        IconButton(
                            onClick = { showDescriptionCard = false },
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.08f), CircleShape)
                                .size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close description",
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Channel Logo and Name
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        AsyncImage(
                            model = channel.logoUrl,
                            contentDescription = channel.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.1f))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = channel.name,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = categoryName.uppercase(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFD0BCFF),
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Scrollable Description text
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = "CHANNEL DETAILS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = channel.description.ifBlank { "No description is set for this channel. Manage it dynamically from the admin panel." },
                            fontSize = 12.sp,
                            color = Color.LightGray,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "TECHNICAL DIAGNOSTICS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFD0BCFF),
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        androidx.compose.material3.Card(
                            colors = androidx.compose.material3.CardDefaults.cardColors(
                                containerColor = Color.White.copy(alpha = 0.04f)
                            ),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Health Row
                                val health = channel.channelHealth.ifBlank { "Unknown" }
                                val healthColor = when (health.lowercase()) {
                                    "excellent", "good" -> Color(0xFF81C784)
                                    "fair" -> Color(0xFFFFD54F)
                                    "poor" -> Color(0xFFFFB74D)
                                    else -> Color(0xFFFF8A80)
                                }
                                DiagnosticItem(
                                    label = "Connection Health",
                                    value = health.uppercase(),
                                    valueColor = healthColor
                                )
                                
                                // Speed Row
                                val bps = diagnostics?.bitrateEstimate ?: 0L
                                val speedText = if (bps > 0) {
                                    val kbps = bps / 1000
                                    val mbps = kbps / 1000f
                                    if (mbps >= 1.0f) String.format("%.1f Mbps", mbps) else "$kbps Kbps"
                                } else "Measuring..."
                                DiagnosticItem(
                                    label = "Download Speed",
                                    value = speedText,
                                    valueColor = Color.White
                                )
                                
                                // Buffer Capacity Row
                                val bufferDurationSec = (diagnostics?.bufferDurationMs ?: 0L) / 1000f
                                val bufferColor = when {
                                    bufferDurationSec >= 8f -> Color(0xFF81C784) // green
                                    bufferDurationSec >= 3f -> Color(0xFFFFD54F) // yellow
                                    else -> Color(0xFFFF8A80) // red
                                }
                                DiagnosticItem(
                                    label = "Buffer Capacity",
                                    value = String.format("%.2fs", bufferDurationSec),
                                    valueColor = bufferColor
                                )

                                // Dynamic Playback Speed Row
                                val playbackSpeed = diagnostics?.playbackSpeed ?: 1.0f
                                val speedValue = when {
                                    playbackSpeed < 0.85f -> String.format("%.2fx (Buffer Shield Max)", playbackSpeed)
                                    playbackSpeed < 0.98f -> String.format("%.2fx (Adaptive Protection)", playbackSpeed)
                                    playbackSpeed > 1.02f -> String.format("%.2fx (Live Catch-up)", playbackSpeed)
                                    else -> "1.00x (Stable Playback)"
                                }
                                val speedColor = when {
                                    playbackSpeed < 0.98f -> Color(0xFF64B5F6) // blue
                                    playbackSpeed > 1.02f -> Color(0xFFBA68C8) // purple
                                    else -> Color.White
                                }
                                DiagnosticItem(
                                    label = "Anti-Buffer Protection",
                                    value = speedValue,
                                    valueColor = speedColor
                                )

                                // TTFF Row
                                val ttff = diagnostics?.timeToFirstFrameMs ?: 0L
                                DiagnosticItem(
                                    label = "Time To First Frame",
                                    value = if (ttff > 0) "${ttff}ms" else "Measuring...",
                                    valueColor = Color.White
                                )
                                
                                // Rebuffers Row
                                val rebuffers = diagnostics?.rebufferCount ?: 0
                                DiagnosticItem(
                                    label = "Session Rebuffers",
                                    value = "$rebuffers",
                                    valueColor = if (rebuffers > 0) Color(0xFFFFB74D) else Color.White
                                )
                                
                                // Error Rate Row
                                val errorRate = viewModel.getChannelErrorRate(channel.id)
                                DiagnosticItem(
                                    label = "Channel Error Rate",
                                    value = "${"%.1f".format(errorRate * 100)}%",
                                    valueColor = if (errorRate > 0.1f) Color(0xFFFF8A80) else Color.White
                                )
                            }
                        }

                        if (epgPrograms.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = "UPCOMING PROGRAMS",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFD0BCFF),
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            androidx.compose.material3.Card(
                                colors = androidx.compose.material3.CardDefaults.cardColors(
                                    containerColor = Color.White.copy(alpha = 0.04f)
                                ),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    val timeSdf = remember { java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()) }
                                    epgPrograms.take(5).forEachIndexed { index, prog ->
                                        val startStr = timeSdf.format(java.util.Date(prog.startTime))
                                        val endStr = timeSdf.format(java.util.Date(prog.endTime))
                                        Column {
                                            Text(
                                                text = prog.title,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                            Text(
                                                text = "$startStr - $endStr",
                                                fontSize = 11.sp,
                                                color = Color.LightGray
                                            )
                                            if (prog.description.isNotBlank()) {
                                                Text(
                                                    text = prog.description,
                                                    fontSize = 11.sp,
                                                    color = Color.Gray,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.padding(top = 2.dp)
                                                )
                                            }
                                        }
                                        if (index < epgPrograms.take(5).size - 1) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(0.5.dp)
                                                    .background(Color.White.copy(alpha = 0.08f))
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PulsatingLiveIndicator(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(Color.Red.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
            .border(0.5.dp, Color.Red.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    alpha = alpha
                )
                .background(Color(0xFFE53935), RoundedCornerShape(50))
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = "LIVE",
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.5.sp
        )
    }
}

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

@Composable
fun VerticalBrightnessSlider(
    brightness: Float,
    onBrightnessChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(44.dp)
            .height(200.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color.Black.copy(alpha = 0.65f))
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(22.dp))
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, dragAmount ->
                    change.consume()
                    val sensitivity = 200f
                    val delta = -dragAmount / sensitivity
                    onBrightnessChange((brightness + delta).coerceIn(0.01f, 1.0f))
                }
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(brightness)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFE0B0FF),
                            Color(0xFFA855F7)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            CustomBrightnessIcon(color = Color.White)

            Text(
                text = "${(brightness * 100).toInt()}%",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun CustomBrightnessIcon(modifier: Modifier = Modifier, color: Color = Color.White) {
    Box(
        modifier = modifier
            .size(18.dp)
            .border(1.5.dp, color, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
    }
}

@Composable
fun VerticalVolumeSlider(
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(44.dp)
            .height(200.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color.Black.copy(alpha = 0.65f))
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(22.dp))
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, dragAmount ->
                    change.consume()
                    val sensitivity = 200f
                    val delta = -dragAmount / sensitivity
                    onVolumeChange((volume + delta).coerceIn(0.0f, 1.0f))
                }
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(volume.coerceIn(0f, 1f))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFD0BCFF),
                            Color(0xFF81C784)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (volume <= 0f) "🔇" else if (volume < 0.5f) "🔉" else "🔊",
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )

            Text(
                text = "${(volume * 100).toInt()}%",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun DiagnosticItem(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color.Gray
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
    }
}
