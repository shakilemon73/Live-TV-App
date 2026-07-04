/**
 * IPTV Server-Driven/Cloud-Cached Architecture - JSON Generator Engine
 * 
 * This script runs as a scheduled cron-job to:
 * 1. Fetch 17 raw GitHub M3U URLs using HTTP conditional headers (ETag/Last-Modified) to minimize bandwidth.
 * 2. Parse, sanitize, and fuzzy-deduplicate channel names (stripping country prefixes, quality suffixes, and server tags).
 * 3. Group streams representing the same channel together under a single channel object with multiple sources.
 * 4. Run parallel URL validation (HTTP HEAD request with a 1500ms timeout) via a concurrency-controlled queue.
 * 5. Output a single, consolidated 'channels.json' file optimized for fast client consumption.
 * 
 * Deployment: This can be deployed to a serverless function, GitHub Actions, or any server cron-job.
 */

const fs = require('fs');
const path = require('path');
const https = require('https');
const http = require('http');
const crypto = require('crypto');

// Configuration
const M3U_PLAYLISTS = {
    "BDIX IPTV": "https://raw.githubusercontent.com/abusaeeidx/Mrgify-BDIX-IPTV/main/playlist.m3u",
    "Animation": "https://raw.githubusercontent.com/shakilemon73/my-m3u-playlist/main/channel_list/animation.m3u",
    "Auto": "https://raw.githubusercontent.com/shakilemon73/my-m3u-playlist/main/channel_list/auto.m3u",
    "Bangla Channel": "https://raw.githubusercontent.com/shakilemon73/my-m3u-playlist/main/channel_list/bangla-channel.m3u",
    "Bangla News": "https://raw.githubusercontent.com/shakilemon73/my-m3u-playlist/main/channel_list/bangla_news.m3u",
    "Business": "https://raw.githubusercontent.com/shakilemon73/my-m3u-playlist/main/channel_list/business.m3u",
    "Comedy": "https://raw.githubusercontent.com/shakilemon73/my-m3u-playlist/main/channel_list/comedy.m3u",
    "Cricket": "https://raw.githubusercontent.com/shakilemon73/my-m3u-playlist/main/channel_list/cricket.m3u",
    "Documentary": "https://raw.githubusercontent.com/shakilemon73/my-m3u-playlist/main/channel_list/documentary.m3u",
    "Entertainment": "https://raw.githubusercontent.com/shakilemon73/my-m3u-playlist/main/channel_list/entertainment.m3u",
    "International News": "https://raw.githubusercontent.com/shakilemon73/my-m3u-playlist/main/channel_list/international_news.m3u",
    "Kids": "https://raw.githubusercontent.com/shakilemon73/my-m3u-playlist/main/channel_list/kids.m3u",
    "Lifestyle": "https://raw.githubusercontent.com/shakilemon73/my-m3u-playlist/main/channel_list/lifestyle.m3u",
    "Movies": "https://raw.githubusercontent.com/shakilemon73/my-m3u-playlist/main/channel_list/movies.m3u",
    "Music": "https://raw.githubusercontent.com/shakilemon73/my-m3u-playlist/main/channel_list/music.m3u",
    "Religious": "https://raw.githubusercontent.com/shakilemon73/my-m3u-playlist/main/channel_list/religious.m3u",
    "Sports & Football": "https://raw.githubusercontent.com/shakilemon73/my-m3u-playlist/main/channel_list/sports_football.m3u"
};

const OUTPUT_FILE = path.join(__dirname, 'channels.json');
const CACHE_FILE = path.join(__dirname, '.cache.json');
const VALIDATION_CONCURRENCY = 15; // Run 15 validation checks in parallel
const VALIDATION_TIMEOUT = 1500;  // 1500ms timeout per stream validation check

// Load HTTP Delta Handshake Cache
let networkCache = {};
if (fs.existsSync(CACHE_FILE)) {
    try {
        networkCache = JSON.parse(fs.readFileSync(CACHE_FILE, 'utf8'));
    } catch (e) {
        console.error("Failed to load delta cache file, starting fresh:", e.message);
    }
}

// Regex matching identical to Android client (ChannelNameNormalizer.kt)
const BRACKETS_REGEX = /\s*\[.*?\]\s*/g;
const PARENTHESES_REGEX = /\s*\(.*?\)\s*/g;
const COUNTRY_PREFIX_REGEX = /^(?:[a-z]{2,3}|us-es|us-en)\s*[:|\-•·]\s*/i;
const QUALITY_TAGS_REGEX = /\b(hd|sd|fhd|uhd|2k|4k|8k|1080p|720p|576p|480p|1080i|720i|50fps|60fps|hevc|h264|h265|h\.264|x264|mpeg\d*|ac3|dd5\.1|stereo)\b/gi;
const SERVER_IDENTIFIERS_REGEX = /\b(server\s*\d+|source\s*\d+|source\s*[a-z]|backup\s*\d*|alt\s*\d*|src\s*\d+|ch\s*\d+|v\.\d+|v\d+|ver\s*\d+)\b/gi;
const CLEANUP_PUNCTUATION_REGEX = /^(?:[\s:||\-•·/\\+]+)|(?:[\s:||\-•·/]+$)/g;
const MULTIPLE_WHITESPACES_REGEX = /\s+/g;

function sanitizeChannelName(name) {
    if (!name) return "";
    let clean = name;
    clean = clean.replace(BRACKETS_REGEX, " ");
    clean = clean.replace(PARENTHESES_REGEX, " ");
    clean = clean.replace(COUNTRY_PREFIX_REGEX, " ");
    clean = clean.replace(QUALITY_TAGS_REGEX, " ");
    clean = clean.replace(SERVER_IDENTIFIERS_REGEX, " ");
    clean = clean.replace(CLEANUP_PUNCTUATION_REGEX, "");
    clean = clean.replace(MULTIPLE_WHITESPACES_REGEX, " ");
    return clean.trim();
}

function getNormalizedKey(name) {
    return sanitizeChannelName(name).toLowerCase();
}

// Fetch helper that respects conditional headers (ETag/Last-Modified)
function fetchWithDeltaHandshake(url) {
    return new Promise((resolve, reject) => {
        const cached = networkCache[url] || {};
        const headers = {};
        
        if (cached.etag) {
            headers['If-None-Match'] = cached.etag;
        }
        if (cached.lastModified) {
            headers['If-Modified-Since'] = cached.lastModified;
        }

        const req = https.get(url, { headers }, (res) => {
            if (res.statusCode === 304) {
                console.log(`[304 NOT MODIFIED] ${url}`);
                return resolve({ changed: false, data: cached.data });
            }

            if (res.statusCode !== 200) {
                return reject(new Error(`HTTP status code ${res.statusCode} for URL: ${url}`));
            }

            const newEtag = res.headers['etag'];
            const newLastModified = res.headers['last-modified'];

            let data = '';
            res.on('data', (chunk) => { data += chunk; });
            res.on('end', () => {
                // Update delta cache
                networkCache[url] = {
                    etag: newEtag || null,
                    lastModified: newLastModified || null,
                    data: data
                };
                resolve({ changed: true, data: data });
            });
        });

        req.on('error', (err) => {
            reject(err);
        });

        req.setTimeout(10000, () => {
            req.destroy();
            reject(new Error(`Timeout fetching ${url}`));
        });
    });
}

// Simple M3U Parser
function parseM3u(text) {
    const channels = [];
    const lines = text.split(/\r?\n/);
    let currentChannel = null;

    for (const line of lines) {
        const trimmed = line.trim();
        if (trimmed.startsWith('#EXTM3U')) {
            continue;
        }

        if (trimmed.startsWith('#EXTINF:')) {
            // Parse info
            currentChannel = {
                name: "",
                logoUrl: "",
                tvgId: "",
                tvgName: "",
                groupTitle: ""
            };

            // Extract tvg-logo
            const logoMatch = trimmed.match(/tvg-logo=["'](.*?)["']/i);
            if (logoMatch) currentChannel.logoUrl = logoMatch[1];

            // Extract tvg-id
            const idMatch = trimmed.match(/tvg-id=["'](.*?)["']/i);
            if (idMatch) currentChannel.tvgId = idMatch[1];

            // Extract tvg-name
            const nameMatch = trimmed.match(/tvg-name=["'](.*?)["']/i);
            if (nameMatch) currentChannel.tvgName = nameMatch[1];

            // Extract group-title
            const groupMatch = trimmed.match(/group-title=["'](.*?)["']/i);
            if (groupMatch) currentChannel.groupTitle = groupMatch[1];

            // Extract name (last element of #EXTINF line after trailing comma)
            const commaIndex = trimmed.lastIndexOf(',');
            if (commaIndex !== -1) {
                currentChannel.name = trimmed.substring(commaIndex + 1).trim();
            }
        } else if (trimmed.startsWith('http://') || trimmed.startsWith('https://')) {
            if (currentChannel) {
                currentChannel.streamUrl = trimmed;
                channels.push(currentChannel);
                currentChannel = null;
            }
        }
    }
    return channels;
}

// Stream Checker (HEAD or small GET, returns true if working)
function validateStreamUrl(url) {
    return new Promise((resolve) => {
        const urlObj = new URL(url);
        const protocol = urlObj.protocol === 'https:' ? https : http;
        
        const options = {
            method: 'HEAD',
            hostname: urlObj.hostname,
            path: urlObj.pathname + urlObj.search,
            port: urlObj.port || undefined,
            headers: {
                'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) ExoPlayer/Client'
            }
        };

        const req = protocol.request(options, (res) => {
            const status = res.statusCode;
            if (status >= 200 && status < 400) {
                resolve(true);
            } else {
                // If HEAD fails, retry once with GET (some servers block HEAD)
                retryWithGet(url).then(resolve);
            }
        });

        req.on('error', () => {
            retryWithGet(url).then(resolve);
        });

        req.setTimeout(VALIDATION_TIMEOUT, () => {
            req.destroy();
            resolve(false);
        });

        req.end();
    });
}

function retryWithGet(url) {
    return new Promise((resolve) => {
        const urlObj = new URL(url);
        const protocol = urlObj.protocol === 'https:' ? https : http;
        
        const options = {
            method: 'GET',
            hostname: urlObj.hostname,
            path: urlObj.pathname + urlObj.search,
            port: urlObj.port || undefined,
            headers: {
                'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) ExoPlayer/Client',
                'Range': 'bytes=0-1024' // Download only first KB
            }
        };

        const req = protocol.request(options, (res) => {
            const status = res.statusCode;
            resolve(status >= 200 && status < 400);
        });

        req.on('error', () => {
            resolve(false);
        });

        req.setTimeout(VALIDATION_TIMEOUT, () => {
            req.destroy();
            resolve(false);
        });

        req.end();
    });
}

// Concurrency-controlled Worker Pool
async function processValidationQueue(tasks) {
    const results = [];
    let activeCount = 0;
    let index = 0;

    return new Promise((resolve) => {
        function next() {
            if (index >= tasks.length && activeCount === 0) {
                return resolve(results);
            }

            while (activeCount < VALIDATION_CONCURRENCY && index < tasks.length) {
                const currentIdx = index++;
                const url = tasks[currentIdx];
                activeCount++;
                
                validateStreamUrl(url).then((working) => {
                    results[currentIdx] = { url, working };
                    activeCount--;
                    next();
                });
            }
        }
        next();
    });
}

// Main Execution
async function main() {
    console.log("=== Starting IPTV Server-Side Generator Engine ===");
    
    const parsedPlaylists = {};
    let totalLoadedChannelsCount = 0;

    // 1. Fetch playlists with Delta checking
    for (const [category, url] of Object.entries(M3U_PLAYLISTS)) {
        try {
            console.log(`[Fetching] ${category} ...`);
            const { changed, data } = await fetchWithDeltaHandshake(url);
            const parsed = parseM3u(data);
            parsedPlaylists[category] = parsed;
            totalLoadedChannelsCount += parsed.length;
            console.log(`  Parsed ${parsed.length} channels from ${category}`);
        } catch (e) {
            console.error(`  [FAILED] to parse or fetch playlist ${category}:`, e.message);
            // Fallback to cache if available
            if (networkCache[url] && networkCache[url].data) {
                const parsed = parseM3u(networkCache[url].data);
                parsedPlaylists[category] = parsed;
                totalLoadedChannelsCount += parsed.length;
                console.log(`  [CACHE FALLBACK] Restored ${parsed.length} channels for ${category}`);
            }
        }
    }

    // Save Updated Delta Cache
    fs.writeFileSync(CACHE_FILE, JSON.stringify(networkCache, null, 2));
    console.log(`Saved conditional GET metadata to cache file.`);

    // 2. Fuzzy Deduplication & Grouping
    console.log("Grouping and deduplicating channel listings...");
    const groupedChannelsMap = {};
    
    let catIdCounter = 1;
    const categoryIdsMap = {};

    for (const [category, channelsList] of Object.entries(parsedPlaylists)) {
        if (!categoryIdsMap[category]) {
            categoryIdsMap[category] = catIdCounter++;
        }
        const categoryId = categoryIdsMap[category];

        for (const rawCh of channelsList) {
            const key = getNormalizedKey(rawCh.name);
            if (!groupedChannelsMap[key]) {
                groupedChannelsMap[key] = {
                    name: sanitizeChannelName(rawCh.name),
                    logoUrl: rawCh.logoUrl,
                    category: category,
                    categoryId: categoryId,
                    description: `Unified stream for ${sanitizeChannelName(rawCh.name)}`,
                    tvgId: rawCh.tvgId,
                    tvgName: rawCh.tvgName,
                    rawUrls: []
                };
            }

            // Append URL source if not already present
            if (!groupedChannelsMap[key].rawUrls.includes(rawCh.streamUrl)) {
                groupedChannelsMap[key].rawUrls.push(rawCh.streamUrl);
            }

            // Fill missing fields with best values
            if (!groupedChannelsMap[key].logoUrl && rawCh.logoUrl) {
                groupedChannelsMap[key].logoUrl = rawCh.logoUrl;
            }
            if (!groupedChannelsMap[key].tvgId && rawCh.tvgId) {
                groupedChannelsMap[key].tvgId = rawCh.tvgId;
            }
        }
    }

    const uniqueStreamUrls = new Set();
    Object.values(groupedChannelsMap).forEach(ch => {
        ch.rawUrls.forEach(url => uniqueStreamUrls.add(url));
    });

    console.log(`Grouped into ${Object.keys(groupedChannelsMap).length} unique channels.`);
    console.log(`Extracted ${uniqueStreamUrls.size} unique stream source URLs for validation.`);

    // 3. Validation Pool (Concurrently validate each URL)
    console.log(`Starting parallel stream validation (concurrency = ${VALIDATION_CONCURRENCY}, timeout = ${VALIDATION_TIMEOUT}ms)...`);
    const validationResults = await processValidationQueue([...uniqueStreamUrls]);
    
    // Map URL to validation status
    const statusMap = {};
    validationResults.forEach(res => {
        statusMap[res.url] = res.working;
    });

    const workingCount = validationResults.filter(r => r.working).length;
    console.log(`Stream validation finished. ${workingCount}/${validationResults.length} sources are ONLINE.`);

    // 4. Assemble Master Output
    const finalChannels = [];
    const activeCategories = new Set();

    Object.values(groupedChannelsMap).forEach(ch => {
        const streams = ch.rawUrls.map((url, idx) => ({
            url: url,
            name: `Source ${idx + 1}`,
            isBroken: !statusMap[url]
        }));

        // Keep channel if at least one source is working
        const allBroken = streams.every(s => s.isBroken);
        
        // Filter out channels where ALL sources are dead (optional: comment out if you want to keep broken channels in the list)
        if (allBroken) {
            return; // Skip fully dead channels to keep app start lightning fast!
        }

        finalChannels.push({
            name: ch.name,
            logoUrl: ch.logoUrl,
            category: ch.category,
            categoryId: ch.categoryId,
            description: ch.description,
            tvgId: ch.tvgId,
            tvgName: ch.tvgName,
            streams: streams
        });

        activeCategories.add(ch.category);
    });

    // Generate categories list
    const finalCategories = Array.from(activeCategories).map(catName => ({
        id: categoryIdsMap[catName],
        name: catName
    })).sort((a, b) => a.name.localeCompare(b.name));

    const masterJson = {
        updatedAt: Date.now(),
        categories: finalCategories,
        channels: finalChannels
    };

    // Save final master JSON
    fs.writeFileSync(OUTPUT_FILE, JSON.stringify(masterJson, null, 2));
    console.log(`SUCCESS! Wrote ${finalChannels.length} active multi-source channels to: ${OUTPUT_FILE}`);
}

main().catch(err => {
    console.error("FATAL ERROR Running Server-Side Engine:", err);
    process.exit(1);
});
