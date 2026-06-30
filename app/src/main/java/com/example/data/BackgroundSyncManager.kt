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

object BackgroundSyncManager {
    private const val TAG = "BackgroundSyncManager"
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var repository: LiveTvRepository? = null

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

    fun initialize(repo: LiveTvRepository) {
        this.repository = repo
    }

    private val streamCheckClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(3000, TimeUnit.MILLISECONDS)
            .readTimeout(3000, TimeUnit.MILLISECONDS)
            .writeTimeout(3000, TimeUnit.MILLISECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private suspend fun checkStreamUrl(streamUrl: String): Boolean = withContext(Dispatchers.IO) {
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
                    // If content-type indicates an HTML landing/error page, we need to treat it with caution
                    if (contentType.contains("text/html") && !streamUrl.contains(".m3u8", ignoreCase = true)) {
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
                    // Many ISPs redirect dead links to an HTML block or portal page. If text/html is found
                    // for an expected video stream, we treat it as dead/broken.
                    if (contentType.contains("text/html") && !streamUrl.contains(".m3u8", ignoreCase = true)) {
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
            var checkedCount = 0
            
            val brokenIds = mutableListOf<Int>()
            val workingIds = mutableListOf<Int>()
            
            // Limit active parallel checks to 5 to avoid out of memory and socket starvation
            currentChannels.asFlow()
                .flatMapMerge(concurrency = 5) { channel ->
                    flow {
                        val working = checkStreamUrl(channel.streamUrl)
                        emit(channel to working)
                    }
                }
                .collect { (channel, working) ->
                    synchronized(this@BackgroundSyncManager) {
                        if (working) {
                            workingIds.add(channel.id)
                        } else {
                            brokenIds.add(channel.id)
                        }
                        checkedCount++
                        _streamCheckingProgress.value = checkedCount.toFloat() / total
                        _streamCheckingStatus.value = "Verified $checkedCount / $total channels..."
                    }
                }
            
            _streamCheckingStatus.value = "Applying stream updates..."
            
            // Critical part: Update the database in ONE SINGLE BATCH TRANSACTION at the end of the run!
            repo.updateChannelsBrokenStatuses(brokenIds, workingIds)
            
            _streamCheckingStatus.value = "All channels verified! Found ${brokenIds.size} broken streams."
            _isCheckingStreams.value = false
            delay(4000)
            if (_streamCheckingStatus.value?.startsWith("All channels verified") == true) {
                _streamCheckingStatus.value = null
            }
        }
    }

    fun syncWithCloudGist(url: String, onComplete: (Boolean) -> Unit = {}) {
        if (_isSyncing.value) return
        val repo = repository ?: return
        
        syncScope.launch {
            _isSyncing.value = true
            _syncStatusMessage.value = "Contacting remote playlist provider..."
            try {
                repo.fetchAndParseM3u(url)
                _syncStatusMessage.value = "Playlist synced successfully!"
                onComplete(true)
                // Automatically run verification in the background upon sync completion!
                verifyAllChannels()
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
    suspend fun verifyAllChannelsSuspend(): Boolean = withContext(Dispatchers.IO) {
        val repo = repository ?: return@withContext false
        try {
            val currentChannels = repo.allChannels.first()
            if (currentChannels.isEmpty()) {
                return@withContext false
            }
            
            val total = currentChannels.size
            val brokenIds = mutableListOf<Int>()
            val workingIds = mutableListOf<Int>()
            
            currentChannels.asFlow()
                .flatMapMerge(concurrency = 5) { channel ->
                    flow {
                        val working = checkStreamUrl(channel.streamUrl)
                        emit(channel to working)
                    }
                }
                .collect { (channel, working) ->
                    synchronized(this@BackgroundSyncManager) {
                        if (working) {
                            workingIds.add(channel.id)
                        } else {
                            brokenIds.add(channel.id)
                        }
                    }
                }
            
            repo.updateChannelsBrokenStatuses(brokenIds, workingIds)
            Log.d(TAG, "verifyAllChannelsSuspend complete. Found ${brokenIds.size} broken out of $total channels.")
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
