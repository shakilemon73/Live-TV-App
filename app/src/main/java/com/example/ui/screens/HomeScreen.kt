package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
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
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.data.CategoryEntity
import com.example.data.ChannelEntity
import com.example.data.GroupedChannel
import com.example.data.StreamSource
import com.example.data.RecordingEntity
import com.example.ui.ChannelViewModel

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

    var currentTab by remember { mutableStateOf(0) } // 0 = Channels, 1 = Categories, 2 = Settings
    var showFavoritesOnly by remember { mutableStateOf(false) }
    var titleTapCount by remember { mutableStateOf(0) }
    var showAdminOverride by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

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
    val filterBrokenChannels by viewModel.filterBrokenChannels.collectAsStateWithLifecycle()
    
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
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                if (currentTab == 0) {
                    // Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.clickable(
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
                                text = "Live Stream Hub",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                letterSpacing = (-0.5).sp
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Pulsating Live broadcast dot
                                PulsatingCardIndicator()
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "ACTIVE BROADCASTS",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = accentColor,
                                    letterSpacing = 1.5.sp
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Refresh & Verify Channels Button
                            IconButton(
                                onClick = { viewModel.verifyAllChannels() },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("refresh_verify_header_button")
                            ) {
                                if (isCheckingStreams) {
                                    CircularProgressIndicator(
                                        color = accentColor,
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.5.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Refresh & Verify Channels",
                                        tint = Color.White
                                    )
                                }
                            }

                            // Dynamic Network connection status icon in the top header bar
                            NetworkConnectionIndicator(isOnline = isOnline)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Modern glassmorphic search input
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = { Text("Find channels, broadcasts...", color = Color.Gray) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = Color.Gray
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
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
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("search_bar")
                    )

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
                            label = { Text("Favorites (${favorites.size})", fontWeight = FontWeight.Bold) },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (showFavoritesOnly) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Favorites",
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFFF8A80),
                                selectedLabelColor = Color.Black,
                                selectedLeadingIconColor = Color.Black,
                                containerColor = cardBg,
                                labelColor = Color(0xFFCAC4D0),
                                iconColor = Color(0xFFCAC4D0)
                            ),
                            border = null,
                            shape = RoundedCornerShape(14.dp)
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
                                            Text("All")
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .background(
                                                        if (isAllSelected) onPurpleColor.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f),
                                                        CircleShape
                                                    )
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
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
                                    shape = RoundedCornerShape(14.dp),
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
                                            Text(category.name)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .background(
                                                        if (isSelected) onPurpleColor.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f),
                                                        CircleShape
                                                    )
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
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
                                    shape = RoundedCornerShape(14.dp),
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
                            NetworkConnectionIndicator(isOnline = isOnline)
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
                    // Offline Library Top Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Offline Library",
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
                                    text = "YOUR RECORDED SHOWS",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = accentColor,
                                    letterSpacing = 1.5.sp
                                )
                            }
                        }
                        NetworkConnectionIndicator(isOnline = isOnline)
                    }
                } else {
                    // Settings Top Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Portal Settings",
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
                                    text = "MANAGE SOURCES & PREFERENCES",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = accentColor,
                                    letterSpacing = 1.5.sp
                                )
                            }
                        }
                        NetworkConnectionIndicator(isOnline = isOnline)
                    }
                }
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .background(darkBg)
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

                NavigationBar(
                    containerColor = cardBg,
                    tonalElevation = 8.dp,
                    modifier = Modifier
                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                ) {
                    NavigationBarItem(
                        selected = currentTab == 0,
                        onClick = { currentTab = 0 },
                        icon = { Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Channels") },
                        label = { Text("Channels", fontWeight = if (currentTab == 0) FontWeight.Bold else FontWeight.Normal) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = onPurpleColor,
                            selectedTextColor = accentColor,
                            indicatorColor = accentColor,
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray
                        )
                    )
                    NavigationBarItem(
                        selected = currentTab == 1,
                        onClick = { currentTab = 1 },
                        icon = { Icon(imageVector = Icons.Default.Star, contentDescription = "Categories") },
                        label = { Text("Genres", fontWeight = if (currentTab == 1) FontWeight.Bold else FontWeight.Normal) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = onPurpleColor,
                            selectedTextColor = accentColor,
                            indicatorColor = accentColor,
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray
                        )
                    )
                    NavigationBarItem(
                        selected = currentTab == 2,
                        onClick = { currentTab = 2 },
                        icon = { Icon(imageVector = Icons.Default.List, contentDescription = "Recordings") },
                        label = { Text("Offline", fontWeight = if (currentTab == 2) FontWeight.Bold else FontWeight.Normal) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = onPurpleColor,
                            selectedTextColor = accentColor,
                            indicatorColor = accentColor,
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray
                        )
                    )
                    NavigationBarItem(
                        selected = currentTab == 3,
                        onClick = { currentTab = 3 },
                        icon = { Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings", fontWeight = if (currentTab == 3) FontWeight.Bold else FontWeight.Normal) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = onPurpleColor,
                            selectedTextColor = accentColor,
                            indicatorColor = accentColor,
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray
                        )
                    )
                }
            }
        },
        containerColor = darkBg,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

        if (currentTab == 0) {
            val listToDisplay = if (showFavoritesOnly) {
                favorites.filter { channel ->
                    searchQuery.isEmpty() || channel.name.contains(searchQuery, ignoreCase = true)
                }
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
                        columns = GridCells.Fixed(2),
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
                                text = if (showFavoritesOnly) "No Saved Favorites" else "No Broadcasts Found",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (showFavoritesOnly) "Click the heart button on live feeds to collect your favorite channels here." else "We couldn't find any channels matching this query. Please register new feeds in settings.",
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
                        columns = GridCells.Fixed(2),
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

                        // GRID OF CHANNELS (Using unique stable keys & contentType to enable smooth recycle/scrolling)
                        val displayList = if (searchQuery.isNotEmpty() || showFavoritesOnly) listToDisplay else remainingChannels
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
                            text = if (categorySearchQuery.isNotEmpty()) "Try searching for a different keyword or category name." else "Head to Settings to synchronize categories or add channels in the Admin panel.",
                            fontSize = 13.sp,
                            color = Color.Gray,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
                    items(items = processedCategories, key = { it.id }, contentType = { "category" }) { category ->
                        val count = categoryCounts[category.id] ?: 0
                        
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .clickable {
                                    viewModel.selectCategory(category.id)
                                    showFavoritesOnly = false
                                    currentTab = 0
                                }
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
            OfflineRecordingsScreen(
                viewModel = viewModel,
                innerPadding = innerPadding,
                onCardClick = onRecordingCardClick,
                cardBg = cardBg,
                accentColor = accentColor
            )
        } else {
            // Tab 3: Settings View
            val autoSyncOnLaunch by viewModel.autoSyncOnLaunch.collectAsStateWithLifecycle()
            val lastSyncTime by viewModel.lastSyncTime.collectAsStateWithLifecycle()
            val syncStatusMessage by viewModel.syncStatusMessage.collectAsStateWithLifecycle()
            val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

            val streamCheckingProgress by viewModel.streamCheckingProgress.collectAsStateWithLifecycle()
            val streamCheckingStatus by viewModel.streamCheckingStatus.collectAsStateWithLifecycle()
            val filterBrokenChannels by viewModel.filterBrokenChannels.collectAsStateWithLifecycle()

            var localAutoSync by remember(autoSyncOnLaunch) { mutableStateOf(autoSyncOnLaunch) }

            val scrollState = rememberScrollState()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Section 1: Secure Background Database Synchronization
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = "Cloud Source", tint = accentColor, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Channel Database Synchronization", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Keep your TV streams up-to-date automatically. Feeds are securely cached and synchronized in the background.", fontSize = 12.sp, color = Color.Gray)

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Auto Update on Launch", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text("Automatically synchronize the stream list on application startup", fontSize = 11.sp, color = Color.Gray)
                            }
                            Switch(
                                checked = localAutoSync,
                                onCheckedChange = { 
                                    localAutoSync = it
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

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = {
                                viewModel.syncWithCloudGist()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = onPurpleColor),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("save_sync_button")
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(color = onPurpleColor, modifier = Modifier.size(18.dp))
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Sync Now", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Check for Updates Now", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Sync Log Status Indicator
                if (syncStatusMessage != null || lastSyncTime > 0) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f)),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(8.dp).background(if (syncStatusMessage?.contains("failed", ignoreCase = true) == true) Color(0xFFFF8A80) else Color(0xFF81C784), CircleShape))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Cloud Sync status log:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.LightGray,
                                    letterSpacing = 1.sp
                                )
                            }
                            if (syncStatusMessage != null) {
                                Spacer(modifier = Modifier.height(4.dp))
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
                                    text = "Last sync completed at: $formattedDate",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }

                // Section: Stream Link Quality Assurance & Verification
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle, 
                                contentDescription = "Quality Assurance", 
                                tint = Color(0xFF81C784), 
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Stream Quality Filter", 
                                fontSize = 18.sp, 
                                fontWeight = FontWeight.Bold, 
                                color = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Scan all streams to identify non-functional links. Broken streams will be automatically excluded from your guide.", 
                            fontSize = 12.sp, 
                            color = Color.Gray
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Exclude Broken Streams", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text("Only show verified, working channels in the player lists", fontSize = 11.sp, color = Color.Gray)
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
                                Text(
                                    text = streamCheckingStatus ?: "Scanning stream links...",
                                    fontSize = 12.sp,
                                    color = Color.LightGray,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                )
                            }
                        } else {
                            Button(
                                onClick = {
                                    viewModel.verifyAllChannels()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF2E7D32), 
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("verify_streams_button")
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Check, 
                                        contentDescription = "Verify", 
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Scan & Validate All Channels", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Section 3: App Compliance & Technical Specification Details
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Info, contentDescription = "App Info", tint = Color.LightGray, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Application Information", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Engine Version", fontSize = 12.sp, color = Color.Gray)
                            Text("v1.0.0 (ExoPlayer Live Support)", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Medium)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Build Compliance", fontSize = 12.sp, color = Color.Gray)
                            Text("Google Play Console Ready", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Medium)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Client UTC Time", fontSize = 12.sp, color = Color.Gray)
                            Text("2026-06-27 02:36:45", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Medium)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Active Operator", fontSize = 12.sp, color = Color.Gray)
                            Text("stephanlegarza710", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
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
    Card(
        shape = RoundedCornerShape(26.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1C22)),
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp)
            .clickable { onClick(channel) }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
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
            // Widescreen background backdrop
            AsyncImage(
                model = imageRequest,
                contentDescription = channel.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Bottom cinematic dark vignette
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.2f),
                                Color.Black.copy(alpha = 0.85f)
                            ),
                            startY = 0f
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
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(50))
                            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        PulsatingCardIndicator()
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "FEATURED STREAM",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            letterSpacing = 1.sp
                        )
                    }

                    // Fast Favorite Toggler
                    IconButton(
                        onClick = { onToggleFavorite(channel) },
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(50))
                    ) {
                        Icon(
                            imageVector = if (channel.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (channel.isFavorite) Color(0xFFFF8A80) else Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Title and action controls
                Column {
                    Text(
                        text = categoryName.uppercase(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = channel.name,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = channel.description.ifBlank { "Exclusive top-recommended live broadcast stream." },
                            fontSize = 12.sp,
                            color = Color.LightGray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        // Large Play CTA Button
                        Button(
                            onClick = { onClick(channel) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accentColor,
                                contentColor = Color(0xFF381E72)
                            ),
                            shape = RoundedCornerShape(50),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("WATCH NOW", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
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
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(channel) }
            .testTag("channel_card_${channel.id}")
    ) {
        Column {
            // Card Backdrop containing Live indicators and Favorite toggles
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
            ) {
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
                AsyncImage(
                    model = imageRequest,
                    contentDescription = channel.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Dark subtle vignette gradient
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)),
                                startY = 40f
                            )
                        )
                )

                // Category tag
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .align(Alignment.TopStart)
                ) {
                    Text(
                        text = categoryName.uppercase(),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = secondaryAccentColor
                    )
                }

                // Heart favorite indicator overlay
                IconButton(
                    onClick = { onToggleFavorite(channel) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(34.dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(50))
                ) {
                    Icon(
                        imageVector = if (channel.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (channel.isFavorite) Color(0xFFFF8A80) else Color.White,
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
                        text = "LIVE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            // Description and title area
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = channel.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = channel.description.ifBlank { "Tap to stream live feed." },
                    fontSize = 11.sp,
                    color = Color.Gray,
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
                AsyncImage(
                    model = recording.channelLogoUrl,
                    contentDescription = recording.channelName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
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
