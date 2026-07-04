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
        
        val lowerUrl = videoUrl.lowercase()
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
            
            val cacheDataSource = CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(httpDataSourceFactory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                .createDataSource()
            
            val dataSpec = DataSpec.Builder()
                .setUri(Uri.parse(videoUrl))
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
            Log.d(TAG, "Successfully prefetched $size bytes of stream: $videoUrl")
        } catch (e: Exception) {
            Log.w(TAG, "Prefetch failed or interrupted for $videoUrl: ${e.message}")
        }
    }
}
