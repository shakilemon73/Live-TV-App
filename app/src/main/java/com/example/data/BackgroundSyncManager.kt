package com.example.data

import android.content.Context
import android.util.Log
import com.example.data.ChannelEntity
import com.example.data.LiveTvRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class BackgroundSyncManager(
    val repository: LiveTvRepository,
    private val context: Context
) {
    companion object {
        private const val TAG = "BackgroundSyncManager"
    }
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = context.applicationContext.getSharedPreferences("live_tv_prefs", Context.MODE_PRIVATE)

    private val _isCheckingStreams = MutableStateFlow(false)
    val isCheckingStreams: StateFlow<Boolean> = _isCheckingStreams.asStateFlow()

    private val _streamCheckingProgress = MutableStateFlow(0f)
    val streamCheckingProgress: StateFlow<Float> = _streamCheckingProgress.asStateFlow()

    private val _streamCheckingStatus = MutableStateFlow<String?>(null)
    val streamCheckingStatus: StateFlow<String?> = _streamCheckingStatus.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncStatusMessage = MutableStateFlow<String?>(null)
    val syncStatusMessage: StateFlow<String?> = _syncStatusMessage.asStateFlow()

    private val streamCheckClient: OkHttpClient by lazy {
        com.example.data.CachedHttpClient.getClient(context)
    }

    suspend fun checkStreamUrl(streamUrl: String): Boolean = withContext(Dispatchers.IO) {
        if (streamUrl.isBlank()) return@withContext false

        // 1. First, attempt a highly efficient HEAD request to save bandwidth
        try {
            val headRequest = Request.Builder()
                .url(streamUrl)
                .head()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build()
            
            streamCheckClient.newCall(headRequest).execute().use { response ->
                if (response.isSuccessful || response.code in 200..399) {
                    val contentType = response.header("Content-Type")?.lowercase() ?: ""
                    val isKnownStream = streamUrl.contains(".m3u8", ignoreCase = true) ||
                                        streamUrl.contains(".mpd", ignoreCase = true) ||
                                        streamUrl.contains(".ts", ignoreCase = true) ||
                                        streamUrl.contains(".mp4", ignoreCase = true) ||
                                        streamUrl.contains(".ism", ignoreCase = true) ||
                                        streamUrl.startsWith("rtsp://", ignoreCase = true) ||
                                        streamUrl.startsWith("rtsps://", ignoreCase = true)
                    
                    // If content-type indicates an HTML landing/error page, we need to treat it with caution
                    if (contentType.contains("text/html") && !isKnownStream) {
                        // Fall back to a GET request to verify if it's indeed an HTML block or a real media file
                    } else {
                        return@withContext true
                    }
                }
            }
        } catch (e: Exception) {
            // HEAD request failed or timed out; some streams reject HEAD entirely. Fall back to GET.
        }

        // 2. Highly optimized GET request requesting only the first 1KB (avoiding downloading endless live streams)
        try {
            val getRequest = Request.Builder()
                .url(streamUrl)
                .get()
                .header("Range", "bytes=0-1024") // Ask for first 1KB only
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build()
            
            streamCheckClient.newCall(getRequest).execute().use { response ->
                if (response.isSuccessful || response.code in 200..399) {
                    val contentType = response.header("Content-Type")?.lowercase() ?: ""
                    val isKnownStream = streamUrl.contains(".m3u8", ignoreCase = true) ||
                                        streamUrl.contains(".mpd", ignoreCase = true) ||
                                        streamUrl.contains(".ts", ignoreCase = true) ||
                                        streamUrl.contains(".mp4", ignoreCase = true) ||
                                        streamUrl.contains(".ism", ignoreCase = true) ||
                                        streamUrl.startsWith("rtsp://", ignoreCase = true) ||
                                        streamUrl.startsWith("rtsps://", ignoreCase = true)
                    
                    // Many ISPs redirect dead links to an HTML block or portal page. If text/html is found
                    // for an expected video stream, we treat it as dead/broken.
                    if (contentType.contains("text/html") && !isKnownStream) {
                        return@withContext false
                    }
                    return@withContext true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "GET stream check failed for: $streamUrl, error: ${e.message}")
        }
        return@withContext false
    }

    suspend fun hasAnyBrokenStream(channels: List<ChannelEntity>): Boolean = withContext(Dispatchers.IO) {
        if (channels.isEmpty()) return@withContext false
        val semaphore = Semaphore(5)
        var foundBroken = false
        try {
            coroutineScope {
                channels.forEach { channel ->
                    launch {
                        if (foundBroken) return@launch
                        semaphore.withPermit {
                            if (foundBroken) return@withPermit
                            val working = checkStreamUrl(channel.streamUrl)
                            if (!working) {
                                foundBroken = true
                                this@coroutineScope.cancel()
                            }
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            // Expected cancellation
        }
        return@withContext foundBroken
    }

    @OptIn(FlowPreview::class)
    fun verifyAllChannels() {
        if (_isCheckingStreams.value) return
        val repo = repository ?: return
        
        syncScope.launch {
            _isCheckingStreams.value = true
            _streamCheckingProgress.value = 0f
            _streamCheckingStatus.value = "Initializing stream verification..."
            
            val currentChannels = repo.allChannels.first()
            if (currentChannels.isEmpty()) {
                _streamCheckingStatus.value = "No channels to verify."
                _isCheckingStreams.value = false
                return@launch
            }
            
            val total = currentChannels.size
            
            // Advanced Incremental Resume Logic using SharedPreferences
            val lastSessionTime = prefs?.getLong("scan_session_timestamp", 0L) ?: 0L
            val sessionTime = if (lastSessionTime > 0L) {
                lastSessionTime
            } else {
                val now = System.currentTimeMillis()
                prefs?.edit()?.putLong("scan_session_timestamp", now)?.apply()
                now
            }
            
            // Mark interrupted state so we resume seamlessly if app is closed mid-way
            prefs?.edit()?.putBoolean("is_scanning_interrupted", true)?.apply()
            
            // Channels completed so far in this specific session
            val alreadyChecked = currentChannels.filter { it.lastChecked >= sessionTime }
            val pendingCheck = currentChannels.filter { it.lastChecked < sessionTime }
            
            var completedCount = alreadyChecked.size
            _streamCheckingProgress.value = completedCount.toFloat() / total
            _streamCheckingStatus.value = "Resuming verification... ($completedCount / $total channels)"
            
            if (pendingCheck.isEmpty()) {
                _streamCheckingStatus.value = "Verification complete! All channels up to date."
                _isCheckingStreams.value = false
                prefs?.edit()?.putBoolean("is_scanning_interrupted", false)?.putLong("scan_session_timestamp", 0L)?.apply()
                return@launch
            }
            
            val brokenIds = mutableListOf<Int>()
            val workingIds = mutableListOf<Int>()
            
            pendingCheck.asFlow()
                .flatMapMerge(concurrency = 5) { channel ->
                    flow {
                        val working = checkStreamUrl(channel.streamUrl)
                        emit(channel to working)
                    }
                }
                .collect { (channel, working) ->
                    // 1. Instantly save progress to database in real-time
                    repo.updateChannelBrokenStatus(channel.id, !working, System.currentTimeMillis())
                    
                    synchronized(this@BackgroundSyncManager) {
                        if (working) {
                            workingIds.add(channel.id)
                        } else {
                            brokenIds.add(channel.id)
                        }
                        completedCount++
                        _streamCheckingProgress.value = completedCount.toFloat() / total
                        _streamCheckingStatus.value = "Verifying streams... ($completedCount / $total)"
                    }
                }
            
            _streamCheckingStatus.value = "Applying final guide updates..."
            
            // Scan finished successfully, reset interrupted state
            prefs?.edit()?.putBoolean("is_scanning_interrupted", false)?.putLong("scan_session_timestamp", 0L)?.apply()
            
            _streamCheckingStatus.value = "Verification complete! Found ${brokenIds.size} broken feeds."
            _isCheckingStreams.value = false
            delay(4000)
            if (_streamCheckingStatus.value?.startsWith("Verification complete") == true) {
                _streamCheckingStatus.value = null
            }
        }
    }

    fun syncWithCloudGist(url: String, force: Boolean = false, onComplete: (Boolean) -> Unit = {}) {
        if (_isSyncing.value) return
        val repo = repository ?: return
        
        syncScope.launch {
            _isSyncing.value = true
            _syncStatusMessage.value = "Contacting remote playlist provider..."
            try {
                val updated = repo.fetchAndParseM3u(url, force)
                if (updated) {
                    _syncStatusMessage.value = "Playlist synced successfully!"
                    verifyAllChannels()
                } else {
                    _syncStatusMessage.value = "Playlist is already up-to-date."
                }
                onComplete(true)
            } catch (e: Exception) {
                Log.e(TAG, "Gist synchronization failed", e)
                _syncStatusMessage.value = "Sync failed: ${e.localizedMessage ?: "Connection error"}"
                onComplete(false)
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun syncWithM3uText(text: String, onComplete: (Boolean) -> Unit = {}) {
        if (text.isBlank() || _isSyncing.value) return
        val repo = repository ?: return
        
        syncScope.launch {
            _isSyncing.value = true
            _syncStatusMessage.value = "Processing and parsing raw playlist text..."
            try {
                repo.parseAndInsertM3uText(text)
                _syncStatusMessage.value = "M3U imported successfully!"
                onComplete(true)
                // Automatically run verification in the background upon sync completion!
                verifyAllChannels()
            } catch (e: Exception) {
                Log.e(TAG, "M3U text import failed", e)
                _syncStatusMessage.value = "Import failed: ${e.localizedMessage ?: "Format error"}"
                onComplete(false)
            } finally {
                _isSyncing.value = false
            }
        }
    }

    @OptIn(FlowPreview::class)
    suspend fun verifyAllChannelsSuspend(onlyIfNotCheckedInLastNHours: Int = 12): Boolean = withContext(Dispatchers.IO) {
        val repo = repository ?: return@withContext false
        try {
            val currentChannels = repo.allChannels.first()
            if (currentChannels.isEmpty()) {
                return@withContext false
            }
            
            val total = currentChannels.size
            val now = System.currentTimeMillis()
            val cutoff = now - (onlyIfNotCheckedInLastNHours * 3600 * 1000L)
            
            // Only re-verify channels that were not checked within the last N hours
            val pendingCheck = currentChannels.filter { it.lastChecked < cutoff }
            if (pendingCheck.isEmpty()) {
                Log.d(TAG, "verifyAllChannelsSuspend: All channels have been checked within the last $onlyIfNotCheckedInLastNHours hours. Skipping.")
                return@withContext true
            }
            
            val brokenIds = mutableListOf<Int>()
            val workingIds = mutableListOf<Int>()
            
            pendingCheck.asFlow()
                .flatMapMerge(concurrency = 2) { channel ->
                    flow {
                        val working = checkStreamUrl(channel.streamUrl)
                        emit(channel to working)
                    }
                }
                .collect { (channel, working) ->
                    repo.updateChannelBrokenStatus(channel.id, !working, System.currentTimeMillis())
                    synchronized(this@BackgroundSyncManager) {
                        if (working) {
                            workingIds.add(channel.id)
                        } else {
                            brokenIds.add(channel.id)
                        }
                    }
                }
            
            Log.d(TAG, "verifyAllChannelsSuspend complete. Verified ${pendingCheck.size} channels that were not checked in the last $onlyIfNotCheckedInLastNHours hours.")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "verifyAllChannelsSuspend failed", e)
            return@withContext false
        }
    }

    suspend fun syncWithCloudGistSuspend(url: String): Boolean = withContext(Dispatchers.IO) {
        val repo = repository ?: return@withContext false
        try {
            repo.fetchAndParseM3u(url)
            verifyAllChannelsSuspend()
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "syncWithCloudGistSuspend failed for url: $url", e)
            return@withContext false
        }
    }
}
