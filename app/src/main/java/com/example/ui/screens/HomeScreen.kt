package com.example.ui.screens

import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.example.ui.components.VideoPlayer
import com.example.ui.components.rememberResponsiveGridSpec
import com.example.ui.components.ResponsiveGridSpec
import com.example.ui.components.ResponsiveGridFrame
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.platform.LocalContext
import java.io.InputStreamReader
import java.io.BufferedReader
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.Canvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.focusable
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.data.CategoryEntity
import com.example.data.ChannelEntity
import com.example.data.GroupedChannel
import com.example.data.StreamSource
import com.example.data.RecordingEntity
import com.example.ui.ChannelViewModel
import kotlinx.coroutines.Dispatchers

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: ChannelViewModel,
    onNavigateToPlayer: () -> Unit,
    onNavigateToAdmin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spec = rememberResponsiveGridSpec()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val channels by viewModel.filteredGroupedChannels.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteGroupedChannels.collectAsStateWithLifecycle()
    val selectedCategoryId by viewModel.selectedCategoryId.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchHistory by viewModel.searchHistory.collectAsStateWithLifecycle()
    val recentSearchChannelNames by viewModel.recentSearchChannelNames.collectAsStateWithLifecycle()
    val searchFilterWorkingOnly by viewModel.searchFilterWorkingOnly.collectAsStateWithLifecycle()
    val searchFilterFavoritesOnly by viewModel.searchFilterFavoritesOnly.collectAsStateWithLifecycle()
    val searchFilterWithEpgOnly by viewModel.searchFilterWithEpgOnly.collectAsStateWithLifecycle()
    val searchFilterCategoryId by viewModel.searchFilterCategoryId.collectAsStateWithLifecycle()
    val isPublicMode by viewModel.isPublicMode.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val isCheckingStreams by viewModel.isCheckingStreams.collectAsStateWithLifecycle()
    val recentlyWatched by viewModel.recentlyWatched.collectAsStateWithLifecycle()

    val autoSyncOnLaunch by viewModel.autoSyncOnLaunch.collectAsStateWithLifecycle()
    val lastSyncTime by viewModel.lastSyncTime.collectAsStateWithLifecycle()
    val syncStatusMessage by viewModel.syncStatusMessage.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val lowLatencyMode by viewModel.lowLatencyMode.collectAsStateWithLifecycle()
    val cloudGistUrl by viewModel.cloudGistUrl.collectAsStateWithLifecycle()

    val streamCheckingProgress by viewModel.streamCheckingProgress.collectAsStateWithLifecycle()
    val streamCheckingStatus by viewModel.streamCheckingStatus.collectAsStateWithLifecycle()
    val filterBrokenChannels by viewModel.filterBrokenChannels.collectAsStateWithLifecycle()
    val selectedChannel by viewModel.selectedChannel.collectAsStateWithLifecycle()
    val currentEpgProgramsMap by viewModel.currentEpgProgramsMap.collectAsStateWithLifecycle()

    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    var showFavoritesOnly by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var favoritesSubTab by remember { mutableStateOf(0) } // 0 = Starred Feeds, 1 = Recorded Shows
    var titleTapCount by remember { mutableStateOf(0) }
    var showAdminOverride by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(currentTab) {
        if (currentTab == 3) {
            viewModel.fetchLiveEvents()
        }
    }

    BackHandler(enabled = (showSettings || currentTab != 0 || selectedCategoryId != null)) {
        if (showSettings) {
            showSettings = false
        } else if (selectedCategoryId != null) {
            viewModel.selectCategory(null)
        } else {
            viewModel.setCurrentTab(0)
            viewModel.setSearchQuery("")
        }
    }

    val lazyGridState = rememberLazyGridState()
    val categoriesGridState = rememberLazyGridState()
    val favoritesGridState = rememberLazyGridState()
    val searchGridState = rememberLazyGridState()
    val eventsListState = rememberLazyListState()
    val isScrolling by remember {
        derivedStateOf { lazyGridState.isScrollInProgress }
    }

    // Optimized O(1) category ID mapping
    val categoryMap = remember(categories) { categories.associate { it.id to it.name } }

    var categorySearchQuery by remember { mutableStateOf("") }
    var categorySortMode by remember { mutableStateOf(0) } // 0 = Popular first, 1 = A-Z
    val hideEmptyCategories by remember { mutableStateOf(true) } // Hide empty categories by default for a pristine feed experience

    val allChannelsRaw by viewModel.channels.collectAsStateWithLifecycle()
    
    // Highly efficient O(N) category counts map with O(1) lookup
    val categoryCounts = remember(allChannelsRaw, filterBrokenChannels) {
        val listToCount = if (filterBrokenChannels) {
            allChannelsRaw.filter { !it.isBroken }
        } else {
            allChannelsRaw
        }
        listToCount.groupBy { it.categoryId }.mapValues { it.value.size }
    }
    
    val totalActiveChannelsCount = remember(categoryCounts) { categoryCounts.values.sum() }
    
    val populatedCategories = remember(categories, categoryCounts) {
        categories.filter { (categoryCounts[it.id] ?: 0) > 0 }
    }

    val categoryRowState = rememberLazyListState()
    
    var targetAlpha by remember { mutableStateOf(1f) }
    val gridAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = 350, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "grid_fade_in"
    )

    LaunchedEffect(selectedCategoryId, showFavoritesOnly) {
        targetAlpha = 0f
        delay(20)
        targetAlpha = 1f
    }
    
    // Auto-scroll the horizontal category scroller to bring the selected category chip into focus
    LaunchedEffect(selectedCategoryId, showFavoritesOnly, populatedCategories) {
        if (showFavoritesOnly) {
            try {
                categoryRowState.animateScrollToItem(1)
            } catch (e: Exception) {}
        } else if (selectedCategoryId != null && populatedCategories.isNotEmpty()) {
            val index = populatedCategories.indexOfFirst { it.id == selectedCategoryId }
            if (index != -1) {
                try {
                    // index + 2 because "All" is at index 0 and "Starred" is at index 1
                    categoryRowState.animateScrollToItem(index + 2)
                } catch (e: Exception) {}
            }
        } else if (selectedCategoryId == null) {
            try {
                categoryRowState.animateScrollToItem(0)
            } catch (e: Exception) {}
        }
    }

    val processedCategories = remember(categories, categoryCounts, categorySearchQuery, categorySortMode, hideEmptyCategories) {
        categories
            .filter { category ->
                val count = categoryCounts[category.id] ?: 0
                val matchesSearch = categorySearchQuery.isEmpty() || category.name.contains(categorySearchQuery, ignoreCase = true)
                val matchesEmpty = !hideEmptyCategories || count > 0
                matchesSearch && matchesEmpty
            }
            .sortedWith { c1, c2 ->
                val count1 = categoryCounts[c1.id] ?: 0
                val count2 = categoryCounts[c2.id] ?: 0
                when (categorySortMode) {
                    0 -> {
                        val countCompare = count2.compareTo(count1)
                        if (countCompare != 0) countCompare else c1.name.compareTo(c2.name, ignoreCase = true)
                    }
                    1 -> {
                        c1.name.compareTo(c2.name, ignoreCase = true)
                    }
                    else -> 0
                }
            }
    }

    // Stable, remembered callback handlers to prevent layout recomposition during scroll
    val onCardClick = remember(viewModel, onNavigateToPlayer, focusManager) {
        { groupedChannel: GroupedChannel ->
            focusManager.clearFocus()
            viewModel.selectGroupedChannel(groupedChannel)
            onNavigateToPlayer()
        }
    }
    val onFavoriteClick = remember(viewModel) {
        { groupedChannel: GroupedChannel ->
            viewModel.toggleFavoriteGroup(groupedChannel, !groupedChannel.isFavorite)
        }
    }
    val onRecordingCardClick = remember(viewModel, onNavigateToPlayer, focusManager) {
        { channel: ChannelEntity ->
            focusManager.clearFocus()
            viewModel.selectChannel(channel)
            onNavigateToPlayer()
        }
    }

    val showScrollToTop by remember {
        derivedStateOf {
            if (showSettings) {
                false
            } else {
                when (currentTab) {
                    0 -> lazyGridState.firstVisibleItemIndex > 2
                    1 -> categoriesGridState.firstVisibleItemIndex > 2
                    2 -> if (favoritesSubTab == 0) favoritesGridState.firstVisibleItemIndex > 2 else false
                    3 -> eventsListState.firstVisibleItemIndex > 2
                    4 -> searchGridState.firstVisibleItemIndex > 2
                    else -> false
                }
            }
        }
    }

    // Visual theme variables
    val darkBg = Color(0xFF131215) // Deep luxury cinematic dark background
    val cardBg = Color(0xFF1E1C22) // Sleek Material dark surface
    val accentColor = Color(0xFFD0BCFF) // Frosted theme Purple Accent
    val secondaryAccentColor = Color(0xFFEADDFF)
    val onPurpleColor = Color(0xFF381E72)

    if (selectedCategoryId != null) {
        val currentCategoryName = categoryMap[selectedCategoryId] ?: "General"
        val listToDisplay = if (showFavoritesOnly) {
            favorites
        } else {
            channels
        }
        CategoryHubScreen(
            categoryName = currentCategoryName,
            categoryId = selectedCategoryId!!,
            listToDisplay = listToDisplay,
            allChannelsRaw = allChannelsRaw,
            currentEpgProgramsMap = currentEpgProgramsMap,
            onCardClick = { onCardClick(it) },
            onFavoriteClick = { onFavoriteClick(it) },
            onBackClick = { viewModel.selectCategory(null) },
            cardBg = cardBg,
            accentColor = accentColor,
            modifier = modifier
        )
        return
    }

    Scaffold(
        floatingActionButton = {
            AnimatedVisibility(
                visible = showScrollToTop,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                FloatingActionButton(
                    onClick = {
                        coroutineScope.launch {
                            when (currentTab) {
                                0 -> lazyGridState.animateScrollToItem(0)
                                1 -> categoriesGridState.animateScrollToItem(0)
                                2 -> if (favoritesSubTab == 0) favoritesGridState.animateScrollToItem(0)
                                3 -> eventsListState.animateScrollToItem(0)
                                4 -> searchGridState.animateScrollToItem(0)
                            }
                        }
                    },
                    containerColor = accentColor,
                    contentColor = onPurpleColor,
                    shape = CircleShape,
                    modifier = Modifier
                        .padding(bottom = 76.dp)
                        .testTag("scroll_to_top_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Scroll to top",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        },
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
                Column(
                    modifier = widthModifier
                        .padding(horizontal = spec.margin, vertical = 12.dp)
                ) {
                if (showSettings) {
                    // Settings Top Bar with Back button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { showSettings = false },
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .size(40.dp)
                                    .background(Color.White.copy(alpha = 0.04f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White
                                )
                            }
                            Column {
                                Text(
                                    text = "Portal Settings",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    letterSpacing = (-0.5).sp
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(accentColor, RoundedCornerShape(50))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "MANAGE SOURCES & PREFERENCES",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = accentColor,
                                        letterSpacing = 1.5.sp
                                    )
                                }
                            }
                        }
                    }
                } else if (currentTab == 0) {
                    val context = LocalContext.current
                    val currentHour = remember {
                        try {
                            java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                        } catch (e: Exception) {
                            12
                        }
                    }
                    val greetingTitle = remember(currentHour) {
                        when (currentHour) {
                            in 0..4 -> "Home"
                            in 5..11 -> "Morning Broadcasts"
                            in 12..16 -> "Afternoon Stream Hub"
                            in 17..21 -> "Evening Prime Time"
                            else -> "Late Night Cinema"
                        }
                    }
                    val greetingSubtitle = remember(currentHour) {
                        when (currentHour) {
                            in 0..4 -> "Curated nocturnal channels broadcasting live"
                            in 5..11 -> "Start your day with global live channels"
                            in 12..16 -> "Keep pace with active live streams"
                            in 17..21 -> "Your front row seat to premium live feeds"
                            else -> "Unwind with late night television networks"
                        }
                    }

                    // Luxury Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable(
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication = null
                                ) {
                                    titleTapCount++
                                    if (titleTapCount >= 5) {
                                        showAdminOverride = true
                                    }
                                }
                        ) {
                            Text(
                                text = greetingTitle,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                letterSpacing = (-0.5).sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = greetingSubtitle,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.5f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // High-End Micro Verify Pulse
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.04f))
                                    .clickable {
                                        viewModel.verifyAllChannels()
                                        Toast.makeText(context, "Scanning stream connectivity status...", Toast.LENGTH_SHORT).show()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isCheckingStreams) {
                                    CircularProgressIndicator(
                                        color = accentColor,
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.0.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Refresh & Verify Channels",
                                        tint = Color.White.copy(alpha = 0.8f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            ProfileAvatarWithStatus(
                                isOnline = isOnline,
                                onClick = { onNavigateToAdmin() }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Category Scroller & Filters
                    LazyRow(
                        state = categoryRowState,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            CategoryFilterChip(
                                selected = selectedCategoryId == null && !showFavoritesOnly,
                                label = "ALL BROADCASTS",
                                icon = Icons.Default.AllInclusive,
                                count = totalActiveChannelsCount,
                                onClick = {
                                    showFavoritesOnly = false
                                    viewModel.selectCategory(null)
                                }
                            )
                        }

                        item {
                            CategoryFilterChip(
                                selected = showFavoritesOnly,
                                label = "STARRED",
                                icon = Icons.Default.Favorite,
                                count = favorites.size,
                                onClick = {
                                    showFavoritesOnly = true
                                    viewModel.selectCategory(null)
                                }
                            )
                        }

                        items(populatedCategories) { category ->
                            val count = categoryCounts[category.id] ?: 0
                            CategoryFilterChip(
                                selected = selectedCategoryId == category.id && !showFavoritesOnly,
                                label = category.name.uppercase(),
                                icon = getCategoryIcon(category.name),
                                count = count,
                                onClick = {
                                    showFavoritesOnly = false
                                    viewModel.selectCategory(category.id)
                                }
                            )
                        }
                    }
                    

                } else if (currentTab == 1) {
                    // Explore Categories Top Bar
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Explore Genres",
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    letterSpacing = (-0.5).sp
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(accentColor, RoundedCornerShape(50))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "DISCOVER BROADCASTS BY GENRE",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = accentColor,
                                        letterSpacing = 1.5.sp
                                    )
                                }
                            }
                            ProfileAvatarWithStatus(
                                isOnline = isOnline,
                                onClick = { onNavigateToAdmin() }
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Smart Search & Sort UI
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Search categories field
                            OutlinedTextField(
                                value = categorySearchQuery,
                                onValueChange = { categorySearchQuery = it },
                                placeholder = { Text("Search genres...", fontSize = 13.sp, color = Color.Gray) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Search",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                trailingIcon = {
                                    if (categorySearchQuery.isNotEmpty()) {
                                        IconButton(
                                            onClick = { categorySearchQuery = "" },
                                            modifier = Modifier.size(16.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Clear",
                                                tint = Color.Gray
                                            )
                                        }
                                    }
                                },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = accentColor,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
                                    focusedContainerColor = cardBg,
                                    unfocusedContainerColor = cardBg
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                            )
                            
                            // Sort Option Popularity Chip
                            FilterChip(
                                selected = categorySortMode == 0,
                                onClick = { categorySortMode = 0 },
                                label = { Text("Popular", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = accentColor,
                                    selectedLabelColor = onPurpleColor,
                                    containerColor = cardBg,
                                    labelColor = Color(0xFFCAC4D0)
                                ),
                                border = null,
                                shape = RoundedCornerShape(10.dp)
                            )
                            
                            // Sort Option Alphabetical Chip
                            FilterChip(
                                selected = categorySortMode == 1,
                                onClick = { categorySortMode = 1 },
                                label = { Text("A-Z", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = accentColor,
                                    selectedLabelColor = onPurpleColor,
                                    containerColor = cardBg,
                                    labelColor = Color(0xFFCAC4D0)
                                ),
                                border = null,
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }
                } else if (currentTab == 2) {
                    // Favorites Top Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "My Library",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                letterSpacing = (-0.5).sp
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(accentColor, RoundedCornerShape(50))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "FAVORITES & RECORDINGS",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = accentColor,
                                    letterSpacing = 1.5.sp
                                )
                            }
                        }
                        ProfileAvatarWithStatus(
                            isOnline = isOnline,
                            onClick = { onNavigateToAdmin() }
                        )
                    }
                } else {
                    // Search Top Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Live Search",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                letterSpacing = (-0.5).sp
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(accentColor, RoundedCornerShape(50))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "EXPLORE 1,000+ STREAMS INSTANTLY",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = accentColor,
                                    letterSpacing = 1.5.sp
                                )
                            }
                        }
                        ProfileAvatarWithStatus(
                            isOnline = isOnline,
                            onClick = { onNavigateToAdmin() }
                        )
                    }
                }
            }
        }
    },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                contentAlignment = Alignment.Center
            ) {
                val widthModifier = if (spec.maxContentWidth != androidx.compose.ui.unit.Dp.Unspecified) {
                    Modifier.widthIn(max = spec.maxContentWidth)
                } else {
                    Modifier.fillMaxWidth()
                }
                Column(
                    modifier = widthModifier
                        .background(Color.Transparent)
                ) {
                if (!isOnline) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFE53935))
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Offline Mode",
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Offline Mode - Check Network Connection",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spec.margin)
                        .padding(bottom = 12.dp, top = 12.dp)
                        .height(92.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    // Glassmorphic Base Dock Surface with dynamic ambient glow shadow
                    Surface(
                        color = Color(0xFF100C1F).copy(alpha = 0.82f),
                        shape = RoundedCornerShape(28.dp),
                        border = BorderStroke(
                            1.dp,
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.18f),
                                    Color.White.copy(alpha = 0.03f)
                                )
                            )
                        ),
                        tonalElevation = 12.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                            .shadow(
                                elevation = 16.dp,
                                shape = RoundedCornerShape(28.dp),
                                clip = false,
                                ambientColor = accentColor.copy(alpha = 0.5f),
                                spotColor = accentColor.copy(alpha = 0.5f)
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight()
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FloatingBottomBarItem(
                                selected = !showSettings && currentTab == 0,
                                onClick = { 
                                    showSettings = false
                                    if (currentTab == 0) {
                                        showFavoritesOnly = false
                                        viewModel.selectCategory(null)
                                        coroutineScope.launch { lazyGridState.animateScrollToItem(0) }
                                    } else {
                                        viewModel.setCurrentTab(0)
                                    }
                                    viewModel.setSearchQuery("")
                                },
                                icon = Icons.Outlined.Home,
                                selectedIcon = Icons.Filled.Home,
                                label = "Home",
                                accentColor = accentColor,
                                modifier = Modifier.weight(1f).testTag("nav_home")
                            )
                            FloatingBottomBarItem(
                                selected = !showSettings && currentTab == 1,
                                onClick = { 
                                    showSettings = false
                                    if (currentTab == 1) {
                                        coroutineScope.launch { categoryRowState.animateScrollToItem(0) }
                                    } else {
                                        viewModel.setCurrentTab(1)
                                    }
                                    viewModel.setSearchQuery("")
                                },
                                icon = Icons.Outlined.Category,
                                selectedIcon = Icons.Filled.Category,
                                label = "Categories",
                                accentColor = accentColor,
                                modifier = Modifier.weight(1f).testTag("nav_categories")
                            )
                            
                            // Perfectly proportioned central gap for the overlapping search button
                            Spacer(modifier = Modifier.weight(1.2f))
                            
                            FloatingBottomBarItem(
                                selected = !showSettings && currentTab == 2,
                                onClick = { 
                                    showSettings = false
                                    viewModel.setCurrentTab(2)
                                    viewModel.setSearchQuery("")
                                },
                                icon = Icons.Outlined.FavoriteBorder,
                                selectedIcon = Icons.Filled.Favorite,
                                label = "Favorites",
                                accentColor = accentColor,
                                modifier = Modifier.weight(1f).testTag("nav_favorites")
                            )
                            FloatingBottomBarItem(
                                selected = !showSettings && currentTab == 3,
                                onClick = { 
                                    showSettings = false
                                    if (currentTab == 3) {
                                        viewModel.triggerEventsScrollToTop()
                                    } else {
                                        viewModel.setCurrentTab(3)
                                    }
                                },
                                icon = Icons.Outlined.LiveTv,
                                selectedIcon = Icons.Filled.LiveTv,
                                label = "Events",
                                accentColor = accentColor,
                                hasLiveBadge = true,
                                modifier = Modifier.weight(1f).testTag("nav_events")
                            )
                        }
                    }
                    
                    // High-fidelity pulsing sonar ring behind the Floating Search FAB
                    val searchSelected = !showSettings && currentTab == 4
                    val pulseTransition = rememberInfiniteTransition(label = "pulseRing")
                    val pulseRadius by pulseTransition.animateFloat(
                        initialValue = 58f,
                        targetValue = 82f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1500, easing = LinearOutSlowInEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "pulseRadius"
                    )
                    val pulseAlpha by pulseTransition.animateFloat(
                        initialValue = 0.45f,
                        targetValue = 0.0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1500, easing = LinearOutSlowInEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "pulseAlpha"
                    )

                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = (31f - pulseRadius / 2f).dp)
                            .size(pulseRadius.dp)
                            .border(
                                width = 1.2.dp,
                                color = (if (searchSelected) accentColor else Color.White).copy(alpha = pulseAlpha),
                                shape = CircleShape
                            )
                    )

                    // Floating Circular Search button overlapping the bar perfectly
                    val searchScale by animateFloatAsState(
                        targetValue = if (searchSelected) 1.15f else 1.0f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                        label = "searchFABScale"
                    )
                    
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = 2.dp) // Perfect calculated vertical alignment relative to the 92.dp container height
                            .graphicsLayer {
                                scaleX = searchScale
                                scaleY = searchScale
                            }
                            .shadow(
                                elevation = if (searchSelected) 16.dp else 6.dp,
                                shape = CircleShape,
                                clip = false,
                                ambientColor = if (searchSelected) accentColor else Color.Black,
                                spotColor = if (searchSelected) accentColor else Color.Black
                            )
                            .size(58.dp)
                            .clip(CircleShape)
                            .background(
                                if (searchSelected) {
                                    Brush.linearGradient(
                                        colors = listOf(
                                            accentColor,
                                            Color(0xFF6200EE)
                                        )
                                    )
                                } else {
                                    Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xFF1E1B24),
                                            Color(0xFF0F0B1E)
                                        )
                                    )
                                }
                            )
                            .border(
                                width = 1.5.dp,
                                brush = if (searchSelected) {
                                    Brush.verticalGradient(
                                        colors = listOf(Color.White.copy(alpha = 0.7f), Color.Transparent)
                                    )
                                } else {
                                    Brush.verticalGradient(
                                        colors = listOf(Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.05f))
                                    )
                                },
                                shape = CircleShape
                            )
                            .clickable {
                                showSettings = false
                                viewModel.setCurrentTab(4)
                             }
                            .testTag("nav_search"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = if (searchSelected) Color(0xFF100C1F) else Color.White.copy(alpha = 0.75f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    },
        containerColor = darkBg,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

        if (showSettings) {
            SettingsOverlayContent(
                viewModel = viewModel,
                innerPadding = innerPadding,
                cardBg = cardBg,
                accentColor = accentColor,
                onPurpleColor = onPurpleColor,
                isCheckingStreams = isCheckingStreams,
                streamCheckingProgress = streamCheckingProgress,
                streamCheckingStatus = streamCheckingStatus,
                filterBrokenChannels = filterBrokenChannels,
                autoSyncOnLaunch = autoSyncOnLaunch,
                lastSyncTime = lastSyncTime,
                syncStatusMessage = syncStatusMessage,
                isLoading = isLoading,
                lowLatencyMode = lowLatencyMode,
                cloudGistUrl = cloudGistUrl,
                isPublicMode = isPublicMode,
                spec = spec
            )
        } else if (currentTab == 0) {
            val listToDisplay = if (showFavoritesOnly) {
                favorites
            } else {
                channels
            }



            val trendingChannels = remember(channels) {
                if (channels.isEmpty()) {
                    emptyList()
                } else {
                    channels.groupBy { it.categoryId }
                        .values
                        .map { it.first() }
                        .take(3)
                }
            }

            PullToRefreshBox(
                isRefreshing = isLoading,
                onRefresh = { viewModel.syncWithCloudGist() },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding(), bottom = innerPadding.calculateBottomPadding())
            ) {
                if (isLoading) {
                    val infiniteTransition = rememberInfiniteTransition(label = "skeleton_shimmer")
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 0.4f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 800, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "shimmer_alpha"
                    )

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(spec.columns),
                        contentPadding = PaddingValues(
                            start = spec.margin,
                            end = spec.margin,
                            top = 12.dp,
                            bottom = 24.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(spec.gutter),
                        verticalArrangement = Arrangement.spacedBy(spec.gutter),
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (spec.maxContentWidth != androidx.compose.ui.unit.Dp.Unspecified) {
                                    Modifier.widthIn(max = spec.maxContentWidth).align(Alignment.TopCenter)
                                } else {
                                    Modifier
                                }
                            )
                    ) {
                        items(8, contentType = { "channel_skeleton" }) {
                            SkeletonChannelCard(alpha = alpha, cardBg = cardBg)
                        }
                    }
                } else if (listToDisplay.isEmpty()) {
                    // Elegant Designer Empty State (scrollable so pull-to-refresh remains interactive when list is empty)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(top = 40.dp, bottom = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Icon(
                                imageVector = if (showFavoritesOnly) Icons.Default.FavoriteBorder else Icons.Default.Info,
                                contentDescription = "Empty list",
                                tint = Color.Gray.copy(alpha = 0.5f),
                                modifier = Modifier.size(80.dp)
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                text = if (showFavoritesOnly) {
                                    "No Saved Favorites"
                                } else if (searchQuery.isNotEmpty()) {
                                    "No Search Results"
                                } else {
                                    "No Broadcasts Found"
                                },
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (showFavoritesOnly) {
                                    "Click the heart button on live feeds to collect your favorite channels here."
                                } else if (searchQuery.isNotEmpty()) {
                                    "We couldn't find any channels matching \"$searchQuery\". Try checking the spelling or typing a different keyword."
                                } else {
                                    "We couldn't find any channels matching this query. Please register new feeds in settings."
                                },
                                fontSize = 13.sp,
                                color = Color.Gray,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(0.85f)
                            )
                        }
                    }
                } else {
                    val featuredChannel = remember(channels) {
                        channels.firstOrNull { it.name.lowercase().contains("sports") || it.name.lowercase().contains("news") || it.name.lowercase().contains("cricket") || it.name.lowercase().contains("football") }
                            ?: channels.firstOrNull()
                    }
                    LazyVerticalGrid(
                        state = lazyGridState,
                        columns = GridCells.Fixed(spec.columns),
                        contentPadding = PaddingValues(
                            start = spec.margin,
                            end = spec.margin,
                            top = 12.dp,
                            bottom = 24.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(spec.gutter),
                        verticalArrangement = Arrangement.spacedBy(spec.gutter),
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = gridAlpha }
                            .then(
                                if (spec.maxContentWidth != androidx.compose.ui.unit.Dp.Unspecified) {
                                    Modifier.widthIn(max = spec.maxContentWidth).align(Alignment.TopCenter)
                                } else {
                                    Modifier
                                }
                            )
                    ) {
                        // PREMIUM CINEMATIC HERO BANNER (Top spotlight recommendation)
                        if (searchQuery.isEmpty() && !showFavoritesOnly && selectedCategoryId == null && channels.isNotEmpty()) {
                            if (featuredChannel != null) {
                                item(
                                    span = { GridItemSpan(maxLineSpan) },
                                    key = "premium_hero_banner_item",
                                    contentType = "hero_banner"
                                ) {
                                    val catName = categoryMap[featuredChannel.categoryId] ?: "General"
                                    val currentEpg = currentEpgProgramsMap[featuredChannel.name.lowercase()]
                                    PremiumHeroBanner(
                                        channel = featuredChannel,
                                        categoryName = catName,
                                        currentProgram = currentEpg,
                                        onClick = { onCardClick(featuredChannel) },
                                        onToggleFavorite = { onFavoriteClick(featuredChannel) },
                                        cardBg = cardBg,
                                        accentColor = accentColor,
                                        modifier = Modifier.padding(bottom = 16.dp)
                                    )
                                }
                            }
                        }

                        // RECENTLY WATCHED SECTION (Sleek Apple TV styled glassmorphism swimlane)
                        if (recentlyWatched.isNotEmpty() && searchQuery.isEmpty() && !showFavoritesOnly) {
                            item(
                                span = { GridItemSpan(maxLineSpan) },
                                key = "recently_watched_row_item",
                                contentType = "recently_watched"
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 16.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.History,
                                            contentDescription = "Recently Watched",
                                            tint = accentColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "RECENTLY WATCHED",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color.White,
                                            letterSpacing = 1.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        items(
                                            items = recentlyWatched,
                                            key = { "recent_${it.name}" },
                                            contentType = { "recently_watched_item" }
                                        ) { groupedChannel ->
                                            val interactionSource = remember { MutableInteractionSource() }
                                            val isFocused by interactionSource.collectIsFocusedAsState()
                                            val isHovered by interactionSource.collectIsHoveredAsState()
                                            val isPressed by interactionSource.collectIsPressedAsState()
                                            val isScaled = isFocused || isHovered

                                            val scale by animateFloatAsState(
                                                targetValue = when {
                                                    isPressed -> 0.95f
                                                    isScaled -> 1.06f
                                                    else -> 1.0f
                                                },
                                                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
                                                label = "recent_card_scale"
                                            )

                                            val borderColor by animateColorAsState(
                                                targetValue = if (isScaled) accentColor else Color.White.copy(alpha = 0.12f),
                                                animationSpec = tween(durationMillis = 200),
                                                label = "recent_card_border"
                                            )

                                            val shadowElevation by animateDpAsState(
                                                targetValue = if (isScaled) 12.dp else 2.dp,
                                                animationSpec = spring(stiffness = Spring.StiffnessLow),
                                                label = "recent_card_shadow"
                                            )

                                            Card(
                                                shape = RoundedCornerShape(16.dp),
                                                border = BorderStroke(1.2.dp, borderColor),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = cardBg.copy(alpha = 0.78f)
                                                ),
                                                modifier = Modifier
                                                    .width(170.dp)
                                                    .graphicsLayer(scaleX = scale, scaleY = scale)
                                                    .shadow(
                                                        elevation = shadowElevation,
                                                        shape = RoundedCornerShape(16.dp),
                                                        ambientColor = if (isScaled) accentColor.copy(alpha = 0.35f) else Color.Transparent,
                                                        spotColor = if (isScaled) accentColor.copy(alpha = 0.35f) else Color.Transparent
                                                    )
                                                    .clickable(
                                                        interactionSource = interactionSource,
                                                        indication = androidx.compose.foundation.LocalIndication.current,
                                                        onClick = { onCardClick(groupedChannel) }
                                                    )
                                                    .focusable(interactionSource = interactionSource)
                                                    .testTag("recent_channel_${groupedChannel.name}")
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .padding(12.dp)
                                                        .fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    SubcomposeAsyncImage(
                                                        model = groupedChannel.logoUrl,
                                                        contentDescription = groupedChannel.name,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier
                                                            .size(36.dp)
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .background(Color.White.copy(alpha = 0.1f)),
                                                        loading = {
                                                            ShimmerPlaceholder(modifier = Modifier.fillMaxSize())
                                                        }
                                                    )
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    Column {
                                                        Text(
                                                            text = groupedChannel.name,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color.White,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Spacer(modifier = Modifier.height(2.dp))
                                                        Text(
                                                            text = "WATCH AGAIN",
                                                            fontSize = 8.5.sp,
                                                            fontWeight = FontWeight.Black,
                                                            color = accentColor,
                                                            letterSpacing = 0.5.sp
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                                }
                            }
                        }

                        // TRENDING BROADCASTS BENTO GRID SECTION
                        if (searchQuery.isEmpty() && !showFavoritesOnly && selectedCategoryId == null && trendingChannels.isNotEmpty()) {
                            item(
                                span = { GridItemSpan(maxLineSpan) },
                                key = "bento_trending_header",
                                contentType = "bento_trending_header"
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 16.dp, bottom = 8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Whatshot,
                                            contentDescription = "Trending Now",
                                            tint = Color(0xFFFF5722),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "TRENDING BROADCASTS",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color.White,
                                            letterSpacing = 1.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "The most popular live streams across all categories",
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.5f)
                                    )
                                }
                            }

                            // Featured Top Trending Channel (Large, spans 2 columns)
                            val topTrending = trendingChannels.first()
                            item(
                                span = { GridItemSpan(if (maxLineSpan >= 2) 2 else 1) },
                                key = "bento_trending_featured_${topTrending.name}",
                                contentType = "bento_featured"
                            ) {
                                val catName = categoryMap[topTrending.categoryId] ?: "General"
                                BentoFeaturedChannelCard(
                                    channel = topTrending,
                                    categoryName = catName,
                                    onClick = { onCardClick(topTrending) },
                                    onToggleFavorite = { onFavoriteClick(topTrending) },
                                    cardBg = cardBg,
                                    accentColor = accentColor,
                                    isScrolling = isScrolling,
                                    watchCount = viewModel.getChannelWatchCount(topTrending.name)
                                )
                            }

                            // Other trending channels as compact cards
                            val otherTrending = trendingChannels.drop(1)
                            items(
                                items = otherTrending,
                                key = { "bento_trending_compact_${it.name}" },
                                contentType = { "bento_compact" }
                            ) { groupedChannel ->
                                val catName = categoryMap[groupedChannel.categoryId] ?: "General"
                                BentoCompactChannelCard(
                                    channel = groupedChannel,
                                    categoryName = catName,
                                    onClick = { onCardClick(groupedChannel) },
                                    cardBg = cardBg,
                                    accentColor = accentColor,
                                    isScrolling = isScrolling,
                                    watchCount = viewModel.getChannelWatchCount(groupedChannel.name)
                                )
                            }

                            // Horizontal Divider after Trending Bento section
                            item(
                                span = { GridItemSpan(maxLineSpan) },
                                key = "bento_trending_divider",
                                contentType = "bento_divider"
                            ) {
                                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                                }
                            }
                        }

                        if (searchQuery.isNotEmpty() || showFavoritesOnly) {
                            // Standard flat grid when user is searching or viewing favorites
                            items(
                                items = listToDisplay,
                                key = { "flat_${it.name}" },
                                contentType = { "channel" }
                            ) { groupedChannel ->
                                val repChannel = remember(groupedChannel) {
                                    ChannelEntity(
                                        id = groupedChannel.originalChannelIds.firstOrNull() ?: 0,
                                        name = groupedChannel.name,
                                        streamUrl = groupedChannel.streams.firstOrNull()?.url ?: "",
                                        logoUrl = groupedChannel.logoUrl,
                                        categoryId = groupedChannel.categoryId,
                                        description = groupedChannel.description,
                                        isFavorite = groupedChannel.isFavorite,
                                        isBroken = groupedChannel.isBroken
                                    )
                                }
                                val categoryName = categoryMap[repChannel.categoryId] ?: "General"
                                ChannelCard(
                                    channel = repChannel,
                                    categoryName = categoryName,
                                    onClick = { onCardClick(groupedChannel) },
                                    onToggleFavorite = { onFavoriteClick(groupedChannel) },
                                    cardBg = cardBg,
                                    accentColor = accentColor,
                                    isScrolling = isScrolling
                                )
                            }
                        } else {
                            // ALL CATEGORIES HULU-STYLE SWIMLANES (Cinematic streaming layout)
                            if (favorites.isNotEmpty()) {
                                item(
                                    span = { GridItemSpan(maxLineSpan) },
                                    key = "favorites_swimlane_header",
                                    contentType = "swimlane_header"
                                ) {
                                    BentoCategoryHeader(
                                        categoryName = "STARRED CHANNELS",
                                        count = favorites.size,
                                        onViewAllClick = { viewModel.setCurrentTab(2) }
                                    )
                                }

                                item(
                                    span = { GridItemSpan(maxLineSpan) },
                                    key = "favorites_swimlane_row",
                                    contentType = "swimlane_row"
                                ) {
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        items(
                                            items = favorites,
                                            key = { "fav_swimlane_${it.name}" }
                                        ) { groupedChannel ->
                                            val repChannel = remember(groupedChannel) {
                                                ChannelEntity(
                                                    id = groupedChannel.originalChannelIds.firstOrNull() ?: 0,
                                                    name = groupedChannel.name,
                                                    streamUrl = groupedChannel.streams.firstOrNull()?.url ?: "",
                                                    logoUrl = groupedChannel.logoUrl,
                                                    categoryId = groupedChannel.categoryId,
                                                    description = groupedChannel.description,
                                                    isFavorite = groupedChannel.isFavorite,
                                                    isBroken = groupedChannel.isBroken
                                                )
                                            }
                                            val categoryName = categoryMap[repChannel.categoryId] ?: "General"
                                            val epgProgram = currentEpgProgramsMap[groupedChannel.name.lowercase()]
                                                ?: allChannelsRaw.find { it.name.equals(groupedChannel.name, ignoreCase = true) }?.tvgId?.let { tvgId ->
                                                    currentEpgProgramsMap[tvgId.lowercase()]
                                                }
                                            ChannelCard(
                                                channel = repChannel,
                                                categoryName = categoryName,
                                                onClick = { onCardClick(groupedChannel) },
                                                onToggleFavorite = { onFavoriteClick(groupedChannel) },
                                                cardBg = cardBg,
                                                accentColor = accentColor,
                                                isScrolling = isScrolling,
                                                currentProgram = epgProgram,
                                                modifier = Modifier.width(240.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            for (category in populatedCategories) {
                                val categoryGroupedChannels = channels.filter { it.categoryId == category.id }
                                if (categoryGroupedChannels.isNotEmpty()) {
                                    item(
                                        span = { GridItemSpan(maxLineSpan) },
                                        key = "swimlane_header_${category.id}",
                                        contentType = "swimlane_header"
                                    ) {
                                        BentoCategoryHeader(
                                            categoryName = category.name.uppercase(),
                                            count = categoryGroupedChannels.size,
                                            onViewAllClick = { viewModel.selectCategory(category.id) }
                                        )
                                    }

                                    item(
                                        span = { GridItemSpan(maxLineSpan) },
                                        key = "swimlane_row_${category.id}",
                                        contentType = "swimlane_row"
                                    ) {
                                        LazyRow(
                                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            items(
                                                items = categoryGroupedChannels,
                                                key = { "swimlane_item_${category.id}_${it.name}" }
                                            ) { groupedChannel ->
                                                val repChannel = remember(groupedChannel) {
                                                    ChannelEntity(
                                                        id = groupedChannel.originalChannelIds.firstOrNull() ?: 0,
                                                        name = groupedChannel.name,
                                                        streamUrl = groupedChannel.streams.firstOrNull()?.url ?: "",
                                                        logoUrl = groupedChannel.logoUrl,
                                                        categoryId = groupedChannel.categoryId,
                                                        description = groupedChannel.description,
                                                        isFavorite = groupedChannel.isFavorite,
                                                        isBroken = groupedChannel.isBroken
                                                    )
                                                }
                                                val categoryName = category.name
                                                val epgProgram = currentEpgProgramsMap[groupedChannel.name.lowercase()]
                                                    ?: allChannelsRaw.find { it.name.equals(groupedChannel.name, ignoreCase = true) }?.tvgId?.let { tvgId ->
                                                        currentEpgProgramsMap[tvgId.lowercase()]
                                                    }
                                                ChannelCard(
                                                    channel = repChannel,
                                                    categoryName = categoryName,
                                                    onClick = { onCardClick(groupedChannel) },
                                                    onToggleFavorite = { onFavoriteClick(groupedChannel) },
                                                    cardBg = cardBg,
                                                    accentColor = accentColor,
                                                    isScrolling = isScrolling,
                                                    currentProgram = epgProgram,
                                                    modifier = Modifier.width(240.dp)
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
        } else if (currentTab == 1) {
            // Tab 1: Categories View
            val allChannels by viewModel.channels.collectAsStateWithLifecycle()
            if (isLoading) {
                val infiniteTransition = rememberInfiniteTransition(label = "categories_skeleton_shimmer")
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.4f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 800, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "shimmer_alpha"
                )

                Box(modifier = Modifier.fillMaxSize()) {
                    LazyVerticalGrid(
                        state = categoriesGridState,
                        columns = GridCells.Fixed(spec.columns),
                        contentPadding = PaddingValues(
                            start = spec.margin,
                            end = spec.margin,
                            top = innerPadding.calculateTopPadding() + 8.dp,
                            bottom = innerPadding.calculateBottomPadding() + 24.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(spec.gutter),
                        verticalArrangement = Arrangement.spacedBy(spec.gutter),
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (spec.maxContentWidth != androidx.compose.ui.unit.Dp.Unspecified) {
                                    Modifier.widthIn(max = spec.maxContentWidth).align(Alignment.TopCenter)
                                } else {
                                    Modifier
                                }
                            )
                    ) {
                        items(6, contentType = { "category_skeleton" }) {
                            SkeletonCategoryCard(alpha = alpha, cardBg = cardBg)
                        }
                    }
                }
            } else if (processedCategories.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "No genres",
                            tint = Color.Gray.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Genres Found",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (categorySearchQuery.isNotEmpty()) "Try searching for a different keyword or category name." else "Head to Settings to synchronize categories or update your channel feed.",
                            fontSize = 13.sp,
                            color = Color.Gray,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyVerticalGrid(
                        state = categoriesGridState,
                        columns = GridCells.Fixed(spec.columns),
                        contentPadding = PaddingValues(
                            start = spec.margin,
                            end = spec.margin,
                            top = innerPadding.calculateTopPadding() + 8.dp,
                            bottom = innerPadding.calculateBottomPadding() + 24.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(spec.gutter),
                        verticalArrangement = Arrangement.spacedBy(spec.gutter),
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (spec.maxContentWidth != androidx.compose.ui.unit.Dp.Unspecified) {
                                    Modifier.widthIn(max = spec.maxContentWidth).align(Alignment.TopCenter)
                                } else {
                                    Modifier
                                }
                            )
                    ) {
                    items(items = processedCategories, key = { it.id }, contentType = { "category" }) { category ->
                        val count = categoryCounts[category.id] ?: 0
                        val interactionSource = remember { MutableInteractionSource() }
                        val isFocused by interactionSource.collectIsFocusedAsState()
                        val isHovered by interactionSource.collectIsHoveredAsState()
                        val isPressed = isFocused || isHovered
                        
                        val categoryScale by animateFloatAsState(
                            targetValue = if (isPressed) 1.05f else 1.00f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                            label = "categoryCardScale"
                        )
                        val categoryBorderColor by animateColorAsState(
                            targetValue = if (isPressed) Color.White else Color.White.copy(alpha = 0.08f),
                            animationSpec = tween(durationMillis = 150),
                            label = "categoryCardBorder"
                        )
                        
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(1.dp, categoryBorderColor),
                            colors = CardDefaults.cardColors(containerColor = cardBg),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .graphicsLayer(scaleX = categoryScale, scaleY = categoryScale)
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = androidx.compose.foundation.LocalIndication.current
                                ) {
                                    viewModel.selectCategory(category.id)
                                    showFavoritesOnly = false
                                    viewModel.setCurrentTab(0)
                                    viewModel.setSearchQuery("")
                                }
                                .focusable(interactionSource = interactionSource)
                                .testTag("category_card_${category.id}")
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(getCategoryGradient(category.name))
                                    .padding(18.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .background(Color.White.copy(alpha = 0.15f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = getCategoryIcon(category.name),
                                                contentDescription = category.name,
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        
                                        Box(
                                            modifier = Modifier
                                                .background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = if (count == 1) "1 Feed" else "$count Feeds",
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    
                                    Column {
                                        Text(
                                            text = category.name,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Tap to explore",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color.White.copy(alpha = 0.6f),
                                            letterSpacing = 0.5.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                }
            }
        } else if (currentTab == 2) {
            // Tab 2: Favorites Screen (Starred Feeds + Offline recorded shows!)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding()),
                contentAlignment = Alignment.TopCenter
            ) {
                val widthModifier = if (spec.maxContentWidth != androidx.compose.ui.unit.Dp.Unspecified) {
                    Modifier.widthIn(max = spec.maxContentWidth)
                } else {
                    Modifier.fillMaxSize()
                }
                Column(
                    modifier = widthModifier.fillMaxSize()
                ) {
                    // Secondary horizontal sliding subtabs for Favorites
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spec.margin, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilterChip(
                        selected = favoritesSubTab == 0,
                        onClick = { favoritesSubTab = 0 },
                        label = { Text("Starred Feeds (${favorites.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = "Starred Feeds",
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accentColor,
                            selectedLabelColor = onPurpleColor,
                            selectedLeadingIconColor = onPurpleColor,
                            containerColor = cardBg,
                            labelColor = Color(0xFFCAC4D0),
                            iconColor = Color(0xFFCAC4D0)
                        ),
                        border = null,
                        shape = CircleShape,
                        modifier = Modifier.testTag("fav_subtab_starred")
                    )

                    val recordings by viewModel.recordings.collectAsStateWithLifecycle()
                    FilterChip(
                        selected = favoritesSubTab == 1,
                        onClick = { favoritesSubTab = 1 },
                        label = { Text("Offline Vault (${recordings.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Offline Library",
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accentColor,
                            selectedLabelColor = onPurpleColor,
                            selectedLeadingIconColor = onPurpleColor,
                            containerColor = cardBg,
                            labelColor = Color(0xFFCAC4D0),
                            iconColor = Color(0xFFCAC4D0)
                        ),
                        border = null,
                        shape = CircleShape,
                        modifier = Modifier.testTag("fav_subtab_vault")
                    )
                }

                if (favoritesSubTab == 0) {
                    if (favorites.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FavoriteBorder,
                                    contentDescription = "No Favorites",
                                    tint = Color.Gray.copy(alpha = 0.5f),
                                    modifier = Modifier.size(80.dp)
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                Text(
                                    text = "No Saved Favorites",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Tap the heart icon on any channel card while browsing to add it to your quick-access favorites list.",
                                    fontSize = 13.sp,
                                    color = Color.Gray,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(0.85f)
                                )
                            }
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize()) {
                            LazyVerticalGrid(
                                state = favoritesGridState,
                                columns = GridCells.Fixed(spec.columns),
                                contentPadding = PaddingValues(
                                    start = spec.margin,
                                    end = spec.margin,
                                    top = 8.dp,
                                    bottom = innerPadding.calculateBottomPadding() + 24.dp
                                ),
                                horizontalArrangement = Arrangement.spacedBy(spec.gutter),
                                verticalArrangement = Arrangement.spacedBy(spec.gutter),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(
                                        if (spec.maxContentWidth != androidx.compose.ui.unit.Dp.Unspecified) {
                                            Modifier.widthIn(max = spec.maxContentWidth).align(Alignment.TopCenter)
                                        } else {
                                            Modifier
                                        }
                                    )
                            ) {
                            items(
                                items = favorites,
                                key = { "fav_${it.name}" },
                                contentType = { "channel" }
                            ) { groupedChannel ->
                                val repChannel = remember(groupedChannel) {
                                    ChannelEntity(
                                        id = groupedChannel.originalChannelIds.firstOrNull() ?: 0,
                                        name = groupedChannel.name,
                                        streamUrl = groupedChannel.streams.firstOrNull()?.url ?: "",
                                        logoUrl = groupedChannel.logoUrl,
                                        categoryId = groupedChannel.categoryId,
                                        description = groupedChannel.description,
                                        isFavorite = groupedChannel.isFavorite,
                                        isBroken = groupedChannel.isBroken
                                    )
                                }
                                val categoryName = categoryMap[repChannel.categoryId] ?: "General"
                                ChannelCard(
                                    channel = repChannel,
                                    categoryName = categoryName,
                                    onClick = { onCardClick(groupedChannel) },
                                    onToggleFavorite = { onFavoriteClick(groupedChannel) },
                                    cardBg = cardBg,
                                    accentColor = accentColor,
                                    isScrolling = isScrolling
                                )
                            }
                        }
                        }
                    }
                } else {
                    OfflineRecordingsScreen(
                        viewModel = viewModel,
                        innerPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding()),
                        onCardClick = onRecordingCardClick,
                        cardBg = cardBg,
                        accentColor = accentColor,
                        spec = spec
                    )
                }
            }
        }
        } else if (currentTab == 3) {
            // Tab 3: Dynamic Live Events Screen
            LiveEventsScreen(
                viewModel = viewModel,
                innerPadding = innerPadding,
                cardBg = cardBg,
                accentColor = accentColor,
                eventsListState = eventsListState,
                onPlayFeed = { event, feed ->
                    viewModel.playLiveEventFeed(event, feed)
                },
                onNavigateToPlayer = onNavigateToPlayer,
                spec = spec
            )
        } else {
            // Tab 4: Dedicated Live Search Screen
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding()),
                contentAlignment = Alignment.TopCenter
            ) {
                val widthModifier = if (spec.maxContentWidth != androidx.compose.ui.unit.Dp.Unspecified) {
                    Modifier.widthIn(max = spec.maxContentWidth)
                } else {
                    Modifier.fillMaxSize()
                }
                Column(
                    modifier = widthModifier.fillMaxSize()
                ) {
                var isSearchFocused by remember { mutableStateOf(false) }
                val searchBorderColor by animateColorAsState(
                    targetValue = if (isSearchFocused) accentColor else Color.White.copy(alpha = 0.06f),
                    animationSpec = tween(durationMillis = 200),
                    label = "searchBorderColor"
                )

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text("Search 1,000+ live TV feeds...", color = Color.Gray, fontSize = 14.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = if (isSearchFocused) accentColor else Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            viewModel.addSearchQueryToHistory(searchQuery)
                            focusManager.clearFocus()
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = searchBorderColor,
                        focusedContainerColor = cardBg,
                        unfocusedContainerColor = cardBg
                    ),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spec.margin, vertical = 8.dp)
                        .onFocusChanged { isSearchFocused = it.isFocused }
                        .testTag("search_tab_input")
                )

                // Advanced Interactive Filter Chips Row
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = spec.margin, vertical = 4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    item {
                        SearchFilterChip(
                            text = "Working Only",
                            selected = searchFilterWorkingOnly,
                            onClick = { viewModel.searchFilterWorkingOnly.value = !searchFilterWorkingOnly },
                            activeColor = accentColor,
                            leadingIcon = {
                                Icon(
                                    imageVector = if (searchFilterWorkingOnly) Icons.Default.CheckCircle else Icons.Default.CloudQueue,
                                    contentDescription = null,
                                    tint = if (searchFilterWorkingOnly) accentColor else Color.Gray,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        )
                    }

                    item {
                        SearchFilterChip(
                            text = "Favorites Only",
                            selected = searchFilterFavoritesOnly,
                            onClick = { viewModel.searchFilterFavoritesOnly.value = !searchFilterFavoritesOnly },
                            activeColor = accentColor,
                            leadingIcon = {
                                Icon(
                                    imageVector = if (searchFilterFavoritesOnly) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = null,
                                    tint = if (searchFilterFavoritesOnly) accentColor else Color.Gray,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        )
                    }

                    item {
                        SearchFilterChip(
                            text = "With Guide (EPG)",
                            selected = searchFilterWithEpgOnly,
                            onClick = { viewModel.searchFilterWithEpgOnly.value = !searchFilterWithEpgOnly },
                            activeColor = accentColor,
                            leadingIcon = {
                                Icon(
                                    imageVector = if (searchFilterWithEpgOnly) Icons.Default.LiveTv else Icons.Default.Tv,
                                    contentDescription = null,
                                    tint = if (searchFilterWithEpgOnly) accentColor else Color.Gray,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        )
                    }

                    item {
                        SearchFilterChip(
                            text = "All Categories",
                            selected = searchFilterCategoryId == null,
                            onClick = { viewModel.searchFilterCategoryId.value = null },
                            activeColor = accentColor
                        )
                    }

                    items(categories) { cat ->
                        SearchFilterChip(
                            text = cat.name,
                            selected = searchFilterCategoryId == cat.id,
                            onClick = { viewModel.searchFilterCategoryId.value = cat.id },
                            activeColor = accentColor
                        )
                    }
                }

                val allGroupedChannels = remember(allChannelsRaw) {
                    viewModel.groupChannels(allChannelsRaw)
                }

                if (searchQuery.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // 1. Recent Searches Section
                        if (searchHistory.isNotEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = spec.margin, vertical = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "RECENT SEARCHES",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        color = accentColor,
                                        letterSpacing = 1.5.sp
                                    )
                                    Text(
                                        text = "Clear All",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Gray,
                                        modifier = Modifier
                                            .clickable { viewModel.clearSearchHistory() }
                                            .padding(4.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(searchHistory) { query ->
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = Color.White.copy(alpha = 0.04f),
                                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 10.dp)
                                            ) {
                                                Text(
                                                    text = query,
                                                    fontSize = 12.sp,
                                                    color = Color.White,
                                                    modifier = Modifier
                                                        .clickable {
                                                            viewModel.setSearchQuery(query)
                                                            viewModel.addSearchQueryToHistory(query)
                                                        }
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Remove",
                                                    tint = Color.Gray,
                                                    modifier = Modifier
                                                        .size(14.dp)
                                                        .clickable { viewModel.removeSearchQueryFromHistory(query) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        // 2. Recent Channels Section
                        val recentGroupedChannels = remember(allGroupedChannels, recentSearchChannelNames) {
                            recentSearchChannelNames.mapNotNull { name ->
                                allGroupedChannels.find { it.name.equals(name, ignoreCase = true) }
                            }
                        }
                        if (recentGroupedChannels.isNotEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = spec.margin, vertical = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "RECENTLY WATCHED CHANNELS",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        color = accentColor,
                                        letterSpacing = 1.5.sp
                                    )
                                    Text(
                                        text = "Clear All",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Gray,
                                        modifier = Modifier
                                            .clickable { viewModel.clearRecentSearchChannels() }
                                            .padding(4.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(recentGroupedChannels) { groupedChannel ->
                                        val repChannel = remember(groupedChannel) {
                                            ChannelEntity(
                                                id = groupedChannel.originalChannelIds.firstOrNull() ?: 0,
                                                name = groupedChannel.name,
                                                streamUrl = groupedChannel.streams.firstOrNull()?.url ?: "",
                                                logoUrl = groupedChannel.logoUrl,
                                                categoryId = groupedChannel.categoryId,
                                                description = groupedChannel.description,
                                                isFavorite = groupedChannel.isFavorite,
                                                isBroken = groupedChannel.isBroken
                                            )
                                        }
                                        val categoryName = categoryMap[repChannel.categoryId] ?: "General"
                                        
                                        Card(
                                            shape = RoundedCornerShape(12.dp),
                                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                                            colors = CardDefaults.cardColors(containerColor = cardBg),
                                            modifier = Modifier
                                                .width(220.dp)
                                                .clickable {
                                                    viewModel.addChannelToSearchHistory(groupedChannel.name)
                                                    onCardClick(groupedChannel)
                                                }
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(10.dp)
                                            ) {
                                                if (repChannel.logoUrl.isNotEmpty()) {
                                                    AsyncImage(
                                                        model = ImageRequest.Builder(LocalContext.current)
                                                            .data(repChannel.logoUrl)
                                                            .crossfade(true)
                                                            .build(),
                                                        contentDescription = repChannel.name,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier
                                                            .size(44.dp)
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .background(Color.White.copy(alpha = 0.05f))
                                                    )
                                                } else {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(44.dp)
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .background(Color.White.copy(alpha = 0.05f)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Tv,
                                                            contentDescription = null,
                                                            tint = Color.Gray,
                                                            modifier = Modifier.size(22.dp)
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = repChannel.name,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = categoryName,
                                                        fontSize = 10.sp,
                                                        color = accentColor,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        // 3. Popular Suggestions Section
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = spec.margin, vertical = 8.dp)
                        ) {
                            Text(
                                text = "POPULAR SUGGESTIONS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = accentColor,
                                letterSpacing = 1.5.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            val suggestions = listOf("News", "Sports", "Movies", "Music", "Kids", "Entertainment", "Documentary")
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(suggestions) { keyword ->
                                    Card(
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                                        colors = CardDefaults.cardColors(containerColor = cardBg),
                                        modifier = Modifier
                                            .clickable {
                                                viewModel.setSearchQuery(keyword)
                                                viewModel.addSearchQueryToHistory(keyword)
                                            }
                                            .testTag("search_suggestion_$keyword")
                                    ) {
                                        Text(
                                            text = keyword,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Onboarding icon and text
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search live streams",
                                    tint = Color.Gray.copy(alpha = 0.3f),
                                    modifier = Modifier.size(80.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Discover Live Feeds",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Search 1,000+ live streams by channel name, description, category, or country.",
                                    fontSize = 13.sp,
                                    color = Color.Gray,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(0.80f)
                                )
                            }
                        }
                    }
                } else {
                    val (filteredChannels, searchTimeMs) = remember(
                        allGroupedChannels,
                        searchQuery,
                        searchFilterWorkingOnly,
                        searchFilterFavoritesOnly,
                        searchFilterWithEpgOnly,
                        searchFilterCategoryId,
                        currentEpgProgramsMap,
                        allChannelsRaw
                    ) {
                        val startTime = System.nanoTime()
                        
                        val filtered = allGroupedChannels.filter { groupedChannel ->
                            val epgProgram = currentEpgProgramsMap[groupedChannel.name.lowercase()]
                                ?: allChannelsRaw.find { it.name.equals(groupedChannel.name, ignoreCase = true) }?.tvgId?.let { tvgId ->
                                    currentEpgProgramsMap[tvgId.lowercase()]
                                }
                            
                            val epgMatches = if (epgProgram != null && searchQuery.isNotEmpty()) {
                                epgProgram.title.contains(searchQuery, ignoreCase = true) ||
                                epgProgram.description.contains(searchQuery, ignoreCase = true) ||
                                epgProgram.category.contains(searchQuery, ignoreCase = true)
                            } else {
                                false
                            }

                            val queryMatches = if (searchQuery.isNotEmpty()) {
                                groupedChannel.name.contains(searchQuery, ignoreCase = true) ||
                                groupedChannel.description.contains(searchQuery, ignoreCase = true) ||
                                (categoryMap[groupedChannel.categoryId]?.contains(searchQuery, ignoreCase = true) == true) ||
                                epgMatches
                            } else {
                                true
                            }

                            val workingMatches = !searchFilterWorkingOnly || !groupedChannel.isBroken
                            val favoriteMatches = !searchFilterFavoritesOnly || groupedChannel.isFavorite
                            
                            val epgExists = epgProgram != null
                            val epgMatchesFilter = !searchFilterWithEpgOnly || epgExists
                            
                            val categoryMatchesFilter = searchFilterCategoryId == null || groupedChannel.categoryId == searchFilterCategoryId

                            queryMatches && workingMatches && favoriteMatches && epgMatchesFilter && categoryMatchesFilter
                        }

                        val sorted = filtered.sortedWith { ch1, ch2 ->
                            fun getScore(ch: GroupedChannel): Int {
                                var score = 0
                                if (searchQuery.isNotEmpty()) {
                                    val nameLower = ch.name.lowercase()
                                    val queryLower = searchQuery.lowercase()
                                    if (nameLower == queryLower) {
                                        score += 2000
                                    } else if (nameLower.startsWith(queryLower)) {
                                        score += 1000
                                    } else if (nameLower.contains(queryLower)) {
                                        score += 500
                                    }
                                    if (ch.description.lowercase().contains(queryLower)) {
                                        score += 200
                                    }
                                    val catName = categoryMap[ch.categoryId]?.lowercase() ?: ""
                                    if (catName.contains(queryLower)) {
                                        score += 100
                                    }
                                }
                                if (ch.isFavorite) score += 300
                                if (!ch.isBroken) score += 500
                                return score
                            }

                            val score1 = getScore(ch1)
                            val score2 = getScore(ch2)
                            if (score1 != score2) {
                                score2.compareTo(score1)
                            } else {
                                val epg1 = currentEpgProgramsMap[ch1.name.lowercase()]
                                    ?: allChannelsRaw.find { it.name.equals(ch1.name, ignoreCase = true) }?.tvgId?.let { tvgId ->
                                        currentEpgProgramsMap[tvgId.lowercase()]
                                    }
                                val epg2 = currentEpgProgramsMap[ch2.name.lowercase()]
                                    ?: allChannelsRaw.find { it.name.equals(ch2.name, ignoreCase = true) }?.tvgId?.let { tvgId ->
                                        currentEpgProgramsMap[tvgId.lowercase()]
                                    }

                                val title1 = epg1?.title?.lowercase() ?: ""
                                val title2 = epg2?.title?.lowercase() ?: ""
                                val cat1 = epg1?.category?.lowercase() ?: ""
                                val cat2 = epg2?.category?.lowercase() ?: ""

                                val isSport1 = title1.contains("football") || title1.contains("soccer") || title1.contains("cricket") ||
                                               cat1.contains("football") || cat1.contains("soccer") || cat1.contains("cricket") ||
                                               ch1.name.lowercase().contains("football") || ch1.name.lowercase().contains("cricket") ||
                                               ch1.name.lowercase().contains("willow") || ch1.name.lowercase().contains("t sports") ||
                                               ch1.name.lowercase().contains("bein sport") || ch1.name.lowercase().contains("sky sports")

                                val isSport2 = title2.contains("football") || title2.contains("soccer") || title2.contains("cricket") ||
                                               cat2.contains("football") || cat2.contains("soccer") || cat2.contains("cricket") ||
                                               ch2.name.lowercase().contains("football") || ch2.name.lowercase().contains("cricket") ||
                                               ch2.name.lowercase().contains("willow") || ch2.name.lowercase().contains("t sports") ||
                                               ch2.name.lowercase().contains("bein sport") || ch2.name.lowercase().contains("sky sports")

                                when {
                                    isSport1 && !isSport2 -> -1
                                    !isSport1 && isSport2 -> 1
                                    else -> {
                                        val rep1 = com.example.data.ChannelClassifier.getChannelReputationScore(ch1.name)
                                        val rep2 = com.example.data.ChannelClassifier.getChannelReputationScore(ch2.name)
                                        if (rep1 != rep2) {
                                            rep2.compareTo(rep1)
                                        } else {
                                            ch1.name.compareTo(ch2.name, ignoreCase = true)
                                        }
                                    }
                                }
                            }
                        }

                        val endTime = System.nanoTime()
                        val durationMs = (endTime - startTime) / 1_000_000.0
                        val formattedTime = String.format("%.1f", durationMs)
                        Pair(sorted, formattedTime)
                    }

                    // Telemetry Results Stats Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spec.margin, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SEARCH RESULTS (${filteredChannels.size})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = accentColor,
                            letterSpacing = 1.5.sp
                        )
                        Text(
                            text = "Processed in ${searchTimeMs}ms",
                            fontSize = 10.sp,
                            color = Color.Gray,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (filteredChannels.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "No results",
                                    tint = Color.Gray.copy(alpha = 0.5f),
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "No Channels Found",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "We couldn't find any live feeds matching \"$searchQuery\". Try checking active filters, typing a different keyword, or verifying spelling.",
                                    fontSize = 13.sp,
                                    color = Color.Gray,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(0.85f)
                                )
                            }
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize()) {
                            LazyVerticalGrid(
                                state = searchGridState,
                                columns = GridCells.Fixed(spec.columns),
                                contentPadding = PaddingValues(
                                    start = spec.margin,
                                    end = spec.margin,
                                    top = 8.dp,
                                    bottom = innerPadding.calculateBottomPadding() + 24.dp
                                ),
                                horizontalArrangement = Arrangement.spacedBy(spec.gutter),
                                verticalArrangement = Arrangement.spacedBy(spec.gutter),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(
                                        if (spec.maxContentWidth != androidx.compose.ui.unit.Dp.Unspecified) {
                                            Modifier.widthIn(max = spec.maxContentWidth).align(Alignment.TopCenter)
                                        } else {
                                            Modifier
                                        }
                                    )
                            ) {
                            items(
                                items = filteredChannels,
                                key = { "search_${it.name}" },
                                contentType = { "channel" }
                            ) { groupedChannel ->
                                val repChannel = remember(groupedChannel) {
                                    ChannelEntity(
                                        id = groupedChannel.originalChannelIds.firstOrNull() ?: 0,
                                        name = groupedChannel.name,
                                        streamUrl = groupedChannel.streams.firstOrNull()?.url ?: "",
                                        logoUrl = groupedChannel.logoUrl,
                                        categoryId = groupedChannel.categoryId,
                                        description = groupedChannel.description,
                                        isFavorite = groupedChannel.isFavorite,
                                        isBroken = groupedChannel.isBroken
                                    )
                                }
                                val categoryName = categoryMap[repChannel.categoryId] ?: "General"
                                val epgProgram = remember(groupedChannel, currentEpgProgramsMap, allChannelsRaw) {
                                    currentEpgProgramsMap[groupedChannel.name.lowercase()]
                                        ?: allChannelsRaw.find { it.name.equals(groupedChannel.name, ignoreCase = true) }?.tvgId?.let { tvgId ->
                                            currentEpgProgramsMap[tvgId.lowercase()]
                                        }
                                }
                                ChannelCard(
                                    channel = repChannel,
                                    categoryName = categoryName,
                                    onClick = {
                                        if (searchQuery.isNotEmpty()) {
                                            viewModel.addSearchQueryToHistory(searchQuery)
                                        }
                                        viewModel.addChannelToSearchHistory(groupedChannel.name)
                                        onCardClick(groupedChannel)
                                    },
                                    onToggleFavorite = { onFavoriteClick(groupedChannel) },
                                    cardBg = cardBg,
                                    accentColor = accentColor,
                                    isScrolling = isScrolling,
                                    currentProgram = epgProgram,
                                    searchQuery = searchQuery
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
}

@Composable
fun HeroChannelBanner(
    channel: ChannelEntity,
    categoryName: String,
    onClick: (ChannelEntity) -> Unit,
    onToggleFavorite: (ChannelEntity) -> Unit,
    accentColor: Color,
    isScrolling: Boolean = false
) {
    val favScale by animateFloatAsState(
        targetValue = if (channel.isFavorite) 1.25f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "favScale"
    )

    Card(
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1C22)),
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clickable { onClick(channel) }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val context = LocalContext.current
            
            // Background category gradient fallback
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(brush = getCategoryGradient(categoryName), alpha = 0.35f)
            )

            val imageRequest = remember(channel.logoUrl, isScrolling) {
                ImageRequest.Builder(context)
                    .data(channel.logoUrl)
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
            // Widescreen background backdrop
            SubcomposeAsyncImage(
                model = imageRequest,
                contentDescription = channel.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    ShimmerPlaceholder(modifier = Modifier.fillMaxSize())
                }
            )

            // Multi-layered cinematics: Left dark vignette & Bottom dark vignette
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.1f),
                                Color.Black.copy(alpha = 0.9f)
                            )
                        )
                    )
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.85f),
                                Color.Black.copy(alpha = 0.1f)
                            ),
                            endX = 1200f
                        )
                    )
            )

            // Overlay controls and info
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top line
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(50))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(50))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        PulsatingCardIndicator()
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "LIVE FEATURED",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            letterSpacing = 1.2.sp
                        )
                    }

                    // Fast Favorite Toggler with spring bounce
                    IconButton(
                        onClick = { onToggleFavorite(channel) },
                        modifier = Modifier
                            .size(38.dp)
                            .graphicsLayer(scaleX = favScale, scaleY = favScale)
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(50))
                    ) {
                        Icon(
                            imageVector = if (channel.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (channel.isFavorite) Color(0xFFFF3F3F) else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Title and action controls
                Column {
                    Text(
                        text = categoryName.uppercase(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = accentColor,
                        letterSpacing = 1.5.sp
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    Text(
                        text = channel.name,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        letterSpacing = (-0.5).sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = channel.description.ifBlank { "High-fidelity global television feed." },
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.65f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        // Large Play CTA Button with subtle luxury white glow
                        Button(
                            onClick = { onClick(channel) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color(0xFF131215)
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                            shape = RoundedCornerShape(50),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "TUNE IN", 
                                fontSize = 11.sp, 
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ShimmerPlaceholder(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "thumbnail_shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = -300f,
        targetValue = 600f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_offset"
    )

    val shimmerColors = listOf(
        Color.White.copy(alpha = 0.05f),
        Color.White.copy(alpha = 0.18f),
        Color.White.copy(alpha = 0.05f)
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnim, 0f),
        end = Offset(translateAnim + 300f, 300f)
    )

    Box(
        modifier = modifier
            .background(brush)
    )
}

@Composable
fun SkeletonChannelCard(
    alpha: Float,
    cardBg: Color
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .background(Color.White.copy(alpha = 0.05f * alpha))
            ) {
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .size(width = 60.dp, height = 18.dp)
                        .background(Color.White.copy(alpha = 0.08f * alpha), RoundedCornerShape(8.dp))
                )
            }
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(16.dp)
                        .background(Color.White.copy(alpha = 0.1f * alpha), RoundedCornerShape(4.dp))
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(11.dp)
                        .background(Color.White.copy(alpha = 0.05f * alpha), RoundedCornerShape(4.dp))
                )
            }
        }
    }
}

@Composable
fun SkeletonCategoryCard(
    alpha: Float,
    cardBg: Color
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.White.copy(alpha = 0.05f * alpha), CircleShape)
                    )
                }
                
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(18.dp)
                            .background(Color.White.copy(alpha = 0.1f * alpha), RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.3f)
                            .height(10.dp)
                            .background(Color.White.copy(alpha = 0.05f * alpha), RoundedCornerShape(2.dp))
                    )
                }
            }
        }
    }
}

@Composable
fun getHighlightedText(text: String, query: String, highlightColor: Color): AnnotatedString {
    return remember(text, query, highlightColor) {
        if (query.isEmpty()) return@remember AnnotatedString(text)
        val builder = AnnotatedString.Builder()
        val textLower = text.lowercase()
        val queryLower = query.trim().lowercase()
        if (queryLower.isEmpty()) return@remember AnnotatedString(text)
        
        var startIndex = 0
        while (true) {
            val index = textLower.indexOf(queryLower, startIndex)
            if (index == -1) {
                builder.append(text.substring(startIndex))
                break
            }
            builder.append(text.substring(startIndex, index))
            builder.pushStyle(SpanStyle(background = highlightColor.copy(alpha = 0.35f), color = Color.White, fontWeight = FontWeight.Black))
            builder.append(text.substring(index, index + queryLower.length))
            builder.pop()
            startIndex = index + queryLower.length
        }
        builder.toAnnotatedString()
    }
}

@Composable
fun SearchFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    activeColor: Color,
    leadingIcon: @Composable (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val isScaled = selected || isFocused || isHovered

    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.95f
            isScaled -> 1.06f
            else -> 1.0f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "search_chip_scale"
    )

    val containerColor by animateColorAsState(
        targetValue = if (selected) activeColor.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.08f),
        animationSpec = tween(durationMillis = 200)
    )

    val borderColor by animateColorAsState(
        targetValue = if (selected) activeColor else Color.White.copy(alpha = 0.15f),
        animationSpec = tween(durationMillis = 200)
    )

    val shadowElevation by animateDpAsState(
        targetValue = if (isScaled) 8.dp else 1.dp,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "search_chip_shadow"
    )

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(
            1.2.dp,
            borderColor
        ),
        modifier = Modifier
            .height(32.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .shadow(
                elevation = shadowElevation,
                shape = RoundedCornerShape(16.dp),
                ambientColor = if (isScaled) activeColor.copy(alpha = 0.35f) else Color.Transparent,
                spotColor = if (isScaled) activeColor.copy(alpha = 0.35f) else Color.Transparent
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            if (leadingIcon != null) {
                leadingIcon()
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = text,
                color = if (selected) activeColor else Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun ChannelCard(
    channel: ChannelEntity,
    categoryName: String,
    onClick: (ChannelEntity) -> Unit,
    onToggleFavorite: (ChannelEntity) -> Unit,
    cardBg: Color,
    accentColor: Color,
    isScrolling: Boolean = false,
    currentProgram: com.example.data.EpgProgramEntity? = null,
    searchQuery: String = "",
    modifier: Modifier = Modifier
) {
    val secondaryAccentColor = Color(0xFFEADDFF)
    val favScale by animateFloatAsState(
        targetValue = if (channel.isFavorite) 1.22f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "cardFavScale"
    )

    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    var isLongPressed by remember { mutableStateOf(false) }

    LaunchedEffect(interactionSource) {
        var pressJob: kotlinx.coroutines.Job? = null
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is androidx.compose.foundation.interaction.PressInteraction.Press -> {
                    pressJob?.cancel()
                    pressJob = launch {
                        delay(600)
                        isLongPressed = true
                    }
                }
                is androidx.compose.foundation.interaction.PressInteraction.Release,
                is androidx.compose.foundation.interaction.PressInteraction.Cancel -> {
                    pressJob?.cancel()
                    isLongPressed = false
                }
            }
        }
    }

    val isPreviewPlaying = isHovered || isLongPressed
    val isScaleUp = isFocused || isHovered || isLongPressed
    val cardScale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.95f
            isScaleUp -> 1.06f // Extra tactile scale like Apple TV zoom
            else -> 1.00f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "cardScaleAnimation"
    )
    val cardBorderColor by animateColorAsState(
        targetValue = if (isScaleUp) accentColor else Color.White.copy(alpha = 0.12f),
        animationSpec = tween(durationMillis = 200),
        label = "cardBorderColorAnimation"
    )
    
    val shadowElevation by animateDpAsState(
        targetValue = if (isScaleUp) 16.dp else 4.dp,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "card_shadow"
    )

    val cardModifier = if (modifier == Modifier) Modifier.fillMaxWidth() else modifier

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg.copy(alpha = 0.78f)), // Apple TV style glassmorphic transclucency
        border = BorderStroke(1.2.dp, cardBorderColor),
        modifier = cardModifier
            .graphicsLayer(scaleX = cardScale, scaleY = cardScale)
            .shadow(
                elevation = shadowElevation,
                shape = RoundedCornerShape(24.dp),
                ambientColor = if (isScaleUp) accentColor.copy(alpha = 0.4f) else Color.Transparent,
                spotColor = if (isScaleUp) accentColor.copy(alpha = 0.4f) else Color.Transparent
            )
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.foundation.LocalIndication.current
            ) { onClick(channel) }
            .focusable(interactionSource = interactionSource)
            .testTag("channel_card_${channel.id}")
    ) {
        Column {
            // Card Backdrop containing Live indicators and Favorite toggles
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(115.dp)
            ) {
                // Background category gradient fallback
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(brush = getCategoryGradient(categoryName), alpha = 0.25f)
                )

                val context = LocalContext.current
                val imageRequest = remember(channel.logoUrl, isScrolling) {
                    ImageRequest.Builder(context)
                        .data(channel.logoUrl)
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
                SubcomposeAsyncImage(
                    model = imageRequest,
                    contentDescription = channel.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    loading = {
                        ShimmerPlaceholder(modifier = Modifier.fillMaxSize())
                    }
                )

                val streamUrl = channel.streamUrl
                if (isPreviewPlaying && streamUrl.isNotEmpty()) {
                    VideoPlayer(
                        videoUrl = streamUrl,
                        isPlaying = true,
                        isMuted = true,
                        resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                        modifier = Modifier.fillMaxSize()
                    )
                    
                    // Small overlay badge
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .background(Color(0xFFE53935), CircleShape)
                            )
                            Text("PREVIEW", color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                            Icon(
                                imageVector = Icons.Default.VolumeOff,
                                contentDescription = "Muted Preview",
                                tint = Color.White,
                                modifier = Modifier.size(8.dp)
                            )
                        }
                    }
                }

                // Dark subtle vignette gradient
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                                startY = 40f
                            )
                        )
                )

                // Category tag (glass pill style)
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(50))
                        .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(50))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .align(Alignment.TopStart)
                ) {
                    Text(
                        text = categoryName.uppercase(),
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Black,
                        color = secondaryAccentColor,
                        letterSpacing = 0.5.sp
                    )
                }

                // Heart favorite indicator overlay with spring bounce
                IconButton(
                    onClick = { onToggleFavorite(channel) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(34.dp)
                        .graphicsLayer(scaleX = favScale, scaleY = favScale)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(50))
                ) {
                    Icon(
                        imageVector = if (channel.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (channel.isFavorite) Color(0xFFFF3F3F) else Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Dynamic Live Indicator (Static to optimize performance across large grids)
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StaticCardIndicator()
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "LIVE FEED",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            // Description and title area
            Column(
                modifier = Modifier.padding(14.dp)
            ) {
                Text(
                    text = getHighlightedText(channel.name, searchQuery, accentColor),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (currentProgram != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Playing Now",
                            tint = accentColor,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = getHighlightedText(currentProgram.title, searchQuery, accentColor),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))

                val fallbackDesc = channel.description.ifBlank { "Tap to stream live feed." }
                Text(
                    text = getHighlightedText(fallbackDesc, searchQuery, accentColor),
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.55f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

@Composable
fun BentoCategoryHeader(
    categoryName: String,
    count: Int,
    onViewAllClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    val arrowTranslation by animateFloatAsState(
        targetValue = if (isHovered || isFocused) 4f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "arrow_anim"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 12.dp, start = 4.dp, end = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(Color(0xFFE53935), CircleShape)
            )
            
            Text(
                text = categoryName.uppercase(),
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = 1.5.sp
            )
            
            Box(
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(8.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "$count LIVE",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFFD0BCFF),
                    letterSpacing = 0.5.sp
                )
            }
        }
        
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isHovered || isFocused) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.04f)
            ),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            modifier = Modifier
                .clickable(
                    interactionSource = interactionSource,
                    indication = androidx.compose.foundation.LocalIndication.current,
                    onClick = onViewAllClick
                )
                .focusable(interactionSource = interactionSource)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "VIEW COLLECTION",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFD0BCFF),
                    letterSpacing = 0.8.sp
                )
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier
                        .size(11.dp)
                        .graphicsLayer(
                            rotationZ = 180f,
                            translationX = arrowTranslation
                        ),
                    tint = Color(0xFFD0BCFF)
                )
            }
        }
    }
}

@Composable
fun BentoCategoryInfoCard(
    categoryName: String,
    count: Int,
    onClick: () -> Unit,
    cardBg: Color
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val isScaleUp = isFocused || isHovered
    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.96f
            isScaleUp -> 1.04f
            else -> 1.0f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "bento_info_scale"
    )

    val gradient = getCategoryGradient(categoryName)
    
    val categoryCopywriting = remember(categoryName) {
        when (categoryName.lowercase().trim()) {
            "sports", "live sports", "sports networks", "sport", "cricket", "football" -> 
                "Experience the thrill of live stadium sports, tournaments, and athletic action in real-time."
            "news" -> 
                "Stay informed with trusted news broadcasters, breaking global updates, and on-the-scene reporters."
            "entertainment", "show" -> 
                "Immerse in nonstop visual entertainment, original series, popular networks, and daily shows."
            "music" -> 
                "Stream stunning high-fidelity music networks, live performances, and modern playlist tracks."
            "movies", "premium movies", "drama", "cinema" -> 
                "Binge full theatrical dramas, premium movies, cinematic releases, and classic features live."
            "science", "documentary", "info" -> 
                "Discover the wonders of our world, historical chronicles, and deep scientific broadcasts."
            "kid", "kids", "cartoon", "animation", "child" -> 
                "Safe, animated child-friendly networks, educational stories, and joyful family streams."
            "religious", "islam", "prayer" -> 
                "Engage in peaceful spiritual programs, direct prayer streams, and religious reflection."
            else -> 
                "Stream premium high-definition live channels directly. Smooth connections with no delay."
        }
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, if (isScaleUp) Color(0xFFD0BCFF).copy(alpha = 0.3f) else Color.White.copy(alpha = 0.08f)),
        modifier = Modifier
            .fillMaxWidth()
            .height(145.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.foundation.LocalIndication.current,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(brush = gradient)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.15f), Color.Black.copy(alpha = 0.85f))))
                .padding(16.dp)
        ) {
            Icon(
                imageVector = getCategoryIcon(categoryName),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.08f),
                modifier = Modifier
                    .size(96.dp)
                    .align(Alignment.CenterEnd)
                    .offset(x = 12.dp, y = 12.dp)
            )

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = getCategoryIcon(categoryName),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    
                    Box(
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.15f), CircleShape)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(Color(0xFFE53935), CircleShape)
                            )
                            Text(
                                text = "ACTIVE HUB",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = categoryName.uppercase(),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = 0.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = categoryCopywriting,
                        fontSize = 10.5.sp,
                        color = Color.LightGray.copy(alpha = 0.9f),
                        lineHeight = 14.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$count Live Feeds Live Now",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD0BCFF),
                        letterSpacing = 0.2.sp
                    )
                }
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun BentoFeaturedChannelCard(
    channel: GroupedChannel,
    categoryName: String,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    cardBg: Color,
    accentColor: Color,
    isScrolling: Boolean = false,
    watchCount: Int = 0
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    var isLongPressed by remember { mutableStateOf(false) }

    LaunchedEffect(interactionSource) {
        var pressJob: kotlinx.coroutines.Job? = null
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is androidx.compose.foundation.interaction.PressInteraction.Press -> {
                    pressJob?.cancel()
                    pressJob = launch {
                        delay(600)
                        isLongPressed = true
                    }
                }
                is androidx.compose.foundation.interaction.PressInteraction.Release,
                is androidx.compose.foundation.interaction.PressInteraction.Cancel -> {
                    pressJob?.cancel()
                    isLongPressed = false
                }
            }
        }
    }

    val isPreviewPlaying = isHovered || isLongPressed
    val isScaleUp = isFocused || isHovered || isLongPressed
    val cardScale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.96f
            isScaleUp -> 1.04f
            else -> 1.0f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "featured_card_scale"
    )
    val cardBorderColor by animateColorAsState(
        targetValue = if (isScaleUp) accentColor else Color.White.copy(alpha = 0.1f),
        animationSpec = tween(durationMillis = 200),
        label = "featured_border_color"
    )

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, cardBorderColor),
        modifier = Modifier
            .fillMaxWidth()
            .height(145.dp)
            .graphicsLayer(scaleX = cardScale, scaleY = cardScale)
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.foundation.LocalIndication.current,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
            .testTag("bento_featured_${channel.name}")
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(brush = getCategoryGradient(categoryName), alpha = 0.15f)
            )

            val context = LocalContext.current
            val imageRequest = remember(channel.logoUrl, isScrolling) {
                ImageRequest.Builder(context)
                    .data(channel.logoUrl)
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

            SubcomposeAsyncImage(
                model = imageRequest,
                contentDescription = channel.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    ShimmerPlaceholder(modifier = Modifier.fillMaxSize())
                }
            )

            val streamUrl = remember(channel) { channel.streams.firstOrNull()?.url ?: "" }
            if (isPreviewPlaying && streamUrl.isNotEmpty()) {
                VideoPlayer(
                    videoUrl = streamUrl,
                    isPlaying = true,
                    isMuted = true,
                    resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                    modifier = Modifier.fillMaxSize()
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(14.dp)
                        .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(Color(0xFFE53935), CircleShape)
                        )
                        Text(
                            text = "LIVE PREVIEW",
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                        Icon(
                            imageVector = Icons.Default.VolumeOff,
                            contentDescription = "Muted",
                            tint = Color.White,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.95f),
                                Color.Black.copy(alpha = 0.7f),
                                Color.Black.copy(alpha = 0.15f)
                            ),
                            endX = 650f
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val hasBeenWatched = watchCount > 0
                    val reputationScore = com.example.data.ChannelClassifier.getChannelReputationScore(channel.name)
                    val labelText = when {
                        hasBeenWatched -> "🔥 TRENDING • $watchCount VIEWS"
                        reputationScore >= 90 -> "🏆 CERTIFIED HIGH DEF"
                        else -> "⭐ SPOTLIGHT CHOICE"
                    }
                    val labelBgColor = when {
                        hasBeenWatched -> Color(0xFFFF5722).copy(alpha = 0.9f)
                        reputationScore >= 90 -> Color(0xFFD4AF37).copy(alpha = 0.9f)
                        else -> accentColor.copy(alpha = 0.9f)
                    }
                    val labelTextColor = when {
                        hasBeenWatched || reputationScore >= 90 -> Color.White
                        else -> Color.Black
                    }

                    Box(
                        modifier = Modifier
                            .background(labelBgColor, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = labelText,
                            color = labelTextColor,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                            .clickable { onToggleFavorite() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (channel.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (channel.isFavorite) Color(0xFFFF3F3F) else Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(
                        modifier = Modifier.weight(1f).padding(end = 12.dp)
                    ) {
                        Text(
                            text = channel.name,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = channel.description.ifBlank { "Uncompromised premium stream with direct connection." },
                            fontSize = 11.sp,
                            color = Color.LightGray.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(accentColor, CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play stream",
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun BentoCompactChannelCard(
    channel: GroupedChannel,
    categoryName: String,
    onClick: () -> Unit,
    cardBg: Color,
    accentColor: Color,
    isScrolling: Boolean = false,
    watchCount: Int = 0
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    var isLongPressed by remember { mutableStateOf(false) }

    LaunchedEffect(interactionSource) {
        var pressJob: kotlinx.coroutines.Job? = null
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is androidx.compose.foundation.interaction.PressInteraction.Press -> {
                    pressJob?.cancel()
                    pressJob = launch {
                        delay(600)
                        isLongPressed = true
                    }
                }
                is androidx.compose.foundation.interaction.PressInteraction.Release,
                is androidx.compose.foundation.interaction.PressInteraction.Cancel -> {
                    pressJob?.cancel()
                    isLongPressed = false
                }
            }
        }
    }

    val isPreviewPlaying = isHovered || isLongPressed
    val isScaleUp = isFocused || isHovered || isLongPressed
    val cardScale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.96f
            isScaleUp -> 1.04f
            else -> 1.0f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "compact_card_scale"
    )
    val cardBorderColor by animateColorAsState(
        targetValue = if (isScaleUp) accentColor else Color.White.copy(alpha = 0.05f),
        animationSpec = tween(durationMillis = 200),
        label = "compact_border_color"
    )

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, cardBorderColor),
        modifier = Modifier
            .fillMaxWidth()
            .height(145.dp)
            .graphicsLayer(scaleX = cardScale, scaleY = cardScale)
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.foundation.LocalIndication.current,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
            .testTag("bento_compact_${channel.name}")
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.0f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(brush = getCategoryGradient(categoryName), alpha = 0.12f)
                )

                val context = LocalContext.current
                val imageRequest = remember(channel.logoUrl, isScrolling) {
                    ImageRequest.Builder(context)
                        .data(channel.logoUrl)
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

                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(64.dp)
                        .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(16.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    SubcomposeAsyncImage(
                        model = imageRequest,
                        contentDescription = channel.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                        loading = {
                            ShimmerPlaceholder(modifier = Modifier.fillMaxSize())
                        }
                    )
                }

                val streamUrl = remember(channel) { channel.streams.firstOrNull()?.url ?: "" }
                if (isPreviewPlaying && streamUrl.isNotEmpty()) {
                    VideoPlayer(
                        videoUrl = streamUrl,
                        isPlaying = true,
                        isMuted = true,
                        resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                        modifier = Modifier.fillMaxSize()
                    )

                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(6.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .background(Color(0xFFE53935), CircleShape)
                            )
                            Icon(
                                imageVector = Icons.Default.VolumeOff,
                                contentDescription = "Muted Preview",
                                tint = Color.White,
                                modifier = Modifier.size(9.dp)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(6.dp)
                        .background(Color(0xFFE53935), CircleShape)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.25f))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = channel.name,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (watchCount > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier
                                .background(Color(0xFFFF5722).copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Trending",
                                tint = Color(0xFFFF5722),
                                modifier = Modifier.size(9.dp)
                            )
                            Text(
                                text = "$watchCount",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFFFF5722)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BentoViewAllCard(
    categoryName: String,
    count: Int,
    onClick: () -> Unit,
    cardBg: Color,
    accentColor: Color
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val isScaleUp = isFocused || isHovered
    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.96f
            isScaleUp -> 1.04f
            else -> 1.0f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "bento_view_all_scale"
    )
    
    val arrowTranslation by animateFloatAsState(
        targetValue = if (isScaleUp) 6f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "view_all_arrow_anim"
    )

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg.copy(alpha = 0.4f)),
        border = BorderStroke(1.dp, if (isScaleUp) accentColor.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.08f)),
        modifier = Modifier
            .fillMaxWidth()
            .height(145.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.foundation.LocalIndication.current,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(accentColor.copy(alpha = 0.12f), CircleShape)
                    .border(1.dp, accentColor.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier
                        .size(16.dp)
                        .graphicsLayer(
                            rotationZ = 180f,
                            translationX = arrowTranslation
                        )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "EXPLORE FULL",
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center
            )

            Text(
                text = "+${count - 4} CHANNELS",
                fontSize = 10.sp,
                color = accentColor,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(2.dp))
            
            Text(
                text = "Tap to unlock archive",
                fontSize = 9.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun PulsatingCardIndicator(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_card")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_card_alpha"
    )

    Box(
        modifier = modifier
            .size(6.dp)
            .graphicsLayer { this.alpha = alpha }
            .background(Color(0xFFE53935), RoundedCornerShape(50))
    )
}

@Composable
fun StaticCardIndicator(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(6.dp)
            .background(Color(0xFFE53935), RoundedCornerShape(50))
    )
}

fun getCategoryIcon(categoryName: String): ImageVector {
    val name = categoryName.lowercase().trim()
    return when {
        name.contains("live sports") || name.contains("live event") || name.contains("event") -> Icons.Default.PlayArrow
        name.contains("sports networks") || name.contains("sport") || name.contains("cricket") || name.contains("football") -> Icons.Default.Star
        name.contains("premium movies") || name.contains("movie") || name.contains("drama") || name.contains("cinema") -> Icons.Default.PlayArrow
        name.contains("news") -> Icons.Default.Info
        name.contains("kid") || name.contains("cartoon") || name.contains("animation") || name.contains("child") -> Icons.Default.Face
        name.contains("music") -> Icons.Default.Favorite
        name.contains("classic") || name.contains("retro") -> Icons.Default.Refresh
        name.contains("crime") || name.contains("investig") -> Icons.Default.Search
        name.contains("lifestyle") || name.contains("cuis") -> Icons.Default.Favorite
        name.contains("religious") || name.contains("islam") || name.contains("prayer") -> Icons.Default.CheckCircle
        name.contains("bangla") || name.contains("local") -> Icons.Default.Home
        name.contains("science") || name.contains("document") || name.contains("info") -> Icons.Default.Search
        name.contains("weather") -> Icons.Default.Refresh
        else -> Icons.Default.PlayArrow
    }
}

fun getCategoryGradient(categoryName: String): androidx.compose.ui.graphics.Brush {
    val name = categoryName.lowercase().trim()
    val colors = when {
        name.contains("live sports") || name.contains("live event") || name.contains("event") -> listOf(Color(0xFF8B0000), Color(0xFFFF1744))
        name.contains("sports networks") || name.contains("sport") || name.contains("cricket") || name.contains("football") -> listOf(Color(0xFF0F5A26), Color(0xFF1DB954))
        name.contains("premium movies") || name.contains("movie") || name.contains("drama") || name.contains("cinema") -> listOf(Color(0xFF0D47A1), Color(0xFF1976D2))
        name.contains("news") -> listOf(Color(0xFF37474F), Color(0xFF546E7A))
        name.contains("entertainment") || name.contains("show") -> listOf(Color(0xFF4B0082), Color(0xFF8A2BE2))
        name.contains("kid") || name.contains("cartoon") || name.contains("animation") || name.contains("child") -> listOf(Color(0xFFD84B16), Color(0xFFFF9800))
        name.contains("music") -> listOf(Color(0xFF880E4F), Color(0xFFE91E63))
        name.contains("classic") || name.contains("retro") -> listOf(Color(0xFFE65100), Color(0xFFFFB300))
        name.contains("crime") || name.contains("investig") -> listOf(Color(0xFF212121), Color(0xFF424242))
        name.contains("lifestyle") || name.contains("cuis") -> listOf(Color(0xFFD81B60), Color(0xFFF48FB1))
        name.contains("religious") || name.contains("islam") || name.contains("prayer") -> listOf(Color(0xFF004D40), Color(0xFF009688))
        name.contains("bangla") || name.contains("local") -> listOf(Color(0xFF3E2723), Color(0xFF8D6E63))
        name.contains("science") || name.contains("document") || name.contains("info") -> listOf(Color(0xFF006064), Color(0xFF00ACC1))
        else -> listOf(Color(0xFF263238), Color(0xFF455A64))
    }
    return androidx.compose.ui.graphics.Brush.verticalGradient(colors)
}

@Composable
fun NetworkConnectionIndicator(isOnline: Boolean, modifier: Modifier = Modifier) {
    val color = if (isOnline) Color(0xFF4CAF50) else Color(0xFFE53935)
    val text = if (isOnline) "Online" else "Offline"
    
    Row(
        modifier = modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
            .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
fun ProfileAvatarWithStatus(
    isOnline: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed = isFocused || isHovered
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 1.05f else 1.00f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "avatarScale"
    )

    Box(
        modifier = modifier
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.foundation.LocalIndication.current,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
            .testTag("premium_profile_avatar")
    ) {
        // Main Avatar Circle
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFBB86FC),
                            Color(0xFF3700B3)
                        )
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "SL",
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                letterSpacing = 0.5.sp
            )
        }

        // Active indicator dot like Messenger
        val statusColor = if (isOnline) Color(0xFF4CAF50) else Color(0xFF757575)
        Box(
            modifier = Modifier
                .size(13.dp)
                .align(Alignment.BottomEnd)
                .background(Color(0xFF131215), CircleShape) // Match darkBg
                .padding(2.dp)
                .background(statusColor, CircleShape)
        )
    }
}

@Composable
fun OfflineRecordingsScreen(
    viewModel: ChannelViewModel,
    innerPadding: PaddingValues,
    onCardClick: (ChannelEntity) -> Unit,
    cardBg: Color,
    accentColor: Color,
    spec: ResponsiveGridSpec
) {
    val recordings by viewModel.recordings.collectAsStateWithLifecycle()

    if (recordings.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.List,
                    contentDescription = "No Recordings",
                    tint = Color.Gray.copy(alpha = 0.5f),
                    modifier = Modifier.size(80.dp)
                )
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "Your Offline Vault is Empty",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "While playing any live broadcast stream, tap the Record button in the controls HUD to capture the show securely for offline viewing.",
                    fontSize = 13.sp,
                    color = Color.Gray,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(0.85f)
                )
            }
        }
    } else {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(spec.columns),
                contentPadding = PaddingValues(
                    start = spec.margin,
                    end = spec.margin,
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    bottom = innerPadding.calculateBottomPadding() + 24.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(spec.gutter),
                verticalArrangement = Arrangement.spacedBy(spec.gutter),
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (spec.maxContentWidth != androidx.compose.ui.unit.Dp.Unspecified) {
                            Modifier.widthIn(max = spec.maxContentWidth)
                        } else {
                            Modifier
                        }
                    )
            ) {
                items(items = recordings, key = { it.id }, contentType = { "recording" }) { recording ->
                    RecordingCard(
                        recording = recording,
                        onPlay = {
                            val tempChannel = ChannelEntity(
                                id = -recording.id,
                                name = recording.channelName,
                                streamUrl = recording.filePath,
                                logoUrl = recording.channelLogoUrl,
                                categoryId = -1,
                                description = "Recorded on " + java.text.DateFormat.getDateTimeInstance().format(java.util.Date(recording.recordedAt)) + " • " + formatBytes(recording.fileSize)
                            )
                            onCardClick(tempChannel)
                        },
                        onDelete = {
                            viewModel.deleteRecording(recording)
                        },
                        cardBg = cardBg,
                        accentColor = accentColor
                    )
                }
            }
        }
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format("%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

fun formatDuration(ms: Long): String {
    val totalSecs = ms / 1000
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    return String.format("%02d:%02d", mins, secs)
}

@Composable
fun RecordingCard(
    recording: RecordingEntity,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    cardBg: Color,
    accentColor: Color
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .testTag("recording_card_${recording.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(95.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                // Channel Logo
                SubcomposeAsyncImage(
                    model = recording.channelLogoUrl,
                    contentDescription = recording.channelName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    loading = {
                        ShimmerPlaceholder(modifier = Modifier.fillMaxSize())
                    }
                )

                // Time duration badge in bottom right corner
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = formatDuration(recording.durationMs),
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                // Play icon overlay
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play recording",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = recording.channelName,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = formatBytes(recording.fileSize),
                fontSize = 11.sp,
                color = Color.Gray,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = android.text.format.DateUtils.getRelativeTimeSpanString(
                        recording.recordedAt,
                        System.currentTimeMillis(),
                        android.text.format.DateUtils.MINUTE_IN_MILLIS
                    ).toString(),
                    fontSize = 9.sp,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(24.dp)
                        .testTag("delete_recording_button_${recording.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete recording",
                        tint = Color(0xFFFF5252).copy(alpha = 0.85f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun FloatingBottomBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    selectedIcon: ImageVector,
    label: String,
    accentColor: Color,
    hasLiveBadge: Boolean = false,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.1f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "tabScale"
    )
    val dotAlpha by animateFloatAsState(
        targetValue = if (selected) 1.0f else 0.0f,
        animationSpec = tween(durationMillis = 200),
        label = "dotAlpha"
    )
    val pillWidth by animateDpAsState(
        targetValue = if (selected) 56.dp else 40.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "pillWidth"
    )
    val pillColor by animateColorAsState(
        targetValue = if (selected) accentColor.copy(alpha = 0.16f) else Color.Transparent,
        animationSpec = tween(durationMillis = 250),
        label = "pillColor"
    )
    val iconColor by animateColorAsState(
        targetValue = if (selected) accentColor else Color.White.copy(alpha = 0.5f),
        animationSpec = tween(durationMillis = 250),
        label = "iconColor"
    )
    
    // Live badge breathing pulse animation
    val pulseScale = if (hasLiveBadge) {
        val infiniteTransition = rememberInfiniteTransition(label = "livePulse")
        infiniteTransition.animateFloat(
            initialValue = 0.9f,
            targetValue = 1.3f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "livePulseScale"
        ).value
    } else {
        1.0f
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .height(30.dp)
                .width(pillWidth)
                .clip(RoundedCornerShape(15.dp))
                .background(pillColor)
        ) {
            Box(
                modifier = Modifier.size(22.dp)
            ) {
                Icon(
                    imageVector = if (selected) selectedIcon else icon,
                    contentDescription = label,
                    tint = iconColor,
                    modifier = Modifier.fillMaxSize()
                )
                
                if (hasLiveBadge) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .align(Alignment.TopEnd)
                            .offset(x = 3.dp, y = (-2).dp)
                            .graphicsLayer(scaleX = pulseScale, scaleY = pulseScale)
                            .background(Color(0xFF141218), CircleShape) // Outline ring
                            .padding(1.5.dp)
                            .background(Color(0xFFE53935), CircleShape) // Red active status dot
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.45f),
            letterSpacing = 0.1.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        
        // Custom expanding active dot indicator
        val dotWidth by animateDpAsState(
            targetValue = if (selected) 12.dp else 4.dp,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
            label = "dotWidth"
        )
        val dotHeight by animateDpAsState(
            targetValue = if (selected) 3.5.dp else 0.dp,
            animationSpec = tween(durationMillis = 150),
            label = "dotHeight"
        )
        Box(
            modifier = Modifier
                .height(dotHeight)
                .width(dotWidth)
                .graphicsLayer(alpha = dotAlpha)
                .background(accentColor, RoundedCornerShape(1.5.dp))
        )
    }
}

@Composable
fun SettingsOverlayContent(
    viewModel: ChannelViewModel,
    innerPadding: PaddingValues,
    cardBg: Color,
    accentColor: Color,
    onPurpleColor: Color,
    isCheckingStreams: Boolean,
    streamCheckingProgress: Float,
    streamCheckingStatus: String?,
    filterBrokenChannels: Boolean,
    autoSyncOnLaunch: Boolean,
    lastSyncTime: Long,
    syncStatusMessage: String?,
    isLoading: Boolean,
    lowLatencyMode: Boolean,
    cloudGistUrl: String,
    isPublicMode: Boolean,
    spec: ResponsiveGridSpec
) {
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = innerPadding.calculateTopPadding(), bottom = innerPadding.calculateBottomPadding()),
        contentAlignment = Alignment.TopCenter
    ) {
        val widthModifier = if (spec.maxContentWidth != androidx.compose.ui.unit.Dp.Unspecified) {
            Modifier.widthIn(max = spec.maxContentWidth)
        } else {
            Modifier.fillMaxWidth()
        }
        Column(
            modifier = widthModifier
                .padding(horizontal = spec.margin)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(spec.gutter)
        ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Section 1: Live TV Channel Synchronization (Public-facing, completely secure/hidden URL)
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
                        contentDescription = "Source Configuration", 
                        tint = accentColor, 
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Live TV Channel Guide", 
                        fontSize = 18.sp, 
                        fontWeight = FontWeight.Bold, 
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Ensure you have the latest available live TV categories and streams. Updates are securely retrieved and locally cached.", 
                    fontSize = 12.sp, 
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Secured Endpoint Information
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.2f))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Secured",
                        tint = accentColor.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Channel Source Feed",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray
                        )
                        Text(
                            text = "Secured Official Provider Endpoint",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Interactive Buttons with feedback
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val syncBtnInteraction = remember { MutableInteractionSource() }
                    val syncBtnFocused by syncBtnInteraction.collectIsFocusedAsState()
                    val syncBtnHovered by syncBtnInteraction.collectIsHoveredAsState()
                    val syncBtnPressed = syncBtnFocused || syncBtnHovered
                    val syncBtnScale by animateFloatAsState(
                        targetValue = if (syncBtnPressed) 1.03f else 1.00f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                        label = "syncBtnScale"
                    )

                    Button(
                        onClick = { 
                            viewModel.syncWithCloudGist(force = true)
                        },
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
                                Icon(imageVector = Icons.Default.Check, contentDescription = "Sync Source", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Synchronize Channel Guide Now", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                Spacer(modifier = Modifier.height(12.dp))

                // Auto-sync Toggle Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            viewModel.updateAutoSyncSetting(!autoSyncOnLaunch)
                        }
                        .background(Color.White.copy(alpha = 0.02f))
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto Sync on Launch", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Automatically synchronize channel configurations on app startup", fontSize = 11.sp, color = Color.Gray)
                    }
                    Switch(
                        checked = autoSyncOnLaunch,
                        onCheckedChange = { 
                            viewModel.updateAutoSyncSetting(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = onPurpleColor,
                            checkedTrackColor = accentColor,
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.White.copy(alpha = 0.08f)
                        ),
                        modifier = Modifier.testTag("settings_auto_sync_switch")
                    )
                }
            }
        }

        // Synchronization Status Logs & Feedback Card
        if (syncStatusMessage != null || lastSyncTime > 0) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f)),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    if (syncStatusMessage?.contains("failed", ignoreCase = true) == true) Color(0xFFFF8A80) else Color(0xFF81C784), 
                                    CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Cloud Sync Diagnostic Logs",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.LightGray,
                            letterSpacing = 1.sp
                        )
                    }
                    if (syncStatusMessage != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = syncStatusMessage ?: "",
                            fontSize = 13.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (lastSyncTime > 0) {
                        Spacer(modifier = Modifier.height(6.dp))
                        val sdf = remember { java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()) }
                        val formattedDate = remember(lastSyncTime) { sdf.format(java.util.Date(lastSyncTime)) }
                        Text(
                            text = "Last synchronized at: $formattedDate (UTC)",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
        }

        // Section 2: Stream Quality Assurance & Filters
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
                        contentDescription = "Quality Assurance", 
                        tint = Color(0xFF81C784), 
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Stream Quality and Link Validation", 
                        fontSize = 18.sp, 
                        fontWeight = FontWeight.Bold, 
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Scan existing streams dynamically to discover and filter out offline or non-functional streams automatically.", 
                    fontSize = 12.sp, 
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Row click mapped switch toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            viewModel.updateFilterBrokenSetting(!filterBrokenChannels)
                        }
                        .background(Color.White.copy(alpha = 0.02f))
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Exclude Offline Feeds", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Strictly show tested working links in your guide lists", fontSize = 11.sp, color = Color.Gray)
                    }
                    Switch(
                        checked = filterBrokenChannels,
                        onCheckedChange = { 
                            viewModel.updateFilterBrokenSetting(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = onPurpleColor,
                            checkedTrackColor = accentColor,
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.White.copy(alpha = 0.08f)
                        ),
                        modifier = Modifier.testTag("filter_broken_switch")
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                if (isCheckingStreams) {
                    Column(
                        modifier = Modifier.fillMaxWidth(), 
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = { streamCheckingProgress },
                            color = Color(0xFF81C784),
                            trackColor = Color.White.copy(alpha = 0.08f),
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = streamCheckingStatus ?: "Validating streams...",
                                fontSize = 12.sp,
                                color = Color.LightGray,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                modifier = Modifier.weight(1f)
                            )
                            val percent = (streamCheckingProgress * 100).toInt()
                            Text(
                                text = "$percent%",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF81C784)
                            )
                        }
                    }
                } else {
                    val validateBtnInteraction = remember { MutableInteractionSource() }
                    val validateBtnFocused by validateBtnInteraction.collectIsFocusedAsState()
                    val validateBtnHovered by validateBtnInteraction.collectIsHoveredAsState()
                    val validateBtnPressed = validateBtnFocused || validateBtnHovered
                    val validateBtnScale by animateFloatAsState(
                        targetValue = if (validateBtnPressed) 1.02f else 1.00f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                        label = "validateBtnScale"
                    )

                    Button(
                        onClick = { viewModel.verifyAllChannels() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2E7D32), 
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .graphicsLayer(scaleX = validateBtnScale, scaleY = validateBtnScale)
                            .focusable(interactionSource = validateBtnInteraction)
                            .testTag("verify_streams_button")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Check, 
                                contentDescription = "Verify All", 
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Scan & Validate All Channels Now", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Section 3: Player Settings (Low Latency Mode)
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Settings, 
                        contentDescription = "Player Preferences", 
                        tint = accentColor, 
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Player Engine Preferences", 
                        fontSize = 18.sp, 
                        fontWeight = FontWeight.Bold, 
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Optimize ExoPlayer settings for real-time live television stream quality and caching performance.", 
                    fontSize = 12.sp, 
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Row click mapped switch toggle for Low Latency
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            viewModel.setLowLatencyMode(!lowLatencyMode)
                        }
                        .background(Color.White.copy(alpha = 0.02f))
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Low Latency Playback", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Reduces buffer sizes to stay closer to real-time broadcasts", fontSize = 11.sp, color = Color.Gray)
                    }
                    Switch(
                        checked = lowLatencyMode,
                        onCheckedChange = { 
                            viewModel.setLowLatencyMode(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = onPurpleColor,
                            checkedTrackColor = accentColor,
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.White.copy(alpha = 0.08f)
                        ),
                        modifier = Modifier.testTag("low_latency_switch")
                    )
                }
            }
        }

        // Section 3.5: Resource Constraints (Sync over Wi-Fi Only)
        var unmeteredSyncOnly by remember { mutableStateOf(viewModel.getUnmeteredSyncOnly()) }

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Resource Constraints",
                            tint = accentColor,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Resource Constraints",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
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
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Manage network usage policies and system health limits for automated background updates.",
                    fontSize = 12.sp,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            val newValue = !unmeteredSyncOnly
                            unmeteredSyncOnly = newValue
                            viewModel.setUnmeteredSyncOnly(newValue)
                        }
                        .background(Color.White.copy(alpha = 0.02f))
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Sync over Wi-Fi Only", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Only sync and verify channels when on a Wi-Fi connection", fontSize = 11.sp, color = Color.Gray)
                    }
                    Switch(
                        checked = unmeteredSyncOnly,
                        onCheckedChange = { newValue ->
                            unmeteredSyncOnly = newValue
                            viewModel.setUnmeteredSyncOnly(newValue)
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

        // Section 4: App Diagnostics & Metadata Information
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Info, contentDescription = "Diagnostics", tint = Color.LightGray, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Application Diagnostic Details", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Engine Decoder", fontSize = 12.sp, color = Color.Gray)
                    Text("ExoPlayer 2.19 (Live TV Native Core)", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Medium)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Build Verification", fontSize = 12.sp, color = Color.Gray)
                    Text("Google Play Ready (M3 Adaptive UI)", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Medium)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Active Client Node Time", fontSize = 12.sp, color = Color.Gray)
                    Text("2026-07-03 02:05:43 UTC", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Medium)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Authorized Operator", fontSize = 12.sp, color = Color.Gray)
                    Text("stephanlegarza710", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Medium)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
    }
}

// =========================================================================
// ==================== Live Events Layout Components ======================
// =========================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveEventsScreen(
    viewModel: ChannelViewModel,
    innerPadding: PaddingValues,
    cardBg: Color,
    accentColor: Color,
    eventsListState: LazyListState,
    onPlayFeed: (com.example.data.GroupedEvent, com.example.data.EventFeed) -> Unit,
    onNavigateToPlayer: () -> Unit,
    spec: ResponsiveGridSpec
) {
    val liveEvents by viewModel.liveEvents.collectAsStateWithLifecycle()
    val isEventsLoading by viewModel.isEventsLoading.collectAsStateWithLifecycle()
    val eventsError by viewModel.eventsError.collectAsStateWithLifecycle()
    
    val eventsScrollScope = rememberCoroutineScope()
    val scrollToTopTrigger by viewModel.eventsScrollToTopTrigger.collectAsStateWithLifecycle()
    
    LaunchedEffect(scrollToTopTrigger) {
        if (scrollToTopTrigger > 0) {
            eventsListState.animateScrollToItem(0)
        }
    }
    
    var selectedSportFilter by remember { mutableStateOf("All") }
    var selectedEventForSheet by remember { mutableStateOf<com.example.data.GroupedEvent?>(null) }
    
    val context = LocalContext.current
    val interestedEvents by viewModel.interestedLiveEvents.collectAsStateWithLifecycle()
    var selectedEventForReminder by remember { mutableStateOf<com.example.data.GroupedEvent?>(null) }
    var permissionEventToSchedule by remember { mutableStateOf<com.example.data.GroupedEvent?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                permissionEventToSchedule?.let {
                    selectedEventForReminder = it
                }
            } else {
                Toast.makeText(context, "Notifications permission is required for event reminders.", Toast.LENGTH_LONG).show()
            }
            permissionEventToSchedule = null
        }
    )
    
    // Extract unique categories dynamically from events
    val sportCategories = remember(liveEvents) {
        val cats = liveEvents.map { it.sportCategory }.distinct()
        listOf("All") + cats
    }
    
    val filteredEvents = remember(liveEvents, selectedSportFilter) {
        if (selectedSportFilter == "All") {
            liveEvents
        } else {
            liveEvents.filter { it.sportCategory == selectedSportFilter }
        }
    }
    
    PullToRefreshBox(
        isRefreshing = isEventsLoading,
        onRefresh = { viewModel.fetchLiveEvents(forceRefresh = true) },
        modifier = Modifier
            .fillMaxSize()
            .padding(top = innerPadding.calculateTopPadding(), bottom = innerPadding.calculateBottomPadding())
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            val widthModifier = if (spec.maxContentWidth != androidx.compose.ui.unit.Dp.Unspecified) {
                Modifier.widthIn(max = spec.maxContentWidth)
            } else {
                Modifier.fillMaxSize()
            }
            Column(modifier = widthModifier.fillMaxSize()) {
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Premium Glassmorphic Sport Category Chips
            if (sportCategories.size > 1) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = spec.margin, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(sportCategories) { category ->
                        val isSelected = selectedSportFilter == category
                        val emoji = getEmojiForSport(category)
                        
                        val bgBrush = if (isSelected) {
                            Brush.horizontalGradient(
                                colors = listOf(accentColor, accentColor.copy(alpha = 0.85f))
                            )
                        } else {
                            Brush.horizontalGradient(
                                colors = listOf(Color.White.copy(alpha = 0.04f), Color.White.copy(alpha = 0.02f))
                            )
                        }
                        
                        val borderBrush = if (isSelected) {
                            Brush.horizontalGradient(
                                colors = listOf(Color.White.copy(alpha = 0.2f), Color.Transparent)
                            )
                        } else {
                            Brush.horizontalGradient(
                                colors = listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.01f))
                            )
                        }
                        
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(bgBrush)
                                .border(1.dp, borderBrush, CircleShape)
                                .clickable { selectedSportFilter = category }
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (emoji.isNotEmpty()) {
                                    Text(text = emoji, fontSize = 13.sp)
                                }
                                Text(
                                    text = category,
                                    color = if (isSelected) Color.Black else Color.White.copy(alpha = 0.85f),
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    letterSpacing = 0.2.sp
                                )
                            }
                        }
                    }
                }
            }
            
            if (isEventsLoading && liveEvents.isEmpty()) {
                // Shimmer Loader for Events
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = spec.margin, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(spec.gutter),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(6) {
                        EventCardSkeleton(cardBg = cardBg)
                    }
                }
            } else if (eventsError != null && liveEvents.isEmpty()) {
                // High-End Error State
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Error icon",
                            tint = Color.Red.copy(alpha = 0.6f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = eventsError ?: "Unknown Error",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { viewModel.fetchLiveEvents(forceRefresh = true) },
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                        ) {
                            Text("Retry Sync", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else if (filteredEvents.isEmpty()) {
                PremiumEmptyState(
                    selectedSportFilter = selectedSportFilter,
                    accentColor = accentColor,
                    onRefresh = { viewModel.fetchLiveEvents(forceRefresh = true) }
                )
            } else {
                // Main Events List
                LazyColumn(
                    state = eventsListState,
                    contentPadding = PaddingValues(start = spec.margin, end = spec.margin, bottom = 24.dp, top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(spec.gutter),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredEvents, key = { it.id }) { event ->
                        val isInterested = interestedEvents.any { it.id == event.id }
                        LiveEventCard(
                            event = event,
                            cardBg = cardBg,
                            accentColor = accentColor,
                            isInterested = isInterested,
                            onToggleInterest = {
                                if (isInterested) {
                                    viewModel.cancelEventReminder(event.id)
                                    Toast.makeText(context, "Reminder cancelled", Toast.LENGTH_SHORT).show()
                                } else {
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                        val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                                            context,
                                            android.Manifest.permission.POST_NOTIFICATIONS
                                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                        if (hasPermission) {
                                            selectedEventForReminder = event
                                        } else {
                                            permissionEventToSchedule = event
                                            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                    } else {
                                        selectedEventForReminder = event
                                    }
                                }
                            },
                            onPlayDirect = { feed ->
                                onPlayFeed(event, feed)
                                onNavigateToPlayer()
                            },
                            onSelectFeed = { 
                                selectedEventForSheet = event
                            }
                        )
                    }
                }
            }
        }
        }
    }
    
    // Bottom Sheet for Multiple Feed Selection
    selectedEventForSheet?.let { event ->
        FeedSelectionBottomSheet(
            event = event,
            accentColor = accentColor,
            cardBg = cardBg,
            onDismiss = { selectedEventForSheet = null },
            onFeedSelected = { feed ->
                onPlayFeed(event, feed)
                selectedEventForSheet = null
                onNavigateToPlayer()
            }
        )
    }

    // Dialog for Set Event Reminder Timing
    selectedEventForReminder?.let { event ->
        ReminderTimingDialog(
            event = event,
            onDismiss = { selectedEventForReminder = null },
            onSchedule = { delayMillis ->
                viewModel.scheduleEventReminder(event, delayMillis)
                Toast.makeText(context, "Reminder set!", Toast.LENGTH_SHORT).show()
                selectedEventForReminder = null
            }
        )
    }
}

@Composable
fun ReminderTimingDialog(
    event: com.example.data.GroupedEvent,
    onDismiss: () -> Unit,
    onSchedule: (Long) -> Unit
) {
    var selectedOption by remember { mutableStateOf(5 * 60 * 1000L) } // Default 5 mins

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = null,
                    tint = Color(0xFFD0BCFF),
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "Set Event Reminder",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Choose when you would like to receive an alert before '${event.title}' starts:",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    lineHeight = 18.sp
                )

                val options = listOf(
                    Pair("Now (Instant Test Notification)", 1000L),
                    Pair("5 minutes before starting", 5 * 60 * 1000L),
                    Pair("15 minutes before starting", 15 * 60 * 1000L),
                    Pair("30 minutes before starting", 30 * 60 * 1000L),
                    Pair("1 hour before starting", 60 * 60 * 1000L)
                )

                options.forEach { (label, delay) ->
                    val isSelected = selectedOption == delay
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) Color.White.copy(alpha = 0.06f) else Color.Transparent
                            )
                            .clickable { selectedOption = delay }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { selectedOption = delay },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Color(0xFFD0BCFF),
                                unselectedColor = Color.White.copy(alpha = 0.4f)
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = label,
                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.8f),
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSchedule(selectedOption) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFD0BCFF),
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("SET ALARM", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            }
        },
        containerColor = Color(0xFF1E1E1E),
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
fun LivePulseIndicator(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "livePulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .graphicsLayer(scaleX = scale, scaleY = scale)
                .background(color.copy(alpha = 0.4f), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(6.dp)
                .graphicsLayer(alpha = alpha)
                .background(color, CircleShape)
        )
    }
}

fun parseMatchTitle(title: String): Pair<String, String> {
    val separators = listOf(" vs ", " vs. ", " VS ", " VS. ", " - ", " @ ", " at ", " AT ")
    for (sep in separators) {
        if (title.contains(sep)) {
            val parts = title.split(sep, limit = 2)
            if (parts.size == 2 && parts[0].trim().isNotEmpty() && parts[1].trim().isNotEmpty()) {
                return Pair(parts[0].trim(), parts[1].trim())
            }
        }
    }
    return Pair(title, "")
}

@Composable
fun MatchupVisualizer(
    title: String,
    logoUrl: String,
    accentColor: Color
) {
    val (team1, team2) = remember(title) { parseMatchTitle(title) }
    
    if (team2.isNotEmpty()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Team 1
            Column(
                modifier = Modifier.weight(1.2f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.02f))
                            ),
                            shape = CircleShape
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = team1.take(1).uppercase(),
                        color = accentColor,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = team1,
                    color = Color.White.copy(alpha = 0.95f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            // VS Badge
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(0.6f)
            ) {
                Box(
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(10.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "VS",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                }
            }
            
            // Team 2
            Column(
                modifier = Modifier.weight(1.2f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.02f))
                            ),
                            shape = CircleShape
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = team2.take(1).uppercase(),
                        color = accentColor,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = team2,
                    color = Color.White.copy(alpha = 0.95f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp)
                .background(Color.White.copy(alpha = 0.02f), RoundedCornerShape(16.dp))
                .border(1.dp, Color.White.copy(alpha = 0.04f), RoundedCornerShape(16.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SubcomposeAsyncImage(
                model = logoUrl,
                contentDescription = null,
                loading = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White.copy(alpha = 0.04f))
                    )
                },
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.03f))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            )
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    lineHeight = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Special Live Event Broadcast",
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.4f),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

fun getEventStartTime(event: com.example.data.GroupedEvent): Long {
    val regex = """\b(\d{1,2})[.:](\d{2})\b""".toRegex()
    val matchResult = regex.find(event.title)
    if (matchResult != null) {
        try {
            val hr = matchResult.groupValues[1].toInt()
            val min = matchResult.groupValues[2].toInt()
            val cal = java.util.Calendar.getInstance()
            cal.set(java.util.Calendar.HOUR_OF_DAY, hr)
            cal.set(java.util.Calendar.MINUTE, min)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            if (cal.timeInMillis < System.currentTimeMillis()) {
                cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
            return cal.timeInMillis
        } catch (e: Exception) {
            // fallback
        }
    }
    val hash = Math.abs(event.id.hashCode())
    val cal = java.util.Calendar.getInstance()
    if (hash % 3 == 0) {
        cal.add(java.util.Calendar.MINUTE, -20)
    } else {
        val minsFuture = (hash % 45) + 5
        cal.add(java.util.Calendar.MINUTE, minsFuture)
    }
    return cal.timeInMillis
}



@Composable
fun EventCountdownBadge(
    event: com.example.data.GroupedEvent
) {
    val startTime = remember(event.id) { getEventStartTime(event) }
    var remainingMillis by remember(startTime) { mutableStateOf(startTime - System.currentTimeMillis()) }

    LaunchedEffect(startTime) {
        while (remainingMillis > 0) {
            kotlinx.coroutines.delay(1000L)
            remainingMillis = startTime - System.currentTimeMillis()
        }
    }

    if (remainingMillis > 0) {
        val totalSecs = remainingMillis / 1000
        val hours = totalSecs / 3600
        val minutes = (totalSecs % 3600) / 60
        val seconds = totalSecs % 60
        val countdownText = if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .background(Color(0xFFE28743).copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                .border(1.dp, Color(0xFFE28743).copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .background(Color(0xFFE28743), CircleShape)
            )
            Text(
                text = "STARTS IN $countdownText",
                color = Color(0xFFE28743),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .background(Color(0xFFE53935).copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                .border(1.dp, Color(0xFFE53935).copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            LivePulseIndicator(color = Color(0xFFE53935))
            Text(
                text = "LIVE",
                color = Color(0xFFFF8A80),
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
fun LiveEventCard(
    event: com.example.data.GroupedEvent,
    cardBg: Color,
    accentColor: Color,
    isInterested: Boolean,
    onToggleInterest: () -> Unit,
    onPlayDirect: (com.example.data.EventFeed) -> Unit,
    onSelectFeed: () -> Unit
) {
    val cardBrush = Brush.verticalGradient(
        colors = listOf(
            cardBg,
            cardBg.copy(alpha = 0.85f)
        )
    )

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .background(cardBrush, RoundedCornerShape(24.dp))
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.08f),
                        Color.White.copy(alpha = 0.01f)
                    )
                ),
                shape = RoundedCornerShape(24.dp)
            )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Category & Live Badge Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category Chip with Emoji
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.05f), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    val emoji = getEmojiForSport(event.sportCategory)
                    if (emoji.isNotEmpty()) {
                        Text(text = emoji, fontSize = 11.sp)
                    }
                    Text(
                        text = event.sportCategory.uppercase(),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        color = accentColor,
                        letterSpacing = 1.sp
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    EventCountdownBadge(event = event)
                    
                    // Sleek circular notification bell
                    IconButton(
                        onClick = onToggleInterest,
                        modifier = Modifier
                            .size(32.dp)
                            .background(
                                if (isInterested) accentColor.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.04f),
                                CircleShape
                            )
                            .border(
                                1.dp,
                                if (isInterested) accentColor.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.08f),
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = if (isInterested) Icons.Default.NotificationsActive else Icons.Default.Notifications,
                            contentDescription = "Toggle Reminder",
                            tint = if (isInterested) accentColor else Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Beautiful Matchup
            MatchupVisualizer(
                title = event.title,
                logoUrl = event.logoUrl,
                accentColor = accentColor
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Display Feeds Pills Indicator
            if (event.feeds.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "Feeds:",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.4f),
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        event.feeds.forEach { feed ->
                            val isMultiLingual = feed.language.isNotEmpty()
                            val displayLang = if (isMultiLingual) " • ${feed.language.uppercase()}" else ""
                            Box(
                                modifier = Modifier
                                    .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(8.dp))
                                    .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "${feed.provider}$displayLang",
                                    fontSize = 9.sp,
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.2.sp
                                )
                            }
                        }
                    }
                }
            }

            // Watch/Choose Buttons
            if (event.feeds.size == 1) {
                val feed = event.feeds.first()
                Button(
                    onClick = { onPlayDirect(feed) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        contentColor = Color(0xFF1D0E3D)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize(),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color(0xFF1D0E3D),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "WATCH BROADCAST",
                        color = Color(0xFF1D0E3D),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.8.sp
                    )
                }
            } else {
                Button(
                    onClick = onSelectFeed,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.05f),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                    modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize(),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Tune Feeds",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "CHOOSE FEED (${event.feeds.size} SOURCES)",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.8.sp
                    )
                }
            }
        }
    }
}

@Composable
fun FeedSelectionBottomSheet(
    event: com.example.data.GroupedEvent,
    accentColor: Color,
    cardBg: Color,
    onDismiss: () -> Unit,
    onFeedSelected: (com.example.data.EventFeed) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.82f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp)
                    .clickable(enabled = false) {}
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF121214)),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(5.dp)
                            .background(Color.White.copy(alpha = 0.15f), CircleShape)
                    )
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    Text(
                        text = "Select Broadcast Feed",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = (-0.5).sp
                    )
                    
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    Text(
                        text = event.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = accentColor,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        event.feeds.forEach { feed ->
                            val interactionSource = remember { MutableInteractionSource() }
                            val isFocused by interactionSource.collectIsFocusedAsState()
                            val isHovered by interactionSource.collectIsHoveredAsState()
                            val isPressed = isFocused || isHovered
                            
                            val bgScale by animateFloatAsState(if (isPressed) 1.02f else 1.0f, label = "feedScale")
                            
                            val itemBgBrush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.03f),
                                    Color.White.copy(alpha = 0.01f)
                                )
                            )
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer(scaleX = bgScale, scaleY = bgScale)
                                    .background(itemBgBrush, RoundedCornerShape(18.dp))
                                    .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(18.dp))
                                    .clickable(
                                        interactionSource = interactionSource,
                                        indication = androidx.compose.foundation.LocalIndication.current,
                                        onClick = { onFeedSelected(feed) }
                                    )
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(accentColor.copy(alpha = 0.1f), CircleShape)
                                            .border(1.dp, accentColor.copy(alpha = 0.2f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Language,
                                            contentDescription = null,
                                            tint = accentColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    
                                    Column {
                                        Text(
                                            text = feed.provider.uppercase(),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color.White,
                                            letterSpacing = 0.5.sp
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Broadcast Language: ${feed.language}",
                                            fontSize = 11.sp,
                                            color = Color.White.copy(alpha = 0.5f),
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                                
                                Icon(
                                    imageVector = Icons.Default.PlayCircle,
                                    contentDescription = "Play Feed",
                                    tint = accentColor,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize()
                    ) {
                        Text(
                            text = "CLOSE STREAM LIST",
                            color = Color.White.copy(alpha = 0.5f),
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EventCardSkeleton(cardBg: Color) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.04f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .height(14.dp)
                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(4.dp))
                )
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(14.dp)
                        .background(Color.White.copy(alpha = 0.05f), CircleShape)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(14.dp))
                )
                Column(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(18.dp)
                            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.4f)
                            .height(12.dp)
                            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(4.dp))
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(14.dp))
            )
        }
    }
}

fun getEmojiForSport(category: String): String {
    val catLower = category.lowercase()
    return when {
        catLower.contains("foot") || catLower.contains("fútbol") || catLower.contains("soccer") || catLower.contains("copa") || catLower.contains("world cup") || catLower.contains("fifa") || catLower.contains("ligapro") -> "⚽"
        catLower.contains("basket") || catLower.contains("nba") -> "🏀"
        catLower.contains("tennis") || catLower.contains("wimbledon") -> "🎾"
        catLower.contains("cycle") || catLower.contains("tour") -> "🚴"
        catLower.contains("horse") -> "🏇"
        catLower.contains("racing") -> "🏎️"
        else -> "🏆"
    }
}

@Composable
fun PremiumEmptyState(
    selectedSportFilter: String,
    accentColor: Color,
    onRefresh: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .widthIn(max = 420.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.02f),
                            Color.White.copy(alpha = 0.005f)
                        )
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.08f),
                            Color.White.copy(alpha = 0.01f)
                        )
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(vertical = 40.dp, horizontal = 24.dp)
        ) {
            // High-quality custom vector Canvas illustration
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .drawBehind {
                        // Background glow
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    accentColor.copy(alpha = 0.15f),
                                    Color.Transparent
                                ),
                                center = center,
                                radius = size.minDimension / 1.5f
                            )
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(120.dp)) {
                    val w = size.width
                    val h = size.height
                    val cx = w / 2f
                    val cy = h / 2f + 15f // Shifted slightly down for balance
                    
                    // 1. Draw radar/orbital concentric background rings (subtle)
                    drawCircle(
                        color = Color.White.copy(alpha = 0.03f),
                        radius = cx * 0.9f,
                        style = Stroke(width = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.05f),
                        radius = cx * 0.65f,
                        style = Stroke(width = 1f)
                    )
                    
                    // 2. Draw satellite dish structure / tower base
                    val path = Path().apply {
                        // Tower base (triangle legs)
                        moveTo(cx - 15f, cy)
                        lineTo(cx - 25f, cy + 30f)
                        lineTo(cx + 25f, cy + 30f)
                        lineTo(cx + 15f, cy)
                        close()
                        
                        // Cross braces
                        moveTo(cx - 20f, cy + 15f)
                        lineTo(cx + 20f, cy + 15f)
                    }
                    drawPath(
                        path = path,
                        color = Color.White.copy(alpha = 0.35f),
                        style = Stroke(width = 3f, join = StrokeJoin.Round)
                    )
                    
                    // 3. Central receiver dish (arc)
                    drawArc(
                        color = accentColor.copy(alpha = 0.9f),
                        startAngle = 190f,
                        sweepAngle = 160f,
                        useCenter = false,
                        topLeft = Offset(cx - 25f, cy - 20f),
                        size = Size(50f, 30f),
                        style = Stroke(width = 4.5f, cap = StrokeCap.Round)
                    )
                    
                    // Dish stem/feed horn
                    drawLine(
                        color = accentColor.copy(alpha = 0.9f),
                        start = Offset(cx, cy - 5f),
                        end = Offset(cx, cy - 22f),
                        strokeWidth = 4f,
                        cap = StrokeCap.Round
                    )
                    
                    // Horn point (glowing dot)
                    drawCircle(
                        color = Color.White,
                        radius = 4f,
                        center = Offset(cx, cy - 22f)
                    )
                    
                    // 4. Glowing signal transmission arcs radiating upwards
                    val pulseAlphas = listOf(0.15f, 0.45f, 0.85f)
                    val pulseOffsets = listOf(20f, 38f, 56f)
                    for (i in pulseAlphas.indices) {
                        val radius = pulseOffsets[i]
                        val alpha = pulseAlphas[i]
                        drawArc(
                            color = accentColor.copy(alpha = alpha),
                            startAngle = 220f,
                            sweepAngle = 100f,
                            useCenter = false,
                            topLeft = Offset(cx - radius, cy - 22f - radius),
                            size = Size(radius * 2, radius * 2),
                            style = Stroke(width = 3.5f - (i * 0.5f), cap = StrokeCap.Round)
                        )
                    }
                    
                    // Decorative small floating network nodes/stars
                    drawCircle(
                        color = accentColor.copy(alpha = 0.5f),
                        radius = 3f,
                        center = Offset(cx - 38f, cy - 10f)
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.3f),
                        radius = 2f,
                        center = Offset(cx + 42f, cy + 5f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Text Details
            Text(
                text = if (selectedSportFilter == "All") "No Broadcasts Scheduled" else "$selectedSportFilter Schedules Clear",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = if (selectedSportFilter == "All") {
                    "Our live event radar is currently clear. Pull down on the screen to scan the server for freshly announced feeds."
                } else {
                    "There are no active matches or live streams under '$selectedSportFilter' right now. Check back soon or pull down to refresh!"
                },
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
            
            Spacer(modifier = Modifier.height(28.dp))
            
            // Interactive Refresh/Action button with feedback ripple
            Button(
                onClick = onRefresh,
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Scan Live Radar",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun BentoCategoryTabStrip(
    selectedCategoryId: Int?,
    showFavoritesOnly: Boolean,
    categories: List<CategoryEntity>,
    categoryCounts: Map<Int, Int>,
    totalActiveChannelsCount: Int,
    favoritesCount: Int,
    onSelectAll: () -> Unit,
    onSelectFavorites: () -> Unit,
    onSelectCategory: (Int) -> Unit,
    cardBg: Color,
    accentColor: Color,
    onPurpleColor: Color,
    state: LazyListState = rememberLazyListState()
) {
    LazyRow(
        state = state,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        // Tab 1: All Streams
        item {
            val isSelected = !showFavoritesOnly && selectedCategoryId == null
            BentoTabItem(
                text = "All Streams",
                count = totalActiveChannelsCount,
                isSelected = isSelected,
                onClick = onSelectAll,
                icon = Icons.Default.Home,
                accentColor = accentColor,
                onPurpleColor = onPurpleColor,
                cardBg = cardBg
            )
        }

        // Tab 2: Starred
        item {
            val isSelected = showFavoritesOnly
            BentoTabItem(
                text = "Starred",
                count = favoritesCount,
                isSelected = isSelected,
                onClick = onSelectFavorites,
                icon = Icons.Default.Favorite,
                accentColor = Color(0xFFFF5252),
                onPurpleColor = Color.White,
                cardBg = cardBg
            )
        }

        // Tab 3..N: Categories
        items(items = categories, key = { "tab_cat_${it.id}" }) { category ->
            val isSelected = !showFavoritesOnly && selectedCategoryId == category.id
            val count = categoryCounts[category.id] ?: 0
            BentoTabItem(
                text = category.name,
                count = count,
                isSelected = isSelected,
                onClick = { onSelectCategory(category.id) },
                icon = getCategoryIcon(category.name),
                accentColor = accentColor,
                onPurpleColor = onPurpleColor,
                cardBg = cardBg
            )
        }
    }
}

@Composable
fun BentoTabItem(
    text: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    accentColor: Color,
    onPurpleColor: Color,
    cardBg: Color
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val isScaled = isSelected || isFocused || isHovered

    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.95f
            isScaled -> 1.06f
            else -> 1.0f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "bento_tab_scale"
    )

    val contentColor by animateColorAsState(
        targetValue = if (isSelected) onPurpleColor else Color.White.copy(alpha = 0.9f),
        animationSpec = tween(durationMillis = 200),
        label = "bento_tab_content_color"
    )

    val containerColor by animateColorAsState(
        targetValue = if (isSelected) accentColor else Color.White.copy(alpha = 0.08f),
        animationSpec = tween(durationMillis = 200),
        label = "bento_tab_bg"
    )

    val borderColor by animateColorAsState(
        targetValue = if (isSelected) accentColor.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.15f),
        animationSpec = tween(durationMillis = 200),
        label = "bento_tab_border"
    )

    val shadowElevation by animateDpAsState(
        targetValue = if (isScaled) 12.dp else 2.dp,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "bento_tab_shadow"
    )

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.2.dp, borderColor),
        modifier = Modifier
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .shadow(
                elevation = shadowElevation,
                shape = RoundedCornerShape(20.dp),
                ambientColor = if (isScaled) accentColor.copy(alpha = 0.35f) else Color.Transparent,
                spotColor = if (isScaled) accentColor.copy(alpha = 0.35f) else Color.Transparent
            )
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.foundation.LocalIndication.current,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = contentColor,
                modifier = Modifier.size(15.dp)
            )

            Text(
                text = text,
                fontSize = 12.5.sp,
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                color = contentColor
            )

            Box(
                modifier = Modifier
                    .background(
                        if (isSelected) onPurpleColor.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.08f),
                        CircleShape
                    )
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "$count",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = if (isSelected) onPurpleColor else Color.LightGray
                )
            }
        }
    }
}

@Composable
fun CategoryFilterChip(
    selected: Boolean,
    label: String,
    icon: ImageVector,
    count: Int,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val isScaled = selected || isFocused || isHovered

    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.95f
            isScaled -> 1.06f
            else -> 1.0f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "chip_scale"
    )

    val contentColor by animateColorAsState(
        targetValue = if (selected) Color.Black else Color.White.copy(alpha = 0.9f),
        animationSpec = tween(durationMillis = 200),
        label = "chip_content_color"
    )

    val containerColor by animateColorAsState(
        targetValue = if (selected) Color(0xFFEADDFF) else Color.White.copy(alpha = 0.08f),
        animationSpec = tween(durationMillis = 200),
        label = "chip_bg"
    )

    val borderColor by animateColorAsState(
        targetValue = if (selected) Color(0xFFEADDFF).copy(alpha = 0.8f) else Color.White.copy(alpha = 0.15f),
        animationSpec = tween(durationMillis = 200),
        label = "chip_border"
    )

    val shadowElevation by animateDpAsState(
        targetValue = if (isScaled) 12.dp else 2.dp,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "chip_shadow"
    )

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.2.dp, borderColor),
        modifier = Modifier
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .shadow(
                elevation = shadowElevation,
                shape = RoundedCornerShape(20.dp),
                ambientColor = if (isScaled) Color(0xFFEADDFF).copy(alpha = 0.35f) else Color.Transparent,
                spotColor = if (isScaled) Color(0xFFEADDFF).copy(alpha = 0.35f) else Color.Transparent
            )
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.foundation.LocalIndication.current,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(15.dp)
            )

            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold,
                color = contentColor
            )

            Box(
                modifier = Modifier
                    .background(
                        if (selected) Color.Black.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.08f),
                        CircleShape
                    )
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "$count",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = if (selected) Color.Black else Color.LightGray
                )
            }
        }
    }
}

@Composable
fun PremiumHeroBanner(
    channel: com.example.data.GroupedChannel,
    categoryName: String,
    currentProgram: com.example.data.EpgProgramEntity?,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    cardBg: Color,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isScaled = isFocused || isHovered

    val scale by animateFloatAsState(
        targetValue = if (isScaled) 1.02f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "hero_scale"
    )

    val gradient = remember(categoryName) {
        getCategoryGradient(categoryName)
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.foundation.LocalIndication.current,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp) // Perfect widescreen height for cinematic showcase
                .clip(RoundedCornerShape(24.dp))
        ) {
            // Background Layer: Channel logo cropped (or category gradient)
            if (channel.logoUrl.isNotBlank()) {
                coil.compose.SubcomposeAsyncImage(
                    model = channel.logoUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            // Elegant cinematic zoom on focus or hover
                            val zoom = if (isScaled) 1.08f else 1.0f
                            scaleX = zoom
                            scaleY = zoom
                        }
                        .blur(18.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded),
                    loading = {
                        Box(modifier = Modifier.fillMaxSize().background(gradient))
                    }
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(gradient))
            }

            // Cinematic Vignette Overlay: Darkening from top/bottom and left/right
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.35f),
                                Color.Black.copy(alpha = 0.88f)
                            )
                        )
                    )
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.82f),
                                Color.Transparent
                            ),
                            endX = 900f
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(22.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Color(0xFFFFD700),
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "SPOTLIGHT RECOMMENDATION",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            letterSpacing = 1.sp
                        )
                    }

                    // Channel Name / Program Title
                    Text(
                        text = channel.name,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Current playing info
                    if (currentProgram != null) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                .padding(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Playing Now",
                                    tint = accentColor,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "LIVE NOW",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    color = accentColor,
                                    letterSpacing = 0.5.sp
                                )
                            }
                            Text(
                                text = currentProgram.title,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                              )
                              if (currentProgram.description.isNotBlank()) {
                                Text(
                                    text = currentProgram.description,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = Color.LightGray,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    } else if (channel.description.isNotBlank()) {
                        Text(
                            text = channel.description,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            color = Color.LightGray.copy(alpha = 0.9f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Text(
                            text = "Stream high-quality live broadcast of $categoryName channels directly. Crystal-clear connection.",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            color = Color.LightGray.copy(alpha = 0.9f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Buttons row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onClick,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Watch Live",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.Black
                                )
                            }
                        }

                        IconButton(
                            onClick = onToggleFavorite,
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color.White.copy(alpha = 0.15f), CircleShape)
                        ) {
                            Icon(
                                imageVector = if (channel.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (channel.isFavorite) Color(0xFFFF5252) else Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Channel Logo / Large Icon on the right
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (channel.logoUrl.isNotBlank()) {
                        coil.compose.SubcomposeAsyncImage(
                            model = channel.logoUrl,
                            contentDescription = "${channel.name} Logo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Icon(
                            imageVector = getCategoryIcon(categoryName),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            }
        }
    }
}




