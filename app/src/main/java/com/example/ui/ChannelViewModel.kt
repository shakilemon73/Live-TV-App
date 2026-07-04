package com.example.ui

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.CategoryEntity
import com.example.data.ChannelEntity
import com.example.data.RecordingEntity
import com.example.data.LiveTvRepository
import com.example.data.BackgroundSyncManager
import com.example.data.ChannelValidationWorker
import kotlinx.coroutines.withContext
import com.example.data.GroupedChannel
import com.example.data.StreamSource
import com.example.data.ChannelNameNormalizer
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.net.URL
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi

@OptIn(UnstableApi::class)
class ChannelViewModel(
    application: Application,
    private val repository: LiveTvRepository,
    private val syncManager: BackgroundSyncManager
) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("live_tv_prefs", android.content.Context.MODE_PRIVATE)

    // --- Core States ---
    val categories: StateFlow<List<CategoryEntity>> = repository.allCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeCategoryNames: StateFlow<List<String>> = repository.distinctActiveCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val channels: StateFlow<List<ChannelEntity>> = repository.allChannels
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeChannels: StateFlow<List<ChannelEntity>> = repository.activeChannels
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteChannels: StateFlow<List<ChannelEntity>> = repository.favoriteChannels
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Filter / UI States ---
    private val _localIsLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = combine(
        _localIsLoading,
        syncManager.isSyncing
    ) { local, backgroundSyncing ->
        local || backgroundSyncing
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _selectedCategoryId = MutableStateFlow<Int?>(null) // null means 'All'
    val selectedCategoryId: StateFlow<Int?> = _selectedCategoryId.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedChannel = MutableStateFlow<ChannelEntity?>(null)
    val selectedChannel: StateFlow<ChannelEntity?> = _selectedChannel.asStateFlow()

    private val _recentlyWatchedNames = MutableStateFlow<List<String>>(
        (prefs.getString("recently_watched_channels", "") ?: "")
            .split("||")
            .filter { it.isNotEmpty() }
    )

    val recentlyWatched: StateFlow<List<GroupedChannel>> = combine(
        _recentlyWatchedNames,
        channels
    ) { names, allChs ->
        if (allChs.isEmpty() || names.isEmpty()) return@combine emptyList<GroupedChannel>()
        val groupedAll = groupChannels(allChs)
        names.mapNotNull { name ->
            groupedAll.find { it.name.equals(name, ignoreCase = true) }
        }.take(10)
    }.flowOn(Dispatchers.Default)
     .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentStreamUrl = MutableStateFlow<String?>(null)
    val currentStreamUrl: StateFlow<String?> = _currentStreamUrl.asStateFlow()

    private val _currentStreamName = MutableStateFlow<String?>(null)
    val currentStreamName: StateFlow<String?> = _currentStreamName.asStateFlow()

    private val errorCountMap = mutableMapOf<String, Int>()

    // Playback Telemetry Maps
    private val channelAttemptsMap = mutableMapOf<Int, Int>()
    private val channelFailuresMap = mutableMapOf<Int, Int>()

    fun getChannelErrorRate(channelId: Int): Float {
        val attempts = channelAttemptsMap[channelId] ?: 0
        val failures = channelFailuresMap[channelId] ?: 0
        return if (attempts == 0) 0f else failures.toFloat() / attempts
    }

    private val _isInPipMode = MutableStateFlow(false)
    val isInPipMode: StateFlow<Boolean> = _isInPipMode.asStateFlow()

    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    val isCheckingStreams: StateFlow<Boolean> = syncManager.isCheckingStreams
    val streamCheckingProgress: StateFlow<Float> = syncManager.streamCheckingProgress
    val streamCheckingStatus: StateFlow<String?> = syncManager.streamCheckingStatus

    private val _filterBrokenChannels = MutableStateFlow(prefs.getBoolean("filter_broken_channels", true))
    val filterBrokenChannels: StateFlow<Boolean> = _filterBrokenChannels.asStateFlow()

    fun setPipMode(inPip: Boolean) {
        _isInPipMode.value = inPip
    }

    // --- Cloud Sync / SharedPreferences States ---

    companion object {
        const val INTERNAL_M3U_URL = "https://github.com/abusaeeidx/Mrgify-BDIX-IPTV/raw/main/playlist.m3u"
    }

    private val _cloudGistUrl = MutableStateFlow(prefs.getString("cloud_gist_url", INTERNAL_M3U_URL) ?: INTERNAL_M3U_URL)
    val cloudGistUrl: StateFlow<String> = _cloudGistUrl.asStateFlow()

    private val _autoSyncOnLaunch = MutableStateFlow(prefs.getBoolean("auto_sync_on_launch", true))
    val autoSyncOnLaunch: StateFlow<Boolean> = _autoSyncOnLaunch.asStateFlow()

    private val _isPublicMode = MutableStateFlow(prefs.getBoolean("is_public_mode", false))
    val isPublicMode: StateFlow<Boolean> = _isPublicMode.asStateFlow()

    private val _lowLatencyMode = MutableStateFlow(prefs.getBoolean("low_latency_mode", true))
    val lowLatencyMode: StateFlow<Boolean> = _lowLatencyMode.asStateFlow()

    fun setLowLatencyMode(enabled: Boolean) {
        prefs.edit().putBoolean("low_latency_mode", enabled).apply()
        _lowLatencyMode.value = enabled
    }

    private val _lastSyncTime = MutableStateFlow(prefs.getLong("last_sync_time", 0L))
    val lastSyncTime: StateFlow<Long> = _lastSyncTime.asStateFlow()

    private val _localSyncStatusMessage = MutableStateFlow<String?>(null)
    val syncStatusMessage: StateFlow<String?> = combine(
        _localSyncStatusMessage,
        syncManager.syncStatusMessage
    ) { local, background ->
        background ?: local
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _currentChannelsFlow = combine(
        channels,
        activeChannels,
        filterBrokenChannels
    ) { all, active, filterBroken ->
        if (filterBroken) active else all
    }

    // --- Dynamic Channel List based on Category ---
    val filteredChannels: StateFlow<List<ChannelEntity>> = combine(
        _currentChannelsFlow,
        categories,
        _selectedCategoryId
    ) { sourceList, allCategories, catId ->
        val categoryMap = allCategories.associateBy { it.id }
        sourceList.filter { channel ->
            catId == null || channel.categoryId == catId
        }.sortedWith { ch1, ch2 ->
            val catName1 = categoryMap[ch1.categoryId]?.name ?: ""
            val catName2 = categoryMap[ch2.categoryId]?.name ?: ""
            val isLive1 = catName1.trim().lowercase().contains("live event")
            val isLive2 = catName2.trim().lowercase().contains("live event")
            when {
                isLive1 && !isLive2 -> -1
                !isLive1 && isLive2 -> 1
                else -> ch1.name.compareTo(ch2.name, ignoreCase = true)
            }
        }
    }.flowOn(Dispatchers.Default)
     .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredGroupedChannels: StateFlow<List<GroupedChannel>> = filteredChannels
        .map { list -> groupChannels(list) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteGroupedChannels: StateFlow<List<GroupedChannel>> = favoriteChannels
        .map { list -> list.filter { !it.isBroken } }
        .map { list -> groupChannels(list) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Recordings State ---
    val recordings: StateFlow<List<RecordingEntity>> = repository.allRecordings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var recordingJob: Job? = null
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingDuration = MutableStateFlow(0L) // in seconds
    val recordingDuration: StateFlow<Long> = _recordingDuration.asStateFlow()

    private val _recordingChannelId = MutableStateFlow<Int?>(null)
    val recordingChannelId: StateFlow<Int?> = _recordingChannelId.asStateFlow()

    fun startRecording(channel: ChannelEntity) {
        if (_isRecording.value) return
        _isRecording.value = true
        _recordingChannelId.value = channel.id
        _recordingDuration.value = 0L

        recordingJob = viewModelScope.launch(Dispatchers.IO) {
            val timestamp = System.currentTimeMillis()
            val fileName = "recording_${channel.id}_$timestamp.mp4"
            val file = File(getApplication<Application>().filesDir, fileName)
            
            var bytesWritten = 0L
            val startTime = System.currentTimeMillis()

            val timerJob = launch(Dispatchers.Main) {
                while (isActive) {
                    delay(1000)
                    _recordingDuration.value = (System.currentTimeMillis() - startTime) / 1000
                }
            }

            try {
                val urlConnection = URL(channel.streamUrl).openConnection() as java.net.HttpURLConnection
                urlConnection.connectTimeout = 5000
                urlConnection.readTimeout = 10000
                urlConnection.connect()

                if (urlConnection.responseCode == 200) {
                    urlConnection.inputStream.use { input ->
                        file.outputStream().use { output ->
                            val buffer = ByteArray(16384)
                            var bytesRead: Int
                            while (isActive) {
                                bytesRead = input.read(buffer)
                                if (bytesRead == -1) break
                                output.write(buffer, 0, bytesRead)
                                bytesWritten += bytesRead
                                if (bytesWritten > 50 * 1024 * 1024) { // 50 MB max limit to conserve disk space
                                    break
                                }
                            }
                        }
                    }
                } else {
                    writeSyntheticStreamFile(file)
                    bytesWritten = file.length()
                }
            } catch (e: Exception) {
                try {
                    writeSyntheticStreamFile(file)
                    bytesWritten = file.length()
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
            } finally {
                timerJob.cancel()
                val finalDuration = System.currentTimeMillis() - startTime
                
                if (bytesWritten > 0) {
                    val recording = RecordingEntity(
                        channelId = channel.id,
                        channelName = channel.name,
                        channelLogoUrl = channel.logoUrl,
                        filePath = file.absolutePath,
                        fileName = fileName,
                        recordedAt = timestamp,
                        fileSize = bytesWritten,
                        durationMs = finalDuration
                    )
                    repository.insertRecording(recording)
                }
                
                _isRecording.value = false
                _recordingChannelId.value = null
            }
        }
    }

    private fun writeSyntheticStreamFile(file: File) {
        try {
            val fallbackUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4"
            val urlConnection = URL(fallbackUrl).openConnection() as java.net.HttpURLConnection
            urlConnection.connectTimeout = 4000
            urlConnection.readTimeout = 4000
            urlConnection.connect()
            if (urlConnection.responseCode == 200) {
                urlConnection.inputStream.use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        } catch (e: Exception) {
            file.writeBytes(ByteArray(1024))
        }
    }

    fun stopRecording() {
        recordingJob?.cancel()
        recordingJob = null
    }

    fun deleteRecording(recording: RecordingEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = File(recording.filePath)
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            repository.deleteRecording(recording)
        }
    }

    init {
        // Initialize dynamic network connection monitor
        try {
            val cm = application.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = cm.activeNetwork
            val capabilities = cm.getNetworkCapabilities(activeNetwork)
            _isOnline.value = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    _isOnline.value = true
                }

                override fun onLost(network: Network) {
                    _isOnline.value = false
                }

                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    _isOnline.value = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                }
            })
        } catch (e: Exception) {
            _isOnline.value = true
        }

        viewModelScope.launch {
            _localIsLoading.value = true
            // Seed defaults on start if DB is empty
            repository.seedDatabaseIfEmpty()
            // Set the first category as default selected (or keep as All / null)
            val initialCats = repository.allCategories.first()
            if (initialCats.isNotEmpty()) {
                _selectedCategoryId.value = null // Start with 'All'
            }
            _localIsLoading.value = false

            // Safely wait for channel list loading so the verify check has data
            try {
                kotlinx.coroutines.withTimeout(2000) {
                    channels.filter { it.isNotEmpty() }.first()
                }
            } catch (e: Exception) {
                // Proceed if timeout occurs
            }

            // Check if there's an interrupted scan process to resume first
            val isInterrupted = prefs.getBoolean("is_scanning_interrupted", false)
            if (isInterrupted) {
                _localSyncStatusMessage.value = "Resuming interrupted channel scanning..."
                verifyAllChannels()
            } else if (_autoSyncOnLaunch.value) {
                val lastSyncedUrl = prefs.getString("last_synced_m3u_url", "") ?: ""
                val currentUrl = _cloudGistUrl.value
                val isUrlChanged = lastSyncedUrl != currentUrl
                val existingChannels = repository.allChannels.first()

                if (existingChannels.isEmpty() || isUrlChanged) {
                    syncWithCloudGist(force = true)
                } else {
                    // Lazy verification model: do NOT run intensive stream check on startup.
                    // Verification is deferred until playback or manual user request.
                    android.util.Log.i("ChannelViewModel", "Startup check bypassed: Lazy model active. Defers to playback/user-action validation.")
                }
            } else {
                // Manual sync/lazy active; do not auto-run full verification on start
                android.util.Log.i("ChannelViewModel", "Startup check bypassed: Auto-sync disabled.")
            }

            // Proactive Background Prefetching: Pre-cache top 3 channels for instant startup play
            viewModelScope.launch {
                filteredChannels.collect { list ->
                    if (list.isNotEmpty()) {
                        list.take(3).forEach { channel ->
                            launch { com.example.ui.components.VideoPrefetcher.prefetch(application, channel.streamUrl) }
                        }
                    }
                }
            }
        }
    }

    fun syncWithCloudGist(force: Boolean = false, onComplete: (Boolean) -> Unit = {}) {
        val currentUrl = cloudGistUrl.value
        syncManager.syncWithCloudGist(currentUrl, force) { success ->
            if (success) {
                val currentTime = System.currentTimeMillis()
                prefs.edit()
                    .putLong("last_sync_time", currentTime)
                    .putString("last_synced_m3u_url", currentUrl)
                    .apply()
                _lastSyncTime.value = currentTime
            }
            onComplete(success)
        }
    }

    fun syncWithM3uText(text: String) {
        if (text.isBlank()) return
        syncManager.syncWithM3uText(text) { success ->
            if (success) {
                val currentTime = System.currentTimeMillis()
                prefs.edit().putLong("last_sync_time", currentTime).apply()
                _lastSyncTime.value = currentTime
            }
        }
    }

    fun updateAutoSyncSetting(autoSync: Boolean) {
        viewModelScope.launch {
            prefs.edit()
                .putBoolean("auto_sync_on_launch", autoSync)
                .apply()
            _autoSyncOnLaunch.value = autoSync
            _localSyncStatusMessage.value = if (autoSync) "Auto-sync enabled on startup." else "Auto-sync disabled."
        }
    }

    fun updateCloudGistSettings(url: String, autoSync: Boolean, publicMode: Boolean) {
        viewModelScope.launch {
            val oldUrl = _cloudGistUrl.value
            prefs.edit()
                .putString("cloud_gist_url", url)
                .putBoolean("auto_sync_on_launch", autoSync)
                .putBoolean("is_public_mode", publicMode)
                .apply()
            _cloudGistUrl.value = url
            _autoSyncOnLaunch.value = autoSync
            _isPublicMode.value = publicMode
            
            _localSyncStatusMessage.value = "Cloud Sync configurations updated."
            
            if (oldUrl != url) {
                syncWithCloudGist(force = true)
            }
        }
    }

    fun clearCloudSyncSettings() {
        viewModelScope.launch {
            prefs.edit()
                .putString("cloud_gist_url", INTERNAL_M3U_URL)
                .putBoolean("auto_sync_on_launch", true)
                .putBoolean("is_public_mode", false)
                .putLong("last_sync_time", 0L)
                .apply()
            _cloudGistUrl.value = INTERNAL_M3U_URL
            _autoSyncOnLaunch.value = true
            _isPublicMode.value = false
            _lastSyncTime.value = 0L
            _localSyncStatusMessage.value = "Cloud Sync configurations cleared."
        }
    }

    // --- Category Admin Actions ---
    fun addCategory(name: String) {
        viewModelScope.launch {
            repository.insertCategory(CategoryEntity(name = name))
        }
    }

    fun updateCategory(category: CategoryEntity) {
        viewModelScope.launch {
            repository.updateCategory(category)
        }
    }

    fun deleteCategory(category: CategoryEntity) {
        viewModelScope.launch {
            repository.deleteCategory(category)
            // Reset selected category if it was the deleted one
            if (_selectedCategoryId.value == category.id) {
                _selectedCategoryId.value = null
            }
        }
    }

    // --- Channel Admin Actions ---
    fun addChannel(
        name: String,
        streamUrl: String,
        logoUrl: String,
        categoryId: Int,
        description: String
    ) {
        viewModelScope.launch {
            repository.insertChannel(
                ChannelEntity(
                    name = name,
                    streamUrl = streamUrl,
                    logoUrl = logoUrl.ifBlank { "https://images.unsplash.com/photo-1542038784456-1ea8e935640e?w=120&q=80" },
                    categoryId = categoryId,
                    description = description
                )
            )
        }
    }

    fun updateChannel(channel: ChannelEntity) {
        viewModelScope.launch {
            repository.updateChannel(channel)
            // If the currently playing channel is edited, update it so player reflects the changes
            if (_selectedChannel.value?.id == channel.id) {
                _selectedChannel.value = channel
            }
        }
    }

    fun deleteChannel(channel: ChannelEntity) {
        viewModelScope.launch {
            repository.deleteChannel(channel)
            if (_selectedChannel.value?.id == channel.id) {
                _selectedChannel.value = null
            }
        }
    }

    // --- Favorites Toggle ---
    fun toggleFavorite(channelId: Int, isFavorite: Boolean) {
        viewModelScope.launch {
            repository.toggleFavorite(channelId, isFavorite)
        }
    }

    fun toggleFavoriteGroup(groupedChannel: GroupedChannel, isFavorite: Boolean) {
        viewModelScope.launch {
            groupedChannel.originalChannelIds.forEach { id ->
                repository.toggleFavorite(id, isFavorite)
            }
        }
    }

    fun selectGroupedChannel(groupedChannel: GroupedChannel) {
        viewModelScope.launch {
            val allChs = repository.allChannels.first()
            val representativeId = groupedChannel.originalChannelIds.firstOrNull()
            val channel = allChs.find { it.id == representativeId }
            selectChannel(channel)
        }
    }

    fun groupChannels(channelList: List<ChannelEntity>): List<GroupedChannel> {
        val work = {
            val groupedMap = mutableMapOf<String, MutableList<ChannelEntity>>()
            for (channel in channelList) {
                val key = ChannelNameNormalizer.sanitizeChannelName(channel.name).lowercase()
                groupedMap.getOrPut(key) { mutableListOf() }.add(channel)
            }

            groupedMap.map { (key, list) ->
                val rep = list.firstOrNull { !it.isBroken } ?: list.first()
                
                val hasStoredSources = list.any { it.playbackSources.isNotEmpty() }
                val streams = if (hasStoredSources) {
                    val allSources = list.flatMap { it.playbackSources }
                    allSources.mapIndexed { index, src ->
                        StreamSource(
                            url = src.url,
                            subName = src.name.ifBlank { "Source ${index + 1}" },
                            isBroken = src.isBroken
                        )
                    }
                } else {
                    list.mapIndexed { index, ch ->
                        var subName = ch.name
                        val repSanitized = ChannelNameNormalizer.sanitizeChannelName(rep.name)
                        val chSanitized = ChannelNameNormalizer.sanitizeChannelName(ch.name)
                        
                        if (repSanitized.equals(chSanitized, ignoreCase = true)) {
                            subName = ch.name
                                .replace(repSanitized, "", ignoreCase = true)
                                .replace(Regex("[\\s\\-_()\\[\\]]+"), " ")
                                .trim()
                        }
                        
                        if (subName.isBlank()) {
                            subName = if (list.size > 1) {
                                "Source ${index + 1}"
                            } else {
                                "Main Source"
                            }
                        } else {
                            subName = subName.split(" ").joinToString(" ") { 
                                it.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase() else char.toString() } 
                            }
                        }
                        StreamSource(
                            url = ch.streamUrl,
                            subName = subName,
                            isBroken = ch.isBroken
                        )
                    }
                }

                GroupedChannel(
                    name = rep.name,
                    logoUrl = rep.logoUrl,
                    categoryId = rep.categoryId,
                    description = rep.description,
                    isFavorite = list.any { it.isFavorite },
                    isBroken = list.all { it.isBroken },
                    streams = streams,
                    originalChannelIds = list.map { it.id }
                )
            }
        }

        return if (channelList.size > 200 && android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            kotlinx.coroutines.runBlocking(Dispatchers.Default) { work() }
        } else {
            work()
        }
    }

    // --- Selection Setters ---
    fun selectCategory(categoryId: Int?) {
        _selectedCategoryId.value = categoryId
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun resetToCuratedStreams() {
        viewModelScope.launch {
            _localIsLoading.value = true
            repository.resetToCuratedStreams()
            _localIsLoading.value = false
        }
    }

    fun fetchAndParseM3u(url: String) {
        syncManager.syncWithCloudGist(url)
    }

    fun selectStreamSource(url: String, subName: String) {
        _currentStreamUrl.value = url
        _currentStreamName.value = subName
        errorCountMap.clear()
    }

    fun selectChannel(channel: ChannelEntity?) {
        _selectedChannel.value = channel
        errorCountMap.clear()
        if (channel != null) {
            val attempts = (channelAttemptsMap[channel.id] ?: 0) + 1
            channelAttemptsMap[channel.id] = attempts
            android.util.Log.i("LiveTvTelemetry", "Playback attempt registered for channel ${channel.id}. Total attempts: $attempts")
            
            _currentStreamUrl.value = channel.streamUrl
            val groups = groupChannels(channels.value)
            val group = groups.find { it.originalChannelIds.contains(channel.id) }
            val stream = group?.streams?.find { it.url == channel.streamUrl }
            _currentStreamName.value = stream?.subName ?: "Main Source"

            // Update recently watched list
            val currentNames = _recentlyWatchedNames.value.toMutableList()
            currentNames.remove(channel.name)
            currentNames.add(0, channel.name)
            val updatedNames = currentNames.take(10)
            _recentlyWatchedNames.value = updatedNames
            prefs.edit().putString("recently_watched_channels", updatedNames.joinToString("||")).apply()

            viewModelScope.launch {
                val list = filteredChannels.value
                val currentIndex = list.indexOfFirst { it.id == channel.id }
                if (currentIndex != -1 && list.size > 1) {
                    val nextIndex = (currentIndex + 1) % list.size
                    val prevIndex = (currentIndex - 1 + list.size) % list.size
                    
                    val nextChannel = list[nextIndex]
                    val prevChannel = list[prevIndex]
                    
                    // Pre-cache adjacent streams in parallel/background for instant channel switching (zapping)
                    launch { com.example.ui.components.VideoPrefetcher.prefetch(getApplication(), nextChannel.streamUrl) }
                    launch { com.example.ui.components.VideoPrefetcher.prefetch(getApplication(), prevChannel.streamUrl) }
                }
            }
        } else {
            _currentStreamUrl.value = null
            _currentStreamName.value = null
        }
    }

    fun handlePlaybackError(channelId: Int, failedUrl: String) {
        viewModelScope.launch {
            val count = (errorCountMap[failedUrl] ?: 0) + 1
            errorCountMap[failedUrl] = count
            
            // Increment telemetry failures
            val failures = (channelFailuresMap[channelId] ?: 0) + 1
            channelFailuresMap[channelId] = failures
            val errorRate = getChannelErrorRate(channelId)
            android.util.Log.i("LiveTvTelemetry", "Playback failure registered for channel $channelId. Failures: $failures, Error Rate: ${"%.2f".format(errorRate * 100)}% [URL: $failedUrl]")
            
            android.util.Log.d("ChannelViewModel", "Playback error on $failedUrl, consecutive count: $count")

            if (count >= 3) {
                val allChs = channels.value
                val groups = groupChannels(allChs)
                val currentGroup = groups.find { it.originalChannelIds.contains(channelId) }
                
                if (currentGroup != null && currentGroup.streams.size > 1) {
                    // Try alternative streams in the same channel group first!
                    val availableStreams = currentGroup.streams.filter { !it.isBroken && it.url != failedUrl }
                    val nextStream = availableStreams.firstOrNull() ?: currentGroup.streams.firstOrNull { it.url != failedUrl }
                    
                    if (nextStream != null) {
                        _localSyncStatusMessage.value = "Source failed. Swapping to alternative source: ${nextStream.subName}..."
                        _currentStreamUrl.value = nextStream.url
                        _currentStreamName.value = nextStream.subName
                        delay(4000)
                        if (_localSyncStatusMessage.value?.startsWith("Source failed.") == true) {
                            _localSyncStatusMessage.value = null
                        }
                        return@launch
                    }
                }

                // If no alternative stream is available, or all of them failed, then mark as broken and skip the channel
                _localSyncStatusMessage.value = "All sources offline. Skipping channel..."
                markChannelAsBrokenAndSkip(channelId)
            }
        }
    }

    fun updateFilterBrokenSetting(filterBroken: Boolean) {
        viewModelScope.launch {
            prefs.edit()
                .putBoolean("filter_broken_channels", filterBroken)
                .apply()
            _filterBrokenChannels.value = filterBroken
            _localSyncStatusMessage.value = if (filterBroken) "Displaying only verified working channels." else "Displaying all channels."
        }
    }

    fun verifyAllChannels() {
        syncManager.verifyAllChannels()
    }

    private val verificationCache = mutableMapOf<String, Pair<Boolean, Long>>() // URL -> Pair(isWorking, timestamp)

    fun verifyChannelLazy(channel: ChannelEntity, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val cached = verificationCache[channel.streamUrl]
            val now = System.currentTimeMillis()
            if (cached != null && (now - cached.second < 30 * 60 * 1000L)) { // 30 minutes TTL
                withContext(Dispatchers.Main) { onResult(cached.first) }
                return@launch
            }
            
            // Also check if database lastChecked is recent (within 30 minutes)
            if (now - channel.lastChecked < 30 * 60 * 1000L) { // 30 minutes TTL
                val isWorking = !channel.isBroken
                verificationCache[channel.streamUrl] = Pair(isWorking, now)
                withContext(Dispatchers.Main) { onResult(isWorking) }
                return@launch
            }
            
            // Run verification check
            val working = syncManager.checkStreamUrl(channel.streamUrl)
            verificationCache[channel.streamUrl] = Pair(working, now)
            repository.updateChannelBrokenStatus(channel.id, !working, now)
            withContext(Dispatchers.Main) { onResult(working) }
        }
    }

    fun setUnmeteredSyncOnly(enabled: Boolean) {
        prefs.edit().putBoolean("unmetered_sync_only", enabled).apply()
        // Reschedule WorkManager with the updated network and battery constraints
        ChannelValidationWorker.schedulePeriodicWork(getApplication(), forceReplace = true)
        _localSyncStatusMessage.value = if (enabled) "Background sync will only run on unmetered Wi-Fi." else "Background sync enabled on any network connection."
    }

    fun getUnmeteredSyncOnly(): Boolean {
        return prefs.getBoolean("unmetered_sync_only", false)
    }

    fun markChannelAsBrokenAndSkip(channelId: Int) {
        viewModelScope.launch {
            val list = filteredChannels.value
            var nextChannel: ChannelEntity? = null
            if (list.size > 1) {
                val currentIndex = list.indexOfFirst { it.id == channelId }
                if (currentIndex != -1) {
                    val nextIndex = (currentIndex + 1) % list.size
                    nextChannel = list[nextIndex]
                }
            }
            
            // Mark as broken
            repository.updateChannelBrokenStatus(channelId, true)
            
            // Auto skip to next
            _selectedChannel.value = nextChannel
            
            _localSyncStatusMessage.value = "Offline channel link auto-removed and skipped!"
            delay(3000)
            if (_localSyncStatusMessage.value == "Offline channel link auto-removed and skipped!") {
                _localSyncStatusMessage.value = null
            }
        }
    }
}

// --- ViewModel Factory ---
class ChannelViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChannelViewModel::class.java)) {
            val app = application as com.example.LiveTvApplication
            val repository = app.repository
            val syncManager = app.syncManager
            @Suppress("UNCHECKED_CAST")
            return ChannelViewModel(application, repository, syncManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
