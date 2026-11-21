package com.unshoo.pixelmusic.data.network.artwork

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * High-resolution studio album artwork resolver.
 * Fetches pristine square studio album artwork (up to 1400x1400px) from Apple Music / iTunes Store
 * and Deezer APIs, replacing video frames, 16:9 thumbnails, and lyric video stills.
 */
@Singleton
class StudioArtworkResolver @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "StudioArtwork"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }

    private val client: OkHttpClient = okHttpClient.newBuilder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()

    private val cache = ConcurrentHashMap<String, String>()

    /**
     * Resolves pristine, high-resolution official studio album artwork (1400x1400px).
     * Priority:
     * 1. Apple Music / iTunes Store API (1400x1400px master artwork)
     * 2. Deezer API (1000x1000px studio album art)
     * 3. Fallback to provided artwork URL
     */
    suspend fun resolveStudioArtwork(
        title: String,
        artist: String,
        fallbackUrl: String? = null
    ): String = withContext(Dispatchers.IO) {
        val cleanT = cleanSearchTerm(title)
        val cleanA = cleanSearchTerm(artist.split(',', '&', '/').firstOrNull() ?: artist)

        if (cleanT.isBlank()) return@withContext fallbackUrl.orEmpty()

        val cacheKey = "${cleanA.lowercase()}_${cleanT.lowercase()}"
        cache[cacheKey]?.let { return@withContext it }

        val query = "$cleanA $cleanT".trim()

        // Tier 1: Apple Music / iTunes Store API (1400x1400px master square artwork)
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val itunesUrl = "https://itunes.apple.com/search?term=$encodedQuery&entity=song&limit=3"
            val request = Request.Builder()
                .url(itunesUrl)
                .header("User-Agent", USER_AGENT)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body.string()
                val json = JsonParser.parseString(body).asJsonObject
                val results = json.getAsJsonArray("results")
                if (results != null && results.size() > 0) {
                    val normCleanT = cleanT.lowercase().replace(Regex("""[^a-zA-Z0-9\u0400-\u04FF]"""), "")
                    for (i in 0 until results.size()) {
                        val item = results.get(i).asJsonObject
                        val trackName = item.get("trackName")?.asString.orEmpty()
                        val normTrackName = trackName.lowercase().replace(Regex("""[^a-zA-Z0-9\u0400-\u04FF]"""), "")
                        val trackMatches = normCleanT.isEmpty() || normTrackName.contains(normCleanT) || normCleanT.contains(normTrackName)
                        if (trackMatches) {
                            val rawArt = item.get("artworkUrl100")?.asString
                            if (!rawArt.isNullOrBlank()) {
                                val highRes = rawArt.replace("100x100bb", "1400x1400bb")
                                cache[cacheKey] = highRes
                                Timber.i("[$TAG] Resolved Apple Music 1400x1400 studio artwork for '$cleanT' by '$cleanA': $highRes")
                                return@withContext highRes
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.d(e, "[$TAG] iTunes artwork search failed for $cleanT")
        }

        // Tier 2: Yandex Music Open API (1000x1000px official album artwork for Russian & international tracks)
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val yandexUrl = "https://api.music.yandex.net/search?text=$encodedQuery&type=track&page=0"
            val request = Request.Builder()
                .url(yandexUrl)
                .header("User-Agent", "Yandex-Music-API")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body.string()
                val json = JsonParser.parseString(body).asJsonObject
                val tracks = json.getAsJsonObject("result")?.getAsJsonObject("tracks")?.getAsJsonArray("results")
                if (tracks != null && tracks.size() > 0) {
                    val normCleanT = cleanT.lowercase().replace(Regex("""[^a-zA-Z0-9\u0400-\u04FF]"""), "")
                    for (i in 0 until tracks.size()) {
                        val item = tracks.get(i).asJsonObject
                        val candTitle = item.get("title")?.asString.orEmpty()
                        val normCandT = candTitle.lowercase().replace(Regex("""[^a-zA-Z0-9\u0400-\u04FF]"""), "")
                        val trackMatches = normCleanT.isEmpty() || normCandT.contains(normCleanT) || normCleanT.contains(normCandT)
                        if (trackMatches) {
                            val coverUri = item.get("coverUri")?.asString
                            if (!coverUri.isNullOrBlank()) {
                                val highRes = "https://" + coverUri.replace("%%", "1000x1000")
                                cache[cacheKey] = highRes
                                Timber.i("[$TAG] Resolved Yandex Music 1000x1000 studio artwork for '$cleanT' by '$cleanA': $highRes")
                                return@withContext highRes
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.d(e, "[$TAG] Yandex Music artwork search failed for $cleanT")
        }

        // Tier 3: Deezer API (1000x1000px studio cover)
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val deezerUrl = "https://api.deezer.com/search?q=$encodedQuery&limit=3"
            val request = Request.Builder()
                .url(deezerUrl)
                .header("User-Agent", USER_AGENT)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body.string()
                val json = JsonParser.parseString(body).asJsonObject
                val data = json.getAsJsonArray("data")
                if (data != null && data.size() > 0) {
                    val normCleanT = cleanT.lowercase().replace(Regex("""[^a-zA-Z0-9\u0400-\u04FF]"""), "")
                    for (i in 0 until data.size()) {
                        val item = data.get(i).asJsonObject
                        val candTitle = item.get("title")?.asString.orEmpty()
                        val normCandT = candTitle.lowercase().replace(Regex("""[^a-zA-Z0-9\u0400-\u04FF]"""), "")
                        val trackMatches = normCleanT.isEmpty() || normCandT.contains(normCleanT) || normCleanT.contains(normCandT)
                        if (trackMatches) {
                            val album = item.getAsJsonObject("album")
                            val coverXl = album?.get("cover_xl")?.asString ?: album?.get("cover_big")?.asString
                            if (!coverXl.isNullOrBlank()) {
                                cache[cacheKey] = coverXl
                                Timber.i("[$TAG] Resolved Deezer 1000x1000 studio artwork for '$cleanT' by '$cleanA': $coverXl")
                                return@withContext coverXl
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.d(e, "[$TAG] Deezer artwork search failed for $cleanT")
        }

        fallbackUrl.orEmpty()
    }

    private fun cleanSearchTerm(str: String): String =
        str.replace(Regex("""\s*[\(\[][^\)\]]*\b(official|video|audio|lyrics?|visuali[sz]er|hd|hq|4k|mv|feat|ft|prod|clip|клип|премьера|караоке|текст\s*песни|минус(овка)?|инструментал|кавер|cover)\b[^\)\]]*[\)\]]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*[-–—+|/]\s*(remix|rmx|sped\s*up|speed\s*up|slowed|reverb|nightcore|karaoke|караоке|instrumental|инструментал|текст\s*песни|минус(овка)?|кавер|cover).*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*[|/•–—]\s*(текст\s*песни|караоке|lyrics?|клип|премьера|audio|разбор).*$""", RegexOption.IGNORE_CASE), "")
            .trim()
}
