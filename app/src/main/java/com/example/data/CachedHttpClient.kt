package com.example.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

object CachedHttpClient {
    private var baseClient: OkHttpClient? = null
    private var cachedClient: OkHttpClient? = null

    @Synchronized
    fun getBaseClient(): OkHttpClient {
        if (baseClient == null) {
            baseClient = OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .build()
        }
        return baseClient!!
    }

    @Synchronized
    fun getClient(context: Context): OkHttpClient {
        if (cachedClient == null) {
            val cacheDirectory = File(context.cacheDir, "http_response_cache")
            val cacheSize = 50 * 1024 * 1024L // 50 MiB of cache
            val cache = Cache(cacheDirectory, cacheSize)

            cachedClient = getBaseClient().newBuilder()
                .cache(cache)
                .addInterceptor { chain ->
                    var request = chain.request()
                    
                    // If network is offline, force use of local cache (up to 7 days stale)
                    if (!isNetworkAvailable(context)) {
                        request = request.newBuilder()
                            .header("Cache-Control", "public, only-if-cached, max-stale=" + 60 * 60 * 24 * 7)
                            .build()
                    } else {
                        // Reduce network overhead by allowing 30 minutes of cache for normal hits,
                        // unless a Cache-Control override is specified on the request (like on SwipeRefresh)
                        if (request.header("Cache-Control") == null) {
                            request = request.newBuilder()
                                .header("Cache-Control", "public, max-age=" + 60 * 30)
                                .build()
                        }
                    }
                    chain.proceed(request)
                }
                .build()
        }
        return cachedClient!!
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (connectivityManager != null) {
            val activeNetwork = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            return capabilities != null && (
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            )
        }
        return false
    }
}
