package com.example.data

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.StringReader
import java.util.concurrent.TimeUnit

object M3uParserService {
    private const val TAG = "M3uParserService"

    @Volatile
    var lastTvgUrl: String? = null

    private val okHttpClient: OkHttpClient by lazy {
        com.example.data.CachedHttpClient.getBaseClient()
    }

    data class ParsedChannel(
        val name: String,
        val streamUrl: String,
        val logoUrl: String,
        val groupTitle: String,
        val tvgId: String,
        val tvgName: String,
        val description: String
    )

    /**
     * Helper to normalize playlist URLs (e.g. GitHub raw, Pastebin raw, etc.)
     */
    fun normalizeUrl(url: String): String {
        var u = url.trim()
        if (u.contains("github.com") && !u.contains("raw.githubusercontent.com")) {
            if (u.contains("/blob/")) {
                u = u.replace("github.com", "raw.githubusercontent.com")
                     .replace("/blob/", "/")
            } else if (u.contains("/raw/")) {
                u = u.replace("github.com", "raw.githubusercontent.com")
                     .replace("/raw/", "/")
            }
        } else if (u.contains("pastebin.com")) {
            if (!u.contains("/raw/")) {
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

    /**
     * Fetches raw M3U text content from a remote URL.
     */
    fun fetchM3uContent(url: String): String? {
        val normalized = normalizeUrl(url)
        val request = Request.Builder()
            .url(normalized)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .build()

        return try {
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string()
                } else {
                    Log.e(TAG, "Failed to fetch M3U playlist from $normalized: Code ${response.code}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network exception fetching M3U playlist from $normalized", e)
            null
        }
    }

    /**
     * Parses from a Reader line-by-line to avoid materializing huge Strings in memory.
     */
    fun parseM3uReader(reader: java.io.Reader): List<ParsedChannel> {
        val channels = mutableListOf<ParsedChannel>()

        val tvgLogoRegex = """tvg-logo="([^"]*)"""".toRegex()
        val groupTitleRegex = """group-title="([^"]*)"""".toRegex()
        val tvgIdRegex = """tvg-id="([^"]*)"""".toRegex()
        val tvgNameRegex = """tvg-name="([^"]*)"""".toRegex()

        var currentLogo = ""
        var currentGroup = "General"
        var currentName = ""
        var currentTvgId = ""
        var currentTvgName = ""
        var currentDescription = ""
        val currentOptions = mutableMapOf<String, String>()

        try {
            val bufferedReader = if (reader is java.io.BufferedReader) reader else java.io.BufferedReader(reader)
            bufferedReader.useLines { lines ->
                for (line in lines) {
                    val trimmedLine = line.trim()
                    if (trimmedLine.startsWith("#EXTM3U", ignoreCase = true)) {
                        val tvgUrlRegex = """(?:url-tvg|x-tvg-url)="([^"]*)"""".toRegex(RegexOption.IGNORE_CASE)
                        val match = tvgUrlRegex.find(trimmedLine)
                        if (match != null) {
                            val url = match.groupValues[1].trim()
                            if (url.isNotEmpty()) {
                                lastTvgUrl = url
                            }
                        }
                    } else if (trimmedLine.startsWith("#EXTINF:")) {
                        // Clear option cache for new channel entry
                        currentOptions.clear()
                        // Extract metadata tags using high-performance regex matches
                        currentLogo = tvgLogoRegex.find(trimmedLine)?.groupValues?.get(1) ?: ""
                        currentTvgId = tvgIdRegex.find(trimmedLine)?.groupValues?.get(1) ?: ""
                        currentTvgName = tvgNameRegex.find(trimmedLine)?.groupValues?.get(1) ?: ""

                        val groupMatch = groupTitleRegex.find(trimmedLine)
                        val rawGroupTitle = groupMatch?.groupValues?.get(1) ?: "General"
                        currentGroup = rawGroupTitle.split(";").firstOrNull { it.isNotBlank() }?.trim() ?: rawGroupTitle.trim()

                        // Extract channel display name after the last comma
                        val commaIndex = trimmedLine.lastIndexOf(',')
                        currentName = if (commaIndex != -1) {
                            trimmedLine.substring(commaIndex + 1).trim()
                        } else {
                            if (currentTvgName.isNotBlank()) {
                                currentTvgName
                            } else if (currentTvgId.isNotBlank()) {
                                currentTvgId
                            } else {
                                val lastQuoteIndex = trimmedLine.lastIndexOf('"')
                                if (lastQuoteIndex != -1 && lastQuoteIndex < trimmedLine.length - 1) {
                                    val suffix = trimmedLine.substring(lastQuoteIndex + 1).trim()
                                    if (suffix.isNotBlank()) suffix else ""
                                } else {
                                    ""
                                }
                            }
                        }
                        currentDescription = if (currentName.isNotEmpty()) "Live stream of $currentName" else ""
                    } else if (trimmedLine.startsWith("#EXTVLCOPT:")) {
                        val optContent = trimmedLine.substringAfter("#EXTVLCOPT:").trim()
                        val eqIndex = optContent.indexOf('=')
                        if (eqIndex != -1) {
                            val rawKey = optContent.substring(0, eqIndex).trim()
                            val rawValue = optContent.substring(eqIndex + 1).trim()
                            val headerKey = when (rawKey.lowercase()) {
                                "http-referrer", "referrer", "referer" -> "Referer"
                                "http-origin", "origin" -> "Origin"
                                "http-user-agent", "user-agent", "http-useragent" -> "User-Agent"
                                else -> {
                                    if (rawKey.startsWith("http-")) {
                                        rawKey.substring(5).split("-").joinToString("-") { it.replaceFirstChar { c -> c.uppercase() } }
                                    } else {
                                        rawKey
                                    }
                                }
                            }
                            currentOptions[headerKey] = rawValue
                        }
                    } else if (trimmedLine.isNotBlank() && !trimmedLine.startsWith("#")) {
                        val streamUrl = trimmedLine
                        if (streamUrl.startsWith("http://") || streamUrl.startsWith("https://")) {
                            
                            // Generate custom names from URLs if the playlist lacked proper names
                            val finalName = if (currentName.isNotEmpty()) currentName else {
                                try {
                                    val uri = java.net.URI(streamUrl)
                                    val host = uri.host ?: ""
                                    val path = uri.path ?: ""
                                    val lastSegment = path.substringAfterLast('/').substringBefore('.')
                                    if (lastSegment.isNotBlank() && lastSegment.length in 3..25) {
                                        lastSegment.replace("-", " ")
                                            .replace("_", " ")
                                            .split(" ")
                                            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                                    } else if (host.isNotBlank()) {
                                        host.removePrefix("www.")
                                    } else {
                                        "Stream Channel"
                                    }
                                } catch (e: Exception) {
                                    "Stream Channel"
                                }
                            }

                            val finalDescription = if (currentDescription.isNotEmpty()) currentDescription else "Live stream of $finalName"

                            // Run validation layer to verify headers for restricted URLs
                            validateAndEnforceHeaders(streamUrl, currentOptions)

                            var finalStreamUrl = streamUrl
                            if (currentOptions.isNotEmpty()) {
                                try {
                                    val optionString = currentOptions.map { (k, v) -> "$k=${java.net.URLEncoder.encode(v, "UTF-8")}" }.joinToString("&")
                                    finalStreamUrl = "$streamUrl|$optionString"
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error encoding header options for streamUrl", e)
                                }
                            }

                            channels.add(
                                ParsedChannel(
                                    name = finalName,
                                    streamUrl = finalStreamUrl,
                                    logoUrl = currentLogo.ifEmpty { "https://images.unsplash.com/photo-1542038784456-1ea8e935640e?w=120&q=80" },
                                    groupTitle = currentGroup.ifEmpty { "General" },
                                    tvgId = currentTvgId,
                                    tvgName = currentTvgName,
                                    description = finalDescription
                                )
                            )

                            // Reset state for next channel entry
                            currentName = ""
                            currentLogo = ""
                            currentGroup = "General"
                            currentDescription = ""
                            currentTvgId = ""
                            currentTvgName = ""
                            currentOptions.clear()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing M3U list Reader", e)
        }

        return channels
    }

    /**
     * Parses a raw M3U/M3U8 string and organizes it into a list of ParsedChannel objects.
     */
    fun parseM3uText(m3uText: String): List<ParsedChannel> {
        if (m3uText.isBlank()) return emptyList()
        return parseM3uReader(java.io.StringReader(m3uText))
    }

    /**
     * Validation layer that checks for required headers in the playlist before attempting to load
     * the stream. For known restricted streams (e.g. Fawanews, Daddylive), it automatically
     * injects referrers, origins, and modern user agents to prevent player crashes or blackout screens.
     */
    fun validateAndEnforceHeaders(streamUrl: String, options: MutableMap<String, String>): Boolean {
        val lowerUrl = streamUrl.lowercase().trim()
        
        // 1. Detect known domains that strictly require referrers or origins
        val needsFawaNewsHeaders = lowerUrl.contains("fawanews") || lowerUrl.contains("193.47.62.")
        val needsDaddyliveHeaders = lowerUrl.contains("daddylive") || lowerUrl.contains("dlhd")
        
        if (needsFawaNewsHeaders) {
            // Fawanews streams require exact Referer, Origin and modern browser User-Agent
            if (!options.containsKey("Referer")) {
                options["Referer"] = "http://www.fawanews.sc/"
            }
            if (!options.containsKey("Origin")) {
                options["Origin"] = "http://www.fawanews.sc/"
            }
            if (!options.containsKey("User-Agent")) {
                options["User-Agent"] = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36 Edg/134.0.0.0"
            }
        } else if (needsDaddyliveHeaders) {
            // Daddylive streams require exact Referer & Origin
            if (!options.containsKey("Referer")) {
                options["Referer"] = "https://daddylive.mp/"
            }
            if (!options.containsKey("Origin")) {
                options["Origin"] = "https://daddylive.mp/"
            }
        }
        
        // 2. Set safe modern user agents for typical HLS/M3U8 streams to avoid 403 Forbidden
        val isHlsOrDASH = lowerUrl.contains("/hls/") || lowerUrl.contains(".m3u8") || lowerUrl.contains(".mpd")
        if (isHlsOrDASH && !options.containsKey("User-Agent")) {
            options["User-Agent"] = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        }
        
        return true
    }
}
