package com.unshoo.pixelmusic.tv

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import android.widget.Toast
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unshoo.pixelmusic.R
import com.unshoo.pixelmusic.data.model.Album
import com.unshoo.pixelmusic.data.model.Artist
import com.unshoo.pixelmusic.data.model.SearchResultItem
import com.unshoo.pixelmusic.data.network.youtube.InnerTubeParser
import com.unshoo.pixelmusic.data.network.youtube.YouTubeTrack
import com.unshoo.pixelmusic.presentation.viewmodel.PlayerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

@Composable
fun TvSearchScreen(
    playerViewModel: PlayerViewModel,
    innerTubeParser: InnerTubeParser,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onNavigateToNowPlaying: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by playerViewModel.playerUiState.collectAsStateWithLifecycle()
    val stableState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val currentSongId = stableState.currentSong?.id
    val query = playerViewModel.searchQuery
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val songResults = uiState.searchResults.filterIsInstance<SearchResultItem.SongItem>().map { it.song }
    val albumResults = uiState.searchResults.filterIsInstance<SearchResultItem.AlbumItem>().map { it.album }
    val artistResults = uiState.searchResults.filterIsInstance<SearchResultItem.ArtistItem>().map { it.artist }

    var onlineResults by remember { mutableStateOf<List<YouTubeTrack>>(emptyList()) }
    var isOnlineLoading by remember { mutableStateOf(false) }
    var loadingTrackId by remember { mutableStateOf<String?>(null) }

    // Search online tracks via InnerTube debounced
    LaunchedEffect(query) {
        val trimmed = query.trim()
        if (trimmed.isNotBlank()) {
            isOnlineLoading = true
            delay(400)
            try {
                onlineResults = innerTubeParser.searchSongs(trimmed)
            } catch (_: Exception) {
                onlineResults = emptyList()
            } finally {
                isOnlineLoading = false
            }
        } else {
            onlineResults = emptyList()
            isOnlineLoading = false
        }
    }

    val hasLocalResults = songResults.isNotEmpty() || albumResults.isNotEmpty() || artistResults.isNotEmpty()
    val hasOnlineResults = onlineResults.isNotEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 40.dp, vertical = 28.dp)
    ) {
        Text(
            text = stringResource(R.string.tv_nav_search),
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp
            ),
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Search Input Bar
        OutlinedTextField(
            value = query,
            onValueChange = {
                playerViewModel.updateSearchQuery(it)
                playerViewModel.performSearch(it)
            },
            placeholder = { Text(stringResource(R.string.tv_search_input_placeholder)) },
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.rounded_search_24),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            trailingIcon = {
                if (isOnlineLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            ),
            modifier = Modifier.fillMaxWidth(0.75f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (query.isBlank()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.tv_search_initial_hint),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (!hasLocalResults && !hasOnlineResults) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (isOnlineLoading) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = stringResource(R.string.tv_search_searching),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Text(
                        text = stringResource(R.string.tv_search_no_results, query),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Local Songs Shelf
                if (songResults.isNotEmpty()) {
                    item {
                        Column {
                            Text(
                                text = stringResource(R.string.tv_search_local_tracks, songResults.size),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                ),
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(bottom = 10.dp)
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                items(songResults, key = { "loc_${it.id}" }) { song ->
                                    TvTrackCard(
                                        song = song,
                                        isPlaying = song.id == currentSongId && stableState.isPlaying,
                                        onClick = {
                                            playerViewModel.playSongs(songResults, song)
                                            onNavigateToNowPlaying()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // YouTube Music (Online) Shelf
                if (onlineResults.isNotEmpty()) {
                    item {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(bottom = 10.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.rounded_play_arrow_filled_24),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = stringResource(R.string.tv_search_online_tracks, onlineResults.size),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    ),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                items(onlineResults, key = { "yt_${it.videoId}" }) { ytTrack ->
                                    val song = remember(ytTrack) { innerTubeParser.toSong(ytTrack) }
                                    TvTrackCard(
                                        song = song,
                                        isPlaying = song.id == currentSongId && stableState.isPlaying,
                                        isLoading = loadingTrackId == ytTrack.videoId,
                                        onClick = {
                                            if (loadingTrackId != null) return@TvTrackCard
                                            loadingTrackId = ytTrack.videoId
                                            coroutineScope.launch {
                                                try {
                                                    val playable = innerTubeParser.resolvePlayableSong(ytTrack)
                                                    if (playable != null) {
                                                        playerViewModel.playSongs(listOf(playable), playable)
                                                        onNavigateToNowPlaying()
                                                    } else {
                                                        Toast.makeText(context, context.getString(R.string.tv_online_stream_failed), Toast.LENGTH_SHORT).show()
                                                    }
                                                } catch (e: Exception) {
                                                    Timber.e(e, "Failed to resolve online song")
                                                    Toast.makeText(context, context.getString(R.string.tv_online_stream_failed), Toast.LENGTH_SHORT).show()
                                                } finally {
                                                    loadingTrackId = null
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Local Albums Shelf
                if (albumResults.isNotEmpty()) {
                    item {
                        Column {
                            Text(
                                text = stringResource(R.string.tv_search_albums, albumResults.size),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                ),
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(bottom = 10.dp)
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                items(albumResults, key = { "alb_${it.id}" }) { album ->
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

                // Local Artists Shelf
                if (artistResults.isNotEmpty()) {
                    item {
                        Column {
                            Text(
                                text = stringResource(R.string.tv_search_artists, artistResults.size),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                ),
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(bottom = 10.dp)
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                items(artistResults, key = { "art_${it.id}" }) { artist ->
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
            }
        }
    }
}
