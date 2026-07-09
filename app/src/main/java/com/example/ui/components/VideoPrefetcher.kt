package com.example.ui.components

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(UnstableApi::class)
@UnstableApi
object VideoPrefetcher {
    private const val TAG = "VideoPrefetcher"
    // Pre-cache 1 MB (1024 * 1024 bytes) of the video stream for progressive videos
    private const val PREFETCH_SIZE = 1 * 1024 * 1024L 

    suspend fun prefetch(context: Context, videoUrl: String) = withContext(Dispatchers.IO) {
        if (videoUrl.isBlank()) return@withContext
        
        var cleanUrl = videoUrl
        val customHeaders = mutableMapOf<String, String>()
        
        val delimiterIndex = videoUrl.indexOf("|").let { 
            if (it != -1) it else videoUrl.indexOf("%7C", ignoreCase = true) 
        }
        if (delimiterIndex != -1) {
            val isPipe = videoUrl[delimiterIndex] == '|'
            val delimiterLen = if (isPipe) 1 else 3
            try {
                val cleanUrlEncoded = videoUrl.substring(0, delimiterIndex)
                val headerParamsEncoded = videoUrl.substring(delimiterIndex + delimiterLen)
                
                cleanUrl = try {
                    java.net.URLDecoder.decode(cleanUrlEncoded, "UTF-8")
                } catch (e: Exception) {
                    cleanUrlEncoded
                }
                
                headerParamsEncoded.split("&").forEach { param ->
                    val kv = param.split("=", limit = 2)
                    if (kv.size == 2) {
                        val key = try {
                            java.net.URLDecoder.decode(kv[0].trim(), "UTF-8")
                        } catch (e: Exception) {
                            kv[0].trim()
                        }
                        val value = try {
                            java.net.URLDecoder.decode(kv[1].trim(), "UTF-8")
                        } catch (e: Exception) {
                            kv[1].trim()
                        }
                        if (key.isNotBlank() && value.isNotBlank()) {
                            customHeaders[key] = value
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing pipe-separated headers for prefetch: $videoUrl", e)
            }
        } else {
            try {
                val uri = Uri.parse(videoUrl)
                val queryNames = uri.queryParameterNames
                if (queryNames.any { it.startsWith("http_") }) {
                    val urlBuilder = uri.buildUpon()
                    queryNames.forEach { name ->
                        if (name.startsWith("http_")) {
                            val headerName = name.removePrefix("http_")
                                .split("_")
                                .joinToString("-") { it.replaceFirstChar { c -> c.uppercase() } }
                            val headerValue = uri.getQueryParameter(name)
                            if (headerValue != null) {
                                customHeaders[headerName] = headerValue
                                urlBuilder.clearQuery() // simple cleanup
                            }
                        }
                    }
                    cleanUrl = urlBuilder.build().toString()
                }
            } catch (e: Exception) {
                // ignore
            }
        }
        
        val lowerUrl = cleanUrl.lowercase()
        if (lowerUrl.startsWith("rtsp://") || lowerUrl.startsWith("rtsps://")) {
            // RTSP is a custom real-time transport protocol and bypasses the HTTP disk cache
            return@withContext
        }
        
        val isHls = lowerUrl.contains(".m3u8") || 
                    lowerUrl.contains("/hls/") || 
                    lowerUrl.contains("/m3u8") ||
                    lowerUrl.contains("type=hls") ||
                    lowerUrl.contains("format=m3u8")
                    
        val isAdaptivePlaylist = isHls || 
                                 lowerUrl.contains(".mpd") || 
                                 lowerUrl.contains(".ism") || 
                                 lowerUrl.contains("/dash/") || 
                                 lowerUrl.contains("/smooth/") ||
                                 lowerUrl.contains("/manifest")
        
        try {
            val cache = VideoCacheManager.getCache(context)
            
            if (isHls) {
                // Enterprise Intelligent HLS Prefetch: Parse playlist & prefetch actual first segments
                Log.d(TAG, "Triggering Intelligent HLS Segment Prefetcher for: $cleanUrl")
                // Pre-cache the main manifest file first
                prefetchUrlPart(context, cleanUrl, 128 * 1024L, customHeaders, cache)
                prefetchHlsSegments(context, cleanUrl, customHeaders, cache)
            } else if (isAdaptivePlaylist) {
                // For other adaptive manifests (e.g. DASH), prefetch manifest structure (typically <128 KB)
                prefetchUrlPart(context, cleanUrl, 128 * 1024L, customHeaders, cache)
            } else {
                // Progressive videos (MP4, MKV, etc.): prefetch first 1 MB for snappy start
                prefetchUrlPart(context, cleanUrl, PREFETCH_SIZE, customHeaders, cache)
            }
            Log.d(TAG, "Completed prefetch process for stream: $cleanUrl")
        } catch (e: Exception) {
            Log.w(TAG, "Prefetch failed or interrupted for $cleanUrl: ${e.message}")
        }
    }

    private suspend fun prefetchHlsSegments(
        context: Context,
        manifestUrl: String,
        headers: Map<String, String>,
        cache: androidx.media3.datasource.cache.SimpleCache
    ) {
        try {
            // 1. Fetch Master/Main Playlist
            val masterText = fetchText(manifestUrl, headers) ?: return
            
            var targetPlaylistUrl = manifestUrl
            if (masterText.contains("#EXT-X-STREAM-INF")) {
                // It's a master playlist. Find the first variant stream URL.
                val lines = masterText.lines()
                var streamUrlLine: String? = null
                for (i in lines.indices) {
                    if (lines[i].startsWith("#EXT-X-STREAM-INF")) {
                        // The next non-empty, non-comment line is the variant URL
                        for (j in i + 1 until lines.size) {
                            val line = lines[j].trim()
                            if (line.isNotEmpty() && !line.startsWith("#")) {
                                streamUrlLine = line
                                break
                            }
                        }
                        if (streamUrlLine != null) break
                    }
                }
                
                if (streamUrlLine != null) {
                    targetPlaylistUrl = resolveUrl(manifestUrl, streamUrlLine)
                }
            }
            
            // 2. Fetch the variant playlist
            val variantText = if (targetPlaylistUrl != manifestUrl) {
                // If we resolved a new variant URL, let's pre-cache that variant manifest first!
                prefetchUrlPart(context, targetPlaylistUrl, 128 * 1024L, headers, cache)
                fetchText(targetPlaylistUrl, headers)
            } else {
                masterText
            }
            
            if (variantText == null) return
            
            // 3. Find the first 2 media segments in the variant playlist
            val segmentUrls = mutableListOf<String>()
            val lines = variantText.lines()
            for (i in lines.indices) {
                if (lines[i].startsWith("#EXTINF")) {
                    for (j in i + 1 until lines.size) {
                        val line = lines[j].trim()
                        if (line.isNotEmpty() && !line.startsWith("#")) {
                            segmentUrls.add(resolveUrl(targetPlaylistUrl, line))
                            break
                        }
                    }
                    if (segmentUrls.size >= 2) break // Prefetch the first 2 segments for initial playback stability
                }
            }
            
            // 4. Prefetch the media segments
            segmentUrls.forEach { segmentUrl ->
                // Prefetch first 512 KB of the segment for instant startup and buffering-proof launch
                Log.d(TAG, "Intelligent HLS Pre-cache targeting segment: $segmentUrl")
                prefetchUrlPart(context, segmentUrl, 512 * 1024L, headers, cache)
            }
        } catch (e: Exception) {
            Log.w(TAG, "HLS segment prefetching failed: ${e.message}")
        }
    }

    private suspend fun fetchText(urlStr: String, headers: Map<String, String>): String? = withContext(Dispatchers.IO) {
        try {
            val url = java.net.URL(urlStr)
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
            connection.inputStream.use { stream ->
                return@withContext stream.bufferedReader().readText()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch text from $urlStr: ${e.message}")
            null
        }
    }

    private fun resolveUrl(baseUrl: String, relativeUrl: String): String {
        return try {
            val base = java.net.URI(baseUrl)
            base.resolve(relativeUrl).toString()
        } catch (e: Exception) {
            if (relativeUrl.startsWith("http://") || relativeUrl.startsWith("https://")) {
                relativeUrl
            } else {
                val lastSlash = baseUrl.lastIndexOf('/')
                if (lastSlash != -1) {
                    baseUrl.substring(0, lastSlash + 1) + relativeUrl
                } else {
                    baseUrl + "/" + relativeUrl
                }
            }
        }
    }

    private fun prefetchUrlPart(
        context: Context,
        url: String,
        length: Long,
        headers: Map<String, String>,
        cache: androidx.media3.datasource.cache.SimpleCache
    ) {
        try {
            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
            if (headers.isNotEmpty()) {
                httpDataSourceFactory.setDefaultRequestProperties(headers)
            }
            
            val cacheDataSource = CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(httpDataSourceFactory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                .createDataSource()
            
            val dataSpec = DataSpec.Builder()
                .setUri(Uri.parse(url))
                .setPosition(0)
                .setLength(length)
                .build()

            val cacheWriter = CacheWriter(
                cacheDataSource,
                dataSpec,
                null, // temporary buffer
                null  // progress listener
            )
            cacheWriter.cache()
            Log.d(TAG, "Successfully pre-cached $length bytes for url: $url")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cache part of $url: ${e.message}")
        }
    }
}
