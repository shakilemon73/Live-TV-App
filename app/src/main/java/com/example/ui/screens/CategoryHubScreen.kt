package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ChannelEntity
import com.example.data.GroupedChannel
import com.example.data.EpgProgramEntity
import com.example.ui.components.rememberResponsiveGridSpec

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryHubScreen(
    categoryName: String,
    categoryId: Int,
    listToDisplay: List<GroupedChannel>,
    allChannelsRaw: List<ChannelEntity>,
    currentEpgProgramsMap: Map<String, EpgProgramEntity>,
    onCardClick: (GroupedChannel) -> Unit,
    onFavoriteClick: (GroupedChannel) -> Unit,
    onBackClick: () -> Unit,
    cardBg: Color,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val spec = rememberResponsiveGridSpec()
    val lazyGridState = rememberLazyGridState()
    val isScrolling by remember {
        derivedStateOf { lazyGridState.isScrollInProgress }
    }

    var localSearchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var sortMode by remember { mutableStateOf(0) } // 0 = Recommended, 1 = A-Z, 2 = Starred Only

    // Filter list dynamically based on search query and sort mode
    val filteredAndSortedChannels = remember(listToDisplay, localSearchQuery, sortMode) {
        var result = listToDisplay
        if (localSearchQuery.isNotEmpty()) {
            result = result.filter { it.name.lowercase().contains(localSearchQuery.lowercase().trim()) }
        }
        when (sortMode) {
            1 -> result.sortedBy { it.name.lowercase() }
            2 -> result.filter { it.isFavorite }
            else -> result // Default (Score-ranked or raw sorted by ViewModel)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchActive) {
                        TextField(
                            value = localSearchQuery,
                            onValueChange = { localSearchQuery = it },
                            placeholder = { Text("Search in $categoryName...", color = Color.White.copy(alpha = 0.5f), fontSize = 15.sp) },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("category_local_search_input"),
                            trailingIcon = {
                                if (localSearchQuery.isNotEmpty()) {
                                    IconButton(onClick = { localSearchQuery = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.White)
                                    }
                                }
                            }
                        )
                    } else {
                        Column {
                            Text(
                                text = "CATEGORY HUB",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = accentColor,
                                letterSpacing = 1.5.sp
                            )
                            Text(
                                text = categoryName,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (isSearchActive) {
                                isSearchActive = false
                                localSearchQuery = ""
                            } else {
                                onBackClick()
                            }
                        },
                        modifier = Modifier.testTag("category_hub_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back to Home",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { isSearchActive = !isSearchActive },
                        modifier = Modifier.testTag("category_hub_search_toggle")
                    ) {
                        Icon(
                            imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "Search Within Category",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F0B1E).copy(alpha = 0.95f),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF0F0B1E),
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF0F0B1E),
                            Color(0xFF07040D)
                        )
                    )
                )
        ) {
            LazyVerticalGrid(
                state = lazyGridState,
                columns = GridCells.Fixed(spec.columns),
                contentPadding = PaddingValues(
                    start = spec.margin,
                    end = spec.margin,
                    top = 16.dp,
                    bottom = 32.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(spec.gutter),
                verticalArrangement = Arrangement.spacedBy(spec.gutter),
                modifier = Modifier.fillMaxSize()
            ) {
                // 1. Full-width Cinematic Category Showcase Header
                item(
                    span = { GridItemSpan(maxLineSpan) },
                    key = "cinematic_category_header_${categoryId}",
                    contentType = "category_showcase_header"
                ) {
                    CategoryDetailShowcaseHeader(
                        categoryName = categoryName,
                        count = listToDisplay.size,
                        onPlayFeaturedClick = {
                            listToDisplay.firstOrNull()?.let { onCardClick(it) }
                        },
                        onShuffleClick = {
                            if (listToDisplay.isNotEmpty()) {
                                onCardClick(listToDisplay.random())
                            }
                        },
                        cardBg = cardBg,
                        accentColor = accentColor
                    )
                }

                // 2. Interactive Sorter & Metadata Status Row
                item(
                    span = { GridItemSpan(maxLineSpan) },
                    key = "category_sorter_row_${categoryId}",
                    contentType = "category_sorter"
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, bottom = 12.dp, start = 4.dp, end = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (localSearchQuery.isNotEmpty()) {
                                "FOUND ${filteredAndSortedChannels.size} RESULTS"
                            } else {
                                "SHOWING ${filteredAndSortedChannels.size} CHANNELS"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.7f),
                            letterSpacing = 0.5.sp
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SortFilterChip(
                                selected = sortMode == 0,
                                label = "RECOMMENDED",
                                onClick = { sortMode = 0 },
                                accentColor = accentColor
                            )
                            SortFilterChip(
                                selected = sortMode == 1,
                                label = "A-Z",
                                onClick = { sortMode = 1 },
                                accentColor = accentColor
                            )
                            SortFilterChip(
                                selected = sortMode == 2,
                                label = "STARRED",
                                onClick = { sortMode = 2 },
                                accentColor = accentColor
                            )
                        }
                    }
                }

                // 3. Grid items: Adaptive Channel Cards
                if (filteredAndSortedChannels.isEmpty()) {
                    item(
                        span = { GridItemSpan(maxLineSpan) },
                        key = "category_hub_empty_${categoryId}"
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 80.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = if (sortMode == 2) Icons.Default.FavoriteBorder else Icons.Default.Search,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.25f),
                                    modifier = Modifier.size(56.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = if (sortMode == 2) {
                                        "No Starred Channels in this category"
                                    } else {
                                        "No Channels found matching query"
                                    },
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.LightGray.copy(alpha = 0.8f)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (sortMode == 2) {
                                        "Tap the heart on any channel card to add it to your favorites."
                                    } else {
                                        "Try searching for another keywords or clear filter settings."
                                    },
                                    fontSize = 11.5.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                } else {
                    items(
                        items = filteredAndSortedChannels,
                        key = { "category_hub_card_${it.name}" },
                        contentType = { "category_hub_channel" }
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
                            currentProgram = epgProgram
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryDetailShowcaseHeader(
    categoryName: String,
    count: Int,
    onPlayFeaturedClick: () -> Unit,
    onShuffleClick: () -> Unit,
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
            isPressed -> 0.98f
            isScaleUp -> 1.02f
            else -> 1.0f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "showcase_header_scale"
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
        border = BorderStroke(1.2.dp, if (isScaleUp) accentColor.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.08f)),
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .shadow(
                elevation = if (isScaleUp) 16.dp else 4.dp,
                shape = RoundedCornerShape(24.dp),
                ambientColor = if (isScaleUp) accentColor.copy(alpha = 0.25f) else Color.Transparent,
                spotColor = if (isScaleUp) accentColor.copy(alpha = 0.25f) else Color.Transparent
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(brush = gradient)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.20f), Color.Black.copy(alpha = 0.85f))))
                .padding(20.dp)
        ) {
            Icon(
                imageVector = getCategoryIcon(categoryName),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.06f),
                modifier = Modifier
                    .size(140.dp)
                    .align(Alignment.CenterEnd)
                    .offset(x = 16.dp, y = 16.dp)
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
                    Column {
                        Text(
                            text = "LIVE FEED HUB",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = accentColor,
                            letterSpacing = 1.5.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = categoryName.uppercase(),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            letterSpacing = 0.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    
                    Box(
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.12f), CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
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
                                text = "CINEMATIC",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }

                Text(
                    text = categoryCopywriting,
                    fontSize = 11.5.sp,
                    color = Color.LightGray.copy(alpha = 0.85f),
                    lineHeight = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(0.75f)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onPlayFeaturedClick,
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color(0xFF100C1F),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "PLAY TOP FEED",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF100C1F),
                                letterSpacing = 0.5.sp
                            )
                        }

                        OutlinedButton(
                            onClick = onShuffleClick,
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White.copy(alpha = 0.05f)),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shuffle,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "SHUFFLE",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    Text(
                        text = "$count Live Feeds",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.6f),
                        letterSpacing = 0.2.sp
                    )
                }
            }
        }
    }
}

@Composable
fun SortFilterChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    accentColor: Color
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isPulsing = isHovered || isFocused || selected

    val backgroundBg = if (selected) accentColor.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f)
    val borderStrokeColor = if (selected) accentColor.copy(alpha = 0.4f) else if (isPulsing) Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.06f)

    Box(
        modifier = Modifier
            .background(backgroundBg, CircleShape)
            .border(1.dp, borderStrokeColor, CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.foundation.LocalIndication.current,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .background(accentColor, CircleShape)
                )
            }
            Text(
                text = label,
                fontSize = 9.sp,
                fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold,
                color = if (selected) accentColor else Color.White.copy(alpha = 0.65f),
                letterSpacing = 0.3.sp
            )
        }
    }
}
