package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.MainNavTab
import com.example.ui.components.AllSongsListView
import com.example.ui.components.AnimatedAlbumArtBackdrop
import com.example.ui.components.BottomPlayerAndNavColumn
import com.example.ui.components.CategoriesView
import com.example.ui.components.FrostedGlassSurface
import com.example.ui.components.GlassCustomizerSheet
import com.example.ui.components.LyricsStageView
import com.example.ui.components.QueueDrawerSheet
import com.example.ui.components.SettingsModalSheet
import com.example.ui.components.SpeedAndPitchModalSheet
import com.example.ui.components.TopStudioBar
import com.example.ui.theme.CustomFontManager
import com.example.ui.theme.GlassThemeConfig
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.MusicPlayerViewModel
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val playerViewModel: MusicPlayerViewModel = viewModel()
            val state by playerViewModel.uiState.collectAsStateWithLifecycle()

            val activePalette = state.currentTrack?.parsePalette() ?: listOf(
                Color(0xFFD97706),
                Color(0xFFDC2626),
                Color(0xFF2563EB),
                Color(0xFF7C3AED),
                Color(0xFF1E1B4B)
            )

            val customUiFontFamily = remember(state.settings.customUiFontPath) {
                CustomFontManager.loadFontFamilyFromFile(state.settings.customUiFontPath)
            }
            val customLyricsFontFamily = remember(state.settings.customLyricsFontPath) {
                CustomFontManager.loadFontFamilyFromFile(state.settings.customLyricsFontPath)
            }

            val glassConfig = GlassThemeConfig(
                themeMode = state.settings.themeMode,
                blurRadiusDp = state.settings.glassBlurRadiusDp,
                frostOpacity = state.settings.glassFrostOpacity,
                specularIntensity = state.settings.glassSpecularIntensity,
                animationSpeed = state.settings.bgAnimationSpeed,
                oledPitchBlack = state.settings.oledPitchBlack,
                theatreMode = state.settings.theatreFullscreenMode,
                isLyricsScreenActive = state.isLyricsExpanded,
                customUiFontFamily = customUiFontFamily,
                customLyricsFontFamily = customLyricsFontFamily,
                activePalette = activePalette
            )

            MyApplicationTheme(glassConfig = glassConfig) {
                MonolithMusicApp(
                    viewModel = playerViewModel
                )
            }
        }
    }
}

@Composable
fun MonolithMusicApp(
    viewModel: MusicPlayerViewModel
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { frameNanos ->
                viewModel.tickFrameClock(frameNanos)
            }
        }
    }

    LaunchedEffect(state.statusBannerMessage) {
        if (state.statusBannerMessage != null) {
            delay(3200)
            viewModel.clearStatusBanner()
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            viewModel.scanSafDirectoryTree(treeUri)
        }
    }

    val multipleFilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importMultipleFiles(uris)
        }
    }

    val manualTtmlLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.loadManualTtmlUriForCurrentTrack(uri)
        }
    }

    // Custom UI Font (.ttf / .otf) Picker
    val uiFontPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.uploadCustomUiFont(uri)
        }
    }

    // Custom Synced Lyrics Font (.ttf / .otf) Picker
    val lyricsFontPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.uploadCustomLyricsFont(uri)
        }
    }

    val hasActiveSubScreen = state.isLyricsExpanded ||
        state.selectedAlbumTitle != null ||
        state.selectedFolderPath != null ||
        state.selectedArtistName != null ||
        state.selectedPlaylistId != null ||
        state.mainTab == MainNavTab.CATEGORIES

    BackHandler(enabled = hasActiveSubScreen) {
        when {
            state.settings.theatreFullscreenMode -> viewModel.setTheatreFullscreenMode(false)
            state.isLyricsExpanded -> viewModel.setLyricsExpanded(false)
            state.selectedAlbumTitle != null -> viewModel.openAlbumDetail(null)
            state.selectedFolderPath != null -> viewModel.openFolderDetail(null)
            state.selectedArtistName != null -> viewModel.openArtistDetail(null)
            state.selectedPlaylistId != null -> viewModel.openPlaylistDetail(null)
            state.mainTab == MainNavTab.CATEGORIES -> viewModel.selectMainTab(MainNavTab.ALL_SONGS)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideTablet = maxWidth >= 760.dp

        // LAYER 0: Dynamic Animated Album Art & Palette Fluid Canvas with Real-Time Blur Slider
        AnimatedAlbumArtBackdrop(
            isPlaying = state.isPlaying,
            playbackSpeed = state.playbackSpeed,
            modifier = Modifier.fillMaxSize()
        )

        // LAYER 1 & 2: Main Content & Synced TTML Lyrics Stage
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            if (state.isLyricsExpanded) {
                LyricsStageView(
                    track = state.currentTrack,
                    lines = state.parsedLyricsLines,
                    activeLineIndex = state.activeLyricsLineIndex,
                    currentPositionMs = state.currentPositionMs,
                    durationMs = state.durationMs,
                    isPlaying = state.isPlaying,
                    playbackSpeed = state.playbackSpeed,
                    playbackPitch = state.playbackPitch,
                    coLocatedTtmlFileName = state.coLocatedTtmlFileName,
                    settings = state.settings,
                    onCollapse = { viewModel.setLyricsExpanded(false) },
                    onToggleTheatreMode = { viewModel.setTheatreFullscreenMode(it) },
                    onSeekToMs = { viewModel.seekTo(it) },
                    onTogglePlayPause = { viewModel.togglePlayPause() },
                    onSkipPrevious = { viewModel.skipToPrevious() },
                    onSkipNext = { viewModel.skipToNext() },
                    onOpenSpeedPitchSheet = { viewModel.setSpeedPitchSheetOpen(true) },
                    onOpenGlassCustomizer = { viewModel.setGlassCustomizerOpen(true) },
                    onAdjustTrackOffset = { viewModel.adjustCurrentTrackLyricsOffset(it) },
                    onLoadManualTtmlClick = { manualTtmlLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                TopStudioBar(
                    blurRadiusDp = state.settings.glassBlurRadiusDp,
                    searchQuery = state.searchQuery,
                    sortOption = state.sortOption,
                    onSearchQueryChange = { viewModel.setSearchQuery(it) },
                    onSortOptionChange = { viewModel.setSortOption(it) },
                    onConnectFolderClick = { folderPickerLauncher.launch(null) },
                    onImportFilesClick = { multipleFilesLauncher.launch(arrayOf("*/*")) },
                    onOpenGlassCustomizer = { viewModel.setGlassCustomizerOpen(true) },
                    onOpenSettings = { viewModel.setSettingsOpen(true) }
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            when (state.mainTab) {
                                MainNavTab.ALL_SONGS -> {
                                    val tracks = state.filteredAndSortedTracks
                                    AllSongsListView(
                                        tracks = tracks,
                                        currentPlayingTrackId = state.currentTrack?.id,
                                        playlists = state.playlists,
                                        contextLabel = "All Songs",
                                        onTrackClick = { clicked ->
                                            viewModel.playSongFromContext(tracks, clicked, "All Songs")
                                        },
                                        onAddToQueueNext = { track ->
                                            viewModel.addToQueueNext(track)
                                        },
                                        onAddTrackToPlaylist = { pl, track ->
                                            viewModel.addTrackToPlaylist(pl, track.id)
                                        },
                                        onCreatePlaylistAndAdd = { name, _ ->
                                            viewModel.createPlaylist(name)
                                        },
                                        onConnectFolderClick = { folderPickerLauncher.launch(null) },
                                        onImportFilesClick = { multipleFilesLauncher.launch(arrayOf("*/*")) }
                                    )
                                }

                                MainNavTab.CATEGORIES -> {
                                    CategoriesView(
                                        subTab = state.categorySubTab,
                                        albums = state.albums,
                                        folders = state.folders,
                                        artists = state.artists,
                                        playlists = state.playlists,
                                        allTracks = state.allTracks,
                                        currentPlayingTrackId = state.currentTrack?.id,
                                        selectedAlbumTitle = state.selectedAlbumTitle,
                                        selectedFolderPath = state.selectedFolderPath,
                                        selectedArtistName = state.selectedArtistName,
                                        selectedPlaylistId = state.selectedPlaylistId,
                                        onSelectSubTab = { viewModel.selectCategorySubTab(it) },
                                        onOpenAlbum = { viewModel.openAlbumDetail(it) },
                                        onOpenFolder = { viewModel.openFolderDetail(it) },
                                        onOpenArtist = { viewModel.openArtistDetail(it) },
                                        onOpenPlaylist = { viewModel.openPlaylistDetail(it) },
                                        onPlayTrackFromContext = { ctxTracks, clicked, label ->
                                            viewModel.playSongFromContext(ctxTracks, clicked, label)
                                        },
                                        onAddToQueueNext = { viewModel.addToQueueNext(it) },
                                        onAddTrackToPlaylist = { pl, t -> viewModel.addTrackToPlaylist(pl, t.id) },
                                        onCreatePlaylist = { viewModel.createPlaylist(it) },
                                        onRenamePlaylist = { pl, name -> viewModel.renamePlaylist(pl, name) },
                                        onDeletePlaylist = { viewModel.deletePlaylist(it) },
                                        onRemoveTrackFromPlaylist = { pl, tId -> viewModel.removeTrackFromPlaylist(pl, tId) }
                                    )
                                }
                            }
                        }

                        if (isWideTablet) {
                            Box(
                                modifier = Modifier
                                    .weight(0.95f)
                                    .fillMaxHeight()
                            ) {
                                LyricsStageView(
                                    track = state.currentTrack,
                                    lines = state.parsedLyricsLines,
                                    activeLineIndex = state.activeLyricsLineIndex,
                                    currentPositionMs = state.currentPositionMs,
                                    durationMs = state.durationMs,
                                    isPlaying = state.isPlaying,
                                    playbackSpeed = state.playbackSpeed,
                                    playbackPitch = state.playbackPitch,
                                    coLocatedTtmlFileName = state.coLocatedTtmlFileName,
                                    settings = state.settings,
                                    onCollapse = { viewModel.setLyricsExpanded(true) },
                                    onToggleTheatreMode = { viewModel.setTheatreFullscreenMode(it) },
                                    onSeekToMs = { viewModel.seekTo(it) },
                                    onTogglePlayPause = { viewModel.togglePlayPause() },
                                    onSkipPrevious = { viewModel.skipToPrevious() },
                                    onSkipNext = { viewModel.skipToNext() },
                                    onOpenSpeedPitchSheet = { viewModel.setSpeedPitchSheetOpen(true) },
                                    onOpenGlassCustomizer = { viewModel.setGlassCustomizerOpen(true) },
                                    onAdjustTrackOffset = { viewModel.adjustCurrentTrackLyricsOffset(it) },
                                    onLoadManualTtmlClick = { manualTtmlLauncher.launch(arrayOf("*/*")) }
                                )
                            }
                        }
                    }

                    BottomPlayerAndNavColumn(
                        currentTrack = state.currentTrack,
                        isPlaying = state.isPlaying,
                        currentPositionMs = state.currentPositionMs,
                        durationMs = state.durationMs,
                        playbackSpeed = state.playbackSpeed,
                        playbackPitch = state.playbackPitch,
                        queueSize = state.queue.size,
                        currentQueueIndex = state.currentQueueIndex,
                        hasCoLocatedTtml = state.hasCoLocatedTtml,
                        mainTab = state.mainTab,
                        onTogglePlayPause = { viewModel.togglePlayPause() },
                        onSkipPrevious = { viewModel.skipToPrevious() },
                        onSkipNext = { viewModel.skipToNext() },
                        onExpandLyrics = { viewModel.setLyricsExpanded(true) },
                        onOpenSpeedPitch = { viewModel.setSpeedPitchSheetOpen(true) },
                        onOpenQueueDrawer = { viewModel.setQueueDrawerOpen(true) },
                        onSelectMainTab = { viewModel.selectMainTab(it) },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = state.statusBannerMessage != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 56.dp, start = 24.dp, end = 24.dp)
        ) {
            FrostedGlassSurface(
                shape = RoundedCornerShape(50),
                elevated = true,
                accentHighlight = true,
                opaqueModal = true,
                modifier = Modifier.clickable { viewModel.clearStatusBanner() }
            ) {
                Text(
                    text = state.statusBannerMessage ?: "",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                )
            }
        }

        if (state.isSpeedPitchSheetOpen) {
            SpeedAndPitchModalSheet(
                speed = state.playbackSpeed,
                pitch = state.playbackPitch,
                onSpeedChange = { viewModel.setPlaybackSpeed(it) },
                onStepSpeed = { viewModel.stepPlaybackSpeed(it) },
                onResetSpeed = { viewModel.resetPlaybackSpeed() },
                onPitchChange = { viewModel.setPlaybackPitch(it) },
                onStepPitch = { viewModel.stepPlaybackPitch(it) },
                onResetPitch = { viewModel.resetPlaybackPitch() },
                onDismiss = { viewModel.setSpeedPitchSheetOpen(false) }
            )
        }

        if (state.isQueueDrawerOpen) {
            QueueDrawerSheet(
                queue = state.queue,
                currentQueueIndex = state.currentQueueIndex,
                queueContextLabel = state.queueContextLabel,
                onJumpToIndex = { viewModel.jumpToQueueIndex(it) },
                onMoveItem = { from, to -> viewModel.moveQueueItem(from, to) },
                onRemoveItem = { viewModel.removeQueueItem(it) },
                onClearUpcoming = { viewModel.clearUpcomingQueue() },
                onDismiss = { viewModel.setQueueDrawerOpen(false) }
            )
        }

        if (state.isGlassCustomizerOpen) {
            GlassCustomizerSheet(
                settings = state.settings,
                onUpdateSettings = { viewModel.updateSettings(it) },
                onUploadUiFontClick = { uiFontPickerLauncher.launch(arrayOf("*/*")) },
                onResetUiFontClick = { viewModel.resetCustomUiFont() },
                onUploadLyricsFontClick = { lyricsFontPickerLauncher.launch(arrayOf("*/*")) },
                onResetLyricsFontClick = { viewModel.resetCustomLyricsFont() },
                onDismiss = { viewModel.setGlassCustomizerOpen(false) }
            )
        }

        if (state.isSettingsOpen) {
            SettingsModalSheet(
                settings = state.settings,
                currentTrack = state.currentTrack,
                totalTracksCount = state.allTracks.size,
                totalFoldersCount = state.folders.size,
                onUpdateSettings = { viewModel.updateSettings(it) },
                onAdjustGlobalOffset = { viewModel.adjustGlobalLyricsOffset(it) },
                onSetGlobalOffset = { viewModel.setGlobalLyricsOffset(it) },
                onAdjustTrackOffset = { viewModel.adjustCurrentTrackLyricsOffset(it) },
                onUploadUiFontClick = { uiFontPickerLauncher.launch(arrayOf("*/*")) },
                onResetUiFontClick = { viewModel.resetCustomUiFont() },
                onUploadLyricsFontClick = { lyricsFontPickerLauncher.launch(arrayOf("*/*")) },
                onResetLyricsFontClick = { viewModel.resetCustomLyricsFont() },
                onClearAllTracks = { viewModel.clearAllLibraryTracks() },
                onExportPlaylistsJson = { viewModel.exportPlaylistsJson() },
                onImportPlaylistsJson = { viewModel.importPlaylistsJson(it) },
                onDismiss = { viewModel.setSettingsOpen(false) }
            )
        }
    }
}
