package com.example.data

import java.util.Locale

/**
 * A robust utility class designed to normalize, sanitize, and deduplicate IPTV/M3U channel names.
 *
 * M3U playlists often contain a lot of noise in channel names, such as:
 * - Country/Language prefixes (e.g., "US: HBO", "UK | Sky Sports", "FR - CANAL+")
 * - Bracketed info (e.g., "BBC One [EN]", "Discovery [HEVC]")
 * - Parenthesized info (e.g., "ESPN (Backup)", "MTV (Server 2)")
 * - Resolution/Quality tags (e.g., "HBO HD", "CNN 1080p", "Sky News 4K", "Discovery 50fps")
 * - Server / Source identifiers (e.g., "National Geo S1", "Disney Ch. Server 3", "Action Alt")
 *
 * This utility uses compiled regular expressions to strip this metadata, leaving a clean,
 * standardized channel name, which is ideal for deduplication, merging, and grouping of streams.
 */
object ChannelNameNormalizer {

    // Regex to match brackets and their contents (e.g., [HEVC], [EN], [1080p])
    private val BRACKETS_REGEX = Regex("\\s*\\[.*?\\]\\s*")

    // Regex to match parentheses and their contents (e.g., (Backup), (Server 1), (Alt))
    private val PARENTHESES_REGEX = Regex("\\s*\\(.*?\\)\\s*")

    // Regex to match common country/language prefixes at the beginning of the string (e.g., "US:", "US |", "US - ", "UK-")
    // Matches 2-3 uppercase letters followed by a separator like colon, pipe, or dash
    private val COUNTRY_PREFIX_REGEX = Regex("^(?i)\\b([a-z]{2,3}|us-es|us-en)\\s*[:|\\-•·]\\s*")

    // Regex to match quality/resolution/codec/frame-rate tags
    // Matches tags like 4k, uhd, fhd, hd, sd, 1080p, 720p, 576p, 480p, 1080i, 720i, 50fps, 60fps, hevc, h264, h265, h.264, x264, ac3, etc.
    private val QUALITY_TAGS_REGEX = Regex(
        "(?i)\\b(hd|sd|fhd|uhd|2k|4k|8k|1080p|720p|576p|480p|1080i|720i|50fps|60fps|hevc|h264|h265|h\\.264|x264|mpeg\\d*|ac3|dd5\\.1|stereo)\\b"
    )

    // Regex to match server, source, backup, and alternate identifiers
    // Matches patterns like "Server 1", "Server2", "S1", "S2", "Backup", "Alt", "Source A", "Src 1", "Ch 1" as suffix/isolated words
    private val SERVER_IDENTIFIERS_REGEX = Regex(
        "(?i)\\b(server\\s*\\d+|source\\s*\\d+|source\\s*[a-z]|backup\\s*\\d*|alt\\s*\\d*|src\\s*\\d+|ch\\s*\\d+|v\\.\\d+|v\\d+|ver\\s*\\d+)\\b"
    )

    // Regex to clean up any leading or trailing punctuation/dashes left after stripping tags
    private val CLEANUP_PUNCTUATION_REGEX = Regex("(^[\\s:|\\-•·\\/\\+]+|[\\s:|\\-•·\\/]+$)")

    // Regex to collapse consecutive whitespaces into a single space
    private val MULTIPLE_WHITESPACES_REGEX = Regex("\\s+")

    private val sanitizeCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    /**
     * Sanitizes a channel name by removing brackets, parentheses, quality tags, and server identifiers.
     * Retains casing but cleans up extra punctuation and spacing.
     *
     * Example: "US: HBO HD [HEVC] (Backup)" -> "HBO"
     */
    fun sanitizeChannelName(name: String): String {
        return sanitizeCache.getOrPut(name) {
            var clean = name

            // 1. Remove bracket details (e.g., "[HEVC]")
            clean = BRACKETS_REGEX.replace(clean, " ")

            // 2. Remove parentheses details (e.g., "(Backup)")
            clean = PARENTHESES_REGEX.replace(clean, " ")

            // 3. Remove country prefix (e.g., "US:")
            clean = COUNTRY_PREFIX_REGEX.replace(clean, " ")

            // 4. Remove quality tags (e.g., "HD", "1080p")
            clean = QUALITY_TAGS_REGEX.replace(clean, " ")

            // 5. Remove server identifiers (e.g., "Server 1", "Alt")
            clean = SERVER_IDENTIFIERS_REGEX.replace(clean, " ")

            // 6. Clean up trailing/leading punctuation
            clean = CLEANUP_PUNCTUATION_REGEX.replace(clean, "")

            // 7. Collapse consecutive spaces and trim
            clean = MULTIPLE_WHITESPACES_REGEX.replace(clean, " ")

            clean.trim()
        }
    }

    /**
     * Generates a standardized, lowercased cache key suitable for deduplication mapping.
     *
     * Example: "US: HBO HD [HEVC] (Backup)" -> "hbo"
     */
    fun getNormalizedKey(name: String): String {
        return sanitizeChannelName(name).lowercase(Locale.getDefault())
    }

    /**
     * Groups a raw list of ChannelEntity objects based on their normalized channel names.
     * Demonstrates how to map multiple stream URLs representing the same channel into a grouped list.
     *
     * @param channels The raw, flat list of channels parsed from M3U.
     * @return A map of Normalized Name -> List of matching raw channels.
     */
    fun deduplicateChannels(channels: List<ChannelEntity>): Map<String, List<ChannelEntity>> {
        return channels.groupBy { getNormalizedKey(it.name) }
    }
}
