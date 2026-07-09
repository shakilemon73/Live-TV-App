package com.example.data

import kotlin.math.sqrt

object CategoryNames {
    const val NEWS = "International News"
    const val SPORTS = "Sports & Football"
    const val ENTERTAINMENT = "Entertainment"
    const val DOCUMENTARY = "Documentary"
    const val KIDS = "Kids"
    const val MUSIC = "Music"
    const val GENERAL = "General"
    const val MOVIES = "Movies"
    const val COMEDY = "Comedy"
    const val RELIGIOUS = "Religious"
    const val LIFESTYLE = "Lifestyle"
    const val BANGLA_CHANNEL = "Bangla Channel"
    const val BANGLA_NEWS = "Bangla News"
    const val CRICKET = "Cricket"
    const val ANIMATION = "Animation"
    const val BUSINESS = "Business"
    const val AUTO = "Auto"
    
    // Remaining of the 19 Playlists
    const val BDIX_IPTV = "BDIX IPTV"
    const val DOMS9_BASE = "Doms9 Base"
    const val DOMS9_US_TV = "Doms9 US TV"

    // Dedicated channel categories
    const val LIVE_SPORTS = "Live Sports Events"
    const val SPORTS_NETWORKS = "Sports Networks"
    const val PREMIUM_MOVIES = "Premium Movies & Drama"
    const val CLASSIC_TV = "Classic & Retro TV"
    const val CRIME_INVESTIGATION = "Crime & Investigation"
    const val SCIENCE_HISTORY = "Science & History"
    const val LIFESTYLE_CUISINE = "Lifestyle & Cuisine"
    const val US_LOCALS = "US Local Networks"
    const val KIDS_ANIMATION = "Kids & Animation"
}

object ChannelClassifier {
    private val normalizationRegex = """(?i)\b(HD|FHD|UHD|SD|4K|1080P|720P|576P|480P|360P|HEVC|H264|H265|RAW|STREAM|TV|HQ|USA?|UK|CA|IN|BD|ES|FR|DE|IT|JP|VIP|LIVE|ONLINE|M3U8)\b|[|:.\-_+\\/\[\]()]+""".toRegex()

    // Highly precise dictionary mapping of all channels from the playlist
    private val DIRECT_MAPPINGS = mapOf(
        // US Local Networks
        "ABC" to CategoryNames.US_LOCALS,
        "CBS" to CategoryNames.US_LOCALS,
        "NBC" to CategoryNames.US_LOCALS,
        "Fox" to CategoryNames.US_LOCALS,
        "CW" to CategoryNames.US_LOCALS,
        "Telemundo" to CategoryNames.US_LOCALS,

        // Premium Movies & Drama
        "Cinemax" to CategoryNames.PREMIUM_MOVIES,
        "HBO" to CategoryNames.PREMIUM_MOVIES,
        "HBO 2" to CategoryNames.PREMIUM_MOVIES,
        "HBO Comedy" to CategoryNames.PREMIUM_MOVIES,
        "HBO Zone" to CategoryNames.PREMIUM_MOVIES,
        "Starz" to CategoryNames.PREMIUM_MOVIES,
        "Starz Encore Classic" to CategoryNames.PREMIUM_MOVIES,
        "Showtime" to CategoryNames.PREMIUM_MOVIES,
        "Showtime Extreme" to CategoryNames.PREMIUM_MOVIES,
        "Lifetime Movie Network" to CategoryNames.PREMIUM_MOVIES,
        "Sony Movie Channel" to CategoryNames.PREMIUM_MOVIES,
        "Turner Classic Movies" to CategoryNames.PREMIUM_MOVIES,
        "FX Movie Channel" to CategoryNames.PREMIUM_MOVIES,

        // Sports Networks
        "ACC Network" to CategoryNames.SPORTS_NETWORKS,
        "Altitude Sports" to CategoryNames.SPORTS_NETWORKS,
        "beIN Sports 1" to CategoryNames.SPORTS_NETWORKS,
        "Big Ten Network" to CategoryNames.SPORTS_NETWORKS,
        "CBS Sports Golazo Network" to CategoryNames.SPORTS_NETWORKS,
        "CBS Sports Network" to CategoryNames.SPORTS_NETWORKS,
        "DIRECTV Sports" to CategoryNames.SPORTS_NETWORKS,
        "DIRECTV Sports +" to CategoryNames.SPORTS_NETWORKS,
        "ESPN" to CategoryNames.SPORTS_NETWORKS,
        "ESPN2" to CategoryNames.SPORTS_NETWORKS,
        "ESPN News" to CategoryNames.SPORTS_NETWORKS,
        "ESPN U" to CategoryNames.SPORTS_NETWORKS,
        "Fox Sports 1" to CategoryNames.SPORTS_NETWORKS,
        "Fox Sports 2" to CategoryNames.SPORTS_NETWORKS,
        "Golf Channel" to CategoryNames.SPORTS_NETWORKS,
        "Marquee Sports Network" to CategoryNames.SPORTS_NETWORKS,
        "MLB Network" to CategoryNames.SPORTS_NETWORKS,
        "NBA TV" to CategoryNames.SPORTS_NETWORKS,
        "NBC Sports Bay Area" to CategoryNames.SPORTS_NETWORKS,
        "NBC Sports Boston" to CategoryNames.SPORTS_NETWORKS,
        "NBC Sports California" to CategoryNames.SPORTS_NETWORKS,
        "NBC Sports NOW" to CategoryNames.SPORTS_NETWORKS,
        "NBC Sports Philadelphia" to CategoryNames.SPORTS_NETWORKS,
        "NESN" to CategoryNames.SPORTS_NETWORKS,
        "NFL Network" to CategoryNames.SPORTS_NETWORKS,
        "NHL Network" to CategoryNames.SPORTS_NETWORKS,
        "Outdoor Channel" to CategoryNames.SPORTS_NETWORKS,
        "Premier Sports 1" to CategoryNames.SPORTS_NETWORKS,
        "Premier Sports 2" to CategoryNames.SPORTS_NETWORKS,
        "SEC Network" to CategoryNames.SPORTS_NETWORKS,
        "Sky Sports Football" to CategoryNames.SPORTS_NETWORKS,
        "Sky Sports News" to CategoryNames.SPORTS_NETWORKS,
        "Sky Sports Premier League" to CategoryNames.SPORTS_NETWORKS,
        "Space City Home Network" to CategoryNames.SPORTS_NETWORKS,
        "Spectrum SportsNet LA Dodgers" to CategoryNames.SPORTS_NETWORKS,
        "Spectrum SportsNet Lakers" to CategoryNames.SPORTS_NETWORKS,
        "Sportsnet 360" to CategoryNames.SPORTS_NETWORKS,
        "Sportsnet East" to CategoryNames.SPORTS_NETWORKS,
        "SportsNet New York" to CategoryNames.SPORTS_NETWORKS,
        "Sportsnet One" to CategoryNames.SPORTS_NETWORKS,
        "Tennis Channel" to CategoryNames.SPORTS_NETWORKS,
        "TSN1" to CategoryNames.SPORTS_NETWORKS,
        "TSN2" to CategoryNames.SPORTS_NETWORKS,
        "Willow Cricket" to CategoryNames.SPORTS_NETWORKS,
        "YES Network" to CategoryNames.SPORTS_NETWORKS,

        // News
        "BBC World News" to CategoryNames.NEWS,
        "CBS News 24/7" to CategoryNames.NEWS,
        "CNBC" to CategoryNames.NEWS,
        "CNN" to CategoryNames.NEWS,
        "Fox Business" to CategoryNames.NEWS,
        "Fox News" to CategoryNames.NEWS,
        "HLN TV" to CategoryNames.NEWS,
        "MSNBC" to CategoryNames.NEWS,
        "Newsmax TV" to CategoryNames.NEWS,
        "NewsNation" to CategoryNames.NEWS,
        "The Weather Channel" to CategoryNames.NEWS,

        // Science & History
        "Discovery Channel" to CategoryNames.SCIENCE_HISTORY,
        "Discovery Science" to CategoryNames.SCIENCE_HISTORY,
        "History Channel" to CategoryNames.SCIENCE_HISTORY,
        "Smithsonian Channel" to CategoryNames.SCIENCE_HISTORY,
        "Nat Geo" to CategoryNames.SCIENCE_HISTORY,
        "Nat Geo Wild" to CategoryNames.SCIENCE_HISTORY,
        "Animal Planet" to CategoryNames.SCIENCE_HISTORY,

        // Kids & Animation
        "Boomerang" to CategoryNames.KIDS_ANIMATION,
        "Cartoon Network" to CategoryNames.KIDS_ANIMATION,
        "Discovery Family Channel" to CategoryNames.KIDS_ANIMATION,
        "Disney Channel" to CategoryNames.KIDS_ANIMATION,
        "Disney Jr" to CategoryNames.KIDS_ANIMATION,
        "Disney XD" to CategoryNames.KIDS_ANIMATION,
        "Nickelodeon" to CategoryNames.KIDS_ANIMATION,
        "Nick Jr" to CategoryNames.KIDS_ANIMATION,
        "Nicktoons" to CategoryNames.KIDS_ANIMATION,
        "PBS Kids" to CategoryNames.KIDS_ANIMATION,

        // Crime & Investigation
        "Crime & Investigation Network" to CategoryNames.CRIME_INVESTIGATION,
        "Investigation Discovery" to CategoryNames.CRIME_INVESTIGATION,
        "Oxygen" to CategoryNames.CRIME_INVESTIGATION,
        "Court TV" to CategoryNames.CRIME_INVESTIGATION,
        "Reelz Channel" to CategoryNames.CRIME_INVESTIGATION,

        // Classic & Retro TV
        "Buzzr" to CategoryNames.CLASSIC_TV,
        "getTV" to CategoryNames.CLASSIC_TV,
        "Grit TV" to CategoryNames.CLASSIC_TV,
        "Comet TV" to CategoryNames.CLASSIC_TV,
        "Cozi TV" to CategoryNames.CLASSIC_TV,
        "Bounce TV" to CategoryNames.CLASSIC_TV,
        "TV Land" to CategoryNames.CLASSIC_TV,
        "INSP" to CategoryNames.CLASSIC_TV,

        // Lifestyle & Cuisine
        "Food Network" to CategoryNames.LIFESTYLE_CUISINE,
        "Cooking Channel" to CategoryNames.LIFESTYLE_CUISINE,
        "TLC" to CategoryNames.LIFESTYLE_CUISINE,
        "MotorTrend TV" to CategoryNames.LIFESTYLE_CUISINE,
        "Discovery Life" to CategoryNames.LIFESTYLE_CUISINE,
        "FYI TV" to CategoryNames.LIFESTYLE_CUISINE
    )

    // Stopwords carrying minimal semantic weight
    private val STOPWORDS = setOf(
        "hd", "fhd", "uhd", "sd", "4k", "1080p", "720p", "576p", "480p", "360p", 
        "hevc", "h264", "h265", "raw", "stream", "tv", "hq", "usa", "us", "uk", 
        "ca", "in", "bd", "vip", "live", "online", "m3u8", "the", "a", "an", "and", 
        "of", "to", "in", "for", "on", "with", "at", "by", "from", "up", "about"
    )

    // Globally mapped high-dimensional centroid vectors representing categories
    private val CATEGORY_CENTROIDS = mapOf(
        CategoryNames.CRICKET to mapOf(
            "willow" to 10.0, "cricket" to 10.0, "ipl" to 10.0, "bpl" to 10.0, 
            "icc" to 10.0, "t20" to 10.0, "odi" to 8.0, "test" to 5.0, 
            "ashes" to 8.0, "batsman" to 6.0, "wicket" to 6.0, "willow cricket" to 12.0,
            "sky cricket" to 10.0, "cricket hd" to 8.0, "star sports cricket" to 10.0
        ),
        CategoryNames.AUTO to mapOf(
            "motor" to 8.0, "auto" to 8.0, "car" to 6.0, "nascar" to 10.0, 
            "motogp" to 10.0, "speed" to 6.0, "turbo" to 6.0, "racing" to 7.0,
            "formula" to 9.0, "f1" to 10.0, "motor tv" to 10.0, "auto tv" to 10.0,
            "car tv" to 10.0, "velocity" to 8.0, "rev" to 6.0
        ),
        CategoryNames.BANGLA_NEWS to mapOf(
            "somoy" to 10.0, "jamuna" to 10.0, "ekattor" to 10.0, "dbc" to 10.0, 
            "nagorik" to 8.0, "news24" to 10.0, "atn" to 5.0, "channel 24" to 10.0, 
            "independent" to 9.0, "somoy tv" to 12.0, "jamuna tv" to 12.0,
            "ekattor tv" to 12.0, "dbc news" to 12.0, "nagorik tv" to 10.0,
            "atn news" to 10.0, "bengali news" to 8.0, "bangla news" to 10.0,
            "news 24 bd" to 10.0, "independent tv" to 12.0, "rtr news" to 8.0
        ),
        CategoryNames.NEWS to mapOf(
            "cnn" to 10.0, "bbc" to 10.0, "al jazeera" to 10.0, "msnbc" to 10.0, 
            "fox news" to 10.0, "euronews" to 10.0, "france" to 6.0, "dw" to 8.0, 
            "sky news" to 10.0, "cctv" to 8.0, "ndtv" to 8.0, "reuters" to 10.0, 
            "press" to 6.0, "rt" to 6.0, "wion" to 10.0, "abc" to 4.0, "cbs" to 4.0, 
            "nbc" to 4.0, "weather" to 8.0, "news" to 5.0, "info" to 4.0, 
            "breaking" to 8.0, "today" to 4.0, "journal" to 6.0, "report" to 6.0, 
            "world" to 4.0, "bbc news" to 12.0, "france 24" to 10.0, "dw news" to 10.0,
            "weather channel" to 10.0, "abc news" to 8.0, "cbs news" to 8.0,
            "nbc news" to 8.0, "global news" to 8.0, "channel news" to 6.0
        ),
        CategoryNames.BUSINESS to mapOf(
            "bloomberg" to 10.0, "cnbc" to 10.0, "business" to 8.0, "market" to 7.0, 
            "finance" to 8.0, "stock" to 7.0, "money" to 6.0, "wealth" to 6.0,
            "fox business" to 12.0, "bloomberg tv" to 12.0, "business news" to 10.0,
            "market watch" to 8.0
        ),
        CategoryNames.ANIMATION to mapOf(
            "animax" to 10.0, "crunchyroll" to 10.0, "anime" to 10.0, "adult swim" to 10.0, 
            "toonis" to 8.0, "manga" to 8.0, "animation" to 8.0, "toon" to 6.0,
            "adultswim" to 10.0, "funimation" to 9.0
        ),
        CategoryNames.KIDS to mapOf(
            "disney" to 10.0, "nickelodeon" to 10.0, "nick" to 8.0, "cartoon" to 9.0, 
            "boomerang" to 10.0, "baby" to 8.0, "babytv" to 10.0, "cbeebies" to 10.0, 
            "duronto" to 10.0, "pogo" to 10.0, "hungama" to 10.0, "gulli" to 8.0, 
            "spacetoon" to 10.0, "child" to 7.0, "kids" to 8.0, "junior" to 7.0,
            "cartoon network" to 12.0, "nick jr" to 10.0, "disney junior" to 10.0,
            "baby tv" to 10.0, "duronto tv" to 12.0, "disney xd" to 10.0
        ),
        CategoryNames.MOVIES to mapOf(
            "hbo" to 10.0, "cinemax" to 10.0, "starz" to 10.0, "showtime" to 10.0, 
            "amc" to 10.0, "mgm" to 9.0, "epix" to 9.0, "hallmark" to 10.0, 
            "lifetime" to 9.0, "pix" to 8.0, "movies" to 8.0, "film" to 7.0, 
            "cineplex" to 9.0, "blockbuster" to 8.0, "thriller" to 7.0, "cinema" to 8.0,
            "star movies" to 12.0, "zee cinema" to 12.0, "colors cineplex" to 12.0,
            "wb channel" to 10.0, "action movies" to 10.0, "movies hd" to 10.0,
            "sky cinema" to 12.0, "hallmark movies" to 12.0, "hbo signature" to 11.0,
            "hbo family" to 11.0, "hbo hits" to 11.0
        ),
        CategoryNames.SPORTS to mapOf(
            "espn" to 10.0, "bein" to 10.0, "sky" to 5.0, "bt" to 6.0, 
            "supersport" to 10.0, "euro" to 5.0, "eurosport" to 10.0, "fox" to 4.0, 
            "ten" to 5.0, "astro" to 6.0, "arena" to 8.0, "stadium" to 8.0, 
            "golf" to 9.0, "tennis" to 9.0, "pga" to 8.0, "wwe" to 10.0, 
            "ufc" to 10.0, "fight" to 8.0, "premier" to 7.0, "liga" to 8.0, 
            "bundesliga" to 9.0, "serie" to 8.0, "champions" to 9.0, "sports" to 6.0, 
            "sport" to 6.0, "football" to 8.0, "soccer" to 8.0, "nfl" to 9.0, 
            "nba" to 9.0, "mlb" to 9.0, "nhl" to 9.0, "bein sport" to 12.0,
            "bein sports" to 12.0, "sky sport" to 12.0, "sky sports" to 12.0,
            "bt sport" to 12.0, "fox sport" to 12.0, "fox sports" to 12.0,
            "ten sport" to 12.0, "ten sports" to 12.0, "sony ten" to 12.0,
            "sports hd" to 10.0, "nfl network" to 12.0, "nba tv" to 12.0,
            "mlb network" to 12.0, "nhl network" to 12.0, "golf channel" to 12.0,
            "tennis channel" to 12.0, "fight network" to 12.0, "premier league" to 12.0,
            "la liga" to 12.0, "champions league" to 12.0, "real madrid" to 10.0,
            "chelsea tv" to 10.0, "mutv" to 10.0
        ),
        CategoryNames.MUSIC to mapOf(
            "mtv" to 10.0, "vh1" to 10.0, "music" to 8.0, "vivid" to 8.0, 
            "sol" to 6.0, "hits" to 7.0, "clubland" to 9.0, "classical" to 8.0, 
            "jazz" to 8.0, "opera" to 8.0, "radio" to 6.0, "vocal" to 6.0,
            "pop" to 6.0, "rock" to 6.0, "music tv" to 10.0, "mtv live" to 11.0,
            "vh1 classic" to 11.0, "clubland tv" to 10.0, "chart show" to 9.0
        ),
        CategoryNames.DOCUMENTARY to mapOf(
            "discovery" to 10.0, "national" to 7.0, "geographic" to 8.0, "nat" to 6.0, 
            "geo" to 6.0, "history" to 9.0, "animal" to 8.0, "planet" to 8.0, 
            "nasa" to 9.0, "curiosity" to 10.0, "smithsonian" to 10.0, "science" to 8.0, 
            "documentary" to 8.0, "docu" to 7.0, "earth" to 6.0, "national geographic" to 12.0,
            "nat geo" to 12.0, "history channel" to 12.0, "animal planet" to 12.0,
            "nasa tv" to 12.0, "curiosity stream" to 12.0, "science channel" to 11.0
        ),
        CategoryNames.COMEDY to mapOf(
            "comedy" to 9.0, "central" to 6.0, "laughter" to 8.0, "humor" to 8.0,
            "joke" to 6.0, "funny" to 7.0, "comedy central" to 12.0, "comedy tv" to 11.0,
            "gold comedy" to 9.0
        ),
        CategoryNames.RELIGIOUS to mapOf(
            "islam" to 9.0, "quran" to 10.0, "peace" to 8.0, "makkah" to 10.0, 
            "madinah" to 10.0, "bible" to 9.0, "christian" to 8.0, "gospel" to 8.0, 
            "dhamma" to 9.0, "religion" to 7.0, "islamic" to 8.0, "religious" to 7.0,
            "church" to 7.0, "god" to 6.0, "islam tv" to 11.0, "quran tv" to 12.0,
            "peace tv" to 11.0, "makkah live" to 12.0, "madinah live" to 12.0,
            "bible tv" to 11.0, "christian tv" to 11.0
        ),
        CategoryNames.LIFESTYLE to mapOf(
            "lifestyle" to 9.0, "fashion" to 9.0, "food" to 9.0, "kitchen" to 8.0, 
            "travel" to 9.0, "home" to 6.0, "garden" to 7.0, "hgtv" to 10.0, 
            "tlc" to 10.0, "style" to 7.0, "living" to 7.0, "cook" to 8.0, 
            "cooking" to 8.0, "gourmet" to 8.0, "fashion tv" to 12.0, "food network" to 12.0,
            "kitchen tv" to 11.0, "travel channel" to 12.0, "home garden" to 10.0
        ),
        CategoryNames.BANGLA_CHANNEL to mapOf(
            "ntv" to 10.0, "rtr" to 6.0, "channel" to 3.0, "gazi" to 8.0, 
            "gtv" to 9.0, "atn" to 8.0, "dipto" to 10.0, "asian" to 8.0, 
            "desh" to 8.0, "ekushey" to 10.0, "sa" to 8.0, "bijoy" to 8.0, 
            "maasranga" to 10.0, "boishakhi" to 10.0, "bangla" to 7.0, "bdix" to 8.0, 
            "dhaka" to 8.0, "bengali" to 7.0, "duronto" to 5.0, "ntv bd" to 12.0,
            "channel i" to 12.0, "atn bangla" to 12.0, "dipto tv" to 12.0,
            "asian tv" to 11.0, "desh tv" to 11.0, "ekushey tv" to 12.0,
            "sa tv" to 11.0, "bijoy tv" to 11.0, "bangla tv" to 11.0,
            "gazi tv" to 12.0, "maasranga tv" to 12.0, "boishakhi tv" to 12.0,
            "duronto tv" to 8.0, "star jalsha" to 11.0, "zee bangla" to 11.0,
            "colors bangla" to 11.0, "sun bangla" to 10.0
        )
    )

    /**
     * World-Class Category Detection System with advanced capability.
     * Evaluates channel name, M3U group title, and playlist source hint
     * to compute high-confidence genre categorization using vector spaces and
     * TF-IDF bigram profiles.
     */
    fun classify(channelName: String, groupTitle: String?, playlistSource: String? = null): String {
        val cleanName = channelName.trim()
        val lowerName = cleanName.lowercase()

        // 1. Direct High-Precision Lookup Match first
        val directMatch = DIRECT_MAPPINGS[cleanName]
        if (directMatch != null) return directMatch

        // Case-insensitive direct match fallback
        val caseInsensitiveMatch = DIRECT_MAPPINGS.entries.firstOrNull { it.key.equals(cleanName, ignoreCase = true) }?.value
        if (caseInsensitiveMatch != null) return caseInsensitiveMatch

        // Explicit Live Events group check
        val explicitGroupCheck = groupTitle?.lowercase()?.trim() ?: ""
        if (explicitGroupCheck == "live events" || 
            explicitGroupCheck == "live event" || 
            explicitGroupCheck == "liveevents" || 
            explicitGroupCheck == "live_events" || 
            explicitGroupCheck == "live-events" || 
            explicitGroupCheck.contains("live event") || 
            explicitGroupCheck.contains("live events")
        ) {
            return "Live Events"
        }

        // 2. High-Precision Pattern Matching for Live Sports Events (highly prevalent in dynamic TV.m3u8)
        if (cleanName.startsWith("[") || 
            lowerName.contains(" vs ") || 
            lowerName.contains(" at ") || 
            lowerName.contains("world cup") || 
            lowerName.contains("copa del mundo") || 
            lowerName.contains("fifa") || 
            lowerName.contains("fútbol") || 
            lowerName.contains("basketball") || 
            lowerName.contains("strmcntr") || 
            lowerName.contains("strmxhd") || 
            lowerName.contains("xyzstrm") || 
            lowerName.contains("strmhub") || 
            lowerName.contains("strmsgate") || 
            lowerName.contains("watchfty") || 
            lowerName.contains("fawa") ||
            lowerName.contains("fighting") ||
            lowerName.contains("ufc") ||
            lowerName.contains("wwe")
        ) {
            return CategoryNames.LIVE_SPORTS
        }

        // 3. Fallback Keyphrase Matching for Custom/Playlist-Aligned Categories
        when {
            // Cricket
            lowerName.contains("cricket") || lowerName.contains("willow") || lowerName.contains("ipl 2") || lowerName.contains("t20") || lowerName.contains("bpl") || lowerName.contains("ten cricket") -> {
                return CategoryNames.CRICKET
            }

            // Bangla News
            lowerName.contains("somoy") || lowerName.contains("jamuna") || lowerName.contains("ekattor") || lowerName.contains("dbc news") || lowerName.contains("news 24 hd") || lowerName.contains("news24 hd") || lowerName.contains("bangla news") || lowerName.contains("bd news") || lowerName.contains("abp ananda") || lowerName.contains("calcutta news") || lowerName.contains("kolkata tv") || lowerName.contains("news18 bangla") || lowerName.contains("r bangla") || lowerName.contains("zee 24 ghanta") || lowerName.contains("ekhon tv") || lowerName.contains("r plus news") -> {
                return CategoryNames.BANGLA_NEWS
            }

            // Bangla Channel
            lowerName.contains("bangla") || lowerName.contains("bengali") || lowerName.contains("bdix") || lowerName.contains("dhaka") || lowerName.contains("ntv") || lowerName.contains("gtv") || lowerName.contains("atn") || lowerName.contains("gazi") || lowerName.contains("maasranga") || lowerName.contains("massranga") || lowerName.contains("nagorik") || lowerName.contains("rtv") || lowerName.contains("sa tv") || lowerName.contains("satv") || lowerName.contains("sangsad") || lowerName.contains("bongo drama") || lowerName.contains("toffee drama") || lowerName.contains("mohona tv") || lowerName.contains("asian tv") || lowerName.contains("bijoy") || lowerName.contains("ananda tv") || lowerName.contains("channel s") || lowerName.contains("falconcast") || lowerName.contains("green tv") || lowerName.contains("tv9 bangla") || lowerName.contains("independent tv") || lowerName.contains("channel 24") || lowerName.contains("channel 9") || lowerName.contains("channel i") || lowerName.contains("deepto") || lowerName.contains("dipto") || lowerName.contains("dippto") || lowerName.contains("ekushey") || lowerName.contains("g series") || lowerName.contains("g-serise") || lowerName.contains("uniques hd") || lowerName.contains("goldmines") -> {
                return CategoryNames.BANGLA_CHANNEL
            }

            // Kids & Animation (Broad Match default to Kids)
            lowerName.contains("disney") || lowerName.contains("nick") || lowerName.contains("cartoon") || lowerName.contains("boomerang") || lowerName.contains("kids") || lowerName.contains("baby") || lowerName.contains("toon") || lowerName.contains("cbeebies") || lowerName.contains("buddystar") || lowerName.contains("duronto") || lowerName.contains("funny junior") || lowerName.contains("jungle book") || lowerName.contains("nikki") || lowerName.contains("nikky") || lowerName.contains("pbs kids") || lowerName.contains("rongeen tv") || lowerName.contains("smarty") || lowerName.contains("tom & jerry") || lowerName.contains("tom & jarry") || lowerName.contains("zoo moo") || lowerName.contains("pogo") -> {
                return CategoryNames.KIDS
            }

            // Animation
            lowerName.contains("animation") || lowerName.contains("anime") || lowerName.contains("animax") -> {
                return CategoryNames.ANIMATION
            }

            // Auto
            lowerName.contains("auto") || lowerName.contains("car") || lowerName.contains("motor") || lowerName.contains("garage") || lowerName.contains("racing") || lowerName.contains("formula 1") || lowerName.contains("f1") || lowerName.contains("nascar") || lowerName.contains("motogp") -> {
                return CategoryNames.AUTO
            }

            // Business
            lowerName.contains("business") || lowerName.contains("finance") || lowerName.contains("stock") || lowerName.contains("investment pitch") || lowerName.contains("market watch") -> {
                return CategoryNames.BUSINESS
            }

            // Comedy
            lowerName.contains("comedy") || lowerName.contains("humor") || lowerName.contains("laughter") || lowerName.contains("ridiculous tv") || lowerName.contains("el búnquer") || lowerName.contains("13 humor") -> {
                return CategoryNames.COMEDY
            }

            // Documentary
            lowerName.contains("discovery") || lowerName.contains("science") || lowerName.contains("history") || lowerName.contains("geo") || lowerName.contains("smithsonian") || lowerName.contains("planet") || lowerName.contains("geographic") || lowerName.contains("nasa") || lowerName.contains("nat geo") || lowerName.contains("animal planet") || lowerName.contains("bbc earth") || lowerName.contains("wild earth") || lowerName.contains("wild life") || lowerName.contains("wild nature") || lowerName.contains("real wild") || lowerName.contains("docment") -> {
                return CategoryNames.DOCUMENTARY
            }

            // Entertainment
            lowerName.contains("entertainment") || lowerName.contains("entretención") || lowerName.contains("realities") || lowerName.contains("13th street") || lowerName.contains("13 ulica") || lowerName.contains("1kzn") || lowerName.contains("mediaset") || lowerName.contains("9gem") || lowerName.contains("9go") || lowerName.contains("e! ") || lowerName.contains("live channel") || lowerName.contains("showtime") -> {
                return CategoryNames.ENTERTAINMENT
            }

            // International News
            lowerName.contains("news") || lowerName.contains("msnbc") || lowerName.contains("cnn") || lowerName.contains("cnbc") || lowerName.contains("bloomberg") || lowerName.contains("weather") || lowerName.contains("breaking") || lowerName.contains("dw") || lowerName.contains("euronews") || lowerName.contains("al jazeera") || lowerName.contains("aljazeera") || lowerName.contains("cgtn") || lowerName.contains("cna") || lowerName.contains("france 24") || lowerName.contains("france news") || lowerName.contains("india today") || lowerName.contains("iran international") || lowerName.contains("ndtv") || lowerName.contains("nhk world") || lowerName.contains("press tv") || lowerName.contains("rt news") || lowerName.contains("rt now") || lowerName.contains("sky news") || lowerName.contains("trt world") || lowerName.contains("times of india") || lowerName.contains("wion") || lowerName.contains("c-span") || lowerName.contains("newsnation") -> {
                return CategoryNames.NEWS
            }

            // Lifestyle
            lowerName.contains("lifestyle") || lowerName.contains("travel") || lowerName.contains("garden") || lowerName.contains("hgtv") || lowerName.contains("tlc") || lowerName.contains("cuisine") || lowerName.contains("fashion") || lowerName.contains("kitchen") || lowerName.contains("luxe life") || lowerName.contains("motortrend") || lowerName.contains("discovery life") || lowerName.contains("fyi tv") || lowerName.contains("digital fashion") || lowerName.contains("hum masala") -> {
                return CategoryNames.LIFESTYLE
            }

            // Movies
            lowerName.contains("movie") || lowerName.contains("cinema") || lowerName.contains("film") || lowerName.contains("hallmark") || lowerName.contains("lifetime") || lowerName.contains("sony max") || lowerName.contains("sony pix") || lowerName.contains("b4u movie") || lowerName.contains("star gold") || lowerName.contains("goldmines") || lowerName.contains("sheemaroo") || lowerName.contains("superrix") || lowerName.contains("zb cinema") || lowerName.contains("zee cinema") || lowerName.contains("zee action") || lowerName.contains("amc") || lowerName.contains("fxx") || lowerName.contains("fx movie") || lowerName.contains("multiplex") || lowerName.contains("cineedge") || lowerName.contains("cinelife") || lowerName.contains("cinepride") || lowerName.contains("frightflix") || lowerName.contains("screem") || lowerName.contains("watch it scream") || lowerName.contains("tnt") || lowerName.contains("tnt4") || lowerName.contains("tnt international") || lowerName.contains("hbo") || lowerName.contains("cinemax") || lowerName.contains("starz") -> {
                return CategoryNames.MOVIES
            }

            // Music
            lowerName.contains("music") || lowerName.contains("song") || lowerName.contains("mtv") || lowerName.contains("vh1") || lowerName.contains("b4u music") || lowerName.contains("hindi hits") || lowerName.contains("yrf music") || lowerName.contains("gaan bangla") || lowerName.contains("dhoom music") || lowerName.contains("music india") || lowerName.contains("music mastii") || lowerName.contains("party universe") || lowerName.contains("radio") || lowerName.contains("radiovisione") || lowerName.contains("9x tashan") || lowerName.contains("9x jhakaas") || lowerName.contains("9x jalwa") || lowerName.contains("9xm") || lowerName.contains("8xm") || lowerName.contains("bengla beats") -> {
                return CategoryNames.MUSIC
            }

            // Religious
            lowerName.contains("religious") || lowerName.contains("islam") || lowerName.contains("bible") || lowerName.contains("prayer") || lowerName.contains("gospel") || lowerName.contains("quran") || lowerName.contains("sunnah") || lowerName.contains("madani") || lowerName.contains("peace tv") || lowerName.contains("peace tv bangla") || lowerName.contains("ewtn") || lowerName.contains("god tv") || lowerName.contains("makkah") || lowerName.contains("madina") || lowerName.contains("noor tv") || lowerName.contains("sunnah tv") || lowerName.contains("3abn") || lowerName.contains("al sunnah") || lowerName.contains("al quran") || lowerName.contains("al_quran") || lowerName.contains("kareem tv") -> {
                return CategoryNames.RELIGIOUS
            }

            // Sports & Football
            lowerName.contains("sports") || lowerName.contains("sport") || lowerName.contains("football") || lowerName.contains("soccer") || lowerName.contains("golf") || lowerName.contains("tennis") || lowerName.contains("nba") || lowerName.contains("nfl") || lowerName.contains("nhl") || lowerName.contains("mlb") || lowerName.contains("premier league") || lowerName.contains("bein") || lowerName.contains("ten") || lowerName.contains("sportsnet") || lowerName.contains("sec network") || lowerName.contains("acc network") || lowerName.contains("ptv sport") || lowerName.contains("t sports") || lowerName.contains("a sports") || lowerName.contains("eurosports") || lowerName.contains("sony sports") || lowerName.contains("sony ten") || lowerName.contains("sky sports") || lowerName.contains("nbc sports") || lowerName.contains("cbs sports") || lowerName.contains("dazn") || lowerName.contains("skynet sports") || lowerName.contains("unite 8 sports") || lowerName.contains("win sports") || lowerName.contains("tudn sports") || lowerName.contains("dd sports") || lowerName.contains("tvp sports") || lowerName.contains("tvri sport") -> {
                return CategoryNames.SPORTS
            }
        }

        // 4. Fallback to existing vector/Cosine-similarity checks for any dynamic or broad sources
        val cleanGroup = groupTitle?.lowercase()?.trim() ?: ""
        val cleanSource = playlistSource?.trim() ?: ""

        // Determine if playlist source is mixed/generic or highly curated
        val isBroadSource = cleanSource.isBlank() || 
                            cleanSource.equals("doms9 base", ignoreCase = true) ||
                            cleanSource.equals("doms9 us tv", ignoreCase = true) ||
                            cleanSource.equals("bdix iptv", ignoreCase = true) ||
                            cleanSource.equals("default", ignoreCase = true) ||
                            cleanSource.equals("all", ignoreCase = true)

        // Build weighted sparse representation profile vector for the channel
        val queryVector = buildQueryVector(channelName, cleanGroup)

        if (queryVector.isEmpty() && !isBroadSource) {
            return mapSourceToCategory(cleanSource)
        }

        // Calculate cosine similarities across all global genre centroids
        val similarityScores = mutableMapOf<String, Double>()
        CATEGORY_CENTROIDS.forEach { (category, centroidVector) ->
            val score = calculateCosineSimilarity(queryVector, centroidVector)
            if (score > 0.0) {
                similarityScores[category] = score
            }
        }

        // Enrich model scoring using historical group classification bias
        val groupPrior = getGroupPrior(cleanGroup)
        if (groupPrior != null) {
            similarityScores[groupPrior] = (similarityScores[groupPrior] ?: 0.0) + 0.35
        }

        // Extract maximum probability category projection
        val topEntry = similarityScores.maxByOrNull { it.value }
        val detectedCategory = topEntry?.key
        val maxConfidence = topEntry?.value ?: 0.0

        // Resolve decision mapping
        val sourceCategory = mapSourceToCategory(cleanSource)
        val hasValidSource = cleanSource.isNotBlank() && 
                             !cleanSource.equals("default", ignoreCase = true) && 
                             !cleanSource.equals("all", ignoreCase = true)

        // For any channel across all 19 playlists, if we detected a dedicated premium category, use it
        if (detectedCategory != null && maxConfidence >= 0.15) {
            return detectedCategory
        }

        if (groupPrior != null) {
            return groupPrior
        }

        if (hasValidSource) {
            return sourceCategory
        }

        // Special default fallback checks
        if (cleanSource.equals("bdix iptv", ignoreCase = true)) {
            return CategoryNames.BANGLA_CHANNEL
        }

        // Group text processing fallback
        return if (groupTitle != null && groupTitle.isNotBlank() && !groupTitle.equals("TV", ignoreCase = true) && !groupTitle.equals("General", ignoreCase = true)) {
            groupTitle.replace("_", " ").split(" ").joinToString(" ") { token ->
                token.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
        } else {
            CategoryNames.GENERAL
        }
    }

    private fun buildQueryVector(channelName: String, groupTitle: String): Map<String, Double> {
        val vector = mutableMapOf<String, Double>()

        // Process and clean channel name
        val cleanName = channelName.lowercase()
            .replace(normalizationRegex, " ")
            .trim()
        val nameTokens = cleanName.split(Regex("\\s+")).filter { it.isNotEmpty() && it !in STOPWORDS }

        // Extract Unigram channel signals (Base Weight: 1.0)
        nameTokens.forEach { token ->
            vector[token] = (vector[token] ?: 0.0) + 1.0
        }

        // Extract Bigram channel signals (Strong Match Phrases: 2.0 to encapsulate strong markers)
        if (nameTokens.size >= 2) {
            for (i in 0 until nameTokens.size - 1) {
                val bigram = "${nameTokens[i]} ${nameTokens[i+1]}"
                vector[bigram] = (vector[bigram] ?: 0.0) + 2.0
            }
        }

        // Process and scale group title cues
        val cleanGroup = groupTitle.lowercase()
            .replace(normalizationRegex, " ")
            .trim()
        val groupTokens = cleanGroup.split(Regex("\\s+")).filter { it.isNotEmpty() && it !in STOPWORDS }

        groupTokens.forEach { token ->
            vector[token] = (vector[token] ?: 0.0) + 0.4
        }

        if (groupTokens.size >= 2) {
            for (i in 0 until groupTokens.size - 1) {
                val bigram = "${groupTokens[i]} ${groupTokens[i+1]}"
                vector[bigram] = (vector[bigram] ?: 0.0) + 0.8
            }
        }

        return vector
    }

    private fun calculateCosineSimilarity(vectorA: Map<String, Double>, vectorB: Map<String, Double>): Double {
        var dotProduct = 0.0
        var normA = 0.0
        var normB = 0.0

        vectorA.forEach { (term, weight) ->
            normA += weight * weight
            val weightB = vectorB[term]
            if (weightB != null) {
                dotProduct += weight * weightB
            }
        }

        vectorB.values.forEach { weight ->
            normB += weight * weight
        }

        if (normA == 0.0 || normB == 0.0) return 0.0
        return dotProduct / (sqrt(normA) * sqrt(normB))
    }

    private fun getGroupPrior(cleanGroup: String): String? {
        return when {
            cleanGroup.contains("cricket") -> CategoryNames.CRICKET
            cleanGroup.contains("sport") || cleanGroup.contains("football") || cleanGroup.contains("soccer") || cleanGroup.contains("wwe") || cleanGroup.contains("ufc") || cleanGroup.contains("live event") -> CategoryNames.SPORTS
            cleanGroup.contains("somoy") || cleanGroup.contains("bangla news") || cleanGroup.contains("bd news") -> CategoryNames.BANGLA_NEWS
            cleanGroup.contains("news") || cleanGroup.contains("info") || cleanGroup.contains("weather") -> CategoryNames.NEWS
            cleanGroup.contains("business") || cleanGroup.contains("finance") || cleanGroup.contains("stock") -> CategoryNames.BUSINESS
            cleanGroup.contains("animation") || cleanGroup.contains("anime") -> CategoryNames.ANIMATION
            cleanGroup.contains("kid") || cleanGroup.contains("child") || cleanGroup.contains("cartoon") || cleanGroup.contains("disney") || cleanGroup.contains("nick") -> CategoryNames.KIDS
            cleanGroup.contains("movie") || cleanGroup.contains("cinema") || cleanGroup.contains("film") || cleanGroup.contains("hbo") -> CategoryNames.MOVIES
            cleanGroup.contains("music") || cleanGroup.contains("song") || cleanGroup.contains("mtv") || cleanGroup.contains("vh1") -> CategoryNames.MUSIC
            cleanGroup.contains("doc") || cleanGroup.contains("science") || cleanGroup.contains("history") || cleanGroup.contains("geo") || cleanGroup.contains("discovery") -> CategoryNames.DOCUMENTARY
            cleanGroup.contains("comedy") || cleanGroup.contains("humor") || cleanGroup.contains("laughter") -> CategoryNames.COMEDY
            cleanGroup.contains("religious") || cleanGroup.contains("islam") || cleanGroup.contains("bible") || cleanGroup.contains("prayer") || cleanGroup.contains("gospel") -> CategoryNames.RELIGIOUS
            cleanGroup.contains("motor") || cleanGroup.contains("auto") -> CategoryNames.AUTO
            cleanGroup.contains("lifestyle") || cleanGroup.contains("fashion") || cleanGroup.contains("food") || cleanGroup.contains("cooking") || cleanGroup.contains("kitchen") || cleanGroup.contains("travel") || cleanGroup.contains("style") -> CategoryNames.LIFESTYLE
            cleanGroup.contains("bangla") || cleanGroup.contains("bdix") || cleanGroup.contains("dhaka") || cleanGroup.contains("bengali") -> CategoryNames.BANGLA_CHANNEL
            cleanGroup.contains("entertainment") || cleanGroup.contains("ent") || cleanGroup.contains("show") || cleanGroup.contains("series") || cleanGroup.contains("drama") || cleanGroup.contains("soap") || cleanGroup.contains("tv") -> CategoryNames.ENTERTAINMENT
            else -> null
        }
    }

    private fun mapSourceToCategory(source: String): String {
        return when {
            source.equals("BDIX IPTV", ignoreCase = true) -> CategoryNames.BDIX_IPTV
            source.equals("Animation", ignoreCase = true) -> CategoryNames.ANIMATION
            source.equals("Auto", ignoreCase = true) -> CategoryNames.AUTO
            source.equals("Bangla Channel", ignoreCase = true) -> CategoryNames.BANGLA_CHANNEL
            source.equals("Bangla News", ignoreCase = true) -> CategoryNames.BANGLA_NEWS
            source.equals("Business", ignoreCase = true) -> CategoryNames.BUSINESS
            source.equals("Comedy", ignoreCase = true) -> CategoryNames.COMEDY
            source.equals("Cricket", ignoreCase = true) -> CategoryNames.CRICKET
            source.equals("Documentary", ignoreCase = true) -> CategoryNames.DOCUMENTARY
            source.equals("Entertainment", ignoreCase = true) -> CategoryNames.ENTERTAINMENT
            source.equals("International News", ignoreCase = true) -> CategoryNames.NEWS
            source.equals("Kids", ignoreCase = true) -> CategoryNames.KIDS
            source.equals("Lifestyle", ignoreCase = true) -> CategoryNames.LIFESTYLE
            source.equals("Movies", ignoreCase = true) -> CategoryNames.MOVIES
            source.equals("Music", ignoreCase = true) -> CategoryNames.MUSIC
            source.equals("Religious", ignoreCase = true) -> CategoryNames.RELIGIOUS
            source.equals("Sports & Football", ignoreCase = true) -> CategoryNames.SPORTS
            source.equals("Doms9 Base", ignoreCase = true) -> CategoryNames.DOMS9_BASE
            source.equals("Doms9 US TV", ignoreCase = true) -> CategoryNames.DOMS9_US_TV
            else -> source
        }
    }

    fun getChannelReputationScore(name: String): Int {
        val lower = name.lowercase()
        return when {
            // News
            lower.contains("bbc world") || lower.contains("bbc news") -> 100
            lower.contains("cnn") -> 100
            lower.contains("msnbc") -> 95
            lower.contains("al jazeera") || lower.contains("aljazeera") -> 95
            lower.contains("sky news") -> 90
            lower.contains("fox news") -> 90
            lower.contains("cnbc") -> 85
            lower.contains("bloomberg") -> 85
            lower.contains("dw ") || lower.endsWith("dw") || lower.contains("deutsche welle") -> 80
            lower.contains("france 24") -> 80

            // Sports & Cricket
            lower.contains("espn") -> 100
            lower.contains("sky sports") -> 100
            lower.contains("willow") -> 95
            lower.contains("bein sports") || lower.contains("beinsports") -> 95
            lower.contains("dazn") -> 90
            lower.contains("t sports") || lower.contains("tsports") -> 90
            lower.contains("ptv sport") -> 85
            lower.contains("sony sports") || lower.contains("sony ten") -> 85
            lower.contains("fox sports") || lower.contains("fs1") || lower.contains("fs2") -> 85
            lower.contains("eurosport") -> 80

            // Movies & Premium Movies & Drama
            lower.contains("hbo") -> 100
            lower.contains("cinemax") -> 95
            lower.contains("starz") -> 90
            lower.contains("showtime") -> 90
            lower.contains("sony pix") || lower.contains("sony max") -> 85
            lower.contains("star gold") -> 85
            lower.contains("zee cinema") -> 80

            // Entertainment
            lower.contains("abc") || lower.contains("cbs") || lower.contains("nbc") || lower.contains("fox") || lower.contains("the cw") || lower.contains("telemundo") -> {
                if (lower.contains("news") || lower.contains("sport")) 70 else 80
            }

            // Documentaries
            lower.contains("discovery channel") || lower.contains("discovery hd") -> 100
            lower.contains("national geographic") || lower.contains("nat geo") -> 100
            lower.contains("history channel") || lower.contains("history hd") -> 95
            lower.contains("animal planet") -> 90
            lower.contains("bbc earth") -> 90
            lower.contains("smithsonian") -> 85

            // Kids & Animation
            lower.contains("disney channel") || lower.contains("disney junior") || lower.contains("disney jr") || lower.contains("disney xd") -> 100
            lower.contains("nickelodeon") || lower.contains("nick jr") || lower.contains("nicktoons") -> 100
            lower.contains("cartoon network") -> 95
            lower.contains("boomerang") -> 90
            lower.contains("duronto") -> 85
            lower.contains("pbs kids") -> 85

            // Bangla Channel & Bangla News
            lower.contains("somoy") -> 100
            lower.contains("jamuna") -> 95
            lower.contains("ekattor") -> 90
            lower.contains("ntv") -> 95
            lower.contains("rtv") -> 90
            lower.contains("channel i") -> 90
            lower.contains("atn bangla") -> 85
            lower.contains("maasranga") || lower.contains("massranga") -> 85

            // Music
            lower.contains("mtv") -> 100
            lower.contains("vh1") -> 95
            lower.contains("gaan bangla") -> 90

            // Religious
            lower.contains("peace tv") -> 100
            lower.contains("al quran") || lower.contains("al_quran") || lower.contains("makkah") -> 100
            lower.contains("al sunnah") || lower.contains("madina") -> 95

            else -> 0
        }
    }
}
