package com.example.data

import android.content.Context
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object EpgParserService {
    private const val TAG = "EpgParserService"

    /**
     * Extracts url-tvg or x-tvg-url from the #EXTM3U line of an M3U playlist.
     */
    fun extractTvgUrl(m3uText: String): String? {
        if (m3uText.isBlank()) return null
        
        // Find first #EXTM3U line
        val lines = m3uText.lineSequence()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXTM3U", ignoreCase = true)) {
                val tvgUrlRegex = """(?:url-tvg|x-tvg-url)="([^"]*)"""".toRegex(RegexOption.IGNORE_CASE)
                val match = tvgUrlRegex.find(trimmed)
                if (match != null) {
                    val url = match.groupValues[1].trim()
                    if (url.isNotEmpty()) {
                        return url
                    }
                }
            }
        }
        return null
    }

    /**
     * Downloads XMLTV EPG content from a remote URL.
     */
    fun fetchXmltvContent(context: Context, url: String): String? {
        val okHttpClient = com.example.data.CachedHttpClient.getClient(context)
        val normalized = M3uParserService.normalizeUrl(url)
        val request = Request.Builder()
            .url(normalized)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .build()

        return try {
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string()
                } else {
                    Log.e(TAG, "Failed to fetch XMLTV EPG from $normalized: Code ${response.code}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network exception fetching XMLTV EPG from $normalized", e)
            null
        }
    }

    /**
     * Parses XMLTV XML content into a list of EpgProgramEntity objects.
     */
    fun parseXmltvText(xmlContent: String): List<EpgProgramEntity> {
        if (xmlContent.isBlank()) return emptyList()

        val programs = mutableListOf<EpgProgramEntity>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val xpp = factory.newPullParser()
            xpp.setInput(StringReader(xmlContent))

            var eventType = xpp.eventType
            var currentChannelId: String? = null
            var currentStart: Long = 0L
            var currentStop: Long = 0L
            var currentTitle = ""
            var currentDesc = ""
            var currentCategory = ""

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tagName = xpp.name
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (tagName.equals("programme", ignoreCase = true)) {
                            currentChannelId = xpp.getAttributeValue(null, "channel")
                            val startAttr = xpp.getAttributeValue(null, "start") ?: ""
                            val stopAttr = xpp.getAttributeValue(null, "stop") ?: ""
                            currentStart = parseXmltvDate(startAttr)
                            currentStop = parseXmltvDate(stopAttr)
                            currentTitle = ""
                            currentDesc = ""
                            currentCategory = ""
                        } else if (tagName.equals("title", ignoreCase = true) && currentChannelId != null) {
                            currentTitle = xpp.nextText().trim()
                        } else if (tagName.equals("desc", ignoreCase = true) && currentChannelId != null) {
                            currentDesc = xpp.nextText().trim()
                        } else if (tagName.equals("category", ignoreCase = true) && currentChannelId != null) {
                            currentCategory = xpp.nextText().trim()
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (tagName.equals("programme", ignoreCase = true)) {
                            if (!currentChannelId.isNullOrEmpty() && currentTitle.isNotEmpty() && currentStart > 0L && currentStop > 0L) {
                                programs.add(
                                    EpgProgramEntity(
                                        channelId = currentChannelId,
                                        title = currentTitle,
                                        description = currentDesc,
                                        startTime = currentStart,
                                        endTime = currentStop,
                                        category = currentCategory
                                    )
                                )
                            }
                            currentChannelId = null
                        }
                    }
                }
                eventType = xpp.next()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing XMLTV EPG", e)
        }

        return programs
    }

    /**
     * Parses standard XMLTV date string like "20260709030000 +0000" or "20260709030000" into milliseconds.
     */
    private fun parseXmltvDate(dateStr: String): Long {
        if (dateStr.isBlank()) return 0L
        val cleanStr = dateStr.trim()
        val formats = listOf(
            "yyyyMMddHHmmss Z",
            "yyyyMMddHHmmss",
            "yyyyMMddHHmm",
            "yyyyMMdd"
        )
        for (format in formats) {
            try {
                val sdf = SimpleDateFormat(format, Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                val date = sdf.parse(cleanStr)
                if (date != null) {
                    return date.time
                }
            } catch (e: Exception) {
                // Try next
            }
        }
        return 0L
    }
}
