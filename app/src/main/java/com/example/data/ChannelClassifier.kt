package com.example.data

object CategoryNames {
    const val NEWS = "International News"
    const val SPORTS = "Sports & Football"
    const val ENTERTAINMENT = "Entertainment"
    const val DOCUMENTARY = "Documentary"
    const val KIDS = "Kids"
    const val MUSIC = "Music"
    const val GENERAL = "General"
}

object ChannelClassifier {
    private val normalizationRegex = """(?i)\b(HD|FHD|UHD|SD|4K|1080P|720P|576P|480P|360P|HEVC|H264|H265|RAW|STREAM|TV|HQ|USA?|UK|CA|IN|BD|ES|FR|DE|IT|JP|VIP|LIVE|ONLINE|M3U8)\b|[|:.\-_+\\/\[\]()]+""".toRegex()

    private val networkAliasMap = mapOf(
        // News
        "cnn" to CategoryNames.NEWS,
        "bbc" to CategoryNames.NEWS,
        "aljazeera" to CategoryNames.NEWS,
        "msnbc" to CategoryNames.NEWS,
        "foxnews" to CategoryNames.NEWS,
        "cnbc" to CategoryNames.NEWS,
        "bloomberg" to CategoryNames.NEWS,
        "dw" to CategoryNames.NEWS,
        "france24" to CategoryNames.NEWS,
        "rt" to CategoryNames.NEWS,
        "news" to CategoryNames.NEWS,
        "reuters" to CategoryNames.NEWS,
        "cctv" to CategoryNames.NEWS,
        "ndtv" to CategoryNames.NEWS,
        "somoy" to CategoryNames.NEWS,
        "jamuna" to CategoryNames.NEWS,
        "ekattor" to CategoryNames.NEWS,
        "independent" to CategoryNames.NEWS,

        // Sports
        "espn" to CategoryNames.SPORTS,
        "bein" to CategoryNames.SPORTS,
        "sky" to CategoryNames.SPORTS,
        "supersport" to CategoryNames.SPORTS,
        "sports" to CategoryNames.SPORTS,
        "sport" to CategoryNames.SPORTS,
        "football" to CategoryNames.SPORTS,
        "soccer" to CategoryNames.SPORTS,
        "nba" to CategoryNames.SPORTS,
        "nfl" to CategoryNames.SPORTS,
        "mlb" to CategoryNames.SPORTS,
        "nhl" to CategoryNames.SPORTS,
        "tennis" to CategoryNames.SPORTS,
        "golf" to CategoryNames.SPORTS,
        "cricket" to CategoryNames.SPORTS,
        "ufc" to CategoryNames.SPORTS,
        "wwe" to CategoryNames.SPORTS,
        "eurosport" to CategoryNames.SPORTS,
        "ten" to CategoryNames.SPORTS,
        "foxsports" to CategoryNames.SPORTS,
        "sony" to CategoryNames.SPORTS,
        "tsports" to CategoryNames.SPORTS,
        "gazi" to CategoryNames.SPORTS,

        // Kids
        "disney" to CategoryNames.KIDS,
        "nick" to CategoryNames.KIDS,
        "cartoon" to CategoryNames.KIDS,
        "nickelodeon" to CategoryNames.KIDS,
        "teletoon" to CategoryNames.KIDS,
        "boomer" to CategoryNames.KIDS,
        "baby" to CategoryNames.KIDS,
        "kid" to CategoryNames.KIDS,
        "kids" to CategoryNames.KIDS,
        "cbeebies" to CategoryNames.KIDS,
        "spacetoon" to CategoryNames.KIDS,
        "pogo" to CategoryNames.KIDS,
        "hungama" to CategoryNames.KIDS,
        "duronto" to CategoryNames.KIDS,

        // Documentary
        "discovery" to CategoryNames.DOCUMENTARY,
        "nat" to CategoryNames.DOCUMENTARY,
        "geo" to CategoryNames.DOCUMENTARY,
        "history" to CategoryNames.DOCUMENTARY,
        "science" to CategoryNames.DOCUMENTARY,
        "animal" to CategoryNames.DOCUMENTARY,
        "planet" to CategoryNames.DOCUMENTARY,
        "docu" to CategoryNames.DOCUMENTARY,
        "documentary" to CategoryNames.DOCUMENTARY,
        "nasa" to CategoryNames.DOCUMENTARY,
        "curiosity" to CategoryNames.DOCUMENTARY,
        "smithsonian" to CategoryNames.DOCUMENTARY,

        // Music
        "mtv" to CategoryNames.MUSIC,
        "vivid" to CategoryNames.MUSIC,
        "music" to CategoryNames.MUSIC,
        "vocal" to CategoryNames.MUSIC,
        "vh1" to CategoryNames.MUSIC,
        "sol" to CategoryNames.MUSIC,
        "hits" to CategoryNames.MUSIC,
        "jazz" to CategoryNames.MUSIC,
        "rock" to CategoryNames.MUSIC,
        "pop" to CategoryNames.MUSIC,
        "classical" to CategoryNames.MUSIC,
        "clubland" to CategoryNames.MUSIC,

        // Entertainment / Movies / Series
        "hbo" to CategoryNames.ENTERTAINMENT,
        "starz" to CategoryNames.ENTERTAINMENT,
        "cinemax" to CategoryNames.ENTERTAINMENT,
        "showtime" to CategoryNames.ENTERTAINMENT,
        "amc" to CategoryNames.ENTERTAINMENT,
        "fx" to CategoryNames.ENTERTAINMENT,
        "tnt" to CategoryNames.ENTERTAINMENT,
        "tbs" to CategoryNames.ENTERTAINMENT,
        "axn" to CategoryNames.ENTERTAINMENT,
        "fox" to CategoryNames.ENTERTAINMENT,
        "warner" to CategoryNames.ENTERTAINMENT,
        "cw" to CategoryNames.ENTERTAINMENT,
        "paramount" to CategoryNames.ENTERTAINMENT,
        "syfy" to CategoryNames.ENTERTAINMENT,
        "comedy" to CategoryNames.ENTERTAINMENT,
        "hallmark" to CategoryNames.ENTERTAINMENT,
        "lifetime" to CategoryNames.ENTERTAINMENT,
        "epix" to CategoryNames.ENTERTAINMENT,
        "action" to CategoryNames.ENTERTAINMENT,
        "movies" to CategoryNames.ENTERTAINMENT,
        "movie" to CategoryNames.ENTERTAINMENT,
        "cinema" to CategoryNames.ENTERTAINMENT,
        "zee" to CategoryNames.ENTERTAINMENT,
        "star" to CategoryNames.ENTERTAINMENT,
        "colors" to CategoryNames.ENTERTAINMENT,
        "colorshd" to CategoryNames.ENTERTAINMENT,
        "jalsha" to CategoryNames.ENTERTAINMENT,
        "sony8" to CategoryNames.ENTERTAINMENT,
        "zeecinema" to CategoryNames.ENTERTAINMENT
    )

    private val fuzzyKeywords = mapOf(
        "news" to CategoryNames.NEWS,
        "sports" to CategoryNames.SPORTS,
        "sport" to CategoryNames.SPORTS,
        "entertainment" to CategoryNames.ENTERTAINMENT,
        "documentary" to CategoryNames.DOCUMENTARY,
        "kids" to CategoryNames.KIDS,
        "music" to CategoryNames.MUSIC,
        "movies" to CategoryNames.ENTERTAINMENT,
        "movie" to CategoryNames.ENTERTAINMENT
    )

    fun classify(channelName: String, groupTitle: String?): String {
        // Step 1: Text Normalization Pipeline
        val cleanedName = channelName.replace(normalizationRegex, " ").lowercase().trim()
        val tokens = cleanedName.split(Regex("\\s+")).filter { it.isNotEmpty() }

        if (tokens.isEmpty()) {
            return fallbackGroup(groupTitle)
        }

        // Step 2: Weighted Scoring Matrix computation
        val scores = mutableMapOf<String, Int>()

        tokens.forEachIndexed { index, token ->
            // Assign higher weights if keyword appears at the beginning of channel name versus end.
            val positionWeight = maxOf(1, 10 - index)
            val matchedCategory = networkAliasMap[token]
            if (matchedCategory != null) {
                scores[matchedCategory] = (scores[matchedCategory] ?: 0) + (10 * positionWeight)
            } else {
                // Check if any alias starts/ends/contains with token
                networkAliasMap.forEach { (alias, category) ->
                    if (token.contains(alias) || alias.contains(token)) {
                        scores[category] = (scores[category] ?: 0) + (5 * positionWeight)
                    }
                }
            }
        }

        val highestConfidenceEntry = scores.maxByOrNull { it.value }
        if (highestConfidenceEntry != null && highestConfidenceEntry.value >= 15) {
            return highestConfidenceEntry.key
        }

        // Step 3: Fuzzy Matcher / Levenshtein Distance Fallback
        for (token in tokens) {
            // Only perform on tokens of reasonable length
            if (token.length >= 4) {
                fuzzyKeywords.forEach { (targetKeyword, categoryName) ->
                    val dist = levenshteinDistance(token, targetKeyword)
                    if (dist <= 2) {
                        return categoryName
                    }
                }
            }
        }

        // Step 4: Default Fallback (group-title or General)
        return fallbackGroup(groupTitle)
    }

    private fun fallbackGroup(groupTitle: String?): String {
        val group = groupTitle?.trim() ?: ""
        if (group.isBlank()) return CategoryNames.GENERAL

        val normalized = group.lowercase()
        return when {
            normalized.contains("news") -> CategoryNames.NEWS
            normalized.contains("sport") -> CategoryNames.SPORTS
            normalized.contains("kid") || normalized.contains("child") || normalized.contains("cartoon") -> CategoryNames.KIDS
            normalized.contains("music") || normalized.contains("song") -> CategoryNames.MUSIC
            normalized.contains("doc") || normalized.contains("science") -> CategoryNames.DOCUMENTARY
            normalized.contains("movie") || normalized.contains("cinema") || normalized.contains("ent") || normalized.contains("series") || normalized.contains("tv") -> CategoryNames.ENTERTAINMENT
            else -> {
                // Format the group-title beautifully
                group.replace("_", " ").split(" ").joinToString(" ") { token ->
                    token.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                }
            }
        }
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        if (s1 == s2) return 0
        if (s1.isEmpty()) return s2.length
        if (s2.isEmpty()) return s1.length

        val dp = IntArray(s2.length + 1) { it }
        for (i in 1..s1.length) {
            var prev = i - 1
            dp[0] = i
            for (j in 1..s2.length) {
                val temp = dp[j]
                if (s1[i - 1] == s2[j - 1]) {
                    dp[j] = prev
                } else {
                    dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + 1)
                }
                prev = temp
            }
        }
        return dp[s2.length]
    }
}
