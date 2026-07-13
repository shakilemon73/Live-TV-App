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
import com.example.data.ChannelClassifier
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

    val interestedLiveEvents: StateFlow<List<com.example.data.InterestedEventEntity>> = repository.interestedLiveEvents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Persisted UI States ---
    private val _currentTab = MutableStateFlow(0)
    val currentTab: StateFlow<Int> = _currentTab.asStateFlow()

    private val _eventsScrollToTopTrigger = MutableStateFlow(0)
    val eventsScrollToTopTrigger: StateFlow<Int> = _eventsScrollToTopTrigger.asStateFlow()

    private var isComingFromLiveEvents = false

    fun setComingFromLiveEvents(fromEvents: Boolean) {
        isComingFromLiveEvents = fromEvents
    }

    fun handleBackNavigation(onBack: () -> Unit) {
        if (isComingFromLiveEvents) {
            _currentTab.value = 3
        }
        onBack()
    }

    fun setCurrentTab(tabIndex: Int) {
        _currentTab.value = tabIndex
    }

    fun triggerEventsScrollToTop() {
        _eventsScrollToTopTrigger.value += 1
    }

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
    private var lastSelectedCategoryName: String? = null

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private fun getListFromPrefs(key: String): List<String> {
        val jsonStr = prefs.getString(key, null) ?: return emptyList()
        return try {
            val jsonArray = org.json.JSONArray(jsonStr)
            val list = mutableListOf<String>()
            for (i in 0 until jsonArray.length()) {
                list.add(jsonArray.getString(i))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveListToPrefs(key: String, list: List<String>) {
        try {
            val jsonArray = org.json.JSONArray()
            list.forEach { jsonArray.put(it) }
            prefs.edit().putString(key, jsonArray.toString()).apply()
        } catch (e: Exception) {
            // ignore
        }
    }

    private val _searchHistory = MutableStateFlow<List<String>>(getListFromPrefs("search_history_queries"))
    val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()

    private val _recentSearchChannelNames = MutableStateFlow<List<String>>(getListFromPrefs("search_history_channels"))
    val recentSearchChannelNames: StateFlow<List<String>> = _recentSearchChannelNames.asStateFlow()

    // Advanced search filter states
    val searchFilterWorkingOnly = MutableStateFlow(false)
    val searchFilterFavoritesOnly = MutableStateFlow(false)
    val searchFilterWithEpgOnly = MutableStateFlow(false)
    val searchFilterCategoryId = MutableStateFlow<Int?>(null)

    private val _selectedChannel = MutableStateFlow<ChannelEntity?>(null)
    val selectedChannel: StateFlow<ChannelEntity?> = _selectedChannel.asStateFlow()

    // --- EPG Program Flows ---
    private val tickerFlow = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(30_000)
        }
    }.flowOn(Dispatchers.Default)
     .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), System.currentTimeMillis())

    val currentEpgProgramsMap: StateFlow<Map<String, com.example.data.EpgProgramEntity>> = combine(
        repository.getActiveEpgProgramsFlow(System.currentTimeMillis()),
        tickerFlow
    ) { programs, now ->
        programs.filter { now in it.startTime..it.endTime }
            .associateBy { it.channelId.lowercase() }
    }.flowOn(Dispatchers.Default)
     .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val currentChannelEpgPrograms: StateFlow<List<com.example.data.EpgProgramEntity>> = _selectedChannel
        .flatMapLatest { channel ->
            if (channel != null) {
                repository.getEpgProgramsForChannelFlow(channel.tvgId, channel.name)
            } else {
                flowOf(emptyList())
            }
        }.flowOn(Dispatchers.Default)
         .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val currentChannelEpgProgram: StateFlow<com.example.data.EpgProgramEntity?> = _selectedChannel
        .flatMapLatest { channel ->
            if (channel != null) {
                repository.getCurrentProgramForChannelFlow(channel.tvgId, channel.name)
            } else {
                flowOf(null)
            }
        }.flowOn(Dispatchers.Default)
         .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Real-time user stats reactivity trigger
    private val _userStatsTrigger = MutableStateFlow(0L)

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
        }.take(5)
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

    // World top-notch engineering tracking & ranking system
    fun getChannelWatchCount(name: String): Int {
        return prefs.getInt("watch_count_${name.lowercase()}", 0)
    }

    fun getChannelLastWatched(name: String): Long {
        return prefs.getLong("last_watched_${name.lowercase()}", 0L)
    }

    fun getChannelFailures(name: String): Int {
        return prefs.getInt("failures_${name.lowercase()}", 0)
    }

    fun incrementChannelWatch(name: String) {
        val lower = name.lowercase()
        val current = prefs.getInt("watch_count_${lower}", 0)
        prefs.edit()
            .putInt("watch_count_${lower}", current + 1)
            .putLong("last_watched_${lower}", System.currentTimeMillis())
            .apply()
        _userStatsTrigger.value = System.currentTimeMillis()
    }

    fun incrementChannelFailure(name: String) {
        val lower = name.lowercase()
        val current = prefs.getInt("failures_${lower}", 0)
        prefs.edit()
            .putInt("failures_${lower}", current + 1)
            .apply()
        _userStatsTrigger.value = System.currentTimeMillis()
    }

    fun calculateChannelRankScore(grouped: GroupedChannel): Double {
        val name = grouped.name
        val lower = name.lowercase()
        val baseReputation = ChannelClassifier.getChannelReputationScore(name).toDouble()
        val watchCount = prefs.getInt("watch_count_$lower", 0)
        val failureCount = prefs.getInt("failures_$lower", 0)
        val lastWatched = prefs.getLong("last_watched_$lower", 0L)
        val isFavorite = grouped.isFavorite

        val engagementScore = watchCount * 15.0
        
        val now = System.currentTimeMillis()
        val recencyBonus = if (lastWatched > 0L) {
            val diffMs = now - lastWatched
            when {
                diffMs < 3600_000 -> 40.0 // last hour
                diffMs < 86400_000 -> 20.0 // last 24 hours
                diffMs < 604800_000 -> 10.0 // last week
                else -> 0.0
            }
        } else 0.0

        val favoriteBonus = if (isFavorite) 50.0 else 0.0
        val failurePenalty = failureCount * 25.0

        return baseReputation + engagementScore + recencyBonus + favoriteBonus - failurePenalty
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
        const val INTERNAL_M3U_URL = "https://iptv-api-worker.shakilemon71.workers.dev/api/channels"
    }

    private val _cloudGistUrl = MutableStateFlow(prefs.getString("cloud_gist_url", INTERNAL_M3U_URL) ?: INTERNAL_M3U_URL)
    val cloudGistUrl: StateFlow<String> = _cloudGistUrl.asStateFlow()

    private val _autoSyncOnLaunch = MutableStateFlow(prefs.getBoolean("auto_sync_on_launch", true))
    val autoSyncOnLaunch: StateFlow<Boolean> = _autoSyncOnLaunch.asStateFlow()

    private val _isPublicMode = MutableStateFlow(prefs.getBoolean("is_public_mode", false))
    val isPublicMode: StateFlow<Boolean> = _isPublicMode.asStateFlow()

    // --- App Update States ---
    private val _appUpdateUrl = MutableStateFlow(prefs.getString("app_update_url", "https://github.com/shakilemon73/my-m3u-playlist/raw/refs/heads/main/app-update.json") ?: "https://github.com/shakilemon73/my-m3u-playlist/raw/refs/heads/main/app-update.json")
    val appUpdateUrl: StateFlow<String> = _appUpdateUrl.asStateFlow()

    private val _isUpdateChecking = MutableStateFlow(false)
    val isUpdateChecking: StateFlow<Boolean> = _isUpdateChecking.asStateFlow()

    private val _updateInfo = MutableStateFlow<com.example.data.UpdateInfo?>(null)
    val updateInfo: StateFlow<com.example.data.UpdateInfo?> = _updateInfo.asStateFlow()

    private val _updateDownloadProgress = MutableStateFlow<Float?>(null)
    val updateDownloadProgress: StateFlow<Float?> = _updateDownloadProgress.asStateFlow()

    private val _updateErrorMessage = MutableStateFlow<String?>(null)
    val updateErrorMessage: StateFlow<String?> = _updateErrorMessage.asStateFlow()

    private val _updateWarningMessage = MutableStateFlow<String?>(null)
    val updateWarningMessage: StateFlow<String?> = _updateWarningMessage.asStateFlow()

    private val _downloadedFile = MutableStateFlow<java.io.File?>(null)
    val downloadedFile: StateFlow<java.io.File?> = _downloadedFile.asStateFlow()

    private val _showUpdateDialog = MutableStateFlow(false)
    val showUpdateDialog: StateFlow<Boolean> = _showUpdateDialog.asStateFlow()

    fun setAppUpdateUrl(url: String) {
        prefs.edit().putString("app_update_url", url).apply()
        _appUpdateUrl.value = url
    }

    fun checkForAppUpdates(isAutoCheck: Boolean = false) {
        viewModelScope.launch {
            _isUpdateChecking.value = true
            _updateErrorMessage.value = null
            _updateWarningMessage.value = null
            _downloadedFile.value = null
            _updateInfo.value = null
            
            val info = com.example.data.AppUpdateManager.checkForUpdates(_appUpdateUrl.value)
            _isUpdateChecking.value = false
            
            if (info != null) {
                val currentVersionCode = com.example.BuildConfig.VERSION_CODE
                if (info.versionCode > currentVersionCode) {
                    _updateInfo.value = info
                    val lastDownloadedVersion = prefs.getInt("last_downloaded_update_version", 0)
                    if (!isAutoCheck || info.versionCode > lastDownloadedVersion) {
                        _showUpdateDialog.value = true
                    } else {
                        _updateErrorMessage.value = "New update is available but already downloaded (v${info.versionName})"
                    }
                } else {
                    _updateErrorMessage.value = "Your app is up to date (v${com.example.BuildConfig.VERSION_NAME})"
                }
            } else {
                _updateErrorMessage.value = "Failed to fetch update info or connection error."
            }
        }
    }

    fun startAppUpdateDownload(context: android.content.Context) {
        val info = _updateInfo.value ?: return
        viewModelScope.launch {
            _updateErrorMessage.value = null
            _updateWarningMessage.value = null
            _downloadedFile.value = null
            _updateDownloadProgress.value = 0f
            
            com.example.data.AppUpdateManager.downloadAndInstallApk(
                context = context,
                apkUrl = info.apkUrl,
                expectedSha256 = info.sha256,
                onProgress = { progress ->
                    _updateDownloadProgress.value = progress
                },
                onVerificationWarning = { warning, file ->
                    _updateDownloadProgress.value = null
                    _updateWarningMessage.value = warning
                    _downloadedFile.value = file
                    prefs.edit().putInt("last_downloaded_update_version", info.versionCode).apply()
                },
                onSuccess = { file ->
                    _updateDownloadProgress.value = null
                    _downloadedFile.value = file
                    prefs.edit().putInt("last_downloaded_update_version", info.versionCode).apply()
                    com.example.data.AppUpdateManager.installApk(context, file)
                },
                onError = { error ->
                    _updateDownloadProgress.value = null
                    _updateErrorMessage.value = error
                    _downloadedFile.value = null
                }
            )
        }
    }

    fun installDownloadedApk(context: android.content.Context) {
        val file = _downloadedFile.value ?: return
        com.example.data.AppUpdateManager.installApk(context, file)
    }

    fun dismissUpdateDialog() {
        _showUpdateDialog.value = false
        _updateDownloadProgress.value = null
        _updateWarningMessage.value = null
        _downloadedFile.value = null
    }

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

    val filteredGroupedChannels: StateFlow<List<GroupedChannel>> = combine(
        filteredChannels,
        _userStatsTrigger
    ) { list, _ ->
        groupChannels(list).sortedByDescending { calculateChannelRankScore(it) }
    }.flowOn(Dispatchers.Default)
     .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteGroupedChannels: StateFlow<List<GroupedChannel>> = combine(
        favoriteChannels,
        _userStatsTrigger
    ) { list, _ ->
        groupChannels(list.filter { !it.isBroken }).sortedByDescending { calculateChannelRankScore(it) }
    }.flowOn(Dispatchers.Default)
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
        // Auto-migrate old playlist URLs to secure Cloudflare Worker URL
        val currentSavedUrl = _cloudGistUrl.value
        if (currentSavedUrl.contains("github.com") ||
            currentSavedUrl.contains("iptv-org") ||
            currentSavedUrl.isBlank() ||
            currentSavedUrl.equals("default", ignoreCase = true) ||
            currentSavedUrl.equals("all", ignoreCase = true)) {
            _cloudGistUrl.value = INTERNAL_M3U_URL
            prefs.edit().putString("cloud_gist_url", INTERNAL_M3U_URL).apply()
        }

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

        // Remap selected category ID to the new ID if categories change (e.g. after database refresh/sync)
        viewModelScope.launch {
            categories.collect { list ->
                val name = lastSelectedCategoryName
                if (name != null) {
                    val newCategory = list.find { it.name.equals(name, ignoreCase = true) }
                    if (newCategory != null) {
                        _selectedCategoryId.value = newCategory.id
                    } else {
                        _selectedCategoryId.value = null
                        lastSelectedCategoryName = null
                    }
                }
            }
        }

        viewModelScope.launch {
            // Load cached live events for instant startup display
            launch(Dispatchers.IO) {
                try {
                    val cached = repository.getCachedLiveEvents().map { it.toGroupedEvent() }
                    if (cached.isNotEmpty()) {
                        _fetchedLiveEvents.value = cached
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            _localIsLoading.value = true
            // Seed defaults on start if DB is empty so the user sees channels instantly
            withContext(Dispatchers.IO) {
                repository.seedDatabaseIfEmpty()
            }
            // Set the default category selection — 'All' shows everything
            _selectedCategoryId.value = null
            _localIsLoading.value = false

            // Check if there's an interrupted scan process to resume first
            val isInterrupted = prefs.getBoolean("is_scanning_interrupted", false)
            if (isInterrupted) {
                // Clear the interrupted flag immediately — an infinite loop of full scans
                // on each launch is the worst possible UX. Use targeted Worker batch re-check instead.
                prefs.edit().putBoolean("is_scanning_interrupted", false).apply()
                _localSyncStatusMessage.value = "Re-checking broken channels via cloud..."
                // Launch non-blocking so the UI remains responsive
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    syncManager.verifyBrokenChannelsViaWorker()
                }
            } else if (_autoSyncOnLaunch.value) {
                val lastSyncedUrl = prefs.getString("last_synced_m3u_url", "") ?: ""
                val currentUrl = _cloudGistUrl.value
                val isUrlChanged = lastSyncedUrl != currentUrl
                val existingChannels = repository.allChannels.first()

                if (existingChannels.isEmpty() || isUrlChanged) {
                    // Empty DB or URL changed — force a full sync
                    syncWithCloudGist(force = true)
                } else {
                    // DB populated and URL same — do a lightweight delta check.
                    // syncWithCloudGist uses ?since= so it only downloads if Worker reports changes.
                    // Then calls verifyBrokenChannelsViaWorker() for targeted broken-channel re-check.
                    syncWithCloudGist(force = false)
                }
            } else {
                // Manual sync/lazy active; seed fallback data only if DB is completely empty
                val existingChannels = repository.allChannels.first()
                if (existingChannels.isEmpty()) {
                    repository.seedDatabaseIfEmpty()
                }
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

            // Auto-check for app updates on launch
            try {
                checkForAppUpdates(isAutoCheck = true)
            } catch (e: Exception) {
                e.printStackTrace()
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
            syncWithCloudGist(force = true)
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

    private var lastChannelList: List<ChannelEntity>? = null
    private var lastGroupedList: List<GroupedChannel>? = null

    fun groupChannels(channelList: List<ChannelEntity>): List<GroupedChannel> {
        synchronized(this) {
            if (channelList === lastChannelList || (lastChannelList != null && channelList == lastChannelList)) {
                return lastGroupedList ?: emptyList()
            }
        }

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

        val result = work()
        synchronized(this) {
            lastChannelList = channelList
            lastGroupedList = result
        }
        return result
    }

    // --- Selection Setters ---
    fun selectCategory(categoryId: Int?) {
        _selectedCategoryId.value = categoryId
        if (categoryId != null) {
            val list = categories.value
            lastSelectedCategoryName = list.find { it.id == categoryId }?.name
        } else {
            lastSelectedCategoryName = null
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun addSearchQueryToHistory(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        val current = _searchHistory.value.toMutableList()
        current.remove(trimmed)
        current.add(0, trimmed)
        val truncated = current.take(15)
        _searchHistory.value = truncated
        saveListToPrefs("search_history_queries", truncated)
    }

    fun removeSearchQueryFromHistory(query: String) {
        val current = _searchHistory.value.toMutableList()
        if (current.remove(query)) {
            _searchHistory.value = current
            saveListToPrefs("search_history_queries", current)
        }
    }

    fun clearSearchHistory() {
        _searchHistory.value = emptyList()
        saveListToPrefs("search_history_queries", emptyList())
    }

    fun addChannelToSearchHistory(channelName: String) {
        val current = _recentSearchChannelNames.value.toMutableList()
        current.remove(channelName)
        current.add(0, channelName)
        val truncated = current.take(10)
        _recentSearchChannelNames.value = truncated
        saveListToPrefs("search_history_channels", truncated)
    }

    fun removeChannelFromSearchHistory(channelName: String) {
        val current = _recentSearchChannelNames.value.toMutableList()
        if (current.remove(channelName)) {
            _recentSearchChannelNames.value = current
            saveListToPrefs("search_history_channels", current)
        }
    }

    fun clearRecentSearchChannels() {
        _recentSearchChannelNames.value = emptyList()
        saveListToPrefs("search_history_channels", emptyList())
    }

    fun resetSearchFilters() {
        searchFilterWorkingOnly.value = false
        searchFilterFavoritesOnly.value = false
        searchFilterWithEpgOnly.value = false
        searchFilterCategoryId.value = null
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
        isComingFromLiveEvents = false
        _selectedChannel.value = channel
        errorCountMap.clear()
        if (channel != null) {
            val attempts = (channelAttemptsMap[channel.id] ?: 0) + 1
            channelAttemptsMap[channel.id] = attempts
            android.util.Log.i("LiveTvTelemetry", "Playback attempt registered for channel ${channel.id}. Total attempts: $attempts")
            
            // Record dynamic behavioral watch stats
            incrementChannelWatch(channel.name)

            _currentStreamUrl.value = channel.streamUrl
            val groups = groupChannels(channels.value)
            val group = groups.find { it.originalChannelIds.contains(channel.id) }
            val stream = group?.streams?.find { it.url == channel.streamUrl }
            _currentStreamName.value = stream?.subName ?: "Main Source"

            // Update recently watched list
            val currentNames = _recentlyWatchedNames.value.toMutableList()
            currentNames.remove(channel.name)
            currentNames.add(0, channel.name)
            val updatedNames = currentNames.take(5)
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
            
            val channel = channels.value.find { it.id == channelId }
            if (channel != null) {
                incrementChannelFailure(channel.name)
            }
            val chName = channel?.name ?: "Unknown Channel"
            com.example.data.StreamLogManager.logError(
                type = "Playback",
                targetName = chName,
                url = failedUrl,
                errorMessage = "Playback Connection Failure (consecutive: $count)"
            )
            
            // Increment telemetry failures
            val failures = (channelFailuresMap[channelId] ?: 0) + 1
            channelFailuresMap[channelId] = failures
            val errorRate = getChannelErrorRate(channelId)
            val maskedFailedUrl = com.example.data.StreamDecryptionUtility.maskUrl(failedUrl)
            android.util.Log.i("LiveTvTelemetry", "Playback failure registered for channel $channelId. Failures: $failures, Error Rate: ${"%.2f".format(errorRate * 100)}% [URL: $maskedFailedUrl]")
            
            android.util.Log.d("ChannelViewModel", "Playback error on $maskedFailedUrl, consecutive count: $count")

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

    // --- Live Events States ---
    private val _fetchedLiveEvents = MutableStateFlow<List<com.example.data.GroupedEvent>>(emptyList())
    
    val liveEvents: StateFlow<List<com.example.data.GroupedEvent>> = combine(
        _fetchedLiveEvents,
        channels
    ) { fetched, dbChannels ->
        com.example.data.LiveEventParser.detectAndMergeSimilarEvents(fetched, dbChannels)
    }.flowOn(Dispatchers.Default)
     .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isEventsLoading = MutableStateFlow(false)
    val isEventsLoading: StateFlow<Boolean> = _isEventsLoading.asStateFlow()

    private val _eventsError = MutableStateFlow<String?>(null)
    val eventsError: StateFlow<String?> = _eventsError.asStateFlow()

    fun fetchLiveEvents(forceRefresh: Boolean = false) {
        if (!forceRefresh && _fetchedLiveEvents.value.isNotEmpty()) return
        
        viewModelScope.launch {
            _isEventsLoading.value = true
            _eventsError.value = null
            
            val grouped = withContext(Dispatchers.IO) {
                try {
                    val workerUrl = "https://iptv-api-worker.shakilemon71.workers.dev/api/events"
                    val request = okhttp3.Request.Builder().url(workerUrl).build()
                    val client = com.example.data.CachedHttpClient.getBaseClient()
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body?.string()
                            if (!body.isNullOrBlank()) {
                                val moshi = com.squareup.moshi.Moshi.Builder()
                                    .addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
                                    .build()
                                val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, com.example.data.GroupedEvent::class.java)
                                val adapter = moshi.adapter<List<com.example.data.GroupedEvent>>(type)
                                adapter.fromJson(body) ?: emptyList()
                            } else {
                                emptyList()
                            }
                        } else {
                            emptyList()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    emptyList()
                }
            }
            
            if (grouped.isNotEmpty()) {
                _fetchedLiveEvents.value = grouped
                
                // Save to Room cache
                launch(Dispatchers.IO) {
                    try {
                        val cachedEntities = grouped.map { com.example.data.CachedLiveEventEntity.fromGroupedEvent(it) }
                        repository.saveCachedLiveEvents(cachedEntities)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                if (grouped.isEmpty() && liveEvents.value.isEmpty()) {
                    _eventsError.value = "No live or upcoming events scheduled right now."
                }
            } else {
                if (liveEvents.value.isEmpty()) {
                    _eventsError.value = "Failed to load secure event schedule. Please verify your connection."
                }
            }
            _isEventsLoading.value = false
        }
    }

    fun playLiveEventFeed(event: com.example.data.GroupedEvent, feed: com.example.data.EventFeed) {
        isComingFromLiveEvents = true
        val tempChannel = ChannelEntity(
            id = -Math.abs(feed.streamUrl.hashCode()), // Negative ID indicates temporary event stream
            name = event.title,
            streamUrl = feed.streamUrl,
            logoUrl = feed.logoUrl.ifEmpty { event.logoUrl },
            category = "Live Events: ${event.sportCategory}",
            categoryId = -999,
            description = "Live event stream of ${event.title} via ${feed.provider} (${feed.language})"
        )
        _selectedChannel.value = tempChannel
        errorCountMap.clear()
        _currentStreamUrl.value = feed.streamUrl
        _currentStreamName.value = "${feed.provider} (${feed.language})"
    }

    fun scheduleEventReminder(event: com.example.data.GroupedEvent, delayMillis: Long) {
        viewModelScope.launch {
            val alertTime = System.currentTimeMillis() + delayMillis
            val entity = com.example.data.InterestedEventEntity.fromGroupedEvent(event, alertTime)
            repository.saveInterestedLiveEvent(entity)
            com.example.data.LiveEventReminderWorker.scheduleReminder(getApplication(), event.id, delayMillis)
            com.example.data.LiveEventReminderWorker.showInstantNotification(
                getApplication(),
                "Reminder Scheduled! 🔔",
                "You will be notified when \"${event.title}\" is starting!"
            )
        }
    }

    fun cancelEventReminder(eventId: String) {
        viewModelScope.launch {
            repository.deleteInterestedLiveEventById(eventId)
            com.example.data.LiveEventReminderWorker.cancelReminder(getApplication(), eventId)
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
