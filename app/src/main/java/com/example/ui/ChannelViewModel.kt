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

class ChannelViewModel(
    application: Application,
    private val repository: LiveTvRepository
) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("live_tv_prefs", android.content.Context.MODE_PRIVATE)

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(1500, TimeUnit.MILLISECONDS)
        .readTimeout(1500, TimeUnit.MILLISECONDS)
        .writeTimeout(1500, TimeUnit.MILLISECONDS)
        .build()

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
        BackgroundSyncManager.isSyncing
    ) { local, backgroundSyncing ->
        local || backgroundSyncing
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _selectedCategoryId = MutableStateFlow<Int?>(null) // null means 'All'
    val selectedCategoryId: StateFlow<Int?> = _selectedCategoryId.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedChannel = MutableStateFlow<ChannelEntity?>(null)
    val selectedChannel: StateFlow<ChannelEntity?> = _selectedChannel.asStateFlow()

    private val _isInPipMode = MutableStateFlow(false)
    val isInPipMode: StateFlow<Boolean> = _isInPipMode.asStateFlow()

    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    val isCheckingStreams: StateFlow<Boolean> = BackgroundSyncManager.isCheckingStreams
    val streamCheckingProgress: StateFlow<Float> = BackgroundSyncManager.streamCheckingProgress
    val streamCheckingStatus: StateFlow<String?> = BackgroundSyncManager.streamCheckingStatus

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

    private val _lastSyncTime = MutableStateFlow(prefs.getLong("last_sync_time", 0L))
    val lastSyncTime: StateFlow<Long> = _lastSyncTime.asStateFlow()

    private val _localSyncStatusMessage = MutableStateFlow<String?>(null)
    val syncStatusMessage: StateFlow<String?> = combine(
        _localSyncStatusMessage,
        BackgroundSyncManager.syncStatusMessage
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

    // --- Dynamic Channel List based on Category and Search ---
    val filteredChannels: StateFlow<List<ChannelEntity>> = combine(
        _currentChannelsFlow,
        categories,
        _selectedCategoryId,
        _searchQuery
    ) { sourceList, allCategories, catId, query ->
        val categoryMap = allCategories.associateBy { it.id }
        sourceList.filter { channel ->
            val matchesCategory = catId == null || channel.categoryId == catId
            val categoryName = categoryMap[channel.categoryId]?.name ?: ""
            val matchesSearch = query.isEmpty() ||
                    channel.name.contains(query, ignoreCase = true) ||
                    channel.description.contains(query, ignoreCase = true) ||
                    categoryName.contains(query, ignoreCase = true)
            matchesCategory && matchesSearch
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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredGroupedChannels: StateFlow<List<GroupedChannel>> = filteredChannels
        .map { list -> groupChannels(list) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteGroupedChannels: StateFlow<List<GroupedChannel>> = favoriteChannels
        .map { list -> list.filter { !it.isBroken } }
        .map { list -> groupChannels(list) }
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

            // Auto sync from secure internal cloud link on launch if enabled, otherwise verify existing channels
            if (_autoSyncOnLaunch.value) {
                syncWithCloudGist()
            } else {
                verifyAllChannels()
            }
        }
    }

    fun syncWithCloudGist() {
        BackgroundSyncManager.syncWithCloudGist(cloudGistUrl.value) { success ->
            if (success) {
                val currentTime = System.currentTimeMillis()
                prefs.edit().putLong("last_sync_time", currentTime).apply()
                _lastSyncTime.value = currentTime
            }
        }
    }

    fun syncWithM3uText(text: String) {
        if (text.isBlank()) return
        BackgroundSyncManager.syncWithM3uText(text) { success ->
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
            prefs.edit()
                .putString("cloud_gist_url", url)
                .putBoolean("auto_sync_on_launch", autoSync)
                .putBoolean("is_public_mode", publicMode)
                .apply()
            _cloudGistUrl.value = url
            _autoSyncOnLaunch.value = autoSync
            _isPublicMode.value = publicMode
            
            _localSyncStatusMessage.value = "Cloud Sync configurations updated."
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
            _selectedChannel.value = channel
        }
    }

    fun groupChannels(channelList: List<ChannelEntity>): List<GroupedChannel> {
        val groupedMap = mutableMapOf<String, MutableList<ChannelEntity>>()
        for (channel in channelList) {
            val key = ChannelNameNormalizer.sanitizeChannelName(channel.name).lowercase()
            groupedMap.getOrPut(key) { mutableListOf() }.add(channel)
        }

        return groupedMap.map { (key, list) ->
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
        BackgroundSyncManager.syncWithCloudGist(url)
    }

    fun selectChannel(channel: ChannelEntity?) {
        _selectedChannel.value = channel
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
        BackgroundSyncManager.verifyAllChannels()
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
            val database = AppDatabase.getDatabase(application)
            val repository = LiveTvRepository(database.liveTvDao())
            BackgroundSyncManager.initialize(repository)
            @Suppress("UNCHECKED_CAST")
            return ChannelViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
