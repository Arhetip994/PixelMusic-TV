package com.unshoo.pixelmusic.tv

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.data.repository.MusicRepository
import com.unshoo.pixelmusic.presentation.components.OptimizedAlbumArt
import com.unshoo.pixelmusic.presentation.viewmodel.LibraryViewModel
import com.unshoo.pixelmusic.presentation.viewmodel.PlayerViewModel
import com.unshoo.pixelmusic.presentation.viewmodel.PlaylistViewModel
import com.unshoo.pixelmusic.utils.formatDuration

enum class TvLibraryTab(
    @androidx.annotation.StringRes val titleRes: Int,
    val iconRes: Int
) {
    SONGS(R.string.tv_tab_all_songs, R.drawable.rounded_music_note_24),
    FAVORITES(R.string.tv_tab_favorites, R.drawable.rounded_favorite_24),
    ALBUMS(R.string.tv_tab_albums, R.drawable.rounded_library_music_24),
    ARTISTS(R.string.tv_tab_artists, R.drawable.rounded_music_note_24),
    PLAYLISTS(R.string.tv_tab_playlists, R.drawable.rounded_playlist_play_24),
    GENRES(R.string.tv_tab_genres, R.drawable.rounded_surround_sound_24)
}

@Composable
fun TvLibraryScreen(
    libraryViewModel: LibraryViewModel,
    playerViewModel: PlayerViewModel,
    playlistViewModel: PlaylistViewModel,
    musicRepository: MusicRepository,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onGenreClick: (Genre) -> Unit,
    onNavigateToNowPlaying: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(TvLibraryTab.SONGS) }

    val allSongs by libraryViewModel.allSongs.collectAsStateWithLifecycle()
    val albums by libraryViewModel.albums.collectAsStateWithLifecycle()
    val artists by libraryViewModel.artists.collectAsStateWithLifecycle()
    val playlistUiState by playlistViewModel.uiState.collectAsStateWithLifecycle()
    val playlists = playlistUiState.playlists
    val genres by remember(musicRepository) {
        musicRepository.getGenres()
    }.collectAsState(initial = emptyList())

    val stableState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val currentSongId = stableState.currentSong?.id

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 40.dp, vertical = 24.dp)
    ) {
        // HEADER & STATS BADGE
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = stringResource(R.string.tv_nav_library),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 26.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )

                Box(
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.tv_tracks_count, allSongs.size),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // MD3 EXPRESSIVE TAB PILLS
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(TvLibraryTab.entries) { tab ->
                val isSelected = selectedTab == tab
                TvFocusableBox(
                    onClick = { selectedTab = tab },
                    shape = RoundedCornerShape(14.dp),
                    focusedScale = 1.08f
                ) { isFocused ->
                    val bg = when {
                        isFocused -> MaterialTheme.colorScheme.primary
                        isSelected -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    }
                    val textColor = when {
                        isFocused -> MaterialTheme.colorScheme.onPrimary
                        isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(bg)
                            .padding(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            painter = painterResource(tab.iconRes),
                            contentDescription = null,
                            tint = textColor,
                            modifier = Modifier
                                .size(18.dp)
                                .padding(end = 6.dp)
                        )
                        Text(
                            text = stringResource(tab.titleRes),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = textColor
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // TAB CONTENT
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "tvLibraryTabAnim",
            modifier = Modifier.weight(1f)
        ) { currentTab ->
            when (currentTab) {
                TvLibraryTab.SONGS -> {
                    TvSongsTabContent(
                        songs = allSongs,
                        currentSongId = currentSongId,
                        isPlaying = stableState.isPlaying,
                        onSongClick = { song ->
                            playerViewModel.playSongs(allSongs, song)
                            onNavigateToNowPlaying()
                        },
                        onShuffleAll = {
                            playerViewModel.shuffleAllSongs()
                            onNavigateToNowPlaying()
                        },
                        onPlayRandom = {
                            playerViewModel.playRandomSong()
                            onNavigateToNowPlaying()
                        }
                    )
                }
                TvLibraryTab.FAVORITES -> {
                    val favSongs = remember(allSongs) { allSongs.filter { it.isFavorite } }
                    TvSongsTabContent(
                        songs = favSongs,
                        currentSongId = currentSongId,
                        isPlaying = stableState.isPlaying,
                        emptyMessage = stringResource(R.string.tv_library_empty_favorites),
                        onSongClick = { song ->
                            playerViewModel.playSongs(favSongs, song)
                            onNavigateToNowPlaying()
                        },
                        onShuffleAll = {
                            playerViewModel.shuffleFavoriteSongs()
                            onNavigateToNowPlaying()
                        },
                        onPlayRandom = {
                            if (favSongs.isNotEmpty()) {
                                val s = favSongs.random()
                                playerViewModel.playSongs(favSongs, s)
                                onNavigateToNowPlaying()
                            }
                        }
                    )
                }
                TvLibraryTab.ALBUMS -> {
                    if (albums.isEmpty()) {
                        TvEmptyStateText(stringResource(R.string.tv_library_empty))
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 180.dp),
                            contentPadding = PaddingValues(bottom = 32.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(albums, key = { it.id }) { album ->
                                TvAlbumCard(album = album, onClick = { onAlbumClick(album) })
                            }
                        }
                    }
                }
                TvLibraryTab.ARTISTS -> {
                    if (artists.isEmpty()) {
                        TvEmptyStateText(stringResource(R.string.tv_library_empty))
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 160.dp),
                            contentPadding = PaddingValues(bottom = 32.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(artists, key = { it.id }) { artist ->
                                TvArtistCard(artist = artist, onClick = { onArtistClick(artist) })
                            }
                        }
                    }
                }
                TvLibraryTab.PLAYLISTS -> {
                    if (playlists.isEmpty()) {
                        TvEmptyStateText(stringResource(R.string.tv_library_empty))
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 200.dp),
                            contentPadding = PaddingValues(bottom = 32.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(playlists, key = { it.id }) { playlist ->
                                TvPlaylistCard(playlist = playlist, onClick = { onPlaylistClick(playlist) })
                            }
                        }
                    }
                }
                TvLibraryTab.GENRES -> {
                    if (genres.isEmpty()) {
                        TvEmptyStateText(stringResource(R.string.tv_library_empty))
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 170.dp),
                            contentPadding = PaddingValues(bottom = 32.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(genres, key = { it.id }) { genre ->
                                TvGenreCard(name = genre.name, onClick = { onGenreClick(genre) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvSongsTabContent(
    songs: List<Song>,
    currentSongId: String?,
    isPlaying: Boolean,
    emptyMessage: String = stringResource(R.string.tv_library_empty_tracks),
    onSongClick: (Song) -> Unit,
    onShuffleAll: () -> Unit,
    onPlayRandom: () -> Unit
) {
    if (songs.isEmpty()) {
        TvEmptyStateText(emptyMessage)
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Quick Action Bar
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            TvFocusableBox(
                onClick = onShuffleAll,
                shape = RoundedCornerShape(10.dp)
            ) { isFocused ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(
                            if (isFocused) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.rounded_shuffle_24),
                        contentDescription = null,
                        tint = if (isFocused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.tv_home_shuffle_all),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (isFocused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            TvFocusableBox(
                onClick = onPlayRandom,
                shape = RoundedCornerShape(10.dp)
            ) { isFocused ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(
                            if (isFocused) MaterialTheme.colorScheme.secondary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.rounded_play_arrow_filled_24),
                        contentDescription = null,
                        tint = if (isFocused) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.tv_home_random_song),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (isFocused) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(songs, key = { _, s -> s.id }) { index, song ->
                val isCurrent = song.id == currentSongId

                TvFocusableBox(
                    onClick = { onSongClick(song) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    focusedScale = 1.02f
                ) { isFocused ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                when {
                                    isFocused -> MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                    isCurrent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                }
                            )
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        // Position / Play indicator
                        Box(
                            modifier = Modifier.width(36.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isCurrent && isPlaying) {
                                Icon(
                                    painter = painterResource(R.drawable.rounded_play_arrow_filled_24),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                Text(
                                    text = "${index + 1}",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Thumbnail
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(8.dp))
                        ) {
                            OptimizedAlbumArt(
                                uri = song.albumArtUriString,
                                title = song.title,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // Title and Artist
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = if (isFocused || isCurrent) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${song.displayArtist} • ${song.album}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Favorite heart if set
                        if (song.isFavorite) {
                            Icon(
                                painter = painterResource(R.drawable.rounded_favorite_24),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(18.dp)
                                    .padding(end = 8.dp)
                            )
                        }

                        // Duration
                        Text(
                            text = formatDuration(song.duration),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvEmptyStateText(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
