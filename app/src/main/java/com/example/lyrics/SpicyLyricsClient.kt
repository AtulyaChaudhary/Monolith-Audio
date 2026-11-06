package com.example.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.max

/**
 * Sync granularity priority ranker (strictly within the SAME song):
 * Syllable level (4) > Word level (3) > Line level / LRC (2) > Plain Text / No Sync (1)
 */
enum class LyricsSyncPriority(val rank: Int, val label: String) {
    NO_SYNC(1, "Plain Text"),
    LINE(2, "Line Sync (LRC)"),
    WORD(3, "Word Sync"),
    SYLLABLE(4, "Syllable Sync")
}

data class SpicyContributor(
    val id: String,
    val username: String,
    val avatarUrl: String?,
    val hasProfileBanner: Boolean = false,
    val profileUrl: String? = null
)

data class LyricsAttributionInfo(
    val songWriters: List<String> = emptyList(),
    val source: String = "", // "spicy_lyrics", "apple_music", "spotify", "local_ttml", "local_lrc", "local_txt"
    val maker: SpicyContributor? = null,
    val uploader: SpicyContributor? = null,
    val syncPriority: LyricsSyncPriority = LyricsSyncPriority.SYLLABLE,
    val matchedTrackId: String? = null
) {
    fun toJsonString(): String {
        val obj = JSONObject()
        val writersArr = JSONArray()
        songWriters.forEach { writersArr.put(it) }
        obj.put("songWriters", writersArr)
        obj.put("source", source)
        obj.put("syncPriority", syncPriority.name)
        if (matchedTrackId != null) obj.put("matchedTrackId", matchedTrackId)
        if (maker != null) {
            val m = JSONObject()
            m.put("id", maker.id)
            m.put("username", maker.username)
            m.put("avatarUrl", maker.avatarUrl ?: "")
            m.put("hasProfileBanner", maker.hasProfileBanner)
            m.put("profileUrl", maker.profileUrl ?: "")
            obj.put("maker", m)
        }
        if (uploader != null) {
            val u = JSONObject()
            u.put("id", uploader.id)
            u.put("username", uploader.username)
            u.put("avatarUrl", uploader.avatarUrl ?: "")
            u.put("hasProfileBanner", uploader.hasProfileBanner)
            u.put("profileUrl", uploader.profileUrl ?: "")
            obj.put("uploader", u)
        }
        return obj.toString()
    }

    companion object {
        fun fromJsonString(json: String?): LyricsAttributionInfo? {
            if (json.isNullOrBlank()) return null
            return try {
                val obj = JSONObject(json)
                val writers = mutableListOf<String>()
                val arr = obj.optJSONArray("songWriters")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val w = arr.optString(i).trim()
                        if (w.isNotEmpty()) writers.add(w)
                    }
                }
                val priority = try {
                    LyricsSyncPriority.valueOf(obj.optString("syncPriority", "SYLLABLE"))
                } catch (_: Exception) {
                    LyricsSyncPriority.SYLLABLE
                }
                fun parseContrib(cObj: JSONObject?): SpicyContributor? {
                    if (cObj == null) return null
                    val uname = cObj.optString("username", "").trim()
                    if (uname.isEmpty()) return null
                    return SpicyContributor(
                        id = cObj.optString("id", ""),
                        username = uname,
                        avatarUrl = cObj.optString("avatarUrl").takeIf { it.isNotBlank() },
                        hasProfileBanner = cObj.optBoolean("hasProfileBanner", false),
                        profileUrl = cObj.optString("profileUrl").takeIf { it.isNotBlank() }
                    )
                }
                LyricsAttributionInfo(
                    songWriters = writers,
                    source = obj.optString("source", ""),
                    maker = parseContrib(obj.optJSONObject("maker")),
                    uploader = parseContrib(obj.optJSONObject("uploader")),
                    syncPriority = priority,
                    matchedTrackId = obj.optString("matchedTrackId").takeIf { it.isNotBlank() }
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}

data class SpicyServiceIds(
    val spotify: String? = null,
    val isrc: String? = null,
    val appleMusic: String? = null,
    val tidal: String? = null,
    val youtube: String? = null
) {
    /**
     * Returns all candidate IDs supported by `GET /v1/lyrics/{TRACK_ID}`:
     * Spotify ID, Apple Music ID, and ISRC.
     */
    fun toCandidateIdList(primaryId: String, variantIds: List<String> = emptyList()): List<String> {
        return buildList {
            if (primaryId.isNotBlank()) add(primaryId.trim())
            if (!spotify.isNullOrBlank()) add(spotify.trim())
            if (!appleMusic.isNullOrBlank()) add(appleMusic.trim())
            if (!isrc.isNullOrBlank()) add(isrc.trim())
            variantIds.forEach { v -> if (v.isNotBlank()) add(v.trim()) }
        }.distinct()
    }
}

data class SpicyTrackVariant(
    val id: String,
    val album: String,
    val durationSeconds: Double
)

data class SpicyTrackSearchResult(
    val id: String,
    val ids: SpicyServiceIds,
    val title: String,
    val artists: List<String>,
    val album: String,
    val durationSeconds: Double,
    val explicit: Boolean,
    val artworkUrl: String?,
    val tier: String,
    val variants: List<SpicyTrackVariant> = emptyList()
) {
    val artistDisplay: String
        get() = if (artists.isEmpty()) "Unknown Artist" else artists.joinToString(", ")

    fun allCandidateIds(): List<String> {
        return ids.toCandidateIdList(
            primaryId = id,
            variantIds = variants.map { it.id }
        )
    }
}

data class FetchedSpicyLyrics(
    val trackId: String,
    val lines: List<SyllableLineModel>,
    val attribution: LyricsAttributionInfo,
    val rawJsonPayload: String
)

/**
 * Production Client for `https://api.spicylyrics.org/v1`:
 * - Substitutes the user's Secret Key (`sl_sk_...`) in place of `"$SPICY_LYRICS_SECRET_KEY"`
 *   and resets back to `"$SPICY_LYRICS_SECRET_KEY"` when revoked.
 * - Enforces strict SAME-SONG verification before comparing sync priority so another song's TTML
 *   is NEVER chosen over the actual song's LRC (`Line`) or Plain Text (`No Sync`) lyrics.
 * - Supports Syllable, Word, Line (LRC), and Plain Text (No Sync) responses.
 */
object SpicyLyricsClient {

    const val DEFAULT_SECRET_KEY_PLACEHOLDER = "\$SPICY_LYRICS_SECRET_KEY"
    const val BASE_URL = "https://api.spicylyrics.org/v1"

    fun isSecretKeyConfigured(key: String?): Boolean {
        val trimmed = key?.trim() ?: return false
        return trimmed.isNotEmpty() && trimmed != DEFAULT_SECRET_KEY_PLACEHOLDER
    }

    /**
     * Generates the exact Node.js `fetch()` snippet from the user's note, dynamically showing
     * either their active secret key or `"$SPICY_LYRICS_SECRET_KEY"` when unset/revoked.
     */
    fun buildNodeJsFetchSnippet(secretKey: String, trackId: String = "TRACK_ID"): String {
        val activeKey = if (isSecretKeyConfigured(secretKey)) {
            secretKey.trim()
        } else {
            DEFAULT_SECRET_KEY_PLACEHOLDER
        }
        val cleanTrackId = trackId.trim().ifEmpty { "TRACK_ID" }
        return """
const response = await fetch(
  "https://api.spicylyrics.org/v1/lyrics/$cleanTrackId",
  {
    headers: {
      Authorization: "Bearer $activeKey",
    },
  }
);

const data = await response.json();
console.log(data);
        """.trimIndent()
    }

    suspend fun searchTracks(
        secretKey: String,
        title: String? = null,
        artist: String? = null,
        album: String? = null,
        durationSeconds: Double? = null,
        freeQuery: String? = null
    ): Result<List<SpicyTrackSearchResult>> = withContext(Dispatchers.IO) {
        if (!isSecretKeyConfigured(secretKey)) {
            return@withContext Result.failure(
                IllegalStateException("Enter your Spicy Lyrics Secret Key (sl_sk_...) in Settings first.")
            )
        }

        try {
            val queryParams = mutableListOf<String>()
            if (!freeQuery.isNullOrBlank()) {
                queryParams.add("q=${encode(freeQuery.trim())}")
            } else {
                if (!title.isNullOrBlank()) queryParams.add("title=${encode(title.trim())}")
                if (!artist.isNullOrBlank() && !artist.equals("Unknown Artist", ignoreCase = true)) {
                    queryParams.add("artist=${encode(artist.trim())}")
                }
                if (!album.isNullOrBlank() && !album.equals("Unknown Album", ignoreCase = true)) {
                    queryParams.add("album=${encode(album.trim())}")
                }
                if (durationSeconds != null && durationSeconds > 1.0) {
                    queryParams.add(
                        "duration=${String.format(java.util.Locale.US, "%.3f", durationSeconds)}"
                    )
                }
            }

            if (queryParams.isEmpty()) {
                return@withContext Result.success(emptyList())
            }

            val urlStr = "$BASE_URL/tracks/search?${queryParams.joinToString("&")}"
            val responseText = executeNodeStyleFetch(urlStr, secretKey.trim())
            val parsedResults = parseSearchResponseJson(responseText)
            Result.success(parsedResults)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Verifies whether a search result candidate is genuinely the SAME song as `requestedTitle` + `requestedArtist`
     * so we NEVER pick another song's TTML over the exact song's LRC / Line or Plain Text lyrics!
     */
    fun isSameSongMatch(
        requestedTitle: String,
        requestedArtist: String?,
        candidate: SpicyTrackSearchResult
    ): Boolean {
        val normReqTitle = normalizeForComparison(cleanTrackTitleForSearch(requestedTitle))
        val normCandTitle = normalizeForComparison(cleanTrackTitleForSearch(candidate.title))
        if (normReqTitle.isEmpty() || normCandTitle.isEmpty()) return false

        val titleMatches = normReqTitle == normCandTitle ||
            normCandTitle.contains(normReqTitle) ||
            normReqTitle.contains(normCandTitle)

        if (!titleMatches) return false

        val cleanReqArtist = requestedArtist?.trim()?.takeIf {
            it.isNotEmpty() && !it.equals("Unknown Artist", ignoreCase = true)
        } ?: return true

        val normReqArtist = normalizeForComparison(cleanReqArtist)
        if (normReqArtist.isEmpty()) return true

        val anyArtistMatches = candidate.artists.any { candArt ->
            val normCandArt = normalizeForComparison(candArt)
            normCandArt.isNotEmpty() && (
                normCandArt == normReqArtist ||
                    normCandArt.contains(normReqArtist) ||
                    normReqArtist.contains(normCandArt)
                )
        }

        // Reject "other_artist" or "related" tiers if the artist doesn't match
        if (!anyArtistMatches && (candidate.tier.equals("other_artist", ignoreCase = true) || candidate.tier.equals("related", ignoreCase = true))) {
            return false
        }

        return anyArtistMatches || candidate.tier.equals("exact", ignoreCase = true)
    }

    private fun normalizeForComparison(input: String): String {
        return input.lowercase()
            .replace(Regex("\\((?:feat|ft|with|prod)[^)]*\\)"), "")
            .replace(Regex("\\[(?:feat|ft|with|prod)[^]]*]"), "")
            .replace(Regex("[^a-z0-9\\p{L}\\p{N}]+"), "")
            .trim()
    }

    /**
     * Automatic Zero-Click Track Lyrics Resolution:
     * 1. Queries `/v1/tracks/search` using the song's own Title, Artist, Album, and Duration.
     * 2. Filters search results strictly to candidates that match the SAME song (`isSameSongMatch`).
     *    NEVER falls back to a different song just because that other song has TTML!
     * 3. Across all IDs of the matched song (`spotify`, `apple_music`, `isrc`, `variants`),
     *    returns the best available lyrics (`Syllable > Word > Line (LRC) > Plain Text`).
     */
    suspend fun autoFetchBestLyricsForTrack(
        secretKey: String,
        rawTitle: String,
        rawArtist: String,
        rawAlbum: String?,
        durationMs: Long
    ): Result<FetchedSpicyLyrics> = withContext(Dispatchers.IO) {
        if (!isSecretKeyConfigured(secretKey)) {
            return@withContext Result.failure(
                IllegalStateException("Spicy Lyrics Secret Key is not configured")
            )
        }

        val cleanedTitle = cleanTrackTitleForSearch(rawTitle)
        val cleanedArtist = rawArtist.takeIf {
            it.isNotBlank() && !it.equals("Unknown Artist", ignoreCase = true)
        }
        val durationSec = if (durationMs > 1000L) durationMs / 1000.0 else null

        var results = searchTracks(
            secretKey = secretKey,
            title = cleanedTitle,
            artist = cleanedArtist,
            album = rawAlbum,
            durationSeconds = durationSec
        ).getOrNull().orEmpty()

        if (results.isEmpty()) {
            val fallbackQ = listOfNotNull(cleanedTitle, cleanedArtist).joinToString(" ").trim()
            if (fallbackQ.isNotEmpty()) {
                results = searchTracks(
                    secretKey = secretKey,
                    freeQuery = fallbackQ
                ).getOrNull().orEmpty()
            }
        }

        if (results.isEmpty()) {
            return@withContext Result.failure(NoSuchElementException("No matching track found for \"$cleanedTitle\""))
        }

        // Strict same-song filter so we NEVER pick another song's TTML over the actual song's LRC!
        val sameSongCandidates = results
            .filter { isSameSongMatch(cleanedTitle, cleanedArtist, it) }
            .sortedByDescending { tierScore(it.tier) }

        if (sameSongCandidates.isEmpty()) {
            return@withContext Result.failure(
                NoSuchElementException("No exact song match found for \"$cleanedTitle\" by ${cleanedArtist ?: "Unknown"}")
            )
        }

        // Collect candidate IDs strictly from the best matching same-song entry
        val primarySameSong = sameSongCandidates.first()
        val bestForExactSong = fetchBestLyricsAcrossIds(
            secretKey = secretKey,
            candidateIds = primarySameSong.allCandidateIds(),
            trackDurationMs = durationMs
        )
        if (bestForExactSong.isSuccess) {
            return@withContext bestForExactSong
        }

        // If the first same-song entry had no lyrics at all on any ID, check the second same-song version if present
        if (sameSongCandidates.size > 1) {
            val secondSameSong = sameSongCandidates[1]
            val secondTry = fetchBestLyricsAcrossIds(
                secretKey = secretKey,
                candidateIds = secondSameSong.allCandidateIds(),
                trackDurationMs = durationMs
            )
            if (secondTry.isSuccess) {
                return@withContext secondTry
            }
        }

        Result.failure(NoSuchElementException("No lyrics available for \"$cleanedTitle\""))
    }

    /**
     * Given multiple IDs for the SAME song (Spotify ID, Apple Music ID, ISRC, and variants),
     * fetches `GET /v1/lyrics/{id}` concurrently across all IDs and selects the highest-quality
     * sync for that song:
     * `Syllable` (4) > `Word` (3) > `Line (LRC)` (2) > `Plain Text (No Sync)` (1).
     */
    suspend fun fetchBestLyricsAcrossIds(
        secretKey: String,
        candidateIds: List<String>,
        trackDurationMs: Long = 180_000L
    ): Result<FetchedSpicyLyrics> = coroutineScope {
        if (!isSecretKeyConfigured(secretKey)) {
            return@coroutineScope Result.failure(
                IllegalStateException("Enter your Spicy Lyrics Secret Key first.")
            )
        }

        val uniqueIds = candidateIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (uniqueIds.isEmpty()) {
            return@coroutineScope Result.failure(IllegalArgumentException("No track IDs provided"))
        }

        val deferredFetches = uniqueIds.map { trackId ->
            async(Dispatchers.IO) {
                fetchSingleTrackLyrics(secretKey.trim(), trackId, trackDurationMs)
            }
        }

        val fetchedCandidates = deferredFetches.awaitAll().mapNotNull { it.getOrNull() }
        if (fetchedCandidates.isEmpty()) {
            return@coroutineScope Result.failure(
                NoSuchElementException("No lyrics returned for IDs: ${uniqueIds.joinToString(", ")}")
            )
        }

        val bestInitial = fetchedCandidates.maxWithOrNull(
            compareBy<FetchedSpicyLyrics> { it.attribution.syncPriority.rank }
                .thenBy { it.lines.size }
        )!!

        if (bestInitial.attribution.syncPriority == LyricsSyncPriority.SYLLABLE) {
            return@coroutineScope Result.success(bestInitial)
        }

        val extraDiscoveredIds = extractLinkedIdsFromRawPayload(bestInitial.rawJsonPayload)
            .filterNot { it in uniqueIds }

        if (extraDiscoveredIds.isNotEmpty()) {
            val extraFetched = extraDiscoveredIds.map { extraId ->
                async(Dispatchers.IO) {
                    fetchSingleTrackLyrics(secretKey.trim(), extraId, trackDurationMs)
                }
            }.awaitAll().mapNotNull { it.getOrNull() }

            val allCombined = fetchedCandidates + extraFetched
            val bestOverall = allCombined.maxWithOrNull(
                compareBy<FetchedSpicyLyrics> { it.attribution.syncPriority.rank }
                    .thenBy { it.lines.size }
            )!!
            return@coroutineScope Result.success(bestOverall)
        }

        Result.success(bestInitial)
    }

    private fun fetchSingleTrackLyrics(
        secretKey: String,
        trackId: String,
        trackDurationMs: Long = 180_000L
    ): Result<FetchedSpicyLyrics> {
        return try {
            val urlStr = "$BASE_URL/lyrics/${encode(trackId)}"
            val rawJson = executeNodeStyleFetch(urlStr, secretKey)
            val parsed = parseLyricsResponseJson(rawJson, trackId, trackDurationMs)
                ?: return Result.failure(NoSuchElementException("Empty lyrics for $trackId"))
            Result.success(parsed)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun executeNodeStyleFetch(urlStr: String, secretKey: String): String {
        val url = URL(urlStr)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 12_000
            setRequestProperty("Authorization", "Bearer $secretKey")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "MonolithAudio-NodeFetch/1.0")
        }

        val status = conn.responseCode
        val stream = if (status in 200..299) conn.inputStream else conn.errorStream
        val body = stream?.let {
            BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { r -> r.readText() }
        } ?: ""

        if (status !in 200..299) {
            throw RuntimeException("HTTP $status from Spicy Lyrics API: ${body.take(180)}")
        }
        return body
    }

    fun parseSearchResponseJson(rawJson: String): List<SpicyTrackSearchResult> {
        val root = JSONObject(rawJson)
        val body = root.optJSONObject("Body") ?: root
        val resultsArr = body.optJSONArray("results") ?: return emptyList()
        val list = mutableListOf<SpicyTrackSearchResult>()

        for (i in 0 until resultsArr.length()) {
            val item = resultsArr.optJSONObject(i) ?: continue
            val id = item.optString("id", "").trim()
            if (id.isEmpty()) continue

            val idsObj = item.optJSONObject("ids")
            val serviceIds = SpicyServiceIds(
                spotify = idsObj?.optString("spotify")?.takeIf { it.isNotBlank() } ?: id,
                isrc = idsObj?.optString("isrc")?.takeIf { it.isNotBlank() },
                appleMusic = idsObj?.optString("apple_music")?.takeIf { it.isNotBlank() },
                tidal = idsObj?.optString("tidal")?.takeIf { it.isNotBlank() },
                youtube = idsObj?.optString("youtube")?.takeIf { it.isNotBlank() }
            )

            val artists = mutableListOf<String>()
            val artistsArr = item.optJSONArray("artists")
            if (artistsArr != null) {
                for (a in 0 until artistsArr.length()) {
                    val name = artistsArr.optString(a).trim()
                    if (name.isNotEmpty()) artists.add(name)
                }
            }

            val variants = mutableListOf<SpicyTrackVariant>()
            val variantsArr = item.optJSONArray("variants")
            if (variantsArr != null) {
                for (v in 0 until variantsArr.length()) {
                    val vo = variantsArr.optJSONObject(v) ?: continue
                    val vid = vo.optString("id").trim()
                    if (vid.isNotEmpty()) {
                        variants.add(
                            SpicyTrackVariant(
                                id = vid,
                                album = vo.optString("album", ""),
                                durationSeconds = vo.optDouble("duration", 0.0)
                            )
                        )
                    }
                }
            }

            list.add(
                SpicyTrackSearchResult(
                    id = id,
                    ids = serviceIds,
                    title = item.optString("title", "Unknown Title"),
                    artists = artists,
                    album = item.optString("album", ""),
                    durationSeconds = item.optDouble("duration", 0.0),
                    explicit = item.optBoolean("explicit", false),
                    artworkUrl = item.optString("artwork").takeIf { it.isNotBlank() },
                    tier = item.optString("tier", "exact"),
                    variants = variants
                )
            )
        }
        return list
    }

    /**
     * Parses `GET /v1/lyrics/{TRACK_ID}` JSON response into `FetchedSpicyLyrics`
     * supporting ALL 4 sync formats:
     * 1. `Syllable` (Syllable-level timings with `IsPartOfWord`)
     * 2. `Word` (Word-level timings)
     * 3. `Line` / LRC (Line-synced timings — sets `isLineSyncedOnly = true` on each line)
     * 4. `Static` / `Plain` / `Text` / `No Sync` (Plain unsynced text lines — sets `isPlainTextUnsynced = true`)
     */
    fun parseLyricsResponseJson(
        rawJson: String,
        requestedId: String,
        trackDurationMs: Long = 180_000L
    ): FetchedSpicyLyrics? {
        val trimmedRaw = rawJson.trim()
        if (trimmedRaw.isEmpty()) return null

        // If the cached payload is raw TTML, LRC, or Plain Text (not JSON), parse it directly
        if (!trimmedRaw.startsWith("{")) {
            val (lines, priority) = TtmlParser.parseAnyLyricsFormat(trimmedRaw, trackDurationMs)
            if (lines.isEmpty()) return null
            val attr = TtmlParser.extractAttributionFromTtml(trimmedRaw)?.copy(syncPriority = priority)
                ?: LyricsAttributionInfo(source = "local", syncPriority = priority, matchedTrackId = requestedId)
            return FetchedSpicyLyrics(
                trackId = requestedId,
                lines = lines,
                attribution = attr,
                rawJsonPayload = rawJson
            )
        }

        val root = JSONObject(trimmedRaw)
        val body = root.optJSONObject("Body") ?: root

        val typeStr = body.optString("Type", "").ifBlank { body.optString("type", "") }.trim()
        val sourceStr = body.optString("source", "").ifBlank { body.optString("Source", "") }.trim()
        val matchedId = body.optString("id", requestedId).trim().ifEmpty { requestedId }

        // 1. Parse SongWriters
        val songWriters = mutableListOf<String>()
        val writersArr = body.optJSONArray("SongWriters") ?: body.optJSONArray("songwriters")
        if (writersArr != null) {
            for (i in 0 until writersArr.length()) {
                val w = writersArr.optString(i).trim()
                if (w.isNotEmpty()) songWriters.add(w)
            }
        }

        // 2. Parse UploadAttribution (Uploader & Maker with circular avatar PFP URLs)
        val attrObj = body.optJSONObject("UploadAttribution")
        val uploader = attrObj?.optJSONObject("Uploader")?.let { parseContributor(it) }
        val maker = attrObj?.optJSONObject("Maker")?.let { parseContributor(it) }

        // 3. Parse Content (or Lyrics / Text / lrc fallback fields)
        val contentAny = body.opt("Content")
            ?: body.opt("content")
            ?: body.opt("Lines")
            ?: body.opt("lines")
            ?: body.opt("Lyrics")
            ?: body.opt("lyrics")
            ?: body.opt("Text")
            ?: body.opt("text")

        var detectedPriority = when (typeStr.lowercase()) {
            "syllable" -> LyricsSyncPriority.SYLLABLE
            "word" -> LyricsSyncPriority.WORD
            "line", "lrc" -> LyricsSyncPriority.LINE
            "static", "plain", "text", "unsynced", "none" -> LyricsSyncPriority.NO_SYNC
            else -> LyricsSyncPriority.LINE
        }

        val parsedLines = mutableListOf<SyllableLineModel>()

        when (contentAny) {
            is String -> {
                val (linesFromStr, inferredPriority) = TtmlParser.parseAnyLyricsFormat(contentAny, trackDurationMs)
                parsedLines.addAll(linesFromStr)
                detectedPriority = inferredPriority
            }
            is JSONArray -> {
                val (linesFromJson, inferredPriority) = parseContentArray(contentAny, typeStr, trackDurationMs)
                parsedLines.addAll(linesFromJson)
                detectedPriority = inferredPriority
            }
            is JSONObject -> {
                // Sometimes `Content` is an object wrapping `Lines` or `Text`
                val innerArr = contentAny.optJSONArray("Lines")
                    ?: contentAny.optJSONArray("lines")
                    ?: contentAny.optJSONArray("Content")
                val innerText = contentAny.optString("Text", "").ifBlank { contentAny.optString("text", "") }
                if (innerArr != null) {
                    val (linesFromJson, inferredPriority) = parseContentArray(innerArr, typeStr, trackDurationMs)
                    parsedLines.addAll(linesFromJson)
                    detectedPriority = inferredPriority
                } else if (innerText.isNotBlank()) {
                    val (linesFromStr, inferredPriority) = TtmlParser.parseAnyLyricsFormat(innerText, trackDurationMs)
                    parsedLines.addAll(linesFromStr)
                    detectedPriority = inferredPriority
                }
            }
        }

        if (parsedLines.isEmpty()) return null

        val attribution = LyricsAttributionInfo(
            songWriters = songWriters,
            source = sourceStr,
            maker = maker,
            uploader = uploader,
            syncPriority = detectedPriority,
            matchedTrackId = matchedId
        )

        return FetchedSpicyLyrics(
            trackId = matchedId,
            lines = parsedLines,
            attribution = attribution,
            rawJsonPayload = rawJson
        )
    }

    private fun parseContributor(obj: JSONObject): SpicyContributor? {
        val username = obj.optString("username", "").trim()
        val id = obj.optString("id", "").trim()
        if (username.isEmpty() && id.isEmpty()) return null
        return SpicyContributor(
            id = id,
            username = username.ifEmpty { id },
            avatarUrl = obj.optString("avatar").takeIf { it.isNotBlank() },
            hasProfileBanner = obj.optBoolean("hasProfileBanner", false),
            profileUrl = obj.optString("url").takeIf { it.isNotBlank() }
        )
    }

    private fun parseContentArray(
        contentArr: JSONArray,
        declaredType: String,
        trackDurationMs: Long
    ): Pair<List<SyllableLineModel>, LyricsSyncPriority> {
        val result = mutableListOf<SyllableLineModel>()
        var lineIdx = 0
        val isDeclaredStatic = declaredType.equals("Static", ignoreCase = true) ||
            declaredType.equals("Plain", ignoreCase = true) ||
            declaredType.equals("Text", ignoreCase = true) ||
            declaredType.equals("Unsynced", ignoreCase = true)

        var hasSyllableSplits = declaredType.equals("Syllable", ignoreCase = true)
        var hasWordTimings = declaredType.equals("Word", ignoreCase = true) || hasSyllableSplits
        var hasLineTimings = declaredType.equals("Line", ignoreCase = true) || hasWordTimings

        var prevLineEndMs = 0.0

        data class PendingLineItem(
            val text: String,
            val startMs: Double?,
            val endMs: Double?,
            val isBg: Boolean,
            val isDuo: Boolean
        )
        val pendingLineItems = mutableListOf<PendingLineItem>()

        for (i in 0 until contentArr.length()) {
            val rawElem = contentArr.opt(i) ?: continue

            // Support array of plain strings: ["Verse 1...", "Verse 2..."]
            if (rawElem is String) {
                val trimmed = rawElem.trim()
                if (trimmed.isNotEmpty()) {
                    pendingLineItems.add(
                        PendingLineItem(
                            text = trimmed,
                            startMs = null,
                            endMs = null,
                            isBg = false,
                            isDuo = false
                        )
                    )
                }
                continue
            }

            val item = rawElem as? JSONObject ?: continue
            val oppositeAligned = item.optBoolean("OppositeAligned", false) ||
                item.optString("Alignment", "").equals("Right", ignoreCase = true) ||
                item.optString("Agent", "").equals("v2", ignoreCase = true)

            // Case A: `Lead` object (Syllable, Word, or Line-with-Lead)
            val leadObj = item.optJSONObject("Lead")
            if (leadObj != null) {
                val leadSyllables = leadObj.optJSONArray("Syllables") ?: leadObj.optJSONArray("Words")
                if (leadSyllables != null && leadSyllables.length() > 0) {
                    val (leadWords, isSyllableGranular) = buildWordsFromSpicySyllables(
                        syllablesArr = leadSyllables,
                        isBg = false
                    )
                    if (isSyllableGranular) hasSyllableSplits = true
                    hasWordTimings = true

                    if (leadWords.isNotEmpty()) {
                        val lStart = parseSpicyTimeMs(leadObj.optDouble("StartTime", Double.NaN))
                            ?: leadWords.first().startTimeMs
                        val lEnd = parseSpicyTimeMs(leadObj.optDouble("EndTime", Double.NaN))
                            ?: leadWords.last().endTimeMs

                        if (lineIdx == 0 && lStart >= 2500.0) {
                            result.add(createInterludeDots(lineIdx++, 0.0, lStart))
                        } else if (prevLineEndMs > 0.0 && lStart - prevLineEndMs >= 3800.0) {
                            result.add(createInterludeDots(lineIdx++, prevLineEndMs + 150.0, lStart))
                        }

                        result.add(
                            SyllableLineModel(
                                index = lineIdx++,
                                startTimeMs = lStart,
                                endTimeMs = max(lStart + 150.0, lEnd),
                                words = leadWords,
                                isDotLine = false,
                                isBgLine = false,
                                isDuoLine = oppositeAligned
                            )
                        )
                        prevLineEndMs = max(prevLineEndMs, lEnd)
                    }
                } else {
                    // `Lead` object in a `Line` response that has `Text` + `StartTime`/`EndTime` instead of `Syllables`
                    val leadText = leadObj.optString("Text", "").ifBlank { leadObj.optString("text", "") }.trim()
                    if (leadText.isNotEmpty()) {
                        val lStart = parseSpicyTimeMs(leadObj.optDouble("StartTime", Double.NaN))
                            ?: parseSpicyTimeMs(item.optDouble("StartTime", Double.NaN))
                        val lEnd = parseSpicyTimeMs(leadObj.optDouble("EndTime", Double.NaN))
                            ?: parseSpicyTimeMs(item.optDouble("EndTime", Double.NaN))
                        if (lStart != null) hasLineTimings = true
                        pendingLineItems.add(
                            PendingLineItem(
                                text = leadText,
                                startMs = lStart,
                                endMs = lEnd,
                                isBg = false,
                                isDuo = oppositeAligned
                            )
                        )
                    }
                }

                // Background vocal array (`Background: [ { Syllables: [...] } ]`)
                val bgArr = item.optJSONArray("Background")
                if (bgArr != null) {
                    for (b in 0 until bgArr.length()) {
                        val bgObj = bgArr.optJSONObject(b) ?: continue
                        val bgSyllables = bgObj.optJSONArray("Syllables") ?: bgObj.optJSONArray("Words")
                        if (bgSyllables != null && bgSyllables.length() > 0) {
                            val (bgWords, _) = buildWordsFromSpicySyllables(
                                syllablesArr = bgSyllables,
                                isBg = true
                            )
                            if (bgWords.isNotEmpty()) {
                                val bgStart = parseSpicyTimeMs(bgObj.optDouble("StartTime", Double.NaN))
                                    ?: bgWords.first().startTimeMs
                                val bgEnd = parseSpicyTimeMs(bgObj.optDouble("EndTime", Double.NaN))
                                    ?: bgWords.last().endTimeMs
                                result.add(
                                    SyllableLineModel(
                                        index = lineIdx++,
                                        startTimeMs = bgStart,
                                        endTimeMs = max(bgStart + 150.0, bgEnd),
                                        words = bgWords,
                                        isDotLine = false,
                                        isBgLine = true,
                                        isDuoLine = oppositeAligned
                                    )
                                )
                            }
                        } else {
                            val bgText = bgObj.optString("Text", "").ifBlank { bgObj.optString("text", "") }.trim()
                            if (bgText.isNotEmpty()) {
                                val bStart = parseSpicyTimeMs(bgObj.optDouble("StartTime", Double.NaN))
                                val bEnd = parseSpicyTimeMs(bgObj.optDouble("EndTime", Double.NaN))
                                pendingLineItems.add(
                                    PendingLineItem(
                                        text = bgText,
                                        startMs = bStart,
                                        endMs = bEnd,
                                        isBg = true,
                                        isDuo = oppositeAligned
                                    )
                                )
                            }
                        }
                    }
                }
                continue
            }

            // Case B: Direct `Syllables` or `Words` JSONArray on the line item
            val directSyllables = item.optJSONArray("Syllables") ?: item.optJSONArray("Words")
            if (directSyllables != null && directSyllables.length() > 0) {
                val isBgLine = item.optBoolean("IsBackground", false)
                val (words, isSyllableGranular) = buildWordsFromSpicySyllables(
                    syllablesArr = directSyllables,
                    isBg = isBgLine
                )
                if (isSyllableGranular) hasSyllableSplits = true
                hasWordTimings = true

                if (words.isNotEmpty()) {
                    val lStart = parseSpicyTimeMs(item.optDouble("StartTime", Double.NaN))
                        ?: words.first().startTimeMs
                    val lEnd = parseSpicyTimeMs(item.optDouble("EndTime", Double.NaN))
                        ?: words.last().endTimeMs
                    result.add(
                        SyllableLineModel(
                            index = lineIdx++,
                            startTimeMs = lStart,
                            endTimeMs = max(lStart + 150.0, lEnd),
                            words = words,
                            isDotLine = false,
                            isBgLine = isBgLine,
                            isDuoLine = oppositeAligned
                        )
                    )
                    prevLineEndMs = max(prevLineEndMs, lEnd)
                }
                continue
            }

            // Case C: Line-level (LRC) or Static/Plain item (`Text` / `Words` string / `Line` + optional `StartTime`/`EndTime`)
            val lineText = item.optString("Text", "")
                .ifBlank { item.optString("text", "") }
                .ifBlank { item.optString("Words", "") }
                .ifBlank { item.optString("words", "") }
                .ifBlank { item.optString("Line", "") }
                .trim()

            if (lineText.isNotEmpty()) {
                val rawStart = parseSpicyTimeMs(item.optDouble("StartTime", Double.NaN))
                    ?: parseSpicyTimeMs(item.optDouble("startTime", Double.NaN))
                    ?: parseSpicyTimeMs(item.optDouble("Time", Double.NaN))
                    ?: parseSpicyTimeMs(item.optDouble("time", Double.NaN))
                val rawEnd = parseSpicyTimeMs(item.optDouble("EndTime", Double.NaN))
                    ?: parseSpicyTimeMs(item.optDouble("endTime", Double.NaN))

                if (rawStart != null && !isDeclaredStatic) hasLineTimings = true
                pendingLineItems.add(
                    PendingLineItem(
                        text = lineText,
                        startMs = if (isDeclaredStatic) null else rawStart,
                        endMs = if (isDeclaredStatic) null else rawEnd,
                        isBg = item.optBoolean("IsBackground", false),
                        isDuo = oppositeAligned
                    )
                )
            }
        }

        // Convert any pending Line (LRC) or Plain Text items into SyllableLineModel rows
        // using the next line's StartTime as the current line's EndTime when EndTime was omitted!
        if (pendingLineItems.isNotEmpty()) {
            val anyTimed = pendingLineItems.any { it.startMs != null } && !isDeclaredStatic
            val fallbackStep = max(2200.0, trackDurationMs.toDouble() / pendingLineItems.size.coerceAtLeast(1))

            for (idx in pendingLineItems.indices) {
                val p = pendingLineItems[idx]
                val lStart = p.startMs ?: (idx * fallbackStep)
                val nextStart = pendingLineItems.getOrNull(idx + 1)?.startMs
                val lEnd = p.endMs ?: nextStart ?: (lStart + fallbackStep)
                val safeEnd = max(lStart + 250.0, lEnd)

                val tokens = p.text.split(Regex("\\s+")).filter { it.isNotBlank() }
                if (tokens.isEmpty()) continue

                val words = tokens.mapIndexed { wIdx, token ->
                    SyllableWordModel(
                        text = token,
                        startTimeMs = lStart,
                        endTimeMs = safeEnd,
                        isLastWordInLine = wIdx == tokens.lastIndex,
                        state = if (!anyTimed) ElementVocalState.Active else ElementVocalState.NotSung,
                        fillFraction = if (!anyTimed) 1f else 0f
                    )
                }

                result.add(
                    SyllableLineModel(
                        index = lineIdx++,
                        startTimeMs = lStart,
                        endTimeMs = safeEnd,
                        words = words,
                        isDotLine = false,
                        isBgLine = p.isBg,
                        isDuoLine = p.isDuo,
                        isLineSyncedOnly = true,
                        isPlainTextUnsynced = !anyTimed,
                        state = if (!anyTimed) ElementVocalState.Active else ElementVocalState.NotSung
                    )
                )
            }
        }

        val priority = when {
            isDeclaredStatic -> LyricsSyncPriority.NO_SYNC
            hasSyllableSplits -> LyricsSyncPriority.SYLLABLE
            hasWordTimings -> LyricsSyncPriority.WORD
            hasLineTimings -> LyricsSyncPriority.LINE
            else -> LyricsSyncPriority.NO_SYNC
        }
        return result to priority
    }

    private fun buildWordsFromSpicySyllables(
        syllablesArr: JSONArray,
        isBg: Boolean
    ): Pair<List<SyllableWordModel>, Boolean> {
        data class RawSyl(
            val text: String,
            val startMs: Double,
            val endMs: Double,
            val isPartOfWord: Boolean
        )

        val rawList = mutableListOf<RawSyl>()
        var sawPartOfWord = false

        for (i in 0 until syllablesArr.length()) {
            val sObj = syllablesArr.optJSONObject(i) ?: continue
            var txt = sObj.optString("Text", "").ifBlank { sObj.optString("text", "") }
            if (isBg) {
                txt = txt.removePrefix("(").removeSuffix(")")
            }
            if (txt.isEmpty()) continue
            val st = parseSpicyTimeMs(sObj.optDouble("StartTime", Double.NaN)) ?: 0.0
            val et = parseSpicyTimeMs(sObj.optDouble("EndTime", Double.NaN)) ?: (st + 250.0)
            val part = sObj.optBoolean("IsPartOfWord", false)
            if (part) sawPartOfWord = true
            rawList.add(RawSyl(txt, st, max(st + 30.0, et), part))
        }

        if (rawList.isEmpty()) return emptyList<SyllableWordModel>() to false

        val groupedWords = mutableListOf<List<RawSyl>>()
        var currentGroup = mutableListOf<RawSyl>()

        for (syl in rawList) {
            currentGroup.add(syl)
            if (!syl.isPartOfWord) {
                groupedWords.add(currentGroup)
                currentGroup = mutableListOf()
            }
        }
        if (currentGroup.isNotEmpty()) {
            groupedWords.add(currentGroup)
        }

        val wordModels = groupedWords.mapIndexed { idx, group ->
            val combinedText = group.joinToString("") { it.text }.trim()
            val wStart = group.first().startMs
            val wEnd = group.last().endMs
            val isLast = idx == groupedWords.lastIndex

            val letters = mutableListOf<SplitLetterModel>()
            for (syl in group) {
                val cleanSyl = syl.text.trim()
                if (cleanSyl.isEmpty()) continue
                val dur = max(20.0, syl.endMs - syl.startMs)
                val step = dur / cleanSyl.length
                cleanSyl.forEachIndexed { cIdx, ch ->
                    val ls = syl.startMs + cIdx * step
                    val le = if (cIdx == cleanSyl.lastIndex) syl.endMs else ls + step
                    letters.add(
                        SplitLetterModel(
                            charText = ch.toString(),
                            startTimeMs = ls,
                            endTimeMs = le
                        )
                    )
                }
            }

            SyllableWordModel(
                text = combinedText.ifEmpty { group.first().text },
                startTimeMs = wStart,
                endTimeMs = max(wStart + 40.0, wEnd),
                isDot = false,
                isBgWord = isBg,
                isLastWordInLine = isLast,
                letterGroup = group.size > 1 || (wEnd - wStart >= 650.0 && combinedText.length >= 3),
                letters = letters
            )
        }

        return wordModels to sawPartOfWord
    }

    private fun createInterludeDots(index: Int, startMs: Double, endMs: Double): SyllableLineModel {
        val total = max(600.0, endMs - startMs)
        val third = total / 3.0
        val dots = (0 until 3).map { d ->
            SyllableWordModel(
                text = "●",
                startTimeMs = startMs + d * third,
                endTimeMs = startMs + (d + 1) * third,
                isDot = true
            )
        }
        return SyllableLineModel(
            index = index,
            startTimeMs = startMs,
            endTimeMs = endMs,
            words = dots,
            isDotLine = true
        )
    }

    private fun parseSpicyTimeMs(value: Double): Double? {
        if (value.isNaN() || value < 0.0) return null
        return value * 1000.0
    }

    private fun extractLinkedIdsFromRawPayload(rawJson: String): List<String> {
        return try {
            val root = JSONObject(rawJson)
            val body = root.optJSONObject("Body") ?: return emptyList()
            val idsObj = body.optJSONObject("ids") ?: return emptyList()
            listOfNotNull(
                idsObj.optString("spotify").takeIf { it.isNotBlank() },
                idsObj.optString("apple_music").takeIf { it.isNotBlank() },
                idsObj.optString("isrc").takeIf { it.isNotBlank() }
            )
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun cleanTrackTitleForSearch(rawTitle: String): String {
        return rawTitle
            .replace(Regex("^\\d+[.\\-_\\s]+"), "")
            .replace(Regex("\\((?:Official|Lyric|Audio|Video|Visualizer|HD|4K|Remaster[^)]*)\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\[(?:Official|FLAC|MP3|320|Lossless[^]]*)]", RegexOption.IGNORE_CASE), "")
            .trim()
            .ifEmpty { rawTitle }
    }

    private fun tierScore(tier: String): Int {
        return when (tier.lowercase()) {
            "exact" -> 5
            "likely" -> 4
            "version" -> 3
            "other_artist" -> 2
            "related" -> 1
            else -> 0
        }
    }

    private fun encode(s: String): String = URLEncoder.encode(s, "UTF-8")
}
