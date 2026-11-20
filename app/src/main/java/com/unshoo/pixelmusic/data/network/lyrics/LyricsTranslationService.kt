package com.unshoo.pixelmusic.data.network.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import timber.log.Timber
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LyricsTranslationService @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private val cache = ConcurrentHashMap<String, String>()

    suspend fun translateLine(text: String, targetLang: String = "ru"): String? = withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return@withContext null
        if (trimmed.any { it in '\u0400'..'\u04FF' }) return@withContext null // already cyrillic

        cache[trimmed]?.let { return@withContext it }

        try {
            val encoded = URLEncoder.encode(trimmed, "UTF-8")
            val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$targetLang&dt=t&q=$encoded"
            val req = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()

            val resp = okHttpClient.newCall(req).execute()
            if (resp.isSuccessful) {
                val body = resp.body.string()
                val json = JSONArray(body)
                val parts = json.optJSONArray(0)
                if (parts != null) {
                    val sb = StringBuilder()
                    for (i in 0 until parts.length()) {
                        val part = parts.optJSONArray(i)
                        if (part != null) {
                            sb.append(part.optString(0, ""))
                        }
                    }
                    val translation = sb.toString().trim()
                    if (translation.isNotBlank()) {
                        cache[trimmed] = translation
                        return@withContext translation
                    }
                }
            }
        } catch (e: Exception) {
            Timber.w("LyricsTranslationService error: ${e.message}")
        }
        null
    }

    suspend fun translateLinesBatch(lines: List<String>, targetLang: String = "ru"): Map<String, String> = withContext(Dispatchers.IO) {
        val results = mutableMapOf<String, String>()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isNotBlank() && !trimmed.any { it in '\u0400'..'\u04FF' }) {
                translateLine(trimmed, targetLang)?.let {
                    results[trimmed] = it
                }
            }
        }
        results
    }
}
