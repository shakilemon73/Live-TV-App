package com.example.data

import com.example.data.M3uParserService

data class EventFeed(
    val rawName: String,
    val provider: String,
    val language: String,
    val streamUrl: String,
    val logoUrl: String
)

data class GroupedEvent(
    val id: String,
    val title: String,
    val sportCategory: String,
    val logoUrl: String,
    val feeds: List<EventFeed>
)

object LiveEventParser {
    
    /**
     * Parse and map list of ParsedChannel into beautifully structured GroupedEvents.
     */
    fun mapParsedChannelsToGroupedEvents(parsedChannels: List<M3uParserService.ParsedChannel>): List<GroupedEvent> {
        val rawEvents = parsedChannels.map { channel ->
            parseRawChannelToFeed(channel)
        }
        
        // Group by normalized title + sportCategory to group multiple feeds for the same match
        val groupedMap = rawEvents.groupBy { 
            val normalizedTitle = it.first.title.lowercase().trim()
            val normalizedCategory = it.first.sportCategory.lowercase().trim()
            "$normalizedTitle|$normalizedCategory"
        }
        
        return groupedMap.map { (groupKey, items) ->
            val representative = items.first().first
            val feeds = items.map { it.second }
            
            GroupedEvent(
                id = groupKey.hashCode().toString(),
                title = representative.title,
                sportCategory = representative.sportCategory,
                logoUrl = representative.logoUrl.ifEmpty { 
                    getPlaceholderLogoForSport(representative.sportCategory)
                },
                feeds = feeds
            )
        }.sortedBy { it.sportCategory }
    }
    
    private data class TemporaryEventInfo(
        val title: String,
        val sportCategory: String,
        val logoUrl: String
    )
    
    private fun parseRawChannelToFeed(channel: M3uParserService.ParsedChannel): Pair<TemporaryEventInfo, EventFeed> {
        val rawName = channel.name.trim()
        
        // 1. Extract Sport/League category from square brackets: [Category]
        var sportCategory = "Live Event"
        var textAfterCategory = rawName
        
        if (rawName.startsWith("[")) {
            val endBracketIndex = rawName.indexOf("]")
            if (endBracketIndex != -1) {
                sportCategory = rawName.substring(1, endBracketIndex).trim()
                textAfterCategory = rawName.substring(endBracketIndex + 1).trim()
            }
        }
        
        // 2. Parse out language, feed provider, and clean match title
        var cleanTitle = textAfterCategory
        var provider = "Default Feed"
        var language = "English"
        
        if (textAfterCategory.contains("|")) {
            val parts = textAfterCategory.split("|", limit = 2)
            cleanTitle = parts[0].trim()
            val feedInfo = parts[1].trim()
            
            // e.g. "Dsports (STRMXHD)" or "English (STRMCNTR)"
            if (feedInfo.contains("(") && feedInfo.endsWith(")")) {
                val pStart = feedInfo.lastIndexOf("(")
                val rest = feedInfo.substring(0, pStart).trim()
                val insideP = feedInfo.substring(pStart + 1, feedInfo.length - 1).trim()
                
                provider = insideP
                language = if (rest.equals("Spanish", ignoreCase = true) || rest.equals("ESPAÑOL", ignoreCase = true) || rest.equals("ESPAÑOL 1", ignoreCase = true)) {
                    "Spanish"
                } else if (rest.equals("Arabic", ignoreCase = true)) {
                    "Arabic"
                } else if (rest.equals("French", ignoreCase = true)) {
                    "French"
                } else if (rest.equals("Portuguese", ignoreCase = true) || rest.contains("BR", ignoreCase = true)) {
                    "Portuguese"
                } else {
                    rest // Use rest as language or extra title
                }
            } else {
                provider = feedInfo
                language = "Default"
            }
        } else {
            // Check for trailing parentheses, e.g. "Australia vs Philippines (FAWA)"
            if (cleanTitle.endsWith(")") && cleanTitle.contains("(")) {
                val pStart = cleanTitle.lastIndexOf("(")
                val rest = cleanTitle.substring(0, pStart).trim()
                val insideP = cleanTitle.substring(pStart + 1, cleanTitle.length - 1).trim()
                
                if (insideP.length in 3..10 && insideP.all { it.isUpperCase() }) {
                    cleanTitle = rest
                    provider = insideP
                }
            }
        }
        
        // 3. Keep cleanTitle looking stellar and clean
        cleanTitle = cleanTitle.trim()
        
        val tempInfo = TemporaryEventInfo(
            title = cleanTitle,
            sportCategory = sportCategory,
            logoUrl = channel.logoUrl
        )
        
        val feed = EventFeed(
            rawName = rawName,
            provider = provider,
            language = language,
            streamUrl = channel.streamUrl,
            logoUrl = channel.logoUrl
        )
        
        return Pair(tempInfo, feed)
    }
    
    private fun getPlaceholderLogoForSport(category: String): String {
        val catLower = category.lowercase()
        return when {
            catLower.contains("foot") || catLower.contains("fútbol") || catLower.contains("soccer") || catLower.contains("copa") || catLower.contains("world cup") || catLower.contains("fifa") || catLower.contains("ligapro") -> {
                "https://images.unsplash.com/photo-1508098682722-e99c43a406b2?w=300&q=80" // Soccer ball
            }
            catLower.contains("basket") || catLower.contains("nba") -> {
                "https://images.unsplash.com/photo-1546519638-68e109498ffc?w=300&q=80" // Basketball
            }
            catLower.contains("tennis") || catLower.contains("wimbledon") -> {
                "https://images.unsplash.com/photo-1595435934249-5df7ed86e1c0?w=300&q=80" // Tennis racket
            }
            catLower.contains("cycle") || catLower.contains("tour") -> {
                "https://images.unsplash.com/photo-1485965120184-e220f721d03e?w=300&q=80" // Bicycle
            }
            catLower.contains("horse") -> {
                "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=300&q=80" // Horse
            }
            else -> {
                "https://images.unsplash.com/photo-1461896836934-ffe607ba8211?w=300&q=80" // Generic sports stadium
            }
        }
    }

    private val BRACKETS_REGEX = Regex("\\[.*?\\]")
    private val NON_ALPHANUM_REGEX = Regex("[^a-zA-Z0-9 ]")
    private val WHITESPACES_REGEX = Regex("\\s+")

    private data class PrecompiledChannelInfo(
        val channel: ChannelEntity,
        val channelName: String,
        val categoryLower: String,
        val nameLower: String,
        val words: Set<String>,
        val simplified: String,
        val isSportOrLiveEvent: Boolean,
        val representsMatch: Boolean
    )

    private var lastFetchedEvents: List<GroupedEvent>? = null
    private var lastChannelsList: List<ChannelEntity>? = null
    private var lastMergedResult: List<GroupedEvent>? = null

    /**
     * Scans currently loaded playlist/database channels to find and match events.
     * Automatically merges similar channels as feeds, or creates standalone live events.
     */
    fun detectAndMergeSimilarEvents(
        fetchedEvents: List<GroupedEvent>,
        channels: List<ChannelEntity>
    ): List<GroupedEvent> {
        synchronized(this) {
            if (fetchedEvents === lastFetchedEvents && channels === lastChannelsList) {
                return lastMergedResult ?: emptyList()
            }
            if (lastFetchedEvents != null && lastChannelsList != null &&
                fetchedEvents == lastFetchedEvents && channels == lastChannelsList) {
                return lastMergedResult ?: emptyList()
            }
        }

        if (channels.isEmpty()) return fetchedEvents.sortedBy { it.sportCategory }
        
        val mergedList = fetchedEvents.map { it.copy(feeds = it.feeds.toMutableList()) }.toMutableList()
        
        // 1. Precompile event words & simplified names
        val eventTitleWords = fetchedEvents.map { event ->
            val clean = event.title.lowercase()
                .replace(BRACKETS_REGEX, " ")
                .replace(NON_ALPHANUM_REGEX, " ")
            val words = clean.split(WHITESPACES_REGEX).filter { 
                it.length > 2 && it != "vs" && it != "and" && it != "feed" && it != "live" && it != "stream" && it != "sports" 
            }.toSet()
            val simplified = clean.replace(WHITESPACES_REGEX, "")
            Triple(event.id, words, simplified)
        }
        
        // 2. Precompile channel details to avoid Regex work in the nested loop
        val channelInfos = channels.filter { it.streamUrl.isNotBlank() }.map { channel ->
            val channelName = channel.name.trim()
            val clean = channelName.lowercase()
                .replace(BRACKETS_REGEX, " ")
                .replace(NON_ALPHANUM_REGEX, " ")
            val words = clean.split(WHITESPACES_REGEX).filter { 
                it.length > 2 && it != "vs" && it != "and" && it != "feed" && it != "live" && it != "stream" && it != "sports" 
            }.toSet()
            val simplified = clean.replace(WHITESPACES_REGEX, "")
            
            val categoryLower = channel.category.lowercase()
            val nameLower = channelName.lowercase()
            
            val isSportOrLiveEvent = categoryLower.contains("sports") || 
                    categoryLower.contains("live") || 
                    categoryLower.contains("event") ||
                    nameLower.contains("sports") ||
                    nameLower.contains("live")
            
            val representsMatch = nameLower.contains(" vs ") || 
                    nameLower.contains(" v ") || 
                    nameLower.contains(" at ") || 
                    nameLower.contains(" @ ")
            
            PrecompiledChannelInfo(
                channel = channel,
                channelName = channelName,
                categoryLower = categoryLower,
                nameLower = nameLower,
                words = words,
                simplified = simplified,
                isSportOrLiveEvent = isSportOrLiveEvent,
                representsMatch = representsMatch
            )
        }
        
        for (info in channelInfos) {
            val channel = info.channel
            val channelName = info.channelName
            var matched = false
            
            for (i in mergedList.indices) {
                val event = mergedList[i]
                val precompEvent = eventTitleWords.find { it.first == event.id } ?: continue
                val eventWords = precompEvent.second
                val eventSimplified = precompEvent.third
                
                // Compare similarity using precomputed tokens
                val wordsMatch = if (eventWords.isNotEmpty() && info.words.isNotEmpty()) {
                    eventWords.intersect(info.words).size >= 2
                } else {
                    false
                }
                
                val similar = wordsMatch || 
                        (eventSimplified.isNotEmpty() && info.simplified.isNotEmpty() && 
                         (eventSimplified.contains(info.simplified) || info.simplified.contains(eventSimplified)))
                
                if (similar) {
                    val feedExists = event.feeds.any { 
                        it.streamUrl.trim() == channel.streamUrl.trim() 
                    }
                    if (!feedExists) {
                        (event.feeds as MutableList).add(
                            EventFeed(
                                rawName = channelName,
                                provider = if (channel.category.isNotEmpty()) "Matched (${channel.category})" else "TV Playlist Stream",
                                language = "Auto",
                                streamUrl = channel.streamUrl,
                                logoUrl = channel.logoUrl
                            )
                        )
                    }
                    matched = true
                }
            }
            
            // If not matched to an existing event, auto-detect standalone sports events from the database
            if (!matched) {
                if (info.isSportOrLiveEvent && info.representsMatch) {
                    var sportCategory = "Live Sport"
                    val categoryLower = info.categoryLower
                    val nameLower = info.nameLower
                    if (categoryLower.contains("foot") || nameLower.contains("foot") || nameLower.contains("soccer") || nameLower.contains("fútbol")) {
                        sportCategory = "Football"
                    } else if (categoryLower.contains("basket") || nameLower.contains("nba") || nameLower.contains("basket")) {
                        sportCategory = "Basketball"
                    } else if (categoryLower.contains("tennis") || nameLower.contains("tennis") || nameLower.contains("wimbledon")) {
                        sportCategory = "Tennis"
                    } else if (categoryLower.contains("racing") || nameLower.contains("f1") || nameLower.contains("race")) {
                        sportCategory = "Racing"
                    }
                    
                    val cleanTitle = channelName.replace(BRACKETS_REGEX, "").trim()
                    
                    val feedExists = mergedList.any { existing ->
                        existing.feeds.any { it.streamUrl.trim() == channel.streamUrl.trim() }
                    }
                    
                    if (!feedExists) {
                        val newEvent = GroupedEvent(
                            id = "auto_${channel.id}_${cleanTitle.hashCode()}",
                            title = cleanTitle,
                            sportCategory = sportCategory,
                            logoUrl = channel.logoUrl.ifEmpty { getPlaceholderLogoForSport(sportCategory) },
                            feeds = listOf(
                                EventFeed(
                                    rawName = channelName,
                                    provider = "Playlist Auto-Detect",
                                    language = "Live",
                                    streamUrl = channel.streamUrl,
                                    logoUrl = channel.logoUrl
                                )
                            )
                        )
                        mergedList.add(newEvent)
                    }
                }
            }
        }
        
        val result = mergedList.sortedBy { it.sportCategory }
        synchronized(this) {
            lastFetchedEvents = fetchedEvents
            lastChannelsList = channels
            lastMergedResult = result
        }
        return result
    }

    private fun areNamesSimilar(name1: String, name2: String): Boolean {
        val clean1 = name1.lowercase().replace(BRACKETS_REGEX, " ").replace(NON_ALPHANUM_REGEX, " ")
        val clean2 = name2.lowercase().replace(BRACKETS_REGEX, " ").replace(NON_ALPHANUM_REGEX, " ")
        
        val words1 = clean1.split(WHITESPACES_REGEX).filter { 
            it.length > 2 && it != "vs" && it != "and" && it != "feed" && it != "live" && it != "stream" && it != "sports" 
        }.toSet()
        val words2 = clean2.split(WHITESPACES_REGEX).filter { 
            it.length > 2 && it != "vs" && it != "and" && it != "feed" && it != "live" && it != "stream" && it != "sports" 
        }.toSet()
        
        if (words1.isEmpty() || words2.isEmpty()) return false
        
        val intersection = words1.intersect(words2)
        if (intersection.size >= 2) return true
        
        val simplified1 = clean1.replace(WHITESPACES_REGEX, "")
        val simplified2 = clean2.replace(WHITESPACES_REGEX, "")
        if (simplified1.contains(simplified2) || simplified2.contains(simplified1)) return true
        
        return false
    }
}
