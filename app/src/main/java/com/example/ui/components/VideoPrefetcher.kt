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
        
        val isAdaptivePlaylist = lowerUrl.contains(".m3u8") || 
                                 lowerUrl.contains(".mpd") || 
                                 lowerUrl.contains(".ism") || 
                                 lowerUrl.contains("/hls/") || 
                                 lowerUrl.contains("/dash/") || 
                                 lowerUrl.contains("/m3u8") ||
                                 lowerUrl.contains("/smooth/") ||
                                 lowerUrl.contains("/manifest")
        
        val size = if (isAdaptivePlaylist) {
            // For adaptive manifests, prefetch manifest structure (typically <128 KB)
            128 * 1024L
        } else {
            PREFETCH_SIZE
        }

        try {
            val cache = VideoCacheManager.getCache(context)
            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
            
            if (customHeaders.isNotEmpty()) {
                httpDataSourceFactory.setDefaultRequestProperties(customHeaders)
            }
            
            val cacheDataSource = CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(httpDataSourceFactory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                .createDataSource()
            
            val dataSpec = DataSpec.Builder()
                .setUri(Uri.parse(cleanUrl))
                .setPosition(0)
                .setLength(size)
                .build()

            val cacheWriter = CacheWriter(
                cacheDataSource,
                dataSpec,
                null, // temporary buffer (allocated inside)
                null  // progress listener
            )

            cacheWriter.cache()
            Log.d(TAG, "Successfully prefetched $size bytes of stream: $cleanUrl")
        } catch (e: Exception) {
            Log.w(TAG, "Prefetch failed or interrupted for $cleanUrl: ${e.message}")
        }
    }
}
