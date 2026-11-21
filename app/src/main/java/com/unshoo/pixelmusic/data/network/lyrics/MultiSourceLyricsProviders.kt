package com.unshoo.pixelmusic.data.network.lyrics

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.net.URLEncoder
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

object LyricsMatchingHelper {
    private val NOISE_REGEX = Regex(
        """\s*[\(\[][^\)\]]*\b(official|video|audio|lyrics?|visuali[sz]er|remaster(ed)?|hd|hq|4k|mv|feat|ft|prod|edit|mix|version|cover|клип|премьера|караоке|текст\s*песни|минус(овка)?|инструментал|кавер|remix|rmx|sped\s*up|speed\s*up|slowed|reverb|nightcore|hardstyle|phonk|bootleg|mashup|ремикс|hoodtrap|drill|trap)\b[^\)\]]*[\)\]]""",
        RegexOption.IGNORE_CASE
    )
    private val REWORKED_TAIL_REGEX = Regex(
        """\s*[-–—+]?\s*\b(remix|rmx|sped\s*up|speed\s*up|slowed|reverb|nightcore|hardstyle|phonk|bootleg|mashup|ремикс|hoodtrap|drill|trap|edit)\b.*$""",
        RegexOption.IGNORE_CASE
    )
    private val PIPE_NOISE_REGEX = Regex("""\s*[|/•–—]\s*(текст\s*песни|караоке|lyrics?|клип|премьера|audio|разбор|кавер|cover|official).*$""", RegexOption.IGNORE_CASE)
    private val BRACKETS_REGEX = Regex("""[\(\[].*?[\)\]]""")
    private val NON_ALPHANUM_REGEX = Regex("""[^a-zA-Z0-9\u0400-\u04FF]""")

    fun cleanNoise(str: String): String {
        return str
            .replace(NOISE_REGEX, "")
            .replace(PIPE_NOISE_REGEX, "")
            .replace(REWORKED_TAIL_REGEX, "")
            .replace(BRACKETS_REGEX, "")
            .trim()
    }

    fun plain(str: String): String {
        return str.lowercase(Locale.ROOT)
            .replace('ё', 'е')
            .replace("weekend", "weeknd")
            .replace("timless", "timeless")
            .replace("playboy", "playboi")
            .replace(NON_ALPHANUM_REGEX, "")
    }

    fun fitsCandidate(
        candTitle: String,
        candArtists: List<String>,
        candDurationSec: Double,
        targetSongTitle: String,
        targetArtist: String,
        targetDurationSec: Double
    ): Boolean {
        val pTargetTitle = plain(cleanNoise(targetSongTitle))
        val pCandTitle = plain(cleanNoise(candTitle))

        if (pTargetTitle.isEmpty() || pCandTitle.isEmpty()) return false

        val titleMatches = pTargetTitle == pCandTitle ||
                pCandTitle.startsWith(pTargetTitle) ||
                pTargetTitle.startsWith(pCandTitle) ||
                pCandTitle.contains(pTargetTitle) ||
                pTargetTitle.contains(pCandTitle)

        if (!titleMatches) return false

        // Check duration (tolerance relaxed to 45.0s for online and live music videos)
        if (targetDurationSec > 0 && candDurationSec > 0) {
            val diff = abs(targetDurationSec - candDurationSec)
            if (diff > 45.0) return false
        }

        val pTargetArtist = plain(targetArtist)
        if (pTargetArtist.isNotEmpty() && candArtists.isNotEmpty()) {
            val candCombined = candArtists.joinToString("") { plain(it) }
            var artistMatched = false

            for (a in candArtists) {
                val pa = plain(a)
                if (pa.isNotEmpty() && (pTargetArtist.contains(pa) || pa.contains(pTargetArtist))) {
                    artistMatched = true
                    break
                }
            }

            if (!artistMatched && candCombined.isNotEmpty()) {
                if (pTargetArtist.contains(candCombined) || candCombined.contains(pTargetArtist)) {
                    artistMatched = true
                }
            }

            // Word-level check (e.g. "индия")
            if (!artistMatched) {
                val words = targetArtist.lowercase(Locale.ROOT)
                    .replace('ё', 'е')
                    .split(NON_ALPHANUM_REGEX)
                    .filter { it.length > 2 }

                for (w in words) {
                    if (candCombined.contains(w)) {
                        artistMatched = true
                        break
                    }
                }
            }

            if (!artistMatched && targetDurationSec > 0 && candDurationSec > 0) {
                val diff = abs(targetDurationSec - candDurationSec)
                if (diff <= 3.5) {
                    artistMatched = true
                }
            }

            return artistMatched
        }

        return true
    }
}

data class ExternalLyricsResult(
    val provider: String,
    val lyrics: String,
    val title: String = "",
    val artist: String = ""
)

@Singleton
class MultiSourceLyricsProviders @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "MultiLyrics"
    }

    /**
     * Attempts to fetch synced lyrics from multiple fallback providers in order:
     * 1. LRCLIB General Search (flexible fuzzy query)
     * 2. QQ Music (Tencent QQ Music with instant synced LRC)
     * 3. lrc.red (Apple Music synced timings)
     * 4. NetEase Cloud Music (Massive catalog, great for Russian/Pop/Asian)
     * 5. KuGou (Extensive Chinese and international catalog)
     */
    suspend fun fetchLyricsWaterfall(
        title: String,
        artist: String,
        durationMs: Long
    ): ExternalLyricsResult? = withContext(Dispatchers.IO) {
        val targetDurationSec = durationMs / 1000.0
        val cleanArtist = artist.trim()
            .replace(Regex("""\bweekend\b""", RegexOption.IGNORE_CASE), "Weeknd")
            .replace(Regex("""\bplayboy\s*carti\b""", RegexOption.IGNORE_CASE), "Playboi Carti")
        val cleanTitle = LyricsMatchingHelper.cleanNoise(title)
            .replace(Regex("""\btimless\b""", RegexOption.IGNORE_CASE), "Timeless")
            .ifBlank { title.trim() }

        // 1. Try LRCLIB General Search
        try {
            fetchFromLrcLibGeneral(cleanTitle, cleanArtist, targetDurationSec)?.let {
                Timber.tag(TAG).d("Found lyrics from LRCLIB general for $cleanArtist - $cleanTitle")
                return@withContext it
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w("LRCLIB general search error: ${e.message}")
        }

        // 2. Try QQ Music (Tencent QQ Music)
        try {
            fetchFromQQMusic(cleanTitle, cleanArtist, targetDurationSec)?.let {
                Timber.tag(TAG).d("Found lyrics from QQ Music for $cleanArtist - $cleanTitle")
                return@withContext it
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w("QQ Music error: ${e.message}")
        }

        // 3. Try lrc.red
        try {
            fetchFromLrcRed(cleanTitle, cleanArtist, targetDurationSec)?.let {
                Timber.tag(TAG).d("Found lyrics from lrc.red for $cleanArtist - $cleanTitle")
                return@withContext it
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w("lrc.red error: ${e.message}")
        }

        // 4. Try NetEase
        try {
            fetchFromNetEase(cleanTitle, cleanArtist, targetDurationSec)?.let {
                Timber.tag(TAG).d("Found lyrics from NetEase for $cleanArtist - $cleanTitle")
                return@withContext it
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w("NetEase error: ${e.message}")
        }

        // 5. Try KuGou
        try {
            fetchFromKuGou(cleanTitle, cleanArtist, targetDurationSec)?.let {
                Timber.tag(TAG).d("Found lyrics from KuGou for $cleanArtist - $cleanTitle")
                return@withContext it
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w("KuGou error: ${e.message}")
        }

        // If title contains hyphen " - ", try swapping parts
        if (cleanTitle.contains(" - ") || cleanTitle.contains(" — ")) {
            val delim = if (cleanTitle.contains(" - ")) " - " else " — "
            val parts = cleanTitle.split(delim, limit = 2)
            val p0 = parts[0].trim()
            val p1 = parts[1].trim()
            try {
                fetchFromLrcLibGeneral(p1, p0, targetDurationSec)?.let { return@withContext it }
                fetchFromQQMusic(p1, p0, targetDurationSec)?.let { return@withContext it }
            } catch (_: Exception) {}
        }

        null
    }

    suspend fun fetchFromQQMusic(
        title: String,
        artist: String,
        targetDurationSec: Double
    ): ExternalLyricsResult? = withContext(Dispatchers.IO) {
        val firstArtist = artist.split(',', '&', '/', ';').firstOrNull()?.trim() ?: artist
        val query = if (firstArtist.isNotBlank() && !firstArtist.equals("YouTube Music", ignoreCase = true)) {
            "$firstArtist $title"
        } else title
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val searchUrl = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp?w=$encodedQuery&n=5&format=json"

        val request = Request.Builder()
            .url(searchUrl)
            .addHeader("Referer", "https://y.qq.com")
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .build()

        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) return@withContext null
        val bodyStr = response.body.string()

        val root = JSONObject(bodyStr)
        val dataObj = root.optJSONObject("data") ?: return@withContext null
        val songObj = dataObj.optJSONObject("song") ?: return@withContext null
        val listArr = songObj.optJSONArray("list") ?: return@withContext null

        for (i in 0 until listArr.length()) {
            val item = listArr.optJSONObject(i) ?: continue
            val songmid = item.optString("songmid", "")
            val songname = item.optString("songname", "")
            val singerArr = item.optJSONArray("singer")
            val artists = mutableListOf<String>()
            if (singerArr != null) {
                for (j in 0 until singerArr.length()) {
                    singerArr.optJSONObject(j)?.optString("name", "")?.let {
                        if (it.isNotBlank()) artists.add(it)
                    }
                }
            }
            val interval = item.optDouble("interval", 0.0)

            if (songmid.isBlank()) continue

            if (LyricsMatchingHelper.fitsCandidate(
                    candTitle = songname,
                    candArtists = artists,
                    candDurationSec = interval,
                    targetSongTitle = title,
                    targetArtist = artist,
                    targetDurationSec = targetDurationSec
                )
            ) {
                val lyricUrl = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg?songmid=$songmid&format=json&nobase64=1"
                val lyricReq = Request.Builder()
                    .url(lyricUrl)
                    .addHeader("Referer", "https://y.qq.com")
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build()

                val lyricResp = okHttpClient.newCall(lyricReq).execute()
                if (lyricResp.isSuccessful) {
                    val lyricBody = lyricResp.body.string()
                    val lyricJson = JSONObject(lyricBody)
                    val rawLrc = lyricJson.optString("lyric", "")
                    if (rawLrc.isNotBlank()) {
                        val decoded = rawLrc.replace("&apos;", "'")
                            .replace("&quot;", "\"")
                            .replace("&amp;", "&")
                            .replace("&lt;", "<")
                            .replace("&gt;", ">")
                        return@withContext ExternalLyricsResult(
                            provider = "QQMusic",
                            lyrics = decoded,
                            title = songname,
                            artist = artists.joinToString(", ")
                        )
                    }
                }
            }
        }
        null
    }

    suspend fun fetchFromLrcLibGeneral(
        title: String,
        artist: String,
        targetDurationSec: Double
    ): ExternalLyricsResult? = withContext(Dispatchers.IO) {
        val cleanArtist = if (artist.isNotBlank() && !artist.equals("YouTube Music", ignoreCase = true)) {
            artist.split(',', '&', '/', ';').firstOrNull()?.trim() ?: artist
        } else ""

        // 1. Try exact get if artist is present
        if (cleanArtist.isNotBlank()) {
            try {
                val encT = URLEncoder.encode(title, "UTF-8")
                val encA = URLEncoder.encode(cleanArtist, "UTF-8")
                val getUrl = "https://lrclib.net/api/get?track_name=$encT&artist_name=$encA"
                val getReq = Request.Builder()
                    .url(getUrl)
                    .addHeader("User-Agent", "PixelMusic/1.8.11")
                    .build()
                val getResp = okHttpClient.newCall(getReq).execute()
                if (getResp.isSuccessful) {
                    val getBody = getResp.body.string()
                    val obj = org.json.JSONObject(getBody)
                    val synced = obj.optString("syncedLyrics", "")
                    val plain = obj.optString("plainLyrics", "")
                    val lyrics = if (synced.isNotBlank()) synced else plain
                    if (lyrics.isNotBlank()) {
                        Timber.tag(TAG).d("Found lyrics from LRCLIB exact get for $cleanArtist - $title")
                        return@withContext ExternalLyricsResult(
                            provider = "lrclib",
                            lyrics = lyrics,
                            title = obj.optString("trackName", title),
                            artist = obj.optString("artistName", cleanArtist)
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.tag(TAG).d("LRCLIB exact get error for $title: ${e.message}")
            }
        }

        val query = if (cleanArtist.isNotBlank()) "$cleanArtist $title" else title
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val searchUrl = "https://lrclib.net/api/search?q=$encodedQuery"

        val request = Request.Builder()
            .url(searchUrl)
            .addHeader("User-Agent", "PixelMusic/1.8.11")
            .build()

        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) return@withContext null
        val bodyStr = response.body.string()

        val arr = org.json.JSONArray(bodyStr)
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val candName = item.optString("name", "")
            val candArtist = item.optString("artistName", "")
            val candDuration = item.optDouble("duration", 0.0)
            val synced = item.optString("syncedLyrics", "")
            val plain = item.optString("plainLyrics", "")

            val lyrics = if (synced.isNotBlank()) synced else plain
            if (lyrics.isBlank()) continue

            if (LyricsMatchingHelper.fitsCandidate(
                    candTitle = candName,
                    candArtists = listOf(candArtist),
                    candDurationSec = candDuration,
                    targetSongTitle = title,
                    targetArtist = artist,
                    targetDurationSec = targetDurationSec
                )
            ) {
                return@withContext ExternalLyricsResult(
                    provider = "lrclib",
                    lyrics = lyrics,
                    title = candName,
                    artist = candArtist
                )
            }
        }
        null
    }

    suspend fun fetchFromLrcRed(
        title: String,
        artist: String,
        targetDurationSec: Double
    ): ExternalLyricsResult? = withContext(Dispatchers.IO) {
        val firstArtist = artist.split(',', '&', '/', ';').firstOrNull()?.trim() ?: artist
        val query = if (firstArtist.isNotBlank()) "$firstArtist $title" else title
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val searchUrl = "https://lrc.red/search.json?q=$encodedQuery"

        val request = Request.Builder()
            .url(searchUrl)
            .addHeader("User-Agent", "PixelMusic/1.8")
            .addHeader("X-Client-ID", "PlasmusicLyrics/1.0")
            .build()

        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) return@withContext null
        val bodyStr = response.body.string()

        val root = JSONObject(bodyStr)
        val hits = root.optJSONArray("hits") ?: return@withContext null

        for (i in 0 until hits.length()) {
            val hit = hits.optJSONObject(i) ?: continue
            val candTitle = hit.optString("title", "")
            val candArtist = hit.optString("artist", "")
            val candDuration = hit.optDouble("duration", 0.0)
            val isrc = hit.optString("isrc", "")

            if (isrc.isBlank()) continue

            if (LyricsMatchingHelper.fitsCandidate(
                    candTitle = candTitle,
                    candArtists = listOf(candArtist),
                    candDurationSec = candDuration,
                    targetSongTitle = title,
                    targetArtist = artist,
                    targetDurationSec = targetDurationSec
                )
            ) {
                // Fetch .lrc directly
                val lrcUrl = "https://lrc.red/s/$isrc.lrc"
                val lrcReq = Request.Builder()
                    .url(lrcUrl)
                    .addHeader("User-Agent", "PixelMusic/1.8")
                    .addHeader("X-Client-ID", "PlasmusicLyrics/1.0")
                    .build()

                val lrcResp = okHttpClient.newCall(lrcReq).execute()
                if (lrcResp.isSuccessful) {
                    val lrcText = lrcResp.body.string()
                    if (lrcText.isNotBlank()) {
                        return@withContext ExternalLyricsResult(
                            provider = "lrc.red",
                            lyrics = lrcText,
                            title = candTitle,
                            artist = candArtist
                        )
                    }
                }
            }
        }
        null
    }

    suspend fun fetchFromNetEase(
        title: String,
        artist: String,
        targetDurationSec: Double
    ): ExternalLyricsResult? = withContext(Dispatchers.IO) {
        val firstArtist = artist.split(',', '&', '/', ';').firstOrNull()?.trim() ?: artist
        val query = if (firstArtist.isNotBlank()) "$firstArtist $title" else title
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val searchUrl = "https://music.163.com/api/cloudsearch/pc?type=1&limit=8&s=$encodedQuery"

        val request = Request.Builder()
            .url(searchUrl)
            .addHeader("Referer", "https://music.163.com/")
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .build()

        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) return@withContext null
        val bodyStr = response.body.string()

        val root = JSONObject(bodyStr)
        val result = root.optJSONObject("result") ?: return@withContext null
        val songs = result.optJSONArray("songs") ?: return@withContext null

        for (i in 0 until songs.length()) {
            val s = songs.optJSONObject(i) ?: continue
            val songId = s.optLong("id", 0L)
            if (songId <= 0L) continue

            val sTitle = s.optString("name", "")
            val sDurSec = s.optDouble("dt", 0.0) / 1000.0
            val arArray = s.optJSONArray("ar")
            val artists = mutableListOf<String>()
            if (arArray != null) {
                for (j in 0 until arArray.length()) {
                    artists.add(arArray.optJSONObject(j)?.optString("name", "") ?: "")
                }
            }

            if (LyricsMatchingHelper.fitsCandidate(
                    candTitle = sTitle,
                    candArtists = artists,
                    candDurationSec = sDurSec,
                    targetSongTitle = title,
                    targetArtist = artist,
                    targetDurationSec = targetDurationSec
                )
            ) {
                // Fetch lyric
                val lyricUrl = "https://music.163.com/api/song/lyric?lv=-1&kv=-1&tv=-1&id=$songId"
                val lyricReq = Request.Builder()
                    .url(lyricUrl)
                    .addHeader("Referer", "https://music.163.com/")
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build()

                val lyricResp = okHttpClient.newCall(lyricReq).execute()
                if (lyricResp.isSuccessful) {
                    val lyricBody = lyricResp.body.string()
                    if (lyricBody.isNotBlank()) {
                        val lyricJson = JSONObject(lyricBody)
                        val lrcText = lyricJson.optJSONObject("lrc")?.optString("lyric", "")
                        if (!lrcText.isNullOrBlank()) {
                            return@withContext ExternalLyricsResult(
                                provider = "NetEase",
                                lyrics = lrcText,
                                title = sTitle,
                                artist = artists.joinToString(", ")
                            )
                        }
                    }
                }
            }
        }
        null
    }

    suspend fun fetchFromKuGou(
        title: String,
        artist: String,
        targetDurationSec: Double
    ): ExternalLyricsResult? = withContext(Dispatchers.IO) {
        val firstArtist = artist.split(',', '&', '/', ';').firstOrNull()?.trim() ?: artist
        val query = if (firstArtist.isNotBlank()) "$firstArtist $title" else title
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val searchUrl = "https://songsearch.kugou.com/song_search_v2?keyword=$encodedQuery&page=1&pagesize=5&clientver=&platform=WebFilter"

        val request = Request.Builder()
            .url(searchUrl)
            .addHeader("User-Agent", "Mozilla/5.0")
            .build()

        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) return@withContext null
        val bodyStr = response.body.string()

        val root = JSONObject(bodyStr)
        val dataObj = root.optJSONObject("data") ?: return@withContext null
        val lists = dataObj.optJSONArray("lists") ?: return@withContext null

        for (i in 0 until lists.length()) {
            val item = lists.optJSONObject(i) ?: continue
            val songName = item.optString("SongName", "")
            val singerName = item.optString("SingerName", "")
            val fileHash = item.optString("FileHash", "")
            val duration = item.optDouble("Duration", 0.0)

            if (fileHash.isBlank()) continue

            if (LyricsMatchingHelper.fitsCandidate(
                    candTitle = songName,
                    candArtists = listOf(singerName),
                    candDurationSec = duration,
                    targetSongTitle = title,
                    targetArtist = artist,
                    targetDurationSec = targetDurationSec
                )
            ) {
                // Step 2: query krcs
                val krcUrl = "https://krcs.kugou.com/search?ver=1&man=yes&client=mobi&keyword=&duration=${duration.toInt()}&hash=$fileHash"
                val krcReq = Request.Builder().url(krcUrl).addHeader("User-Agent", "Mozilla/5.0").build()
                val krcResp = okHttpClient.newCall(krcReq).execute()
                if (!krcResp.isSuccessful) continue
                val krcBody = krcResp.body.string()
                val krcJson = JSONObject(krcBody)
                val candidates = krcJson.optJSONArray("candidates") ?: continue
                if (candidates.length() == 0) continue

                val cand0 = candidates.optJSONObject(0) ?: continue
                val cid = cand0.optString("id", "")
                val ckey = cand0.optString("accesskey", "")
                if (cid.isBlank() || ckey.isBlank()) continue

                // Step 3: download lrc
                val dlUrl = "https://lyrics.kugou.com/download?ver=1&client=pc&id=$cid&accesskey=$ckey&fmt=lrc&charset=utf8"
                val dlReq = Request.Builder().url(dlUrl).addHeader("User-Agent", "Mozilla/5.0").build()
                val dlResp = okHttpClient.newCall(dlReq).execute()
                if (!dlResp.isSuccessful) continue
                val dlBody = dlResp.body.string()
                val dlJson = JSONObject(dlBody)
                val base64Content = dlJson.optString("content", "")
                if (base64Content.isNotBlank()) {
                    val decodedBytes = Base64.decode(base64Content, Base64.DEFAULT)
                    val lrcText = String(decodedBytes, Charsets.UTF_8)
                    if (lrcText.isNotBlank()) {
                        return@withContext ExternalLyricsResult(
                            provider = "KuGou",
                            lyrics = lrcText,
                            title = songName,
                            artist = singerName
                        )
                    }
                }
            }
        }
        null
    }
}
