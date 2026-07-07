package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.platform.LocalContext
import coil.request.CachePolicy
import coil.request.ImageRequest
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.CategoryEntity
import com.example.data.ChannelEntity
import com.example.ui.ChannelViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    viewModel: ChannelViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Channels", "Categories", "Cloud Sync", "Debug Console")

    // Dynamic coloring matching home screen
    val darkBg = Color(0xFF1C1B1F)
    val cardBg = Color(0xFF2B2930)
    val accentColor = Color(0xFFD0BCFF)
    val secondaryAccentColor = Color(0xFFEADDFF)
    val onPurpleColor = Color(0xFF381E72)

    val lazyListState = rememberLazyListState()
    val isScrolling by remember {
        derivedStateOf { lazyListState.isScrollInProgress }
    }

    // Dialog trigger states
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var showEditCategoryDialog by remember { mutableStateOf<CategoryEntity?>(null) }
    var showAddChannelDialog by remember { mutableStateOf(false) }
    var showEditChannelDialog by remember { mutableStateOf<ChannelEntity?>(null) }
    val focusManager = LocalFocusManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Admin Dashboard", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            focusManager.clearFocus()
                            onNavigateBack()
                        },
                        modifier = Modifier.testTag("admin_back_button")
                    ) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = darkBg,
                    titleContentColor = Color.White
                ),
                modifier = Modifier.statusBarsPadding()
            )
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                ExtendedFloatingActionButton(
                    onClick = { showAddChannelDialog = true },
                    icon = { Icon(Icons.Default.Add, "Add Channel") },
                    text = { Text("Add Channel") },
                    containerColor = accentColor,
                    contentColor = onPurpleColor,
                    modifier = Modifier.testTag("add_channel_fab")
                )
            } else if (selectedTab == 1) {
                ExtendedFloatingActionButton(
                    onClick = { showAddCategoryDialog = true },
                    icon = { Icon(Icons.Default.Add, "Add Category") },
                    text = { Text("Add Category") },
                    containerColor = secondaryAccentColor,
                    contentColor = onPurpleColor,
                    modifier = Modifier.testTag("add_category_fab")
                )
            }
        },
        containerColor = darkBg,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Tab Header Row
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color(0xFF2B2930),
                contentColor = Color.White,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = accentColor
                    )
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 15.sp
                            )
                        },
                        selectedContentColor = accentColor,
                        unselectedContentColor = Color.Gray,
                        modifier = Modifier.testTag("admin_tab_$index")
                    )
                }
            }

            // Tab Content
            when (selectedTab) {
                0 -> {
                // Channels management view
                var m3uInputUrl by remember { mutableStateOf("https://iptv-org.github.io/iptv/index.m3u") }

                Column(modifier = Modifier.fillMaxSize()) {
                    // Curated streams directory card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Curated Streams Directory",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Restore the list of 20+ free, stable m3u8 live streams.",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                            Button(
                                onClick = { viewModel.resetToCuratedStreams() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = accentColor,
                                    contentColor = onPurpleColor
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("reset_to_curated_button")
                            ) {
                                Text("Load", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Custom M3U / M3U8 Playlist URL import card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "Import Custom M3U Playlist",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                              )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Enter a URL to fetch, parse, and register channels.",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = m3uInputUrl,
                                    onValueChange = { m3uInputUrl = it },
                                    placeholder = { Text("Playlist URL (.m3u/.m3u8)", color = Color.Gray, fontSize = 11.sp) },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = accentColor,
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                                        focusedContainerColor = Color(0xFF1C1B1F),
                                        unfocusedContainerColor = Color(0xFF1C1B1F)
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("m3u_playlist_url_input")
                                )
                                Button(
                                    onClick = {
                                        if (m3uInputUrl.isNotBlank()) {
                                            viewModel.fetchAndParseM3u(m3uInputUrl.trim())
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = accentColor,
                                        contentColor = onPurpleColor
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                    modifier = Modifier.testTag("import_m3u_playlist_button")
                                ) {
                                    Text("Import", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

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

                        LazyColumn(
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 80.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        ) {
                            items(6, contentType = { "channel_admin_skeleton" }) {
                                SkeletonChannelAdminRow(alpha = alpha, cardBg = cardBg)
                            }
                        }
                    } else if (channels.isEmpty()) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text("No channels registered. Add your first stream!", color = Color.Gray)
                        }
                    } else {
                        LazyColumn(
                            state = lazyListState,
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 80.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        ) {
                            items(
                                items = channels,
                                key = { it.id },
                                contentType = { "channel_admin_item" }
                            ) { channel ->
                                val categoryName = categories.find { it.id == channel.categoryId }?.name ?: "General"
                                ChannelAdminRow(
                                    channel = channel,
                                    categoryName = categoryName,
                                    onEdit = { showEditChannelDialog = channel },
                                    onDelete = { viewModel.deleteChannel(channel) },
                                    cardBg = cardBg,
                                    isScrolling = isScrolling
                                )
                            }
                        }
                    }
                }
                }
                1 -> {
                // Categories management view
                if (isLoading) {
                    val infiniteTransition = rememberInfiniteTransition(label = "categories_admin_skeleton")
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 0.4f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 800, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "shimmer_alpha"
                    )
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(5, contentType = { "category_admin_skeleton" }) {
                            SkeletonCategoryAdminRow(alpha = alpha, cardBg = cardBg)
                        }
                    }
                } else if (categories.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No categories registered. Create one!", color = Color.Gray)
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            items = categories,
                            key = { it.id },
                            contentType = { "category_admin_item" }
                        ) { category ->
                            CategoryAdminRow(
                                category = category,
                                onEdit = { showEditCategoryDialog = category },
                                onDelete = { viewModel.deleteCategory(category) },
                                cardBg = cardBg
                            )
                        }
                    }
                }
                }
                2 -> {
                    CloudSyncSettingsView(
                        viewModel = viewModel,
                        cardBg = cardBg,
                        accentColor = accentColor,
                        onPurpleColor = onPurpleColor
                    )
                }
                3 -> {
                    DebugConsoleView(
                        viewModel = viewModel,
                        cardBg = cardBg,
                        accentColor = accentColor,
                        onPurpleColor = onPurpleColor
                    )
                }
            }
        }

        // --- Categories Dialogs ---
        if (showAddCategoryDialog) {
            var categoryName by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showAddCategoryDialog = false },
                title = { Text("Add Category", fontWeight = FontWeight.Bold) },
                text = {
                    OutlinedTextField(
                        value = categoryName,
                        onValueChange = { categoryName = it },
                        label = { Text("Category Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_category_name")
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (categoryName.isNotBlank()) {
                                viewModel.addCategory(categoryName.trim())
                                showAddCategoryDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFD0BCFF),
                            contentColor = Color(0xFF381E72)
                        ),
                        modifier = Modifier.testTag("submit_category_button")
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddCategoryDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        showEditCategoryDialog?.let { currentCategory ->
            var categoryName by remember { mutableStateOf(currentCategory.name) }
            AlertDialog(
                onDismissRequest = { showEditCategoryDialog = null },
                title = { Text("Edit Category", fontWeight = FontWeight.Bold) },
                text = {
                    OutlinedTextField(
                        value = categoryName,
                        onValueChange = { categoryName = it },
                        label = { Text("Category Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (categoryName.isNotBlank()) {
                                viewModel.updateCategory(currentCategory.copy(name = categoryName.trim()))
                                showEditCategoryDialog = null
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFD0BCFF),
                            contentColor = Color(0xFF381E72)
                        )
                    ) {
                        Text("Update")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEditCategoryDialog = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // --- Channels Dialogs (Add & Edit) ---
        if (showAddChannelDialog) {
            ChannelFormDialog(
                title = "Add Live Channel",
                categories = categories,
                onDismiss = { showAddChannelDialog = false },
                onSubmit = { name, url, logo, catId, desc ->
                    viewModel.addChannel(name, url, logo, catId, desc)
                    showAddChannelDialog = false
                }
            )
        }

        showEditChannelDialog?.let { currentChannel ->
            ChannelFormDialog(
                title = "Edit Live Channel",
                categories = categories,
                initialChannel = currentChannel,
                onDismiss = { showEditChannelDialog = null },
                onSubmit = { name, url, logo, catId, desc ->
                    viewModel.updateChannel(
                        currentChannel.copy(
                            name = name,
                            streamUrl = url,
                            logoUrl = logo,
                            categoryId = catId,
                            description = desc
                        )
                    )
                    showEditChannelDialog = null
                }
            )
        }
    }
}

// --- List Row Composables ---

@Composable
fun ChannelAdminRow(
    channel: ChannelEntity,
    categoryName: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    cardBg: Color,
    isScrolling: Boolean = false
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
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
            // Channel Mini Image Preview
            AsyncImage(
                model = imageRequest,
                contentDescription = channel.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Gray)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.name,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = categoryName.uppercase(),
                    color = Color(0xFFD0BCFF),
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
                Text(
                    text = channel.streamUrl,
                    color = Color.Gray,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Admin Operations
            IconButton(onClick = onEdit, modifier = Modifier.testTag("edit_channel_button_${channel.id}")) {
                Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Channel", tint = Color.LightGray)
            }
            IconButton(onClick = onDelete, modifier = Modifier.testTag("delete_channel_button_${channel.id}")) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete Channel", tint = Color(0xFFE53935))
            }
        }
    }
}

@Composable
fun CategoryAdminRow(
    category: CategoryEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    cardBg: Color
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = category.name,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )

            Row {
                IconButton(onClick = onEdit, modifier = Modifier.testTag("edit_category_button_${category.id}")) {
                    Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Category", tint = Color.LightGray)
                }
                IconButton(onClick = onDelete, modifier = Modifier.testTag("delete_category_button_${category.id}")) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete Category", tint = Color(0xFFE53935))
                }
            }
        }
    }
}

// --- Channel Form Dialog (Reusable for Add & Edit) ---

@Composable
fun ChannelFormDialog(
    title: String,
    categories: List<CategoryEntity>,
    initialChannel: ChannelEntity? = null,
    onDismiss: () -> Unit,
    onSubmit: (name: String, url: String, logo: String, categoryId: Int, description: String) -> Unit
) {
    var name by remember { mutableStateOf(initialChannel?.name ?: "") }
    var streamUrl by remember { mutableStateOf(initialChannel?.streamUrl ?: "") }
    var logoUrl by remember { mutableStateOf(initialChannel?.logoUrl ?: "") }
    var description by remember { mutableStateOf(initialChannel?.description ?: "") }

    // Category ID selection
    var selectedCategoryId by remember {
        mutableStateOf(
            initialChannel?.categoryId ?: categories.firstOrNull()?.id ?: 0
        )
    }

    var expandedCategorySelector by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Channel Name *") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_channel_name")
                )

                OutlinedTextField(
                    value = streamUrl,
                    onValueChange = { streamUrl = it },
                    label = { Text("Stream URL (M3U8) *") },
                    singleLine = true,
                    placeholder = { Text("https://example.com/live.m3u8") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_channel_url")
                )

                OutlinedTextField(
                    value = logoUrl,
                    onValueChange = { logoUrl = it },
                    label = { Text("Logo Image URL (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (Optional)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                // Safe Category selection Dropdown substitute (Fully styling compatible)
                Text(
                    text = "Channel Category *",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expandedCategorySelector = !expandedCategorySelector }
                        .background(Color.DarkGray.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        .padding(16.dp)
                ) {
                    val activeCategoryName = categories.find { it.id == selectedCategoryId }?.name ?: "Select Category"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = activeCategoryName, color = Color.White)
                        Icon(
                            imageVector = if (expandedCategorySelector) Icons.Default.KeyboardArrowUp else Icons.Default.ArrowDropDown,
                            contentDescription = "Dropdown indicator",
                            tint = Color.White
                        )
                    }
                }

                if (expandedCategorySelector) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 150.dp)
                    ) {
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            items(categories) { category ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedCategoryId = category.id
                                            expandedCategorySelector = false
                                        }
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = category.name, color = Color.White)
                                    if (selectedCategoryId == category.id) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = Color(0xFFD0BCFF)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && streamUrl.isNotBlank() && selectedCategoryId != 0) {
                        onSubmit(name.trim(), streamUrl.trim(), logoUrl.trim(), selectedCategoryId, description.trim())
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFD0BCFF),
                    contentColor = Color(0xFF381E72)
                ),
                modifier = Modifier.testTag("submit_channel_button")
            ) {
                Text("Save Channel")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun SkeletonChannelAdminRow(
    alpha: Float,
    cardBg: Color
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Image outline placeholder
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.08f * alpha))
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Name placeholder
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(14.dp)
                        .background(Color.White.copy(alpha = 0.1f * alpha), RoundedCornerShape(4.dp))
                )
                // Category placeholder
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.3f)
                        .height(10.dp)
                        .background(Color.White.copy(alpha = 0.08f * alpha), RoundedCornerShape(4.dp))
                )
                // URL placeholder
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(10.dp)
                        .background(Color.White.copy(alpha = 0.05f * alpha), RoundedCornerShape(4.dp))
                )
            }
        }
    }
}

@Composable
fun SkeletonCategoryAdminRow(
    alpha: Float,
    cardBg: Color
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.White.copy(alpha = 0.05f * alpha), CircleShape)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .height(16.dp)
                        .background(Color.White.copy(alpha = 0.1f * alpha), RoundedCornerShape(4.dp))
                )
                Box(
                    modifier = Modifier
                        .width(60.dp)
                        .height(10.dp)
                        .background(Color.White.copy(alpha = 0.05f * alpha), RoundedCornerShape(2.dp))
                )
            }
        }
    }
}

@Composable
fun CloudSyncSettingsView(
    viewModel: com.example.ui.ChannelViewModel,
    cardBg: Color,
    accentColor: Color,
    onPurpleColor: Color
) {
    val cloudGistUrl by viewModel.cloudGistUrl.collectAsStateWithLifecycle()
    val autoSyncOnLaunch by viewModel.autoSyncOnLaunch.collectAsStateWithLifecycle()
    val isPublicMode by viewModel.isPublicMode.collectAsStateWithLifecycle()
    val lastSyncTime by viewModel.lastSyncTime.collectAsStateWithLifecycle()
    val syncStatusMessage by viewModel.syncStatusMessage.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    var urlInput by remember { mutableStateOf(cloudGistUrl) }
    var autoSync by remember { mutableStateOf(autoSyncOnLaunch) }
    var publicMode by remember { mutableStateOf(isPublicMode) }

    // Synchronize local UI state with ViewModel state on load
    LaunchedEffect(cloudGistUrl, autoSyncOnLaunch, isPublicMode) {
        urlInput = cloudGistUrl
        autoSync = autoSyncOnLaunch
        publicMode = isPublicMode
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero / Introduction card
        Card(
            colors = CardDefaults.cardColors(containerColor = cardBg),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Cloud Sync",
                        tint = accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "GitHub Gist Cloud Sync",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Manage your channels easily from the cloud! Host an M3U playlist file on GitHub Gist, copy the RAW url, and enter it below. The app will auto-pull channels on startup to stay in sync.",
                    fontSize = 12.sp,
                    color = Color.LightGray,
                    lineHeight = 16.sp
                )
            }
        }

        // Configuration Form Card
        Card(
            colors = CardDefaults.cardColors(containerColor = cardBg),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Sync Configurations",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color.White
                )

                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text("Gist Raw URL / Cloud M3U URL") },
                    placeholder = { Text("https://gist.githubusercontent.com/.../raw/playlist.m3u") },
                    singleLine = false,
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                        focusedContainerColor = Color(0xFF1C1B1F),
                        unfocusedContainerColor = Color(0xFF1C1B1F)
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("gist_url_input")
                )

                Text(
                    text = "Quick Presets:",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = { urlInput = "https://gist.github.com/shakilemon73/94df652949eec0ff174d34e0871aab42" },
                        label = { Text("My Gist", fontSize = 10.sp, color = Color.White) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = Color.White.copy(alpha = 0.08f)
                        ),
                        border = BorderStroke(1.dp, accentColor)
                    )
                    AssistChip(
                        onClick = { urlInput = "https://github.com/abusaeeidx/Mrgify-BDIX-IPTV/raw/main/playlist.m3u" },
                        label = { Text("BDIX IPTV", fontSize = 10.sp, color = Color.White) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = Color.White.copy(alpha = 0.05f)
                        ),
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f))
                    )
                    AssistChip(
                        onClick = { urlInput = "https://iptv-org.github.io/iptv/index.m3u" },
                        label = { Text("IPTV-Org", fontSize = 10.sp, color = Color.White) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = Color.White.copy(alpha = 0.05f)
                        ),
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f))
                    )
                    AssistChip(
                        onClick = { urlInput = "https://github.com/abusaeeidx/Mrgify-BDIX-IPTV/raw/main/playlist.m3u, https://iptv-org.github.io/iptv/index.m3u" },
                        label = { Text("Merge Both", fontSize = 10.sp, color = Color.White) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = Color.White.copy(alpha = 0.05f)
                        ),
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f))
                    )
                    AssistChip(
                        onClick = { urlInput = "https://github.com/doms9/iptv/raw/refs/heads/default/M3U8/base.m3u8" },
                        label = { Text("Doms9 Base", fontSize = 10.sp, color = Color.White) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = Color.White.copy(alpha = 0.05f)
                        ),
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f))
                    )
                    AssistChip(
                        onClick = { urlInput = "https://github.com/doms9/iptv/raw/refs/heads/default/M3U8/TV.m3u8" },
                        label = { Text("Doms9 US TV", fontSize = 10.sp, color = Color.White) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = Color.White.copy(alpha = 0.05f)
                        ),
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f))
                    )
                    AssistChip(
                        onClick = { urlInput = "https://github.com/doms9/iptv/raw/refs/heads/default/M3U8/events.m3u8" },
                        label = { Text("Doms9 Events", fontSize = 10.sp, color = Color.White) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = Color.White.copy(alpha = 0.05f)
                        ),
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f))
                    )
                    AssistChip(
                        onClick = { urlInput = "https://github.com/doms9/iptv/raw/refs/heads/default/M3U8/base.m3u8, https://github.com/doms9/iptv/raw/refs/heads/default/M3U8/TV.m3u8" },
                        label = { Text("Doms9 Merged (Base + TV)", fontSize = 10.sp, color = Color.White) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = Color.White.copy(alpha = 0.05f)
                        ),
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f))
                    )
                    AssistChip(
                        onClick = { urlInput = "https://github.com/doms9/iptv/raw/refs/heads/default/M3U8/events.m3u8, https://github.com/doms9/iptv/raw/refs/heads/default/M3U8/TV.m3u8" },
                        label = { Text("Doms9 Merged (Events + TV)", fontSize = 10.sp, color = Color.White) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = Color.White.copy(alpha = 0.05f)
                        ),
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f))
                    )
                }

                // Auto Sync On Launch Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-Sync on Startup",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Fetch and load playlist automatically when app launches",
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                    }
                    Switch(
                        checked = autoSync,
                        onCheckedChange = { autoSync = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = accentColor,
                            checkedTrackColor = accentColor.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.testTag("auto_sync_switch")
                    )
                }

                // Public App Mode Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Public Client Mode",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Locks settings and hides the settings button from the homepage to prevent public users from modifying configurations.",
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                    }
                    Switch(
                        checked = publicMode,
                        onCheckedChange = { publicMode = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = accentColor,
                            checkedTrackColor = accentColor.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.testTag("public_mode_switch")
                    )
                }

                if (publicMode) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF131215)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Tip",
                                tint = accentColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Admin Note: To reopen this Admin Screen in Public Mode, tap the \"Live Stream Hub\" logo 5 times on the homepage.",
                                fontSize = 9.sp,
                                color = Color.Gray,
                                lineHeight = 12.sp
                            )
                        }
                    }
                }

                // Action Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.updateCloudGistSettings(
                                url = urlInput.trim(),
                                autoSync = autoSync,
                                publicMode = publicMode
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentColor,
                            contentColor = onPurpleColor
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).testTag("save_cloud_sync_button")
                    ) {
                        Text("Save & Apply", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    if (cloudGistUrl.isNotBlank()) {
                        OutlinedButton(
                            onClick = { viewModel.clearCloudSyncSettings() },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFFE53935)
                            ),
                            border = BorderStroke(1.dp, Color(0xFFE53935).copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("clear_cloud_sync_button")
                        ) {
                            Text("Clear", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Live Sync Status Card
        if (cloudGistUrl.isNotBlank()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Sync Health & Status",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Active Cloud Source:",
                                fontSize = 10.sp,
                                color = Color.Gray
                            )
                            Text(
                                text = cloudGistUrl,
                                fontSize = 11.sp,
                                color = accentColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Divider(color = Color.White.copy(alpha = 0.05f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Last Successfully Synced:",
                                fontSize = 10.sp,
                                color = Color.Gray
                            )
                            val formattedTime = if (lastSyncTime > 0) {
                                val date = java.util.Date(lastSyncTime)
                                val format = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                                format.format(date)
                            } else {
                                "Never"
                            }
                            Text(
                                text = formattedTime,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                        }

                        Button(
                            onClick = { viewModel.syncWithCloudGist(force = true) },
                            enabled = !isLoading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.1f),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("sync_now_button")
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Sync Now", fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    syncStatusMessage?.let { status ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (status.contains("fail", ignoreCase = true)) {
                                    Color(0xFFE53935).copy(alpha = 0.15f)
                                } else {
                                    Color(0xFF4CAF50).copy(alpha = 0.15f)
                                }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (status.contains("fail", ignoreCase = true)) Icons.Default.Warning else Icons.Default.Check,
                                    contentDescription = null,
                                    tint = if (status.contains("fail", ignoreCase = true)) Color(0xFFE53935) else Color(0xFF4CAF50),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = status,
                                    fontSize = 11.sp,
                                    color = Color.White
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
fun DebugConsoleView(
    viewModel: ChannelViewModel,
    cardBg: Color,
    accentColor: Color,
    onPurpleColor: Color,
    modifier: Modifier = Modifier
) {
    val logs by com.example.data.StreamLogManager.logs.collectAsStateWithLifecycle()
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val context = androidx.compose.ui.platform.LocalContext.current

    var selectedFilter by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }

    val filteredLogs = remember(logs, selectedFilter, searchQuery) {
        logs.filter { log ->
            val matchesFilter = selectedFilter == "All" || log.type.equals(selectedFilter, ignoreCase = true)
            val matchesSearch = searchQuery.isBlank() ||
                    log.targetName.contains(searchQuery, ignoreCase = true) ||
                    log.url.contains(searchQuery, ignoreCase = true) ||
                    log.errorMessage.contains(searchQuery, ignoreCase = true)
            matchesFilter && matchesSearch
        }
    }

    val totalCount = logs.size
    val playbackCount = logs.count { it.type.equals("Playback", ignoreCase = true) }
    val verificationCount = logs.count { it.type.equals("Verification", ignoreCase = true) }
    val fetchCount = logs.count { it.type.equals("Playlist Fetch", ignoreCase = true) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search logs...", color = Color.Gray) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.Gray) },
                trailingIcon = if (searchQuery.isNotEmpty()) {
                    {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.Gray)
                        }
                    }
                } else null,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accentColor,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                    focusedLabelColor = accentColor,
                    unfocusedLabelColor = Color.Gray,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )

            Button(
                onClick = { com.example.data.StreamLogManager.clearLogs() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFBA1A1A),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                modifier = Modifier.testTag("clear_logs_button")
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Clear Logs", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Clear", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = cardBg),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                StatItem(label = "Total", value = totalCount.toString(), color = Color.White)
                StatItem(label = "Playback", value = playbackCount.toString(), color = Color(0xFFF2B8B5))
                StatItem(label = "Verify", value = verificationCount.toString(), color = Color(0xFFFFD494))
                StatItem(label = "Fetch", value = fetchCount.toString(), color = Color(0xFF94D4FF))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        val filters = listOf("All", "Playback", "Verification", "Playlist Fetch")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            filters.forEach { filter ->
                val isSelected = selectedFilter == filter
                InputChip(
                    selected = isSelected,
                    onClick = { selectedFilter = filter },
                    label = { Text(filter) },
                    colors = InputChipDefaults.inputChipColors(
                        selectedContainerColor = accentColor,
                        selectedLabelColor = onPurpleColor,
                        containerColor = cardBg,
                        labelColor = Color.White
                    ),
                    border = BorderStroke(1.dp, if (isSelected) accentColor else Color.White.copy(alpha = 0.12f))
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (filteredLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "No Logs",
                        tint = Color.Gray.copy(alpha = 0.5f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No stream error logs found", color = Color.Gray, fontSize = 14.sp)
                }
            }
        } else {
            val sdf = remember { java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()) }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredLogs, key = { it.id }) { log ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val (badgeColor, badgeText) = when (log.type.lowercase()) {
                                    "playback" -> Color(0xFFF2B8B5) to "PLAYBACK"
                                    "verification" -> Color(0xFFFFD494) to "VERIFY"
                                    "playlist fetch" -> Color(0xFF94D4FF) to "FETCH"
                                    else -> Color.Gray to log.type.uppercase()
                                }
                                Box(
                                    modifier = Modifier
                                        .background(badgeColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                        .border(1.dp, badgeColor.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = badgeText,
                                        color = badgeColor,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Text(
                                    text = sdf.format(java.util.Date(log.timestamp)),
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = log.targetName,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = log.errorMessage,
                                fontSize = 12.sp,
                                color = Color(0xFFF2B8B5),
                                fontWeight = FontWeight.Medium
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = log.url,
                                    fontSize = 11.sp,
                                    color = Color.Gray,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(
                                    onClick = {
                                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(log.url))
                                        android.widget.Toast.makeText(context, "URL Copied!", android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "Copy URL",
                                        tint = accentColor,
                                        modifier = Modifier.size(16.dp)
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

@Composable
fun StatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = color)
        Text(text = label, fontSize = 11.sp, color = Color.Gray)
    }
}
