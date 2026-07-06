package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.focus.onFocusChanged
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val channels by viewModel.filteredGroupedChannels.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteGroupedChannels.collectAsStateWithLifecycle()
    val selectedCategoryId by viewModel.selectedCategoryId.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
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

    var currentTab by remember { mutableStateOf(0) } // 0 = Home, 1 = Categories, 2 = Favorites, 3 = Search
    var showFavoritesOnly by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var favoritesSubTab by remember { mutableStateOf(0) } // 0 = Starred Feeds, 1 = Recorded Shows
    var titleTapCount by remember { mutableStateOf(0) }
    var showAdminOverride by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    BackHandler(enabled = showSettings || currentTab != 0) {
        if (showSettings) {
            showSettings = false
        } else {
            currentTab = 0
            viewModel.setSearchQuery("")
        }
    }

    val lazyGridState = rememberLazyGridState()
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
        val work = {
            val listToCount = if (filterBrokenChannels) {
                allChannelsRaw.filter { !it.isBroken }
            } else {
                allChannelsRaw
            }
            listToCount.groupBy { it.categoryId }.mapValues { it.value.size }
        }
        if (allChannelsRaw.size > 200) {
            kotlinx.coroutines.runBlocking(Dispatchers.Default) { work() }
        } else {
            work()
        }
    }
    
    val totalActiveChannelsCount = remember(categoryCounts) { categoryCounts.values.sum() }
    
    val populatedCategories = remember(categories, categoryCounts) {
        categories.filter { (categoryCounts[it.id] ?: 0) > 0 }
    }

    val categoryRowState = rememberLazyListState()
    
    // Auto-scroll the horizontal category scroller to bring the selected category chip into focus
    LaunchedEffect(selectedCategoryId, populatedCategories) {
        if (selectedCategoryId != null && populatedCategories.isNotEmpty()) {
            val index = populatedCategories.indexOfFirst { it.id == selectedCategoryId }
            if (index != -1) {
                try {
                    // index + 1 because the "All" chip is at index 0
                    categoryRowState.animateScrollToItem(index + 1)
                } catch (e: Exception) {}
            }
        } else if (selectedCategoryId == null) {
            try {
                categoryRowState.animateScrollToItem(0)
            } catch (e: Exception) {}
        }
    }

    val processedCategories = remember(categories, categoryCounts, categorySearchQuery, categorySortMode, hideEmptyCategories) {
        val work = {
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
        if (categories.size > 200) {
            kotlinx.coroutines.runBlocking(Dispatchers.Default) { work() }
        } else {
            work()
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

    // Visual theme variables
    val darkBg = Color(0xFF131215) // Deep luxury cinematic dark background
    val cardBg = Color(0xFF1E1C22) // Sleek Material dark surface
    val accentColor = Color(0xFFD0BCFF) // Frosted theme Purple Accent
    val secondaryAccentColor = Color(0xFFEADDFF)
    val onPurpleColor = Color(0xFF381E72)

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .background(darkBg)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
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
                                onClick = { showSettings = true }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Category Scroller & Filters
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Favorites toggle chip
                        FilterChip(
                            selected = showFavoritesOnly,
                            onClick = { showFavoritesOnly = !showFavoritesOnly },
                            label = { Text("Starred (${favorites.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (showFavoritesOnly) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Favorites",
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFFF5252),
                                selectedLabelColor = Color.White,
                                selectedLeadingIconColor = Color.White,
                                containerColor = cardBg,
                                labelColor = Color(0xFFCAC4D0),
                                iconColor = Color(0xFFCAC4D0)
                            ),
                            border = null,
                            shape = CircleShape
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // Horizontal scrolling genre selection with active-centered state and channel counts
                        LazyRow(
                            state = categoryRowState,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            item {
                                val isAllSelected = !showFavoritesOnly && selectedCategoryId == null
                                FilterChip(
                                    selected = isAllSelected,
                                    onClick = {
                                        showFavoritesOnly = false
                                        viewModel.selectCategory(null)
                                    },
                                    label = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("All Streams", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .background(
                                                        if (isAllSelected) onPurpleColor.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f),
                                                        CircleShape
                                                    )
                                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "$totalActiveChannelsCount",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isAllSelected) onPurpleColor else Color.LightGray
                                                )
                                            }
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Home,
                                            contentDescription = "All",
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
                                    modifier = Modifier.testTag("category_chip_all")
                                )
                            }

                            items(items = populatedCategories, key = { it.id }) { category ->
                                val isSelected = !showFavoritesOnly && selectedCategoryId == category.id
                                val count = categoryCounts[category.id] ?: 0
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        showFavoritesOnly = false
                                        viewModel.selectCategory(category.id)
                                    },
                                    label = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(category.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .background(
                                                        if (isSelected) onPurpleColor.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f),
                                                        CircleShape
                                                    )
                                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "$count",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSelected) onPurpleColor else Color.LightGray
                                                )
                                            }
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = getCategoryIcon(category.name),
                                            contentDescription = category.name,
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
                                    modifier = Modifier.testTag("category_chip_${category.id}")
                                )
                            }
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
                                onClick = { showSettings = true }
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
                            onClick = { showSettings = true }
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
                            onClick = { showSettings = true }
                        )
                    }
                }
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .background(Color.Transparent)
                    .navigationBarsPadding()
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
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        color = Color(0xFF141218).copy(alpha = 0.92f), // Glassy ultra-dark premium background
                        shape = RoundedCornerShape(28.dp),
                        border = BorderStroke(
                            1.dp,
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.12f),
                                    Color.White.copy(alpha = 0.04f)
                                )
                            )
                        ),
                        tonalElevation = 12.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(72.dp)
                                .padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FloatingBottomBarItem(
                                selected = !showSettings && currentTab == 0,
                                onClick = { 
                                    showSettings = false
                                    currentTab = 0 
                                    viewModel.setSearchQuery("")
                                },
                                icon = Icons.Default.Home,
                                selectedIcon = Icons.Default.Home,
                                label = "Home",
                                accentColor = accentColor,
                                modifier = Modifier.testTag("nav_home")
                            )
                            FloatingBottomBarItem(
                                selected = !showSettings && currentTab == 1,
                                onClick = { 
                                    showSettings = false
                                    currentTab = 1 
                                    viewModel.setSearchQuery("")
                                },
                                icon = Icons.Default.Category,
                                selectedIcon = Icons.Default.Category,
                                label = "Categories",
                                accentColor = accentColor,
                                modifier = Modifier.testTag("nav_categories")
                            )
                            FloatingBottomBarItem(
                                selected = !showSettings && currentTab == 2,
                                onClick = { 
                                    showSettings = false
                                    currentTab = 2 
                                    viewModel.setSearchQuery("")
                                },
                                icon = Icons.Default.FavoriteBorder,
                                selectedIcon = Icons.Default.Favorite,
                                label = "Favorites",
                                accentColor = accentColor,
                                modifier = Modifier.testTag("nav_favorites")
                            )
                            FloatingBottomBarItem(
                                selected = !showSettings && currentTab == 3,
                                onClick = { 
                                    showSettings = false
                                    currentTab = 3 
                                },
                                icon = Icons.Default.Search,
                                selectedIcon = Icons.Default.Search,
                                label = "Search",
                                accentColor = accentColor,
                                modifier = Modifier.testTag("nav_search")
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
                isPublicMode = isPublicMode
            )
        } else if (currentTab == 0) {
            val listToDisplay = if (showFavoritesOnly) {
                favorites
            } else {
                channels
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
                        columns = GridCells.Adaptive(minSize = 165.dp),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 12.dp,
                            bottom = 24.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
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
                    // World-Class OTT Dynamic Layout: Grid with Featured widescreen Hero Banner (breaks monotony)
                    val featuredChannel = listToDisplay.firstOrNull()
                    val remainingChannels = if (featuredChannel != null) listToDisplay.drop(1) else listToDisplay

                    LazyVerticalGrid(
                        state = lazyGridState,
                        columns = GridCells.Adaptive(minSize = 165.dp),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 12.dp,
                            bottom = 24.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // RECENTLY WATCHED SECTION
                        if (recentlyWatched.isNotEmpty() && searchQuery.isEmpty() && !showFavoritesOnly) {
                            item(
                                span = { GridItemSpan(maxLineSpan) },
                                key = "recently_watched_row_item",
                                contentType = "recently_watched"
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
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
                                    Spacer(modifier = Modifier.height(6.dp))
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        contentPadding = PaddingValues(vertical = 4.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        items(
                                            items = recentlyWatched,
                                            key = { "recent_${it.name}" },
                                            contentType = { "recently_watched_item" }
                                        ) { groupedChannel ->
                                            Card(
                                                shape = RoundedCornerShape(16.dp),
                                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = cardBg
                                                ),
                                                modifier = Modifier
                                                    .width(140.dp)
                                                    .clickable {
                                                        onCardClick(groupedChannel)
                                                    }
                                                    .testTag("recent_channel_${groupedChannel.name}")
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .padding(8.dp)
                                                        .fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    SubcomposeAsyncImage(
                                                        model = groupedChannel.logoUrl,
                                                        contentDescription = groupedChannel.name,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier
                                                            .size(28.dp)
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(Color.White.copy(alpha = 0.1f)),
                                                        loading = {
                                                            ShimmerPlaceholder(modifier = Modifier.fillMaxSize())
                                                        }
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Column {
                                                        Text(
                                                            text = groupedChannel.name,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color.White,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Text(
                                                            text = "WATCH AGAIN",
                                                            fontSize = 8.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = accentColor
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                                }
                            }
                        }

                        // FEATURED BANNER ROW (Spans both columns)
                        if (featuredChannel != null && searchQuery.isEmpty() && !showFavoritesOnly) {
                            item(
                                span = { GridItemSpan(maxLineSpan) },
                                key = "featured_banner",
                                contentType = "hero"
                            ) {
                                val categoryName = categoryMap[featuredChannel.categoryId] ?: "General"
                                val repChannel = remember(featuredChannel) {
                                    ChannelEntity(
                                        id = featuredChannel.originalChannelIds.firstOrNull() ?: 0,
                                        name = featuredChannel.name,
                                        streamUrl = featuredChannel.streams.firstOrNull()?.url ?: "",
                                        logoUrl = featuredChannel.logoUrl,
                                        categoryId = featuredChannel.categoryId,
                                        description = featuredChannel.description,
                                        isFavorite = featuredChannel.isFavorite,
                                        isBroken = featuredChannel.isBroken
                                    )
                                }
                                HeroChannelBanner(
                                    channel = repChannel,
                                    categoryName = categoryName,
                                    onClick = { onCardClick(featuredChannel) },
                                    onToggleFavorite = { onFavoriteClick(featuredChannel) },
                                    accentColor = accentColor,
                                    isScrolling = isScrolling
                                )
                            }

                            // Section Divider
                            item(
                                span = { GridItemSpan(maxLineSpan) },
                                key = "section_divider",
                                contentType = "divider"
                            ) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "LIVE FEEDS BROADCASTING NOW",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.Gray,
                                    letterSpacing = 1.sp,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                        }

                        val (eventsToHighlight, mainGridList) = if (searchQuery.isEmpty() && !showFavoritesOnly) {
                            val selectedCategoryName = categoryMap[selectedCategoryId] ?: ""
                            if (selectedCategoryName.trim().lowercase().contains("live event")) {
                                Pair(emptyList<GroupedChannel>(), listToDisplay)
                            } else {
                                val evs = listToDisplay.filter {
                                    val catName = categoryMap[it.categoryId] ?: ""
                                    catName.trim().lowercase().contains("live event")
                                }
                                val regulars = remainingChannels.filter {
                                    val catName = categoryMap[it.categoryId] ?: ""
                                    !catName.trim().lowercase().contains("live event")
                                }
                                Pair(evs, regulars)
                            }
                        } else {
                            Pair(emptyList<GroupedChannel>(), listToDisplay)
                        }

                        // LIVE EVENTS SECTION (Elite Horizontal Slider for Live Sports & Upcoming Events)
                        if (eventsToHighlight.isNotEmpty()) {
                            item(
                                span = { GridItemSpan(maxLineSpan) },
                                key = "live_events_row_item",
                                contentType = "live_events_slider"
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 16.dp, top = 8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(Color(0xFFE53935), CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "⚡ UPCOMING & ACTIVE LIVE EVENTS",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color.White,
                                            letterSpacing = 1.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        contentPadding = PaddingValues(vertical = 4.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        items(
                                            items = eventsToHighlight,
                                            key = { "event_${it.name}" },
                                            contentType = { "live_event_card" }
                                        ) { groupedChannel ->
                                            Card(
                                                shape = RoundedCornerShape(16.dp),
                                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                                                colors = CardDefaults.cardColors(containerColor = cardBg),
                                                modifier = Modifier
                                                    .width(190.dp)
                                                    .clickable { onCardClick(groupedChannel) }
                                                    .testTag("live_event_${groupedChannel.name}")
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp)) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(85.dp)
                                                            .clip(RoundedCornerShape(10.dp))
                                                            .background(Color.Black.copy(alpha = 0.3f))
                                                    ) {
                                                        SubcomposeAsyncImage(
                                                            model = groupedChannel.logoUrl,
                                                            contentDescription = groupedChannel.name,
                                                            contentScale = ContentScale.Fit,
                                                            modifier = Modifier.fillMaxSize().padding(6.dp),
                                                            loading = {
                                                                ShimmerPlaceholder(modifier = Modifier.fillMaxSize())
                                                            }
                                                        )
                                                        Box(
                                                            modifier = Modifier
                                                                .align(Alignment.TopStart)
                                                                .padding(6.dp)
                                                                .background(Color(0xFFE53935), RoundedCornerShape(4.dp))
                                                                .padding(horizontal = 5.dp, vertical = 2.dp)
                                                        ) {
                                                            Text(
                                                                text = "LIVE",
                                                                color = Color.White,
                                                                fontSize = 7.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    Text(
                                                        text = groupedChannel.name,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White,
                                                        maxLines = 2,
                                                        minLines = 2,
                                                        overflow = TextOverflow.Ellipsis,
                                                        lineHeight = 15.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                                }
                            }
                        }

                        // GRID OF CHANNELS (Using unique stable keys & contentType to enable smooth recycle/scrolling)
                        val displayList = if (searchQuery.isNotEmpty() || showFavoritesOnly) listToDisplay else mainGridList
                        items(
                            items = displayList,
                            key = { it.name },
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

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = innerPadding.calculateTopPadding() + 8.dp,
                        bottom = innerPadding.calculateBottomPadding() + 24.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(6, contentType = { "category_skeleton" }) {
                        SkeletonCategoryCard(alpha = alpha, cardBg = cardBg)
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
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = innerPadding.calculateTopPadding() + 8.dp,
                        bottom = innerPadding.calculateBottomPadding() + 24.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
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
                                    currentTab = 0
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
        } else if (currentTab == 2) {
            // Tab 2: Favorites Screen (Starred Feeds + Offline recorded shows!)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding())
            ) {
                // Secondary horizontal sliding subtabs for Favorites
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
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
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 165.dp),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 8.dp,
                                bottom = innerPadding.calculateBottomPadding() + 24.dp
                            ),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
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
                } else {
                    OfflineRecordingsScreen(
                        viewModel = viewModel,
                        innerPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding()),
                        onCardClick = onRecordingCardClick,
                        cardBg = cardBg,
                        accentColor = accentColor
                    )
                }
            }
        } else {
            // Tab 3: Dedicated Live Search Screen
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding())
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
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .onFocusChanged { isSearchFocused = it.isFocused }
                        .testTag("search_tab_input")
                )
                
                if (searchQuery.isEmpty()) {
                    // Popular Suggestions placed directly under the search bar
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
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
                                        .clickable { viewModel.setSearchQuery(keyword) }
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

                    // Centered onboarding/empty search state taking the remaining space
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
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
                } else {
                    val allGroupedChannels = remember(allChannelsRaw) {
                        viewModel.groupChannels(allChannelsRaw)
                    }
                    val filteredChannels = remember(allGroupedChannels, searchQuery) {
                        allGroupedChannels.filter { groupedChannel ->
                            groupedChannel.name.contains(searchQuery, ignoreCase = true) ||
                            groupedChannel.description.contains(searchQuery, ignoreCase = true) ||
                            (categoryMap[groupedChannel.categoryId]?.contains(searchQuery, ignoreCase = true) == true)
                        }
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
                                    text = "We couldn't find any live feeds matching \"$searchQuery\". Try searching for a different keyword or check your spelling.",
                                    fontSize = 13.sp,
                                    color = Color.Gray,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(0.85f)
                                )
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 165.dp),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 8.dp,
                                bottom = innerPadding.calculateBottomPadding() + 24.dp
                            ),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
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
fun ChannelCard(
    channel: ChannelEntity,
    categoryName: String,
    onClick: (ChannelEntity) -> Unit,
    onToggleFavorite: (ChannelEntity) -> Unit,
    cardBg: Color,
    accentColor: Color,
    isScrolling: Boolean = false
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

    val isScaleUp = isFocused || isHovered
    val cardScale by animateFloatAsState(
        targetValue = if (isScaleUp) 1.05f else 1.00f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "cardScaleAnimation"
    )
    val cardBorderColor by animateColorAsState(
        targetValue = if (isScaleUp) accentColor else Color.White.copy(alpha = 0.05f),
        animationSpec = tween(durationMillis = 200),
        label = "cardBorderColorAnimation"
    )

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, cardBorderColor),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer(scaleX = cardScale, scaleY = cardScale)
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
                    text = channel.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = channel.description.ifBlank { "Tap to stream live feed." },
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
        name.contains("live event") || name.contains("event") -> Icons.Default.CheckCircle
        name.contains("news") -> Icons.Default.Info
        name.contains("sport") || name.contains("cricket") || name.contains("football") -> Icons.Default.Star
        name.contains("kid") || name.contains("cartoon") || name.contains("animation") || name.contains("child") -> Icons.Default.Face
        name.contains("music") -> Icons.Default.Favorite
        name.contains("movie") || name.contains("drama") || name.contains("cinema") -> Icons.Default.PlayArrow
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
        name.contains("live event") || name.contains("event") -> listOf(Color(0xFF8B0000), Color(0xFFFF1744))
        name.contains("news") -> listOf(Color(0xFF8B0000), Color(0xFFE53935))
        name.contains("sport") || name.contains("cricket") || name.contains("football") -> listOf(Color(0xFF0F5A26), Color(0xFF1DB954))
        name.contains("entertainment") || name.contains("show") -> listOf(Color(0xFF4B0082), Color(0xFF8A2BE2))
        name.contains("kid") || name.contains("cartoon") || name.contains("animation") || name.contains("child") -> listOf(Color(0xFFD84B16), Color(0xFFFF9800))
        name.contains("movie") || name.contains("drama") || name.contains("cinema") -> listOf(Color(0xFF0D47A1), Color(0xFF1976D2))
        name.contains("music") -> listOf(Color(0xFF880E4F), Color(0xFFE91E63))
        name.contains("religious") || name.contains("islam") || name.contains("prayer") -> listOf(Color(0xFF004D40), Color(0xFF009688))
        name.contains("bangla") || name.contains("local") -> listOf(Color(0xFFE65100), Color(0xFFFFB300))
        name.contains("science") || name.contains("document") || name.contains("info") -> listOf(Color(0xFF3E2723), Color(0xFF795548))
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
    accentColor: Color
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
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
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
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.12f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "tabScale"
    )
    val dotAlpha by animateFloatAsState(
        targetValue = if (selected) 1.0f else 0.0f,
        animationSpec = tween(durationMillis = 250),
        label = "dotAlpha"
    )
    
    Column(
        modifier = modifier
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 4.dp, horizontal = 12.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .height(34.dp)
                .width(60.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(if (selected) accentColor.copy(alpha = 0.18f) else Color.Transparent)
        ) {
            Icon(
                imageVector = if (selected) selectedIcon else icon,
                contentDescription = label,
                tint = if (selected) accentColor else Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.45f),
            letterSpacing = 0.2.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .size(4.dp)
                .graphicsLayer(alpha = dotAlpha)
                .background(accentColor, CircleShape)
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
    isPublicMode: Boolean
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = innerPadding.calculateTopPadding(), bottom = innerPadding.calculateBottomPadding())
            .padding(horizontal = 16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
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

