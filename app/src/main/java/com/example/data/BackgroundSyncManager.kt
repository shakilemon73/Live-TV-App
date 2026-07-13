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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
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

    private fun isNetworkAvailable(): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            if (connectivityManager != null) {
                val activeNetwork = connectivityManager.activeNetwork
                if (activeNetwork != null) {
                    val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
                    capabilities != null && capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                } else {
                    false
                }
            } else {
                false
            }
        } catch (e: Exception) {
            true // fallback to true in case of unexpected system service exceptions
        }
    }

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

    // ────────────────────────────────────────────────────────────────────────
    // World-class validation helpers
    // ────────────────────────────────────────────────────────────────────────

    /** HTML portal / ISP redirect page signatures that mean the stream is dead. */
    private val HTML_PORTAL_SIGNATURES = listOf(
        "<!doctype html", "<html", "<head>", "<body>",
        "error 404", "not found", "access denied",
        "<!DOCTYPE HTML"
    )

    /**
     * Returns true if the response body starts with HTML portal content,
     * indicating an ISP redirect, captive portal, or error page — NOT a live stream.
     */
    private fun bodyLooksLikeHtmlPortal(bodyBytes: ByteArray?): Boolean {
        if (bodyBytes == null || bodyBytes.isEmpty()) return false
        val snippet = String(bodyBytes.copyOf(minOf(bodyBytes.size, 512)), Charsets.UTF_8).lowercase()
        return HTML_PORTAL_SIGNATURES.any { snippet.contains(it) }
    }

    /**
     * Returns true if the body starts with an HLS manifest marker.
     * A .m3u8 URL that returns anything other than #EXTM3U is a broken/dead feed.
     */
    private fun bodyIsValidHlsManifest(bodyBytes: ByteArray?): Boolean {
        if (bodyBytes == null || bodyBytes.isEmpty()) return false
        val snippet = String(bodyBytes.copyOf(minOf(bodyBytes.size, 512)), Charsets.UTF_8).trim()
        return snippet.startsWith("#EXTM3U") || snippet.startsWith("#EXT-X-VERSION")
    }

    /**
     * Server-assisted validation for encrypted:// URLs.
     *
     * Sends the encrypted token to the Cloudflare Worker's /api/validate endpoint.
     * The Worker decrypts it server-side, probes the real stream URL, and returns
     * { working: bool }. The real URL is NEVER exposed to the Android client.
     *
     * @param encryptedUrl  The full "encrypted://..." string stored in the DB.
     * @param workerBaseUrl The Cloudflare Worker base URL (from SharedPreferences).
     * @return true if the server says the stream is live, false otherwise.
     */
    private suspend fun checkEncryptedUrlViaServer(
        encryptedUrl: String,
        workerBaseUrl: String
    ): Boolean = withContext(Dispatchers.IO) {
        val token = encryptedUrl.removePrefix("encrypted://")
        if (token.isBlank()) return@withContext false

        val validateUrl = workerBaseUrl.trimEnd('/') + "/api/validate?t=" +
            android.net.Uri.encode(token)

        return@withContext try {
            val checkerClient = streamCheckClient.newBuilder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
                .build()
            val request = okhttp3.Request.Builder()
                .url(validateUrl)
                .header("User-Agent", "TNi-App-Validator/1.0")
                .build()
            checkerClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use false
                val body = response.body?.string() ?: return@use false
                val json = org.json.JSONObject(body)
                json.optBoolean("working", false)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Server-assisted validation failed, assuming working to avoid false-positive: ${e.message}")
            true // Network error — don't mark as broken to avoid false positives
        }
    }

    suspend fun checkStreamUrl(streamUrl: String): Boolean = withContext(Dispatchers.IO) {
        if (streamUrl.isBlank()) return@withContext false

        // Determine the Cloudflare Worker base URL (for server-assisted encrypted URL checks).
        // Extract the scheme + host from the configured cloud URL (e.g. https://x.workers.dev/api/channels -> https://x.workers.dev)
        val defaultWorkerUrl = "https://iptv-api-worker.shakilemon71.workers.dev"
        val configuredUrl = prefs.getString("cloud_gist_url", defaultWorkerUrl) ?: defaultWorkerUrl
        val workerBaseUrl = try {
            val uri = java.net.URI(configuredUrl)
            "${uri.scheme}://${uri.host}${if (uri.port != -1) ":${uri.port}" else ""}"
        } catch (e: Exception) {
            defaultWorkerUrl
        }

        val decryptedUrl = if (streamUrl.startsWith("encrypted://")) {
            val localDecrypted = try {
                StreamDecryptionUtility.decrypt(streamUrl, context)
            } catch (e: Exception) {
                ""
            }
            if (localDecrypted.isNotBlank() && !localDecrypted.startsWith("encrypted://")) {
                localDecrypted
            } else {
                // Local decryption failed — try server-assisted validation instead
                Log.d(TAG, "Local decryption failed for encrypted URL, using server-assisted validation")
                return@withContext checkEncryptedUrlViaServer(streamUrl, workerBaseUrl)
            }
        } else {
            streamUrl
        }
        if (decryptedUrl.isBlank()) return@withContext false

        var cleanUrl = decryptedUrl
        val customHeaders = mutableMapOf<String, String>()

        if (decryptedUrl.contains("|")) {
            try {
                val parts = decryptedUrl.split("|", limit = 2)
                cleanUrl = parts[0]
                val headerParams = parts[1]
                headerParams.split("&").forEach { param ->
                    val kv = param.split("=", limit = 2)
                    if (kv.size == 2) {
                        val rawKey = kv[0].trim()
                        val key = when (rawKey.lowercase()) {
                            "http-referrer", "referrer", "referer" -> "Referer"
                            "http-origin", "origin" -> "Origin"
                            "http-user-agent", "user-agent", "http-useragent" -> "User-Agent"
                            else -> rawKey
                        }
                        val rawValue = kv[1].trim()
                        val value = try {
                            java.net.URLDecoder.decode(rawValue, "UTF-8")
                        } catch (e: Exception) {
                            rawValue
                        }
                        if (key.isNotBlank() && value.isNotBlank()) {
                            customHeaders[key] = value
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing pipe-separated headers for stream check: ${StreamDecryptionUtility.maskUrl(streamUrl)}", e)
            }
        }

        if (!isNetworkAvailable()) {
            // Device is offline. Bypassing check to avoid false-positives
            return@withContext true
        }

        val isHls = cleanUrl.contains(".m3u8", ignoreCase = true) ||
                    cleanUrl.contains(".m3u", ignoreCase = true)
        val isDash = cleanUrl.contains(".mpd", ignoreCase = true)

        // Set up a custom OkHttpClient with slightly longer timeouts for stream validation
        // (8s handles slow IPTV origin servers without generating false positives)
        val checkerClient = streamCheckClient.newBuilder()
            .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(6, java.util.concurrent.TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        // Helper: read first N bytes from an OkHttp response body safely.
        // Uses the Okio Buffer directly (whose .size is Long) to avoid type-mismatch with minOf.
        fun readBodySnippet(response: okhttp3.Response, maxBytes: Int = 512): ByteArray? {
            return try {
                response.body?.source()?.let { source ->
                    source.request(maxBytes.toLong())
                    val buf = source.buffer                       // Buffer.size is Long
                    val len = minOf(buf.size, maxBytes.toLong()) // minOf(Long, Long) — unambiguous
                    buf.readByteArray(len)                       // readByteArray(Long): ByteArray
                }
            } catch (e: Exception) { null }
        }

        /**
         * Perform a single HTTP request phase.
         * Returns:
         *   true  — stream is definitively WORKING
         *   false — stream is definitively BROKEN / DEAD
         *   null  — inconclusive, try the next phase
         */
        fun tryRequestPhase(useHead: Boolean, useRange: Boolean): Boolean? {
            try {
                val builder = okhttp3.Request.Builder().url(cleanUrl)
                if (useHead) {
                    builder.head()
                } else {
                    builder.get()
                    if (useRange) {
                        builder.header("Range", "bytes=0-2048")
                    }
                }

                if (!customHeaders.containsKey("User-Agent")) {
                    builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                }
                customHeaders.forEach { (k, v) ->
                    builder.header(k, v)
                }

                checkerClient.newCall(builder.build()).execute().use { response ->
                    val code = response.code
                    val contentType = response.header("Content-Type")?.lowercase() ?: ""

                    // ─ Hard dead-link codes ─
                    if (code == 404 || code == 410 || code == 451) {
                        Log.d(TAG, "Stream dead (HTTP $code): ${StreamDecryptionUtility.maskUrl(streamUrl)}")
                        return false
                    }

                    // ─ Permanently blocked ─
                    if (code == 401 || code == 403) {
                        Log.d(TAG, "Stream blocked (HTTP $code): ${StreamDecryptionUtility.maskUrl(streamUrl)}")
                        return false
                    }

                    // ─ For range requests, some streaming servers return 416 / 400 ─
                    // Fall through to a non-range GET in the next phase
                    if (useRange && (code == 416 || code == 400 || code == 405)) {
                        return null
                    }

                    if (code in 200..399) {
                        // ─ HTML portal / ISP redirect detection via Content-Type ─
                        if (contentType.contains("text/html") && !isHls) {
                            Log.d(TAG, "Stream returns HTML portal (text/html on non-HLS URL): ${StreamDecryptionUtility.maskUrl(streamUrl)}")
                            return false
                        }

                        // ─ For GET requests: read body snippet and sniff for portals / HLS markers ─
                        if (!useHead) {
                            val bodyBytes = readBodySnippet(response, 512)
                            // ISP portal / captive portal body check
                            if (bodyLooksLikeHtmlPortal(bodyBytes)) {
                                Log.d(TAG, "Stream body looks like HTML portal: ${StreamDecryptionUtility.maskUrl(streamUrl)}")
                                return false
                            }
                            // HLS manifest integrity check: .m3u8 must begin with #EXTM3U
                            if (isHls && bodyBytes != null && bodyBytes.isNotEmpty()) {
                                if (!bodyIsValidHlsManifest(bodyBytes)) {
                                    Log.d(TAG, "M3U8 URL returned non-HLS body — broken manifest: ${StreamDecryptionUtility.maskUrl(streamUrl)}")
                                    return false
                                }
                                return true // Valid HLS manifest confirmed
                            }
                        }

                        return true // 2xx/3xx with no disqualifying content
                    }

                    // Other error codes: inconclusive — try next phase
                    return null
                }
            } catch (e: Exception) {
                // Network / timeout exception — inconclusive
                return null
            }
        }

        // Phase 1: Fast HEAD request
        val phase1 = tryRequestPhase(useHead = true, useRange = false)
        if (phase1 != null) {
            // For HLS, a successful HEAD must be followed by a body check to verify manifest
            if (phase1 == true && isHls) {
                // Fall through to Phase 2 for body sniffing
            } else {
                return@withContext phase1
            }
        }

        // Phase 2: GET with Range header + body sniff (efficient)
        val phase2 = tryRequestPhase(useHead = false, useRange = true)
        if (phase2 != null) return@withContext phase2

        // Phase 3: Full GET (safest fallback, response closed immediately)
        val phase3 = tryRequestPhase(useHead = false, useRange = false)
        if (phase3 != null) return@withContext phase3

        // All phases exhausted with network errors — don't mark as broken if we're online
        // (avoids false positives for briefly unreachable servers)
        Log.w(TAG, "All validation phases inconclusive for: ${StreamDecryptionUtility.maskUrl(streamUrl)}. Treating as working.")
        return@withContext true
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

    /**
     * ⚠️ Manual-only: called when the user explicitly taps "Verify All Channels".
     * Do NOT call this on app startup — it scans every channel and blocks for minutes.
     * For startup validation use [verifyBrokenChannelsViaWorker] instead.
     */
    @OptIn(FlowPreview::class)
    fun verifyAllChannels() {
        if (_isCheckingStreams.value) return
        val repo = repository ?: return

        syncScope.launch {
            _isCheckingStreams.value = true
            _streamCheckingProgress.value = 0f
            _streamCheckingStatus.value = "Initializing stream verification..."

            if (!isNetworkAvailable()) {
                _streamCheckingStatus.value = "Device is offline. Skipping channel verification."
                _isCheckingStreams.value = false
                prefs?.edit()?.putBoolean("is_scanning_interrupted", false)?.putLong("scan_session_timestamp", 0L)?.apply()
                return@launch
            }

            val currentChannels = repo.allChannels.first()
            if (currentChannels.isEmpty()) {
                _streamCheckingStatus.value = "No channels to verify."
                _isCheckingStreams.value = false
                return@launch
            }

            val total = currentChannels.size
            val now = System.currentTimeMillis()

            // Advanced Incremental Resume Logic using SharedPreferences
            val lastSessionTime = prefs?.getLong("scan_session_timestamp", 0L) ?: 0L
            val sessionTime = if (lastSessionTime > 0L) {
                lastSessionTime
            } else {
                prefs?.edit()?.putLong("scan_session_timestamp", now)?.apply()
                now
            }

            // Mark interrupted state so we resume seamlessly if app is closed mid-way
            prefs?.edit()?.putBoolean("is_scanning_interrupted", true)?.apply()

            // ─ Skip-recently-confirmed-working guard ──────────────────────────────
            // Channels that were confirmed LIVE (isBroken=false) within the last 4 hours
            // don't need re-checking. Only check:
            //   a) New channels with lastChecked = 0
            //   b) Channels marked broken
            //   c) Channels not checked in this session yet AND not recently confirmed working
            val SKIP_WINDOW_MS = 4 * 60 * 60 * 1000L // 4 hours
            val alreadyChecked = currentChannels.filter { it.lastChecked >= sessionTime }
            val pendingCheck = currentChannels.filter { ch ->
                when {
                    ch.lastChecked >= sessionTime -> false // Already checked this session
                    ch.lastChecked == 0L -> true           // New channel — always check
                    ch.isBroken -> true                    // Broken — always re-check
                    (now - ch.lastChecked) < SKIP_WINDOW_MS -> false // Confirmed working within 4h — skip
                    else -> true
                }
            }

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
                    // Instantly save progress to database in real-time
                    repo.updateChannelBrokenStatus(channel.id, !working, System.currentTimeMillis())

                    if (!working) {
                        com.example.data.StreamLogManager.logError(
                            type = "Verification",
                            targetName = channel.name,
                            url = channel.streamUrl,
                            errorMessage = "Unreachable Stream"
                        )
                    }

                    synchronized(this@BackgroundSyncManager) {
                        if (working) workingIds.add(channel.id) else brokenIds.add(channel.id)
                        completedCount++
                        _streamCheckingProgress.value = completedCount.toFloat() / total
                        _streamCheckingStatus.value = "Verifying streams... ($completedCount / $total)"
                    }
                }

            _streamCheckingStatus.value = "Applying final guide updates..."
            prefs?.edit()?.putBoolean("is_scanning_interrupted", false)?.putLong("scan_session_timestamp", 0L)?.apply()
            _streamCheckingStatus.value = "Verification complete! Found ${brokenIds.size} broken feeds."
            _isCheckingStreams.value = false
            delay(4000)
            if (_streamCheckingStatus.value?.startsWith("Verification complete") == true) {
                _streamCheckingStatus.value = null
            }
        }
    }

    /**
     * Priority fast-path validation for newly-detected channels.
     * Runs at higher concurrency (8) so new channels show Live/Dead badges immediately.
     * Called right after a delta sync that detected new/updated channels.
     *
     * NOTE: Only called for channels the Worker couldn't validate (validationReason="not_checked").
     * Channels the Worker already validated (isBroken set authoritatively) are skipped.
     */
    @OptIn(FlowPreview::class)
    suspend fun verifyNewChannelsFirst(channelIds: List<Int>) {
        if (channelIds.isEmpty()) return
        val repo = repository ?: return
        if (!isNetworkAvailable()) return

        val allChannels = repo.allChannels.first()
        // Optimize: Only local-probe channels that the Worker couldn't validate (lastChecked == 0L)
        val newChannels = allChannels.filter { it.id in channelIds.toSet() && it.lastChecked == 0L }
        if (newChannels.isEmpty()) return

        Log.d(TAG, "Priority validating ${newChannels.size} unchecked channels...")
        _streamCheckingStatus.value = "Checking ${newChannels.size} channels..."

        newChannels.asFlow()
            .flatMapMerge(concurrency = 8) { channel ->
                flow {
                    val working = checkStreamUrl(channel.streamUrl)
                    emit(channel to working)
                }
            }
            .collect { (channel, working) ->
                repo.updateChannelBrokenStatus(channel.id, !working, System.currentTimeMillis())
                Log.d(TAG, "New channel '${channel.name}': ${if (working) "LIVE" else "DEAD"}")
            }

        _streamCheckingStatus.value = null
        Log.d(TAG, "Priority validation complete for ${newChannels.size} channels.")
    }

    /**
     * Server-assisted batch re-verification for broken channels.
     *
     * Sends only [isBroken=true] channels to the Worker's /api/validate-batch in groups
     * of 10. The Worker decrypts each URL server-side, probes it with a 4-second timeout,
     * and returns a definitive working/broken verdict — without exposing real URLs.
     *
     * Why this is better than [verifyAllChannels]:
     *   - Only processes channels already confirmed broken (not the full library)
     *   - Worker-side validation is faster (no Android battery/CPU cost)
     *   - Stays well within Cloudflare's 50 subrequest cap (10 per batch call)
     *   - Results in DB immediately without blocking the UI
     */
    suspend fun verifyBrokenChannelsViaWorker() = withContext(Dispatchers.IO) {
        val repo = repository ?: return@withContext
        if (!isNetworkAvailable()) return@withContext

        val brokenChannels = repo.allChannels.first().filter { it.isBroken }
        if (brokenChannels.isEmpty()) {
            Log.d(TAG, "verifyBrokenChannelsViaWorker: No broken channels to re-check.")
            return@withContext
        }

        // Derive the Worker base URL from the configured cloud URL
        val defaultWorkerUrl = "https://iptv-api-worker.shakilemon71.workers.dev"
        val configuredUrl = prefs?.getString("cloud_gist_url", defaultWorkerUrl) ?: defaultWorkerUrl
        val workerBaseUrl = try {
            val uri = java.net.URI(configuredUrl)
            "${uri.scheme}://${uri.host}${if (uri.port != -1) ":${uri.port}" else ""}"
        } catch (e: Exception) {
            defaultWorkerUrl
        }
        val batchUrl = "$workerBaseUrl/api/validate-batch"

        Log.d(TAG, "verifyBrokenChannelsViaWorker: Re-checking ${brokenChannels.size} broken channels via Worker batch API.")
        _streamCheckingStatus.value = "Re-validating ${brokenChannels.size} broken channels via cloud..."

        // Process in batches of 10 (Worker's validated limit per call)
        val batches = brokenChannels.chunked(10)
        var recovered = 0
        var stillBroken = 0

        for (batch in batches) {
            val urlPayload = batch.map { it.streamUrl } // These are already "encrypted://..."
            try {
                val jsonBody = org.json.JSONObject()
                jsonBody.put("urls", org.json.JSONArray(urlPayload))
                val requestBody = jsonBody.toString()
                    .toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = okhttp3.Request.Builder()
                    .url(batchUrl)
                    .post(requestBody)
                    .header("User-Agent", "TNi-App-Validator/2.0")
                    .build()

                streamCheckClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "Batch validation call failed: HTTP ${response.code}")
                        return@use
                    }
                    val body = response.body?.string() ?: return@use
                    val json = org.json.JSONObject(body)
                    val results = json.optJSONArray("results") ?: return@use

                    for (i in 0 until results.length()) {
                        val result = results.getJSONObject(i)
                        val encToken = result.optString("url", "")
                        val working = result.optBoolean("working", false)

                        // Match result back to channel by streamUrl token
                        val matched = batch.find { it.streamUrl.contains(encToken) || encToken.contains(it.streamUrl.removePrefix("encrypted://")) }
                        if (matched != null) {
                            repo.applyWorkerValidationResult(matched.id, working)
                            if (working) recovered++ else stillBroken++
                            Log.d(TAG, "Batch re-check '${matched.name}': ${if (working) "RECOVERED" else "STILL DEAD"}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Batch validation request failed: ${e.message}")
                // Don't fail silently — continue with next batch
            }
        }

        _streamCheckingStatus.value = null
        Log.d(TAG, "verifyBrokenChannelsViaWorker complete: $recovered recovered, $stillBroken still broken.")
    }

    /**
     * Lightweight change detection: calls /api/changes?since=<lastSyncTs> on the worker.
     * Returns true if the worker has new or removed channels since the last sync.
     * Fast (< 200ms) — safe to call on every app refresh without downloading full channel list.
     */
    suspend fun checkForChanges(workerBaseUrl: String, lastSyncTimestamp: Long): Boolean =
        withContext(Dispatchers.IO) {
            if (!isNetworkAvailable()) return@withContext false
            return@withContext try {
                val changesUrl = "$workerBaseUrl/api/changes?since=$lastSyncTimestamp"
                val request = Request.Builder()
                    .url(changesUrl)
                    .header("User-Agent", "TNi-App/1.0")
                    .build()
                streamCheckClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use false
                    val body = response.body?.string() ?: return@use false
                    val json = JSONObject(body)
                    json.optBoolean("changed", false)
                }
            } catch (e: Exception) {
                Log.w(TAG, "checkForChanges failed: ${e.message}")
                false
            }
        }

    fun syncWithCloudGist(url: String, force: Boolean = false, onComplete: (Boolean) -> Unit = {}) {
        if (_isSyncing.value) return
        val repo = repository ?: return

        syncScope.launch {
            _isSyncing.value = true
            _syncStatusMessage.value = "Contacting remote playlist provider..."
            try {
                val (updated, newChannelIds) = repo.fetchAndParseM3u(url, force)
                
                // Sync portion is complete! Call onComplete and turn off syncing indicator immediately
                // so the channels are displayed to the user instantly!
                _syncStatusMessage.value = "Playlist synced successfully!"
                _isSyncing.value = false
                onComplete(true)

                // Now run validation tasks in the background non-blockingly
                if (updated) {
                    if (newChannelIds.isNotEmpty()) {
                        launch {
                            verifyNewChannelsFirst(newChannelIds)
                        }
                    }
                    launch {
                        verifyBrokenChannelsViaWorker()
                    }
                } else {
                    launch {
                        verifyBrokenChannelsViaWorker()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Gist synchronization failed", e)
                _syncStatusMessage.value = "Offline / Connection failed. Loaded cached channels from offline database."
                _isSyncing.value = false
                onComplete(false)
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
        if (!isNetworkAvailable()) {
            Log.d(TAG, "verifyAllChannelsSuspend: Network is unavailable. Skipping background verification.")
            return@withContext true
        }
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
                    
                    if (!working) {
                        com.example.data.StreamLogManager.logError(
                            type = "Verification",
                            targetName = channel.name,
                            url = channel.streamUrl,
                            errorMessage = "Scheduled background check failed"
                        )
                    }
                    
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
            val (updated, newChannelIds) = repo.fetchAndParseM3u(url)
            if (updated) {
                if (newChannelIds.isNotEmpty()) {
                    verifyNewChannelsFirst(newChannelIds)
                }
            }
            // Always re-verify broken channels via the Worker batch API
            // (even on no-update syncs, broken channels might have recovered)
            verifyBrokenChannelsViaWorker()
            return@withContext updated
        } catch (e: Exception) {
            Log.e(TAG, "syncWithCloudGistSuspend failed for url: $url", e)
            return@withContext false
        }
    }
}
