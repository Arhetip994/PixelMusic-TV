package com.unshoo.pixelmusic.tv

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unshoo.pixelmusic.R
import com.unshoo.pixelmusic.data.model.Album
import com.unshoo.pixelmusic.data.model.Artist
import com.unshoo.pixelmusic.data.model.Genre
import com.unshoo.pixelmusic.data.model.Playlist
import com.unshoo.pixelmusic.data.repository.MusicRepository
import com.unshoo.pixelmusic.presentation.viewmodel.LibraryViewModel
import com.unshoo.pixelmusic.presentation.viewmodel.MainViewModel
import com.unshoo.pixelmusic.presentation.viewmodel.PlayerViewModel
import com.unshoo.pixelmusic.presentation.viewmodel.PlaylistViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun TvHomeScreen(
    playerViewModel: PlayerViewModel,
    libraryViewModel: LibraryViewModel,
    mainViewModel: MainViewModel,
    playlistViewModel: PlaylistViewModel,
    musicRepository: MusicRepository,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onGenreClick: (Genre) -> Unit = {},
    onNavigateToNowPlaying: () -> Unit,
    onOpenQrConnect: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isSyncing by mainViewModel.isSyncing.collectAsStateWithLifecycle()
    val allSongs by libraryViewModel.allSongs.collectAsStateWithLifecycle()
    val albums by libraryViewModel.albums.collectAsStateWithLifecycle()
    val artists by libraryViewModel.artists.collectAsStateWithLifecycle()
    val playlistUiState by playlistViewModel.uiState.collectAsStateWithLifecycle()
    val playlists = playlistUiState.playlists
    val stableState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val currentSongId = stableState.currentSong?.id

    val dailyMixSongs by playerViewModel.dailyMixSongs.collectAsStateWithLifecycle()
    val yourMixSongs by playerViewModel.yourMixSongs.collectAsStateWithLifecycle()
    val activeMix = remember(dailyMixSongs, yourMixSongs, allSongs) {
        when {
            dailyMixSongs.isNotEmpty() -> dailyMixSongs
            yourMixSongs.isNotEmpty() -> yourMixSongs
            allSongs.size >= 3 -> allSongs.take(20)
            else -> emptyList()
        }
    }
    val dailyMixTitle = stringResource(R.string.tv_section_daily_mix)

    val genres by remember(musicRepository) {
        musicRepository.getGenres()
    }.collectAsState(initial = emptyList())

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(top = 28.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        // QUICK ACTIONS HEADER
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 40.dp)
            ) {
                Text(
                    text = stringResource(R.string.tv_nav_home),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 26.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    TvFocusableBox(
                        onClick = onOpenQrConnect,
                        shape = RoundedCornerShape(10.dp)
                    ) { isFocused ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(
                                    if (isFocused) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .padding(horizontal = 20.dp, vertical = 12.dp)
                        ) {
                            androidx.compose.material3.Icon(
                                painter = androidx.compose.ui.res.painterResource(R.drawable.ic_qr_code_24),
                                contentDescription = null,
                                tint = if (isFocused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = stringResource(R.string.tv_qr_connect_banner_btn),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = if (isFocused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    TvFocusableBox(
                        onClick = {
                            playerViewModel.shuffleAllSongs()
                            onNavigateToNowPlaying()
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) { isFocused ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(
                                    if (isFocused) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .padding(horizontal = 20.dp, vertical = 12.dp)
                        ) {
                            androidx.compose.material3.Icon(
                                painter = androidx.compose.ui.res.painterResource(R.drawable.rounded_shuffle_24),
                                contentDescription = null,
                                tint = if (isFocused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = stringResource(R.string.tv_home_shuffle_all),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = if (isFocused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    TvFocusableBox(
                        onClick = {
                            playerViewModel.playRandomSong()
                            onNavigateToNowPlaying()
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) { isFocused ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(
                                    if (isFocused) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .padding(horizontal = 20.dp, vertical = 12.dp)
                        ) {
                            androidx.compose.material3.Icon(
                                painter = androidx.compose.ui.res.painterResource(R.drawable.rounded_play_arrow_filled_24),
                                contentDescription = null,
                                tint = if (isFocused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = stringResource(R.string.tv_home_random_song),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = if (isFocused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    TvFocusableBox(
                        onClick = {
                            playerViewModel.shuffleFavoriteSongs()
                            onNavigateToNowPlaying()
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) { isFocused ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(
                                    if (isFocused) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .padding(horizontal = 20.dp, vertical = 12.dp)
                        ) {
                            androidx.compose.material3.Icon(
                                painter = androidx.compose.ui.res.painterResource(R.drawable.rounded_favorite_24),
                                contentDescription = null,
                                tint = if (isFocused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = stringResource(R.string.tv_home_favorite_songs),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = if (isFocused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    TvFocusableBox(
                        onClick = {
                            scope.launch {
                                TvUtils.scanStorageForMusic(context) {
                                    mainViewModel.retrySync()
                                }
                            }
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) { isFocused ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(
                                    if (isFocused) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .padding(horizontal = 18.dp, vertical = 12.dp)
                        ) {
                            androidx.compose.material3.Icon(
                                painter = androidx.compose.ui.res.painterResource(R.drawable.rounded_folder_open_24),
                                contentDescription = null,
                                tint = if (isFocused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = if (isSyncing) stringResource(R.string.tv_home_scanning) else stringResource(R.string.tv_home_scan_disk),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = if (isFocused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    TvFocusableBox(
                        onClick = {
                            scope.launch {
                                TvUtils.installDemoMusic(context) {
                                    mainViewModel.retrySync()
                                }
                            }
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) { isFocused ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(
                                    if (isFocused) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .padding(horizontal = 18.dp, vertical = 12.dp)
                        ) {
                            androidx.compose.material3.Icon(
                                painter = androidx.compose.ui.res.painterResource(R.drawable.rounded_playlist_add_24),
                                contentDescription = null,
                                tint = if (isFocused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = stringResource(R.string.tv_home_demo_music),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = if (isFocused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // EMPTY STATE IF NO SONGS
        if (allSongs.isEmpty() && albums.isEmpty() && artists.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 40.dp, vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = if (isSyncing) stringResource(R.string.tv_home_library_scanning) else stringResource(R.string.tv_home_library_empty),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isSyncing) stringResource(R.string.tv_home_library_scanning_desc)
                            else stringResource(R.string.tv_home_library_empty_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            TvFocusableBox(
                                onClick = {
                                    scope.launch {
                                        TvUtils.installDemoMusic(context) {
                                            mainViewModel.retrySync()
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) { isFocused ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .background(
                                            if (isFocused) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                        )
                                        .padding(horizontal = 24.dp, vertical = 16.dp)
                                ) {
                                    androidx.compose.material3.Icon(
                                        painter = androidx.compose.ui.res.painterResource(R.drawable.rounded_playlist_add_24),
                                        contentDescription = null,
                                        tint = if (isFocused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(end = 12.dp)
                                    )
                                    Column {
                                        Text(
                                            text = stringResource(R.string.tv_home_btn_load_demo),
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = if (isFocused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = stringResource(R.string.tv_home_demo_desc),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isFocused) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            TvFocusableBox(
                                onClick = {
                                    scope.launch {
                                        TvUtils.scanStorageForMusic(context) {
                                            mainViewModel.retrySync()
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) { isFocused ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .background(
                                            if (isFocused) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        .padding(horizontal = 24.dp, vertical = 16.dp)
                                ) {
                                    androidx.compose.material3.Icon(
                                        painter = androidx.compose.ui.res.painterResource(R.drawable.rounded_folder_open_24),
                                        contentDescription = null,
                                        tint = if (isFocused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(end = 12.dp)
                                    )
                                    Column {
                                        Text(
                                            text = stringResource(R.string.tv_home_btn_scan_memory),
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = if (isFocused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = stringResource(R.string.tv_home_scan_memory_desc),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isFocused) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // DAILY MIX SECTION
        if (activeMix.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    TvDailyMixCard(
                        songCount = activeMix.size,
                        onClick = {
                            playerViewModel.playSongs(activeMix, activeMix.first(), dailyMixTitle)
                            onNavigateToNowPlaying()
                        }
                    )

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 40.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(activeMix.take(15), key = { "mix_${it.id}" }) { song ->
                            TvTrackCard(
                                song = song,
                                isPlaying = song.id == currentSongId && stableState.isPlaying,
                                onClick = {
                                    playerViewModel.playSongs(activeMix, song, dailyMixTitle)
                                    onNavigateToNowPlaying()
                                }
                            )
                        }
                    }
                }
            }
        }

        // ALBUMS SHELF
        if (albums.isNotEmpty()) {
            item {
                Column {
                    Text(
                        text = stringResource(R.string.tv_section_albums),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 40.dp, vertical = 4.dp)
                    )

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 40.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(albums, key = { it.id }) { album ->
                            TvAlbumCard(
                                album = album,
                                onClick = {
                                    onAlbumClick(album)
                                }
                            )
                        }
                    }
                }
            }
        }

        // PLAYLISTS SHELF
        if (playlists.isNotEmpty()) {
            item {
                Column {
                    Text(
                        text = stringResource(R.string.tv_section_playlists),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 40.dp, vertical = 4.dp)
                    )

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 40.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(playlists, key = { it.id }) { playlist ->
                            TvPlaylistCard(
                                playlist = playlist,
                                onClick = { onPlaylistClick(playlist) }
                            )
                        }
                    }
                }
            }
        }

        // FAVORITES SHELF
        val favoriteSongs = allSongs.filter { it.isFavorite }
        if (favoriteSongs.isNotEmpty()) {
            item {
                Column {
                    Text(
                        text = stringResource(R.string.tv_home_favorite_songs_count, favoriteSongs.size),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 40.dp, vertical = 4.dp)
                    )

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 40.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(favoriteSongs, key = { "fav_${it.id}" }) { song ->
                            TvTrackCard(
                                song = song,
                                isPlaying = song.id == currentSongId && stableState.isPlaying,
                                onClick = {
                                    playerViewModel.playSongs(favoriteSongs, song)
                                    onNavigateToNowPlaying()
                                }
                            )
                        }
                    }
                }
            }
        }

        // SONGS SHELF
        if (allSongs.isNotEmpty()) {
            item {
                Column {
                    Text(
                        text = stringResource(R.string.tv_section_all_tracks),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 40.dp, vertical = 4.dp)
                    )

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 40.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(allSongs.take(40), key = { it.id }) { song ->
                            TvTrackCard(
                                song = song,
                                isPlaying = song.id == currentSongId && stableState.isPlaying,
                                onClick = {
                                    playerViewModel.playSongs(allSongs, song)
                                    onNavigateToNowPlaying()
                                }
                            )
                        }
                    }
                }
            }
        }

        // ARTISTS SHELF
        if (artists.isNotEmpty()) {
            item {
                Column {
                    Text(
                        text = stringResource(R.string.tv_section_artists),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 40.dp, vertical = 4.dp)
                    )

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 40.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(artists, key = { it.id }) { artist ->
                            TvArtistCard(
                                artist = artist,
                                onClick = {
                                    onArtistClick(artist)
                                }
                            )
                        }
                    }
                }
            }
        }

        // GENRES SHELF
        if (genres.isNotEmpty()) {
            item {
                Column {
                    Text(
                        text = stringResource(R.string.tv_section_genres),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 40.dp, vertical = 4.dp)
                    )

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 40.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(genres, key = { it.id }) { genre ->
                            TvGenreCard(
                                name = genre.name,
                                onClick = { onGenreClick(genre) }
                            )
                        }
                    }
                }
            }
        }
    }
}
