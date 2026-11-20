package com.unshoo.pixelmusic.tv

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unshoo.pixelmusic.R
import com.unshoo.pixelmusic.data.model.Album
import com.unshoo.pixelmusic.data.model.Artist
import com.unshoo.pixelmusic.data.model.Genre
import com.unshoo.pixelmusic.data.model.Playlist
import com.unshoo.pixelmusic.data.repository.MusicRepository
import com.unshoo.pixelmusic.presentation.components.OptimizedAlbumArt
import com.unshoo.pixelmusic.presentation.viewmodel.EqualizerViewModel
import com.unshoo.pixelmusic.presentation.viewmodel.LibraryViewModel
import com.unshoo.pixelmusic.presentation.viewmodel.MainViewModel
import com.unshoo.pixelmusic.presentation.viewmodel.PlayerViewModel
import com.unshoo.pixelmusic.presentation.viewmodel.PlaylistViewModel

sealed interface TvSubScreen {
    object None : TvSubScreen
    data class AlbumDetail(val album: Album) : TvSubScreen
    data class ArtistDetail(val artist: Artist) : TvSubScreen
    data class PlaylistDetail(val playlist: Playlist) : TvSubScreen
    data class GenreDetail(val genre: Genre) : TvSubScreen
}

enum class TvDestination(
    @androidx.annotation.StringRes val titleRes: Int,
    val iconRes: Int
) {
    HOME(R.string.tv_nav_home, R.drawable.rounded_home_24),
    LIBRARY(R.string.tv_nav_library, R.drawable.rounded_library_music_24),
    NOW_PLAYING(R.string.tv_nav_player, R.drawable.rounded_play_arrow_filled_24),
    SEARCH(R.string.tv_nav_search, R.drawable.rounded_search_24),
    SETTINGS(R.string.tv_nav_settings, R.drawable.rounded_settings_24)
}

@Composable
fun TvMainScreen(
    playerViewModel: PlayerViewModel,
    libraryViewModel: LibraryViewModel,
    mainViewModel: MainViewModel,
    playlistViewModel: PlaylistViewModel,
    equalizerViewModel: EqualizerViewModel,
    musicRepository: MusicRepository,
    tvQrTransferManager: com.unshoo.pixelmusic.tv.qr.TvQrTransferManager,
    innerTubeParser: com.unshoo.pixelmusic.data.network.youtube.InnerTubeParser,
    userPreferencesRepository: com.unshoo.pixelmusic.data.preferences.UserPreferencesRepository,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isWelcomeDone by userPreferencesRepository.tvWelcomeDoneFlow.collectAsStateWithLifecycle(initialValue = false)
    var currentDestination by remember { mutableStateOf(TvDestination.HOME) }
    var currentSubScreen by remember { mutableStateOf<TvSubScreen>(TvSubScreen.None) }
    var isFullscreenLyrics by remember { mutableStateOf(false) }
    var showWelcomeScreen by remember(isWelcomeDone) { mutableStateOf(!isWelcomeDone) }
    var showQrDialog by remember { mutableStateOf(false) }
    val stableState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val currentSong = stableState.currentSong

    // Fullscreen Welcome / Onboarding Screen if active
    if (showWelcomeScreen) {
        val coroutineScope = rememberCoroutineScope()
        TvWelcomeScreen(
            mainViewModel = mainViewModel,
            onComplete = {
                coroutineScope.launch {
                    userPreferencesRepository.setTvWelcomeDone(true)
                    showWelcomeScreen = false
                }
            }
        )
        return
    }

    // Back button in Fullscreen Karaoke closes karaoke
    androidx.activity.compose.BackHandler(enabled = isFullscreenLyrics || showQrDialog) {
        if (showQrDialog) {
            showQrDialog = false
        } else if (isFullscreenLyrics) {
            isFullscreenLyrics = false
        }
    }

    if (showQrDialog) {
        val coroutineScope = rememberCoroutineScope()
        com.unshoo.pixelmusic.tv.qr.TvQrConnectDialog(
            transferManager = tvQrTransferManager,
            onDismiss = { showQrDialog = false },
            onPlayLink = { item ->
                coroutineScope.launch {
                    val allSongs = musicRepository.getAllSongsOnce()
                    val match = allSongs.firstOrNull {
                        it.title.contains(item.title, ignoreCase = true) ||
                        (item.title.isNotBlank() && it.artist.contains(item.title, ignoreCase = true)) ||
                        it.path.contains(item.url, ignoreCase = true)
                    }
                    if (match != null) {
                        playerViewModel.playSongs(listOf(match), match)
                        currentDestination = TvDestination.NOW_PLAYING
                    } else {
                        val parsedArtist = item.artist.ifBlank {
                            if (item.title.contains("—")) item.title.substringBefore("—").trim() else "YouTube Music"
                        }
                        val parsedTitle = if (item.title.contains("—")) item.title.substringAfter("—").trim() else item.title.ifBlank { "YouTube Track" }
                        val playable = innerTubeParser.resolvePlayableSongFromUrl(
                            url = item.url,
                            defaultTitle = parsedTitle,
                            defaultArtist = parsedArtist,
                            thumb = item.thumbnailUrl
                        )
                        if (playable != null) {
                            playerViewModel.playSongs(listOf(playable), playable)
                            currentDestination = TvDestination.NOW_PLAYING
                        } else {
                            android.widget.Toast.makeText(context, context.getString(R.string.tv_online_stream_failed), android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                    showQrDialog = false
                }
            },
            onSavePlaylist = { item ->
                val fallbackName = context.getString(R.string.tv_qr_received_fallback)
                val name = item.title.ifBlank { fallbackName }
                playlistViewModel.createPlaylist(name = name, songIds = emptyList())
                tvQrTransferManager.clearReceivedItem()
            }
        )
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // LEFT NAVIGATION RAIL (hidden in Fullscreen Karaoke for true full screen)
        if (!isFullscreenLyrics) {
            Column(
                modifier = Modifier
                    .width(100.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // App Logo Icon
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.rounded_music_note_24),
                        contentDescription = "PixelMusic TV",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Navigation Destinations
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TvDestination.entries.forEach { destination ->
                        val isSelected = currentDestination == destination

                        TvFocusableBox(
                            onClick = {
                                currentDestination = destination
                                currentSubScreen = TvSubScreen.None
                            },
                            modifier = Modifier.size(66.dp),
                            shape = RoundedCornerShape(18.dp),
                            focusedScale = 1.10f,
                            focusedBorderColor = MaterialTheme.colorScheme.primary
                        ) { isFocused ->
                            val bg = when {
                                isFocused -> MaterialTheme.colorScheme.primary
                                isSelected -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            }
                            val tint = when {
                                isFocused -> MaterialTheme.colorScheme.onPrimary
                                isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(bg)
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        painter = painterResource(destination.iconRes),
                                        contentDescription = stringResource(destination.titleRes),
                                        tint = tint,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = stringResource(destination.titleRes),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.sp,
                                            fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium
                                        ),
                                        color = tint,
                                        maxLines = 1
                                    )
                                }

                                // Active playback indicator on Player tab
                                if (destination == TvDestination.NOW_PLAYING && stableState.isPlaying) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp)
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (isFocused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary)
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom spacer for balance
                Spacer(modifier = Modifier.height(48.dp))
            }
        }

        // RIGHT CONTENT AREA
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            // Main Destination Content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (val sub = currentSubScreen) {
                    is TvSubScreen.AlbumDetail -> {
                        TvAlbumDetailScreen(
                            album = sub.album,
                            musicRepository = musicRepository,
                            playerViewModel = playerViewModel,
                            onBack = { currentSubScreen = TvSubScreen.None },
                            onNavigateToNowPlaying = {
                                currentDestination = TvDestination.NOW_PLAYING
                                currentSubScreen = TvSubScreen.None
                            }
                        )
                    }
                    is TvSubScreen.ArtistDetail -> {
                        TvArtistDetailScreen(
                            artist = sub.artist,
                            musicRepository = musicRepository,
                            playerViewModel = playerViewModel,
                            onAlbumClick = { album ->
                                currentSubScreen = TvSubScreen.AlbumDetail(album)
                            },
                            onBack = { currentSubScreen = TvSubScreen.None },
                            onNavigateToNowPlaying = {
                                currentDestination = TvDestination.NOW_PLAYING
                                currentSubScreen = TvSubScreen.None
                            }
                        )
                    }
                    is TvSubScreen.PlaylistDetail -> {
                        TvPlaylistDetailScreen(
                            playlist = sub.playlist,
                            musicRepository = musicRepository,
                            playerViewModel = playerViewModel,
                            onBack = { currentSubScreen = TvSubScreen.None },
                            onNavigateToNowPlaying = {
                                currentDestination = TvDestination.NOW_PLAYING
                                currentSubScreen = TvSubScreen.None
                            }
                        )
                    }
                    is TvSubScreen.GenreDetail -> {
                        TvGenreDetailScreen(
                            genre = sub.genre,
                            musicRepository = musicRepository,
                            playerViewModel = playerViewModel,
                            onBack = { currentSubScreen = TvSubScreen.None },
                            onNavigateToNowPlaying = {
                                currentDestination = TvDestination.NOW_PLAYING
                                currentSubScreen = TvSubScreen.None
                            }
                        )
                    }
                    TvSubScreen.None -> {
                        AnimatedContent(
                            targetState = currentDestination,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "tvNavigation"
                        ) { target ->
                            when (target) {
                                TvDestination.HOME -> TvHomeScreen(
                                    playerViewModel = playerViewModel,
                                    libraryViewModel = libraryViewModel,
                                    mainViewModel = mainViewModel,
                                    playlistViewModel = playlistViewModel,
                                    musicRepository = musicRepository,
                                    onAlbumClick = { album ->
                                        currentSubScreen = TvSubScreen.AlbumDetail(album)
                                    },
                                    onArtistClick = { artist ->
                                        currentSubScreen = TvSubScreen.ArtistDetail(artist)
                                    },
                                    onPlaylistClick = { playlist ->
                                        currentSubScreen = TvSubScreen.PlaylistDetail(playlist)
                                    },
                                    onGenreClick = { genre ->
                                        currentSubScreen = TvSubScreen.GenreDetail(genre)
                                    },
                                    onNavigateToNowPlaying = { currentDestination = TvDestination.NOW_PLAYING },
                                    onOpenQrConnect = { showQrDialog = true }
                                )
                                TvDestination.LIBRARY -> TvLibraryScreen(
                                    libraryViewModel = libraryViewModel,
                                    playerViewModel = playerViewModel,
                                    playlistViewModel = playlistViewModel,
                                    musicRepository = musicRepository,
                                    onAlbumClick = { album ->
                                        currentSubScreen = TvSubScreen.AlbumDetail(album)
                                    },
                                    onArtistClick = { artist ->
                                        currentSubScreen = TvSubScreen.ArtistDetail(artist)
                                    },
                                    onPlaylistClick = { playlist ->
                                        currentSubScreen = TvSubScreen.PlaylistDetail(playlist)
                                    },
                                    onGenreClick = { genre ->
                                        currentSubScreen = TvSubScreen.GenreDetail(genre)
                                    },
                                    onNavigateToNowPlaying = { currentDestination = TvDestination.NOW_PLAYING }
                                )
                                TvDestination.NOW_PLAYING -> TvNowPlayingScreen(
                                    playerViewModel = playerViewModel,
                                    isFullscreenLyrics = isFullscreenLyrics,
                                    onToggleFullscreenLyrics = { isFullscreenLyrics = it }
                                )
                                TvDestination.SEARCH -> TvSearchScreen(
                                    playerViewModel = playerViewModel,
                                    innerTubeParser = innerTubeParser,
                                    onAlbumClick = { album ->
                                        currentSubScreen = TvSubScreen.AlbumDetail(album)
                                    },
                                    onArtistClick = { artist ->
                                        currentSubScreen = TvSubScreen.ArtistDetail(artist)
                                    },
                                    onNavigateToNowPlaying = { currentDestination = TvDestination.NOW_PLAYING }
                                )
                                TvDestination.SETTINGS -> TvSettingsScreen(
                                    equalizerViewModel = equalizerViewModel,
                                    tvQrTransferManager = tvQrTransferManager,
                                    playerViewModel = playerViewModel,
                                    onOpenWelcome = { showWelcomeScreen = true },
                                    onOpenQrConnect = { showQrDialog = true }
                                )
                            }
                        }
                    }
                }
            }

            // TV Mini-Player Bar (shown when playing and NOT in Now Playing screen)
            if (currentSong != null && currentDestination != TvDestination.NOW_PLAYING && currentSubScreen == TvSubScreen.None) {
                TvFocusableBox(
                    onClick = {
                        currentDestination = TvDestination.NOW_PLAYING
                        currentSubScreen = TvSubScreen.None
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .padding(horizontal = 24.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp)
                ) { isFocused ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                if (isFocused) MaterialTheme.colorScheme.surfaceVariant
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                            )
                            .padding(horizontal = 16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                        ) {
                            OptimizedAlbumArt(
                                uri = currentSong.albumArtUriString,
                                title = currentSong.title,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentSong.title,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = currentSong.displayArtist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        TvIconButton(
                            iconRes = if (stableState.isPlaying) R.drawable.rounded_pause_filled_24
                            else R.drawable.rounded_play_arrow_filled_24,
                            contentDescription = if (stableState.isPlaying) "Pause" else "Play",
                            size = 44.dp,
                            iconSize = 22.dp,
                            onClick = { playerViewModel.playPause() }
                        )
                    }
                }
            }
        }
    }
}
