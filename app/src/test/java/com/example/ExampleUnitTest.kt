package com.example

import com.example.data.ChannelEntity
import com.example.data.ChannelNameNormalizer
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests and practical demonstrations for [ChannelNameNormalizer].
 * This suite demonstrates how the regex-based stripping functions work
 * and shows how to use the normalizer for deduplication mapping of channels.
 */
class ExampleUnitTest {

    @Test
    fun testBracketsRemoval() {
        val original = "Sky Cinema 1 [EN] [HEVC]"
        val expected = "Sky Cinema 1"
        val actual = ChannelNameNormalizer.sanitizeChannelName(original)
        assertEquals(expected, actual)
    }

    @Test
    fun testParenthesesRemoval() {
        val original = "CNN International (Backup) (Server 2)"
        val expected = "CNN International"
        val actual = ChannelNameNormalizer.sanitizeChannelName(original)
        assertEquals(expected, actual)
    }

    @Test
    fun testCountryPrefixRemoval() {
        val original = "US: HBO Max"
        val originalPipe = "UK | BBC One"
        val originalDash = "FR - CANAL+"
        
        assertEquals("HBO Max", ChannelNameNormalizer.sanitizeChannelName(original))
        assertEquals("BBC One", ChannelNameNormalizer.sanitizeChannelName(originalPipe))
        assertEquals("CANAL+", ChannelNameNormalizer.sanitizeChannelName(originalDash))
    }

    @Test
    fun testQualityTagsRemoval() {
        val originalFhd = "National Geographic FHD"
        val original4k = "Discovery 4K UHD 50fps"
        val original1080p = "Eurosport 1080p"
        
        assertEquals("National Geographic", ChannelNameNormalizer.sanitizeChannelName(originalFhd))
        assertEquals("Discovery", ChannelNameNormalizer.sanitizeChannelName(original4k))
        assertEquals("Eurosport", ChannelNameNormalizer.sanitizeChannelName(original1080p))
    }

    @Test
    fun testServerAndBackupIdentifiersRemoval() {
        val originalServer = "Disney Channel Server 1"
        val originalSource = "Action Movies Source 2"
        val originalAlt = "Comedy Central Alt"
        val originalBackup = "HBO Backup"
        
        assertEquals("Disney Channel", ChannelNameNormalizer.sanitizeChannelName(originalServer))
        assertEquals("Action Movies", ChannelNameNormalizer.sanitizeChannelName(originalSource))
        assertEquals("Comedy Central", ChannelNameNormalizer.sanitizeChannelName(originalAlt))
        assertEquals("HBO", ChannelNameNormalizer.sanitizeChannelName(originalBackup))
    }

    @Test
    fun testCombinedMessyChannelNames() {
        val messy1 = "US: HBO HD [HEVC] (Backup)"
        val messy2 = "FR | TF1 FHD (Server 2)"
        val messy3 = "UK - Sky Sports Main Event UHD 4K [EN] (Source A)"

        assertEquals("HBO", ChannelNameNormalizer.sanitizeChannelName(messy1))
        assertEquals("TF1", ChannelNameNormalizer.sanitizeChannelName(messy2))
        assertEquals("Sky Sports Main Event", ChannelNameNormalizer.sanitizeChannelName(messy3))
    }

    @Test
    fun testDeduplicationMappingDemonstration() {
        // Create dummy list of ChannelEntity objects simulating a flat list from M3U
        val channelList = listOf(
            ChannelEntity(
                id = 1,
                categoryId = 101,
                name = "US: HBO HD [HEVC]",
                streamUrl = "http://stream.example/hbo-fhd.m3u8",
                logoUrl = ""
            ),
            ChannelEntity(
                id = 2,
                categoryId = 101,
                name = "HBO FHD (Server 2)",
                streamUrl = "http://stream.example/hbo-server2.m3u8",
                logoUrl = ""
            ),
            ChannelEntity(
                id = 3,
                categoryId = 101,
                name = "HBO SD [Backup]",
                streamUrl = "http://stream.example/hbo-backup.m3u8",
                logoUrl = ""
            ),
            ChannelEntity(
                id = 4,
                categoryId = 102,
                name = "UK | BBC One HD",
                streamUrl = "http://stream.example/bbc1-hd.m3u8",
                logoUrl = ""
            ),
            ChannelEntity(
                id = 5,
                categoryId = 102,
                name = "BBC One FHD (Alt)",
                streamUrl = "http://stream.example/bbc1-alt.m3u8",
                logoUrl = ""
            )
        )

        // Group channels by their normalized key
        val groupedChannels: Map<String, List<ChannelEntity>> = ChannelNameNormalizer.deduplicateChannels(channelList)

        // We expect exactly 2 distinct channel groups: "hbo" and "bbc one"
        assertEquals(2, groupedChannels.size)
        assertTrue(groupedChannels.containsKey("hbo"))
        assertTrue(groupedChannels.containsKey("bbc one"))

        // Verify the streams grouped under "hbo" group
        val hboStreams = groupedChannels["hbo"] ?: emptyList()
        assertEquals(3, hboStreams.size)
        assertEquals("US: HBO HD [HEVC]", hboStreams[0].name)
        assertEquals("HBO FHD (Server 2)", hboStreams[1].name)
        assertEquals("HBO SD [Backup]", hboStreams[2].name)

        // Verify the streams grouped under "bbc one" group
        val bbcStreams = groupedChannels["bbc one"] ?: emptyList()
        assertEquals(2, bbcStreams.size)
        assertEquals("UK | BBC One HD", bbcStreams[0].name)
        assertEquals("BBC One FHD (Alt)", bbcStreams[1].name)

        // Print representation for visual clarity
        println("Deduplication Results:")
        groupedChannels.forEach { (key, list) ->
            println("Channel Key: '$key' has ${list.size} sources:")
            list.forEach { ch ->
                println("  - Raw Name: '${ch.name}' -> Stream URL: ${ch.streamUrl}")
            }
        }
    }

    @Test
    fun testDoms9Playlists() {
        val url1 = "https://github.com/doms9/iptv/raw/refs/heads/default/M3U8/events.m3u8"
        val url2 = "https://github.com/doms9/iptv/blob/default/M3U8/TV.m3u8"

        val norm1 = com.example.data.M3uParserService.normalizeUrl(url1)
        val norm2 = com.example.data.M3uParserService.normalizeUrl(url2)

        println("Normalized events.m3u8: $norm1")
        println("Normalized TV.m3u8: $norm2")

        val content1 = com.example.data.M3uParserService.fetchM3uContent(url1)
        val content2 = com.example.data.M3uParserService.fetchM3uContent(url2)

        assertNotNull("events.m3u8 content should not be null", content1)
        assertNotNull("TV.m3u8 content should not be null", content2)

        val channels1 = com.example.data.M3uParserService.parseM3uText(content1!!)
        val channels2 = com.example.data.M3uParserService.parseM3uText(content2!!)

        println("events.m3u8 parsed channels count: ${channels1.size}")
        println("TV.m3u8 parsed channels count: ${channels2.size}")

        assertTrue("events.m3u8 channels should not be empty", channels1.isNotEmpty())
        assertTrue("TV.m3u8 channels should not be empty", channels2.isNotEmpty())
    }
}
