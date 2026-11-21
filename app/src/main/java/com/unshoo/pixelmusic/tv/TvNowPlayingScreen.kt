package com.unshoo.pixelmusic.tv

import android.content.Context
import android.media.AudioManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.unshoo.pixelmusic.R
import com.unshoo.pixelmusic.presentation.components.OptimizedAlbumArt
import com.unshoo.pixelmusic.presentation.components.WavySliderExpressive
import com.unshoo.pixelmusic.presentation.viewmodel.PlayerViewModel
import com.unshoo.pixelmusic.utils.formatDuration

@Composable
fun TvNowPlayingScreen(
    playerViewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
    isFullscreenLyrics: Boolean = false,
    onToggleFullscreenLyrics: (Boolean) -> Unit = {}
) {
    val stableState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val currentPosition by playerViewModel.currentPlaybackPosition.collectAsStateWithLifecycle()
    val song = stableState.currentSong

    if (song == null) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.tv_nothing_playing),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
        return
    }

    val totalDuration = if (stableState.totalDuration > 0L) stableState.totalDuration else song.duration
    val progress = (currentPosition.toFloat() / totalDuration.coerceAtLeast(1L)).coerceIn(0f, 1f)

    // Synced lyrics calculation with 300ms lookahead for audio buffer sync + user sync offset
    val lyricsSyncOffset by playerViewModel.currentSongLyricsSyncOffset.collectAsStateWithLifecycle()
    val syncedLines = stableState.lyrics?.synced
    val currentLyricIndex by remember(syncedLines, lyricsSyncOffset) {
        derivedStateOf {
            if (syncedLines.isNullOrEmpty()) -1
            else {
                val pos = (currentPosition + 300L + lyricsSyncOffset).coerceAtLeast(0L)
                val idx = syncedLines.indexOfLast { it.time <= pos }
                if (idx >= 0) idx else 0
            }
        }
    }

    val lyricsListState = rememberLazyListState()
    val fullscreenLyricsListState = rememberLazyListState()
    val queue by playerViewModel.queueFlow.collectAsStateWithLifecycle()
    var showQueueTab by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showTrackInfoDialog by remember { mutableStateOf(false) }
    var isAmbientMode by remember { mutableStateOf(false) }
    val activeTimerDisplay by playerViewModel.activeTimerValueDisplay.collectAsStateWithLifecycle()
    val isEndOfTrackActive by playerViewModel.isEndOfTrackTimerActive.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var currentVolume by remember { mutableStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) }

    BackHandler(enabled = showSleepTimerDialog || showTrackInfoDialog || isFullscreenLyrics || isAmbientMode) {
        if (showSleepTimerDialog) showSleepTimerDialog = false
        else if (showTrackInfoDialog) showTrackInfoDialog = false
        else if (isFullscreenLyrics) onToggleFullscreenLyrics(false)
        else if (isAmbientMode) isAmbientMode = false
    }

    LaunchedEffect(currentLyricIndex, isFullscreenLyrics) {
        if (currentLyricIndex >= 0) {
            lyricsListState.animateScrollToItem(
                (currentLyricIndex - 1).coerceAtLeast(0)
            )
            if (isFullscreenLyrics) {
                fullscreenLyricsListState.animateScrollToItem(
                    index = (currentLyricIndex - 1).coerceAtLeast(0)
                )
            }
        }
    }

    // Auto-fetch online lyrics from LRCLIB if current song has no lyrics yet
    LaunchedEffect(song.id) {
        if (stableState.lyrics == null) {
            playerViewModel.fetchLyricsForCurrentSong()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Ambient background blur layer with gradient overlay
        Box(modifier = Modifier.fillMaxSize()) {
            OptimizedAlbumArt(
                uri = song.albumArtUriString,
                title = song.title,
                modifier = Modifier.fillMaxSize()
            )
            // Dark gradient scrim
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.background.copy(alpha = 0.88f),
                                MaterialTheme.colorScheme.background.copy(alpha = 0.95f),
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
            )
        }

        // Two-column TV Layout (16:9)
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // LEFT COLUMN: Large Album Artwork & Audio Badges
            Column(
                modifier = Modifier
                    .weight(0.42f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(310.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(24.dp)
                        )
                ) {
                    OptimizedAlbumArt(
                        uri = song.albumArtUriString,
                        title = song.title,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Audio Quality & Format Info
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val formatLabel = song.mimeType?.substringAfterLast('/')?.uppercase() ?: "AUDIO"
                    if (formatLabel == "FLAC") {
                        Box(
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f),
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "LOSSLESS",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = formatLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (song.bitrate != null && song.bitrate > 0) {
                        Box(
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${song.bitrate / 1000} kbps",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // RIGHT COLUMN: Info, Progress, Controls, and Synced Lyrics
            Column(
                modifier = Modifier
                    .weight(0.58f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center
            ) {
                // Title and Artist
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "${song.displayArtist} — ${song.album}",
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Interactive Focusable Progress Bar with D-pad Left/Right Seeking (±10s)
                var isProgressFocused by remember { mutableStateOf(false) }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { isProgressFocused = it.isFocused }
                        .focusable()
                        .onKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                when (keyEvent.key) {
                                    Key.DirectionLeft -> {
                                        playerViewModel.seekTo((currentPosition - 10_000L).coerceAtLeast(0L))
                                        true
                                    }
                                    Key.DirectionRight -> {
                                        playerViewModel.seekTo((currentPosition + 10_000L).coerceAtMost(totalDuration))
                                        true
                                    }
                                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                        playerViewModel.playPause()
                                        true
                                    }
                                    else -> false
                                }
                            } else false
                        }
                        .background(
                            if (isProgressFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    WavySliderExpressive(
                        value = { progress },
                        onValueChange = { newProgress ->
                            playerViewModel.seekTo((newProgress * totalDuration).toLong().coerceIn(0L, totalDuration))
                        },
                        isPlaying = stableState.isPlaying,
                        strokeWidth = if (isProgressFocused) 7.dp else 5.dp,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        thumbColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatDuration(currentPosition),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 13.sp,
                                fontWeight = if (isProgressFocused) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (isProgressFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (isProgressFocused) {
                            Text(
                                text = stringResource(R.string.tv_seek_hint),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Text(
                            text = formatDuration(totalDuration),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Control Buttons Row (D-pad accessible)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TvIconButton(
                        iconRes = R.drawable.rounded_shuffle_24,
                        contentDescription = "Shuffle",
                        isActive = stableState.isShuffleEnabled,
                        onClick = { playerViewModel.toggleShuffle() }
                    )

                    TvIconButton(
                        iconRes = R.drawable.rounded_skip_previous_filled_24,
                        contentDescription = "Previous Song",
                        onClick = { playerViewModel.previousSong() }
                    )

                    TvIconButton(
                        iconRes = if (stableState.isPlaying) R.drawable.rounded_pause_filled_24
                        else R.drawable.rounded_play_arrow_filled_24,
                        contentDescription = if (stableState.isPlaying) "Pause" else "Play",
                        size = 68.dp,
                        iconSize = 34.dp,
                        onClick = { playerViewModel.playPause() }
                    )

                    TvIconButton(
                        iconRes = R.drawable.rounded_skip_next_filled_24,
                        contentDescription = "Next Song",
                        onClick = { playerViewModel.nextSong() }
                    )

                    TvIconButton(
                        iconRes = if (stableState.repeatMode == Player.REPEAT_MODE_ONE) R.drawable.rounded_repeat_one_24
                        else R.drawable.rounded_repeat_24,
                        contentDescription = "Repeat",
                        isActive = stableState.repeatMode != Player.REPEAT_MODE_OFF,
                        onClick = { playerViewModel.cycleRepeatMode() }
                    )

                    TvIconButton(
                        iconRes = if (song.isFavorite) R.drawable.rounded_favorite_24
                        else R.drawable.round_favorite_border_24,
                        contentDescription = "Favorite",
                        isActive = song.isFavorite,
                        onClick = { playerViewModel.toggleFavorite() }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // TV Volume & Utilities Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Volume Controls
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TvIconButton(
                            iconRes = R.drawable.rounded_volume_down_24,
                            contentDescription = stringResource(R.string.tv_volume_down),
                            size = 36.dp,
                            iconSize = 18.dp,
                            onClick = {
                                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                                currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                            }
                        )

                        LinearProgressIndicator(
                            progress = { (currentVolume.toFloat() / maxVolume).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .width(110.dp)
                                .height(6.dp)
                                .clip(CircleShape),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )

                        Text(
                            text = "${(currentVolume * 100 / maxVolume)}%",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(36.dp)
                        )

                        TvIconButton(
                            iconRes = R.drawable.rounded_volume_up_24,
                            contentDescription = stringResource(R.string.tv_volume_up),
                            size = 36.dp,
                            iconSize = 18.dp,
                            onClick = {
                                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                                currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Right: Quick Utilities (Timer, Ambient, Track Info)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TvIconButton(
                            iconRes = R.drawable.rounded_timer_24,
                            contentDescription = stringResource(R.string.tv_sleep_timer),
                            size = 36.dp,
                            iconSize = 18.dp,
                            isActive = activeTimerDisplay != null || isEndOfTrackActive,
                            onClick = { showSleepTimerDialog = true }
                        )

                        TvIconButton(
                            iconRes = R.drawable.rounded_tv_24,
                            contentDescription = stringResource(R.string.tv_ambient_mode),
                            size = 36.dp,
                            iconSize = 18.dp,
                            onClick = { isAmbientMode = true }
                        )

                        TvIconButton(
                            iconRes = R.drawable.rounded_audio_file_24,
                            contentDescription = stringResource(R.string.tv_track_info_title),
                            size = 36.dp,
                            iconSize = 18.dp,
                            isActive = showTrackInfoDialog,
                            onClick = { showTrackInfoDialog = true }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Toggle tabs: [Текст песни] / [Очередь]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TvFocusableBox(
                        onClick = { showQueueTab = false },
                        shape = RoundedCornerShape(8.dp)
                    ) { isFocused ->
                        Text(
                            text = stringResource(R.string.tv_tab_lyrics),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = if (!showQueueTab || isFocused) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (isFocused) MaterialTheme.colorScheme.onPrimary
                            else if (!showQueueTab) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .background(
                                    if (isFocused) MaterialTheme.colorScheme.primary
                                    else if (!showQueueTab) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                )
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }

                    TvFocusableBox(
                        onClick = { showQueueTab = true },
                        shape = RoundedCornerShape(8.dp)
                    ) { isFocused ->
                        Text(
                            text = stringResource(R.string.tv_tab_queue, queue.size),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = if (showQueueTab || isFocused) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (isFocused) MaterialTheme.colorScheme.onPrimary
                            else if (showQueueTab) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .background(
                                    if (isFocused) MaterialTheme.colorScheme.primary
                                    else if (showQueueTab) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                )
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }

                    if (!showQueueTab && !syncedLines.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.weight(1f))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Синхр:",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TvFocusableBox(
                                onClick = { playerViewModel.setLyricsSyncOffset(song.id, lyricsSyncOffset - 500) },
                                shape = RoundedCornerShape(6.dp)
                            ) { isFocused ->
                                Text(
                                    text = "-0.5с",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                                    color = if (isFocused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .background(
                                            if (isFocused) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            if (lyricsSyncOffset != 0) {
                                TvFocusableBox(
                                    onClick = { playerViewModel.setLyricsSyncOffset(song.id, 0) },
                                    shape = RoundedCornerShape(6.dp)
                                ) { isFocused ->
                                    val offsetText = if (lyricsSyncOffset > 0) "+${lyricsSyncOffset / 1000f}с" else "${lyricsSyncOffset / 1000f}с"
                                    Text(
                                        text = "Сброс ($offsetText)",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                                        color = if (isFocused) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.error,
                                        modifier = Modifier
                                            .background(
                                                if (isFocused) MaterialTheme.colorScheme.error
                                                else MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                                            )
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            TvFocusableBox(
                                onClick = { playerViewModel.setLyricsSyncOffset(song.id, lyricsSyncOffset + 500) },
                                shape = RoundedCornerShape(6.dp)
                            ) { isFocused ->
                                Text(
                                    text = "+0.5с",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                                    color = if (isFocused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .background(
                                            if (isFocused) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // TAB CONTENT: Lyrics OR Queue
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            RoundedCornerShape(14.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    if (showQueueTab) {
                        if (queue.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = stringResource(R.string.tv_queue_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                itemsIndexed(queue, key = { _, qSong -> qSong.id }) { index, qSong ->
                                    val isCurrent = qSong.id == song.id
                                    TvFocusableBox(
                                        onClick = { playerViewModel.playSong(qSong) },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(6.dp),
                                        focusedScale = 1.01f
                                    ) { isFocused ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(
                                                    when {
                                                        isFocused -> MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                                        isCurrent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                        else -> Color.Transparent
                                                    }
                                                )
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = if (isCurrent) "▶" else "${index + 1}",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.width(28.dp)
                                            )
                                            Text(
                                                text = qSong.title,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                                ),
                                                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = formatDuration(qSong.duration),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Lyrics View (clickable to expand fullscreen karaoke)
                        TvFocusableBox(
                            onClick = { onToggleFullscreenLyrics(true) },
                            modifier = Modifier.fillMaxSize(),
                            shape = RoundedCornerShape(8.dp),
                            focusedScale = 1.01f
                        ) { isLyricsFocused ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        if (isLyricsFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                        else Color.Transparent
                                    )
                                    .padding(4.dp)
                            ) {
                                if (syncedLines.isNullOrEmpty()) {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = if (stableState.lyrics?.plain != null) stableState.lyrics!!.plain!!.joinToString("\n")
                                                   else stringResource(R.string.tv_lyrics_not_loaded),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        TvFocusableBox(
                                            onClick = { playerViewModel.fetchLyricsForCurrentSong() },
                                            shape = RoundedCornerShape(6.dp)
                                        ) { isBtnFocused ->
                                            Text(
                                                text = stringResource(R.string.tv_lyrics_search_btn),
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                color = if (isBtnFocused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                                modifier = Modifier
                                                    .background(
                                                        if (isBtnFocused) MaterialTheme.colorScheme.primary
                                                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                    )
                                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                } else {
                                    Column(modifier = Modifier.fillMaxSize()) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 2.dp),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            Text(
                                                text = if (isLyricsFocused) stringResource(R.string.tv_open_karaoke_hint) else "⛶ ${stringResource(R.string.tv_fullscreen_lyrics)}",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                color = if (isLyricsFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        LazyColumn(
                                            state = lyricsListState,
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            itemsIndexed(syncedLines) { index, line ->
                                                val isCurrent = index == currentLyricIndex
                                                val color by animateColorAsState(
                                                    targetValue = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                                    animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                                                    label = "prevLineCol"
                                                )
                                                Text(
                                                    text = line.line,
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        fontSize = if (isCurrent) 17.sp else 13.sp,
                                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                                    ),
                                                    color = color,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // TV Sleep Timer Dialog Overlay
        if (showSleepTimerDialog) {
            TvSleepTimerDialog(
                activeTimerDisplay = activeTimerDisplay,
                isEndOfTrackActive = isEndOfTrackActive,
                onSetTimer = { playerViewModel.setSleepTimer(it) },
                onSetEndOfTrack = { playerViewModel.setEndOfTrackTimer(true) },
                onCancelTimer = { playerViewModel.cancelSleepTimer() },
                onDismiss = { showSleepTimerDialog = false }
            )
        }

        // TV Track Properties Dialog Overlay
        if (showTrackInfoDialog) {
            val file = remember(song.path) { java.io.File(song.path) }
            val unitMb = stringResource(R.string.tv_unit_mb)
            val unitKb = stringResource(R.string.tv_unit_kb)
            val fileSizeStr = remember(file, unitMb, unitKb) {
                if (file.exists()) {
                    val bytes = file.length()
                    val mb = bytes / (1024.0 * 1024.0)
                    if (mb >= 1.0) String.format(java.util.Locale.US, "%.1f %s", mb, unitMb)
                    else String.format(java.util.Locale.US, "%.0f %s", bytes / 1024.0, unitKb)
                } else "—"
            }
            val bitrateStr = if (song.bitrate != null && song.bitrate > 0) "${song.bitrate / 1000} kbps" else "—"
            val sampleRateStr = if (song.sampleRate != null && song.sampleRate > 0) "${song.sampleRate / 1000.0} kHz" else "—"
            val formatStr = (song.mimeType?.substringAfterLast('/') ?: "audio").uppercase()

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .width(460.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.tv_track_info_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TvInfoPropertyRow(stringResource(R.string.tv_info_format), formatStr)
                        TvInfoPropertyRow(stringResource(R.string.tv_info_bitrate), bitrateStr)
                        TvInfoPropertyRow(stringResource(R.string.tv_info_sample_rate), sampleRateStr)
                        TvInfoPropertyRow(stringResource(R.string.tv_info_duration), formatDuration(totalDuration))
                        TvInfoPropertyRow(stringResource(R.string.tv_info_size), fileSizeStr)
                        if (!song.genre.isNullOrBlank()) {
                            TvInfoPropertyRow(stringResource(R.string.tv_section_genres), song.genre)
                        }
                        if (song.year > 0) {
                            TvInfoPropertyRow(stringResource(R.string.tv_info_year), song.year.toString())
                        }
                    }

                    TvFocusableBox(
                        onClick = { showTrackInfoDialog = false },
                        shape = RoundedCornerShape(10.dp)
                    ) { isFocused ->
                        Text(
                            text = stringResource(R.string.tv_dialog_close),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = if (isFocused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .background(
                                    if (isFocused) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                )
                                .padding(horizontal = 24.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        }

        // Fullscreen Karaoke Mode Overlay
        if (isFullscreenLyrics) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0C0C0F))
            ) {
                // Dimmed ambient backdrop of album art
                OptimizedAlbumArt(
                    uri = song.albumArtUriString,
                    title = song.title,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = 0.18f }
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.85f),
                                    Color.Black.copy(alpha = 0.88f),
                                    Color.Black.copy(alpha = 0.96f)
                                )
                            )
                        )
                )

                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Minimal Top Bar (Exit Karaoke)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 36.dp, vertical = 18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.tv_karaoke_title),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Синхр:",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TvFocusableBox(
                                onClick = { playerViewModel.setLyricsSyncOffset(song.id, lyricsSyncOffset - 500) },
                                shape = RoundedCornerShape(8.dp)
                            ) { isFocused ->
                                Text(
                                    text = "-0.5с",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (isFocused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier
                                        .background(
                                            if (isFocused) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                        )
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                            if (lyricsSyncOffset != 0) {
                                TvFocusableBox(
                                    onClick = { playerViewModel.setLyricsSyncOffset(song.id, 0) },
                                    shape = RoundedCornerShape(8.dp)
                                ) { isFocused ->
                                    val offsetText = if (lyricsSyncOffset > 0) "+${lyricsSyncOffset / 1000f}с" else "${lyricsSyncOffset / 1000f}с"
                                    Text(
                                        text = "Сброс ($offsetText)",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (isFocused) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.error,
                                        modifier = Modifier
                                            .background(
                                                if (isFocused) MaterialTheme.colorScheme.error
                                                else MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                                            )
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                            TvFocusableBox(
                                onClick = { playerViewModel.setLyricsSyncOffset(song.id, lyricsSyncOffset + 500) },
                                shape = RoundedCornerShape(8.dp)
                            ) { isFocused ->
                                Text(
                                    text = "+0.5с",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (isFocused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier
                                        .background(
                                            if (isFocused) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                        )
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            TvFocusableBox(
                                onClick = { onToggleFullscreenLyrics(false) },
                                shape = RoundedCornerShape(10.dp)
                            ) { isFocused ->
                                Row(
                                    modifier = Modifier
                                        .background(
                                            if (isFocused) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                        )
                                        .padding(horizontal = 14.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(R.string.tv_close_karaoke),
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (isFocused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    // Main Lyrics Display - Takes 100% of Center Screen (Fast, buttery smooth 60fps)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        if (syncedLines.isNullOrEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (stableState.lyrics?.plain != null) stableState.lyrics!!.plain!!.joinToString("\n\n")
                                    else stringResource(R.string.tv_no_lyrics),
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            LazyColumn(
                                state = fullscreenLyricsListState,
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                contentPadding = PaddingValues(top = 100.dp, bottom = 140.dp)
                            ) {
                                itemsIndexed(
                                    items = syncedLines,
                                    key = { index, line -> "${line.time}_$index" }
                                ) { index, line ->
                                    val isCurrent = index == currentLyricIndex
                                    val distanceFromCurrent = if (currentLyricIndex >= 0) kotlin.math.abs(index - currentLyricIndex) else 100

                                    TvKaraokeLineItem(
                                        lineText = line.line,
                                        lineTime = line.time.toLong(),
                                        isCurrent = isCurrent,
                                        distanceFromCurrent = distanceFromCurrent,
                                        onSeek = { playerViewModel.seekTo(it) }
                                    )
                                }
                            }
                        }
                    }

                    // Compact Bottom Player Bar (Mini Cover + Title/Artist + Play/Pause + Scrubber)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.85f))
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                            )
                            .padding(horizontal = 36.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Mini Cover
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(
                                        width = 0.5.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                            ) {
                                OptimizedAlbumArt(
                                    uri = song.albumArtUriString,
                                    title = song.title,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            // Compact Title & Artist
                            Column(
                                modifier = Modifier.widthIn(min = 140.dp, max = 260.dp)
                            ) {
                                Text(
                                    text = song.title,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${song.displayArtist} — ${song.album}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Play / Pause Mini Button
                            TvIconButton(
                                iconRes = if (stableState.isPlaying) R.drawable.rounded_pause_filled_24
                                else R.drawable.rounded_play_arrow_filled_24,
                                contentDescription = if (stableState.isPlaying) "Pause" else "Play",
                                size = 44.dp,
                                iconSize = 24.dp,
                                onClick = { playerViewModel.playPause() }
                            )

                            // Interactive Scrubber Timeline (Rewind / Fast-forward)
                            var isKaraokeScrubberFocused by remember { mutableStateOf(false) }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .onFocusChanged { isKaraokeScrubberFocused = it.isFocused }
                                    .focusable()
                                    .onKeyEvent { keyEvent ->
                                        if (keyEvent.type == KeyEventType.KeyDown) {
                                            when (keyEvent.key) {
                                                Key.DirectionLeft -> {
                                                    playerViewModel.seekTo((currentPosition - 10_000L).coerceAtLeast(0L))
                                                    true
                                                }
                                                Key.DirectionRight -> {
                                                    playerViewModel.seekTo((currentPosition + 10_000L).coerceAtMost(totalDuration))
                                                    true
                                                }
                                                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                                    playerViewModel.playPause()
                                                    true
                                                }
                                                else -> false
                                            }
                                        } else false
                                    }
                                    .background(
                                        if (isKaraokeScrubberFocused) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        else Color.Transparent,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = formatDuration(currentPosition),
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = if (isKaraokeScrubberFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (isKaraokeScrubberFocused) {
                                        Text(
                                            text = stringResource(R.string.tv_seek_hint),
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Text(
                                        text = formatDuration(totalDuration),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(if (isKaraokeScrubberFocused) 8.dp else 4.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = if (isKaraokeScrubberFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // OLED Burn-in Protection Ambient Mode Overlay
        if (isAmbientMode) {
            val infiniteTransition = rememberInfiniteTransition(label = "ambientPulse")
            val scale by infiniteTransition.animateFloat(
                initialValue = 1.0f,
                targetValue = 1.05f,
                animationSpec = infiniteRepeatable(
                    animation = tween(10000),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "scale"
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .focusable()
                    .onKeyEvent {
                        isAmbientMode = false
                        false
                    },
                contentAlignment = Alignment.Center
            ) {
                // Blurred background backdrop
                OptimizedAlbumArt(
                    uri = song.albumArtUriString,
                    title = song.title,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = 0.22f }
                )

                // Floating slow-breathing Album Art
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(380.dp)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                            .clip(RoundedCornerShape(28.dp))
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(28.dp)
                            )
                    ) {
                        OptimizedAlbumArt(
                            uri = song.albumArtUriString,
                            title = song.title,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 28.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${song.displayArtist} — ${song.album}",
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }

                    TvFocusableBox(
                        onClick = { isAmbientMode = false },
                        shape = RoundedCornerShape(20.dp)
                    ) { isFocused ->
                        Text(
                            text = stringResource(R.string.tv_ambient_exit_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier
                                .background(
                                    if (isFocused) MaterialTheme.colorScheme.surfaceVariant
                                    else Color.Transparent,
                                    RoundedCornerShape(20.dp)
                                )
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvKaraokeLineItem(
    lineText: String,
    lineTime: Long,
    isCurrent: Boolean,
    distanceFromCurrent: Int,
    onSeek: (Long) -> Unit
) {
    val targetAlpha = when {
        isCurrent -> 1.0f
        distanceFromCurrent == 1 -> 0.55f
        else -> 0.25f
    }
    val targetColor = if (isCurrent) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "tvKaraokeLineAlpha"
    )
    val color by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "tvKaraokeLineColor"
    )

    TvFocusableBox(
        onClick = { onSeek(lineTime) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp)
    ) { isFocused ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .padding(start = 64.dp, end = 96.dp)
                .graphicsLayer {
                    this.alpha = if (isFocused) 1.0f else alpha
                }
        ) {
            Text(
                text = lineText,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 28.sp,
                    lineHeight = 38.sp,
                    fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.Medium
                ),
                color = if (isFocused) MaterialTheme.colorScheme.primary else color,
                softWrap = true,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TvInfoPropertyRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}


