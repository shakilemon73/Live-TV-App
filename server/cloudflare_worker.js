/**
 * Cloudflare Worker — Supercharged IPTV Intelligence Engine v2.0
 *
 * NEW in v2 vs v1:
 * 1. AES-128/CBC Stream URL Encryption — identical to Android StreamDecryptionUtility.kt
 *    (Web Crypto API, same IV, same PKCS7 padding, same base64-url output format)
 * 2. Full 18-Category Channel Classifier — full port of ChannelClassifier.kt:
 *    - 50+ exact channel-to-category DIRECT_MAPPINGS dictionary
 *    - Live sports pattern detection (brackets, "vs", "world cup", strmcntr, fawa, ufc, wwe)
 *    - Cosine-similarity engine with weighted bigram/unigram TF-IDF vectors
 *    - Category centroid vectors for 18 categories
 *    - Group-title prior boosting
 * 3. Reputation Scorer — every channel gets a 0-100 quality score; lists sorted by it
 * 4. Full M3U Parser — full port of M3uParserService.parseM3uReader():
 *    - #EXTVLCOPT: header parsing (Referer, Origin, User-Agent appended as |k=v to URL)
 *    - Auto-injects Referer/Origin/UA for Fawanews and Daddylive restricted streams
 *    - Parses url-tvg/x-tvg-url from #EXTM3U header
 *    - Generates fallback name from URL path if #EXTINF has no name
 * 5. Full URL Normalizer — adds Pastebin + gist.github.com support on top of existing GitHub
 * 6. Normalized-name cache — O(1) channel name deduplication
 * 7. /api/key — AES key rotation endpoint (for StreamDecryptionUtility.fetchAndRotateKey())
 * 8. /api/stream — AES-strength secure playback redirect (replaces old /api/play XOR scheme)
 */

// ─── 1. PLAYLIST CONFIGURATION ─────────────────────────────────────────────

const M3U_PLAYLISTS = {
  "BDIX IPTV":           "https://raw.githubusercontent.com/abusaeeidx/Mrgify-BDIX-IPTV/main/playlist.m3u",
  "Animation":           "https://raw.githubusercontent.com/shakilemon73/my-m3u-playlist/main/channel_list/animation.m3u",
  "Auto":                "https://raw.githubusercontent.com/shakilemon73/my-m3u-playlist/main/channel_list/auto.m3u",
  "Bangla Channel":      "https://raw.githubusercontent.com/shakilemon73/my-m3u-playlist/main/channel_list/bangla-channel.m3u",
  "Bangla News":         "https://raw.githubusercontent.com/shakilemon73/my-m3u-playlist/main/channel_list/bangla_news.m3u",
  "Business":            "https://raw.githubusercontent.com/shakilemon73/my-m3u-playlist/main/channel_list/business.m3u",
  "Comedy":              "https://raw.githubusercontent.com/shakilemon73/my-m3u-playlist/main/channel_list/comedy.m3u",
  "Cricket":             "https://raw.githubusercontent.com/shakilemon73/my-m3u-playlist/main/channel_list/cricket.m3u",
  "Documentary":         "https://raw.githubusercontent.com/shakilemon73/my-m3u-playlist/main/channel_list/documentary.m3u",
  "Entertainment":       "https://raw.githubusercontent.com/shakilemon73/my-m3u-playlist/main/channel_list/entertainment.m3u",
  "International News":  "https://raw.githubusercontent.com/shakilemon73/my-m3u-playlist/main/channel_list/international_news.m3u",
  "Kids":                "https://raw.githubusercontent.com/shakilemon73/my-m3u-playlist/main/channel_list/kids.m3u",
  "Lifestyle":           "https://raw.githubusercontent.com/shakilemon73/my-m3u-playlist/main/channel_list/lifestyle.m3u",
  "Movies":              "https://raw.githubusercontent.com/shakilemon73/my-m3u-playlist/main/channel_list/movies.m3u",
  "Music":               "https://raw.githubusercontent.com/shakilemon73/my-m3u-playlist/main/channel_list/music.m3u",
  "Religious":           "https://raw.githubusercontent.com/shakilemon73/my-m3u-playlist/main/channel_list/religious.m3u",
  "Sports & Football":   "https://raw.githubusercontent.com/shakilemon73/my-m3u-playlist/main/channel_list/sports_football.m3u",
  "Live Events":         "https://raw.githubusercontent.com/shakilemon73/my-m3u-playlist/main/channel_list/live-event.m3u",
  "Doms9 Base":          "https://raw.githubusercontent.com/doms9/iptv/refs/heads/default/M3U8/base.m3u8",
  "Doms9 US TV":         "https://raw.githubusercontent.com/doms9/iptv/refs/heads/default/M3U8/TV.m3u8"
};

// Admin token — change before deploying
const ADMIN_SECRET_TOKEN = "SUPER_SECURE_ADMIN_TOKEN_CHANGE_ME";

// ─── 2. MAIN ROUTING EXPORT ─────────────────────────────────────────────────

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    const path = url.pathname;

    const corsHeaders = {
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "GET, HEAD, OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type, Authorization, X-Requested-With",
      "Access-Control-Max-Age": "86400",
    };

    if (request.method === "OPTIONS") {
      return new Response(null, { headers: corsHeaders });
    }

    try {
      if (path === "/api/channels" || path === "/channels") {
        return await handleGetChannels(request, env, ctx, corsHeaders);
      }
      if (path === "/api/events" || path === "/events") {
        return await handleGetEvents(request, env, ctx, corsHeaders);
      }
      if (path === "/api/sync" || path === "/sync") {
        const tokenParam = url.searchParams.get("token");
        if (tokenParam !== ADMIN_SECRET_TOKEN) {
          return new Response(JSON.stringify({ error: "Unauthorized access" }), {
            status: 401, headers: { "Content-Type": "application/json", ...corsHeaders }
          });
        }
        ctx.waitUntil(synchronizePlaylists(env));
        return new Response(JSON.stringify({ message: "Background channel synchronization triggered" }), {
          status: 202, headers: { "Content-Type": "application/json", ...corsHeaders }
        });
      }
      // Secure AES playback redirect (replaces old /api/play XOR scheme)
      if (path === "/api/stream" || path === "/stream") {
        return await handleStreamRedirect(request, url, env, corsHeaders);
      }
      // Legacy /api/play kept for backward compatibility
      if (path === "/api/play" || path === "/play") {
        return await handleStreamRedirect(request, url, env, corsHeaders);
      }
      // AES key rotation endpoint (for StreamDecryptionUtility.fetchAndRotateKey())
      if (path === "/api/key" || path === "/key") {
        return await handleGetKey(request, env, corsHeaders);
      }
      if (path === "/api/status" || path === "/status") {
        return await handleGetStatus(env, corsHeaders);
      }
      // ─── Delta Change Detection ──────────────────────────────────────────────────
      // Lightweight endpoint: Android app polls this to check if anything changed
      // since its last sync, without downloading the full channel list.
      if (path === "/api/changes" || path === "/changes") {
        return await handleGetChanges(url, env, corsHeaders);
      }
      // ─── World-Class Server-Assisted Stream Validation (single URL) ─────
      if (path === "/api/validate" || path === "/validate") {
        return await handleValidateStream(request, url, env, corsHeaders);
      }
      // ─── Batch Server-Assisted Validation ──────────────────────────────
      // POST { urls: ["encrypted://...", ...] } — validates up to 10 encrypted URLs
      // per request, decrypts server-side, probes each, returns
      // [{ url, working, status, reason }]. Real URLs never leave the Worker.
      if ((path === "/api/validate-batch" || path === "/validate-batch") && request.method === "POST") {
        return await handleValidateBatch(request, env, corsHeaders);
      }
      return new Response(JSON.stringify({ error: "Endpoint not found" }), {
        status: 404, headers: { "Content-Type": "application/json", ...corsHeaders }
      });
    } catch (err) {
      return new Response(JSON.stringify({ error: "Internal Server Error", message: err.message }), {
        status: 500, headers: { "Content-Type": "application/json", ...corsHeaders }
      });
    }
  },

  async scheduled(event, env, ctx) {
    console.log("Cron trigger fired: revalidating and compiling IPTV playlists...");
    ctx.waitUntil(synchronizePlaylists(env));
  }
};

// ─── 3. ROUTE HANDLERS ──────────────────────────────────────────────────────
async function handleGetChannels(request, env, ctx, corsHeaders) {
  const urlObj = new URL(request.url);
  const sinceParam = urlObj.searchParams.get("since");
  const secureMode = urlObj.searchParams.get("secure") !== "false";

  // 1. Construct a clean, sanitized URL for the Cloudflare CDN Cache Key.
  // This removes any random query parameters to prevent cache-busting.
  const cleanCacheUrl = new URL(urlObj.origin + urlObj.pathname);
  if (sinceParam !== null) {
    const since = parseInt(sinceParam, 10);
    if (!isNaN(since) && since > 0) {
      cleanCacheUrl.searchParams.set("since", since.toString());
    }
  }
  cleanCacheUrl.searchParams.set("secure", secureMode.toString());

  const cache = caches.default;
  const cacheKey = new Request(cleanCacheUrl.toString(), {
    method: "GET",
    headers: request.headers
  });

  // 2. Check CDN Cache first (this is free and requires 0 KV reads / 0 Worker request bills!)
  const cachedResponse = await cache.match(cacheKey);
  if (cachedResponse) {
    console.log("Serving channels from Cloudflare CDN Edge Cache...");
    return cachedResponse;
  }

  // 3. Cache Miss: Execute validation / query KV
  let channelsPayload = null;
  if (env.IPTV_KV) {
    channelsPayload = await env.IPTV_KV.get("compiled_channels");
  }

  if (!channelsPayload) {
    console.log("KV empty/unavailable. Running dynamic compile...");
    const freshData = await compileMasterChannelsList(env);
    channelsPayload = JSON.stringify(freshData);
    if (env.IPTV_KV) {
      ctx.waitUntil(env.IPTV_KV.put("compiled_channels", channelsPayload, { expirationTtl: 86400 }));
    }
  }

  const meta = JSON.parse(channelsPayload);
  const lastChangeAt = meta.lastChangeAt || meta.updatedAt || 0;

  // ── Lightweight ?since= check (thin-client fast path) ──
  if (sinceParam !== null) {
    const since = parseInt(sinceParam, 10);
    if (!isNaN(since) && since > 0 && lastChangeAt <= since) {
      const unchangedResponse = new Response(JSON.stringify({ 
        changed: false, 
        lastChangeAt, 
        totalChannels: (meta.channels || []).length 
      }), {
        status: 200,
        headers: { 
          "Content-Type": "application/json; charset=utf-8", 
          // Cache the { changed: false } response at edge for 5 minutes (300 seconds)
          "Cache-Control": "public, max-age=300, s-maxage=300, stale-while-revalidate=60",
          ...corsHeaders 
        }
      });
      // Store in CDN cache
      ctx.waitUntil(cache.put(cacheKey, unchangedResponse.clone()));
      return unchangedResponse;
    }
  }

  // ── Serve full channel list (Changed or initial load) ──
  if (secureMode) {
    // Encrypt all stream URLs using AES-128/CBC (matches Android StreamDecryptionUtility)
    meta.channels = await Promise.all(meta.channels.map(async channel => {
      const securedStreams = await Promise.all(channel.streams.map(async stream => ({
        ...stream,
        url: await encryptUrl(stream.url, env),
        isObfuscated: true,
        format: detectVideoFormat(stream.url)
      })));
      return { ...channel, streams: securedStreams };
    }));
  } else {
    meta.channels = meta.channels.map(channel => ({
      ...channel,
      streams: channel.streams.map(stream => ({
        ...stream,
        format: detectVideoFormat(stream.url)
      }))
    }));
  }

  // Always include changed: true when returning a full payload so the client knows to import
  meta.changed = true;

  const responseBody = JSON.stringify(meta);
  const fullResponse = new Response(responseBody, {
    status: 200,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      // Cache the full response at edge for 5 minutes (300 seconds)
      "Cache-Control": "public, max-age=300, s-maxage=300, stale-while-revalidate=60",
      ...corsHeaders
    }
  });

  // Store in CDN cache
  ctx.waitUntil(cache.put(cacheKey, fullResponse.clone()));
  return fullResponse;
}

function parsePipeStreamUrl(streamUrl) {
  const parts = streamUrl.split("|");
  const cleanUrl = parts[0];
  const customHeaders = {};
  if (parts.length > 1) {
    const params = parts[1].split("&");
    params.forEach(param => {
      const kv = param.split("=", 2);
      if (kv.length === 2) {
        customHeaders[kv[0]] = decodeURIComponent(kv[1]);
      }
    });
  }
  return { cleanUrl, customHeaders };
}

// ─── World-Class Stream Validation Engine ───────────────────────────────────

/**
 * HTML portal / ISP redirect page detection.
 * Returns true if the body snippet looks like a captive portal or error page.
 */
function looksLikeHtmlPortal(bodySnippet, contentType) {
  if (!bodySnippet) return false;
  const lower = bodySnippet.toLowerCase();
  // Definitive HTML markers
  if (lower.includes("<!doctype html") || lower.includes("<html")) return true;
  // ISP / Cloudflare error pages
  if (lower.includes("error") && lower.includes("<title")) return true;
  // Blank redirect placeholders sometimes served for dead streams
  if (contentType.includes("text/html")) return true;
  return false;
}

/**
 * For .m3u8 / HLS streams: verify the body starts with #EXTM3U.
 * A server returning a 200 OK with anything else is a broken manifest.
 */
function isValidHlsManifest(bodySnippet) {
  if (!bodySnippet) return false;
  const trimmed = bodySnippet.trim();
  return trimmed.startsWith("#EXTM3U") || trimmed.startsWith("#EXT-X-VERSION");
}

/**
 * Validate a PLAIN (non-encrypted) stream URL with world-class multi-phase probing.
 * Returns { working: bool, httpStatus: number|null, reason: string }
 */
async function validateStreamUrl(streamUrl, env, timeoutMs = 3000) {
  const { cleanUrl, customHeaders } = parsePipeStreamUrl(streamUrl);
  
  const isHls = cleanUrl.includes(".m3u8") || cleanUrl.includes(".m3u");
  const isDash = cleanUrl.includes(".mpd");
  const isMediaUrl = isHls || isDash ||
    cleanUrl.includes(".ts") || cleanUrl.includes(".mp4") ||
    cleanUrl.includes("/hls/") || cleanUrl.includes("/live/");

  const baseHeaders = {
    "User-Agent": customHeaders["User-Agent"] || customHeaders["user-agent"] ||
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Accept": "*/*"
  };
  for (const [key, val] of Object.entries(customHeaders)) {
    if (key.toLowerCase() !== "user-agent") baseHeaders[key] = val;
  }

  // ── Phase 1: HEAD request ──
  let headResult = null;
  try {
    const ctrl = new AbortController();
    const tid = setTimeout(() => ctrl.abort(), timeoutMs);
    const resp = await fetch(cleanUrl, { method: "HEAD", headers: baseHeaders, signal: ctrl.signal });
    clearTimeout(tid);
    const code = resp.status;
    const ct = (resp.headers.get("Content-Type") || "").toLowerCase();

    if (code === 404 || code === 410 || code === 451) {
      return { working: false, httpStatus: code, reason: `Dead link (HTTP ${code})` };
    }
    if (code >= 200 && code < 400) {
      // HTML portal on a stream URL = broken
      if (ct.includes("text/html") && !isHls) {
        headResult = { working: false, httpStatus: code, reason: "HTML portal response" };
      } else {
        const validCt = ct.includes("mpegurl") || ct.includes("video/") ||
          ct.includes("audio/") || ct.includes("dash+xml") ||
          ct.includes("octet-stream") || ct.includes("binary/") || ct === "";
        if (validCt) {
          headResult = { working: true, httpStatus: code, reason: "HEAD OK" };
        }
        // Empty / unknown content-type: fall through to GET body sniff
      }
    }
    // 405 Method Not Allowed: server rejects HEAD — fall through to GET
    if (code === 403 || code === 401) {
      return { working: false, httpStatus: code, reason: `Blocked (HTTP ${code})` };
    }
  } catch (_) {
    // Network error / timeout — fall through to GET
  }

  if (headResult !== null) {
    // For HLS, even a successful HEAD must be confirmed by manifest body sniff
    if (headResult.working && isHls) {
      // Fall through to GET body sniff below
    } else {
      return headResult;
    }
  }

  // ── Phase 2: GET with byte-range + body sniff ──
  try {
    const ctrl = new AbortController();
    const tid = setTimeout(() => ctrl.abort(), timeoutMs);
    const resp = await fetch(cleanUrl, {
      method: "GET",
      headers: { ...baseHeaders, "Range": "bytes=0-2048" },
      signal: ctrl.signal
    });
    clearTimeout(tid);
    const code = resp.status;
    const ct = (resp.headers.get("Content-Type") || "").toLowerCase();

    // Read first 2 KB of body for sniffing
    const reader = resp.body?.getReader();
    let bodySnippet = "";
    if (reader) {
      const { value } = await reader.read();
      reader.cancel();
      if (value) bodySnippet = new TextDecoder().decode(value.slice(0, 2048));
    }

    if (code === 404 || code === 410 || code === 451) {
      return { working: false, httpStatus: code, reason: `Dead link (HTTP ${code})` };
    }
    if (code === 403 || code === 401) {
      return { working: false, httpStatus: code, reason: `Blocked (HTTP ${code})` };
    }
    if (code >= 200 && code < 400 || code === 206) {
      // HTML portal / ISP intercept detection
      if (looksLikeHtmlPortal(bodySnippet, ct)) {
        return { working: false, httpStatus: code, reason: "HTML portal intercepted response" };
      }
      // HLS manifest validation
      if (isHls) {
        if (isValidHlsManifest(bodySnippet)) {
          return { working: true, httpStatus: code, reason: "HLS manifest verified" };
        } else if (bodySnippet.length > 0) {
          return { working: false, httpStatus: code, reason: "M3U8 URL returned non-HLS body" };
        }
      }
      // Generic media content-type check
      const validCt = ct.includes("mpegurl") || ct.includes("video/") ||
        ct.includes("audio/") || ct.includes("dash+xml") ||
        ct.includes("octet-stream") || ct.includes("binary/") || ct === "";
      if (validCt) {
        return { working: true, httpStatus: code, reason: "GET OK with valid content-type" };
      }
      // Non-empty body that doesn't look like HTML: treat as working (some servers send custom types)
      if (bodySnippet.length > 10 && !looksLikeHtmlPortal(bodySnippet, ct)) {
        return { working: true, httpStatus: code, reason: "GET OK with non-empty non-HTML body" };
      }
      return { working: false, httpStatus: code, reason: `Unexpected content-type: ${ct}` };
    }
    return { working: false, httpStatus: code, reason: `HTTP ${code}` };
  } catch (err) {
    return { working: false, httpStatus: null, reason: `Network error: ${err.message}` };
  }
}

/**
 * Handler for /api/validate — Android app calls this to validate an encrypted:// token.
 * The worker decrypts the token, probes the real URL, and returns { working, status, reason }.
 * The real URL is NEVER sent back to the client.
 */
async function handleValidateStream(request, url, env, corsHeaders) {
  const tokenParam = url.searchParams.get("t") || url.searchParams.get("url");
  if (!tokenParam) {
    return new Response(JSON.stringify({ working: false, reason: "Missing token parameter" }), {
      status: 400, headers: { "Content-Type": "application/json", ...corsHeaders }
    });
  }

  let realUrl;
  try {
    // decryptUrl() expects a full "encrypted://base64payload" string.
    // The Android app may send either the full string or just the raw base64 payload.
    const token = tokenParam.startsWith("encrypted://")
      ? tokenParam                              // Already has the prefix — pass as-is
      : "encrypted://" + tokenParam;           // Raw payload — reconstruct the prefix
    realUrl = await decryptUrl(token, env);
  } catch (e) {
    return new Response(JSON.stringify({ working: false, reason: "Decryption failed" }), {
      status: 200, headers: { "Content-Type": "application/json", ...corsHeaders }
    });
  }

  if (!realUrl || (!realUrl.startsWith("http://") && !realUrl.startsWith("https://")
      && !realUrl.startsWith("rtmp") && !realUrl.startsWith("rtsp"))) {
    return new Response(JSON.stringify({ working: false, reason: "Invalid decrypted URL" }), {
      status: 200, headers: { "Content-Type": "application/json", ...corsHeaders }
    });
  }

  const result = await validateStreamUrl(realUrl, env, 5000); // Use 5s timeout for on-demand checks
  return new Response(JSON.stringify({
    working: result.working,
    status: result.httpStatus,
    reason: result.reason
    // NOTE: realUrl is intentionally NOT included in the response
  }), {
    status: 200,
    headers: {
      "Content-Type": "application/json",
      "Cache-Control": "no-store",
      ...corsHeaders
    }
  });
}

/**
 * /api/validate-batch — POST { urls: ["encrypted://...", ...] }
 *
 * Batch server-side validation for up to 10 encrypted stream URLs.
 * Android sends broken/suspect channels here for re-verification without
 * exposing real stream URLs. Each URL is decrypted, probed in parallel,
 * and the result (working: bool) returned per-URL.
 *
 * Subrequest budget: 10 URLs × ~2 subrequests (HEAD + GET) = 20 subrequests,
 * well within Cloudflare's 50 subrequest cap per invocation.
 */
async function handleValidateBatch(request, env, corsHeaders) {
  let body;
  try {
    body = await request.json();
  } catch (_) {
    return new Response(JSON.stringify({ error: "Invalid JSON body" }), {
      status: 400, headers: { "Content-Type": "application/json", ...corsHeaders }
    });
  }

  const urls = Array.isArray(body?.urls) ? body.urls : [];
  if (urls.length === 0) {
    return new Response(JSON.stringify({ error: "No URLs provided" }), {
      status: 400, headers: { "Content-Type": "application/json", ...corsHeaders }
    });
  }

  // Hard cap at 10 URLs per batch to stay well within subrequest limits
  const batch = urls.slice(0, 10);

  const results = await Promise.all(batch.map(async (encToken) => {
    const token = typeof encToken === "string" ? encToken.trim() : "";
    if (!token) return { url: token, working: false, reason: "Empty token" };

    // Reconstruct the full "encrypted://..." string if caller stripped the prefix
    const fullToken = token.startsWith("encrypted://") ? token : "encrypted://" + token;

    let realUrl;
    try {
      realUrl = await decryptUrl(fullToken, env);
    } catch (_) {
      return { url: token, working: false, reason: "Decryption failed" };
    }

    if (!realUrl || (!realUrl.startsWith("http://") && !realUrl.startsWith("https://")
        && !realUrl.startsWith("rtmp") && !realUrl.startsWith("rtsp"))) {
      return { url: token, working: false, reason: "Invalid decrypted URL" };
    }

    const result = await validateStreamUrl(realUrl, env, 4000);
    return {
      url: token,           // Echo back the encrypted token (not the real URL!)
      working: result.working,
      status: result.httpStatus,
      reason: result.reason
    };
  }));

  return new Response(JSON.stringify({ results }), {
    status: 200,
    headers: {
      "Content-Type": "application/json",
      "Cache-Control": "no-store",
      ...corsHeaders
    }
  });
}

async function rewriteM3u8RelativeUrls(text, baseUrl, env, requestUrl) {
  const lines = text.split(/\r?\n/);
  const proxyUrlBase = new URL("/api/stream?t=", requestUrl).toString();
  
  const rewrittenLines = await Promise.all(lines.map(async line => {
    const trimmed = line.trim();
    if (!trimmed) return line;
    
    if (trimmed.startsWith("#")) {
      if (trimmed.startsWith("#EXT-X-KEY:") || trimmed.startsWith("#EXT-X-MAP:")) {
        const match = trimmed.match(/URI=["']([^"']*)["']/i);
        if (match) {
          try {
            const absoluteUri = new URL(match[1], baseUrl).toString();
            const encryptedUri = await encryptUrl(absoluteUri, env);
            const proxiedUri = proxyUrlBase + encodeURIComponent(encryptedUri);
            return trimmed.replace(/URI=["']([^"']*)["']/i, `URI="${proxiedUri}"`);
          } catch {
            return line;
          }
        }
      }
      return line;
    }
    
    try {
      const absoluteUrl = new URL(trimmed, baseUrl).toString();
      const encryptedUrl = await encryptUrl(absoluteUrl, env);
      return proxyUrlBase + encodeURIComponent(encryptedUrl);
    } catch {
      return line;
    }
  }));
  
  return rewrittenLines.join("\n");
}

async function handleStreamRedirect(request, url, env, corsHeaders) {
  // Support both ?t=<token> (new) and ?url=<token> (legacy)
  const tokenParam = url.searchParams.get("t") || url.searchParams.get("url");
  if (!tokenParam) {
    return new Response("Missing stream token parameter", { status: 400, headers: corsHeaders });
  }
  const realUrl = await decryptUrl(tokenParam, env);
  if (!realUrl || (!realUrl.startsWith("http://") && !realUrl.startsWith("https://") && !realUrl.startsWith("rtmp") && !realUrl.startsWith("rtsp"))) {
    return new Response("Invalid or unresolvable stream token", { status: 400, headers: corsHeaders });
  }

  // Parse pipe-separated URL and custom headers
  const { cleanUrl, customHeaders } = parsePipeStreamUrl(realUrl);

  const requestHeaders = new Headers();
  
  // Set default User-Agent if not present
  const defaultUA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
  requestHeaders.set("User-Agent", customHeaders["User-Agent"] || customHeaders["user-agent"] || defaultUA);
  
  for (const [key, val] of Object.entries(customHeaders)) {
    if (key.toLowerCase() !== "user-agent") {
      requestHeaders.set(key, val);
    }
  }

  try {
    const response = await fetch(cleanUrl, {
      method: "GET",
      headers: requestHeaders
    });

    if (!response.ok) {
      // Fallback to 302 redirect in case of proxy fetch failure
      return Response.redirect(cleanUrl, 302);
    }

    let contentType = response.headers.get("Content-Type") || "application/vnd.apple.mpegurl";

    const responseHeaders = new Headers(corsHeaders);
    responseHeaders.set("Content-Type", contentType);
    
    const cacheControl = response.headers.get("Cache-Control");
    if (cacheControl) {
      responseHeaders.set("Cache-Control", cacheControl);
    } else {
      responseHeaders.set("Cache-Control", "no-cache, no-store, must-revalidate");
    }

    const isPlaylist = cleanUrl.includes(".m3u8") || cleanUrl.includes(".m3u") || contentType.includes("mpegurl") || contentType.includes("mpegURL");
    
    if (isPlaylist) {
      const playlistText = await response.text();
      const rewrittenPlaylist = await rewriteM3u8RelativeUrls(playlistText, cleanUrl, env, request.url);
      return new Response(rewrittenPlaylist, {
        status: response.status,
        headers: responseHeaders
      });
    }

    return new Response(response.body, {
      status: response.status,
      statusText: response.statusText,
      headers: responseHeaders
    });
  } catch (error) {
    console.error("Proxy fetch error:", error.message);
    return Response.redirect(cleanUrl, 302);
  }
}

async function handleGetKey(request, env, corsHeaders) {
  // Require Bearer token authorization
  const authHeader = request.headers.get("Authorization") || "";
  const providedToken = authHeader.replace("Bearer ", "").trim();
  if (providedToken !== ADMIN_SECRET_TOKEN) {
    return new Response(JSON.stringify({ error: "Unauthorized" }), {
      status: 401, headers: { "Content-Type": "application/json", ...corsHeaders }
    });
  }
  const keyValue = (env && env.STREAM_DECRYPTION_KEY) || "Secr3tM3u8Str3am";
  return new Response(JSON.stringify({ key: keyValue, decryptionKey: keyValue }), {
    status: 200,
    headers: {
      "Content-Type": "application/json",
      "Cache-Control": "no-store",
      ...corsHeaders
    }
  });
}

async function handleGetStatus(env, corsHeaders) {
  let stats = {
    status: "healthy",
    version: "2.0",
    updatedAt: null,
    totalChannels: 0,
    totalCategories: 0,
    syncEngine: "Cloudflare Workers v2 — Full Intelligence Port",
    encryption: "AES-128/CBC (matches Android StreamDecryptionUtility)",
    classifier: "Cosine Similarity + TF-IDF Bigrams (18 categories)",
    githubIntegration: (env && (env.GITHUB_TOKEN || env.GITHUB_PAT)) ? "Authenticated (Private Playlist Support)" : "Public-Only"
  };
  if (env && env.IPTV_KV) {
    const rawData = await env.IPTV_KV.get("compiled_channels");
    if (rawData) {
      const parsed = JSON.parse(rawData);
      stats.updatedAt = parsed.updatedAt;
      stats.totalChannels = parsed.channels?.length || 0;
      stats.totalCategories = parsed.categories?.length || 0;
    }
  }
  return new Response(JSON.stringify(stats), {
    status: 200, headers: { "Content-Type": "application/json", ...corsHeaders }
  });
}

async function handleGetEvents(request, env, ctx, corsHeaders) {
  const url = new URL(request.url);
  const format = url.searchParams.get("format") || "json";
  const secureMode = url.searchParams.get("secure") !== "false";
  const cache = caches.default;
  const cacheKey = new Request(request.url, request);

  let response = await cache.match(cacheKey);
  if (response) {
    console.log("Serving live events from Cloudflare CDN Cache...");
    return response;
  }

  const eventsSources = [
    "https://raw.githubusercontent.com/doms9/iptv/refs/heads/default/M3U8/events.m3u8",
    "https://raw.githubusercontent.com/shakilemon73/my-m3u-playlist/main/channel_list/live-event.m3u"
  ];

  try {
    const contents = await Promise.all(eventsSources.map(async srcUrl => {
      try {
        const res = await smartFetch(srcUrl, env);
        if (res.ok) return await res.text();
      } catch (err) {
        console.error("Failed to fetch event source:", srcUrl, err.message);
      }
      return "";
    }));

    if (format === "raw") {
      response = new Response(contents.filter(Boolean).join("\n"), {
        status: 200,
        headers: {
          "Content-Type": "text/plain; charset=utf-8",
          "Cache-Control": "public, max-age=600, s-maxage=900, stale-while-revalidate=120",
          ...corsHeaders
        }
      });
    } else {
      const parsedChannels = [];
      for (const content of contents) {
        if (content) parsedChannels.push(...parseM3uText(content));
      }
      let groupedEvents = mapParsedChannelsToGroupedEvents(parsedChannels);

      // Fetch regular master channels to perform LiveEventParser matching/merging on Cloudflare edge
      let dbChannels = [];
      if (env && env.IPTV_KV) {
        const channelsPayload = await env.IPTV_KV.get("compiled_channels");
        if (channelsPayload) {
          const parsedPayload = JSON.parse(channelsPayload);
          dbChannels = parsedPayload.channels || [];
        }
      }

      if (dbChannels.length > 0) {
        groupedEvents = mergeLiveEventsWithMasterChannels(groupedEvents, dbChannels);
      }

      // Secure stream URLs using AES-128-CBC encryption if secure mode is enabled
      if (secureMode) {
        groupedEvents = await Promise.all(groupedEvents.map(async event => {
          const securedFeeds = await Promise.all(event.feeds.map(async feed => ({
            ...feed,
            streamUrl: await encryptUrl(feed.streamUrl, env)
          })));
          return { ...event, feeds: securedFeeds };
        }));
      }

      response = new Response(JSON.stringify(groupedEvents), {
        status: 200,
        headers: {
          "Content-Type": "application/json; charset=utf-8",
          "Cache-Control": "public, max-age=600, s-maxage=900, stale-while-revalidate=120",
          ...corsHeaders
        }
      });
    }
    ctx.waitUntil(cache.put(cacheKey, response.clone()));
    return response;
  } catch (err) {
    return new Response(JSON.stringify({ error: "Failed to compile live events", message: err.message }), {
      status: 500, headers: { "Content-Type": "application/json", ...corsHeaders }
    });
  }
}

// ─── 4. SYNCHRONIZER PIPELINE ───────────────────────────────────────────────

// ─── SHA-256 helper for playlist change detection ───────────────────────────
async function sha256hex(text) {
  const buf = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(text));
  return Array.from(new Uint8Array(buf)).map(b => b.toString(16).padStart(2, '0')).join('');
}

/**
 * Delta-Aware Synchronization Pipeline
 *
 * How it works:
 * 1. For each playlist URL, fetch raw text and compute its SHA-256 hash.
 * 2. Compare hash against the previously stored hash in KV.
 *    - If unchanged → skip re-parsing (use cached parsed data from previous compile).
 *    - If changed or new → re-parse and mark playlist as "dirty".
 * 3. After compiling the full new channel set:
 *    - Identify NEW channels (not in previous KV compiled_channels by normalized key).
 *    - Identify REMOVED channels (in previous KV, not in new set).
 *    - For NEW channels: run stream URL validation (max 45 cap).
 *    - For UNCHANGED channels: carry over previous isBroken/validationReason from KV.
 * 4. Only write to KV if the channel set actually changed.
 * 5. Record lastChangeAt timestamp whenever a real change is detected.
 */
async function synchronizePlaylists(env) {
  console.log('Delta sync: checking playlist hashes for changes...');
  if (!env || !env.IPTV_KV) {
    console.warn("KV namespace 'IPTV_KV' not bound — cannot sync.");
    return;
  }

  try {
    // Load previous compiled data from KV to enable delta comparison
    let previousChannelMap = {}; // normalizedKey → { isBroken, validationReason, streams }
    let previousUpdatedAt = 0;
    const rawPrev = await env.IPTV_KV.get('compiled_channels');
    if (rawPrev) {
      try {
        const prev = JSON.parse(rawPrev);
        previousUpdatedAt = prev.updatedAt || 0;
        for (const ch of (prev.channels || [])) {
          const key = getNormalizedKey(ch.name);
          previousChannelMap[key] = ch;
        }
      } catch (_) {}
    }

    // Fetch all playlists, computing content hash per playlist
    const playlistHashResults = await Promise.all(
      Object.entries(M3U_PLAYLISTS).map(async ([categoryName, url]) => {
        try {
          // BYPASS CACHE during sync so we always get fresh content from GitHub/Gist
          const response = await smartFetchNoCache(url, env);
          if (!response.ok) throw new Error(`HTTP ${response.status}`);
          const text = await response.text();
          const hash = await sha256hex(text);

          const hashKey = `playlist_hash:${categoryName}`;
          const storedHash = await env.IPTV_KV.get(hashKey);
          const changed = storedHash !== hash;

          return { categoryName, url, text, hash, hashKey, changed,
                   parsed: parseM3uText(text) };
        } catch (e) {
          console.error(`Failed to fetch '${categoryName}':`, e.message);
          return { categoryName, url, text: '', hash: '', hashKey: '', changed: false, parsed: [] };
        }
      })
    );

    const anyPlaylistChanged = playlistHashResults.some(r => r.changed && r.parsed.length > 0);
    if (!anyPlaylistChanged) {
      console.log('Delta sync: no playlist content changed — skipping KV update.');
      return;
    }

    // Update hashes in KV for changed playlists
    await Promise.all(
      playlistHashResults
        .filter(r => r.changed && r.hash)
        .map(r => env.IPTV_KV.put(r.hashKey, r.hash))
    );

    // Compile new full channel set from all (fresh) playlist data
    const data = await compileMasterChannelsList(env, playlistHashResults, previousChannelMap);

    // Detect if the channel set actually changed vs what we had before
    const newKeys = new Set(data.channels.map(c => getNormalizedKey(c.name)));
    const prevKeys = new Set(Object.keys(previousChannelMap));
    const newCount = [...newKeys].filter(k => !prevKeys.has(k)).length;
    const removedCount = [...prevKeys].filter(k => !newKeys.has(k)).length;
    const channelSetChanged = newCount > 0 || removedCount > 0;

    const now = Date.now();
    const lastChangeAt = channelSetChanged ? now : (previousUpdatedAt || now);

    // Compute a short fingerprint of the channel set so clients can detect
    // changes without downloading the full payload (used by /api/changes and
    // the ?since= check in /api/channels).
    const channelFingerprint = await sha256hex(
      data.channels.map(c => `${getNormalizedKey(c.name)}:${c.streams.map(s => (s.isBroken ? '0' : '1')).join(',')}`).join('|')
    );

    await env.IPTV_KV.put('compiled_channels', JSON.stringify({
      ...data,
      lastChangeAt,
      fingerprint: channelFingerprint,
      newChannelCount: newCount,
      removedChannelCount: removedCount
    }));

    console.log(
      `Delta sync complete: ${data.channels.length} total channels,` +
      ` +${newCount} new, -${removedCount} removed.`
    );
  } catch (error) {
    console.error('Synchronization failed:', error.message);
  }
}

/**
 * Lightweight /api/changes handler.
 * Android app polls this on each refresh to check if anything changed
 * since its last sync timestamp — without downloading the full channel list.
 */
async function handleGetChanges(url, env, corsHeaders) {
  const since = parseInt(url.searchParams.get('since') || '0', 10);
  if (!env || !env.IPTV_KV) {
    return new Response(JSON.stringify({ changed: false, reason: 'KV not bound' }), {
      status: 200, headers: { 'Content-Type': 'application/json', ...corsHeaders }
    });
  }
  const raw = await env.IPTV_KV.get('compiled_channels');
  if (!raw) {
    return new Response(JSON.stringify({ changed: true, reason: 'no_data' }), {
      status: 200, headers: { 'Content-Type': 'application/json', ...corsHeaders }
    });
  }
  let meta = {};
  try { meta = JSON.parse(raw); } catch (_) {}

  const lastChangeAt = meta.lastChangeAt || meta.updatedAt || 0;
  const changed = lastChangeAt > since;
  return new Response(JSON.stringify({
    changed,
    lastChangeAt,
    updatedAt: meta.updatedAt || 0,
    newChannelCount: meta.newChannelCount || 0,
    removedChannelCount: meta.removedChannelCount || 0,
    totalChannels: (meta.channels || []).length
  }), {
    status: 200,
    headers: {
      'Content-Type': 'application/json',
      'Cache-Control': 'no-store',
      ...corsHeaders
    }
  });
}

/**
 * Compile the master channel list.
 * When called from synchronizePlaylists (delta path), receives pre-fetched
 * playlistHashResults and previousChannelMap to enable delta validation.
 * When called standalone (legacy path), fetches all playlists fresh.
 */
async function compileMasterChannelsList(env, playlistHashResults = null, previousChannelMap = {}) {
  const parsedPlaylists = {};

  if (playlistHashResults) {
    // Delta path: use pre-fetched data from synchronizePlaylists
    playlistHashResults.forEach(r => { parsedPlaylists[r.categoryName] = r.parsed; });
  } else {
    // Standalone path: fetch all playlists (bypass cache for freshness)
    const results = await Promise.all(
      Object.entries(M3U_PLAYLISTS).map(async ([categoryName, url]) => {
        try {
          const response = await smartFetchNoCache(url, env);
          if (!response.ok) throw new Error(`HTTP ${response.status}`);
          const text = await response.text();
          return { categoryName, parsed: parseM3uText(text) };
        } catch (e) {
          console.error(`Failed to fetch/parse '${categoryName}':`, e.message);
          return { categoryName, parsed: [] };
        }
      })
    );
    results.forEach(res => { parsedPlaylists[res.categoryName] = res.parsed; });
  }

  // Group, deduplicate, and classify
  const groupedMap = {};
  let catIdCounter = 1;
  const categoryIdsMap = {};

  for (const [playlistCategory, channelList] of Object.entries(parsedPlaylists)) {
    if (!categoryIdsMap[playlistCategory]) {
      categoryIdsMap[playlistCategory] = catIdCounter++;
    }

    for (const item of channelList) {
      const normalizedKey = getNormalizedKey(item.name);
      if (!normalizedKey) continue;

      if (!groupedMap[normalizedKey]) {
        // Full intelligent classification using v2 classifier
        const smartCategory = classifyChannelName(item.name, item.groupTitle, playlistCategory);
        if (!categoryIdsMap[smartCategory]) {
          categoryIdsMap[smartCategory] = catIdCounter++;
        }
        groupedMap[normalizedKey] = {
          name: sanitizeChannelName(item.name),
          logoUrl: item.logoUrl || getFallbackLogo(item.name),
          category: smartCategory,
          categoryId: categoryIdsMap[smartCategory],
          description: `Unified feed for ${sanitizeChannelName(item.name)}`,
          tvgId: item.tvgId || "",
          tvgName: item.tvgName || "",
          reputationScore: getChannelReputationScore(item.name),
          rawUrls: [],
          headerOptions: {}
        };
      }

      if (!groupedMap[normalizedKey].rawUrls.includes(item.streamUrl)) {
        groupedMap[normalizedKey].rawUrls.push(item.streamUrl);
      }
      if (!groupedMap[normalizedKey].logoUrl && item.logoUrl) {
        groupedMap[normalizedKey].logoUrl = item.logoUrl;
      }
      if (!groupedMap[normalizedKey].tvgId && item.tvgId) {
        groupedMap[normalizedKey].tvgId = item.tvgId;
      }
      // Merge VLC header options
      if (item.headerOptions && Object.keys(item.headerOptions).length > 0) {
        groupedMap[normalizedKey].headerOptions = {
          ...item.headerOptions,
          ...groupedMap[normalizedKey].headerOptions
        };
      }
    }
  }

  const channels = [];
  const activeCategories = new Set();

  let validationCount = 0;
  const maxValidations = 45; // Safe within Cloudflare's 50 subrequest cap

  for (const channel of Object.values(groupedMap)) {
    const normalizedKey = getNormalizedKey(channel.name);
    const prevChannel = previousChannelMap[normalizedKey]; // may be undefined for new channels
    const isNewChannel = !prevChannel;

    const streams = await Promise.all(channel.rawUrls.map(async (url, idx) => {
      let isBroken = false;
      let validationReason = 'not_checked';

      if (isNewChannel && validationCount < maxValidations) {
        // ── NEW channel: always validate its streams ──
        validationCount++;
        try {
          const result = await validateStreamUrl(url, env, 3000);
          isBroken = !result.working;
          validationReason = result.reason;
        } catch (e) {
          isBroken = false;
          validationReason = `exception: ${e.message}`;
        }
      } else if (!isNewChannel) {
        // ── EXISTING channel: carry over previous validation state ──
        // Find matching stream in previous data by URL
        const prevStream = (prevChannel.streams || []).find(s => s.url === url);
        if (prevStream) {
          isBroken = prevStream.isBroken || false;
          validationReason = prevStream.validationReason || 'carried_over';
        } else {
          // New stream URL added to existing channel — validate it
          if (validationCount < maxValidations) {
            validationCount++;
            try {
              const result = await validateStreamUrl(url, env, 3000);
              isBroken = !result.working;
              validationReason = result.reason;
            } catch (e) {
              isBroken = false;
              validationReason = `exception: ${e.message}`;
            }
          }
        }
      }

      return {
        url,
        name: `Source ${idx + 1}`,
        isBroken,
        validationReason,
        isNew: isNewChannel,
        format: detectVideoFormat(url),
        ...(Object.keys(channel.headerOptions).length > 0 ? { headers: channel.headerOptions } : {})
      };
    }));

    channels.push({
      name: channel.name,
      logoUrl: channel.logoUrl,
      category: channel.category,
      categoryId: channel.categoryId,
      description: channel.description,
      tvgId: channel.tvgId,
      tvgName: channel.tvgName,
      reputationScore: channel.reputationScore,
      streams
    });

    activeCategories.add(channel.category);
  }

  // Sort channels within each category by reputationScore descending
  channels.sort((a, b) => {
    if (a.category === b.category) {
      return (b.reputationScore || 0) - (a.reputationScore || 0);
    }
    return a.category.localeCompare(b.category);
  });

  const categories = Array.from(activeCategories).map(catName => ({
    id: categoryIdsMap[catName],
    name: catName
  })).sort((a, b) => a.name.localeCompare(b.name));

  return { updatedAt: Date.now(), categories, channels };
}

// ─── 5. M3U PARSER v2 (full port from M3uParserService.parseM3uReader()) ────

/**
 * Full M3U/M3U8 parser with:
 * - #EXTVLCOPT: header extraction (Referer, Origin, User-Agent)
 * - url-tvg / x-tvg-url extraction from #EXTM3U line
 * - Auto-injection of Referer/Origin/UA for Fawanews, Daddylive restricted streams
 * - Fallback name generation from URL path segment
 */
function parseM3uText(text) {
  const channels = [];
  const lines = text.split(/\r?\n/);

  let currentName = "";
  let currentLogo = "";
  let currentTvgId = "";
  let currentTvgName = "";
  let currentGroup = "General";
  let currentDescription = "";
  let currentOptions = {};

  for (const line of lines) {
    const trimmed = line.trim();
    if (!trimmed) continue;

    if (trimmed.startsWith("#EXTM3U")) {
      // Extract EPG/tvg URL
      const tvgUrlMatch = trimmed.match(/(?:url-tvg|x-tvg-url)=["']([^"']*)["']/i);
      if (tvgUrlMatch) console.log("EPG URL detected:", tvgUrlMatch[1]);
      continue;
    }

    if (trimmed.startsWith("#EXTINF:")) {
      currentOptions = {};
      const logoMatch = trimmed.match(/tvg-logo=["']([^"']*)["']/i);
      currentLogo = logoMatch ? logoMatch[1] : "";

      const idMatch = trimmed.match(/tvg-id=["']([^"']*)["']/i);
      currentTvgId = idMatch ? idMatch[1] : "";

      const nameMatch = trimmed.match(/tvg-name=["']([^"']*)["']/i);
      currentTvgName = nameMatch ? nameMatch[1] : "";

      const groupMatch = trimmed.match(/group-title=["']([^"']*)["']/i);
      const rawGroup = groupMatch ? groupMatch[1] : "General";
      currentGroup = rawGroup.split(";").find(p => p.trim() !== "")?.trim() || rawGroup.trim();

      const commaIndex = trimmed.lastIndexOf(",");
      if (commaIndex !== -1) {
        currentName = trimmed.substring(commaIndex + 1).trim();
      } else {
        currentName = currentTvgName || currentTvgId || "";
      }
      currentDescription = currentName ? `Live stream of ${currentName}` : "";
      continue;
    }

    // Parse #EXTVLCOPT: headers (port from M3uParserService lines 218–237)
    if (trimmed.startsWith("#EXTVLCOPT:")) {
      const optContent = trimmed.substring("#EXTVLCOPT:".length).trim();
      const eqIndex = optContent.indexOf("=");
      if (eqIndex !== -1) {
        const rawKey = optContent.substring(0, eqIndex).trim().toLowerCase();
        const rawValue = optContent.substring(eqIndex + 1).trim();
        let headerKey;
        if (rawKey === "http-referrer" || rawKey === "referrer" || rawKey === "referer") {
          headerKey = "Referer";
        } else if (rawKey === "http-origin" || rawKey === "origin") {
          headerKey = "Origin";
        } else if (rawKey === "http-user-agent" || rawKey === "user-agent" || rawKey === "http-useragent") {
          headerKey = "User-Agent";
        } else if (rawKey.startsWith("http-")) {
          headerKey = rawKey.substring(5).split("-").map(p => p.charAt(0).toUpperCase() + p.slice(1)).join("-");
        } else {
          headerKey = rawKey;
        }
        currentOptions[headerKey] = rawValue;
      }
      continue;
    }

    if (trimmed.startsWith("#")) continue;

    // Stream URL line
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://") ||
        trimmed.startsWith("rtmp://") || trimmed.startsWith("rtsp://")) {

      // Auto-inject required headers for restricted streams (port from validateAndEnforceHeaders)
      validateAndEnforceHeaders(trimmed, currentOptions);

      // Generate fallback name from URL path if #EXTINF lacked a name
      let finalName = currentName;
      if (!finalName) {
        try {
          const u = new URL(trimmed);
          const segments = u.pathname.split("/").filter(Boolean);
          const lastSeg = segments[segments.length - 1]?.split(".")[0] || "";
          if (lastSeg.length >= 3 && lastSeg.length <= 25) {
            finalName = lastSeg.replace(/[-_]/g, " ").replace(/\b\w/g, c => c.toUpperCase());
          } else {
            finalName = u.hostname.replace(/^www\./, "");
          }
        } catch {
          finalName = "Stream Channel";
        }
      }

      // Append VLC options as |key=encvalue to stream URL (matches Android format)
      let finalStreamUrl = trimmed;
      if (Object.keys(currentOptions).length > 0) {
        const optStr = Object.entries(currentOptions)
          .map(([k, v]) => `${k}=${encodeURIComponent(v)}`).join("&");
        finalStreamUrl = `${trimmed}|${optStr}`;
      }

      channels.push({
        name: finalName,
        streamUrl: finalStreamUrl,
        logoUrl: currentLogo || "https://images.unsplash.com/photo-1542038784456-1ea8e935640e?w=120&q=80",
        groupTitle: currentGroup || "General",
        tvgId: currentTvgId,
        tvgName: currentTvgName,
        description: currentDescription || `Live stream of ${finalName}`,
        headerOptions: { ...currentOptions }
      });

      // Reset state for next channel
      currentName = "";
      currentLogo = "";
      currentGroup = "General";
      currentDescription = "";
      currentTvgId = "";
      currentTvgName = "";
      currentOptions = {};
    }
  }

  return channels;
}

/**
 * Auto-inject Referer/Origin/User-Agent for known restricted stream domains.
 * Port of M3uParserService.validateAndEnforceHeaders()
 */
function validateAndEnforceHeaders(streamUrl, options) {
  const lower = streamUrl.toLowerCase();

  // Fawanews — requires exact Referer, Origin and modern browser UA
  if (lower.includes("fawanews") || lower.includes("193.47.62.")) {
    if (!options["Referer"]) options["Referer"] = "http://www.fawanews.sc/";
    if (!options["Origin"])  options["Origin"]  = "http://www.fawanews.sc/";
    if (!options["User-Agent"]) {
      options["User-Agent"] = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36 Edg/134.0.0.0";
    }
  }

  // Daddylive — requires exact Referer & Origin
  if (lower.includes("daddylive") || lower.includes("dlhd")) {
    if (!options["Referer"]) options["Referer"] = "https://daddylive.mp/";
    if (!options["Origin"])  options["Origin"]  = "https://daddylive.mp/";
  }

  // HLS/DASH streams — set modern browser UA to prevent 403 Forbidden
  const isHls = lower.includes("/hls/") || lower.includes(".m3u8") || lower.includes(".mpd");
  if (isHls && !options["User-Agent"]) {
    options["User-Agent"] = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
  }
}

/**
 * Full URL normalizer — adds Pastebin + gist.github.com + GitHub blob/raw support.
 * Port of LiveTvRepository.normalizeUrl() + M3uParserService.normalizeUrl()
 */
function normalizePlaylistUrl(url) {
  let u = url.trim();

  // Gist: gist.github.com → gist.githubusercontent.com + /raw
  if (u.includes("gist.github.com")) {
    if (!u.includes("gist.githubusercontent.com")) {
      u = u.replace("gist.github.com", "gist.githubusercontent.com");
    }
    if (!u.includes("/raw")) {
      u = u.endsWith("/") ? u.slice(0, -1) : u;
      u = u + "/raw";
    }
    return u;
  }

  // Pastebin: /xxxx → /raw/xxxx
  if (u.includes("pastebin.com") && !u.includes("/raw/")) {
    const lastSlash = u.lastIndexOf("/");
    if (lastSlash !== -1 && lastSlash < u.length - 1) {
      const code = u.substring(lastSlash + 1);
      if (code && !code.includes("raw")) {
        u = `https://pastebin.com/raw/${code}`;
      }
    }
    return u;
  }

  // GitHub blob → raw
  if (u.includes("github.com") && !u.includes("raw.githubusercontent.com")) {
    if (u.includes("/blob/")) {
      u = u.replace("github.com", "raw.githubusercontent.com").replace("/blob/", "/");
    } else if (u.includes("/raw/")) {
      u = u.replace("github.com", "raw.githubusercontent.com").replace("/raw/", "/");
    }
  }

  return u;
}

// ─── 6. FULL CHANNEL CLASSIFIER v2 (port of ChannelClassifier.kt) ───────────

// Category name constants (mirrors CategoryNames object in Kotlin)
const CAT = {
  NEWS:             "International News",
  SPORTS:           "Sports & Football",
  ENTERTAINMENT:    "Entertainment",
  DOCUMENTARY:      "Documentary",
  KIDS:             "Kids",
  MUSIC:            "Music",
  GENERAL:          "General",
  MOVIES:           "Movies",
  COMEDY:           "Comedy",
  RELIGIOUS:        "Religious",
  LIFESTYLE:        "Lifestyle",
  BANGLA_CHANNEL:   "Bangla Channel",
  BANGLA_NEWS:      "Bangla News",
  CRICKET:          "Cricket",
  ANIMATION:        "Animation",
  BUSINESS:         "Business",
  AUTO:             "Auto",
  LIVE_SPORTS:      "Live Sports Events",
  SPORTS_NETWORKS:  "Sports Networks",
  PREMIUM_MOVIES:   "Premium Movies & Drama",
  CLASSIC_TV:       "Classic & Retro TV",
  CRIME:            "Crime & Investigation",
  SCIENCE_HISTORY:  "Science & History",
  LIFESTYLE_CUISINE:"Lifestyle & Cuisine",
  US_LOCALS:        "US Local Networks",
  KIDS_ANIMATION:   "Kids & Animation"
};

// 50+ exact channel-to-category direct mappings (port of DIRECT_MAPPINGS in ChannelClassifier.kt)
const DIRECT_MAPPINGS = {
  // US Local Networks
  "ABC": CAT.US_LOCALS, "CBS": CAT.US_LOCALS, "NBC": CAT.US_LOCALS,
  "Fox": CAT.US_LOCALS, "CW": CAT.US_LOCALS, "Telemundo": CAT.US_LOCALS,

  // Premium Movies & Drama
  "HBO": CAT.PREMIUM_MOVIES, "HBO 2": CAT.PREMIUM_MOVIES, "HBO Comedy": CAT.PREMIUM_MOVIES,
  "HBO Zone": CAT.PREMIUM_MOVIES, "Cinemax": CAT.PREMIUM_MOVIES, "Starz": CAT.PREMIUM_MOVIES,
  "Starz Encore Classic": CAT.PREMIUM_MOVIES, "Showtime": CAT.PREMIUM_MOVIES,
  "Showtime Extreme": CAT.PREMIUM_MOVIES, "Lifetime Movie Network": CAT.PREMIUM_MOVIES,
  "Sony Movie Channel": CAT.PREMIUM_MOVIES, "Turner Classic Movies": CAT.PREMIUM_MOVIES,
  "FX Movie Channel": CAT.PREMIUM_MOVIES,

  // Sports Networks
  "ESPN": CAT.SPORTS_NETWORKS, "ESPN2": CAT.SPORTS_NETWORKS, "ESPN News": CAT.SPORTS_NETWORKS,
  "ESPN U": CAT.SPORTS_NETWORKS, "Fox Sports 1": CAT.SPORTS_NETWORKS,
  "Fox Sports 2": CAT.SPORTS_NETWORKS, "Golf Channel": CAT.SPORTS_NETWORKS,
  "MLB Network": CAT.SPORTS_NETWORKS, "NBA TV": CAT.SPORTS_NETWORKS,
  "NFL Network": CAT.SPORTS_NETWORKS, "NHL Network": CAT.SPORTS_NETWORKS,
  "Tennis Channel": CAT.SPORTS_NETWORKS, "Willow Cricket": CAT.SPORTS_NETWORKS,
  "ACC Network": CAT.SPORTS_NETWORKS, "Big Ten Network": CAT.SPORTS_NETWORKS,
  "CBS Sports Network": CAT.SPORTS_NETWORKS, "beIN Sports 1": CAT.SPORTS_NETWORKS,
  "Sky Sports Football": CAT.SPORTS_NETWORKS, "Sky Sports News": CAT.SPORTS_NETWORKS,
  "Sky Sports Premier League": CAT.SPORTS_NETWORKS, "TSN1": CAT.SPORTS_NETWORKS,
  "TSN2": CAT.SPORTS_NETWORKS, "YES Network": CAT.SPORTS_NETWORKS,
  "NESN": CAT.SPORTS_NETWORKS, "SEC Network": CAT.SPORTS_NETWORKS,

  // News
  "BBC World News": CAT.NEWS, "CBS News 24/7": CAT.NEWS, "CNBC": CAT.NEWS,
  "CNN": CAT.NEWS, "Fox Business": CAT.NEWS, "Fox News": CAT.NEWS,
  "HLN TV": CAT.NEWS, "MSNBC": CAT.NEWS, "Newsmax TV": CAT.NEWS,
  "NewsNation": CAT.NEWS, "The Weather Channel": CAT.NEWS,

  // Science & History
  "Discovery Channel": CAT.SCIENCE_HISTORY, "Discovery Science": CAT.SCIENCE_HISTORY,
  "History Channel": CAT.SCIENCE_HISTORY, "Smithsonian Channel": CAT.SCIENCE_HISTORY,
  "Nat Geo": CAT.SCIENCE_HISTORY, "Nat Geo Wild": CAT.SCIENCE_HISTORY,
  "Animal Planet": CAT.SCIENCE_HISTORY,

  // Kids & Animation
  "Boomerang": CAT.KIDS_ANIMATION, "Cartoon Network": CAT.KIDS_ANIMATION,
  "Discovery Family Channel": CAT.KIDS_ANIMATION, "Disney Channel": CAT.KIDS_ANIMATION,
  "Disney Jr": CAT.KIDS_ANIMATION, "Disney XD": CAT.KIDS_ANIMATION,
  "Nickelodeon": CAT.KIDS_ANIMATION, "Nick Jr": CAT.KIDS_ANIMATION,
  "Nicktoons": CAT.KIDS_ANIMATION, "PBS Kids": CAT.KIDS_ANIMATION,

  // Crime & Investigation
  "Crime & Investigation Network": CAT.CRIME, "Investigation Discovery": CAT.CRIME,
  "Oxygen": CAT.CRIME, "Court TV": CAT.CRIME,

  // Classic & Retro TV
  "Buzzr": CAT.CLASSIC_TV, "getTV": CAT.CLASSIC_TV, "Grit TV": CAT.CLASSIC_TV,
  "Comet TV": CAT.CLASSIC_TV, "Cozi TV": CAT.CLASSIC_TV, "Bounce TV": CAT.CLASSIC_TV,
  "TV Land": CAT.CLASSIC_TV, "INSP": CAT.CLASSIC_TV,

  // Lifestyle & Cuisine
  "Food Network": CAT.LIFESTYLE_CUISINE, "Cooking Channel": CAT.LIFESTYLE_CUISINE,
  "TLC": CAT.LIFESTYLE_CUISINE, "FYI TV": CAT.LIFESTYLE_CUISINE
};

// Stopwords with minimal semantic weight (mirrors ChannelClassifier.STOPWORDS)
const STOPWORDS = new Set([
  "hd","fhd","uhd","sd","4k","1080p","720p","576p","480p","360p","hevc","h264","h265",
  "raw","stream","tv","hq","usa","us","uk","ca","in","bd","vip","live","online","m3u8",
  "the","a","an","and","of","to","for","on","with","at","by","from","up","about"
]);

// Normalization regex (mirrors ChannelClassifier.normalizationRegex)
const NORM_REGEX = /(?:^|\s)(?:HD|FHD|UHD|SD|4K|1080P|720P|576P|480P|360P|HEVC|H264|H265|RAW|STREAM|TV|HQ|USA?|UK|CA|IN|BD|ES|FR|DE|IT|JP|VIP|LIVE|ONLINE|M3U8)\b|[|:.\-_+\\/\[\]()]+/gi;

// Weighted category centroid vectors (port of CATEGORY_CENTROIDS in ChannelClassifier.kt)
const CATEGORY_CENTROIDS = {
  [CAT.CRICKET]: {
    "willow":12.0,"cricket":10.0,"ipl":10.0,"bpl":10.0,"icc":10.0,"t20":10.0,
    "odi":8.0,"test":5.0,"ashes":8.0,"willow cricket":12.0,"sky cricket":10.0,
    "star sports cricket":10.0
  },
  [CAT.AUTO]: {
    "motor":8.0,"auto":8.0,"car":6.0,"nascar":10.0,"motogp":10.0,"speed":6.0,
    "turbo":6.0,"racing":7.0,"formula":9.0,"f1":10.0,"motor tv":10.0,"auto tv":10.0
  },
  [CAT.BANGLA_NEWS]: {
    "somoy":10.0,"jamuna":10.0,"ekattor":10.0,"dbc":10.0,"nagorik":8.0,"news24":10.0,
    "atn":5.0,"channel 24":10.0,"independent":9.0,"somoy tv":12.0,"jamuna tv":12.0,
    "ekattor tv":12.0,"dbc news":12.0,"atn news":10.0,"bangla news":10.0,"bd news":8.0
  },
  [CAT.NEWS]: {
    "cnn":10.0,"bbc":10.0,"al jazeera":10.0,"msnbc":10.0,"fox news":10.0,
    "euronews":10.0,"france":6.0,"dw":8.0,"sky news":10.0,"cctv":8.0,"ndtv":8.0,
    "reuters":10.0,"wion":10.0,"weather":8.0,"news":5.0,"breaking":8.0,
    "bbc news":12.0,"france 24":10.0,"dw news":10.0,"weather channel":10.0
  },
  [CAT.BUSINESS]: {
    "bloomberg":10.0,"cnbc":10.0,"business":8.0,"market":7.0,"finance":8.0,
    "stock":7.0,"money":6.0,"bloomberg tv":12.0,"fox business":12.0,"business news":10.0
  },
  [CAT.ANIMATION]: {
    "animax":10.0,"crunchyroll":10.0,"anime":10.0,"adult swim":10.0,"manga":8.0,
    "animation":8.0,"toon":6.0,"funimation":9.0,"adultswim":10.0
  },
  [CAT.KIDS]: {
    "disney":10.0,"nickelodeon":10.0,"nick":8.0,"cartoon":9.0,"boomerang":10.0,
    "baby":8.0,"babytv":10.0,"cbeebies":10.0,"duronto":10.0,"pogo":10.0,
    "hungama":10.0,"spacetoon":10.0,"kids":8.0,"junior":7.0,
    "cartoon network":12.0,"nick jr":10.0,"disney junior":10.0,"baby tv":10.0
  },
  [CAT.MOVIES]: {
    "hbo":10.0,"cinemax":10.0,"starz":10.0,"showtime":10.0,"amc":10.0,"mgm":9.0,
    "epix":9.0,"hallmark":10.0,"lifetime":9.0,"movies":8.0,"film":7.0,"cinema":8.0,
    "star movies":12.0,"zee cinema":12.0,"sky cinema":12.0,"hallmark movies":12.0,
    "hbo signature":11.0,"hbo family":11.0,"action movies":10.0
  },
  [CAT.SPORTS]: {
    "espn":10.0,"bein":10.0,"eurosport":10.0,"supersport":10.0,"golf":9.0,"tennis":9.0,
    "pga":8.0,"wwe":10.0,"ufc":10.0,"football":8.0,"soccer":8.0,"nfl":9.0,"nba":9.0,
    "mlb":9.0,"nhl":9.0,"sports":6.0,"sport":6.0,"bein sports":12.0,"sky sports":12.0,
    "fox sports":12.0,"ten sports":12.0,"premier league":12.0,"champions league":12.0
  },
  [CAT.MUSIC]: {
    "mtv":10.0,"vh1":10.0,"music":8.0,"hits":7.0,"classical":8.0,"jazz":8.0,
    "opera":8.0,"pop":6.0,"rock":6.0,"music tv":10.0,"mtv live":11.0,"vh1 classic":11.0
  },
  [CAT.DOCUMENTARY]: {
    "discovery":10.0,"national":7.0,"geographic":8.0,"nat geo":12.0,"history":9.0,
    "animal":8.0,"planet":8.0,"nasa":9.0,"smithsonian":10.0,"science":8.0,
    "documentary":8.0,"history channel":12.0,"animal planet":12.0,"nasa tv":12.0
  },
  [CAT.COMEDY]: {
    "comedy":9.0,"central":6.0,"laughter":8.0,"humor":8.0,"funny":7.0,
    "comedy central":12.0,"comedy tv":11.0
  },
  [CAT.RELIGIOUS]: {
    "islam":9.0,"quran":10.0,"peace":8.0,"makkah":10.0,"madinah":10.0,"bible":9.0,
    "christian":8.0,"gospel":8.0,"religion":7.0,"church":7.0,"god tv":10.0,
    "peace tv":11.0,"makkah live":12.0,"quran tv":12.0
  },
  [CAT.LIFESTYLE]: {
    "lifestyle":9.0,"fashion":9.0,"food":9.0,"kitchen":8.0,"travel":9.0,"hgtv":10.0,
    "tlc":10.0,"cooking":8.0,"gourmet":8.0,"fashion tv":12.0,"food network":12.0,
    "travel channel":12.0
  },
  [CAT.BANGLA_CHANNEL]: {
    "ntv":10.0,"gazi":8.0,"gtv":9.0,"atn":8.0,"dipto":10.0,"asian":8.0,"desh":8.0,
    "ekushey":10.0,"sa":8.0,"bijoy":8.0,"maasranga":10.0,"boishakhi":10.0,
    "bangla":7.0,"bdix":8.0,"dhaka":8.0,"bengali":7.0,"ntv bd":12.0,"channel i":12.0,
    "atn bangla":12.0,"dipto tv":12.0,"gazi tv":12.0,"maasranga tv":12.0
  }
};

/**
 * Full channel classifier — port of ChannelClassifier.classify()
 * Priority: direct lookup → live sports → keyphrase → cosine similarity → group prior → source
 */
function classifyChannelName(name, groupTitle, playlistSource) {
  const cleanName = (name || "").trim();
  const lowerName = cleanName.toLowerCase();

  // 1. Direct exact lookup
  if (DIRECT_MAPPINGS[cleanName]) return DIRECT_MAPPINGS[cleanName];
  const ciMatch = Object.entries(DIRECT_MAPPINGS).find(([k]) => k.toLowerCase() === lowerName);
  if (ciMatch) return ciMatch[1];

  // Explicit live events group check
  const explicitGroup = (groupTitle || "").toLowerCase().trim();
  if (explicitGroup === "live events" || explicitGroup === "live event" ||
      explicitGroup.includes("live event")) return "Live Events";

  // 2. Live sports pattern detection
  if (cleanName.startsWith("[") || lowerName.includes(" vs ") || lowerName.includes(" at ") ||
      lowerName.includes("world cup") || lowerName.includes("copa del mundo") ||
      lowerName.includes("fifa") || lowerName.includes("fútbol") ||
      lowerName.includes("basketball") || lowerName.includes("strmcntr") ||
      lowerName.includes("strmxhd") || lowerName.includes("xyzstrm") ||
      lowerName.includes("strmhub") || lowerName.includes("watchfty") ||
      lowerName.includes("fawa") || lowerName.includes("fighting") ||
      lowerName.includes("ufc") || lowerName.includes("wwe")) {
    return CAT.LIVE_SPORTS;
  }

  // 3. Keyphrase matching (mirrors large when{} block in ChannelClassifier.kt)
  if (lowerName.includes("cricket")||lowerName.includes("willow")||lowerName.includes("t20")||lowerName.includes("bpl")||lowerName.includes("ten cricket")) return CAT.CRICKET;
  if (lowerName.includes("somoy")||lowerName.includes("jamuna")||lowerName.includes("ekattor")||lowerName.includes("dbc news")||lowerName.includes("bangla news")||lowerName.includes("bd news")||lowerName.includes("news24 hd")||lowerName.includes("abp ananda")) return CAT.BANGLA_NEWS;
  if (lowerName.includes("bangla")||lowerName.includes("bengali")||lowerName.includes("bdix")||lowerName.includes("dhaka")||lowerName.includes("ntv")||lowerName.includes("gtv")||lowerName.includes("atn")||lowerName.includes("gazi")||lowerName.includes("maasranga")||lowerName.includes("nagorik")||lowerName.includes("rtv")||lowerName.includes("sa tv")||lowerName.includes("satv")||lowerName.includes("channel i")||lowerName.includes("deepto")||lowerName.includes("dipto")||lowerName.includes("ekushey")||lowerName.includes("channel s")) return CAT.BANGLA_CHANNEL;
  if (lowerName.includes("disney")||lowerName.includes("nick")||lowerName.includes("cartoon")||lowerName.includes("boomerang")||lowerName.includes("kids")||lowerName.includes("baby")||lowerName.includes("toon")||lowerName.includes("cbeebies")||lowerName.includes("duronto")||lowerName.includes("pbs kids")||lowerName.includes("pogo")) return CAT.KIDS;
  if (lowerName.includes("animation")||lowerName.includes("anime")||lowerName.includes("animax")) return CAT.ANIMATION;
  if (lowerName.includes("auto")||lowerName.includes("motor")||lowerName.includes("nascar")||lowerName.includes("motogp")||lowerName.includes("formula 1")||lowerName.includes(" f1 ")||lowerName.endsWith(" f1")||lowerName.includes("racing")) return CAT.AUTO;
  if (lowerName.includes("business")||lowerName.includes("finance")||lowerName.includes("bloomberg")||lowerName.includes("stock market")) return CAT.BUSINESS;
  if (lowerName.includes("comedy")||lowerName.includes("humor")||lowerName.includes("laughter")) return CAT.COMEDY;
  if (lowerName.includes("discovery")||lowerName.includes("science")||lowerName.includes("history")||lowerName.includes("smithsonian")||lowerName.includes("nat geo")||lowerName.includes("animal planet")||lowerName.includes("bbc earth")||lowerName.includes("nasa")) return CAT.DOCUMENTARY;
  if (lowerName.includes("entertainment")||lowerName.includes("showtime")||lowerName.includes("realities")) return CAT.ENTERTAINMENT;
  if (lowerName.includes("news")||lowerName.includes("msnbc")||lowerName.includes("cnn")||lowerName.includes("cnbc")||lowerName.includes("bloomberg")||lowerName.includes("weather")||lowerName.includes("al jazeera")||lowerName.includes("france 24")||lowerName.includes("sky news")||lowerName.includes("euronews")||lowerName.includes("dw")||lowerName.includes("ndtv")||lowerName.includes("wion")) return CAT.NEWS;
  if (lowerName.includes("lifestyle")||lowerName.includes("travel")||lowerName.includes("hgtv")||lowerName.includes("tlc")||lowerName.includes("cuisine")||lowerName.includes("fashion")||lowerName.includes("food network")||lowerName.includes("cooking channel")) return CAT.LIFESTYLE;
  if (lowerName.includes("movie")||lowerName.includes("cinema")||lowerName.includes("film")||lowerName.includes("hallmark")||lowerName.includes("lifetime")||lowerName.includes("hbo")||lowerName.includes("cinemax")||lowerName.includes("starz")||lowerName.includes("amc")) return CAT.MOVIES;
  if (lowerName.includes("music")||lowerName.includes("mtv")||lowerName.includes("vh1")||lowerName.includes("song")||lowerName.includes("b4u music")||lowerName.includes("radio")) return CAT.MUSIC;
  if (lowerName.includes("religious")||lowerName.includes("islam")||lowerName.includes("bible")||lowerName.includes("prayer")||lowerName.includes("gospel")||lowerName.includes("quran")||lowerName.includes("makkah")||lowerName.includes("peace tv")||lowerName.includes("god tv")) return CAT.RELIGIOUS;
  if (lowerName.includes("sports")||lowerName.includes("sport")||lowerName.includes("football")||lowerName.includes("soccer")||lowerName.includes("golf")||lowerName.includes("tennis")||lowerName.includes("nba")||lowerName.includes("nfl")||lowerName.includes("nhl")||lowerName.includes("mlb")||lowerName.includes("espn")||lowerName.includes("bein")||lowerName.includes("sky sport")||lowerName.includes("premier league")) return CAT.SPORTS;

  // 4. Cosine similarity fallback
  const queryVector = buildQueryVector(cleanName, groupTitle || "");
  let topCategory = null;
  let topScore = 0;
  for (const [cat, centroid] of Object.entries(CATEGORY_CENTROIDS)) {
    const score = calculateCosineSimilarity(queryVector, centroid);
    if (score > topScore) { topScore = score; topCategory = cat; }
  }

  // Group prior boosting
  const groupPrior = getGroupPrior(explicitGroup);
  if (groupPrior) {
    const boosted = (topScore || 0) + 0.35;
    if (!topCategory || boosted > topScore) return groupPrior;
  }

  if (topCategory && topScore >= 0.15) return topCategory;

  // 5. Source fallback
  const sourceCat = mapSourceToCategory(playlistSource || "");
  if (sourceCat) return sourceCat;

  // 6. Group title text fallback
  if (groupTitle && groupTitle.trim() && groupTitle !== "TV" && groupTitle !== "General") {
    return groupTitle.replace(/_/g, " ").replace(/\b\w/g, c => c.toUpperCase());
  }

  return CAT.GENERAL;
}

function buildQueryVector(channelName, groupTitle) {
  const vector = {};
  const cleanName = channelName.toLowerCase().replace(NORM_REGEX, " ").trim();
  const nameTokens = cleanName.split(/\s+/).filter(t => t.length > 1 && !STOPWORDS.has(t));

  nameTokens.forEach(t => { vector[t] = (vector[t] || 0) + 1.0; });
  for (let i = 0; i < nameTokens.length - 1; i++) {
    const bigram = `${nameTokens[i]} ${nameTokens[i+1]}`;
    vector[bigram] = (vector[bigram] || 0) + 2.0;
  }

  const cleanGroup = groupTitle.toLowerCase().replace(NORM_REGEX, " ").trim();
  const groupTokens = cleanGroup.split(/\s+/).filter(t => t.length > 1 && !STOPWORDS.has(t));
  groupTokens.forEach(t => { vector[t] = (vector[t] || 0) + 0.4; });
  for (let i = 0; i < groupTokens.length - 1; i++) {
    const bigram = `${groupTokens[i]} ${groupTokens[i+1]}`;
    vector[bigram] = (vector[bigram] || 0) + 0.8;
  }

  return vector;
}

function calculateCosineSimilarity(vectorA, vectorB) {
  let dot = 0, normA = 0, normB = 0;
  for (const [term, wA] of Object.entries(vectorA)) {
    normA += wA * wA;
    if (vectorB[term]) dot += wA * vectorB[term];
  }
  for (const wB of Object.values(vectorB)) normB += wB * wB;
  if (normA === 0 || normB === 0) return 0;
  return dot / (Math.sqrt(normA) * Math.sqrt(normB));
}

function getGroupPrior(cleanGroup) {
  if (cleanGroup.includes("cricket")) return CAT.CRICKET;
  if (cleanGroup.includes("sport")||cleanGroup.includes("football")||cleanGroup.includes("soccer")||cleanGroup.includes("wwe")||cleanGroup.includes("ufc")) return CAT.SPORTS;
  if (cleanGroup.includes("somoy")||cleanGroup.includes("bangla news")||cleanGroup.includes("bd news")) return CAT.BANGLA_NEWS;
  if (cleanGroup.includes("news")||cleanGroup.includes("info")||cleanGroup.includes("weather")) return CAT.NEWS;
  if (cleanGroup.includes("business")||cleanGroup.includes("finance")||cleanGroup.includes("stock")) return CAT.BUSINESS;
  if (cleanGroup.includes("animation")||cleanGroup.includes("anime")) return CAT.ANIMATION;
  if (cleanGroup.includes("kid")||cleanGroup.includes("child")||cleanGroup.includes("cartoon")||cleanGroup.includes("disney")||cleanGroup.includes("nick")) return CAT.KIDS;
  if (cleanGroup.includes("movie")||cleanGroup.includes("cinema")||cleanGroup.includes("film")||cleanGroup.includes("hbo")) return CAT.MOVIES;
  if (cleanGroup.includes("music")||cleanGroup.includes("song")||cleanGroup.includes("mtv")) return CAT.MUSIC;
  if (cleanGroup.includes("doc")||cleanGroup.includes("science")||cleanGroup.includes("history")||cleanGroup.includes("discovery")) return CAT.DOCUMENTARY;
  if (cleanGroup.includes("comedy")||cleanGroup.includes("humor")) return CAT.COMEDY;
  if (cleanGroup.includes("religious")||cleanGroup.includes("islam")||cleanGroup.includes("bible")||cleanGroup.includes("prayer")||cleanGroup.includes("gospel")) return CAT.RELIGIOUS;
  if (cleanGroup.includes("motor")||cleanGroup.includes("auto")) return CAT.AUTO;
  if (cleanGroup.includes("lifestyle")||cleanGroup.includes("fashion")||cleanGroup.includes("food")||cleanGroup.includes("cooking")||cleanGroup.includes("travel")) return CAT.LIFESTYLE;
  if (cleanGroup.includes("bangla")||cleanGroup.includes("bdix")||cleanGroup.includes("dhaka")||cleanGroup.includes("bengali")) return CAT.BANGLA_CHANNEL;
  if (cleanGroup.includes("entertainment")||cleanGroup.includes("drama")||cleanGroup.includes("series")) return CAT.ENTERTAINMENT;
  return null;
}

function mapSourceToCategory(source) {
  const s = source.toLowerCase();
  if (s === "bdix iptv") return CAT.BANGLA_CHANNEL;
  if (s === "animation") return CAT.ANIMATION;
  if (s === "auto") return CAT.AUTO;
  if (s === "bangla channel") return CAT.BANGLA_CHANNEL;
  if (s === "bangla news") return CAT.BANGLA_NEWS;
  if (s === "business") return CAT.BUSINESS;
  if (s === "comedy") return CAT.COMEDY;
  if (s === "cricket") return CAT.CRICKET;
  if (s === "documentary") return CAT.DOCUMENTARY;
  if (s === "entertainment") return CAT.ENTERTAINMENT;
  if (s === "international news") return CAT.NEWS;
  if (s === "kids") return CAT.KIDS;
  if (s === "lifestyle") return CAT.LIFESTYLE;
  if (s === "movies") return CAT.MOVIES;
  if (s === "music") return CAT.MUSIC;
  if (s === "religious") return CAT.RELIGIOUS;
  if (s === "sports & football") return CAT.SPORTS;
  if (s === "doms9 base" || s === "doms9 us tv") return null; // Use classifier for these
  return null;
}

// ─── 7. CHANNEL NAME NORMALIZER (with normalize cache) ──────────────────────

const _sanitizeCache = new Map();

const RE_BRACKETS    = /\s*\[.*?\]\s*/g;
const RE_PARENS      = /\s*\(.*?\)\s*/g;
const RE_COUNTRY     = /^(?:[a-z]{2,3}|us-es|us-en)\s*[:|•·\-]\s*/i;
const RE_QUALITY     = /\b(hd|sd|fhd|uhd|2k|4k|8k|1080p|720p|576p|480p|1080i|720i|50fps|60fps|hevc|h264|h265|h\.264|x264|mpeg\d*|ac3|dd5\.1|stereo)\b/gi;
const RE_SERVER      = /\b(server\s*\d+|source\s*\d+|source\s*[a-z]|backup\s*\d*|alt\s*\d*|src\s*\d+|ch\s*\d+|v\.\d+|v\d+|ver\s*\d+)\b/gi;
const RE_PUNCTUATION = /(^[\s:|•·\/\\+\-]+|[\s:|•·\/\\+\-]+$)/g;
const RE_SPACES      = /\s+/g;

function sanitizeChannelName(name) {
  if (!name) return "";
  const cached = _sanitizeCache.get(name);
  if (cached !== undefined) return cached;
  let c = name;
  c = c.replace(RE_BRACKETS, " ");
  c = c.replace(RE_PARENS, " ");
  c = c.replace(RE_COUNTRY, " ");
  c = c.replace(RE_QUALITY, " ");
  c = c.replace(RE_SERVER, " ");
  c = c.replace(RE_PUNCTUATION, "");
  c = c.replace(RE_SPACES, " ");
  const result = c.trim();
  _sanitizeCache.set(name, result);
  return result;
}

function getNormalizedKey(name) {
  return sanitizeChannelName(name).toLowerCase();
}

// ─── 8. REPUTATION SCORER (port of ChannelClassifier.getChannelReputationScore()) ─

function getChannelReputationScore(name) {
  const lower = (name || "").toLowerCase();
  // News
  if (lower.includes("bbc world")||lower.includes("bbc news")) return 100;
  if (lower.includes("cnn")) return 100;
  if (lower.includes("msnbc")) return 95;
  if (lower.includes("al jazeera")||lower.includes("aljazeera")) return 95;
  if (lower.includes("sky news")) return 90;
  if (lower.includes("fox news")) return 90;
  if (lower.includes("cnbc")) return 85;
  if (lower.includes("bloomberg")) return 85;
  if (lower.includes("france 24")) return 80;
  if (lower.includes("dw ") || lower.endsWith("dw")) return 80;
  // Sports
  if (lower.includes("espn")) return 100;
  if (lower.includes("sky sports")) return 100;
  if (lower.includes("willow")) return 95;
  if (lower.includes("bein sports")||lower.includes("beinsports")) return 95;
  if (lower.includes("dazn")) return 90;
  if (lower.includes("t sports")||lower.includes("tsports")) return 90;
  if (lower.includes("sony sports")||lower.includes("sony ten")) return 85;
  if (lower.includes("fox sports")||lower.includes("fs1")) return 85;
  if (lower.includes("eurosport")) return 80;
  // Movies & Premium
  if (lower.includes("hbo")) return 100;
  if (lower.includes("cinemax")) return 95;
  if (lower.includes("starz")) return 90;
  if (lower.includes("showtime")) return 90;
  if (lower.includes("sony pix")||lower.includes("sony max")) return 85;
  if (lower.includes("star gold")) return 85;
  if (lower.includes("zee cinema")) return 80;
  // US Networks
  if (lower.includes("abc")||lower.includes("cbs")||lower.includes("nbc")) return 80;
  // Documentary
  if (lower.includes("discovery channel")||lower.includes("discovery hd")) return 100;
  if (lower.includes("national geographic")||lower.includes("nat geo")) return 100;
  if (lower.includes("history channel")||lower.includes("history hd")) return 95;
  if (lower.includes("animal planet")) return 90;
  if (lower.includes("bbc earth")) return 90;
  if (lower.includes("smithsonian")) return 85;
  // Kids
  if (lower.includes("disney channel")||lower.includes("disney junior")||lower.includes("disney jr")) return 100;
  if (lower.includes("nickelodeon")||lower.includes("nick jr")) return 100;
  if (lower.includes("cartoon network")) return 95;
  if (lower.includes("boomerang")) return 90;
  if (lower.includes("duronto")) return 85;
  if (lower.includes("pbs kids")) return 85;
  // Bangla
  if (lower.includes("somoy")) return 100;
  if (lower.includes("jamuna")) return 95;
  if (lower.includes("ekattor")) return 90;
  if (lower.includes("ntv")) return 95;
  if (lower.includes("rtv")) return 90;
  if (lower.includes("channel i")) return 90;
  if (lower.includes("atn bangla")) return 85;
  if (lower.includes("maasranga")||lower.includes("massranga")) return 85;
  // Music
  if (lower.includes("mtv")) return 100;
  if (lower.includes("vh1")) return 95;
  // Religious
  if (lower.includes("peace tv")) return 100;
  if (lower.includes("al quran")||lower.includes("makkah")) return 100;
  return 0;
}

// ─── 9. AES-128/CBC ENCRYPTION (Web Crypto API — matches Android StreamDecryptionUtility) ─

/**
 * Same IV as StreamDecryptionUtility.kt line 27–32:
 * { 0xA5, 0x5A, 0x12, 0x34, 0x56, 0x78, 0x90, 0xAB, 0xCD, 0xEF, 0xFE, 0xDC, 0xBA, 0x09, 0x87, 0x65 }
 */
const AES_IV = new Uint8Array([0xA5,0x5A,0x12,0x34,0x56,0x78,0x90,0xAB,0xCD,0xEF,0xFE,0xDC,0xBA,0x09,0x87,0x65]);

function getAesKeyBytes(env) {
  const keyStr = (env && env.STREAM_DECRYPTION_KEY) || "Secr3tM3u8Str3am";
  const raw = new TextEncoder().encode(keyStr);
  const padded = new Uint8Array(16); // zero-padded 16 bytes — matches Android getKeyBytes()
  padded.set(raw.slice(0, Math.min(16, raw.length)));
  return padded;
}

async function encryptUrl(url, env) {
  if (!url || url.startsWith("encrypted://")) return url;
  try {
    const keyBytes = getAesKeyBytes(env);
    const cryptoKey = await crypto.subtle.importKey("raw", keyBytes, { name: "AES-CBC" }, false, ["encrypt"]);
    const encoded = new TextEncoder().encode(url);
    const encryptedBuffer = await crypto.subtle.encrypt({ name: "AES-CBC", iv: AES_IV }, cryptoKey, encoded);
    // URL-safe base64 — matches Android Base64.NO_WRAP | Base64.URL_SAFE
    const b64 = btoa(String.fromCharCode(...new Uint8Array(encryptedBuffer)))
      .replace(/\+/g, "-").replace(/\//g, "_");
    return `encrypted://${b64}`;
  } catch (e) {
    console.error("AES encrypt error:", e.message);
    return url; // Return plaintext as fallback
  }
}

async function decryptUrl(token, env) {
  if (!token) return token;
  // Support both new "encrypted://" and old XOR base64 tokens
  if (!token.startsWith("encrypted://")) {
    // Try legacy XOR deobfuscation for backward compatibility
    return legacyDeobfuscate(token);
  }
  
  const b64 = token.replace("encrypted://", "").replace(/-/g, "+").replace(/_/g, "/");
  let bytes;
  try {
    const binaryStr = atob(b64);
    bytes = new Uint8Array(binaryStr.length);
    for (let i = 0; i < binaryStr.length; i++) bytes[i] = binaryStr.charCodeAt(i);
  } catch (e) {
    console.error("Base64 decode error:", e.message);
    return token;
  }

  const candidateKeys = [];
  const activeKeyStr = (env && env.STREAM_DECRYPTION_KEY) || "";
  if (activeKeyStr) {
    candidateKeys.push(activeKeyStr);
  }
  if (!candidateKeys.includes("Secr3tM3u8Str3am")) {
    candidateKeys.push("Secr3tM3u8Str3am");
  }
  if (!candidateKeys.includes("ecr3tM3u8Str3a")) {
    candidateKeys.push("ecr3tM3u8Str3a");
  }

  for (const keyStr of candidateKeys) {
    try {
      const raw = new TextEncoder().encode(keyStr);
      const keyBytes = new Uint8Array(16); // zero-padded 16 bytes
      keyBytes.set(raw.slice(0, Math.min(16, raw.length)));
      
      const cryptoKey = await crypto.subtle.importKey("raw", keyBytes, { name: "AES-CBC" }, false, ["decrypt"]);
      const decrypted = await crypto.subtle.decrypt({ name: "AES-CBC", iv: AES_IV }, cryptoKey, bytes);
      const decryptedStr = new TextDecoder().decode(decrypted);
      if (decryptedStr.startsWith("http://") || 
          decryptedStr.startsWith("https://") || 
          decryptedStr.startsWith("rtmp") || 
          decryptedStr.startsWith("rtsp") ||
          decryptedStr.includes("|")
      ) {
        return decryptedStr;
      }
    } catch (e) {
      // Try next key
    }
  }

  console.error("All AES decrypt attempts failed for token:", token);
  return token;
}

// Legacy XOR deobfuscation kept for backward compatibility with old clients
const OBFUSCATION_KEY = 42;
function legacyDeobfuscate(str) {
  try {
    const hex = atob(str);
    let result = "";
    for (let i = 0; i < hex.length; i += 2) {
      result += String.fromCharCode(parseInt(hex.substring(i, i + 2), 16) ^ OBFUSCATION_KEY);
    }
    return result;
  } catch { return str; }
}

// ─── 10. GITHUB / URL FETCHER ────────────────────────────────────────────────

function parseGithubUrl(url) {
  try {
    let path = url.replace(/^(https?:\/\/)?(www\.)?(github\.com|raw\.githubusercontent\.com)\//, "");
    const segments = path.split("/");
    if (segments.length < 3) return null;
    const owner = segments[0];
    const repo = segments[1];
    let remaining = segments.slice(2);
    if (remaining[0] === "raw" || remaining[0] === "blob") remaining = remaining.slice(1);
    let branch, filePath;
    if (remaining[0] === "refs" && remaining[1] === "heads" && remaining.length > 2) {
      branch = `refs/heads/${remaining[2]}`;
      filePath = remaining.slice(3).join("/");
    } else if (remaining[0] === "refs" && remaining[1] === "tags" && remaining.length > 2) {
      branch = `refs/tags/${remaining[2]}`;
      filePath = remaining.slice(3).join("/");
    } else {
      branch = remaining[0];
      filePath = remaining.slice(1).join("/");
    }
    filePath = filePath.split("?")[0];
    if (!owner || !repo || !branch || !filePath) return null;
    return { owner, repo, branch, filePath };
  } catch (e) {
    console.error("Error parsing GitHub URL:", url, e.message);
    return null;
  }
}

/**
 * smartFetch — used for general requests (EPG, live events).
 * Uses Cloudflare CDN cache (TTL 3600s) for performance.
 */
async function smartFetch(url, env) {
  return _doFetch(url, env, true);
}

/**
 * smartFetchNoCache — used during synchronizePlaylists().
 * BYPASSES Cloudflare CDN cache entirely so GitHub Gist / raw file updates
 * are always reflected immediately without waiting for CDN TTL to expire.
 * Uses GitHub API (with token) for Gist/private repo support.
 */
async function smartFetchNoCache(url, env) {
  return _doFetch(url, env, false);
}

async function _doFetch(url, env, useCache) {
  // Normalize URL first (Pastebin, Gist, GitHub blob)
  const normalizedUrl = normalizePlaylistUrl(url);

  const headers = { 'User-Agent': 'IPTV-Sync-Engine-v2', 'Accept-Encoding': 'gzip' };
  const githubToken = env && (env.GITHUB_TOKEN || env.GITHUB_PAT);
  const isGithub = normalizedUrl.includes('githubusercontent.com') || normalizedUrl.includes('github.com');
  const parsedGit = isGithub ? parseGithubUrl(normalizedUrl) : null;

  // GitHub API path — always returns fresh content, bypasses CDN
  if (githubToken && parsedGit) {
    try {
      const { owner, repo, branch, filePath } = parsedGit;
      // Add cache-bust timestamp to force GitHub to return latest commit
      const apiUrl = `https://api.github.com/repos/${owner}/${repo}/contents/${filePath}?ref=${branch}`;
      const apiRes = await fetch(apiUrl, {
        headers: {
          'User-Agent': 'IPTV-Sync-Engine-v2',
          'Authorization': `token ${githubToken}`,
          'Accept': 'application/vnd.github.v3.raw',
          'Cache-Control': 'no-cache'   // Force GitHub API to bypass its own cache
        }
      });
      if (apiRes.ok) return apiRes;
    } catch (err) {
      console.error('Private fetch failed, falling back:', err.message);
    }
  }

  let targetUrl = normalizedUrl;
  if (parsedGit) {
    // Append timestamp as cache-bust for raw.githubusercontent.com
    const cacheBust = useCache ? '' : `?_ts=${Date.now()}`;
    targetUrl = `https://raw.githubusercontent.com/${parsedGit.owner}/${parsedGit.repo}/${parsedGit.branch}/${parsedGit.filePath}${cacheBust}`;
  }

  // Gist URLs: always add cache-bust when not using cache
  if (!useCache && targetUrl.includes('gist.githubusercontent.com') && !targetUrl.includes('?')) {
    targetUrl += `?_ts=${Date.now()}`;
  }

  const fetchOptions = { headers };
  if (useCache) {
    fetchOptions.cf = { cacheTtl: 3600, cacheEverything: true };
  } else {
    // Explicitly bypass Cloudflare CDN cache
    fetchOptions.cf = { cacheTtl: 0, cacheEverything: false, bypassCache: true };
    headers['Cache-Control'] = 'no-cache, no-store';
    headers['Pragma'] = 'no-cache';
  }

  return fetch(targetUrl, fetchOptions);
}

// ─── 11. LIVE EVENTS HELPERS ─────────────────────────────────────────────────

function parseRawChannelToFeed(channel) {
  const rawName = (channel.name || "").trim();
  let sportCategory = "Live Event";
  let textAfterCategory = rawName;

  if (rawName.startsWith("[")) {
    const end = rawName.indexOf("]");
    if (end !== -1) {
      sportCategory = rawName.substring(1, end).trim();
      textAfterCategory = rawName.substring(end + 1).trim();
    }
  }

  let cleanTitle = textAfterCategory;
  let provider = "Default Feed";
  let language = "English";

  if (textAfterCategory.includes("|")) {
    const parts = textAfterCategory.split("|", 2);
    cleanTitle = parts[0].trim();
    const feedInfo = parts[1].trim();
    if (feedInfo.includes("(") && feedInfo.endsWith(")")) {
      const pStart = feedInfo.lastIndexOf("(");
      const rest = feedInfo.substring(0, pStart).trim();
      provider = feedInfo.substring(pStart + 1, feedInfo.length - 1).trim();
      language = rest.toLowerCase().includes("spanish") ? "Spanish"
               : rest.toLowerCase().includes("arabic") ? "Arabic"
               : rest.toLowerCase().includes("french") ? "French"
               : rest || "Default";
    } else {
      provider = feedInfo;
    }
  }

  return {
    title: cleanTitle.trim(), sportCategory,
    logoUrl: channel.logoUrl || getPlaceholderLogoForSport(sportCategory),
    feed: { rawName, provider, language, streamUrl: channel.streamUrl, logoUrl: channel.logoUrl }
  };
}

function mapParsedChannelsToGroupedEvents(parsedChannels) {
  const rawEvents = parsedChannels.map(parseRawChannelToFeed);
  const groupedMap = {};
  for (const item of rawEvents) {
    const key = `${item.title.toLowerCase().trim()}|${item.sportCategory.toLowerCase().trim()}`;
    if (!groupedMap[key]) {
      groupedMap[key] = { title: item.title, sportCategory: item.sportCategory, logoUrl: item.logoUrl, feeds: [] };
    }
    groupedMap[key].feeds.push(item.feed);
  }
  return Object.entries(groupedMap).map(([k, g]) => {
    let hash = 0;
    for (let i = 0; i < k.length; i++) { hash = (hash << 5) - hash + k.charCodeAt(i); hash |= 0; }
    return { id: Math.abs(hash).toString(), title: g.title, sportCategory: g.sportCategory, logoUrl: g.logoUrl, feeds: g.feeds };
  }).sort((a, b) => a.sportCategory.localeCompare(b.sportCategory));
}

function getPlaceholderLogoForSport(category) {
  const c = category.toLowerCase();
  if (c.includes("foot")||c.includes("soccer")||c.includes("copa")||c.includes("fifa")||c.includes("fútbol")) return "https://images.unsplash.com/photo-1508098682722-e99c43a406b2?w=300&q=80";
  if (c.includes("basket")||c.includes("nba")) return "https://images.unsplash.com/photo-1546519638-68e109498ffc?w=300&q=80";
  if (c.includes("tennis")||c.includes("wimbledon")) return "https://images.unsplash.com/photo-1595435934249-5df7ed86e1c0?w=300&q=80";
  return "https://images.unsplash.com/photo-1461896836934-ffe607ba8211?w=300&q=80";
}

// ─── 12. SHARED HELPERS ─────────────────────────────────────────────────────

function detectVideoFormat(urlStr) {
  const url = (urlStr || "").toLowerCase().split("|")[0]; // Strip VLC options
  if (url.includes(".mpd")||url.includes("format=mpd")||url.includes("/dash/")) return "DASH";
  if (url.includes(".m3u8")||url.includes(".m3u")||url.includes("format=m3u8")||url.includes("/hls/")||url.includes("/playlist")) return "HLS";
  if (url.includes(".mp4")||url.includes(".mkv")||url.includes(".avi")||url.includes(".webm")||url.includes(".mov")) return "PROGRESSIVE";
  if (url.startsWith("rtmp://") || url.startsWith("rtmps://")) return "RTMP";
  if (url.startsWith("rtsp://")) return "RTSP";
  return "HLS"; // Default for IPTV
}

function getFallbackLogo(name) {
  const c = getNormalizedKey(name);
  if (c.includes("news")) return "https://images.unsplash.com/photo-1504711434969-e33886168f5c?w=240&q=80";
  if (c.includes("sport")||c.includes("cricket")||c.includes("football")) return "https://images.unsplash.com/photo-1508098682722-e99c43a406b2?w=240&q=80";
  if (c.includes("kids")||c.includes("cartoon")||c.includes("disney")) return "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=240&q=80";
  if (c.includes("music")||c.includes("mtv")) return "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=240&q=80";
  if (c.includes("documentary")||c.includes("history")||c.includes("geo")) return "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=240&q=80";
  return "https://images.unsplash.com/photo-1526470608268-f674ce90ebd4?w=240&q=80";
}

function mergeLiveEventsWithMasterChannels(fetchedEvents, channels) {
  const mergedList = fetchedEvents.map(it => ({
    id: it.id,
    title: it.title,
    sportCategory: it.sportCategory,
    logoUrl: it.logoUrl,
    feeds: [...it.feeds]
  }));

  const RE_BRACKETS = /\[.*?\]/g;
  const RE_NON_ALPHANUM = /[^a-zA-Z0-9 ]/g;
  const RE_WHITESPACES = /\s+/g;

  const eventTitleWords = fetchedEvents.map(event => {
    const clean = event.title.toLowerCase()
      .replace(RE_BRACKETS, " ")
      .replace(RE_NON_ALPHANUM, " ");
    const words = clean.split(RE_WHITESPACES).filter(w =>
      w.length > 2 && w !== "vs" && w !== "and" && w !== "feed" && w !== "live" && w !== "stream" && w !== "sports"
    );
    const simplified = clean.replace(RE_WHITESPACES, "");
    return { id: event.id, words: new Set(words), simplified };
  });

  const channelInfos = channels.filter(ch => {
    return ch.streams && ch.streams.length > 0 && ch.streams[0].url;
  }).map(channel => {
    const channelName = channel.name.trim();
    const clean = channelName.toLowerCase()
      .replace(RE_BRACKETS, " ")
      .replace(RE_NON_ALPHANUM, " ");
    const words = clean.split(RE_WHITESPACES).filter(w =>
      w.length > 2 && w !== "vs" && w !== "and" && w !== "feed" && w !== "live" && w !== "stream" && w !== "sports"
    );
    const simplified = clean.replace(RE_WHITESPACES, "");
    const categoryLower = (channel.category || "").toLowerCase();
    const nameLower = channelName.toLowerCase();

    const isExplicitLiveEventGroup = categoryLower === "live events" ||
      categoryLower === "live event" ||
      categoryLower === "liveevents" ||
      categoryLower === "live_events" ||
      categoryLower === "live-events" ||
      categoryLower.includes("live event") ||
      categoryLower.includes("live events");

    const isSportOrLiveEvent = categoryLower.includes("sports") ||
      categoryLower.includes("live") ||
      categoryLower.includes("event") ||
      nameLower.includes("sports") ||
      nameLower.includes("live") ||
      isExplicitLiveEventGroup;

    const representsMatch = nameLower.includes(" vs ") ||
      nameLower.includes(" v ") ||
      nameLower.includes(" at ") ||
      nameLower.includes(" @ ");

    return {
      channel,
      channelName,
      categoryLower,
      nameLower,
      words: new Set(words),
      simplified,
      isSportOrLiveEvent,
      representsMatch,
      isExplicitLiveEventGroup
    };
  });

  for (const info of channelInfos) {
    const channel = info.channel;
    const channelName = info.channelName;
    const streamUrl = channel.streams[0].url;
    let matched = false;

    for (let i = 0; i < mergedList.length; i++) {
      const event = mergedList[i];
      const precompEvent = eventTitleWords.find(e => e.id === event.id);
      if (!precompEvent) continue;

      const eventWords = precompEvent.words;
      const eventSimplified = precompEvent.simplified;

      let intersectionSize = 0;
      for (const word of info.words) {
        if (eventWords.has(word)) intersectionSize++;
      }

      const wordsMatch = eventWords.size > 0 && info.words.size > 0 && intersectionSize >= 2;
      const similar = wordsMatch ||
        (eventSimplified.length > 0 && info.simplified.length > 0 &&
         (eventSimplified.includes(info.simplified) || info.simplified.includes(eventSimplified)));

      if (similar) {
        const feedExists = event.feeds.some(f => f.streamUrl.trim() === streamUrl.trim());
        if (!feedExists) {
          event.feeds.push({
            rawName: channelName,
            provider: channel.category ? `Matched (${channel.category})` : "TV Playlist Stream",
            language: "Auto",
            streamUrl: streamUrl,
            logoUrl: channel.logoUrl || ""
          });
        }
        matched = true;
      }
    }

    if (!matched) {
      const shouldAutoDetect = (info.isSportOrLiveEvent && info.representsMatch) || info.isExplicitLiveEventGroup;
      if (shouldAutoDetect) {
        let sportCategory = "Live Sport";
        const categoryLower = info.categoryLower;
        const nameLower = info.nameLower;

        if (categoryLower.includes("foot") || nameLower.includes("foot") || nameLower.includes("soccer") || nameLower.includes("fútbol")) {
          sportCategory = "Football";
        } else if (categoryLower.includes("basket") || nameLower.includes("nba") || nameLower.includes("basket")) {
          sportCategory = "Basketball";
        } else if (categoryLower.includes("tennis") || nameLower.includes("tennis") || nameLower.includes("wimbledon")) {
          sportCategory = "Tennis";
        } else if (categoryLower.includes("racing") || nameLower.includes("f1") || nameLower.includes("race")) {
          sportCategory = "Racing";
        } else if (categoryLower.includes("cricket") || nameLower.includes("cricket")) {
          sportCategory = "Cricket";
        } else if (categoryLower.includes("golf") || nameLower.includes("golf")) {
          sportCategory = "Golf";
        } else if (categoryLower.includes("ufc") || categoryLower.includes("wwe") || categoryLower.includes("mma") || categoryLower.includes("wrestl") || nameLower.includes("ufc") || nameLower.includes("wwe") || nameLower.includes("mma") || nameLower.includes("wrestl")) {
          sportCategory = "Wrestling/MMA";
        } else {
          sportCategory = (channel.category && channel.category.trim() &&
            categoryLower !== "live events" && categoryLower !== "live event") ? channel.category : "Live Event";
        }

        const cleanTitle = channelName.replace(RE_BRACKETS, "").trim();
        const feedExists = mergedList.some(existing =>
          existing.feeds.some(f => f.streamUrl.trim() === streamUrl.trim())
        );

        if (!feedExists) {
          let hash = 0;
          const key = `auto_${cleanTitle.toLowerCase()}`;
          for (let k = 0; k < key.length; k++) {
            hash = (hash << 5) - hash + key.charCodeAt(k);
            hash |= 0;
          }
          const uniqueId = `auto_${Math.abs(hash).toString()}`;

          mergedList.push({
            id: uniqueId,
            title: cleanTitle,
            sportCategory: sportCategory,
            logoUrl: channel.logoUrl || getPlaceholderLogoForSport(sportCategory),
            feeds: [
              {
                rawName: channelName,
                provider: "Playlist Auto-Detect",
                language: "Live",
                streamUrl: streamUrl,
                logoUrl: channel.logoUrl || ""
              }
            ]
          });
        }
      }
    }
  }

  return mergedList.sort((e1, e2) => {
    const cat1 = e1.sportCategory.toLowerCase();
    const cat2 = e2.sportCategory.toLowerCase();
    const score1 = (cat1.includes("football") || cat1.includes("soccer")) ? 100 : (cat1.includes("cricket") ? 90 : 0);
    const score2 = (cat2.includes("football") || cat2.includes("soccer")) ? 100 : (cat2.includes("cricket") ? 90 : 0);

    if (score1 !== score2) return score2 - score1;
    return e1.sportCategory.localeCompare(e2.sportCategory);
  });
}
