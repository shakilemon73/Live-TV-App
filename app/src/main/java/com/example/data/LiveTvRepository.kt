package com.example.data

import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import androidx.room.withTransaction

class LiveTvRepository(private val dao: LiveTvDao, private val context: android.content.Context) {
    private val okHttpClient = com.example.data.CachedHttpClient.getClient(context)

    companion object {
        val CATEGORIZED_M3U_MAP = mapOf(
            "BDIX IPTV" to "https://github.com/abusaeeidx/Mrgify-BDIX-IPTV/raw/main/playlist.m3u",
            "Animation" to "https://github.com/shakilemon73/my-m3u-playlist/raw/refs/heads/main/channel_list/animation.m3u",
            "Auto" to "https://github.com/shakilemon73/my-m3u-playlist/raw/refs/heads/main/channel_list/auto.m3u",
            "Bangla Channel" to "https://github.com/shakilemon73/my-m3u-playlist/raw/refs/heads/main/channel_list/bangla-channel.m3u",
            "Bangla News" to "https://github.com/shakilemon73/my-m3u-playlist/raw/refs/heads/main/channel_list/bangla_news.m3u",
            "Business" to "https://github.com/shakilemon73/my-m3u-playlist/raw/refs/heads/main/channel_list/business.m3u",
            "Comedy" to "https://github.com/shakilemon73/my-m3u-playlist/raw/refs/heads/main/channel_list/comedy.m3u",
            "Cricket" to "https://github.com/shakilemon73/my-m3u-playlist/raw/refs/heads/main/channel_list/cricket.m3u",
            "Documentary" to "https://github.com/shakilemon73/my-m3u-playlist/raw/refs/heads/main/channel_list/documentary.m3u",
            "Entertainment" to "https://github.com/shakilemon73/my-m3u-playlist/raw/refs/heads/main/channel_list/entertainment.m3u",
            "International News" to "https://github.com/shakilemon73/my-m3u-playlist/raw/refs/heads/main/channel_list/international_news.m3u",
            "Kids" to "https://github.com/shakilemon73/my-m3u-playlist/raw/refs/heads/main/channel_list/kids.m3u",
            "Lifestyle" to "https://github.com/shakilemon73/my-m3u-playlist/raw/refs/heads/main/channel_list/lifestyle.m3u",
            "Movies" to "https://github.com/shakilemon73/my-m3u-playlist/raw/refs/heads/main/channel_list/movies.m3u",
            "Music" to "https://github.com/shakilemon73/my-m3u-playlist/raw/refs/heads/main/channel_list/music.m3u",
            "Religious" to "https://github.com/shakilemon73/my-m3u-playlist/raw/refs/heads/main/channel_list/religious.m3u",
            "Sports & Football" to "https://github.com/shakilemon73/my-m3u-playlist/raw/refs/heads/main/channel_list/sports_football.m3u",
            "Live Events" to "https://github.com/shakilemon73/my-m3u-playlist/raw/refs/heads/main/channel_list/live-event.m3u",
            "Doms9 Base" to "https://github.com/doms9/iptv/raw/refs/heads/default/M3U8/base.m3u8",
            "Doms9 US TV" to "https://github.com/doms9/iptv/raw/refs/heads/default/M3U8/TV.m3u8"
        )
    }

    val distinctActiveCategories: Flow<List<String>> = dao.getDistinctActiveCategories()
    val allCategories: Flow<List<CategoryEntity>> = dao.getAllCategories()
    val allChannels: Flow<List<ChannelEntity>> = dao.getAllChannels()
    val activeChannels: Flow<List<ChannelEntity>> = dao.getActiveChannels()
    val favoriteChannels: Flow<List<ChannelEntity>> = dao.getFavoriteChannels()
    val allRecordings: Flow<List<RecordingEntity>> = dao.getAllRecordings()
    val cachedLiveEvents: Flow<List<CachedLiveEventEntity>> = dao.getAllCachedLiveEventsFlow()
    val interestedLiveEvents: Flow<List<InterestedEventEntity>> = dao.getAllInterestedEventsFlow()

    suspend fun getCachedLiveEvents(): List<CachedLiveEventEntity> = withContext(Dispatchers.IO) {
        dao.getAllCachedLiveEvents()
    }

    suspend fun saveCachedLiveEvents(events: List<CachedLiveEventEntity>) = withContext(Dispatchers.IO) {
        dao.replaceCachedLiveEvents(events)
    }

    suspend fun getInterestedLiveEvents(): List<InterestedEventEntity> = withContext(Dispatchers.IO) {
        dao.getAllInterestedEvents()
    }

    suspend fun getInterestedEventById(id: String): InterestedEventEntity? = withContext(Dispatchers.IO) {
        dao.getInterestedEventById(id)
    }

    suspend fun saveInterestedLiveEvent(event: InterestedEventEntity) = withContext(Dispatchers.IO) {
        dao.insertInterestedEvent(event)
    }

    suspend fun deleteInterestedLiveEventById(id: String) = withContext(Dispatchers.IO) {
        dao.deleteInterestedEventById(id)
    }

    suspend fun updateInterestedEventNotified(id: String, isNotified: Boolean) = withContext(Dispatchers.IO) {
        dao.updateInterestedEventNotified(id, isNotified)
    }

    suspend fun insertRecording(recording: RecordingEntity): Long {
        return dao.insertRecording(recording)
    }

    suspend fun deleteRecording(recording: RecordingEntity) {
        dao.deleteRecording(recording)
    }

    fun getChannelsByCategory(categoryId: Int): Flow<List<ChannelEntity>> {
        return dao.getChannelsByCategory(categoryId)
    }

    suspend fun insertCategory(category: CategoryEntity): Long {
        return dao.insertCategory(category)
    }

    suspend fun updateCategory(category: CategoryEntity) {
        dao.updateCategory(category)
    }

    suspend fun deleteCategory(category: CategoryEntity) {
        dao.deleteCategory(category)
    }

    // --- EPG Programs ---
    suspend fun syncEpg(epgUrl: String) = withContext(Dispatchers.IO) {
        try {
            Log.d("LiveTvRepository", "Starting EPG Sync for URL: $epgUrl")
            val xmltv = EpgParserService.fetchXmltvContent(context, epgUrl)
            if (!xmltv.isNullOrBlank()) {
                val programs = EpgParserService.parseXmltvText(xmltv)
                if (programs.isNotEmpty()) {
                    val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
                    dao.deleteOldEpgPrograms(cutoff)
                    dao.insertEpgPrograms(programs)
                    Log.d("LiveTvRepository", "Successfully synced ${programs.size} EPG programs")
                } else {
                    Log.w("LiveTvRepository", "Parsed 0 programs from XML")
                }
            } else {
                Log.w("LiveTvRepository", "Failed to fetch XMLTV EPG content")
            }
        } catch (e: Exception) {
            Log.e("LiveTvRepository", "Error syncing EPG", e)
        }
    }

    fun getActiveEpgProgramsFlow(now: Long): Flow<List<EpgProgramEntity>> {
        return dao.getActiveEpgProgramsFlow(now)
    }

    fun getEpgProgramsForChannelFlow(tvgId: String, channelName: String): Flow<List<EpgProgramEntity>> {
        val searchIds = listOf(tvgId, channelName).filter { it.isNotBlank() }
        return dao.getEpgProgramsForChannelFlow(searchIds, System.currentTimeMillis())
    }

    fun getCurrentProgramForChannelFlow(tvgId: String, channelName: String): Flow<EpgProgramEntity?> {
        val searchIds = listOf(tvgId, channelName).filter { it.isNotBlank() }
        return dao.getCurrentProgramForChannelFlow(searchIds, System.currentTimeMillis())
    }

    suspend fun getCurrentProgramForChannel(tvgId: String, channelName: String): EpgProgramEntity? {
        val searchIds = listOf(tvgId, channelName).filter { it.isNotBlank() }
        return dao.getCurrentProgramForChannel(searchIds, System.currentTimeMillis())
    }

    private fun encryptChannelUrls(channel: ChannelEntity): ChannelEntity {
        val encStreamUrl = if (!channel.streamUrl.startsWith("encrypted://")) {
            StreamDecryptionUtility.encrypt(channel.streamUrl)
        } else {
            channel.streamUrl
        }
        val encSources = channel.playbackSources.map { src ->
            if (!src.url.startsWith("encrypted://")) {
                src.copy(url = StreamDecryptionUtility.encrypt(src.url))
            } else {
                src
            }
        }
        return channel.copy(streamUrl = encStreamUrl, playbackSources = encSources)
    }

    suspend fun insertChannel(channel: ChannelEntity): Long {
        val resolved = if (channel.category.isEmpty() && channel.categoryId != 0) {
            val cat = dao.getCategoryById(channel.categoryId)
            if (cat != null) channel.copy(category = cat.name) else channel
        } else {
            channel
        }
        return dao.insertChannel(encryptChannelUrls(resolved))
    }

    suspend fun updateChannel(channel: ChannelEntity) {
        val resolved = if (channel.category.isEmpty() && channel.categoryId != 0) {
            val cat = dao.getCategoryById(channel.categoryId)
            if (cat != null) channel.copy(category = cat.name) else channel
        } else {
            channel
        }
        dao.updateChannel(encryptChannelUrls(resolved))
    }

    suspend fun deleteChannel(channel: ChannelEntity) {
        dao.deleteChannel(channel)
    }

    suspend fun toggleFavorite(channelId: Int, isFavorite: Boolean) {
        dao.updateFavoriteStatus(channelId, isFavorite)
    }

    suspend fun updateChannelBrokenStatus(channelId: Int, isBroken: Boolean, lastChecked: Long = System.currentTimeMillis()) {
        dao.updateChannelBrokenStatus(channelId, isBroken, lastChecked)
    }

    suspend fun updateChannelsBrokenStatuses(brokenIds: List<Int>, workingIds: List<Int>, lastChecked: Long = System.currentTimeMillis()) {
        dao.updateChannelsBrokenStatuses(brokenIds, workingIds, lastChecked)
    }

    suspend fun parseAndInsertM3uText(m3uText: String) = withContext(Dispatchers.IO) {
        try {
            val tempCategories = mutableMapOf<String, Int>()
            val channelsToInsert = mutableListOf<ChannelEntity>()
            val maxChannels = 1500

            // Clear existing channels and categories BEFORE downloading/parsing
            dao.deleteAllChannels()
            dao.deleteAllCategories()

            val parsedList = M3uParserService.parseM3uText(m3uText)
            val extractedEpgUrl = M3uParserService.lastTvgUrl
            if (extractedEpgUrl != null) {
                M3uParserService.lastTvgUrl = null
                kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                    syncEpg(extractedEpgUrl)
                }
            }
            for (parsed in parsedList) {
                if (channelsToInsert.size >= maxChannels) break

                val detectedGroup = detectCategory(parsed.name, parsed.groupTitle)
                var catId = tempCategories[detectedGroup]
                if (catId == null) {
                    val newId = dao.insertCategory(CategoryEntity(name = detectedGroup))
                    catId = newId.toInt()
                    tempCategories[detectedGroup] = catId
                }

                channelsToInsert.add(
                    encryptChannelUrls(
                        ChannelEntity(
                            name = parsed.name,
                            streamUrl = parsed.streamUrl,
                            logoUrl = parsed.logoUrl,
                            category = detectedGroup,
                            categoryId = catId,
                            description = parsed.description,
                            tvgId = parsed.tvgId,
                            tvgName = parsed.tvgName
                        )
                    )
                )

                if (channelsToInsert.size >= 100) {
                    dao.insertChannels(channelsToInsert.toList())
                    channelsToInsert.clear()
                }
            }

            if (channelsToInsert.isNotEmpty()) {
                dao.insertChannels(channelsToInsert)
            } else {
                val categories = dao.getAllCategoriesList()
                if (categories.isEmpty()) {
                    seedFallbackData()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            val categories = dao.getAllCategoriesList()
            if (categories.isEmpty()) {
                seedFallbackData()
            }
        }
    }

    suspend fun seedDatabaseIfEmpty() {
        val channels = dao.getAllChannelsList()
        if (channels.isEmpty()) {
            seedFallbackData()
        }
    }

    suspend fun resetToCuratedStreams() = withContext(Dispatchers.IO) {
        dao.deleteAllChannels()
        dao.deleteAllCategories()
        seedFallbackData()
    }

    private suspend fun performSmartDeltaSync(parsedChannels: List<ChannelEntity>) = withContext(Dispatchers.IO) {
        if (parsedChannels.isEmpty()) return@withContext

        val db = AppDatabase.getDatabase(context)
        db.withTransaction {
            // Get all current categories to look up their names
            val categoryMap = dao.getAllCategoriesList().associate { it.id to it.name }

            // Group parsed channels by their normalized name
            val parsedGrouped = parsedChannels.groupBy { ChannelNameNormalizer.sanitizeChannelName(it.name).lowercase() }

            // Get all current channels in the database
            val existingChannels = dao.getAllChannelsList()

            // Map existing channels by normalized base name
            val existingByNormalizedName = existingChannels.associateBy { ChannelNameNormalizer.sanitizeChannelName(it.name).lowercase() }

            val toInsert = mutableListOf<ChannelEntity>()
            val toUpdate = mutableListOf<ChannelEntity>()

            // Keep track of which categories were updated in this sync
            val updatedCategoryIds = parsedChannels.map { it.categoryId }.toSet()

            // Keep track of all updated/parsed stream URLs to identify dropped/broken ones
            val parsedStreamUrls = parsedChannels.map { it.streamUrl }.toSet()

            for ((normalizedName, channelsInGroup) in parsedGrouped) {
                val existing = existingByNormalizedName[normalizedName]

                val bestChannelForCategory = channelsInGroup.firstOrNull { ch ->
                    val catName = categoryMap[ch.categoryId] ?: ""
                    catName.isNotBlank() && !catName.equals("BDIX IPTV", ignoreCase = true) && !catName.equals("General", ignoreCase = true)
                } ?: channelsInGroup.first()

                if (existing != null) {
                    // Build a prioritized list of playback sources
                    val updatedSources = mutableListOf<PlaybackSource>()
                    
                    // 1. Add all newly parsed sources from this sync at the very front as prioritized sources
                    channelsInGroup.forEachIndexed { index, ch ->
                        updatedSources.add(PlaybackSource(ch.streamUrl, "Source ${index + 1}", false))
                    }
                    
                    // 2. Append any non-duplicate legacy sources from the database as backup sources
                    val existingSources = existing.playbackSources.toMutableList()
                    if (existingSources.isEmpty() && existing.streamUrl.isNotBlank()) {
                        existingSources.add(PlaybackSource(existing.streamUrl, "Source 1", existing.isBroken))
                    }
                    
                    var sourceCounter = updatedSources.size + 1
                    existingSources.forEach { oldSrc ->
                        if (updatedSources.none { it.url == oldSrc.url }) {
                            updatedSources.add(oldSrc.copy(name = "Backup Source ${sourceCounter++}"))
                        }
                    }

                    // The primary streamUrl is always the first parsed streamUrl from the latest sync
                    val primaryStreamUrl = channelsInGroup.first().streamUrl

                    val updated = existing.copy(
                        categoryId = bestChannelForCategory.categoryId, // Ensure channel category is correctly mapped/updated!
                        category = bestChannelForCategory.category.ifEmpty { existing.category },
                        logoUrl = bestChannelForCategory.logoUrl.ifEmpty { existing.logoUrl },
                        description = bestChannelForCategory.description.ifEmpty { existing.description },
                        tvgId = bestChannelForCategory.tvgId.ifEmpty { existing.tvgId },
                        tvgName = bestChannelForCategory.tvgName.ifEmpty { existing.tvgName },
                        isBroken = false, // Got a fresh update from the M3U, so mark active
                        lastChecked = System.currentTimeMillis(),
                        streamUrl = primaryStreamUrl,
                        playbackSources = updatedSources
                    )
                    toUpdate.add(updated)
                } else {
                    // Completely new channel group
                    val representative = bestChannelForCategory
                    val sources = channelsInGroup.mapIndexed { index, ch ->
                        PlaybackSource(ch.streamUrl, "Source ${index + 1}", false)
                    }

                    val newChannel = representative.copy(
                        playbackSources = sources,
                        streamUrl = sources.firstOrNull()?.url ?: representative.streamUrl,
                        isBroken = false,
                        lastChecked = System.currentTimeMillis()
                    )
                    toInsert.add(newChannel)
                }
            }

            // 4. Handle dropped sources / broken status for updated playlists (to avoid cross-playlist category interference)
            val updatedPlaylistUrls = parsedChannels.map { it.playlistUrl }.filter { it.isNotEmpty() }.toSet()
            for (existing in existingChannels) {
                if (existing.playlistUrl.isNotEmpty() && existing.playlistUrl in updatedPlaylistUrls) {
                    val existingSources = existing.playbackSources.toMutableList()
                    if (existingSources.isEmpty() && existing.streamUrl.isNotBlank()) {
                        existingSources.add(PlaybackSource(existing.streamUrl, "Source 1", existing.isBroken))
                    }

                    var modified = false
                    val updatedSources = existingSources.map { source ->
                        if (!parsedStreamUrls.contains(source.url)) {
                            modified = true
                            source.copy(isBroken = true)
                        } else {
                            source
                        }
                    }

                    if (modified) {
                        val allBroken = updatedSources.all { it.isBroken }
                        val updatedChannel = existing.copy(
                            playbackSources = updatedSources,
                            isBroken = allBroken,
                            lastChecked = System.currentTimeMillis()
                        )
                        val index = toUpdate.indexOfFirst { it.id == existing.id }
                        if (index != -1) {
                            toUpdate[index] = toUpdate[index].copy(
                                playbackSources = updatedSources,
                                isBroken = allBroken
                            )
                        } else {
                            toUpdate.add(updatedChannel)
                        }
                    }
                }
            }

            // Perform updates and insertions in chunks/batches
            if (toInsert.isNotEmpty()) {
                toInsert.chunked(250).forEach { chunk ->
                    dao.insertChannels(chunk)
                }
            }
            if (toUpdate.isNotEmpty()) {
                toUpdate.chunked(250).forEach { chunk ->
                    dao.updateChannels(chunk)
                }
            }

            // Clean up stale channels (channels that exist in the DB but were not part of this sync session)
            val syncedPlaylistUrls = parsedChannels.map { it.playlistUrl }.filter { it.isNotBlank() }.toSet()
            val updatedIds = toUpdate.map { it.id }.toSet()
            val staleChannels = existingChannels.filter { it.playlistUrl in syncedPlaylistUrls && it.playlistUrl.isNotBlank() && it.id !in updatedIds }
            if (staleChannels.isNotEmpty()) {
                staleChannels.chunked(250).forEach { chunk ->
                    dao.deleteChannels(chunk)
                }
            }

            // Clean up any empty categories
            val remainingChannels = dao.getAllChannelsList()
            val activeCategoryIds = remainingChannels.map { it.categoryId }.toSet()
            val allCategories = dao.getAllCategoriesList()
            val emptyCategories = allCategories.filter { it.id !in activeCategoryIds }
            if (emptyCategories.isNotEmpty()) {
                emptyCategories.forEach { cat ->
                    dao.deleteCategory(cat)
                }
            }
        }
    }

    suspend fun fetchAndParseServerDrivenJson(url: String, force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val requestBuilder = Request.Builder().url(url)
        
        if (!force) {
            val cachedMeta = dao.getM3uMeta(url)
            if (cachedMeta != null) {
                if (!cachedMeta.eTag.isNullOrBlank()) {
                    requestBuilder.header("If-None-Match", cachedMeta.eTag)
                }
                if (!cachedMeta.lastModified.isNullOrBlank()) {
                    requestBuilder.header("If-Modified-Since", cachedMeta.lastModified)
                }
            }
        }
        
        val request = requestBuilder.build()
        try {
            okHttpClient.newCall(request).execute().use { response ->
                val responseCode = response.code
                if (responseCode == 304) {
                    Log.i("LiveTvRepository", "HTTP 304 Not Modified for Server JSON: $url. Trusting local database cache.")
                    return@withContext false
                }
                
                if (!response.isSuccessful) {
                    Log.e("LiveTvRepository", "Failed to fetch server JSON: HTTP $responseCode")
                    return@withContext false
                }
                
                val bodyText = response.body?.string() ?: return@withContext false
                
                val moshi = Moshi.Builder()
                    .addLast(KotlinJsonAdapterFactory())
                    .build()
                val adapter = moshi.adapter(UnifiedChannelsResponse::class.java)
                val responseObj = adapter.fromJson(bodyText) ?: return@withContext false
                
                val categoriesToInsert = responseObj.categories.map { cat ->
                    CategoryEntity(id = cat.id, name = cat.name)
                }
                
                val channelsToInsert = responseObj.channels.map { ch ->
                    val streams = ch.streams.map { s ->
                        PlaybackSource(url = s.url, name = s.name, isBroken = s.isBroken)
                    }
                    ChannelEntity(
                        name = ch.name,
                        streamUrl = ch.streams.firstOrNull()?.url ?: "",
                        logoUrl = ch.logoUrl ?: "",
                        categoryId = ch.categoryId,
                        category = ch.category,
                        description = ch.description ?: "",
                        tvgId = ch.tvgId ?: "",
                        tvgName = ch.tvgName ?: "",
                        playbackSources = streams,
                        playlistUrl = url,
                        lastChecked = System.currentTimeMillis()
                    )
                }
                
                dao.clearAndInsertUnifiedChannels(categoriesToInsert, channelsToInsert)
                
                val newEtag = response.header("ETag")
                val newLastModified = response.header("Last-Modified")
                dao.insertM3uMeta(M3uMetaEntity(url = url, eTag = newEtag, lastModified = newLastModified))
                
                Log.i("LiveTvRepository", "Successfully synced and imported ${responseObj.channels.size} server-driven channels.")
                return@withContext true
            }
        } catch (e: Exception) {
            Log.e("LiveTvRepository", "Error fetching/parsing server JSON from $url", e)
            return@withContext false
        }
    }

    suspend fun fetchAndParseM3u(m3uUrl: String = "https://iptv-org.github.io/iptv/index.m3u", force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val trimmedUrl = m3uUrl.trim()
        if (trimmedUrl.endsWith(".json", ignoreCase = true) || trimmedUrl.contains("/channels.json")) {
            val jsonSuccess = fetchAndParseServerDrivenJson(trimmedUrl, force)
            return@withContext jsonSuccess
        }

        var anyModified = false
        try {
            val existingCategories = dao.getAllCategoriesList()
            val tempCategories = existingCategories.associate { it.name to it.id }.toMutableMap()

            val existingChannels = dao.getAllChannelsList()
            val forceSync = force || existingChannels.isEmpty()
            val parsedStreamUrlsInSession = mutableSetOf<String>()

            val channelsToInsert = mutableListOf<ChannelEntity>()
            val maxChannels = 3000 // Increased limit to hold categorised lists comfortably

            val rawUrls = m3uUrl.split(Regex("[,;\\n\\r]+")).map { it.trim() }.filter { it.isNotEmpty() }
            val targetUrls = if (rawUrls.isEmpty()) {
                listOf("default")
            } else {
                rawUrls
            }

            for (url in targetUrls) {
                val isDefaultOrInternalUrl = url.equals("default", ignoreCase = true) ||
                                             url.equals("all", ignoreCase = true) ||
                                             url.isBlank() ||
                                             url == "https://iptv-org.github.io/iptv/index.m3u" ||
                                             url == "https://github.com/abusaeeidx/Mrgify-BDIX-IPTV/raw/main/playlist.m3u"

                if (isDefaultOrInternalUrl) {
                    for ((categoryName, categoryUrl) in CATEGORIZED_M3U_MAP) {
                        try {
                            val normalizedUrl = normalizeUrl(categoryUrl)
                            val modified = downloadAndParseM3uContent(
                                urlStr = normalizedUrl,
                                tempCategories = tempCategories,
                                channelsToInsert = channelsToInsert,
                                maxChannels = maxChannels,
                                isSubPlaylist = false,
                                categoryOverride = categoryName,
                                parsedStreamUrlsInSession = parsedStreamUrlsInSession,
                                playlistUrl = normalizedUrl,
                                force = forceSync
                            )
                            if (modified) {
                                anyModified = true
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                } else {
                    if (url.startsWith("http://") || url.startsWith("https://")) {
                        try {
                            val normalizedUrl = normalizeUrl(url)
                            val modified = downloadAndParseM3uContent(
                                urlStr = normalizedUrl,
                                tempCategories = tempCategories,
                                channelsToInsert = channelsToInsert,
                                maxChannels = maxChannels,
                                isSubPlaylist = false,
                                categoryOverride = null,
                                parsedStreamUrlsInSession = parsedStreamUrlsInSession,
                                playlistUrl = normalizedUrl,
                                force = forceSync
                            )
                            if (modified) {
                                anyModified = true
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }

            if (channelsToInsert.isNotEmpty()) {
                performSmartDeltaSync(channelsToInsert)
                return@withContext true
            } else {
                // Last resort fallback only if download succeeded but returned absolutely nothing
                val categories = dao.getAllCategoriesList()
                if (categories.isEmpty()) {
                    seedFallbackData()
                    return@withContext true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            val categories = dao.getAllCategoriesList()
            if (categories.isEmpty()) {
                seedFallbackData()
                return@withContext true
            }
        }
        return@withContext anyModified
    }

    private fun normalizeUrl(url: String): String {
        var u = url.trim()
        if (u.contains("gist.github.com")) {
            if (!u.contains("gist.githubusercontent.com")) {
                u = u.replace("gist.github.com", "gist.githubusercontent.com")
            }
            if (!u.contains("/raw")) {
                u = if (u.endsWith("/")) u.substring(0, u.length - 1) else u
                u = "$u/raw"
            }
        } else if (u.contains("github.com")) {
            if (u.contains("/blob/")) {
                u = u.replace("github.com", "raw.githubusercontent.com")
                     .replace("/blob/", "/")
            } else if (u.contains("/raw/")) {
                u = u.replace("github.com", "raw.githubusercontent.com")
                     .replace("/raw/", "/")
            }
        } else if (u.contains("pastebin.com")) {
            if (!u.contains("/raw/")) {
                // E.g. https://pastebin.com/xxxx -> https://pastebin.com/raw/xxxx
                val lastSlash = u.lastIndexOf('/')
                if (lastSlash != -1 && lastSlash < u.length - 1) {
                    val code = u.substring(lastSlash + 1)
                    if (code.isNotBlank() && !code.contains("raw")) {
                        u = "https://pastebin.com/raw/$code"
                    }
                }
            }
        }
        return u
    }

    private data class RawChannel(
        val name: String,
        val streamUrl: String,
        val logoUrl: String,
        val group: String,
        val tvgId: String,
        val tvgName: String,
        val description: String
    )

    private suspend fun downloadAndParseM3uContent(
        urlStr: String,
        tempCategories: MutableMap<String, Int>,
        channelsToInsert: MutableList<ChannelEntity>,
        maxChannels: Int,
        isSubPlaylist: Boolean = false,
        categoryOverride: String? = null,
        parsedStreamUrlsInSession: MutableSet<String> = mutableSetOf(),
        playlistUrl: String,
        force: Boolean = false
    ): Boolean {
        val requestBuilder = Request.Builder().url(urlStr)

        if (!isSubPlaylist && !force) {
            val cachedMeta = dao.getM3uMeta(urlStr)
            if (cachedMeta != null) {
                if (!cachedMeta.eTag.isNullOrBlank()) {
                    requestBuilder.header("If-None-Match", cachedMeta.eTag)
                }
                if (!cachedMeta.lastModified.isNullOrBlank()) {
                    requestBuilder.header("If-Modified-Since", cachedMeta.lastModified)
                }
            }
        }

        val request = requestBuilder.build()
        try {
            return okHttpClient.newCall(request).execute().use { response ->
                val responseCode = response.code
                if (!isSubPlaylist && responseCode == 304) {
                    println("HTTP 304 Not Modified for $urlStr. Aborting parse and trusting local cache.")
                    return@use false
                }

                if (responseCode != 200) {
                    StreamLogManager.logError(
                        type = "Playlist Fetch",
                        targetName = categoryOverride ?: "M3U Playlist",
                        url = urlStr,
                        errorMessage = "HTTP Error $responseCode"
                    )
                    return@use false
                }

                if (!isSubPlaylist) {
                    val newEtag = response.header("ETag")
                    val newLastModified = response.header("Last-Modified")
                    if (newEtag != null || newLastModified != null) {
                        dao.insertM3uMeta(M3uMetaEntity(urlStr, newEtag, newLastModified))
                    }
                }

                val body = response.body ?: return@use false
                // Securely save downloaded raw content using memory-only cache & protected local encrypted directory
                val rawText = body.string()
                M3uParserService.savePlaylistSecurely(context, urlStr, rawText)
                
                val parsedList = java.io.StringReader(rawText).use { reader ->
                    M3uParserService.parseM3uReader(reader)
                }
                val extractedEpgUrl = M3uParserService.lastTvgUrl
                if (extractedEpgUrl != null) {
                    M3uParserService.lastTvgUrl = null
                    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                        syncEpg(extractedEpgUrl)
                    }
                }

                val nestedUrls = mutableListOf<String>()
                val rawChannels = mutableListOf<RawChannel>()

                for (parsed in parsedList) {
                    if (parsed.streamUrl.endsWith(".m3u")) {
                        if (!isSubPlaylist) {
                            nestedUrls.add(parsed.streamUrl)
                        }
                    } else {
                        if (parsedStreamUrlsInSession.contains(parsed.streamUrl)) {
                            continue
                        }
                        parsedStreamUrlsInSession.add(parsed.streamUrl)

                        rawChannels.add(
                            RawChannel(
                                name = parsed.name,
                                streamUrl = parsed.streamUrl,
                                logoUrl = parsed.logoUrl,
                                group = parsed.groupTitle,
                                tvgId = parsed.tvgId,
                                tvgName = parsed.tvgName,
                                description = parsed.description
                            )
                        )
                    }
                }

                // Process the raw channels concurrently using a thread pool (Dispatchers.Default)
                val classifiedChannels = withContext(Dispatchers.Default) {
                    rawChannels.chunked(250).map { chunk ->
                        async {
                            chunk.map { raw ->
                                val detectedGroup = ChannelClassifier.classify(raw.name, raw.group, categoryOverride)
                                val categoryName = detectedGroup.trim()
                                Triple(raw, categoryName, detectedGroup)
                            }
                        }
                    }.awaitAll().flatten()
                }

                // Shifting back to Dispatchers.IO (current context) to resolve IDs & save to list
                for ((raw, categoryName, _) in classifiedChannels) {
                    if (channelsToInsert.size >= maxChannels) break

                    val matchedKey = tempCategories.keys.firstOrNull { it.equals(categoryName, ignoreCase = true) }
                    var catId = if (matchedKey != null) tempCategories[matchedKey] else null

                    if (catId == null) {
                        val newId = dao.insertCategory(CategoryEntity(name = categoryName))
                        catId = newId.toInt()
                        tempCategories[categoryName] = catId
                    }

                    channelsToInsert.add(
                        encryptChannelUrls(
                            ChannelEntity(
                                name = raw.name,
                                streamUrl = raw.streamUrl,
                                logoUrl = raw.logoUrl.ifEmpty { "https://images.unsplash.com/photo-1542038784456-1ea8e935640e?w=120&q=80" },
                                categoryId = catId,
                                category = categoryName,
                                description = raw.description,
                                tvgId = raw.tvgId,
                                tvgName = raw.tvgName,
                                playlistUrl = playlistUrl
                            )
                        )
                    )
                }

                // If index.m3u didn't contain direct channels, parse sub-playlists to pull streams
                if (nestedUrls.isNotEmpty() && !isSubPlaylist && (channelsToInsert.size < maxChannels)) {
                    val preferredSubplaylists = nestedUrls.filter { subUrl ->
                        val lower = subUrl.lowercase()
                        lower.contains("/us.m3u") || lower.contains("/uk.m3u") || lower.contains("/ca.m3u") || 
                        lower.contains("/fr.m3u") || lower.contains("/de.m3u") || lower.contains("/jp.m3u") || 
                        lower.contains("/in.m3u") || lower.contains("/es.m3u") || lower.contains("/it.m3u")
                    }.take(4)

                    val finalSubplaylists = if (preferredSubplaylists.size >= 2) {
                        preferredSubplaylists
                    } else {
                        nestedUrls.take(4)
                    }

                    for (subUrl in finalSubplaylists) {
                        try {
                            downloadAndParseM3uContent(
                                urlStr = subUrl,
                                tempCategories = tempCategories,
                                channelsToInsert = channelsToInsert,
                                maxChannels = maxChannels,
                                isSubPlaylist = true,
                                categoryOverride = categoryOverride,
                                parsedStreamUrlsInSession = parsedStreamUrlsInSession,
                                playlistUrl = playlistUrl,
                                force = force
                            )
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        if (channelsToInsert.size >= maxChannels) break
                    }
                }
                return@use true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            StreamLogManager.logError(
                type = "Playlist Fetch",
                targetName = categoryOverride ?: "M3U Playlist",
                url = urlStr,
                errorMessage = e.localizedMessage ?: "Parsing/Network Error"
            )
        }
        return false
    }

    private suspend fun seedFallbackData() {
        val categories = dao.getAllCategoriesList()
        if (categories.isEmpty()) {
            val newsId = dao.insertCategory(CategoryEntity(name = "International News")).toInt()
            val sportsId = dao.insertCategory(CategoryEntity(name = "Sports & Football")).toInt()
            val entertainmentId = dao.insertCategory(CategoryEntity(name = "Entertainment")).toInt()
            val documentaryId = dao.insertCategory(CategoryEntity(name = "Documentary")).toInt()
            val kidsId = dao.insertCategory(CategoryEntity(name = "Kids")).toInt()
            val musicId = dao.insertCategory(CategoryEntity(name = "Music")).toInt()
            
            val curatedChannels = listOf(
                // Live Event
                ChannelEntity(
                    name = "NASA Live Space Events",
                    streamUrl = "https://ntvlive.akamaized.net/hls/live/2014027/NASA-NTV1-HLS/master.m3u8",
                    logoUrl = "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=120&q=80",
                    categoryId = documentaryId,
                    description = "Live coverage of NASA space launches, missions, press conferences, and cosmic events direct from outer space.",
                    isFavorite = true
                ),

                // News
                ChannelEntity(
                    name = "NHK World Japan",
                    streamUrl = "https://nhkworld.akamaized.net/hls/live/2007470/live_wa/index.m3u8",
                    logoUrl = "https://images.unsplash.com/photo-1542038784456-1ea8e935640e?w=120&q=80",
                    categoryId = newsId,
                    description = "NHK World-Japan is the international service of Japan's public broadcaster NHK, providing the latest news on Japan and Asia.",
                    isFavorite = true
                ),
                ChannelEntity(
                    name = "France 24 English",
                    streamUrl = "https://static.france24.com/live/F24_EN_LO_HLS/live_web.m3u8",
                    logoUrl = "https://images.unsplash.com/photo-1585829365295-ab7cd400c167?w=120&q=80",
                    categoryId = newsId,
                    description = "France 24 is a French international news television network based in Paris, broadcasting in English.",
                    isFavorite = true
                ),
                ChannelEntity(
                    name = "DW English",
                    streamUrl = "https://dwamdstream102.akamaized.net/hls/live/2015532/dwamdstream102/index.m3u8",
                    logoUrl = "https://images.unsplash.com/photo-1504711434969-e33886168f5c?w=120&q=80",
                    categoryId = newsId,
                    description = "Deutsche Welle is a German public state-owned international broadcaster, offering live news and analysis.",
                    isFavorite = false
                ),
                ChannelEntity(
                    name = "Al Jazeera English",
                    streamUrl = "https://live-lh.aljazeera.net/i/AJE_delivery@136007/master.m3u8",
                    logoUrl = "https://images.unsplash.com/photo-1526470608268-f674ce90ebd4?w=120&q=80",
                    categoryId = newsId,
                    description = "Al Jazeera English is an international 24-hour English-language news channel owned by the Al Jazeera Media Network.",
                    isFavorite = false
                ),
                ChannelEntity(
                    name = "Euronews English",
                    streamUrl = "https://euronews-eng.live.cdn.hexaglobe.net/euronews-eng_edge.smil/playlist.m3u8",
                    logoUrl = "https://images.unsplash.com/photo-1461360228754-6e81c478b882?w=120&q=80",
                    categoryId = newsId,
                    description = "Euronews is a European television news network, providing news from a European perspective.",
                    isFavorite = false
                ),
                ChannelEntity(
                    name = "CBS News Live",
                    streamUrl = "https://cbsn-us.cbsnstream.cbsnews.com/main/manifest.m3u8",
                    logoUrl = "https://images.unsplash.com/photo-1557804506-669a67965ba0?w=120&q=80",
                    categoryId = newsId,
                    description = "CBS News Live is a 24/7 digital streaming news network from CBS, offering breaking news and reports.",
                    isFavorite = false
                ),
                ChannelEntity(
                    name = "Sky News UK",
                    streamUrl = "https://skynews-skynews-main-1-skynews-main-gb.bt.ottera.tv/playlist.m3u8",
                    logoUrl = "https://images.unsplash.com/photo-1588681664899-f142ff2241b3?w=120&q=80",
                    categoryId = newsId,
                    description = "Sky News is a British free-to-air television news channel and digital media outlet.",
                    isFavorite = false
                ),

                // Sports
                ChannelEntity(
                    name = "Red Bull TV",
                    streamUrl = "https://rbmn-live.akamaized.net/hls/live/590964/sports/sports_1.m3u8",
                    logoUrl = "https://images.unsplash.com/photo-1563811771046-ba984ff30900?w=120&q=80",
                    categoryId = sportsId,
                    description = "Globally available sports, lifestyle and music content, including live event broadcasts.",
                    isFavorite = true
                ),
                ChannelEntity(
                    name = "SportsGrid",
                    streamUrl = "https://sportsgrid-sportsgrid-1-us.ottera.tv/playlist.m3u8",
                    logoUrl = "https://images.unsplash.com/photo-1508098682722-e99c43a406b2?w=120&q=80",
                    categoryId = sportsId,
                    description = "SportsGrid is a 24-hour streaming sports network featuring betting analysis, real-time graphics, and reporting.",
                    isFavorite = false
                ),
                ChannelEntity(
                    name = "Origin Sports",
                    streamUrl = "https://originsports-originsports-1-us.ottera.tv/playlist.m3u8",
                    logoUrl = "https://images.unsplash.com/photo-1459865264687-595d652de67e?w=120&q=80",
                    categoryId = sportsId,
                    description = "Classic games, legendary players, and analysis focusing on the college and university sports history.",
                    isFavorite = false
                ),
                ChannelEntity(
                    name = "WPT Poker Tour",
                    streamUrl = "https://worldpokertour-worldpokertour-1-us.ottera.tv/playlist.m3u8",
                    logoUrl = "https://images.unsplash.com/photo-1511193311914-0346f16efe90?w=120&q=80",
                    categoryId = sportsId,
                    description = "24/7 poker entertainment featuring the greatest tournaments and players from the World Poker Tour.",
                    isFavorite = false
                ),

                // Entertainment & Movies
                ChannelEntity(
                    name = "Runtime Free Movies",
                    streamUrl = "https://runtime-runtime-1-us.ottera.tv/playlist.m3u8",
                    logoUrl = "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=120&q=80",
                    categoryId = entertainmentId,
                    description = "Runtime is your destination for free movies and award-winning television series.",
                    isFavorite = true
                ),
                ChannelEntity(
                    name = "FilmRise Free Movies",
                    streamUrl = "https://filmrise-filmrisefreemovies-1-us.ottera.tv/playlist.m3u8",
                    logoUrl = "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=120&q=80",
                    categoryId = entertainmentId,
                    description = "Free full-length feature films across popular genres including drama, comedy, romance, and thriller.",
                    isFavorite = false
                ),
                ChannelEntity(
                    name = "FilmRise Action",
                    streamUrl = "https://filmrise-filmriseaction-1-us.ottera.tv/playlist.m3u8",
                    logoUrl = "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=120&q=80",
                    categoryId = entertainmentId,
                    description = "Explosive action-packed movies and thrilling television series streaming 24/7.",
                    isFavorite = false
                ),
                ChannelEntity(
                    name = "Shout! Factory TV",
                    streamUrl = "https://shout-shoutfactory-1-us.ottera.tv/playlist.m3u8",
                    logoUrl = "https://images.unsplash.com/photo-1594909122845-11baa439b7bf?w=120&q=80",
                    categoryId = entertainmentId,
                    description = "Pop culture classics, cult films, comedy shows, and unique documentaries curated for fans.",
                    isFavorite = false
                ),

                // Science & Space
                ChannelEntity(
                    name = "NASA TV",
                    streamUrl = "https://ntv1.akamaized.net/hls/live/2012127/NASA-NTV1-HLS/master.m3u8",
                    logoUrl = "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=120&q=80",
                    categoryId = documentaryId,
                    description = "Live coverage of spacecraft launches, space station activities, science missions, and space education.",
                    isFavorite = true
                ),
                ChannelEntity(
                    name = "NASA TV Media",
                    streamUrl = "https://ntv2.akamaized.net/hls/live/2012128/NASA-NTV2-HLS/master.m3u8",
                    logoUrl = "https://images.unsplash.com/photo-1446776811953-b23d57bd21aa?w=120&q=80",
                    categoryId = documentaryId,
                    description = "Live imagery from the ISS, robotic explorers, press conferences, and space science documentaries.",
                    isFavorite = false
                ),

                // Kids & Animation
                ChannelEntity(
                    name = "Toon Goggles",
                    streamUrl = "https://toongoggles-toongoggles-1-us.ottera.tv/playlist.m3u8",
                    logoUrl = "https://images.unsplash.com/photo-1560942485-b2a11cc13456?w=120&q=80",
                    categoryId = kidsId,
                    description = "Safe, fun, and educational kids cartoons, games, live action shows, and animations.",
                    isFavorite = true
                ),
                ChannelEntity(
                    name = "Pocket.watch",
                    streamUrl = "https://pocketwatch-pocketwatch-1-us.ottera.tv/playlist.m3u8",
                    logoUrl = "https://images.unsplash.com/photo-1607604276583-eef5d076aa5f?w=120&q=80",
                    categoryId = kidsId,
                    description = "Hit shows from popular kid creators and family friendly challenges, comedy, and adventures.",
                    isFavorite = false
                ),
                ChannelEntity(
                    name = "Cocomelon (Moonbug)",
                    streamUrl = "https://moonbug-cocomelon-1-us.ottera.tv/playlist.m3u8",
                    logoUrl = "https://images.unsplash.com/photo-1515488042361-404e9250afef?w=120&q=80",
                    categoryId = kidsId,
                    description = "Nursery rhymes and children's songs set in 3D animation, loved by preschool kids globally.",
                    isFavorite = false
                ),

                // Music
                ChannelEntity(
                    name = "Clubbing TV",
                    streamUrl = "https://clubbing-clubbingtv-1-us.ottera.tv/playlist.m3u8",
                    logoUrl = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=120&q=80",
                    categoryId = musicId,
                    description = "The global home of electronic music, offering DJ sets, festival live streams, and club culture reports.",
                    isFavorite = true
                ),
                ChannelEntity(
                    name = "Stingray DJAZZ",
                    streamUrl = "https://stingray-djazz-1-us.ottera.tv/playlist.m3u8",
                    logoUrl = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=120&q=80",
                    categoryId = musicId,
                    description = "A premium music television channel dedicated to jazz, soul, blues, and world music concerts.",
                    isFavorite = false
                ),
                ChannelEntity(
                    name = "Qello Concerts",
                    streamUrl = "https://stingray-qello-1-us.ottera.tv/playlist.m3u8",
                    logoUrl = "https://images.unsplash.com/photo-1459749411175-04bf5292ceea?w=120&q=80",
                    categoryId = musicId,
                    description = "Full-length concerts and music documentaries from history's most legendary bands and solo artists.",
                    isFavorite = false
                )
            )

            val categoryNamesMap = mapOf(
                newsId to "International News",
                sportsId to "Sports & Football",
                entertainmentId to "Entertainment",
                documentaryId to "Documentary",
                kidsId to "Kids",
                musicId to "Music"
            )
            val channelsWithCategories = curatedChannels.map { channel ->
                val channelWithCat = channel.copy(category = categoryNamesMap[channel.categoryId] ?: "")
                encryptChannelUrls(channelWithCat)
            }
            dao.insertChannels(channelsWithCategories)
        }
    }

    private fun detectCategory(channelName: String, parsedGroup: String): String {
        return ChannelClassifier.classify(channelName, parsedGroup)
    }
}
