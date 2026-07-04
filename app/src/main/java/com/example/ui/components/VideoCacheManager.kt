package com.example.ui.components

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

@OptIn(UnstableApi::class)
@UnstableApi
object VideoCacheManager {
    private var cache: SimpleCache? = null

    @Synchronized
    fun getCache(context: Context): SimpleCache {
        if (cache == null) {
            val cacheDir = File(context.cacheDir, "video_stream_cache")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            val databaseProvider = StandaloneDatabaseProvider(context)
            // 150 MB disk cache limit for seamless instant start prefetching
            val evictor = LeastRecentlyUsedCacheEvictor(150 * 1024 * 1024)
            cache = SimpleCache(cacheDir, evictor, databaseProvider)
        }
        return cache!!
    }
}
