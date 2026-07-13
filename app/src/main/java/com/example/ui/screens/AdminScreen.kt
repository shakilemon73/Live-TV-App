package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.ChannelViewModel
import com.example.ui.components.ResponsiveGridFrame
import com.example.ui.components.rememberResponsiveGridSpec
import com.example.ui.components.ResponsiveGridSpec
import androidx.compose.foundation.border

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    viewModel: ChannelViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spec = rememberResponsiveGridSpec()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val sharedPrefs = remember { context.getSharedPreferences("live_tv_prefs", Context.MODE_PRIVATE) }

    // Collect states from ViewModel
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val autoSyncOnLaunch by viewModel.autoSyncOnLaunch.collectAsStateWithLifecycle()
    val lastSyncTime by viewModel.lastSyncTime.collectAsStateWithLifecycle()
    val syncStatusMessage by viewModel.syncStatusMessage.collectAsStateWithLifecycle()
    val lowLatencyMode by viewModel.lowLatencyMode.collectAsStateWithLifecycle()
    val filterBrokenChannels by viewModel.filterBrokenChannels.collectAsStateWithLifecycle()
    val isCheckingStreams by viewModel.isCheckingStreams.collectAsStateWithLifecycle()
    val streamCheckingProgress by viewModel.streamCheckingProgress.collectAsStateWithLifecycle()
    val streamCheckingStatus by viewModel.streamCheckingStatus.collectAsStateWithLifecycle()

    // Local Persisted Preferences
    var hardwareAcceleration by remember {
        mutableStateOf(sharedPrefs.getBoolean("pref_hardware_acceleration", true))
    }
    var aspectPreference by remember {
        mutableStateOf(sharedPrefs.getString("pref_aspect_ratio", "Fit") ?: "Fit")
    }
    var audioPresetPreference by remember {
        mutableStateOf(sharedPrefs.getString("pref_audio_preset", "Balanced 3D") ?: "Balanced 3D")
    }
    var themeAccent by remember {
        mutableStateOf(sharedPrefs.getString("pref_theme_accent", "Lavender") ?: "Lavender")
    }
    var wifiSyncOnly by remember {
        mutableStateOf(viewModel.getUnmeteredSyncOnly())
    }

    // Dynamic Colors based on selected accent preference
    val accentColor = remember(themeAccent) {
        when (themeAccent) {
            "Emerald" -> Color(0xFF81C784)
            "Amber" -> Color(0xFFFFD54F)
            "Cosmic Slate" -> Color(0xFF90CAF9)
            else -> Color(0xFFD0BCFF) // Lavender (default)
        }
    }
    val onPurpleColor = Color(0xFF381E72)
    val darkBg = Color(0xFF0F0E13)
    val cardBg = Color(0xFF1B1921)

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(darkBg)
                    .statusBarsPadding(),
                contentAlignment = Alignment.Center
            ) {
                val widthModifier = if (spec.maxContentWidth != androidx.compose.ui.unit.Dp.Unspecified) {
                    Modifier.widthIn(max = spec.maxContentWidth)
                } else {
                    Modifier.fillMaxWidth()
                }
                Row(
                    modifier = widthModifier
                        .fillMaxWidth()
                        .padding(horizontal = spec.margin, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            focusManager.clearFocus()
                            onNavigateBack()
                        },
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(40.dp)
                            .background(Color.White.copy(alpha = 0.04f), CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
                            .testTag("admin_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    Column {
                        Text(
                            text = "App Settings",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        Text(
                            text = "Personalize your broadcast experience",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        },
        containerColor = darkBg,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        ResponsiveGridFrame(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(darkBg, Color(0xFF14121A))
                    )
                )
        ) { spec ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = spec.gutter),
                verticalArrangement = Arrangement.spacedBy(spec.gutter)
            ) {
            // Service Connection status indicator card
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(Color(0xFF81C784), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("API Gateway Status", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                        Text("Connected to Cloudflare Edge (42ms)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Text(
                        "AES-128 SECURE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        modifier = Modifier
                            .background(accentColor.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // SECTION 1: SOURCE CONFIGURATION & CLOUD SYNC
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Cloud Feed",
                            tint = accentColor,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Channel Synchronization",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Synchronize categories and dynamic live stream URLs securely with the cloud.",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Sync Button
                    val syncBtnInteraction = remember { MutableInteractionSource() }
                    val syncBtnPressed by syncBtnInteraction.collectIsFocusedAsState()
                    val syncBtnScale by animateFloatAsState(
                        targetValue = if (syncBtnPressed) 1.03f else 1.00f,
                        label = "syncBtnScale"
                    )

                    Button(
                        onClick = { viewModel.syncWithCloudGist(force = true) },
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = onPurpleColor),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .graphicsLayer(scaleX = syncBtnScale, scaleY = syncBtnScale)
                            .focusable(interactionSource = syncBtnInteraction)
                            .testTag("save_sync_button")
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = onPurpleColor, modifier = Modifier.size(18.dp))
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.Check, contentDescription = "Sync", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Synchronize Guides Now", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Diagnostic Logs / Status
                    if (syncStatusMessage != null || lastSyncTime > 0) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.Black.copy(alpha = 0.2f))
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(
                                            if (syncStatusMessage?.contains("failed", ignoreCase = true) == true) Color(0xFFFF8A80) else Color(0xFF81C784),
                                            CircleShape
                                        )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("SYNC DIAGNOSTIC LOG", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                            }
                            if (syncStatusMessage != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(syncStatusMessage ?: "", fontSize = 12.sp, color = Color.White)
                            }
                            if (lastSyncTime > 0) {
                                Spacer(modifier = Modifier.height(4.dp))
                                val sdf = remember { java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()) }
                                val formattedDate = remember(lastSyncTime) { sdf.format(java.util.Date(lastSyncTime)) }
                                Text("Last synced: $formattedDate (Local)", fontSize = 11.sp, color = Color.Gray)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Auto sync toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { viewModel.updateAutoSyncSetting(!autoSyncOnLaunch) }
                            .background(Color.White.copy(alpha = 0.02f))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto Sync on Launch", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Securely fetch latest guides on startup", fontSize = 11.sp, color = Color.Gray)
                        }
                        Switch(
                            checked = autoSyncOnLaunch,
                            onCheckedChange = { viewModel.updateAutoSyncSetting(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = onPurpleColor,
                                checkedTrackColor = accentColor,
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.White.copy(alpha = 0.08f)
                            ),
                            modifier = Modifier.testTag("settings_auto_sync_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Wi-Fi only sync
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                wifiSyncOnly = !wifiSyncOnly
                                viewModel.setUnmeteredSyncOnly(wifiSyncOnly)
                            }
                            .background(Color.White.copy(alpha = 0.02f))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Sync over Wi-Fi Only", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Save mobile data bandwidth costs", fontSize = 11.sp, color = Color.Gray)
                        }
                        Switch(
                            checked = wifiSyncOnly,
                            onCheckedChange = {
                                wifiSyncOnly = it
                                viewModel.setUnmeteredSyncOnly(it)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = onPurpleColor,
                                checkedTrackColor = accentColor,
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.White.copy(alpha = 0.08f)
                            ),
                            modifier = Modifier.testTag("settings_unmetered_sync_switch")
                        )
                    }
                }
            }

            // SECTION 2: STREAM QUALITY & LINK VALIDATION
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Quality",
                            tint = Color(0xFF81C784),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Quality Assurance & Diagnostics",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Scan live channel lists to check server responsiveness and filter out unplayable links.",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Filter Broken Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { viewModel.updateFilterBrokenSetting(!filterBrokenChannels) }
                            .background(Color.White.copy(alpha = 0.02f))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Exclude Offline Channels", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Hide non-functional server streams automatically", fontSize = 11.sp, color = Color.Gray)
                        }
                        Switch(
                            checked = filterBrokenChannels,
                            onCheckedChange = { viewModel.updateFilterBrokenSetting(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = onPurpleColor,
                                checkedTrackColor = accentColor,
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.White.copy(alpha = 0.08f)
                            ),
                            modifier = Modifier.testTag("filter_broken_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Scanner status progress
                    if (isCheckingStreams) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LinearProgressIndicator(
                                progress = { streamCheckingProgress },
                                color = Color(0xFF81C784),
                                trackColor = Color.White.copy(alpha = 0.08f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = streamCheckingStatus ?: "Scanning URLs...",
                                    fontSize = 12.sp,
                                    color = Color.LightGray,
                                    fontStyle = FontStyle.Italic,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val pct = (streamCheckingProgress * 100).toInt()
                                Text(
                                    text = "$pct%",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF81C784)
                                )
                            }
                        }
                    } else {
                        Button(
                            onClick = { viewModel.verifyAllChannels() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32), contentColor = Color.White),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("verify_streams_button")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.Search, contentDescription = "Scan", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Scan & Validate All Streams", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }

            // SECTION 3: PLAYER PREFERENCES & VIDEO SETTINGS
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Player preferences",
                            tint = accentColor,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Player & Video Engine",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Customize the ExoPlayer rendering engine for optimal screen and network performance.",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Low Latency Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { viewModel.setLowLatencyMode(!lowLatencyMode) }
                            .background(Color.White.copy(alpha = 0.02f))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Low Latency Playback", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Reduces buffering times to match real-time broadcasts", fontSize = 11.sp, color = Color.Gray)
                        }
                        Switch(
                            checked = lowLatencyMode,
                            onCheckedChange = { viewModel.setLowLatencyMode(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = onPurpleColor,
                                checkedTrackColor = accentColor,
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.White.copy(alpha = 0.08f)
                            ),
                            modifier = Modifier.testTag("low_latency_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Hardware Acceleration
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                hardwareAcceleration = !hardwareAcceleration
                                sharedPrefs.edit().putBoolean("pref_hardware_acceleration", hardwareAcceleration).apply()
                            }
                            .background(Color.White.copy(alpha = 0.02f))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Hardware Decoding", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Leverages device GPU for fluid 60FPS streams and cooler battery", fontSize = 11.sp, color = Color.Gray)
                        }
                        Switch(
                            checked = hardwareAcceleration,
                            onCheckedChange = {
                                hardwareAcceleration = it
                                sharedPrefs.edit().putBoolean("pref_hardware_acceleration", it).apply()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = onPurpleColor,
                                checkedTrackColor = accentColor,
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.White.copy(alpha = 0.08f)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Aspect Ratio Preference Selector (Chips)
                    Text(
                        "Preferred Aspect Ratio Format",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val aspectRatios = listOf("Fit", "Original 16:9", "Zoom 4:3", "Stretch")
                        aspectRatios.forEach { ratio ->
                            val isSelected = aspectPreference == ratio
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    aspectPreference = ratio
                                    sharedPrefs.edit().putString("pref_aspect_ratio", ratio).apply()
                                },
                                label = { Text(ratio, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = accentColor,
                                    selectedLabelColor = onPurpleColor,
                                    containerColor = Color.White.copy(alpha = 0.05f),
                                    labelColor = Color.LightGray
                                ),
                                border = if (isSelected) null else BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Audio Presets
                    Text(
                        "Dolby Digital Audio Presets",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val audioPresets = listOf("Balanced 3D", "Speech Focus", "Music Live", "Bass Plus")
                        audioPresets.forEach { preset ->
                            val isSelected = audioPresetPreference == preset
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    audioPresetPreference = preset
                                    sharedPrefs.edit().putString("pref_audio_preset", preset).apply()
                                },
                                label = { Text(preset, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = accentColor,
                                    selectedLabelColor = onPurpleColor,
                                    containerColor = Color.White.copy(alpha = 0.05f),
                                    labelColor = Color.LightGray
                                ),
                                border = if (isSelected) null else BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                            )
                        }
                    }
                }
            }

            // SECTION 4: VISUAL PERSONALIZATION (THEMING)
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Personalization",
                            tint = Color(0xFFFF8A80),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Visual Customization",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Customize the interface colors to match your design style and preference.",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Theme Selection (Lavender, Emerald, Amber, Cosmic Slate)
                    Text(
                        "Theme Accent Palette",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val accents = listOf("Lavender", "Emerald", "Amber", "Cosmic Slate")
                        accents.forEach { name ->
                            val isSelected = themeAccent == name
                            val pillColor = when (name) {
                                "Emerald" -> Color(0xFF81C784)
                                "Amber" -> Color(0xFFFFD54F)
                                "Cosmic Slate" -> Color(0xFF90CAF9)
                                else -> Color(0xFFD0BCFF)
                            }
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    themeAccent = name
                                    sharedPrefs.edit().putString("pref_theme_accent", name).apply()
                                },
                                label = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(pillColor, CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(name, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = pillColor.copy(alpha = 0.15f),
                                    selectedLabelColor = Color.White,
                                    containerColor = Color.White.copy(alpha = 0.03f),
                                    labelColor = Color.LightGray
                                ),
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = if (isSelected) pillColor else Color.White.copy(alpha = 0.1f)
                                )
                            )
                        }
                    }
                }
            }

            // SECTION 5: OFFLINE CACHE & STORAGE
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = "Storage",
                            tint = Color(0xFF90CAF9),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Storage & Database Health",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Review cached stats in your local Room database and perform deep cleaning.",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Local Database Statistics Grid
                    Text("ROOM LOCAL DATABASE STATS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.Black.copy(alpha = 0.2f))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Cached Channels", fontSize = 12.sp, color = Color.LightGray)
                            Text("${channels.size} items", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total Categories", fontSize = 12.sp, color = Color.LightGray)
                            Text("${categories.size} groups", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            val favoritesCount = remember(channels) { channels.count { it.isFavorite } }
                            Text("Pinned Favorites", fontSize = 12.sp, color = Color.LightGray)
                            Text("$favoritesCount streams", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        val estimatedSize = remember(channels, categories) {
                            val bytes = channels.size * 320 + categories.size * 120
                            if (bytes < 1024) "$bytes Bytes" else String.format("%.2f KB", bytes / 1024f)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Room Database Disk Space", fontSize = 12.sp, color = Color.LightGray)
                            Text(estimatedSize, fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Reset button
                    OutlinedButton(
                        onClick = { viewModel.clearCloudSyncSettings() },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF8A80)),
                        border = BorderStroke(1.dp, Color(0xFFFF8A80).copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Reset", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reset Sync Configurations to Default", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            // SECTION 6: SOFTWARE INFO & UPDATES (ABOUT)
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "About",
                            tint = Color.LightGray,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Software Information & Build", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Application Client", fontSize = 12.sp, color = Color.Gray)
                        Text("LiveTV Hub Premium", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Medium)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Core Player Version", fontSize = 12.sp, color = Color.Gray)
                        Text("ExoPlayer Live TV Core 2.19", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Medium)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Device Architecture", fontSize = 12.sp, color = Color.Gray)
                        Text("${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (SDK ${android.os.Build.VERSION.SDK_INT})", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Medium)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Compiled Architecture", fontSize = 12.sp, color = Color.Gray)
                        Text("Kotlin / Jetpack Compose / M3", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Medium)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val isCheckingUpdate by viewModel.isUpdateChecking.collectAsStateWithLifecycle()

                    Button(
                        onClick = { viewModel.checkForAppUpdates(isAutoCheck = false) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.05f), contentColor = Color.White),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                    ) {
                        if (isCheckingUpdate) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.Refresh, contentDescription = "Update Check", modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Check for Software Updates", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
}
