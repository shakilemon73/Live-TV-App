package com.example.data

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.StringReader
import java.util.concurrent.TimeUnit

object M3uParserService {
    private const val TAG = "M3uParserService"

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
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
     * Parses a raw M3U/M3U8 string and organizes it into a list of ParsedChannel objects.
     */
    fun parseM3uText(m3uText: String): List<ParsedChannel> {
        val channels = mutableListOf<ParsedChannel>()
        if (m3uText.isBlank()) return channels

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

        try {
            BufferedReader(StringReader(m3uText)).useLines { lines ->
                for (line in lines) {
                    val trimmedLine = line.trim()
                    if (trimmedLine.startsWith("#EXTINF:")) {
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

                            channels.add(
                                ParsedChannel(
                                    name = finalName,
                                    streamUrl = streamUrl,
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
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing M3U list text", e)
        }

        return channels
    }
}
