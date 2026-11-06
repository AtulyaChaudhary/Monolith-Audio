package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueuePlayNext
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AlbumSummary
import com.example.data.ArtistSummary
import com.example.data.CategorySubTab
import com.example.data.FolderSummary
import com.example.data.MainNavTab
import com.example.data.PlaylistEntity
import com.example.data.SortOption
import com.example.data.TrackEntity
import com.example.ui.theme.MonoLightGrey
import com.example.ui.theme.MonoSilver
import kotlin.math.roundToInt

@Composable
fun TopStudioBar(
    blurRadiusDp: Float,
    searchQuery: String,
    sortOption: SortOption,
    onSearchQueryChange: (String) -> Unit,
    onSortOptionChange: (SortOption) -> Unit,
    onRescanClick: () -> Unit,
    onConnectFolderClick: () -> Unit,
    onImportFilesClick: () -> Unit,
    onOpenSpicySearchClick: () -> Unit,
    onOpenGlassCustomizer: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FrostedGlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            elevated = true,
            opaqueModal = true
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "MONOLITH AUDIO",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.0.sp
                            ),
                            color = Color.White
                        )
                        Text(
                            text = "MATERIAL YOU • SPICY LYRICS & TTML ENGINE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MonoSilver
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MiniPillButton(
                            text = "Rescan",
                            onClick = onRescanClick,
                            tag = "rescan_library_button"
                        )
                        IconButton(
                            onClick = onOpenSpicySearchClick,
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("top_open_spicy_search_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lyrics,
                                contentDescription = "Search Spicy Lyrics",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = onConnectFolderClick,
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("connect_folder_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CreateNewFolder,
                                contentDescription = "Connect Music Folder",
                                tint = Color.White
                            )
                        }
                        IconButton(
                            onClick = onImportFilesClick,
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("import_files_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AudioFile,
                                contentDescription = "Import Audio & TTML Files",
                                tint = Color.White
                            )
                        }
                        IconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("open_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = Color.White
                            )
                        }
                    }
                }

                // Search & Sort Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = {
                            Text(
                                "Search songs, folders, albums, artists...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MonoSilver
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = MonoSilver,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.White.copy(alpha = 0.65f),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.22f),
                            cursorColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("library_search_input")
                    )

                    Box {
                        FrostedGlassSurface(
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .height(50.dp)
                                .clickable { sortMenuExpanded = true }
                                .padding(horizontal = 12.dp)
                                .testTag("sort_dropdown_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.align(Alignment.Center)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sort,
                                    contentDescription = "Sort",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = sortOption.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = sortMenuExpanded,
                            onDismissRequest = { sortMenuExpanded = false }
                        ) {
                            SortOption.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    onClick = {
                                        onSortOptionChange(option)
                                        sortMenuExpanded = false
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

@Composable
fun AllSongsListView(
    tracks: List<TrackEntity>,
    currentPlayingTrackId: String?,
    playlists: List<PlaylistEntity>,
    contextLabel: String,
    onTrackClick: (TrackEntity) -> Unit,
    onAddToQueueNext: (TrackEntity) -> Unit,
    onAddTrackToPlaylist: (PlaylistEntity, TrackEntity) -> Unit,
    onCreatePlaylistAndAdd: (String, TrackEntity) -> Unit,
    onConnectFolderClick: (() -> Unit)? = null,
    onImportFilesClick: (() -> Unit)? = null,
    onRescanClick: (() -> Unit)? = null,
    headerContent: (@Composable () -> Unit)? = null,
    onRemoveFromPlaylist: ((TrackEntity) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var trackForPlaylistDialog by remember { mutableStateOf<TrackEntity?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxSize()
            .testTag("tracks_lazy_column")
    ) {
        if (headerContent != null) {
            item {
                headerContent()
            }
        }

        if (tracks.isEmpty()) {
            item {
                FrostedGlassSurface(
                    shape = RoundedCornerShape(22.dp),
                    elevated = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CreateNewFolder,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(38.dp)
                        )
                        Text(
                            text = "Your Local Music Library is Empty",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )
                        Text(
                            text = "Connect a music folder, rescan your device folders, or select audio (.flac, .mp3, .wav, .m4a, .ogg, .opus) and co-located .ttml lyrics files.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MonoSilver
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(top = 6.dp)
                        ) {
                            if (onConnectFolderClick != null) {
                                Button(
                                    onClick = onConnectFolderClick,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White,
                                        contentColor = Color.Black
                                    )
                                ) {
                                    Text("Connect Folder")
                                }
                            }
                            if (onRescanClick != null) {
                                Button(
                                    onClick = onRescanClick,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    modifier = Modifier.testTag("empty_rescan_button")
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Rescan")
                                }
                            }
                            if (onImportFilesClick != null) {
                                Button(
                                    onClick = onImportFilesClick,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White.copy(alpha = 0.18f),
                                        contentColor = Color.White
                                    )
                                ) {
                                    Text("Import Files")
                                }
                            }
                        }
                    }
                }
            }
        }

        itemsIndexed(
            items = tracks,
            key = { _, t -> t.id },
            contentType = { _, _ -> "track_row" }
        ) { idx, track ->
            val isCurrent = track.id == currentPlayingTrackId
            val hasTtml = track.hasAnySyncedLyrics

            FrostedGlassSurface(
                shape = RoundedCornerShape(16.dp),
                elevated = isCurrent,
                accentHighlight = isCurrent,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onTrackClick(track) }
                    .testTag("song_row_${track.baseFileName.lowercase()}")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Track Number or Active Indicator
                    Box(
                        modifier = Modifier.width(26.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isCurrent) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = "Currently Playing",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Text(
                                text = "${idx + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MonoSilver
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Unblurred, crystal-sharp Album Art
                    CrispAlbumArtCover(
                        title = track.album,
                        subtitle = track.artist,
                        palette = track.parsePalette(),
                        coverArtPath = track.coverArtPath,
                        sizeDp = 48.dp,
                        cornerRadiusDp = 12.dp,
                        modifier = Modifier.size(48.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (hasTtml) {
                                FrostedGlassSurface(
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "TTML",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            text = "${track.artist} • ${track.album}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MonoLightGrey,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = "${track.folderPath}/${track.baseFileName}.${track.extension} • ${formatDurationMs(track.durationMs)}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MonoSilver,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Priority "Add to Queue" button (inserts at currentIndex + 1)
                    IconButton(
                        onClick = { onAddToQueueNext(track) },
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("add_to_queue_${track.baseFileName.lowercase()}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.QueuePlayNext,
                            contentDescription = "Add ${track.title} to Queue Next",
                            tint = Color.White
                        )
                    }

                    // Add to Playlist or Remove from Playlist
                    if (onRemoveFromPlaylist != null) {
                        IconButton(
                            onClick = { onRemoveFromPlaylist(track) },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Remove from Playlist",
                                tint = MonoSilver
                            )
                        }
                    } else {
                        IconButton(
                            onClick = { trackForPlaylistDialog = track },
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("add_to_playlist_${track.baseFileName.lowercase()}")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                                contentDescription = "Add to Playlist",
                                tint = MonoSilver
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(140.dp))
        }
    }

    if (trackForPlaylistDialog != null) {
        val targetTrack = trackForPlaylistDialog!!
        AddToPlaylistModal(
            track = targetTrack,
            playlists = playlists,
            onDismiss = { trackForPlaylistDialog = null },
            onSelectPlaylist = { pl ->
                onAddTrackToPlaylist(pl, targetTrack)
                trackForPlaylistDialog = null
            },
            onCreateNewAndAdd = { newName ->
                onCreatePlaylistAndAdd(newName, targetTrack)
                trackForPlaylistDialog = null
            }
        )
    }
}

@Composable
fun CategoriesView(
    subTab: CategorySubTab,
    albums: List<AlbumSummary>,
    folders: List<FolderSummary>,
    artists: List<ArtistSummary>,
    playlists: List<PlaylistEntity>,
    allTracks: List<TrackEntity>,
    currentPlayingTrackId: String?,
    selectedAlbumTitle: String?,
    selectedFolderPath: String?,
    selectedArtistName: String?,
    selectedPlaylistId: String?,
    onSelectSubTab: (CategorySubTab) -> Unit,
    onOpenAlbum: (String?) -> Unit,
    onOpenFolder: (String?) -> Unit,
    onOpenArtist: (String?) -> Unit,
    onOpenPlaylist: (String?) -> Unit,
    onPlayTrackFromContext: (List<TrackEntity>, TrackEntity, String) -> Unit,
    onAddToQueueNext: (TrackEntity) -> Unit,
    onAddTrackToPlaylist: (PlaylistEntity, TrackEntity) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onRenamePlaylist: (PlaylistEntity, String) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onRemoveTrackFromPlaylist: (PlaylistEntity, String) -> Unit
) {
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var playlistToRename by remember { mutableStateOf<PlaylistEntity?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        // 4 Distinct Sub-Navigation Options Pill Switcher
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CategorySubTab.entries.forEach { tab ->
                val isSelected = subTab == tab
                val icon = when (tab) {
                    CategorySubTab.ALBUMS -> Icons.Default.Album
                    CategorySubTab.FOLDERS -> Icons.Default.Folder
                    CategorySubTab.ARTISTS -> Icons.Default.Person
                    CategorySubTab.PLAYLISTS -> Icons.AutoMirrored.Filled.QueueMusic
                }

                FrostedGlassSurface(
                    shape = RoundedCornerShape(50),
                    elevated = isSelected,
                    accentHighlight = isSelected,
                    modifier = Modifier
                        .clickable { onSelectSubTab(tab) }
                        .testTag("category_subtab_${tab.name.lowercase()}")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = tab.title,
                            tint = if (isSelected) Color.White else MonoSilver,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = tab.title,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (isSelected) Color.White else MonoSilver
                        )
                    }
                }
            }
        }

        // Sub-Tab Content
        when (subTab) {
            CategorySubTab.ALBUMS -> {
                val activeAlbum = albums.firstOrNull { it.albumTitle == selectedAlbumTitle }
                if (activeAlbum != null) {
                    AllSongsListView(
                        tracks = activeAlbum.tracks,
                        currentPlayingTrackId = currentPlayingTrackId,
                        playlists = playlists,
                        contextLabel = "Album: ${activeAlbum.albumTitle}",
                        onTrackClick = { clicked ->
                            onPlayTrackFromContext(
                                activeAlbum.tracks,
                                clicked,
                                "Album: ${activeAlbum.albumTitle}"
                            )
                        },
                        onAddToQueueNext = onAddToQueueNext,
                        onAddTrackToPlaylist = onAddTrackToPlaylist,
                        onCreatePlaylistAndAdd = { name, _ -> onCreatePlaylist(name) },
                        headerContent = {
                            CategoryDetailHeader(
                                title = activeAlbum.albumTitle,
                                subtitle = "${activeAlbum.artist} • ${activeAlbum.tracks.size} Tracks",
                                palette = activeAlbum.palette,
                                coverArtPath = activeAlbum.coverArtPath,
                                onBack = { onOpenAlbum(null) },
                                onPlayAll = {
                                    activeAlbum.tracks.firstOrNull()?.let { first ->
                                        onPlayTrackFromContext(
                                            activeAlbum.tracks,
                                            first,
                                            "Album: ${activeAlbum.albumTitle}"
                                        )
                                    }
                                }
                            )
                        }
                    )
                } else {
                    // Option 1 — Albums Grid: Album Art prominently on top, followed by Album Title and Artist
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 156.dp),
                        contentPadding = PaddingValues(
                            start = 14.dp,
                            end = 14.dp,
                            top = 8.dp,
                            bottom = 140.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("albums_grid")
                    ) {
                        items(albums, key = { it.albumTitle }) { album ->
                            FrostedGlassSurface(
                                shape = RoundedCornerShape(20.dp),
                                elevated = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenAlbum(album.albumTitle) }
                                    .testTag("album_card_${album.albumTitle.lowercase().replace(' ', '_')}")
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    // Prominent Unblurred Album Art on Top
                                    CrispAlbumArtCover(
                                        title = album.albumTitle,
                                        subtitle = album.artist,
                                        palette = album.palette,
                                        coverArtPath = album.coverArtPath,
                                        sizeDp = 140.dp,
                                        cornerRadiusDp = 14.dp,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = album.albumTitle,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = album.artist,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MonoSilver,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${album.tracks.size} tracks",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MonoLightGrey
                                    )
                                }
                            }
                        }
                    }
                }
            }

            CategorySubTab.FOLDERS -> {
                val activeFolder = folders.firstOrNull { it.folderPath == selectedFolderPath }
                if (activeFolder != null) {
                    AllSongsListView(
                        tracks = activeFolder.tracks,
                        currentPlayingTrackId = currentPlayingTrackId,
                        playlists = playlists,
                        contextLabel = "Folder: ${activeFolder.folderPath}",
                        onTrackClick = { clicked ->
                            onPlayTrackFromContext(
                                activeFolder.tracks,
                                clicked,
                                "Folder: ${activeFolder.folderPath}"
                            )
                        },
                        onAddToQueueNext = onAddToQueueNext,
                        onAddTrackToPlaylist = onAddTrackToPlaylist,
                        onCreatePlaylistAndAdd = { name, _ -> onCreatePlaylist(name) },
                        headerContent = {
                            CategoryDetailHeader(
                                title = activeFolder.folderName,
                                subtitle = "${activeFolder.folderPath} • ${activeFolder.tracks.size} songs • ${activeFolder.ttmlCount} co-located .ttml",
                                palette = activeFolder.tracks.first().parsePalette(),
                                coverArtPath = activeFolder.tracks.first().coverArtPath,
                                onBack = { onOpenFolder(null) },
                                onPlayAll = {
                                    activeFolder.tracks.firstOrNull()?.let { first ->
                                        onPlayTrackFromContext(
                                            activeFolder.tracks,
                                            first,
                                            "Folder: ${activeFolder.folderPath}"
                                        )
                                    }
                                }
                            )
                        }
                    )
                } else {
                    // Option 2 — Folders List
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("folders_list")
                    ) {
                        itemsIndexed(folders, key = { _, f -> f.folderPath }) { _, folder ->
                            FrostedGlassSurface(
                                shape = RoundedCornerShape(18.dp),
                                elevated = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenFolder(folder.folderPath) }
                                    .testTag("folder_card_${folder.folderName.lowercase()}")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(14.dp)
                                ) {
                                    FrostedGlassSurface(
                                        shape = RoundedCornerShape(14.dp),
                                        accentHighlight = true,
                                        modifier = Modifier.size(52.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Folder,
                                            contentDescription = "Folder",
                                            tint = Color.White,
                                            modifier = Modifier
                                                .size(26.dp)
                                                .align(Alignment.Center)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = folder.folderName,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color.White
                                        )
                                        Text(
                                            text = folder.folderPath,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MonoLightGrey
                                        )
                                        Text(
                                            text = "${folder.tracks.size} songs • ${folder.ttmlCount} .ttml files paired",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MonoSilver
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            folder.tracks.firstOrNull()?.let { first ->
                                                onPlayTrackFromContext(
                                                    folder.tracks,
                                                    first,
                                                    "Folder: ${folder.folderPath}"
                                                )
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Play Folder",
                                            tint = Color.White
                                        )
                                    }
                                }
                            }
                        }
                        item { Spacer(modifier = Modifier.height(140.dp)) }
                    }
                }
            }

            CategorySubTab.ARTISTS -> {
                val activeArtist = artists.firstOrNull { it.artistName == selectedArtistName }
                if (activeArtist != null) {
                    AllSongsListView(
                        tracks = activeArtist.tracks,
                        currentPlayingTrackId = currentPlayingTrackId,
                        playlists = playlists,
                        contextLabel = "Artist: ${activeArtist.artistName}",
                        onTrackClick = { clicked ->
                            onPlayTrackFromContext(
                                activeArtist.tracks,
                                clicked,
                                "Artist: ${activeArtist.artistName}"
                            )
                        },
                        onAddToQueueNext = onAddToQueueNext,
                        onAddTrackToPlaylist = onAddTrackToPlaylist,
                        onCreatePlaylistAndAdd = { name, _ -> onCreatePlaylist(name) },
                        headerContent = {
                            CategoryDetailHeader(
                                title = activeArtist.artistName,
                                subtitle = "${activeArtist.albumCount} Albums • ${activeArtist.tracks.size} Songs",
                                palette = activeArtist.palette,
                                coverArtPath = activeArtist.tracks.first().coverArtPath,
                                onBack = { onOpenArtist(null) },
                                onPlayAll = {
                                    activeArtist.tracks.firstOrNull()?.let { first ->
                                        onPlayTrackFromContext(
                                            activeArtist.tracks,
                                            first,
                                            "Artist: ${activeArtist.artistName}"
                                        )
                                    }
                                }
                            )
                        }
                    )
                } else {
                    // Option 3 — Artists List
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("artists_list")
                    ) {
                        itemsIndexed(artists, key = { _, a -> a.artistName }) { _, artist ->
                            FrostedGlassSurface(
                                shape = RoundedCornerShape(18.dp),
                                elevated = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenArtist(artist.artistName) }
                                    .testTag("artist_card_${artist.artistName.lowercase().replace(' ', '_')}")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(14.dp)
                                ) {
                                    CrispAlbumArtCover(
                                        title = artist.artistName,
                                        subtitle = "",
                                        palette = artist.palette,
                                        coverArtPath = artist.tracks.firstOrNull()?.coverArtPath,
                                        sizeDp = 52.dp,
                                        cornerRadiusDp = 26.dp,
                                        modifier = Modifier.size(52.dp)
                                    )
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = artist.artistName,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "${artist.albumCount} albums • ${artist.tracks.size} songs",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MonoSilver
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            artist.tracks.firstOrNull()?.let { first ->
                                                onPlayTrackFromContext(
                                                    artist.tracks,
                                                    first,
                                                    "Artist: ${artist.artistName}"
                                                )
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Play Artist",
                                            tint = Color.White
                                        )
                                    }
                                }
                            }
                        }
                        item { Spacer(modifier = Modifier.height(140.dp)) }
                    }
                }
            }

            CategorySubTab.PLAYLISTS -> {
                val activePlaylist = playlists.firstOrNull { it.id == selectedPlaylistId }
                if (activePlaylist != null) {
                    val trackById = allTracks.associateBy { it.id }
                    val playlistTracks = activePlaylist.trackIds.mapNotNull { trackById[it] }
                    AllSongsListView(
                        tracks = playlistTracks,
                        currentPlayingTrackId = currentPlayingTrackId,
                        playlists = playlists,
                        contextLabel = "Playlist: ${activePlaylist.name}",
                        onTrackClick = { clicked ->
                            onPlayTrackFromContext(
                                playlistTracks,
                                clicked,
                                "Playlist: ${activePlaylist.name}"
                            )
                        },
                        onAddToQueueNext = onAddToQueueNext,
                        onAddTrackToPlaylist = onAddTrackToPlaylist,
                        onCreatePlaylistAndAdd = { name, _ -> onCreatePlaylist(name) },
                        onRemoveFromPlaylist = { track ->
                            onRemoveTrackFromPlaylist(activePlaylist, track.id)
                        },
                        headerContent = {
                            CategoryDetailHeader(
                                title = activePlaylist.name,
                                subtitle = "Custom Playlist • ${playlistTracks.size} Songs",
                                palette = playlistTracks.firstOrNull()?.parsePalette()
                                    ?: listOf(Color(0xFF6366F1), Color(0xFFEC4899), Color(0xFF06B6D4)),
                                coverArtPath = playlistTracks.firstOrNull()?.coverArtPath,
                                onBack = { onOpenPlaylist(null) },
                                onPlayAll = {
                                    playlistTracks.firstOrNull()?.let { first ->
                                        onPlayTrackFromContext(
                                            playlistTracks,
                                            first,
                                            "Playlist: ${activePlaylist.name}"
                                        )
                                    }
                                }
                            )
                        }
                    )
                } else {
                    // Option 4 — Custom Playlists
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("playlists_list")
                    ) {
                        item {
                            FrostedGlassSurface(
                                shape = RoundedCornerShape(18.dp),
                                accentHighlight = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showCreatePlaylistDialog = true }
                                    .testTag("create_playlist_button")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "New Playlist",
                                        tint = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Create New Custom Playlist",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        itemsIndexed(playlists, key = { _, p -> p.id }) { _, pl ->
                            FrostedGlassSurface(
                                shape = RoundedCornerShape(18.dp),
                                elevated = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenPlaylist(pl.id) }
                                    .testTag("playlist_card_${pl.name.lowercase().replace(' ', '_')}")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(14.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = pl.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "${pl.trackIds.size} songs",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MonoSilver
                                        )
                                    }
                                    IconButton(onClick = { playlistToRename = pl }) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Rename Playlist",
                                            tint = MonoSilver
                                        )
                                    }
                                    IconButton(onClick = { onDeletePlaylist(pl.id) }) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete Playlist",
                                            tint = MonoSilver
                                        )
                                    }
                                }
                            }
                        }

                        item { Spacer(modifier = Modifier.height(140.dp)) }
                    }
                }
            }
        }
    }

    if (showCreatePlaylistDialog) {
        PlaylistNameDialog(
            title = "Create Playlist",
            initialName = "",
            onDismiss = { showCreatePlaylistDialog = false },
            onConfirm = { name ->
                onCreatePlaylist(name)
                showCreatePlaylistDialog = false
            }
        )
    }

    if (playlistToRename != null) {
        val target = playlistToRename!!
        PlaylistNameDialog(
            title = "Rename Playlist",
            initialName = target.name,
            onDismiss = { playlistToRename = null },
            onConfirm = { newName ->
                onRenamePlaylist(target, newName)
                playlistToRename = null
            }
        )
    }
}

@Composable
private fun CategoryDetailHeader(
    title: String,
    subtitle: String,
    palette: List<Color>,
    coverArtPath: String?,
    onBack: () -> Unit,
    onPlayAll: () -> Unit
) {
    FrostedGlassSurface(
        shape = RoundedCornerShape(20.dp),
        elevated = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(14.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(44.dp)
                    .testTag("category_detail_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            CrispAlbumArtCover(
                title = title,
                subtitle = subtitle,
                palette = palette,
                coverArtPath = coverArtPath,
                sizeDp = 58.dp,
                cornerRadiusDp = 14.dp,
                modifier = Modifier.size(58.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MonoSilver,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onPlayAll,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                ),
                shape = CircleShape,
                modifier = Modifier.testTag("category_play_all_button")
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Play", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun PlaylistNameDialog(
    title: String,
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Playlist Name") },
                singleLine = true,
                modifier = Modifier.testTag("playlist_name_input")
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name) },
                modifier = Modifier.testTag("confirm_playlist_dialog_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun AddToPlaylistModal(
    track: TrackEntity,
    playlists: List<PlaylistEntity>,
    onDismiss: () -> Unit,
    onSelectPlaylist: (PlaylistEntity) -> Unit,
    onCreateNewAndAdd: (String) -> Unit
) {
    var newPlaylistName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add \"${track.title}\" to Playlist") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                playlists.forEach { pl ->
                    FrostedGlassSurface(
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectPlaylist(pl) }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(pl.name, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                            Text(" • ${pl.trackIds.size} tracks", color = MonoSilver, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("Or create new playlist") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            if (newPlaylistName.isNotBlank()) {
                TextButton(onClick = { onCreateNewAndAdd(newPlaylistName) }) {
                    Text("Create & Add")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
