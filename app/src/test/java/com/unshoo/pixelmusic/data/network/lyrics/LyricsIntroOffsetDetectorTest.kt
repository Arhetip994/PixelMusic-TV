package com.unshoo.pixelmusic.data.network.lyrics

import com.unshoo.pixelmusic.data.model.Lyrics
import com.unshoo.pixelmusic.data.model.SyncedLine
import com.unshoo.pixelmusic.data.model.SyncedWord
import com.unshoo.pixelmusic.utils.LyricsUtils
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LyricsIntroOffsetDetectorTest {

    @Test
    fun detectIntroOffsetMs_detectsDurationDeltaAccurately() = runTest {
        val detector = LyricsIntroOffsetDetector(mockk<OkHttpClient>(relaxed = true))

        // Music video: 139.143s (139143ms), studio lyrics: 124.0s (124000ms) -> delta 15143ms
        val offsetMs = detector.detectIntroOffsetMs(
            audioDurationMs = 139_143L,
            lyricsDurationMs = 124_000L,
            videoId = null
        )

        assertEquals(15_143L, offsetMs)
    }

    @Test
    fun detectIntroOffsetMs_returnsZeroWhenDurationsMatch() = runTest {
        val detector = LyricsIntroOffsetDetector(mockk<OkHttpClient>(relaxed = true))

        val offsetMs = detector.detectIntroOffsetMs(
            audioDurationMs = 124_000L,
            lyricsDurationMs = 124_000L,
            videoId = null
        )

        assertEquals(0L, offsetMs)
    }

    @Test
    fun detectIntroOffsetMs_returnsZeroWhenDifferenceTooSmall() = runTest {
        val detector = LyricsIntroOffsetDetector(mockk<OkHttpClient>(relaxed = true))

        // 1.5s difference is standard trailing silence variation, not an intro skit
        val offsetMs = detector.detectIntroOffsetMs(
            audioDurationMs = 125_500L,
            lyricsDurationMs = 124_000L,
            videoId = null
        )

        assertEquals(0L, offsetMs)
    }

    @Test
    fun applyOffsetToLrc_shiftsTimestampsAccurately() {
        val originalLrc = """
            [00:02.41] "Любовь и Ракеты"
            [00:07.28] Yeah-yeah, фо, фо, фо
            [00:10.20] Плохая сука, она в меня влюбилась
        """.trimIndent()

        // 15 seconds intro offset (15000ms)
        val shiftedLrc = LyricsUtils.applyOffsetToLrc(originalLrc, 15_000L, tagOffset = true)

        assertTrue(shiftedLrc.contains("[intro_offset:15000]"))
        assertTrue(shiftedLrc.contains("[00:17.41] \"Любовь и Ракеты\""))
        assertTrue(shiftedLrc.contains("[00:22.28] Yeah-yeah, фо, фо, фо"))
        assertTrue(shiftedLrc.contains("[00:25.20] Плохая сука, она в меня влюбилась"))
    }

    @Test
    fun applyOffsetToLrc_shiftsEnhancedWordTags() {
        val enhancedLrc = """
            [00:02.41]<00:02.41>Любовь <00:03.10>и <00:03.50>Ракеты
        """.trimIndent()

        val shiftedLrc = LyricsUtils.applyOffsetToLrc(enhancedLrc, 10_000L, tagOffset = false)

        assertTrue(shiftedLrc.contains("[00:12.41]<00:12.41>Любовь <00:13.10>и <00:13.50>Ракеты"))
    }

    @Test
    fun applyOffset_shiftsInMemoryLyricsModel() {
        val originalLyrics = Lyrics(
            synced = listOf(
                SyncedLine(
                    time = 2410,
                    line = "Любовь и Ракеты",
                    words = listOf(SyncedWord(2410, "Любовь", true))
                ),
                SyncedLine(
                    time = 10200,
                    line = "Плохая сука",
                    words = listOf(SyncedWord(10200, "Плохая", true))
                )
            ),
            plain = listOf("Любовь и Ракеты", "Плохая сука")
        )

        val shifted = LyricsUtils.applyOffset(originalLyrics, 14_000L)

        assertEquals(16410, shifted.synced?.get(0)?.time)
        assertEquals(16410, shifted.synced?.get(0)?.words?.get(0)?.time)
        assertEquals(24200, shifted.synced?.get(1)?.time)
        assertEquals(24200, shifted.synced?.get(1)?.words?.get(0)?.time)
    }
}
