package com.unshoo.pixelmusic.data.network.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Detects intro offset in milliseconds between playing audio (e.g. YouTube music video with intro skit)
 * and studio lyrics (timed to the clean album audio release).
 *
 * Employs a dual-tier strategy:
 * 1. SponsorBlock Crowdsourced Segments: Looks for "music_offtopic" segments beginning at timestamp 0..2s.
 * 2. Duration Delta Heuristics: Compares playing audio duration with studio lyrics duration.
 *    If the video is 3.5s to 55s longer than the studio master, the delta is the intro delay.
 */
@Singleton
class LyricsIntroOffsetDetector @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "LyricsIntroOffset"
    }

    private val fastClient = okHttpClient.newBuilder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    // Cache offsets by videoId or songId to prevent duplicate network calls
    private val offsetCache = ConcurrentHashMap<String, Long>()

    /**
     * Calculates the intro offset in milliseconds that must be added to lyric timestamps
     * so that lines and karaoke words align with the playing audio.
     */
    suspend fun detectIntroOffsetMs(
        audioDurationMs: Long,
        lyricsDurationMs: Long,
        videoId: String?
    ): Long = withContext(Dispatchers.IO) {
        val cacheKey = videoId ?: "${audioDurationMs}_${lyricsDurationMs}"
        offsetCache[cacheKey]?.let { return@withContext it }

        // Tier 1: Query SponsorBlock for verified music_offtopic segment at start of video
        if (!videoId.isNullOrBlank() && videoId.length in 10..12) {
            try {
                val sbOffset = fetchSponsorBlockIntroMs(videoId)
                if (sbOffset >= 2000L) {
                    Timber.tag(TAG).i("SponsorBlock intro detected for $videoId: ${sbOffset}ms")
                    offsetCache[cacheKey] = sbOffset
                    return@withContext sbOffset
                }
            } catch (e: Exception) {
                Timber.tag(TAG).d("SponsorBlock lookup failed for $videoId: ${e.message}")
            }
        }

        // Tier 2: Duration delta between playing track and studio lyrics
        // Music videos almost always append 3.5s to 55s intro dialogue/acting before music starts.
        if (audioDurationMs > 0 && lyricsDurationMs > 0) {
            val deltaMs = audioDurationMs - lyricsDurationMs
            val deltaSec = deltaMs / 1000.0
            if (deltaSec in 3.5..55.0) {
                Timber.tag(TAG).i(
                    "Duration-based intro offset detected: ${deltaMs}ms (audio: ${audioDurationMs}ms, studio lyrics: ${lyricsDurationMs}ms)"
                )
                offsetCache[cacheKey] = deltaMs
                return@withContext deltaMs
            }
        }

        0L
    }

    private fun fetchSponsorBlockIntroMs(videoId: String): Long {
        val url = "https://sponsor.ajay.app/api/skipSegments?videoID=$videoId&categories=[\"music_offtopic\"]"
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .build()

        val response = fastClient.newCall(request).execute()
        if (!response.isSuccessful) return 0L
        val body = response.body.string()
        if (body.isBlank()) return 0L

        val arr = JSONArray(body)
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val category = item.optString("category", "")
            if (category == "music_offtopic") {
                val segment = item.optJSONArray("segment") ?: continue
                val start = segment.optDouble(0, -1.0)
                val end = segment.optDouble(1, -1.0)
                // If segment begins within the first 2.5 seconds, it represents an intro skit/dialogue
                if (start in 0.0..2.5 && end > start + 1.0) {
                    return ((end - start) * 1000).toLong()
                }
            }
        }
        return 0L
    }
}
